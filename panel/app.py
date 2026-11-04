"""
Panel SERVER PANAS
------------------
Aplicacion web local que amplia lo que permite el panel de Exaroton:

- Copias de inventario por personaje (inventario, armadura, cofre de Ender,
  amuletos/Curios y contenido de mochilas), cada X segundos, guardando las
  ultimas N copias por personaje.
- Comparar dos copias y reponer exactamente lo que falta (mismo NBT).
- Consola sin restricciones, control del servidor, editor de archivos.
- Log permanente del servidor guardado aparte, por dia.

Solo escucha en 127.0.0.1 (tu PC). Abrir: http://127.0.0.1:8765
"""
import datetime
import json
import os
import re
import subprocess
import threading
import time
import traceback
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import auth
import exaroton
import items
import nbt
import datapacks as dp

BASE = os.path.dirname(os.path.abspath(__file__))
DATOS = os.path.join(BASE, 'datos')
DIR_INV = os.path.join(DATOS, 'inventarios')
DIR_LOGS = os.path.join(DATOS, 'logs')
ARCH_CONFIG = os.path.join(DATOS, 'config-panel.json')
ARCH_ERRORES = os.path.join(DATOS, 'panel-errores.log')
for d in (DATOS, DIR_INV, DIR_LOGS):
    os.makedirs(d, exist_ok=True)

CONFIG_DEFECTO = {
    'intervalo_seg': 30,        # cada cuanto el panel lee el historial del servidor
    'copias_por_jugador': 240,  # 2 horas de historial (240 x 30 s)
    'minutos_retencion': 120,   # descarta copias mas viejas que esto
    'solo_si_cambia': False,    # con 10 s guardamos todo para no perder detalle
    'modo_silencioso': True,    # los OPs no ven en el chat los comandos del panel
    'puerto': 8765,
}
NOMBRE_OK = re.compile(r'^[A-Za-z0-9_]{1,16}$')
ID_OK = re.compile(r'^[a-z0-9_.\-]+:[a-z0-9_./\-]+$')
STORAGE = 'panelsp:inv'
ARCH_STORAGE = 'world/data/command_storage_panelsp.dat'
ARCH_ESPEJO = os.path.join(DATOS, 'estado-espejo.json')


def uuid_texto(enteros):
    """UUID de Minecraft (4 enteros con signo) -> texto con guiones."""
    h = ''.join('%08x' % (x & 0xFFFFFFFF) for x in enteros)
    return '%s-%s-%s-%s-%s' % (h[0:8], h[8:12], h[12:16], h[16:20], h[20:32])


def cargar_config():
    cfg = dict(CONFIG_DEFECTO)
    if os.path.exists(ARCH_CONFIG):
        try:
            with open(ARCH_CONFIG, 'r', encoding='utf-8') as f:
                cfg.update(json.load(f))
        except Exception:
            pass
    return cfg


def guardar_config(cfg):
    with open(ARCH_CONFIG, 'w', encoding='utf-8') as f:
        json.dump(cfg, f, indent=2)


def registrar_error(msg):
    with open(ARCH_ERRORES, 'a', encoding='utf-8') as f:
        f.write('%s  %s\n' % (datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), msg))


CONFIG = cargar_config()
EXA = exaroton.Exaroton()
auth.configurar_almacen(EXA)  # en la nube los usuarios se guardan en el servidor de exaroton
ESTADO_PANEL ={'ultima_captura': None, 'ultimo_resultado': None, 'ultimo_error': None, 'silencio_aplicado': None}


# ====================== Tareas en segundo plano (generico) ======================
class GestorTareas:
    """
    Gestor UNICO de tareas pesadas que NO deben bloquear el hilo HTTP.

    Cada tipo de tarea ('spawn', 'captura', 'espejo'...) tiene su propio estado
    y su propio bloqueo: mientras una tarea de un tipo esta EN CURSO, no se puede
    lanzar otra del MISMO tipo (se responde 'ocupado'); tipos distintos pueden
    correr a la vez. El trabajo se ejecuta en un hilo de fondo y el endpoint
    responde al instante con 'en curso'. El frontend consulta el progreso en
    /api/tareas/<tipo> y pinta la barra (fases) hasta que termina.

    Fases tipicas: 'enviando' -> 'ejecutando' -> 'verificando' -> (resultado).
    resultado: None (en curso) | 'ok' | 'error'.

    Reutiliza el mismo patron que ya tenian el INSTALADOR y la restauracion del
    MOD (candado + flag activo + hilo + estado consultable), generalizado para
    cualquier accion pesada del panel.
    """
    def __init__(self):
        self._estados = {}
        self._global = threading.Lock()

    def _slot(self, tipo):
        with self._global:
            s = self._estados.get(tipo)
            if s is None:
                s = {
                    'tipo': tipo,
                    'activo': False,
                    'fase': None,
                    'etiqueta': None,
                    'resultado': None,     # None | 'ok' | 'error'
                    'mensaje': '',
                    'iniciado': None,
                    'terminado': None,
                    'candado': threading.Lock(),
                }
                self._estados[tipo] = s
            return s

    def estado(self, tipo):
        """Estado publico (sin el objeto candado) para el frontend."""
        s = self._slot(tipo)
        return {k: v for k, v in s.items() if k != 'candado'}

    def ocupado(self, tipo):
        return self._slot(tipo)['activo']

    def fase(self, tipo, fase, mensaje=None):
        """Actualiza la fase de una tarea en curso (lo usa el trabajo de fondo)."""
        s = self._slot(tipo)
        s['fase'] = fase
        if mensaje is not None:
            s['mensaje'] = mensaje

    def lanzar(self, tipo, trabajo, etiqueta=None):
        """
        Intenta arrancar una tarea de 'tipo'. 'trabajo' es una funcion sin
        argumentos que hace el trabajo pesado, devuelve un dict con al menos
        {'ok': bool, 'mensaje': str} (o lanza una excepcion si falla) y puede
        llamar a self.fase(tipo, ...) para reportar progreso.

        Devuelve de inmediato un dict:
          - si acepto: {'ok': True, 'en_curso': True, 'estado': <estado>}
          - si ya habia una del mismo tipo: {'ok': False, 'ocupado': True, ...}
        """
        s = self._slot(tipo)
        if not s['candado'].acquire(blocking=False):
            return {'ok': False, 'ocupado': True,
                    'mensaje': 'Ya hay una tarea de este tipo en curso. Espera a que termine.',
                    'estado': self.estado(tipo)}
        if s['activo']:
            s['candado'].release()
            return {'ok': False, 'ocupado': True,
                    'mensaje': 'Ya hay una tarea de este tipo en curso. Espera a que termine.',
                    'estado': self.estado(tipo)}
        s['activo'] = True
        s['fase'] = 'enviando'
        s['etiqueta'] = etiqueta
        s['resultado'] = None
        s['mensaje'] = 'Enviando...'
        s['iniciado'] = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        s['terminado'] = None

        def _correr():
            try:
                r = trabajo() or {}
                if r.get('ok', True):
                    s['resultado'] = 'ok'
                    s['fase'] = 'hecho'
                    s['mensaje'] = r.get('mensaje', 'Listo.')
                else:
                    s['resultado'] = 'error'
                    s['fase'] = 'error'
                    s['mensaje'] = r.get('mensaje', 'La tarea no se completo.')
            except Exception as ex:
                s['resultado'] = 'error'
                s['fase'] = 'error'
                s['mensaje'] = str(ex)
                registrar_error('tarea %s: %s' % (tipo, traceback.format_exc()))
            finally:
                s['activo'] = False
                s['terminado'] = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                s['candado'].release()

        threading.Thread(target=_correr, daemon=True).start()
        return {'ok': True, 'en_curso': True, 'mensaje': s['mensaje'], 'estado': self.estado(tipo)}


TAREAS = GestorTareas()


# ====================== Copias de inventario ======================
class Capturador:
    def __init__(self):
        self.candado = threading.Lock()

    def aplicar_silencio(self):
        valor = 'false' if CONFIG.get('modo_silencioso') else 'true'
        if ESTADO_PANEL['silencio_aplicado'] == valor:
            return
        EXA.comando('gamerule logAdminCommands ' + valor)
        ESTADO_PANEL['silencio_aplicado'] = valor

    def capturar(self, motivo='auto'):
        with self.candado:
            st = EXA.estado()
            if st.get('status') != 1:
                return {'ok': False, 'mensaje': 'El servidor no esta encendido.'}
            jugadores = [p for p in ((st.get('players') or {}).get('list') or []) if NOMBRE_OK.match(p)]
            if not jugadores:
                return {'ok': False, 'mensaje': 'No hay jugadores conectados.'}
            self.aplicar_silencio()

            marca = str(int(time.time() * 1000))
            EXA.comando('data remove storage %s jugadores' % STORAGE)
            for p in jugadores:
                for clave, origen in (('Inventory', 'Inventory'),
                                      ('EnderItems', 'EnderItems'),
                                      ('Curios', 'ForgeCaps."curios:inventory"')):
                    EXA.comando('data modify storage %s jugadores.%s.%s set from entity %s %s' % (STORAGE, p, clave, p, origen))
                    time.sleep(0.25)
            EXA.comando('data modify storage %s marca set value "%s"' % (STORAGE, marca))
            EXA.comando('save-all')

            contenido = None
            for _ in range(20):
                time.sleep(2.5)
                try:
                    raiz = nbt.load(EXA.leer_archivo(ARCH_STORAGE))
                except Exception:
                    continue
                c = nbt.get(raiz, ['data', 'contents', 'inv'])
                m = nbt.get(c, ['marca'])
                if m and m[1] == marca:
                    contenido = c
                    break
            if contenido is None:
                return {'ok': False, 'mensaje': 'El servidor no guardo la copia a tiempo. Se reintenta en la proxima vuelta.'}

            ahora = datetime.datetime.now()
            guardados, sin_cambios = [], []
            for p in jugadores:
                nodo = nbt.get(contenido, ['jugadores', p])
                if nodo is None:
                    continue
                lista = items.extraer(nodo)
                if CONFIG.get('solo_si_cambia') and motivo == 'auto':
                    ultima = self.ultima_copia(p)
                    if ultima is not None and items.huella(ultima['items']) == items.huella(lista):
                        sin_cambios.append(p)
                        continue
                self.guardar(p, ahora, lista, nbt.snbt(nodo), motivo)
                guardados.append(p)
            ESTADO_PANEL['ultima_captura'] = ahora.strftime('%Y-%m-%d %H:%M:%S')
            return {'ok': True, 'guardados': guardados, 'sin_cambios': sin_cambios,
                    'mensaje': 'Copia tomada: %d guardadas, %d sin cambios.' % (len(guardados), len(sin_cambios))}

    @staticmethod
    def carpeta(jugador):
        d = os.path.join(DIR_INV, jugador)
        os.makedirs(d, exist_ok=True)
        return d

    # ---------- historial guardado por el servidor (datapack HistorialInventario) ----------
    def _estado_espejo(self):
        if os.path.exists(ARCH_ESPEJO):
            with open(ARCH_ESPEJO, 'r', encoding='utf-8') as f:
                return json.load(f)
        return {'ultimo_gt': {}, 'desfase': None, 'uuids': {}}

    def _guardar_estado_espejo(self, e):
        with open(ARCH_ESPEJO, 'w', encoding='utf-8') as f:
            json.dump(e, f, indent=1)

    def _nombres(self, e, uuids_necesarios):
        faltan = [u for u in uuids_necesarios if u not in e['uuids']]
        if faltan:
            try:
                cache = json.loads(EXA.leer_archivo('usercache.json').decode('utf-8'))
                for x in cache:
                    e['uuids'][x['uuid']] = x['name']
            except Exception:
                registrar_error('usercache: %s' % traceback.format_exc())
        return e['uuids']

    def _gametime_actual(self):
        """Lee el gametime del servidor (para poner fecha real a las copias)."""
        antes = set(EXA.logs().splitlines())
        EXA.comando('time query gametime')
        for _ in range(4):
            time.sleep(1.2)
            for l in EXA.logs().splitlines():
                if l not in antes:
                    m = re.search(r'The time is (\d+)', l)
                    if m:
                        return int(m.group(1))
        return None

    def espejo(self):
        """Descarga el historial que guarda el servidor y lo separa por personaje."""
        with self.candado:
            e = self._estado_espejo()
            online = EXA.estado().get('status') == 1
            if online:
                self.aplicar_silencio()
                gt = self._gametime_actual()
                if gt is not None:
                    e['desfase'] = time.time() - gt / 20.0
            try:
                raiz = nbt.load(EXA.leer_archivo(ARCH_STORAGE))
            except exaroton.ErrorApi:
                return {'ok': False, 'mensaje': 'El servidor todavia no tiene historial guardado.'}
            copias = nbt.get(raiz, ['data', 'contents', 'hist', 'copias'])
            if not copias or copias[0] != nbt.LIST or not copias[1][1]:
                return {'ok': False, 'mensaje': 'El historial del servidor esta vacio (se llena cada 2 min con jugadores conectados).'}

            fotos = []
            for c in copias[1][1]:
                g = nbt.get(c, ['gt'])
                jug = nbt.get(c, ['jugadores'])
                if g and jug and jug[0] == nbt.LIST:
                    fotos.append((int(g[1]), jug[1][1]))
            fotos.sort(key=lambda x: x[0])
            uuids = set()
            for _, lista in fotos:
                for j in lista:
                    u = nbt.get(j, ['UUID'])
                    if u:
                        uuids.add(uuid_texto(u[1]))
            nombres = self._nombres(e, uuids)

            nuevas = 0
            for g, lista in fotos:
                for j in lista:
                    u = nbt.get(j, ['UUID'])
                    nombre = nombres.get(uuid_texto(u[1])) if u else None
                    if not nombre or not NOMBRE_OK.match(nombre):
                        continue
                    if g <= int(e['ultimo_gt'].get(nombre, -1)):
                        continue
                    e['ultimo_gt'][nombre] = g
                    lista_items = items.extraer(j)
                    if CONFIG.get('solo_si_cambia'):
                        ultima = self.ultima_copia(nombre)
                        if ultima is not None and items.huella(ultima['items']) == items.huella(lista_items):
                            continue
                    if e.get('desfase') is not None:
                        fecha = datetime.datetime.fromtimestamp(e['desfase'] + g / 20.0)
                    else:
                        fecha = datetime.datetime.now()
                    self.guardar(nombre, fecha, lista_items, nbt.snbt(j), 'servidor', gametime=g)
                    nuevas += 1
            self._guardar_estado_espejo(e)
            ESTADO_PANEL['ultima_captura'] = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            return {'ok': True, 'mensaje': 'Historial del servidor leido: %d fotos, %d copias nuevas guardadas.' % (len(fotos), nuevas)}

    def guardar(self, jugador, fecha, lista, snbt_completo, motivo, gametime=None):
        ident = fecha.strftime('%Y%m%d-%H%M%S')
        while os.path.exists(os.path.join(self.carpeta(jugador), ident + '.json')):
            fecha = fecha + datetime.timedelta(seconds=1)
            ident = fecha.strftime('%Y%m%d-%H%M%S')
        datos = {'jugador': jugador, 'id': ident, 'fecha': fecha.strftime('%Y-%m-%d %H:%M:%S'),
                 'motivo': motivo, 'gametime': gametime, 'items': lista, 'snbt': snbt_completo}
        with open(os.path.join(self.carpeta(jugador), ident + '.json'), 'w', encoding='utf-8') as f:
            json.dump(datos, f, ensure_ascii=False)
        self.podar(jugador)

    def podar(self, jugador):
        """Deja como maximo N copias y descarta las mas viejas que los minutos de retencion."""
        carpeta = self.carpeta(jugador)
        archivos = sorted(x for x in os.listdir(carpeta) if x.endswith('.json'))
        # por cantidad
        sobran = len(archivos) - int(CONFIG.get('copias_por_jugador', 30))
        for viejo in archivos[:max(0, sobran)]:
            os.remove(os.path.join(carpeta, viejo))
            archivos.remove(viejo)
        # por tiempo (segun la fecha del nombre AAAAMMDD-HHMMSS)
        limite = datetime.datetime.now() - datetime.timedelta(minutes=int(CONFIG.get('minutos_retencion', 5)))
        for x in list(archivos):
            try:
                f = datetime.datetime.strptime(x[:-5], '%Y%m%d-%H%M%S')
            except ValueError:
                continue
            if f < limite and len(archivos) > 2:
                os.remove(os.path.join(carpeta, x))
                archivos.remove(x)

    def lista_copias(self, jugador):
        d = self.carpeta(jugador)
        salida = []
        for x in sorted((x for x in os.listdir(d) if x.endswith('.json')), reverse=True):
            with open(os.path.join(d, x), 'r', encoding='utf-8') as f:
                c = json.load(f)
            salida.append({'id': c['id'], 'fecha': c['fecha'], 'motivo': c.get('motivo', ''),
                           'items': sum(i['cantidad'] for i in c['items'])})
        return salida

    def leer_copia(self, jugador, ident):
        if not re.match(r'^\d{8}-\d{6}$', ident or ''):
            raise ValueError('Copia no valida')
        with open(os.path.join(self.carpeta(jugador), ident + '.json'), 'r', encoding='utf-8') as f:
            return json.load(f)

    def ultima_copia(self, jugador):
        d = self.carpeta(jugador)
        archivos = sorted(x for x in os.listdir(d) if x.endswith('.json'))
        if not archivos:
            return None
        with open(os.path.join(d, archivos[-1]), 'r', encoding='utf-8') as f:
            return json.load(f)

    def bucle(self):
        while True:
            try:
                r = self.espejo()
                ESTADO_PANEL['ultimo_resultado'] = r.get('mensaje')
                ESTADO_PANEL['ultimo_error'] = None
            except Exception as ex:
                ESTADO_PANEL['ultimo_error'] = str(ex)
                registrar_error('captura: %s' % traceback.format_exc())
            time.sleep(max(5, int(CONFIG.get('intervalo_seg', 10))))


