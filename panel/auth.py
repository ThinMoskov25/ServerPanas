"""
Autenticacion del panel: usuarios, contrasenas y sesiones.

Las contrasenas se guardan como PBKDF2-SHA256 con sal (datos/usuarios.json);
nunca en texto. Las sesiones viven en memoria (se pierden al reiniciar el panel).

Gestion de usuarios (la contrasena se teclea, no se pasa por argumento):
    python auth.py crear <usuario> [admin|colaborador]
    python auth.py borrar <usuario>
    python auth.py listar
"""
import getpass
import hashlib
import hmac
import json
import os
import re
import secrets
import sys
import threading
import time

BASE = os.path.dirname(os.path.abspath(__file__))
ARCH_USUARIOS = os.path.join(BASE, 'datos', 'usuarios.json')
ITERACIONES = 240000
DURACION_SESION = 12 * 3600
MAX_FALLOS = 5
BLOQUEO_SEG = 600
ROLES = ('admin', 'colaborador', 'jugador')
NOMBRE_OK = re.compile(r'^[A-Za-z0-9_.\-]{3,32}$')

# Rutas que un colaborador no puede usar: editor de archivos, ajustes del panel
# e instalacion del mod (reinicia el servidor).
SOLO_ADMIN = {
    ('GET', '/api/archivos'), ('GET', '/api/archivo'), ('PUT', '/api/archivo'),
    ('POST', '/api/config'), ('POST', '/api/instalar-mod'),
    ('GET', '/api/usuarios'), ('POST', '/api/usuarios'), ('DELETE', '/api/usuarios'),
    ('GET', '/api/acceso'), ('POST', '/api/acceso/ngrok'),
}

_candado = threading.Lock()
_sesiones = {}   # token -> {'usuario', 'rol', 'expira'}
_fallos = {}     # ip -> [num_fallos, bloqueado_hasta]

# Donde viven los usuarios:
#  - 'archivo' (por defecto, en el PC): datos/usuarios.json
#  - 'exaroton' (en la nube): un archivo en el propio servidor de exaroton, porque
#    plataformas como Render borran el disco al reiniciar. Se lee con cache corta.
ALMACEN = os.environ.get('PANEL_ALMACEN', 'archivo')
REMOTO_USUARIOS = os.environ.get('PANEL_USUARIOS_REMOTO', 'serverpanas-panel/usuarios.json')
_exa = None
_cache = {'usuarios': None, 'ts': 0.0}


def configurar_almacen(cliente_exaroton):
    """El panel pasa aqui su cliente de exaroton (necesario si ALMACEN == 'exaroton')."""
    global _exa
    _exa = cliente_exaroton


def _cargar():
    if ALMACEN == 'exaroton':
        with _candado:
            if _cache['usuarios'] is None or time.time() - _cache['ts'] > 30:
                try:
                    crudo = _exa.leer_archivo(REMOTO_USUARIOS)
                    _cache['usuarios'] = json.loads(crudo.decode('utf-8')).get('usuarios', {})
                except Exception:
                    # Si no se puede leer, se conserva lo ultimo conocido. Sin usuarios el
                    # panel rechaza todo lo que llega de Internet, asi que nunca queda abierto.
                    if _cache['usuarios'] is None:
                        _cache['usuarios'] = {}
                _cache['ts'] = time.time()
            return json.loads(json.dumps(_cache['usuarios']))
    try:
        with open(ARCH_USUARIOS, 'r', encoding='utf-8') as f:
            return json.load(f).get('usuarios', {})
    except FileNotFoundError:
        return {}


def _guardar(usuarios):
    if ALMACEN == 'exaroton':
        _exa.escribir_archivo(REMOTO_USUARIOS, json.dumps({'usuarios': usuarios}, indent=2).encode('utf-8'))
        with _candado:
            _cache.update(usuarios=json.loads(json.dumps(usuarios)), ts=time.time())
        return
    os.makedirs(os.path.dirname(ARCH_USUARIOS), exist_ok=True)
    tmp = ARCH_USUARIOS + '.tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump({'usuarios': usuarios}, f, indent=2)
    os.replace(tmp, ARCH_USUARIOS)


def _hash(clave, sal, iteraciones=ITERACIONES):
    return hashlib.pbkdf2_hmac('sha256', clave.encode('utf-8'), bytes.fromhex(sal), iteraciones).hex()


def hay_usuarios():
    return bool(_cargar())


def crear_usuario(nombre, clave, rol='colaborador'):
    if not NOMBRE_OK.match(nombre or ''):
        raise ValueError('Usuario no valido (3-32 letras, numeros, . _ -)')
    if rol not in ROLES:
        raise ValueError('Rol no valido: usa admin, colaborador o jugador')
    if len(clave) < 10:
        raise ValueError('La contrasena debe tener al menos 10 caracteres')
    usuarios = _cargar()
    sal = secrets.token_hex(16)
    usuarios[nombre] = {'sal': sal, 'iter': ITERACIONES, 'hash': _hash(clave, sal), 'rol': rol}
    _guardar(usuarios)


def listar_usuarios():
    return [{'usuario': n, 'rol': u['rol']} for n, u in sorted(_cargar().items())]


