"""
Cliente de la API de Exaroton.

Usa las credenciales que ya existen en ServerPanas\\automatizacion:
- exaroton-token.xml : token cifrado con DPAPI (solo tu usuario de Windows lo abre)
- config.json        : serverId
El token se descifra en memoria y nunca se escribe ni se muestra.
"""
import ctypes
import http.client
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
if os.name == 'nt':  # DPAPI solo existe en Windows; en la nube el token llega por entorno
    from ctypes import wintypes

AUTOMATIZACION = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'automatizacion'))


if os.name == 'nt':
    class _Blob(ctypes.Structure):
        _fields_ = [('cbData', wintypes.DWORD), ('pbData', ctypes.POINTER(ctypes.c_char))]


def _dpapi_descifrar(datos):
    buf = ctypes.create_string_buffer(datos, len(datos))
    entrada = _Blob(len(datos), ctypes.cast(buf, ctypes.POINTER(ctypes.c_char)))
    salida = _Blob()
    if not ctypes.windll.crypt32.CryptUnprotectData(ctypes.byref(entrada), None, None, None, None, 0, ctypes.byref(salida)):
        raise RuntimeError('No se pudo descifrar el token (debe ejecutarse con tu usuario de Windows).')
    try:
        return ctypes.string_at(salida.pbData, salida.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(salida.pbData)


def cargar_token():
    # En el servidor de la nube (Linux) el token viene del entorno del servicio
    # (/etc/serverpanas/panel.env, solo legible por root y el servicio).
    if os.environ.get('EXAROTON_TOKEN'):
        return os.environ['EXAROTON_TOKEN'].strip()
    if os.name != 'nt':
        raise RuntimeError('Falta EXAROTON_TOKEN en el entorno del servicio.')
    ruta = os.path.join(AUTOMATIZACION, 'exaroton-token.xml')
    with open(ruta, 'rb') as f:
        crudo = f.read()
    if crudo[:2] in (b'\xff\xfe', b'\xfe\xff'):
        xml = crudo.decode('utf-16')
    else:
        xml = crudo.decode('utf-8-sig', errors='replace')
    m = re.search(r'<SS>([0-9a-fA-F]+)</SS>', xml)
    if not m:
        raise RuntimeError('exaroton-token.xml no tiene un token valido. Ejecuta 1-configurar-token.ps1')
    return _dpapi_descifrar(bytes.fromhex(m.group(1))).decode('utf-16-le')


def cargar_server_id():
    if os.environ.get('EXAROTON_SERVER_ID'):
        return os.environ['EXAROTON_SERVER_ID'].strip()
    with open(os.path.join(AUTOMATIZACION, 'config.json'), 'r', encoding='utf-8-sig') as f:
        return json.load(f)['serverId']


class ErrorApi(Exception):
    pass


class Exaroton:
    def __init__(self):
        # El token y el serverId NO cambian durante la vida del panel: se leen
        # UNA sola vez aqui (descifrado DPAPI incluido) y se reutilizan en cada
        # peticion. No se vuelven a leer de disco en cada request.
        self._token = cargar_token()
        self.server_id = cargar_server_id()
        self._base = 'https://api.exaroton.com/v1/servers/' + self.server_id
        self._candado = threading.Lock()
        self._pool = []
        self._pool_candado = threading.Lock()
        # Cache corta del estado del servidor. estado() hace un round-trip a la
        # API de Exaroton (~1 s); como el panel lo consulta muy seguido (refresco
        # cada pocos segundos + varios endpoints internos), cachearlo unos
        # segundos evita pagar esa latencia una y otra vez. Se puede forzar.
        self._estado_cache = None
        self._estado_cache_ts = 0.0
        self._estado_ttl = 2.5
        self._estado_candado = threading.Lock()

    def _tomar_conexion(self, host, timeout):
        """Reutiliza una conexion abierta: abrir TLS cada vez cuesta casi 1 s por llamada."""
        with self._pool_candado:
            conn = self._pool.pop() if self._pool else None
        if conn is None:
            return http.client.HTTPSConnection(host, timeout=timeout), False
        conn.timeout = timeout
        if conn.sock is not None:
            conn.sock.settimeout(timeout)
        return conn, True

    def _devolver_conexion(self, conn, respuesta):
        with self._pool_candado:
            if respuesta.will_close or len(self._pool) >= 6:
                conn.close()
            else:
                self._pool.append(conn)

    def _peticion(self, metodo, ruta, cuerpo=None, crudo=False, timeout=20, content_type=None):
        cabeceras = {'Authorization': 'Bearer ' + self._token, 'User-Agent': 'ServerPanas-Panel/1.0'}
        datos = None
        if content_type is not None:
            # Peticion especial (p.ej. crear directorio con inode/directory):
            # se manda sin cuerpo pero con el Content-Type indicado.
            cabeceras['Content-Type'] = content_type
            datos = b''
        elif cuerpo is not None:
            if isinstance(cuerpo, (bytes, bytearray)):
                datos = bytes(cuerpo)
                cabeceras['Content-Type'] = 'application/octet-stream'
            else:
                datos = json.dumps(cuerpo).encode('utf-8')
                cabeceras['Content-Type'] = 'application/json'
        partes = urllib.parse.urlsplit(self._base + ruta)
        destino = partes.path + ('?' + partes.query if partes.query else '')
        for intento in (1, 2):
            conn, reutilizada = self._tomar_conexion(partes.netloc, timeout)
            try:
                conn.request(metodo, destino, body=datos, headers=cabeceras)
                r = conn.getresponse()
                contenido = r.read()
                break
            except (http.client.HTTPException, OSError) as e:
                conn.close()
                # Una conexion guardada puede haber caducado: se reintenta una vez con otra nueva
                if not (reutilizada and intento == 1):
                    raise ErrorApi('Sin conexion con Exaroton: %s' % e)
        self._devolver_conexion(conn, r)
        if r.status >= 400:
            raise ErrorApi('Exaroton respondio %s: %s' % (r.status, contenido.decode('utf-8', errors='replace')[:300]))
        if crudo:
            return contenido
        return json.loads(contenido.decode('utf-8')) if contenido else {}

    @staticmethod
    def _ruta_archivo(ruta):
        return urllib.parse.quote(ruta.strip('/'), safe='/')

    def estado(self, forzar=False):
        """Estado del servidor. Cachea el resultado unos segundos (ver __init__)
        para no hacer un round-trip a Exaroton en cada consulta. forzar=True
        salta la cache y pide el estado fresco (acciones que lo necesiten)."""
        ahora = time.time()
        if not forzar and self._estado_cache is not None and (ahora - self._estado_cache_ts) < self._estado_ttl:
            return self._estado_cache
        with self._estado_candado:
            # Otro hilo pudo refrescar mientras esperabamos el candado.
            ahora = time.time()
            if not forzar and self._estado_cache is not None and (ahora - self._estado_cache_ts) < self._estado_ttl:
                return self._estado_cache
            datos = self._peticion('GET', '')['data']
            self._estado_cache = datos
            self._estado_cache_ts = time.time()
            return datos

    def invalidar_estado(self):
        """Olvida el estado cacheado (p.ej. tras start/stop/restart) para que el
        siguiente estado() refleje el cambio sin esperar a que caduque la cache."""
        self._estado_cache = None
        self._estado_cache_ts = 0.0

    def comando(self, texto):
        with self._candado:
            return self._peticion('POST', '/command/', {'command': texto})

    def logs(self):
        return (self._peticion('GET', '/logs/').get('data') or {}).get('content') or ''

    def accion(self, nombre):
        if nombre not in ('start', 'stop', 'restart'):
            raise ErrorApi('Accion no valida')
        r = self._peticion('GET', '/%s/' % nombre)
        # El estado va a cambiar: olvida la cache para reflejarlo cuanto antes.
        self.invalidar_estado()
        return r

    # ---------------- Cache de archivos ----------------
    # Cada lectura a exaroton tarda ~0.5-0.7 s. Guardamos el resultado (o el error, p. ej.
    # "no existe") CACHE_SEG segundos; escribir o crear algo borra su entrada. Un hilo del
    # panel refresca en segundo plano lo mas usado (modo fresco), asi que lo normal es leer
    # siempre de la cache. Dos peticiones simultaneas al mismo archivo hacen una sola llamada.
    CACHE_SEG = 90
    _hilo = threading.local()

    def fresco(self):
        """Contexto: las lecturas dentro ignoran la cache (y la actualizan)."""
        exa = self

        class _Ctx:
            def __enter__(self_):
                exa._hilo.fresco = getattr(exa._hilo, 'fresco', 0) + 1

            def __exit__(self_, *a):
                exa._hilo.fresco -= 1
        return _Ctx()

    def _cache_init(self):
        if not hasattr(self, '_cache_arch'):
            self._cache_arch = {}
            self._cache_candado = threading.Lock()
            self._cache_vuelo = {}

    def _cacheado_archivo(self, clave, leer, usar_cache=True):
        self._cache_init()
        ahora = time.time()
        if usar_cache and not getattr(self._hilo, 'fresco', 0):
            with self._cache_candado:
                e = self._cache_arch.get(clave)
            if e and ahora - e[0] < self.CACHE_SEG:
                if e[2]:
                    raise e[2]
                return e[1]
        with self._cache_candado:
            candado = self._cache_vuelo.setdefault(clave, threading.Lock())
        with candado:
            if usar_cache and not getattr(self._hilo, 'fresco', 0):
                with self._cache_candado:
                    e = self._cache_arch.get(clave)
                if e and e[0] >= ahora and time.time() - e[0] < self.CACHE_SEG:   # otro hilo ya lo trajo
                    if e[2]:
                        raise e[2]
                    return e[1]
            try:
                valor = leer()
                err = None
            except ErrorApi as ex:
                valor, err = None, ex
            with self._cache_candado:
                self._cache_arch[clave] = (time.time(), valor, err)
            if err:
                raise err
            return valor

    def invalidar_archivo(self, ruta):
        self._cache_init()
        r = ruta.strip('/')
        padre = r.rsplit('/', 1)[0] if '/' in r else ''
        with self._cache_candado:
            for k in ('data:' + r, 'info:' + r, 'info:' + padre):
                self._cache_arch.pop(k, None)

    def info_archivo(self, ruta, cache=True):
        r = ruta.strip('/')
        return self._cacheado_archivo('info:' + r, lambda: self._peticion('GET', '/files/info/' + self._ruta_archivo(ruta))['data'], cache)

    def leer_archivo(self, ruta, cache=True):
        # Timeout holgado: algunos archivos (storage NBT, usercache) pueden pesar.
        r = ruta.strip('/')
        return self._cacheado_archivo('data:' + r, lambda: self._peticion('GET', '/files/data/' + self._ruta_archivo(ruta), crudo=True, timeout=60), cache)

    def escribir_archivo(self, ruta, datos):
        # Timeout holgado: subir el .jar del mod son varios MB.
        try:
            return self._peticion('PUT', '/files/data/' + self._ruta_archivo(ruta), cuerpo=datos, timeout=120)
        finally:
            self.invalidar_archivo(ruta)

    def crear_directorio(self, ruta):
        """Crea un directorio en el servidor. La API de Exaroton crea carpetas
        enviando un PUT con Content-Type 'inode/directory'. Idempotente: si ya
        existe, no falla (devuelve éxito igualmente)."""
        try:
            return self._peticion('PUT', '/files/data/' + self._ruta_archivo(ruta),
                                  content_type='inode/directory')
        finally:
            self.invalidar_archivo(ruta)