CAPTURADOR = Capturador()


# ====================== Instalacion del mod (una sola vez, controlada) ======================
RAIZ_SP = os.path.normpath(os.path.join(BASE, '..'))
DIR_MOD = os.path.join(RAIZ_SP, 'mods-propios', 'InvBackupMod')
JAR_LOCAL = os.path.join(DIR_MOD, 'build', 'libs', 'invbackup-forge-1.20.1-1.0.0.jar')
JAR_REMOTO = 'mods/invbackup-forge-1.20.1-1.0.0.jar'
GRADLE_BAT = os.path.join(os.path.expanduser('~'), 'gradle-dist', 'gradle-8.1.1', 'bin', 'gradle.bat')
# JDK 17 (Temurin) instalado para compilar Forge 1.20.1
_JDK_BASE = r'C:\Program Files\Eclipse Adoptium'


def _buscar_jdk17():
    try:
        for n in sorted(os.listdir(_JDK_BASE)):
            if n.startswith('jdk-17') and os.path.exists(os.path.join(_JDK_BASE, n, 'bin', 'java.exe')):
                return os.path.join(_JDK_BASE, n)
    except Exception:
        pass
    return None


class InstaladorMod:
    """
    Instala el mod InvBackup en el servidor de forma controlada y UNICA:
    compila el .jar si hace falta, lo sube a mods/, reinicia el servidor y
    confirma en los logs que Forge cargo el mod. Solo puede correr una
    instalacion a la vez; mientras esta activa, el boton queda bloqueado.
    """
    def __init__(self):
        self.candado = threading.Lock()
        self.activo = False
        self.pasos = []            # historial legible de lo que va pasando
        self.resultado = None      # 'ok' | 'error' | None (en curso)
        self.mensaje = ''
        self.iniciado = None
        self.terminado = None
        # Cache de "ya esta el .jar en el servidor". Comprobarlo hace un
        # round-trip a la API (info_archivo); como /api/estado se pide cada pocos
        # segundos, lo cacheamos ~60 s (el .jar casi nunca cambia).
        self._jar_cache = None
        self._jar_cache_ts = 0.0
        self._jar_ttl = 60.0

    def estado(self):
        return {
            'activo': self.activo,
            'pasos': list(self.pasos),
            'resultado': self.resultado,
            'mensaje': self.mensaje,
            'iniciado': self.iniciado,
            'terminado': self.terminado,
            'ya_instalado': self._jar_en_servidor_cache(),
        }

    def _jar_en_servidor_cache(self):
        ahora = time.time()
        if self._jar_cache is not None and (ahora - self._jar_cache_ts) < self._jar_ttl:
            return self._jar_cache
        self._jar_cache = self._jar_en_servidor()
        self._jar_cache_ts = time.time()
        return self._jar_cache

    def _paso(self, texto):
        self.pasos.append('%s  %s' % (datetime.datetime.now().strftime('%H:%M:%S'), texto))

    def _jar_en_servidor(self):
        try:
            EXA.info_archivo(JAR_REMOTO)
            return True
        except Exception:
            return False

    def lanzar(self):
        """Arranca la instalacion en segundo plano. Devuelve si acepto o no la orden."""
        # Candado NO bloqueante: si ya hay una en curso, se rechaza de inmediato.
        if not self.candado.acquire(blocking=False):
            return {'ok': False, 'mensaje': 'Ya hay una instalacion en curso. Espera a que termine.', 'estado': self.estado()}
        if self.activo:
            self.candado.release()
            return {'ok': False, 'mensaje': 'Ya hay una instalacion en curso. Espera a que termine.', 'estado': self.estado()}
        self.activo = True
        self.resultado = None
        self.mensaje = 'Instalacion iniciada.'
        self.pasos = []
        self.iniciado = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        self.terminado = None
        threading.Thread(target=self._correr, daemon=True).start()
        return {'ok': True, 'mensaje': 'Instalacion iniciada.', 'estado': self.estado()}

    def _correr(self):
        try:
            self._ejecutar()
            if self.resultado is None:
                self.resultado = 'ok'
                self.mensaje = 'Mod instalado y verificado. Ya esta funcionando en el servidor.'
        except Exception as ex:
            self.resultado = 'error'
            self.mensaje = str(ex)
            self._paso('ERROR: %s' % ex)
            registrar_error('instalar-mod: %s' % traceback.format_exc())
        finally:
            self.activo = False
            self.terminado = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            self._jar_cache = None  # fuerza recomprobar el .jar tras instalar
            self.candado.release()

    def _ejecutar(self):
        # 1) Compilar el .jar si no existe
        if not os.path.exists(JAR_LOCAL):
            self._paso('No existe el .jar compilado. Compilando (puede tardar varios minutos)...')
            self._compilar()
            if not os.path.exists(JAR_LOCAL):
                raise RuntimeError('La compilacion termino pero no se genero el .jar.')
            self._paso('Compilacion lista.')
        else:
            self._paso('Usando el .jar ya compilado.')

        # 2) Subir el .jar a mods/
        self._paso('Subiendo el mod a la carpeta mods/ del servidor...')
        with open(JAR_LOCAL, 'rb') as f:
            datos = f.read()
        EXA.escribir_archivo(JAR_REMOTO, datos)
        if not self._jar_en_servidor():
            raise RuntimeError('El .jar no aparece en el servidor despues de subirlo.')
        self._paso('Mod subido (%d KB).' % (len(datos) // 1024))

        # 3) Reiniciar el servidor para que Forge cargue el mod
        st = EXA.estado()
        online = st.get('status') == 1
        lista = (st.get('players') or {}).get('list') or []
        if online:
            if lista:
                self._paso('Avisando a los jugadores del reinicio...')
                try:
                    EXA.comando('say [Panel] Reinicio para instalar respaldo de inventario. Volvemos en ~1 minuto.')
                except Exception:
                    pass
                try:
                    EXA.comando('save-all')
                    time.sleep(3)
                except Exception:
                    pass
            self._paso('Reiniciando el servidor...')
            EXA.accion('restart')
        else:
            self._paso('El servidor estaba apagado. Encendiendolo...')
            EXA.accion('start')

        # 4) Esperar a que vuelva a estar online
        self._paso('Esperando a que el servidor vuelva a encender...')
        if not self._esperar_online(timeout=420):
            raise RuntimeError('El servidor no volvio a encender a tiempo. Revisa el panel de Exaroton.')
        self._paso('Servidor encendido de nuevo.')

        # 5) Verificar en los logs que el mod cargo
        self._paso('Verificando en los logs que el mod cargo...')
        if self._verificar_logs(timeout=120):
            self._paso('Confirmado: Forge cargo el mod InvBackup.')
        else:
            # No es fatal: el mod esta en mods/ y el server encendio. Avisamos.
            self._paso('No encontre la linea del mod en los logs visibles, pero el .jar esta en mods/ y el servidor encendio. Si los jugadores pueden entrar, el mod esta activo (es server-side).')
        self.resultado = 'ok'
        self.mensaje = 'Mod instalado. El servidor esta encendido con el respaldo de inventario activo.'

    def _compilar(self):
        jdk = _buscar_jdk17()
        if not jdk:
            raise RuntimeError('No encontre el JDK 17 en %s. Instalalo para poder compilar.' % _JDK_BASE)
        if not os.path.exists(GRADLE_BAT):
            raise RuntimeError('No encontre Gradle en %s.' % GRADLE_BAT)
        env = dict(os.environ)
        env['JAVA_HOME'] = jdk
        env['PATH'] = os.path.join(jdk, 'bin') + os.pathsep + env.get('PATH', '')
        proc = subprocess.run(
            [GRADLE_BAT, 'build', '--no-daemon', '--console=plain'],
            cwd=DIR_MOD, env=env, capture_output=True, text=True, timeout=1800,
        )
        if proc.returncode != 0:
            cola = (proc.stdout or '')[-800:] + '\n' + (proc.stderr or '')[-800:]
            raise RuntimeError('Fallo la compilacion:\n%s' % cola.strip())

    def _esperar_online(self, timeout=420):
        fin = time.time() + timeout
        while time.time() < fin:
            try:
                if EXA.estado().get('status') == 1:
                    return True
            except Exception:
                pass
            time.sleep(6)
        return False

    def _verificar_logs(self, timeout=120):
        fin = time.time() + timeout
        patrones = ('invbackup', 'InvBackup', 'Server Panas Inventory Backup', 'InvBackupMod')
        while time.time() < fin:
            try:
                log = EXA.logs()
                if any(p in log for p in patrones):
                    return True
            except Exception:
                pass
            time.sleep(6)
        return False


INSTALADOR = InstaladorMod()


# ====================== Restaurar con el MOD (lo que guarda al morir/salir) ======================
CARPETA_MOD = 'world/inventory_backups'


class GestorMod:
    """
    Une las dos piezas:
      - CATALOGO: las copias que ya guarda el panel desde el datapack
        (datos/inventarios/<jugador>/*.json), que tienen Inventory + EnderItems
        + Curios y mucho historial.
      - MOTOR: el MOD InvBackup, que restaura en vivo con /invrestore.

    Al restaurar una copia del datapack, se convierte esa copia al formato de
    archivo que lee el mod (world/inventory_backups/<UUID>/<id>.nbt) y se llama
    al comando del mod. La restauracion es UNICA: mientras una corre, los
    botones quedan bloqueados y no se puede lanzar otra.
    """
    def __init__(self):
        self.candado = threading.Lock()
        self.activo = False
        self.resultado = None     # 'ok' | 'error' | None
        self.mensaje = ''
        self.jugador = None
        self.archivo = None
        self._cache_uuid = {}

    def estado(self):
        return {'activo': self.activo, 'resultado': self.resultado, 'mensaje': self.mensaje,
                'jugador': self.jugador, 'archivo': self.archivo}

    def _uuid_de_nombre(self, nombre):
        if not self._cache_uuid:
            try:
                cache = json.loads(EXA.leer_archivo('usercache.json').decode('utf-8'))
                for x in cache:
                    self._cache_uuid[x['name']] = x['uuid']
            except Exception:
                pass
        return self._cache_uuid.get(nombre)

    def jugadores(self):
        """Jugadores con copias en el catalogo del datapack (lo que guarda el panel)."""
        salida = []
        for p in sorted(os.listdir(DIR_INV)):
            d = os.path.join(DIR_INV, p)
            if os.path.isdir(d):
                n = len([x for x in os.listdir(d) if x.endswith('.json')])
                if n:
                    salida.append({'jugador': p, 'respaldos': n})
        salida.sort(key=lambda x: x['jugador'].lower())
        return salida

    def respaldos(self, jugador):
        """Lista las copias del datapack de ese jugador, del mas nuevo al mas viejo."""
        try:
            lista_copias = CAPTURADOR.lista_copias(jugador)  # ya vienen ordenadas nuevo->viejo
        except Exception:
            lista_copias = []
        if not lista_copias:
            return {'ok': False, 'mensaje': 'Sin copias para %s.' % jugador, 'lista': []}
        lista = [{'archivo': c['id'], 'fecha': c['fecha'], 'items': c.get('items', 0),
                  'motivo': c.get('motivo', '')} for c in lista_copias]
        return {'ok': True, 'lista': lista}

    def restaurar(self, jugador, archivo):
        if not NOMBRE_OK.match(jugador or ''):
            return {'ok': False, 'mensaje': 'Jugador no valido.'}
        if not re.match(r'^\d{8}-\d{6}$', archivo or ''):
            return {'ok': False, 'mensaje': 'Copia no valida.'}
        if not self.candado.acquire(blocking=False):
            return {'ok': False, 'mensaje': 'Ya hay una restauracion en curso. Espera a que termine.', 'estado': self.estado()}
        if self.activo:
            self.candado.release()
            return {'ok': False, 'mensaje': 'Ya hay una restauracion en curso. Espera a que termine.', 'estado': self.estado()}
        self.activo = True
        self.resultado = None
        self.jugador = jugador
        self.archivo = archivo
        self.mensaje = 'Restaurando...'
        threading.Thread(target=self._correr, args=(jugador, archivo), daemon=True).start()
        return {'ok': True, 'mensaje': 'Restauracion iniciada.', 'estado': self.estado()}

    def _construir_nbt_mod(self, jugador, copia):
        """
        Convierte una copia del datapack (su SNBT con Inventory/EnderItems/Curios)
        al CompoundTag que el mod espera y lo devuelve como bytes NBT comprimidos.
        """
        nodo = nbt.parse_snbt(copia['snbt'])  # (COMPOUND, {...}) del jugador
        if nodo[0] != nbt.COMPOUND:
            raise RuntimeError('La copia no tiene un formato valido.')
        orig = nodo[1]
        raiz = {}
        raiz['player'] = (nbt.STRING, jugador)
        uuid = self._uuid_de_nombre(jugador) or ''
        raiz['uuid'] = (nbt.STRING, uuid)
        raiz['motivo'] = (nbt.STRING, 'datapack:' + copia.get('motivo', ''))
        raiz['epoch'] = (nbt.LONG, int(time.time() * 1000))
        # Inventory (lo que el mod carga en inventario+armadura+offhand)
        if 'Inventory' in orig:
            raiz['Inventory'] = orig['Inventory']
        else:
            raiz['Inventory'] = (nbt.LIST, (nbt.END, []))
        raiz['SelectedSlot'] = (nbt.INT, 0)
        # Cofre de Ender
        if 'EnderItems' in orig:
            raiz['EnderItems'] = orig['EnderItems']
        # Curios -> ForgeCaps."curios:inventory". El datapack guardo el nodo tal
        # cual estaba en ForgeCaps."curios:inventory", bajo la clave Curios.
        if 'Curios' in orig:
            forgecaps = (nbt.COMPOUND, {'curios:inventory': orig['Curios']})
            raiz['ForgeCaps'] = forgecaps
        return nbt.dump((nbt.COMPOUND, raiz), nombre_raiz='', comprimir=True)

    def _correr(self, jugador, archivo):
        try:
            st = EXA.estado()
            if st.get('status') != 1:
                raise RuntimeError('El servidor no esta encendido.')
            conectados = (st.get('players') or {}).get('list') or []
            if jugador not in conectados:
                raise RuntimeError('%s no esta conectado. El mod restaura sobre el jugador en el juego, asi que debe estar dentro. Conectados: %s'
                                   % (jugador, ', '.join(conectados) if conectados else 'nadie'))
            uuid = self._uuid_de_nombre(jugador)
            if not uuid:
                raise RuntimeError('No encuentro el UUID de %s en usercache.json.' % jugador)

            # 1) Leer la copia del datapack y convertirla al .nbt del mod.
            copia = CAPTURADOR.leer_copia(jugador, archivo)
            if not copia.get('snbt'):
                raise RuntimeError('Esa copia no guardo el detalle completo (snbt). Elige otra.')
            datos_nbt = self._construir_nbt_mod(jugador, copia)

            # 2) Subir ese archivo a la carpeta del mod, con un nombre reconocible.
            nombre_mod = 'panel_%s' % archivo  # p.ej. panel_20261024-170404
            ruta_remota = '%s/%s/%s.nbt' % (CARPETA_MOD, uuid, nombre_mod)
            EXA.escribir_archivo(ruta_remota, datos_nbt)

            # 3) Pedir al mod que lo restaure (motor en vivo).
            antes = set(EXA.logs().splitlines())
            EXA.comando('invrestore %s %s' % (jugador, nombre_mod))
            time.sleep(2.5)
            salida = [l for l in EXA.logs().splitlines() if l not in antes]
            exito = any(('restaurado' in l or 'restored' in l) for l in salida)
            fallo = any(('No hay respaldos' in l or 'No existe' in l or 'Error al restaurar' in l) for l in salida)
            if fallo and not exito:
                detalle = next((l.split(']')[-1].strip() for l in salida if 'Error' in l or 'No ' in l), 'el mod rechazo la restauracion')
                raise RuntimeError(detalle)
            self.resultado = 'ok'
            self.mensaje = 'Inventario de %s restaurado desde la copia del %s.' % (jugador, copia['fecha'])
        except Exception as ex:
            self.resultado = 'error'
            self.mensaje = str(ex)
            registrar_error('mod-restaurar: %s' % traceback.format_exc())
        finally:
            self.activo = False
            self.candado.release()


MOD = GestorMod()


# ====================== Log permanente ======================
EVENTOS = [
    (re.compile(r'logged in with entity id .* at \('), 'CONEXION'),
    (re.compile(r': [A-Za-z0-9_]+ joined the game$'), 'ENTRO'),
    (re.compile(r': [A-Za-z0-9_]+ left the game$'), 'SALIO'),
    (re.compile(r'was slain by|was killed|was shot by|was blown up|was doomed to fall|drowned|fell from|fell off|hit the ground too hard|tried to swim in lava|starved to death|suffocated|withered away|went off with a bang|walked into|burned to death|was burnt|froze to death|was pricked|was impaled|was squished|was fireballed|was stung'), 'MUERTE'),
    (re.compile(r'has made the advancement|has reached the goal|has completed the challenge'), 'LOGRO'),
    (re.compile(r'Gave .* to [A-Za-z0-9_]+'), 'ITEM-DADO'),
    (re.compile(r'Teleported [A-Za-z0-9_]+ to|teleported to the location'), 'TELEPORT'),
    (re.compile(r'The player [A-Za-z0-9_]+ has successfully been saved'), 'TUMBA'),
    (re.compile(r'<[A-Za-z0-9_]+> '), 'CHAT'),
]


class GuardaLogs:
    def __init__(self):
        self.vistas = set()
        self.fecha = None

    def _preparar_dia(self):
        hoy = datetime.date.today().isoformat()
        if hoy != self.fecha:
            self.fecha = hoy
            self.vistas = set()
            arch = os.path.join(DIR_LOGS, hoy + '-completo.log')
            if os.path.exists(arch):
                with open(arch, 'r', encoding='utf-8', errors='replace') as f:
                    self.vistas.update(l.rstrip('\n') for l in f)
        return self.fecha

    def vuelta(self):
        dia = self._preparar_dia()
        nuevas, eventos = [], []
        for l in EXA.logs().splitlines():
            if not l or l in self.vistas:
                continue
            self.vistas.add(l)
            nuevas.append(l)
            for patron, etiqueta in EVENTOS:
                if patron.search(l):
                    eventos.append('%-10s %s' % (etiqueta, l))
                    break
        if nuevas:
            with open(os.path.join(DIR_LOGS, dia + '-completo.log'), 'a', encoding='utf-8') as f:
                f.write('\n'.join(nuevas) + '\n')
        if eventos:
            with open(os.path.join(DIR_LOGS, dia + '-eventos.log'), 'a', encoding='utf-8') as f:
                f.write('\n'.join(eventos) + '\n')

    def bucle(self):
        while True:
            try:
                self.vuelta()
            except Exception:
                registrar_error('logs: %s' % traceback.format_exc())
            time.sleep(5)


GUARDA_LOGS = GuardaLogs()


# ====================== Acciones ======================
def ejecutar_comando(texto):
    texto = (texto or '').strip().lstrip('/')
    if not texto:
        raise ValueError('Comando vacio')
    antes = set(EXA.logs().splitlines())
    EXA.comando(texto)
    despues = []
    # El log de exaroton tarda un poco en reflejar la respuesta
    for espera in (1.8, 2.5, 3):
        time.sleep(espera)
        try:
            despues = [l for l in EXA.logs().splitlines() if l not in antes]
        except exaroton.ErrorApi:
            continue
        if despues:
            break
    return {'enviado': texto, 'salida': despues[-40:]}


def reponer(jugador, lista):
    if not NOMBRE_OK.match(jugador or ''):
        raise ValueError('Jugador no valido')
    st = EXA.estado()
    conectados = (st.get('players') or {}).get('list') or []
    if jugador not in conectados:
        raise ValueError('%s NO esta conectado al servidor. Los items solo se pueden entregar a jugadores dentro del juego. Pidele que entre y vuelve a intentar. (Conectados ahora: %s)'
                         % (jugador, ', '.join(conectados) if conectados else 'nadie'))
    antes = set(EXA.logs().splitlines())
    enviados = []
    for it in lista:
        iid = it.get('id', '')
        cant = int(it.get('cantidad', 1))
        if not ID_OK.match(iid) or cant < 1:
            continue
        cmd = 'give %s %s%s %d' % (jugador, iid, it.get('tag') or '', cant)
        EXA.comando(cmd)
        enviados.append(iid)
        time.sleep(0.35)
    time.sleep(1.8)
    salida = [l for l in EXA.logs().splitlines() if l not in antes]
    ok = [l for l in salida if 'Gave ' in l]
    errores = [l for l in salida if '<--[HERE]' in l or 'Unknown item' in l or 'Expected' in l]
    return {'enviados': len(enviados), 'confirmados': len(ok), 'errores': errores, 'salida': salida[-40:]}


def jugadores_con_copias():
    salida = []
    for p in sorted(os.listdir(DIR_INV)):
        d = os.path.join(DIR_INV, p)
        if os.path.isdir(d):
            arch = sorted(x for x in os.listdir(d) if x.endswith('.json'))
            salida.append({'jugador': p, 'copias': len(arch), 'ultima': arch[-1][:-5] if arch else None})
    return salida


def leer_log_local(fecha, tipo, filtro, n):
    if not re.match(r'^\d{4}-\d{2}-\d{2}$', fecha or ''):
        fecha = datetime.date.today().isoformat()
    tipo = 'eventos' if tipo == 'eventos' else 'completo'
    arch = os.path.join(DIR_LOGS, '%s-%s.log' % (fecha, tipo))
    if not os.path.exists(arch):
        return {'fecha': fecha, 'lineas': [], 'fechas': fechas_log()}
    with open(arch, 'r', encoding='utf-8', errors='replace') as f:
        lineas = f.read().splitlines()
    if filtro:
        fl = filtro.lower()
        lineas = [l for l in lineas if fl in l.lower()]
    return {'fecha': fecha, 'lineas': lineas[-n:], 'total': len(lineas), 'fechas': fechas_log()}


def fechas_log():
    return sorted({x[:10] for x in os.listdir(DIR_LOGS) if x.endswith('.log')}, reverse=True)


# ====================== Mods: Misiones Diarias y Tienda/Economia ======================
# Lectura SOLO de datos. Las acciones (reset, reload) se hacen por consola, como
# el resto del panel. No se escribe nunca directamente a los .dat de los mods.

# Rutas de los SavedData (world/data/<NOMBRE>.dat). NOMBRE sale de cada mod:
#   DailyQuests: QuestData.NOMBRE = MOD_ID = "dailyquests"
#   ServerEco:   BalanceData/MarketData/AuctionData/DiscoveryData = "servereco_*"
ARCH_MISIONES = 'world/data/dailyquests.dat'
ARCH_ECO_BALANCE = 'world/data/servereco_balance.dat'
ARCH_ECO_MARKET = 'world/data/servereco_market.dat'
ARCH_ECO_AUCTION = 'world/data/servereco_auction.dat'
ARCH_ECO_VALORES = 'config/servereco/valores.json'

# Pool de misiones de DailyQuests (espejo de QuestRegistry.java: id -> titulo,
# descripcion, cantidad objetivo y nivel). Se usa para enriquecer lo que guarda
# el .dat (que solo tiene id/progreso/reclamada/personalizada). Si el mod anade
# misiones nuevas, las que falten aqui se muestran con su id crudo sin romper.
#
# Los ids y textos salen de QuestRegistry.POOL_FACIL / POOL_MEDIO / POOL_DIFICIL.
# Las personalizadas que asigna "/misiones dar <jugador> <nivel>" usan estos
# mismos ids (se eligen al azar del pool del nivel), por eso tambien aparecen.
MISIONES_POOL = {
    # ---- FACIL ----
    'facil_minar_piedra':      {'titulo': '[Facil] Primeros golpes',      'descripcion': 'Rompe 10 bloques de piedra',       'cantidad': 10, 'nivel': 'facil'},
    'facil_minar_carbon':      {'titulo': '[Facil] Un poco de carbon',    'descripcion': 'Rompe 8 minerales de carbon',      'cantidad': 8,  'nivel': 'facil'},
    'facil_matar_zombis':      {'titulo': '[Facil] Limpieza nocturna',    'descripcion': 'Elimina 5 zombis',                 'cantidad': 5,  'nivel': 'facil'},
    'facil_matar_aranas':      {'titulo': '[Facil] Telaranas fuera',      'descripcion': 'Elimina 5 aranas',                 'cantidad': 5,  'nivel': 'facil'},
    'facil_entregar_trigo':    {'titulo': '[Facil] Pequena cosecha',      'descripcion': 'Entrega 8 de trigo',               'cantidad': 8,  'nivel': 'facil'},
    'facil_entregar_madera':   {'titulo': '[Facil] Un poco de lena',      'descripcion': 'Entrega 8 troncos de roble',       'cantidad': 8,  'nivel': 'facil'},
    # ---- MEDIO ----
    'medio_minar_hierro':      {'titulo': '[Medio] Vena de hierro',       'descripcion': 'Rompe 32 minerales de hierro',     'cantidad': 32, 'nivel': 'medio'},
    'medio_minar_oro':         {'titulo': '[Medio] Fiebre del oro',       'descripcion': 'Rompe 24 minerales de oro',        'cantidad': 24, 'nivel': 'medio'},
    'medio_matar_esqueletos':  {'titulo': '[Medio] Rompehuesos',          'descripcion': 'Elimina 20 esqueletos',            'cantidad': 20, 'nivel': 'medio'},
    'medio_matar_zombis':      {'titulo': '[Medio] Horda contenida',      'descripcion': 'Elimina 25 zombis',                'cantidad': 25, 'nivel': 'medio'},
    'medio_entregar_cuero':    {'titulo': '[Medio] Curtidor mayor',       'descripcion': 'Entrega 32 de cuero',              'cantidad': 32, 'nivel': 'medio'},
    'medio_entregar_hierro':   {'titulo': '[Medio] Fundidor',            'descripcion': 'Entrega 32 lingotes de hierro',    'cantidad': 32, 'nivel': 'medio'},
    # ---- DIFICIL ----
    'dificil_minar_diamante':  {'titulo': '[Dificil] Veta legendaria',    'descripcion': 'Rompe 16 minerales de diamante',   'cantidad': 16, 'nivel': 'dificil'},
    'dificil_minar_hierro':    {'titulo': '[Dificil] Mina industrial',    'descripcion': 'Rompe 128 minerales de hierro',    'cantidad': 128,'nivel': 'dificil'},
    'dificil_matar_creepers':  {'titulo': '[Dificil] Cazador de creepers','descripcion': 'Elimina 5 creepers',               'cantidad': 5,  'nivel': 'dificil'},
    'dificil_matar_esqueletos':{'titulo': '[Dificil] Masacre de huesos',  'descripcion': 'Elimina 40 esqueletos',            'cantidad': 40, 'nivel': 'dificil'},
    'dificil_entregar_diamante':{'titulo': '[Dificil] Tributo de diamantes','descripcion': 'Entrega 64 diamantes',           'cantidad': 64, 'nivel': 'dificil'},
    'dificil_entregar_oro':    {'titulo': '[Dificil] Cofre del tesoro',   'descripcion': 'Entrega 64 lingotes de oro',       'cantidad': 64, 'nivel': 'dificil'},
}


def _mapa_uuid_nombre():
    """UUID (texto con guiones) -> nombre, leyendo usercache.json del servidor."""
    mapa = {}
    try:
        cache = json.loads(EXA.leer_archivo('usercache.json').decode('utf-8'))
        for x in cache:
            if x.get('uuid') and x.get('name'):
                mapa[x['uuid'].lower()] = x['name']
    except Exception:
        pass
    return mapa


# ---- Consola: jugadores, waystones y teletransporte ----
_cache_consola = {}
RUTA_WAYSTONES = ['ForgeData', 'PlayerPersisted', 'BalmData', 'WaystonesData', 'Waystones']
MODOS_JUEGO = {0: 'Supervivencia', 1: 'Creativo', 2: 'Aventura', 3: 'Espectador'}


def _cacheado(clave, segundos, leer):
    guardado = _cache_consola.get(clave)
    if guardado and time.time() - guardado[0] < segundos:
        return guardado[1]
    valor = leer()
    _cache_consola[clave] = (time.time(), valor)
    return valor


def _leer_jugadores_guardados():
    """Lee world/playerdata: posicion, dimension y waystones descubiertas de cada jugador."""
    mapa = _mapa_uuid_nombre()
    lista = []
    for h in EXA.info_archivo('world/playerdata').get('children') or []:
        archivo = h.get('name', '')
        nombre = mapa.get(archivo[:-4].lower())
        if not archivo.endswith('.dat') or not nombre:
            continue
        try:
            raiz = nbt.load(EXA.leer_archivo('world/playerdata/' + archivo))
        except (exaroton.ErrorApi, ValueError, OSError):
            continue
        pos = nbt.get(raiz, ['Pos'])
        dim = nbt.get(raiz, ['Dimension'])
        vida = nbt.get(raiz, ['Health'])
        modo = nbt.get(raiz, ['playerGameType'])
        ids = nbt.get(raiz, RUTA_WAYSTONES)
        lista.append({
            'nombre': nombre,
            'pos': [round(v) for _, v in pos[1][1]] if pos else None,
            'dim': dim[1] if dim else None,
            'vida': round(vida[1], 1) if vida else None,
            'modo': MODOS_JUEGO.get(modo[1]) if modo else None,
            'waystones': [v for _, v in ids[1][1]] if ids else [],
        })
    return lista


def consola_jugadores():
    """Todos los jugadores conocidos, marcando los conectados (dato en vivo)."""
    conectados = (EXA.estado().get('players') or {}).get('list') or []
    guardados = _cacheado('jugadores', 20, _leer_jugadores_guardados)
    lista = [dict(j, conectado=j['nombre'] in conectados) for j in guardados]
    conocidos = {j['nombre'] for j in lista}
    lista += [{'nombre': n, 'conectado': True, 'pos': None, 'dim': None, 'vida': None, 'modo': None, 'waystones': []}
              for n in conectados if n not in conocidos]
    lista.sort(key=lambda j: (not j['conectado'], j['nombre'].lower()))
    return {'jugadores': [{k: v for k, v in j.items() if k != 'waystones'} for j in lista]}


def _leer_waystones():
    data = _leer_saveddata('world/data/waystones.dat')
    nodos = nbt.get(data, ['Waystones'])
    mapa = _mapa_uuid_nombre()
    lista = []
    for nodo in (nodos[1][1] if nodos else []):
        pos = nbt.get(nodo, ['BlockPos'])
        uid = nbt.get(nodo, ['WaystoneUid'])
        if not pos or not uid:
            continue
        dueno = nbt.get(nodo, ['OwnerUid'])
        lista.append({
            'id': uuid_texto(uid[1]),
            'nombre': (nbt.get(nodo, ['Name']) or (8, ''))[1],
            'dim': (nbt.get(nodo, ['World']) or (8, 'minecraft:overworld'))[1],
            'x': nbt.get(pos, ['X'])[1], 'y': nbt.get(pos, ['Y'])[1], 'z': nbt.get(pos, ['Z'])[1],
            'global': bool((nbt.get(nodo, ['IsGlobal']) or (1, 0))[1]),
            'dueno': mapa.get(uuid_texto(dueno[1])) if dueno else None,
        })
    return lista


def consola_waystones(jugador=None):
    """Todas las waystones; con jugador, marca las que el tiene descubiertas."""
    lista = _cacheado('waystones', 30, _leer_waystones)
    if not jugador:
        return {'waystones': lista}
    if not NOMBRE_OK.match(jugador):
        raise ValueError('Jugador no valido')
    guardados = _cacheado('jugadores', 20, _leer_jugadores_guardados)
    ids = next((set(j['waystones']) for j in guardados if j['nombre'] == jugador), set())
    return {'waystones': [dict(w, descubierta=w['global'] or w['id'] in ids) for w in lista]}


def consola_tp(jugador, destino):
    """Teletransporta a un jugador conectado a otro jugador, una waystone o unas coordenadas."""
    if not NOMBRE_OK.match(jugador or ''):
        raise ValueError('Jugador no valido')
    conectados = (EXA.estado(forzar=True).get('players') or {}).get('list') or []
    if jugador not in conectados:
        raise ValueError('%s no esta conectado: solo se puede teletransportar a jugadores dentro del juego.' % jugador)
    destino = destino or {}
    tipo = destino.get('tipo')
    if tipo == 'jugador':
        otro = destino.get('nombre') or ''
        if otro == jugador:
            raise ValueError('Elige un jugador distinto.')
        if otro not in conectados:
            raise ValueError('%s no esta conectado.' % (otro or 'El destino'))
        return ejecutar_comando('tp %s %s' % (jugador, otro))
    if tipo == 'waystone':
        w = next((x for x in _cacheado('waystones', 30, _leer_waystones) if x['id'] == destino.get('id')), None)
        if not w:
            raise ValueError('Esa waystone ya no existe.')
        # La piedra ocupa dos bloques: se aparece encima, centrado
        return ejecutar_comando('execute in %s run tp %s %s %s %s' % (w['dim'], jugador, w['x'] + 0.5, w['y'] + 2, w['z'] + 0.5))
    if tipo == 'coords':
        x, y, z = float(destino['x']), float(destino['y']), float(destino['z'])
        dim = destino.get('dim') or 'minecraft:overworld'
        if not ID_OK.match(dim):
            raise ValueError('Dimension no valida')
        return ejecutar_comando('execute in %s run tp %s %s %s %s' % (dim, jugador, x, y, z))
    raise ValueError('Destino no valido')


def _leer_saveddata(ruta):
    """
    Lee un .dat de SavedData de Minecraft y devuelve el compuesto 'data'
    (lo que escribe el metodo save() del mod). Si el archivo aun no existe
    (nadie ha generado datos todavia), devuelve None sin romper.
    """
    try:
        crudo = EXA.leer_archivo(ruta)
    except exaroton.ErrorApi:
        return None
    raiz = nbt.load(crudo)
    return nbt.get(raiz, ['data'])


def _nombre_item(item_nodo):
    """Nombre legible de un ItemStack NBT ({id, Count, tag}). Usa el display
    name personalizado si lo tiene; si no, el id del item."""
    if item_nodo is None or item_nodo[0] != nbt.COMPOUND:
        return '', 1, ''
    v = item_nodo[1]
    iid = v['id'][1] if 'id' in v and v['id'][0] == nbt.STRING else ''
    cnt = int(v['Count'][1]) if 'Count' in v else 1
    nombre = ''
    disp = nbt.get(item_nodo, ['tag', 'display', 'Name'])
    if disp and disp[0] == nbt.STRING:
        nombre = items._texto_json(disp[1])
    return nombre or iid, cnt, iid


def _nombre_visible_item(item_id, nombre):
    """Resuelve nombres por defecto del mercado al catálogo localizado."""
    if nombre and nombre != item_id:
        return nombre
    for item in _cargar_indice():
        if item['id'] == item_id:
            return item['nombre']
    ruta = (item_id or '').split(':', 1)[-1]
    return ' '.join(palabra.capitalize() for palabra in ruta.replace('/', ' ').replace('_', ' ').split())


# ---------------- Misiones Diarias ----------------
def misiones_lista():
    """Jugadores con misiones del dia y su progreso (lee dailyquests.dat)."""
    data = _leer_saveddata(ARCH_MISIONES)
    if data is None:
        return {'ok': True, 'jugadores': [], 'mensaje': 'Aun no hay datos de misiones (nadie ha abierto /misiones).'}
    nombres = _mapa_uuid_nombre()
    hoy = int(time.time() // 86400)  # epochDay UTC, igual que QuestData.diaActual()
    salida = []
    lista = nbt.get(data, ['jugadores'])
    if lista and lista[0] == nbt.LIST:
        for jt in lista[1][1]:
            u = nbt.get(jt, ['uuid'])
            uuid = uuid_texto(u[1]) if u else None
            dia = nbt.get(jt, ['dia'])
            dia = int(dia[1]) if dia else None
            misiones = []
            ms = nbt.get(jt, ['misiones'])
            if ms and ms[0] == nbt.LIST:
                for mt in ms[1][1]:
                    mid = nbt.get(mt, ['id'])
                    mid = mid[1] if mid else ''
                    prog = nbt.get(mt, ['progreso'])
                    prog = int(prog[1]) if prog else 0
                    recl = nbt.get(mt, ['reclamada'])
                    recl = bool(recl[1]) if recl else False
                    pers = nbt.get(mt, ['personalizada'])
                    pers = bool(pers[1]) if pers else False
                    info = MISIONES_POOL.get(mid, {})
                    misiones.append({
                        'id': mid,
                        'titulo': info.get('titulo', mid),
                        'descripcion': info.get('descripcion', ''),
                        'nivel': info.get('nivel', ''),
                        'progreso': prog,
                        'cantidad': info.get('cantidad', 0),
                        'reclamada': recl,
                        'personalizada': pers,
                        'completada': bool(info.get('cantidad')) and prog >= info['cantidad'],
                    })
            salida.append({
                'uuid': uuid,
                'jugador': nombres.get((uuid or '').lower(), uuid or 'desconocido'),
                'dia': dia,
                'del_dia': dia == hoy,
                'misiones': misiones,
            })
    salida.sort(key=lambda x: (x['jugador'] or '').lower())
    return {'ok': True, 'jugadores': salida}


def misiones_reset(jugador):
    """Regenera las misiones de un jugador via el comando del mod (OP)."""
    if not NOMBRE_OK.match(jugador or ''):
        raise ValueError('Jugador no valido')
    return ejecutar_comando('misiones reset %s' % jugador)


# Niveles validos para una mision personalizada (igual que QuestRegistry.Nivel).
MISIONES_NIVELES = ('facil', 'medio', 'dificil')


def misiones_dar(jugador, nivel):
    """Asigna una mision PERSONALIZADA al azar del pool del nivel a un jugador,
    via el comando OP del mod (/misiones dar <jugador> <facil|medio|dificil>).
    El mod elige al azar el tipo y el objetivo dentro del nivel indicado."""
    if not NOMBRE_OK.match(jugador or ''):
        raise ValueError('Jugador no valido')
    nivel = (nivel or '').strip().lower()
    if nivel not in MISIONES_NIVELES:
        raise ValueError('Nivel no valido (usa facil, medio o dificil).')
    return ejecutar_comando('misiones dar %s %s' % (jugador, nivel))


# ---------------- Tienda / Economia ----------------
def eco_saldos():
    """Saldo de creditos por jugador (lee servereco_balance.dat)."""
    data = _leer_saveddata(ARCH_ECO_BALANCE)
    if data is None:
        return {'ok': True, 'saldos': [], 'mensaje': 'Aun no hay saldos (nadie ha ganado creditos).'}
    nombres = _mapa_uuid_nombre()
    salida = []
    arr = nbt.get(data, ['saldos'])
    if arr and arr[0] == nbt.LIST:
        for jt in arr[1][1]:
            u = nbt.get(jt, ['uuid'])
            uuid = uuid_texto(u[1]) if u else None
            saldo = nbt.get(jt, ['saldo'])
            salida.append({
                'uuid': uuid,
                'jugador': nombres.get((uuid or '').lower(), uuid or 'desconocido'),
                'saldo': int(saldo[1]) if saldo else 0,
            })
    salida.sort(key=lambda x: -x['saldo'])
    return {'ok': True, 'saldos': salida}


def _leer_listados(ruta, clave_lista):
    """Lee los lotes de mercado o subastas (misma estructura NBT)."""
    data = _leer_saveddata(ruta)
    if data is None:
        return []
    salida = []
    arr = nbt.get(data, [clave_lista])
    if arr and arr[0] == nbt.LIST:
        for lt in arr[1][1]:
            lid = nbt.get(lt, ['id'])
            vend = nbt.get(lt, ['vendedorNombre'])
            precio = nbt.get(lt, ['precio'])
            item_nodo = nbt.get(lt, ['item'])
            nombre, cant, iid = _nombre_item(item_nodo)
            nombre = _nombre_visible_item(iid, nombre)
            salida.append({
                'id': int(lid[1]) if lid else 0,
                'vendedor': vend[1] if vend and vend[0] == nbt.STRING else '',
                'item': nombre,
                'item_id': iid,
                'cantidad': cant,
                'precio': int(precio[1]) if precio else 0,
            })
    return salida


def eco_mercado():
    """Listados del mercado global y subastas activas de ServerEco."""
    mercado = _leer_listados(ARCH_ECO_MARKET, 'listados')
    subastas = _leer_listados(ARCH_ECO_AUCTION, 'lotes')
    return {'ok': True, 'mercado': mercado, 'subastas': subastas}


def eco_valores_leer():
    """Devuelve el JSON de valores (como texto, para editarlo)."""
    try:
        datos = EXA.leer_archivo(ARCH_ECO_VALORES)
    except exaroton.ErrorApi:
        return {'ok': True, 'texto': '', 'existe': False,
                'mensaje': 'valores.json aun no existe (se genera al arrancar el servidor con el mod).'}
    try:
        texto = datos.decode('utf-8')
    except UnicodeDecodeError:
        return {'ok': False, 'texto': '', 'existe': True, 'mensaje': 'El archivo no es texto UTF-8.'}
    return {'ok': True, 'texto': texto, 'existe': True}


def eco_valores_guardar(texto):
    """Guarda el JSON de valores (valida el formato antes de subir)."""
    if texto is None:
        raise ValueError('Falta el contenido del archivo')
    try:
        json.loads(texto)
    except Exception as ex:
        raise ValueError('El JSON no es valido: %s' % ex)
    EXA.escribir_archivo(ARCH_ECO_VALORES, texto.encode('utf-8'))
    return {'ok': True, 'mensaje': 'valores.json guardado. Pulsa "Recargar valores" para aplicarlo en vivo.'}


def eco_reload():
    """Aplica los valores en caliente con el comando del mod (OP nivel 2+)."""
    return ejecutar_comando('tienda reload')


# ---------------- Tienda / Economia: MODULO ADMIN de categorias ----------------
# El mod ServerEco agrupa cada item en una CATEGORIA y multiplica su valor final
# por (porcentaje_categoria / 100). Estos porcentajes viven en la seccion
# "categorias" de config/servereco/valores.json. El panel muestra una tabla con
# el % actual (editable) y, por categoria, 2-3 items de MUESTRA con su valor
# BASE y su valor FINAL = base * %/100, para que el admin vea el efecto antes de
# aplicar. Al guardar, el panel reescribe SOLO la seccion "categorias" (leyendo
# y re-serializando el JSON completo para no perder el resto) y ejecuta
# /tienda reload.

# Categorias espejo de ItemValues.Categoria (mod). Orden = como se muestran.
ECO_CATEGORIAS = [
    'ITEMS_MOD_PREMIUM',
    'COMIDA',
    'EQUIPO',
    'MINERALES_GEMAS',
    'DROPS_MOB',
    'BLOQUES_CONSTRUCCION',
    'DECORACION',
    'MISC',
]

# Nombre legible de cada categoria para la UI.
ECO_CATEGORIAS_ETIQUETA = {
    'ITEMS_MOD_PREMIUM': 'Items de mods premium',
    'COMIDA': 'Comida',
    'EQUIPO': 'Equipo (herramientas/armas/armaduras)',
    'MINERALES_GEMAS': 'Minerales y gemas',
    'DROPS_MOB': 'Drops de mob',
    'BLOQUES_CONSTRUCCION': 'Bloques de construccion',
    'DECORACION': 'Decoracion',
    'MISC': 'Utilidad / Misc',
}

# Items de MUESTRA por categoria con un valor BASE representativo. El valor base
# es orientativo (coincide con las anclas/valores por defecto del mod) y sirve
# para que el admin PREVISUALICE el efecto del porcentaje; el precio real lo
# sigue calculando el motor analitico del mod. Donde el item aparece en las
# secciones "anclasMateriasPrimas" o "valoresFijos" del propio valores.json, se
# usa ESE valor (mas fiel) en vez del de respaldo.
ECO_MUESTRAS = {
    'ITEMS_MOD_PREMIUM': [
        ('apotheosis:gem', 'Gema (Apotheosis)', 500),
        ('cataclysm:bloodthirster', 'Arma de jefe (Cataclysm)', 3000),
        ('iceandfire:dragonbone', 'Hueso de dragon', 400),
    ],
    'COMIDA': [
        ('minecraft:bread', 'Pan', 4),
        ('minecraft:cooked_beef', 'Filete asado', 8),
        ('minecraft:golden_apple', 'Manzana dorada', 120),
    ],
    'EQUIPO': [
        ('minecraft:iron_sword', 'Espada de hierro', 50),
        ('minecraft:diamond_pickaxe', 'Pico de diamante', 320),
        ('minecraft:diamond_chestplate', 'Peto de diamante', 820),
    ],
    'MINERALES_GEMAS': [
        ('minecraft:iron_ingot', 'Lingote de hierro', 22),
        ('minecraft:diamond', 'Diamante', 100),
        ('minecraft:netherite_ingot', 'Lingote de netherita', 1600),
    ],
    'DROPS_MOB': [
        ('minecraft:rotten_flesh', 'Carne podrida', 3),
        ('minecraft:bone', 'Hueso', 5),
        ('minecraft:gunpowder', 'Polvora', 12),
    ],
    'BLOQUES_CONSTRUCCION': [
        ('minecraft:cobblestone', 'Roca', 1),
        ('minecraft:oak_log', 'Tronco de roble', 4),
        ('minecraft:stone', 'Piedra', 2),
    ],
    'DECORACION': [
        ('minecraft:glass', 'Cristal', 2),
        ('minecraft:white_carpet', 'Alfombra', 2),
        ('minecraft:torch', 'Antorcha', 1),
    ],
    'MISC': [
        ('minecraft:bucket', 'Cubo', 30),
        ('minecraft:book', 'Libro', 6),
        ('minecraft:compass', 'Brujula', 40),
    ],
}


def _eco_cargar_json():
    """Lee y parsea valores.json. Devuelve (dict, existe). Si no existe o no es
    JSON valido, devuelve ({}, False/True) segun corresponda."""
    try:
        datos = EXA.leer_archivo(ARCH_ECO_VALORES)
    except exaroton.ErrorApi:
        return {}, False
    try:
        return json.loads(datos.decode('utf-8')), True
    except Exception:
        return {}, True


def _eco_base_muestra(cfg, item_id, base_defecto):
    """Valor base de un item de muestra: usa el del propio valores.json si esta
    (valoresFijos o anclasMateriasPrimas), si no el de respaldo."""
    if isinstance(cfg, dict):
        fijos = cfg.get('valoresFijos') or {}
        anclas = cfg.get('anclasMateriasPrimas') or {}
        if item_id in fijos:
            try:
                return int(fijos[item_id])
            except (TypeError, ValueError):
                pass
        if item_id in anclas:
            try:
                return int(anclas[item_id])
            except (TypeError, ValueError):
                pass
    return base_defecto


def eco_categorias_leer():
    """Devuelve las categorias con su % actual y items de muestra (base/final)."""
    cfg, existe = _eco_cargar_json()
    categorias_cfg = cfg.get('categorias') if isinstance(cfg, dict) else None
    if not isinstance(categorias_cfg, dict):
        categorias_cfg = {}

    salida = []
    for cat in ECO_CATEGORIAS:
        try:
            pct = int(categorias_cfg.get(cat, 100))
        except (TypeError, ValueError):
            pct = 100
        muestras = []
        for item_id, nombre, base_def in ECO_MUESTRAS.get(cat, []):
            base = _eco_base_muestra(cfg, item_id, base_def)
            final = int(round(base * pct / 100.0))
            muestras.append({
                'item_id': item_id,
                'nombre': nombre,
                'base': base,
                'final': final,
            })
        salida.append({
            'categoria': cat,
            'etiqueta': ECO_CATEGORIAS_ETIQUETA.get(cat, cat),
            'porcentaje': pct,
            'muestras': muestras,
        })

    return {
        'ok': True,
        'existe': existe,
        'categorias': salida,
        'mensaje': '' if existe else 'valores.json aun no existe (se genera al arrancar el servidor con el mod).',
    }


def eco_categorias_guardar(porcentajes):
    """Escribe SOLO la seccion 'categorias' en valores.json (conservando el
    resto) y aplica en caliente con /tienda reload."""
    if not isinstance(porcentajes, dict):
        raise ValueError('Faltan los porcentajes de categorias')

    cfg, existe = _eco_cargar_json()
    if not existe:
        raise ValueError('valores.json aun no existe en el servidor. Arranca el servidor con el mod para generarlo.')
    if not isinstance(cfg, dict):
        raise ValueError('valores.json no es un objeto JSON valido; revisa el editor avanzado.')

    categorias_cfg = cfg.get('categorias')
    if not isinstance(categorias_cfg, dict):
        categorias_cfg = {}

    # Validar y aplicar SOLO categorias conocidas; el resto del JSON intacto.
    for cat in ECO_CATEGORIAS:
        if cat not in porcentajes:
            continue
        try:
            pct = int(round(float(porcentajes[cat])))
        except (TypeError, ValueError):
            raise ValueError('Porcentaje no numerico para la categoria %s' % cat)
        if pct < 0 or pct > 100000:
            raise ValueError('Porcentaje fuera de rango (0-100000) para %s' % cat)
        categorias_cfg[cat] = pct

    cfg['categorias'] = categorias_cfg

    texto = json.dumps(cfg, ensure_ascii=False, indent=2)
    EXA.escribir_archivo(ARCH_ECO_VALORES, texto.encode('utf-8'))

    # Aplicar en caliente.
    recarga = eco_reload()
    return {
        'ok': True,
        'mensaje': 'Porcentajes guardados y recargados en vivo (/tienda reload).',
        'enviado': recarga.get('enviado') if isinstance(recarga, dict) else None,
    }


# ---------------- Tienda / Economia: CATALOGO + BUSCADOR POR NOMBRE ----------------
# El buscador de item para valor personalizado es un FILTRO POR NOMBRE LEGIBLE:
# el admin escribe "diamante", "espada", "manzana dorada"... y el panel muestra
# en vivo los items cuyo NOMBRE mostrable coincide (no por id tecnico).
#
# El catalogo (id + nombre es_es/en_us de vanilla + todos los mods) se genera
# una sola vez con generar_indice.py y se cachea en datos/items_index.json.
# Para regenerarlo:  cd panel && python generar_indice.py  (ver ese script).
import unicodedata

ARCH_INDICE_ITEMS = os.path.join(DATOS, 'items_index.json')
# Cache en memoria del indice y su hora de carga (se recarga si cambia el fichero).
_INDICE = {'items': [], 'mtime': None, 'meta': {}}


def _normalizar_busqueda(texto):
    """Minusculas + sin acentos/diacriticos, para comparar nombres sin importar
    mayusculas ni tildes (asi 'MANZANA' y 'manzana' y 'esmerálda' empatan)."""
    if not texto:
        return ''
    desc = unicodedata.normalize('NFKD', str(texto))
    sin = ''.join(c for c in desc if not unicodedata.combining(c))
    return sin.lower().strip()


def _cargar_indice():
    """Carga (y cachea) datos/items_index.json. Devuelve la lista de items ya
    con campos normalizados para buscar. Si el archivo cambio en disco, lo
    relee. Si no existe, devuelve lista vacia (el buscador avisa como regenerar)."""
    try:
        mtime = os.path.getmtime(ARCH_INDICE_ITEMS)
    except OSError:
        _INDICE['items'] = []
        _INDICE['mtime'] = None
        _INDICE['meta'] = {}
        return _INDICE['items']
    if _INDICE['mtime'] == mtime and _INDICE['items']:
        return _INDICE['items']
    with open(ARCH_INDICE_ITEMS, 'r', encoding='utf-8') as f:
        doc = json.load(f)
    items = []
    for it in doc.get('items', []):
        iid = it.get('id', '')
        nombre = it.get('nombre', '') or iid
        n_norm = _normalizar_busqueda(nombre)
        items.append({
            'id': iid,
            'nombre': nombre,
            'n_norm': n_norm,
            'n_palabras': n_norm.split(),
            'id_norm': _normalizar_busqueda(iid),
        })
    _INDICE['items'] = items
    _INDICE['mtime'] = mtime
    _INDICE['meta'] = {k: doc.get(k) for k in ('total', 'vanilla', 'mods_jars', 'version')}
    return items


def _puntuar_item(it, q_norm, q_palabras):
    """Relevancia de un item frente a la consulta normalizada. Mayor = mejor.
    Devuelve None si no hay coincidencia alguna.

    Motor:
      - Coincidencia EXACTA de nombre: maxima.
      - Nombre EMPIEZA por la consulta: muy alta.
      - Consulta como SUBCADENA del nombre: alta (mas si cae en inicio de palabra).
      - Coincidencia POR PALABRAS: todas las palabras de la consulta aparecen
        (como prefijo de alguna palabra del nombre) -> 'manzana dorada' encuentra
        'Manzana Dorada Encantada'. Puntua por cuantas y como de bien encajan.
      - Coincidencia por id (subcadena) como ultimo recurso, con poco peso.
    Empates: nombre mas corto primero (mas especifico)."""
    nombre = it['n_norm']
    if not q_norm:
        return None
    puntos = 0

    if nombre == q_norm:
        puntos += 1000
    elif nombre.startswith(q_norm):
        puntos += 600
    else:
        pos = nombre.find(q_norm)
        if pos >= 0:
            # subcadena: mejor si empieza en frontera de palabra
            puntos += 350
            if pos == 0 or nombre[pos - 1] == ' ':
                puntos += 60

    # Coincidencia por palabras (prefijo de palabras del nombre).
    if q_palabras:
        palabras = it['n_palabras']
        casadas = 0
        for qp in q_palabras:
            if any(w == qp for w in palabras):
                casadas += 1
                puntos += 40          # palabra exacta
            elif any(w.startswith(qp) for w in palabras):
                casadas += 1
                puntos += 25          # palabra por prefijo
            elif any(qp in w for w in palabras):
                puntos += 8           # palabra por subcadena interna
        if casadas == len(q_palabras) and casadas > 0:
            puntos += 120             # TODAS las palabras de la consulta encajan

    # Ultimo recurso: la consulta aparece en el id tecnico.
    if puntos == 0 and q_norm in it['id_norm']:
        puntos += 50

    if puntos == 0:
        return None
    # Desempate: nombres mas cortos (mas especificos) un pelin arriba.
    puntos += max(0, 40 - len(nombre)) * 0.1
    return puntos


def eco_buscar_items(texto, limite=40):
    """Filtro por NOMBRE legible sobre el catalogo. Devuelve los items mas
    relevantes [{id, nombre}] ordenados por relevancia."""
    items = _cargar_indice()
    q = (texto or '').strip()
    if not items:
        return {
            'ok': True, 'resultados': [], 'total_indice': 0,
            'mensaje': 'El catalogo de items aun no existe. Genera el indice con: '
                       'cd panel && python generar_indice.py',
        }
    if not q:
        return {'ok': True, 'resultados': [], 'total_indice': len(items),
                'mensaje': 'Escribe el nombre de un item (ej. diamante, espada, manzana dorada).'}
    q_norm = _normalizar_busqueda(q)
    q_palabras = [p for p in q_norm.split() if p]
    puntuados = []
    for it in items:
        p = _puntuar_item(it, q_norm, q_palabras)
        if p is not None:
            puntuados.append((p, it))
    # Orden: relevancia desc, luego vanilla antes que mods, nombre corto, id.
    puntuados.sort(key=lambda x: (
        -x[0],
        0 if x[1]['id'].startswith('minecraft:') else 1,
        len(x[1]['n_norm']),
        x[1]['id'],
    ))
    resultados = [{'id': it['id'], 'nombre': it['nombre']} for _, it in puntuados[:limite]]
    return {
        'ok': True,
        'resultados': resultados,
        'total_coincidencias': len(puntuados),
        'total_indice': len(items),
        'mensaje': '' if resultados else 'Ningun item coincide con "%s".' % q,
    }


# ---------------- Tienda / Economia: VALOR PERSONALIZADO ----------------
# Elegido un item (por su id, que viene del buscador por nombre), el panel pide
# al mod su VALOR CALCULADO ACTUAL (/tienda valor <id>) y deja fijar un VALOR
# PERSONALIZADO. El override se guarda en la seccion "overrides" de valores.json
# (mapa modid:item -> creditos) y manda sobre toda la valoracion: ni categoria
# ni heuristica se aplican a un item con override. Al guardar/eliminar se
# reescribe el JSON completo (sin perder categorias ni el resto) y se aplica en
# caliente con /tienda reload, sin reiniciar el servidor.

# Formato de id de item: "modid:item". El path puede llevar / (p.ej. mods).
ECO_ITEM_ID = re.compile(r'^[a-z0-9_.\-]+:[a-z0-9_./\-]+$')

# Linea que imprime /tienda valor en el log del servidor. La parseamos para
# saber el valor calculado y su origen (override|base).
#   [servereco] valor minecraft:diamond = 100 (override)
ECO_RE_VALOR = re.compile(r'\[servereco\]\s+valor\s+(\S+)\s+=\s+(-?\d+)\s+\((\w+)\)')
ECO_RE_VALOR_DESCONOCIDO = re.compile(r'\[servereco\]\s+valor\s+(\S+)\s+=\s+\?\s+\(item desconocido\)')


def _eco_normalizar_id(texto):
    """Normaliza lo que escribe el admin a un id 'modid:item'. Si no lleva ':'
    se asume el namespace vanilla 'minecraft'. Valida el formato final."""
    s = (texto or '').strip().lower().replace(' ', '_')
    if not s:
        raise ValueError('Escribe el nombre o id de un item.')
    if ':' not in s:
        s = 'minecraft:' + s
    if not ECO_ITEM_ID.match(s):
        raise ValueError('Id de item no valido. Usa el formato modid:item (ej. minecraft:diamond).')
    return s


def eco_overrides_leer():
    """Lista los valores personalizados (overrides) ya definidos en valores.json."""
    cfg, existe = _eco_cargar_json()
    overrides = cfg.get('overrides') if isinstance(cfg, dict) else None
    if not isinstance(overrides, dict):
        overrides = {}
    lista = []
    for item_id, valor in overrides.items():
        try:
            lista.append({'item_id': item_id, 'valor': int(valor)})
        except (TypeError, ValueError):
            lista.append({'item_id': item_id, 'valor': valor})
    lista.sort(key=lambda x: x['item_id'])
    return {
        'ok': True,
        'existe': existe,
        'overrides': lista,
        'mensaje': '' if existe else 'valores.json aun no existe (se genera al arrancar el servidor con el mod).',
    }


def eco_item_valor(texto):
    """Pregunta al mod el VALOR CALCULADO ACTUAL de un item con /tienda valor
    <id> y parsea la linea del log. Si el servidor esta apagado o no responde,
    cae al valor de valores.json (override/fijo/ancla) como informativo."""
    item_id = _eco_normalizar_id(texto)

    # Valor conocido desde el propio JSON (siempre disponible, informativo).
    cfg, _ = _eco_cargar_json()
    override_actual = None
    base_json = None
    if isinstance(cfg, dict):
        ov = cfg.get('overrides') or {}
        if item_id in ov:
            try:
                override_actual = int(ov[item_id])
            except (TypeError, ValueError):
                pass
        base_json = _eco_base_muestra(cfg, item_id, None)

    # Intentar el valor EN VIVO desde el mod (solo si el servidor esta encendido).
    valor_mod = None
    origen = None
    existe_item = None
    nota = None
    try:
        encendido = EXA.estado().get('status') == 1
    except Exception:
        encendido = False

    if encendido:
        try:
            antes = set(EXA.logs().splitlines())
            EXA.comando('tienda valor %s' % item_id)
            # El endpoint de logs de Exaroton tarda ~2-4 s en reflejar la salida
            # del comando; reintentamos un par de veces hasta verla.
            nuevas = []
            for _ in range(4):
                time.sleep(2.0)
                nuevas = [l for l in EXA.logs().splitlines() if l not in antes]
                if any(('[servereco] valor' in l) for l in nuevas):
                    break
            for l in reversed(nuevas):
                md = ECO_RE_VALOR_DESCONOCIDO.search(l)
                if md:
                    existe_item = False
                    break
                m = ECO_RE_VALOR.search(l)
                if m:
                    item_id = m.group(1)           # id normalizado por el mod
                    valor_mod = int(m.group(2))
                    origen = m.group(3)            # 'override' | 'base'
                    existe_item = True
                    break
            if existe_item is None:
                nota = 'El mod no respondio a tiempo; se muestra el valor de valores.json.'
        except exaroton.ErrorApi as ex:
            nota = 'No se pudo consultar al servidor (%s); se muestra el valor de valores.json.' % ex
    else:
        nota = 'El servidor esta apagado; se muestra el valor de valores.json (no el calculado en vivo).'

    if existe_item is False:
        raise ValueError('El item "%s" no existe en el servidor (revisa el id).' % item_id)

    # Valor "actual" a mostrar: el del mod si lo tenemos; si no, override/base del JSON.
    valor_actual = valor_mod
    if valor_actual is None:
        valor_actual = override_actual if override_actual is not None else base_json

    return {
        'ok': True,
        'item_id': item_id,
        'valor_actual': valor_actual,
        'origen': origen,
        'override_actual': override_actual,
        'base_json': base_json,
        'en_vivo': valor_mod is not None,
        'nota': nota,
    }


def eco_override_guardar(item_id, valor):
    """Fija un VALOR PERSONALIZADO para un item en la seccion 'overrides' de
    valores.json (conservando el resto) y lo aplica en caliente con /tienda
    reload."""
    item_id = _eco_normalizar_id(item_id)
    try:
        valor = int(round(float(valor)))
    except (TypeError, ValueError):
        raise ValueError('El valor personalizado debe ser un numero entero de creditos.')
    if valor < 0 or valor > 100000000:
        raise ValueError('Valor fuera de rango (0 a 100000000).')

    cfg, existe = _eco_cargar_json()
    if not existe:
        raise ValueError('valores.json aun no existe en el servidor. Arranca el servidor con el mod para generarlo.')
    if not isinstance(cfg, dict):
        raise ValueError('valores.json no es un objeto JSON valido; revisa el editor avanzado.')

    overrides = cfg.get('overrides')
    if not isinstance(overrides, dict):
        overrides = {}
    overrides[item_id] = valor
    cfg['overrides'] = overrides

    texto = json.dumps(cfg, ensure_ascii=False, indent=2)
    EXA.escribir_archivo(ARCH_ECO_VALORES, texto.encode('utf-8'))

    recarga = eco_reload()
    return {
        'ok': True,
        'item_id': item_id,
        'valor': valor,
        'mensaje': 'Valor personalizado guardado para %s (%d creditos) y recargado en vivo.' % (item_id, valor),
        'enviado': recarga.get('enviado') if isinstance(recarga, dict) else None,
    }


def eco_override_eliminar(item_id):
    """Elimina el valor personalizado de un item (vuelve a la valoracion normal)
    y aplica en caliente."""
    item_id = _eco_normalizar_id(item_id)
    cfg, existe = _eco_cargar_json()
    if not existe or not isinstance(cfg, dict):
        raise ValueError('valores.json aun no existe o no es valido.')
    overrides = cfg.get('overrides')
    if not isinstance(overrides, dict) or item_id not in overrides:
        return {'ok': True, 'item_id': item_id, 'mensaje': 'Ese item no tenia valor personalizado.'}
    overrides.pop(item_id, None)
    cfg['overrides'] = overrides

    texto = json.dumps(cfg, ensure_ascii=False, indent=2)
    EXA.escribir_archivo(ARCH_ECO_VALORES, texto.encode('utf-8'))

    recarga = eco_reload()
    return {
        'ok': True,
        'item_id': item_id,
        'mensaje': 'Valor personalizado eliminado para %s y recargado en vivo.' % item_id,
        'enviado': recarga.get('enviado') if isinstance(recarga, dict) else None,
    }


# ---------------- Tienda / Economia: ADMIN de CREDITOS (saldo) ----------------
# El panel NO escribe el .dat de saldos directamente (riesgo de corromperlo con
# el servidor encendido). En su lugar ejecuta el comando OP del mod:
#     /tienda creditos set|add|remove <jugador> <cantidad>   (alias /servereco)
# El mod ajusta el saldo en BalanceData e imprime una linea parseable al log:
#     [servereco] creditos set Nombre 100 -> saldo 20 => 100
# Asi el admin puede devolver lo que un jugador perdio por el bug del sorter.

# Linea que imprime el comando de creditos del mod (la parseamos para confirmar).
ECO_RE_CREDITOS = re.compile(
    r'\[servereco\]\s+creditos\s+(set|add|remove)\s+(\S+)\s+(-?\d+)\s+->\s+saldo\s+(-?\d+)\s+=>\s+(-?\d+)')


def eco_creditos_ajustar(jugador, modo, cantidad):
    """Ajusta el saldo de creditos de un jugador via el comando OP del mod.
    modo: 'set' | 'add' | 'remove'. Devuelve el saldo resultante si el mod lo
    confirma en el log."""
    if not NOMBRE_OK.match(jugador or ''):
        raise ValueError('Jugador no valido (nombre de Minecraft).')
    modo = (modo or '').strip().lower()
    if modo not in ('set', 'add', 'remove'):
        raise ValueError("Modo invalido (usa 'set', 'add' o 'remove').")
    try:
        cantidad = int(round(float(cantidad)))
    except (TypeError, ValueError):
        raise ValueError('La cantidad debe ser un numero entero de creditos.')
    if cantidad < 0:
        raise ValueError('La cantidad no puede ser negativa.')
    if modo != 'set' and cantidad == 0:
        raise ValueError('Para add/remove la cantidad debe ser mayor que 0.')
    if cantidad > 1000000000:
        raise ValueError('Cantidad fuera de rango.')

    try:
        encendido = EXA.estado().get('status') == 1
    except Exception:
        encendido = False
    if not encendido:
        raise ValueError('El servidor esta apagado. Enciendelo para ajustar saldos (el mod aplica el cambio en vivo).')

    r = ejecutar_comando('tienda creditos %s %s %d' % (modo, jugador, cantidad))
    # Parsear la confirmacion del mod en la salida reciente del log.
    saldo_final = None
    saldo_antes = None
    desconocido = False
    for l in (r.get('salida') or []):
        if 'jugador desconocido' in l and jugador in l:
            desconocido = True
        m = ECO_RE_CREDITOS.search(l)
        if m and m.group(2) == jugador:
            saldo_antes = int(m.group(4))
            saldo_final = int(m.group(5))
    if desconocido:
        raise ValueError('El jugador "%s" no existe en el servidor (nunca ha entrado).' % jugador)
    return {
        'ok': True,
        'jugador': jugador,
        'modo': modo,
        'cantidad': cantidad,
        'saldo_antes': saldo_antes,
        'saldo_final': saldo_final,
        'enviado': r.get('enviado'),
        'mensaje': ('Saldo de %s: %s creditos.' % (jugador, saldo_final)) if saldo_final is not None
                   else 'Comando enviado; revisa el saldo con Actualizar (el mod no confirmo en el log a tiempo).',
    }


# ---------------- Tienda / Economia: ADMIN de MERCADO / SUBASTAS ----------------
# El admin puede VER los listados (eco_mercado, ya existente) y ELIMINAR uno por
# su id con el comando OP del mod, que devuelve el item al buzon del vendedor:
#     /tienda mercado quitar <id>     /tienda subasta quitar <id>

def eco_listado_quitar(tipo, listado_id):
    """Elimina un listado de mercado o una subasta por id (comando OP del mod)."""
    tipo = (tipo or '').strip().lower()
    if tipo not in ('mercado', 'subasta'):
        raise ValueError("Tipo invalido (usa 'mercado' o 'subasta').")
    try:
        listado_id = int(listado_id)
    except (TypeError, ValueError):
        raise ValueError('Id de listado no valido.')
    if listado_id <= 0:
        raise ValueError('Id de listado no valido.')
    try:
        encendido = EXA.estado().get('status') == 1
    except Exception:
        encendido = False
    if not encendido:
        raise ValueError('El servidor esta apagado. Enciendelo para quitar listados (el mod lo aplica en vivo).')
    r = ejecutar_comando('tienda %s quitar %d' % (tipo, listado_id))
    salida = r.get('salida') or []
    quito = any(('%s quitar %d' % (tipo, listado_id)) in l and 'buzon' in l for l in salida)
    no_existe = any('no existe' in l for l in salida)
    if no_existe:
        raise ValueError('No existe el %s con id %d (quiza ya se vendio o se quito).' % (tipo, listado_id))
    return {
        'ok': True,
        'tipo': tipo,
        'id': listado_id,
        'enviado': r.get('enviado'),
        'mensaje': ('Listado %d de %s retirado; el item volvio al buzon del vendedor.' % (listado_id, tipo))
                   if quito else 'Comando enviado; actualiza para confirmar.',
    }


# ---------------- Tienda / Economia: ADMIN del CATALOGO BASE DE COMPRA ----------------
# El catalogo base de COMPRA (lo que un jugador puede comprar en /shop) vive en
# el codigo del mod, pero el admin puede AnADIR, CAMBIAR o QUITAR items en
# caliente desde el panel. Esos ajustes se escriben en la seccion
# "catalogoCompra" de valores.json (modid:item -> precio) y se aplican con
# /tienda reload. precio > 0 = en venta a ese precio; el item se QUITA con
# eco_catalogo_quitar (se borra la clave o se pone <= 0).

def eco_catalogo_leer():
    """Lista los ajustes de catalogo de compra definidos por el admin."""
    cfg, existe = _eco_cargar_json()
    cat = cfg.get('catalogoCompra') if isinstance(cfg, dict) else None
    if not isinstance(cat, dict):
        cat = {}
    lista = []
    for item_id, precio in cat.items():
        try:
            lista.append({'item_id': item_id, 'precio': int(precio)})
        except (TypeError, ValueError):
            lista.append({'item_id': item_id, 'precio': precio})
    lista.sort(key=lambda x: x['item_id'])
    return {
        'ok': True,
        'existe': existe,
        'catalogo': lista,
        'mensaje': '' if existe else 'valores.json aun no existe (se genera al arrancar el servidor con el mod).',
    }


def eco_catalogo_guardar(item_id, precio):
    """Anade o cambia el precio de COMPRA de un item en el catalogo base
    (seccion 'catalogoCompra' de valores.json) y aplica en caliente."""
    item_id = _eco_normalizar_id(item_id)
    try:
        precio = int(round(float(precio)))
    except (TypeError, ValueError):
        raise ValueError('El precio de compra debe ser un numero entero de creditos.')
    if precio <= 0:
        raise ValueError('El precio debe ser mayor que 0. Para quitar el item usa el boton de quitar.')
    if precio > 100000000:
        raise ValueError('Precio fuera de rango.')

    cfg, existe = _eco_cargar_json()
    if not existe:
        raise ValueError('valores.json aun no existe en el servidor. Arranca el servidor con el mod para generarlo.')
    if not isinstance(cfg, dict):
        raise ValueError('valores.json no es un objeto JSON valido; revisa el editor avanzado.')
    cat = cfg.get('catalogoCompra')
    if not isinstance(cat, dict):
        cat = {}
    cat[item_id] = precio
    cfg['catalogoCompra'] = cat
    EXA.escribir_archivo(ARCH_ECO_VALORES, json.dumps(cfg, ensure_ascii=False, indent=2).encode('utf-8'))
    recarga = eco_reload()
    return {
        'ok': True,
        'item_id': item_id,
        'precio': precio,
        'mensaje': 'Catalogo: %s en venta a %d creditos (aplicado en vivo).' % (item_id, precio),
        'enviado': recarga.get('enviado') if isinstance(recarga, dict) else None,
    }


def eco_catalogo_quitar(item_id):
    """Quita un item del catalogo base de compra. Si el item estaba en el
    catalogo del CODIGO, marca precio 0 (lo desactiva); si era un extra del
    admin, borra la clave. Aplica en caliente."""
    item_id = _eco_normalizar_id(item_id)
    cfg, existe = _eco_cargar_json()
    if not existe or not isinstance(cfg, dict):
        raise ValueError('valores.json aun no existe o no es valido.')
    cat = cfg.get('catalogoCompra')
    if not isinstance(cat, dict):
        cat = {}
    # Poner a 0 desactiva el item aunque venga del catalogo del codigo.
    cat[item_id] = 0
    cfg['catalogoCompra'] = cat
    EXA.escribir_archivo(ARCH_ECO_VALORES, json.dumps(cfg, ensure_ascii=False, indent=2).encode('utf-8'))
    recarga = eco_reload()
    return {
        'ok': True,
        'item_id': item_id,
        'mensaje': 'Catalogo: %s quitado de la venta (aplicado en vivo).' % item_id,
        'enviado': recarga.get('enviado') if isinstance(recarga, dict) else None,
    }


# ====================== Datapacks: indice de ENTIDADES + buscador ======================
# Gemelo del catalogo de items, pero con entidades (id + nombre legible). Lo usa
# el buscador de mobs-para-vida y el buscador/spawneador de jefes. Reutiliza el
# MISMO motor de busqueda que los items (_normalizar_busqueda / _puntuar_item)
# para que filtre por nombre, sin acentos/mayusculas, por palabras y relevancia.
#
# Se genera con generar_indice_entidades.py (gemelo de generar_indice.py):
#     cd panel && python generar_indice_entidades.py
ARCH_INDICE_ENT = os.path.join(DATOS, 'entities_index.json')
_INDICE_ENT = {'entidades': [], 'mtime': None, 'meta': {}}

# Jefes CONOCIDOS (vanilla + mods del pack). Lista curada de ids REALES (los que
# existen en el indice). Se usa para MARCAR/sugerir cuales son jefes; el buscador
# igual deja elegir cualquier entidad. Si un id no esta en el indice, se ignora.
JEFES_CONOCIDOS = {
    # Vanilla
    'minecraft:ender_dragon', 'minecraft:wither', 'minecraft:warden',
    'minecraft:elder_guardian',
    # Cataclysm
    'cataclysm:ignis', 'cataclysm:netherite_monstrosity', 'cataclysm:the_leviathan',
    'cataclysm:ender_guardian', 'cataclysm:the_harbinger', 'cataclysm:maledictus',
    'cataclysm:wadjet', 'cataclysm:scylla', 'cataclysm:coralssus', 'cataclysm:cindaria',
    # Twilight Forest
    'twilightforest:naga', 'twilightforest:lich', 'twilightforest:hydra',
    'twilightforest:ur_ghast', 'twilightforest:snow_queen', 'twilightforest:alpha_yeti',
    'twilightforest:minoshroom', 'twilightforest:knight_phantom',
    # Ice and Fire
    'iceandfire:fire_dragon', 'iceandfire:ice_dragon', 'iceandfire:lightning_dragon',
    'iceandfire:cyclops', 'iceandfire:hydra', 'iceandfire:gorgon', 'iceandfire:sea_serpent',
    # Mowzie's Mobs
    'mowziesmobs:ferrous_wroughtnaut', 'mowziesmobs:frostmaw', 'mowziesmobs:umvuthi',
    'mowziesmobs:naga', 'mowziesmobs:sculptor',
}


def _cargar_indice_entidades():
    """Carga (y cachea) datos/entities_index.json con los mismos campos
    normalizados que el catalogo de items, para reutilizar el motor de busqueda."""
    try:
        mtime = os.path.getmtime(ARCH_INDICE_ENT)
    except OSError:
        _INDICE_ENT['entidades'] = []
        _INDICE_ENT['mtime'] = None
        _INDICE_ENT['meta'] = {}
        return _INDICE_ENT['entidades']
    if _INDICE_ENT['mtime'] == mtime and _INDICE_ENT['entidades']:
        return _INDICE_ENT['entidades']
    with open(ARCH_INDICE_ENT, 'r', encoding='utf-8') as f:
        doc = json.load(f)
    ents = []
    for it in doc.get('entidades', []):
        iid = it.get('id', '')
        nombre = it.get('nombre', '') or iid
        n_norm = _normalizar_busqueda(nombre)
        ents.append({
            'id': iid,
            'nombre': nombre,
            'n_norm': n_norm,
            'n_palabras': n_norm.split(),
            'id_norm': _normalizar_busqueda(iid),
            'jefe': iid in JEFES_CONOCIDOS,
        })
    _INDICE_ENT['entidades'] = ents
    _INDICE_ENT['mtime'] = mtime
    _INDICE_ENT['meta'] = {k: doc.get(k) for k in ('total', 'vanilla', 'mods_jars', 'version')}
    return ents


def buscar_entidades(texto, limite=40, solo_jefes=False):
    """Filtro por NOMBRE legible sobre el catalogo de entidades (mismo motor que
    los items). Si solo_jefes, limita a los jefes conocidos. Si no hay texto y
    solo_jefes, devuelve TODOS los jefes conocidos ordenados por nombre."""
    ents = _cargar_indice_entidades()
    if not ents:
        return {
            'ok': True, 'resultados': [], 'total_indice': 0,
            'mensaje': 'El catalogo de entidades aun no existe. Genera el indice con: '
                       'cd panel && python generar_indice_entidades.py',
        }
    universo = [e for e in ents if e['jefe']] if solo_jefes else ents
    q = (texto or '').strip()
    if not q:
        if solo_jefes:
            lista = sorted(universo, key=lambda e: e['n_norm'])
            resultados = [{'id': e['id'], 'nombre': e['nombre'], 'jefe': e['jefe']} for e in lista[:limite]]
            return {'ok': True, 'resultados': resultados, 'total_coincidencias': len(universo),
                    'total_indice': len(ents)}
        return {'ok': True, 'resultados': [], 'total_indice': len(ents),
                'mensaje': 'Escribe el nombre de una entidad (ej. dragon, naga, ignis).'}
    q_norm = _normalizar_busqueda(q)
    q_palabras = [p for p in q_norm.split() if p]
    puntuados = []
    for e in universo:
        p = _puntuar_item(e, q_norm, q_palabras)
        if p is not None:
            puntuados.append((p, e))
    puntuados.sort(key=lambda x: (
        -x[0],
        0 if x[1]['id'].startswith('minecraft:') else 1,
        len(x[1]['n_norm']),
        x[1]['id'],
    ))
    resultados = [{'id': e['id'], 'nombre': e['nombre'], 'jefe': e['jefe']} for _, e in puntuados[:limite]]
    return {
        'ok': True,
        'resultados': resultados,
        'total_coincidencias': len(puntuados),
        'total_indice': len(ents),
        'mensaje': '' if resultados else 'Ninguna entidad coincide con "%s".' % q,
    }


# Instancia del modulo de datapacks (vida de mobs, programador, spawns).
DATAPACKS = dp.Datapacks(EXA, exaroton.ErrorApi, registrar_error)


# ====================== Acceso remoto (enlace de ngrok) ======================
ARCH_TUNEL = os.path.join(DATOS, 'tunel.json')
SCRIPT_NGROK = os.path.join(RAIZ_SP, 'herramientas', 'servidor', 'configurar-ngrok.ps1')
SIN_VENTANA = getattr(subprocess, 'CREATE_NO_WINDOW', 0)


_tunel_vivo = {'ts': 0.0, 'url': None}


def _url_ngrok_viva():
    """Enlace que ngrok tiene abierto ahora (API local), con cache de 5 s."""
    if time.time() - _tunel_vivo['ts'] > 5:
        url = None
        try:
            with urllib.request.urlopen('http://127.0.0.1:4040/api/tunnels', timeout=1.5) as r:
                for t in json.load(r).get('tunnels', []):
                    if t.get('public_url', '').startswith('https://'):
                        url = t['public_url']
                        break
        except Exception:
            pass
        _tunel_vivo.update(ts=time.time(), url=url)
    return _tunel_vivo['url']


def tunel_info():
    # En la nube el enlace publico lo fija el servicio (ngrok con dominio fijo, o
    # la URL que Render pone sola en RENDER_EXTERNAL_URL)
    publica = os.environ.get('PANEL_URL_PUBLICA') or os.environ.get('RENDER_EXTERNAL_URL')
    if publica:
        url = publica.rstrip('/')
        return {'tipo': 'nube', 'fijo': True, 'dominio': urllib.parse.urlparse(url).hostname, 'url': url}
    try:
        with open(ARCH_TUNEL, 'r', encoding='utf-8-sig') as f:
            t = json.load(f)
    except (FileNotFoundError, ValueError):
        return None
    if not t.get('fijo', True):
        # Enlace automatico de ngrok: cambia al reiniciarse, asi que se pregunta el actual
        viva = _url_ngrok_viva()
        if viva:
            t['url'], t['dominio'] = viva, urllib.parse.urlparse(viva).hostname
    return t


def _buscar_ngrok():
    local = os.environ.get('LOCALAPPDATA', '')
    enlace = os.path.join(local, 'Microsoft', 'WinGet', 'Links', 'ngrok.exe')
    if os.path.exists(enlace):
        return enlace
    for raiz, _, archivos in os.walk(os.path.join(local, 'Microsoft', 'WinGet', 'Packages')):
        if 'ngrok.exe' in archivos:
            return os.path.join(raiz, 'ngrok.exe')
    return None


def enlace_encendido():
    """Enlace de la pagina de encendido 24/7 en Cloudflare (encendido-nube/publicar.ps1)."""
    try:
        with open(os.path.join(DATOS, 'nube.json'), 'r', encoding='utf-8-sig') as f:
            return json.load(f).get('url')
    except (FileNotFoundError, ValueError):
        return None


def estado_acceso():
    r = _estado_acceso()
    r['encendido_url'] = enlace_encendido()
    return r


def _estado_acceso():
    t = tunel_info()
    if t and t.get('tipo') == 'nube':
        return {'url': t['url'], 'fijo': True, 'tunel_activo': bool(_url_ngrok_viva()),
                'ngrok_instalado': False, 'ngrok_con_token': True, 'en_la_nube': True}
    activo = False
    try:
        r = subprocess.run(['schtasks', '/Query', '/TN', 'ServerPanas-Tunel', '/FO', 'CSV', '/NH'],
                           capture_output=True, text=True, timeout=10, creationflags=SIN_VENTANA)
        activo = r.returncode == 0 and ('Running' in r.stdout or 'En ejecuci' in r.stdout)
    except Exception:
        pass
    cfg_ngrok = os.path.join(os.environ.get('LOCALAPPDATA', ''), 'ngrok', 'ngrok.yml')
    con_token = False
    try:
        with open(cfg_ngrok, 'r', encoding='utf-8') as f:
            con_token = any(l.strip().startswith('authtoken:') for l in f)
    except FileNotFoundError:
        pass
    return {'url': t.get('url') if t else None, 'fijo': bool(t and t.get('fijo', True)),
            'tunel_activo': activo and bool(t and t.get('url')), 'ngrok_instalado': bool(_buscar_ngrok()),
            'ngrok_con_token': con_token}


def activar_ngrok(authtoken, dominio=''):
    """Guarda el authtoken (si viene) y deja el enlace funcionando (tarea en segundo plano)."""
    ngrok = _buscar_ngrok()
    if not ngrok:
        raise RuntimeError('ngrok no esta instalado en este PC.')
    if authtoken:
        TAREAS.fase('ngrok', 'ejecutando', 'Guardando tu authtoken en ngrok...')
        r = subprocess.run([ngrok, 'config', 'add-authtoken', authtoken], capture_output=True, text=True,
                           timeout=60, creationflags=SIN_VENTANA)
        if r.returncode != 0 or 'saved' not in (r.stdout + r.stderr).lower():
            raise RuntimeError('ngrok no acepto el authtoken. Copialo de nuevo desde dashboard.ngrok.com.')
    TAREAS.fase('ngrok', 'verificando', 'Abriendo el enlace (unos 30 segundos)...')
    args = ['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', SCRIPT_NGROK, '-DesdePanel']
    if dominio:
        args += ['-Dominio', dominio]
    p = subprocess.run(args, capture_output=True, text=True, timeout=300, creationflags=SIN_VENTANA)
    _tunel_vivo['ts'] = 0.0
    t = tunel_info()
    if p.returncode != 0 or not t:
        cola = ((p.stdout or '') + (p.stderr or '')).strip().splitlines()[-4:]
        raise RuntimeError('No se pudo crear el enlace: ' + ' '.join(cola))
    return {'ok': True, 'mensaje': 'Enlace listo: %s' % t['url']}


# ====================== Servidor web ======================
class Manejador(BaseHTTPRequestHandler):
    server_version = 'PanelServerPanas/1.0'

    def log_message(self, *args):
        pass

    def _json(self, datos, codigo=200, cabeceras=()):
        cuerpo = json.dumps(datos, ensure_ascii=False).encode('utf-8')
        self.send_response(codigo)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(cuerpo)))
        for k, v in cabeceras:
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(cuerpo)

    def _html(self, nombre):
        with open(os.path.join(BASE, 'static', nombre), 'rb') as f:
            cuerpo = f.read()
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(cuerpo)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(cuerpo)

    def _redirigir(self, destino):
        self.send_response(302)
        self.send_header('Location', destino)
        self.send_header('Content-Length', '0')
        self.end_headers()

    def _desde_internet(self):
        # cloudflared y ngrok anaden estas cabeceras a todo lo que llega por el tunel
        return bool(self.headers.get('Cf-Connecting-IP') or self.headers.get('X-Forwarded-For'))

    def _ip(self):
        # IP real del visitante: Cloudflare la pone en Cf-Connecting-IP y ngrok en X-Forwarded-For
        reenviada = (self.headers.get('X-Forwarded-For') or '').split(',')[0].strip()
        return self.headers.get('Cf-Connecting-IP') or reenviada or self.client_address[0]

    def _token(self):
        for parte in (self.headers.get('Cookie') or '').split(';'):
            k, _, v = parte.strip().partition('=')
            if k == 'sp_sesion':
                return v
        return None

    def _cookie(self, valor, segundos):
        https = self.headers.get('X-Forwarded-Proto') == 'https' or 'https' in (self.headers.get('Cf-Visitor') or '')
        return ('Set-Cookie', 'sp_sesion=%s; Path=/; HttpOnly; SameSite=Strict; Max-Age=%d%s'
                % (valor, segundos, '; Secure' if https else ''))

    def _cuerpo(self):
        n = int(self.headers.get('Content-Length') or 0)
        return self.rfile.read(n) if n else b''

    def _cuerpo_json(self):
        crudo = self._cuerpo()
        return json.loads(crudo.decode('utf-8')) if crudo else {}

    def _peticion_permitida(self, metodo):
        """Bloquea peticiones que no vienen del propio panel.

        Escuchar en 127.0.0.1 no basta: cualquier web abierta en tu navegador
        puede mandar un POST a http://127.0.0.1:8765 (CSRF) o apuntar un
        dominio suyo a 127.0.0.1 para leer la API (DNS rebinding).
        - Host debe ser 127.0.0.1/localhost con nuestro puerto, el tunel de
          Cloudflare (*.trycloudflare.com) o un host de CONFIG['hosts_publicos'].
          Por el tunel, ademas, todo exige sesion iniciada (ver _despachar).
        - Origin, si viene, debe ser el del propio host de la peticion.
        - Lo que no es GET exige la cabecera X-Panel-SP: una web ajena no puede
          enviarla sin un preflight CORS, y este servidor no responde OPTIONS.
        """
        puerto = int(CONFIG.get('puerto', 8765))
        host = (self.headers.get('Host') or '').lower()
        locales = {'127.0.0.1:%d' % puerto, 'localhost:%d' % puerto}
        publicos = {h.lower() for h in CONFIG.get('hosts_publicos', [])}
        t = tunel_info()
        if t and t.get('dominio'):
            publicos.add(t['dominio'].lower())
        if host not in locales and host not in publicos and not host.endswith('.trycloudflare.com'):
            return False
        origen = self.headers.get('Origin')
        if origen is not None and origen.lower() not in ('http://' + host, 'https://' + host):
            return False
        if metodo != 'GET' and self.headers.get('X-Panel-SP') != '1':
            return False
        return True

    def _despachar(self, metodo):
        # Comprobacion de salud de la plataforma (no revela nada ni exige sesion)
        if metodo == 'GET' and self.path == '/salud':
            return self._json({'ok': True})
        if not self._peticion_permitida(metodo):
            return self._json({'error': 'Peticion rechazada: solo se acepta desde el propio panel.'}, 403)
        url = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(url.query).items()}
        ruta = url.path
        try:
            # ---- Inicio de sesion (lo unico accesible sin sesion) ----
            if metodo == 'GET' and ruta == '/login':
                return self._html('login.html')
            if metodo == 'POST' and ruta == '/api/login':
                d = self._cuerpo_json()
                try:
                    token, rol = auth.iniciar_sesion(d.get('usuario'), d.get('clave'), self._ip())
                except PermissionError as ex:
                    return self._json({'error': str(ex)}, 401)
                return self._json({'ok': True, 'rol': rol}, cabeceras=[self._cookie(token, auth.DURACION_SESION)])

            # ---- Quien hace la peticion ----
            if auth.hay_usuarios():
                sesion = auth.sesion(self._token())
                if not sesion:
                    if ruta.startswith('/api/'):
                        return self._json({'error': 'Inicia sesion para continuar.', 'login': True}, 401)
                    return self._redirigir('/login')
            elif self._desde_internet():
                # Sin usuarios el panel solo funciona en este PC: nunca abierto a Internet.
                return self._json({'error': 'El panel no tiene usuarios. Crea uno antes de publicarlo.'}, 403)
            else:
                sesion = {'usuario': 'local', 'rol': 'admin'}

            if metodo == 'GET' and ruta == '/api/sesion':
                return self._json({'usuario': sesion['usuario'], 'rol': sesion['rol'], 'con_login': auth.hay_usuarios()})
            if metodo == 'POST' and ruta == '/api/logout':
                auth.cerrar_sesion(self._token())
                return self._json({'ok': True}, cabeceras=[self._cookie('', 0)])
            if not auth.permitido(sesion['rol'], metodo, ruta):
                return self._json({'error': 'Tu usuario no tiene permiso para esta accion.'}, 403)

            # ---- Usuarios y acceso remoto (solo admin, ver auth.SOLO_ADMIN) ----
            if ruta == '/api/usuarios':
                if metodo == 'GET':
                    return self._json({'usuarios': auth.listar_usuarios(), 'yo': sesion['usuario']})
                d = self._cuerpo_json()
                nombre = (d.get('usuario') or '').strip()
                if metodo == 'POST':
                    primera = not auth.hay_usuarios()
                    auth.guardar_usuario(nombre, d.get('clave') or '', d.get('rol') or None)
                    return self._json({'ok': True, 'primera': primera})
                if metodo == 'DELETE':
                    if nombre == sesion['usuario']:
                        raise ValueError('No puedes borrar tu propio usuario.')
                    auth.borrar_usuario(nombre)
                    return self._json({'ok': True})
            if metodo == 'GET' and ruta == '/api/acceso':
                return self._json(estado_acceso())
            if metodo == 'POST' and ruta == '/api/acceso/ngrok':
                if self._desde_internet():
                    raise ValueError('El enlace solo se puede configurar desde el PC donde corre el panel.')
                d = self._cuerpo_json()
                token = (d.get('authtoken') or '').strip()
                dominio = (d.get('dominio') or '').strip().lower().replace('https://', '').replace('http://', '').strip('/')
                if token and not re.match(r'^[A-Za-z0-9_\-]{20,}$', token):
                    raise ValueError('Ese authtoken no parece valido. Copialo entero desde dashboard.ngrok.com.')
                if not token and not estado_acceso()['ngrok_con_token']:
                    raise ValueError('Pega tu authtoken de ngrok (dashboard.ngrok.com > Your Authtoken).')
                if dominio and not re.match(r'^[a-z0-9-]+(\.[a-z0-9-]+)+$', dominio):
                    raise ValueError('Ese dominio no parece valido. Copialo de dashboard.ngrok.com > Domains.')
                return self._json(TAREAS.lanzar('ngrok', lambda: activar_ngrok(token, dominio), etiqueta='Activar enlace'))

            if metodo == 'GET' and ruta in ('/', '/index.html'):
                return self._html('index.html')
            if metodo == 'GET' and ruta == '/api/estado':
                return self._json({'servidor': EXA.estado(), 'panel': ESTADO_PANEL, 'config': CONFIG, 'instalador': INSTALADOR.estado()})
            if metodo == 'GET' and ruta == '/api/instalar-mod':
                return self._json(INSTALADOR.estado())
            if metodo == 'POST' and ruta == '/api/instalar-mod':
                return self._json(INSTALADOR.lanzar())
            if metodo == 'GET' and ruta == '/api/mod/jugadores':
                return self._json(MOD.jugadores())
            if metodo == 'GET' and ruta == '/api/mod/respaldos':
                return self._json(MOD.respaldos(self._jugador(q)))
            if metodo == 'GET' and ruta == '/api/mod/restaurar-estado':
                return self._json(MOD.estado())
            if metodo == 'POST' and ruta == '/api/mod/restaurar':
                d = self._cuerpo_json()
                return self._json(MOD.restaurar(d.get('jugador'), d.get('archivo')))
            if metodo == 'POST' and ruta.startswith('/api/servidor/'):
                return self._json(EXA.accion(ruta.rsplit('/', 1)[-1]))
            if metodo == 'POST' and ruta == '/api/comando':
                return self._json(ejecutar_comando(self._cuerpo_json().get('comando')))
            if metodo == 'GET' and ruta == '/api/consola/jugadores':
                return self._json(consola_jugadores())
            if metodo == 'GET' and ruta == '/api/consola/waystones':
                return self._json(consola_waystones(q.get('jugador')))
            if metodo == 'POST' and ruta == '/api/consola/tp':
                d = self._cuerpo_json()
                return self._json(consola_tp(d.get('jugador'), d.get('destino')))
            if metodo == 'GET' and ruta == '/api/jugadores':
                return self._json(jugadores_con_copias())
            if metodo == 'GET' and ruta == '/api/copias':
                return self._json(CAPTURADOR.lista_copias(self._jugador(q)))
            if metodo == 'GET' and ruta == '/api/copia':
                c = CAPTURADOR.leer_copia(self._jugador(q), q.get('id'))
                c.pop('snbt', None)
                return self._json(c)
            if metodo == 'POST' and ruta == '/api/capturar':
                # Captura (espejo de inventario) en segundo plano: el hilo HTTP
                # responde al instante y el frontend sigue el progreso.
                def _trabajo_captura():
                    TAREAS.fase('captura', 'ejecutando', 'Pidiendo la copia al servidor...')
                    r = CAPTURADOR.capturar('manual')
                    TAREAS.fase('captura', 'verificando', 'Guardando copias...')
                    return r
                return self._json(TAREAS.lanzar('captura', _trabajo_captura, etiqueta='Capturar inventario'))
            if metodo == 'POST' and ruta == '/api/espejo':
                def _trabajo_espejo():
                    TAREAS.fase('espejo', 'ejecutando', 'Leyendo el historial del servidor...')
                    r = CAPTURADOR.espejo()
                    TAREAS.fase('espejo', 'verificando', 'Guardando copias nuevas...')
                    return r
                return self._json(TAREAS.lanzar('espejo', _trabajo_espejo, etiqueta='Leer historial'))
            if metodo == 'GET' and ruta == '/api/tareas':
                # Estado de todas las tareas por tipo (para la barra de progreso).
                tipo = q.get('tipo')
                if tipo:
                    return self._json(TAREAS.estado(tipo))
                return self._json({t: TAREAS.estado(t) for t in ('spawn', 'captura', 'espejo')})
            if metodo == 'GET' and ruta == '/api/comparar':
                j = self._jugador(q)
                a = CAPTURADOR.leer_copia(j, q.get('a'))
                b = CAPTURADOR.leer_copia(j, q.get('b'))
                faltan, nuevos = items.comparar(a['items'], b['items'])
                return self._json({'antes': a['fecha'], 'despues': b['fecha'], 'faltan': faltan, 'nuevos': nuevos})
            if metodo == 'POST' and ruta == '/api/reponer':
                d = self._cuerpo_json()
                return self._json(reponer(d.get('jugador'), d.get('items') or []))
            # ---- Mods: Misiones Diarias ----
            if metodo == 'GET' and ruta == '/api/misiones':
                return self._json(misiones_lista())
            if metodo == 'POST' and ruta == '/api/misiones/reset':
                return self._json(misiones_reset(self._cuerpo_json().get('jugador')))
            if metodo == 'POST' and ruta == '/api/misiones/dar':
                d = self._cuerpo_json()
                return self._json(misiones_dar(d.get('jugador'), d.get('nivel')))
            # ---- Mods: Tienda / Economia ----
            if metodo == 'GET' and ruta == '/api/eco/saldos':
                return self._json(eco_saldos())
            if metodo == 'GET' and ruta == '/api/eco/mercado':
                return self._json(eco_mercado())
            if metodo == 'GET' and ruta == '/api/eco/valores':
                return self._json(eco_valores_leer())
            if metodo == 'PUT' and ruta == '/api/eco/valores':
                return self._json(eco_valores_guardar(self._cuerpo_json().get('texto')))
            if metodo == 'POST' and ruta == '/api/eco/reload':
                return self._json(eco_reload())
            if metodo == 'GET' and ruta == '/api/eco/categorias':
                return self._json(eco_categorias_leer())
            if metodo == 'PUT' and ruta == '/api/eco/categorias':
                return self._json(eco_categorias_guardar(self._cuerpo_json().get('porcentajes')))
            if metodo == 'GET' and ruta == '/api/eco/overrides':
                return self._json(eco_overrides_leer())
            if metodo == 'GET' and ruta == '/api/eco/buscar-items':
                return self._json(eco_buscar_items(q.get('q', '')))
            if metodo == 'GET' and ruta == '/api/eco/item-valor':
                return self._json(eco_item_valor(q.get('item', '')))
            if metodo == 'PUT' and ruta == '/api/eco/override':
                d = self._cuerpo_json()
                return self._json(eco_override_guardar(d.get('item_id'), d.get('valor')))
            if metodo == 'DELETE' and ruta == '/api/eco/override':
                return self._json(eco_override_eliminar(self._cuerpo_json().get('item_id')))
            if metodo == 'POST' and ruta == '/api/eco/creditos':
                d = self._cuerpo_json()
                return self._json(eco_creditos_ajustar(d.get('jugador'), d.get('modo'), d.get('cantidad')))
            if metodo == 'POST' and ruta == '/api/eco/listado-quitar':
                d = self._cuerpo_json()
                return self._json(eco_listado_quitar(d.get('tipo'), d.get('id')))
            if metodo == 'GET' and ruta == '/api/eco/catalogo':
                return self._json(eco_catalogo_leer())
            if metodo == 'PUT' and ruta == '/api/eco/catalogo':
                d = self._cuerpo_json()
                return self._json(eco_catalogo_guardar(d.get('item_id'), d.get('precio')))
            if metodo == 'DELETE' and ruta == '/api/eco/catalogo':
                return self._json(eco_catalogo_quitar(self._cuerpo_json().get('item_id')))
            # ---- Datapacks: entidades, vida de mobs, programador, jefes ----
            if metodo == 'GET' and ruta == '/api/datapacks':
                return self._json(DATAPACKS.listar())
            if metodo == 'GET' and ruta == '/api/entidades/buscar':
                solo = q.get('jefes') in ('1', 'true', 'si')
                return self._json(buscar_entidades(q.get('q', ''), solo_jefes=solo))
            if metodo == 'GET' and ruta == '/api/datapacks/vida':
                return self._json(DATAPACKS.vida_listar())
            if metodo == 'POST' and ruta == '/api/datapacks/vida':
                d = self._cuerpo_json()
                return self._json(DATAPACKS.vida_agregar(d.get('item_id'), d.get('nombre'), d.get('mult')))
            if metodo == 'DELETE' and ruta == '/api/datapacks/vida':
                return self._json(DATAPACKS.vida_quitar(self._cuerpo_json().get('item_id')))
            if metodo == 'GET' and ruta == '/api/datapacks/programador':
                return self._json(DATAPACKS.prog_listar())
            if metodo == 'POST' and ruta == '/api/datapacks/programador':
                d = self._cuerpo_json()
                return self._json(DATAPACKS.prog_programar(
                    d.get('comando'), d.get('segundos'), d.get('modo'), d.get('descripcion', '')))
            if metodo == 'DELETE' and ruta == '/api/datapacks/programador':
                return self._json(DATAPACKS.prog_cancelar(self._cuerpo_json().get('id')))
            if metodo == 'GET' and ruta == '/api/datapacks/dimensiones':
                return self._json(DATAPACKS.dimensiones_ui())
            if metodo == 'POST' and ruta == '/api/datapacks/spawn':
                # Spawn INMEDIATO: antes corria sincrono en el hilo HTTP
                # (/reload + espera + decenas de comandos de plataforma), por eso
                # "tardaba en llegar". Ahora va a un hilo de fondo y el endpoint
                # responde al instante; el frontend sigue la barra de progreso.
                d = self._cuerpo_json()
                nivel = d.get('nivel', 1)
                entidad = d.get('entidad')
                destino = d.get('destino')
                nombre = d.get('nombre')
                etiqueta = '%s [Nv %s]' % (nombre or (entidad or '').split(':')[-1], nivel)

                def _trabajo_spawn():
                    TAREAS.fase('spawn', 'ejecutando', 'Construyendo y ejecutando en el servidor...')
                    _, info = DATAPACKS.spawn_inmediato(entidad, destino, nivel, nombre)
                    TAREAS.fase('spawn', 'verificando', 'Confirmando aparicion...')
                    if info.get('arena'):
                        msg = ('Spawn de %s [Nv %s] en %s, plataforma aleatoria cerca de '
                               'X:%d Z:%d; se anuncio a todos.') % (
                            info['nombre_jefe'], nivel, info['dim_nombre'], info['x'], info['z'])
                    else:
                        msg = 'Spawn de %s [Nv %s] enviado.' % (info.get('nombre_jefe', ''), nivel)
                    return {'ok': True, 'mensaje': msg, 'info': info}

                return self._json(TAREAS.lanzar('spawn', _trabajo_spawn, etiqueta=etiqueta))
            if metodo == 'POST' and ruta == '/api/datapacks/spawn-programado':
                d = self._cuerpo_json()
                return self._json(DATAPACKS.spawn_programado(
                    d.get('entidad'), d.get('destino'), d.get('segundos'), d.get('nivel', 1), d.get('nombre')))
            if metodo == 'GET' and ruta == '/api/archivos':
                return self._json(EXA.info_archivo(q.get('ruta', '')))
            if metodo == 'GET' and ruta == '/api/archivo':
                datos = EXA.leer_archivo(q.get('ruta', ''))
                try:
                    texto = datos.decode('utf-8')
                    return self._json({'texto': texto, 'binario': False, 'tamano': len(datos)})
                except UnicodeDecodeError:
                    return self._json({'texto': '', 'binario': True, 'tamano': len(datos)})
            if metodo == 'PUT' and ruta == '/api/archivo':
                destino = q.get('ruta', '').strip('/')
                if not destino:
                    raise ValueError('Falta la ruta de destino')
                datos = self._cuerpo()
                EXA.escribir_archivo(destino, datos)
                return self._json({'ok': True, 'ruta': destino, 'tamano': len(datos)})
            if metodo == 'GET' and ruta == '/api/logs':
                return self._json(leer_log_local(q.get('fecha'), q.get('tipo'), q.get('filtro', ''), int(q.get('n', 500))))
            if metodo == 'POST' and ruta == '/api/config':
                d = self._cuerpo_json()
                if 'intervalo_seg' in d:
                    CONFIG['intervalo_seg'] = max(5, int(d['intervalo_seg']))
                if 'copias_por_jugador' in d:
                    CONFIG['copias_por_jugador'] = max(2, min(200, int(d['copias_por_jugador'])))
                if 'minutos_retencion' in d:
                    CONFIG['minutos_retencion'] = max(1, min(120, int(d['minutos_retencion'])))
                if 'solo_si_cambia' in d:
                    CONFIG['solo_si_cambia'] = bool(d['solo_si_cambia'])
                if 'modo_silencioso' in d:
                    CONFIG['modo_silencioso'] = bool(d['modo_silencioso'])
                    ESTADO_PANEL['silencio_aplicado'] = None
                    if EXA.estado().get('status') == 1:
                        CAPTURADOR.aplicar_silencio()
                guardar_config(CONFIG)
                return self._json({'ok': True, 'config': CONFIG})
            self._json({'error': 'Ruta no encontrada'}, 404)
        except exaroton.ErrorApi as ex:
            self._json({'error': str(ex)}, 502)
        except (ValueError, FileNotFoundError) as ex:
            self._json({'error': str(ex)}, 400)
        except Exception as ex:
            registrar_error('web %s %s: %s' % (metodo, ruta, traceback.format_exc()))
            self._json({'error': 'Error interno: %s' % ex}, 500)

    @staticmethod
    def _jugador(q):
        j = q.get('jugador', '')
        if not NOMBRE_OK.match(j):
            raise ValueError('Jugador no valido')
        return j

    def do_GET(self):
        self._despachar('GET')

    def do_POST(self):
        self._despachar('POST')

    def do_PUT(self):
        self._despachar('PUT')

    def do_DELETE(self):
        self._despachar('DELETE')


def main():
    threading.Thread(target=CAPTURADOR.bucle, daemon=True).start()
    threading.Thread(target=GUARDA_LOGS.bucle, daemon=True).start()
    # En la nube (Render) el puerto lo asigna la plataforma y hay que escuchar
    # en todas las interfaces; en el PC sigue siendo solo 127.0.0.1.
    if os.environ.get('PORT'):
        CONFIG['puerto'] = int(os.environ['PORT'])
    puerto = int(CONFIG.get('puerto', 8765))
    host = os.environ.get('PANEL_HOST', '127.0.0.1')
    servidor = ThreadingHTTPServer((host, puerto), Manejador)
    print('Panel SERVER PANAS en http://%s:%d' % (host, puerto), flush=True)
    servidor.serve_forever()


if __name__ == '__main__':
    main()