def _quedaria_sin_admin(usuarios, nombre, rol_nuevo=None):
    if usuarios.get(nombre, {}).get('rol') != 'admin' or rol_nuevo == 'admin':
        return False
    return sum(1 for u in usuarios.values() if u['rol'] == 'admin') <= 1


def _cerrar_sesiones_de(nombre):
    with _candado:
        for t in [t for t, s in _sesiones.items() if s['usuario'] == nombre]:
            del _sesiones[t]


def guardar_usuario(nombre, clave='', rol=None):
    """Crea un usuario o cambia la contrasena y/o el rol de uno existente."""
    usuarios = _cargar()
    if nombre not in usuarios:
        if not usuarios and rol != 'admin':
            raise ValueError('La primera cuenta debe ser de administrador')
        return crear_usuario(nombre, clave, rol or 'jugador')
    if rol and rol not in ROLES:
        raise ValueError('Rol no valido: usa admin, colaborador o jugador')
    if rol and _quedaria_sin_admin(usuarios, nombre, rol):
        raise ValueError('Debe quedar al menos un administrador')
    if clave:
        if len(clave) < 10:
            raise ValueError('La contrasena debe tener al menos 10 caracteres')
        sal = secrets.token_hex(16)
        usuarios[nombre].update({'sal': sal, 'iter': ITERACIONES, 'hash': _hash(clave, sal)})
    if rol:
        usuarios[nombre]['rol'] = rol
    _guardar(usuarios)
    _cerrar_sesiones_de(nombre)  # que vuelva a entrar con sus datos nuevos


def borrar_usuario(nombre):
    usuarios = _cargar()
    if nombre not in usuarios:
        raise ValueError('No existe el usuario %s' % nombre)
    if _quedaria_sin_admin(usuarios, nombre):
        raise ValueError('No puedes borrar al ultimo administrador')
    del usuarios[nombre]
    _guardar(usuarios)
    _cerrar_sesiones_de(nombre)


def iniciar_sesion(nombre, clave, ip):
    """Devuelve (token, rol) o lanza PermissionError."""
    ahora = time.time()
    with _candado:
        n, hasta = _fallos.get(ip, (0, 0))
        if hasta > ahora:
            raise PermissionError('Demasiados intentos. Espera %d minutos.' % ((hasta - ahora) // 60 + 1))
    u = _cargar().get(nombre or '')
    # Se calcula el hash aunque el usuario no exista, para no revelar cuales existen.
    sal = u['sal'] if u else '00' * 16
    calculado = _hash(clave or '', sal, u['iter'] if u else ITERACIONES)
    if not u or not hmac.compare_digest(calculado, u['hash']):
        with _candado:
            n = _fallos.get(ip, (0, 0))[0] + 1
            _fallos[ip] = (0, ahora + BLOQUEO_SEG) if n >= MAX_FALLOS else (n, 0)
        raise PermissionError('Usuario o contrasena incorrectos.')
    token = secrets.token_urlsafe(32)
    with _candado:
        _fallos.pop(ip, None)
        _sesiones[token] = {'usuario': nombre, 'rol': u['rol'], 'expira': ahora + DURACION_SESION}
    return token, u['rol']


def sesion(token):
    if not token:
        return None
    with _candado:
        s = _sesiones.get(token)
        if s and s['expira'] < time.time():
            del _sesiones[token]
            s = None
    if s and s['usuario'] not in _cargar():  # usuario borrado
        return None
    return s


def cerrar_sesion(token):
    with _candado:
        _sesiones.pop(token, None)


# Un jugador solo ve el estado del servidor y puede encenderlo (lista cerrada:
# todo lo que no este aqui le esta prohibido, incluidas rutas futuras).
SOLO_JUGADOR = {
    ('GET', '/'), ('GET', '/index.html'), ('GET', '/api/estado'), ('GET', '/api/tareas'),
    ('POST', '/api/servidor/start'),
}


def permitido(rol, metodo, ruta):
    if rol == 'admin':
        return True
    if rol == 'jugador':
        return (metodo, ruta) in SOLO_JUGADOR
    return (metodo, ruta) not in SOLO_ADMIN


def _main(args):
    if not args or args[0] not in ('crear', 'borrar', 'listar'):
        print(__doc__)
        return 1
    if args[0] == 'listar':
        usuarios = _cargar()
        if not usuarios:
            print('No hay usuarios. El panel solo funciona desde este PC.')
        for n, u in sorted(usuarios.items()):
            print('  %-20s %s' % (n, u['rol']))
        return 0
    if len(args) < 2:
        print('Falta el nombre de usuario.')
        return 1
    if args[0] == 'borrar':
        borrar_usuario(args[1])
        print('Usuario %s borrado.' % args[1])
        return 0
    rol = args[2] if len(args) > 2 else ('admin' if not hay_usuarios() else 'colaborador')
    clave = getpass.getpass('Contrasena para %s (minimo 10 caracteres): ' % args[1])
    if clave != getpass.getpass('Repitela: '):
        print('Las contrasenas no coinciden.')
        return 1
    crear_usuario(args[1], clave, rol)
    print('Usuario %s creado con rol %s.' % (args[1], rol))
    return 0


if __name__ == '__main__':
    try:
        sys.exit(_main(sys.argv[1:]))
    except ValueError as ex:
        print('Error: %s' % ex)
        sys.exit(1)
