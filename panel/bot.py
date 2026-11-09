"""Bot de avisos para los jugadores.

Dos partes que comparten una configuracion (world/datapacks/PanelBot/panel_config.json):

1. Datapack PanelBot (corre dentro del servidor, funciona aunque el panel este caido):
   bienvenida al entrar, guia la primera vez, consejos rotativos a cada jugador con
   su propio reloj, aviso de que cae la noche y aviso al morir con las coordenadas.

2. Avisos de misiones (los manda el panel): lee el progreso de FTB Quests de cada
   jugador conectado y le dice SU siguiente mision, las recompensas que tiene sin
   reclamar y anuncia a todos cuando alguien completa una mision.
"""
import json
import re
import threading
import time
import traceback

DP_BOT = 'world/datapacks/PanelBot'
NS = 'panelbot'
ARCH_CONFIG = DP_BOT + '/panel_config.json'
DIR_CAPITULOS = 'config/ftbquests/quests/chapters'
DIR_PROGRESO = 'world/ftbquests'
DIR_EQUIPOS = 'world/ftbteams/party'
PACK_FORMAT = 15

CONFIG_DEFECTO = {
    'activo': True,
    'nombre': 'Bot Panas',
    'bienvenida': 'Bienvenido de nuevo, {jugador}. Pulsa la tecla del libro de misiones para ver por dónde ibas.',
    'primera_vez': [
        'Hola {jugador}, bienvenido a Server Panas.',
        'Abre el libro de misiones: el capítulo Guía explica los mods del servidor.',
        'Las waystones te dejan viajar rápido. Si mueres, tus cosas quedan en una tumba.',
    ],
    'consejos_activo': True,
    'consejos_min': 15,
    'consejos': [
        'Las misiones dan recompensas: revisa el libro de misiones de vez en cuando.',
        'De noche aparecen élites de colores. Cuanto más rojo, más peligroso y mejor botín.',
        'Descubre waystones para poder volver a ellas desde cualquier otra.',
        'Si mueres, tus objetos quedan en una tumba en el lugar de la muerte.',
        'Usa la tienda del servidor para vender lo que te sobra.',
        'Juega con los demás: los jefes son mucho más fáciles en grupo.',
    ],
    'noche_activo': True,
    'noche': 'Cae la noche: aparecen élites. Ten cuidado ahí fuera.',
    'muerte_activo': True,
    'muerte': 'Has muerto, {jugador}. Tus cosas están en una tumba donde caíste:',
    'misiones_activo': True,
    'misiones_min': 20,
    'anunciar_completadas': True,
}

_COLOR = re.compile(r'&[0-9a-fk-or]', re.I)


def limpiar_color(t):
    return _COLOR.sub('', t or '').strip()


# ---------------------------------------------------------------------------
#  SNBT de FTB (sin comas, claves sin comillas, numeros con sufijo)
# ---------------------------------------------------------------------------
_TOKEN = re.compile(r'\s*(?:([{}\[\]:,])|"((?:[^"\\]|\\.)*)"|([^\s{}\[\]:,"]+))')


def parse_ftb(texto):
    pos, n = 0, len(texto)
    toks = []
    while pos < n:
        m = _TOKEN.match(texto, pos)
        if not m or m.end() == pos:
            break
        pos = m.end()
        if m.group(1):
            if m.group(1) != ',':
                toks.append(('p', m.group(1)))
        elif m.group(2) is not None:
            toks.append(('s', re.sub(r'\\(.)', r'\1', m.group(2))))
        elif m.group(3):
            toks.append(('w', m.group(3)))
    i = 0

    def valor():
        nonlocal i
        tipo, v = toks[i]
        i += 1
        if tipo == 'p' and v == '{':
            d = {}
            while toks[i] != ('p', '}'):
                clave = toks[i][1]
                i += 2  # clave y ':'
                d[clave] = valor()
            i += 1
            return d
        if tipo == 'p' and v == '[':
            lista = []
            while toks[i] != ('p', ']'):
                lista.append(valor())
            i += 1
            return lista
        return v
    return valor() if toks else {}


# ---------------------------------------------------------------------------
#  Datapack
# ---------------------------------------------------------------------------
def _componente(texto, nombre, color='yellow'):
    """Texto del bot a JSON de tellraw; {jugador} se cambia por el nombre del que lo recibe."""
    partes = ['', {'text': '[%s] ' % nombre, 'color': 'aqua', 'bold': True}]
    trozos = (texto or '').split('{jugador}')
    for k, t in enumerate(trozos):
        if t:
            partes.append({'text': t, 'color': color, 'bold': False})
        if k < len(trozos) - 1:
            partes.append({'selector': '@s', 'color': 'gold', 'bold': False})
    return json.dumps(partes, ensure_ascii=False)


def archivos_datapack(cfg):
    nombre = (cfg.get('nombre') or 'Bot').strip()[:24] or 'Bot'
    fn = 'data/%s/functions' % NS
    a = {
        'pack.mcmeta': json.dumps({'pack': {'pack_format': PACK_FORMAT,
                                            'description': 'Server Panas - Panel: bot de avisos.'}}, ensure_ascii=False),
        'data/minecraft/tags/functions/load.json': json.dumps({'values': ['%s:init' % NS]}),
    }
    init = ['scoreboard objectives add pb dummy',
            'scoreboard objectives add pb_sal minecraft.custom:minecraft.leave_game',
            'scoreboard objectives add pb_muerte deathCount',
            'scoreboard objectives add pb_t dummy',
            'scoreboard objectives add pb_i dummy',
            'schedule clear %s:bucle' % NS]
    if cfg.get('activo'):
        init.append('schedule function %s:bucle 2s replace' % NS)
    bucle = ['execute as @a[tag=!pb_conocido] run function %s:primera' % NS,
             'execute as @a[scores={pb_sal=1..}] run function %s:vuelve' % NS]
    primera = ['tag @s add pb_conocido', 'scoreboard players set @s pb_sal 0', 'scoreboard players set @s pb_t 0']
    primera += ['tellraw @s %s' % _componente(l, nombre) for l in cfg.get('primera_vez') or [] if l.strip()]
    vuelve = ['scoreboard players set @s pb_sal 0', 'scoreboard players set @s pb_t 0']
    if (cfg.get('bienvenida') or '').strip():
        vuelve.append('tellraw @s %s' % _componente(cfg['bienvenida'], nombre))
    consejos = [c for c in cfg.get('consejos') or [] if c.strip()]
    if cfg.get('consejos_activo') and consejos:
        seg = max(1, int(cfg.get('consejos_min') or 15)) * 60
        bucle += ['scoreboard players add @a pb_t 1',
                  'execute as @a[scores={pb_t=%d..}] run function %s:consejo' % (seg, NS)]
        c = ['scoreboard players set @s pb_t 0', 'scoreboard players add @s pb_i 0']
        c += ['execute if score @s pb_i matches %d run tellraw @s %s' % (k, _componente(t, nombre, 'white'))
              for k, t in enumerate(consejos)]
        c += ['scoreboard players add @s pb_i 1',
              'execute if score @s pb_i matches %d.. run scoreboard players set @s pb_i 0' % len(consejos)]
        a[fn + '/consejo.mcfunction'] = '\n'.join(c) + '\n'
    if cfg.get('noche_activo') and (cfg.get('noche') or '').strip():
        bucle += ['execute store result score #dia pb run time query daytime',
                  'execute if score #dia pb matches 12500..13500 unless score #noche pb matches 1 run function %s:noche' % NS,
                  'execute unless score #dia pb matches 12500..23999 run scoreboard players set #noche pb 0']
        a[fn + '/noche.mcfunction'] = ('scoreboard players set #noche pb 1\n'
                                       'tellraw @a %s\n' % _componente(cfg['noche'], nombre, 'red'))
    if cfg.get('muerte_activo'):
        bucle.append('execute as @a[scores={pb_muerte=1..}] run function %s:muerte' % NS)
        msg = json.loads(_componente(cfg.get('muerte') or 'Has muerto. Tu tumba está en:', nombre, 'gray'))
        msg.append({'text': ' ', 'bold': False})
        msg.append({'nbt': 'LastDeathLocation.pos', 'entity': '@s', 'color': 'gold', 'bold': False})
        a[fn + '/muerte.mcfunction'] = ('scoreboard players set @s pb_muerte 0\n'
                                        'tellraw @s %s\n' % json.dumps(msg, ensure_ascii=False))
    bucle.append('schedule function %s:bucle 1s replace' % NS)
    a[fn + '/init.mcfunction'] = '\n'.join(init) + '\n'
    a[fn + '/bucle.mcfunction'] = '\n'.join(bucle) + '\n'
    a[fn + '/primera.mcfunction'] = '\n'.join(primera) + '\n'
    a[fn + '/vuelve.mcfunction'] = '\n'.join(vuelve) + '\n'
    return a


def validar_config(d):
    cfg = dict(CONFIG_DEFECTO)
    for k, v in CONFIG_DEFECTO.items():
        if k not in d:
            continue
        x = d[k]
        if isinstance(v, bool):
            cfg[k] = bool(x)
        elif isinstance(v, int):
            try:
                cfg[k] = max(1, min(240, int(x)))
            except (TypeError, ValueError):
                raise ValueError('Valor no válido en %s.' % k)
        elif isinstance(v, list):
            if isinstance(x, str):
                x = x.splitlines()
            cfg[k] = [str(t).strip()[:250] for t in x if str(t).strip()][:30]
        else:
            cfg[k] = str(x or '').strip()[:250]
    if not cfg['nombre']:
        cfg['nombre'] = 'Bot'
    return cfg


# ---------------------------------------------------------------------------
#  Bot
# ---------------------------------------------------------------------------
class Bot:
    def __init__(self, exa, errorapi, registrar_error, mapa_uuid_nombre):
        self.EXA = exa
        self.ErrorApi = errorapi
        self.registrar_error = registrar_error
        self.mapa_uuid_nombre = mapa_uuid_nombre
        self._cfg = None
        self._cfg_t = 0
        self._capitulos = None
        self._capitulos_t = 0
        self._equipos = ({}, 0)
        self._completadas = {}    # jugador -> set de misiones completadas (ultima lectura)
        self._ultimo_aviso = {}   # jugador -> time() del ultimo aviso de mision
        self._conectados = set()
        self._candado = threading.Lock()

    # ---- configuracion ----
    def config(self, forzar=False):
        if self._cfg is None or forzar or time.time() - self._cfg_t > 120:
            try:
                d = json.loads(self.EXA.leer_archivo(ARCH_CONFIG).decode('utf-8'))
                self._cfg = validar_config(d)
                self._cfg['instalado'] = True
            except (self.ErrorApi, ValueError):
                self._cfg = dict(CONFIG_DEFECTO, activo=False, instalado=False)
            self._cfg_t = time.time()
        return dict(self._cfg)

    def guardar(self, d):
        cfg = validar_config(d)
        if self.EXA.estado(forzar=True).get('status') != 1:
            raise ValueError('El servidor está apagado; enciéndelo para aplicar el bot.')
        archivos = archivos_datapack(cfg)
        dirs = sorted({(DP_BOT + '/' + r).rsplit('/', 1)[0] for r in archivos}, key=lambda x: x.count('/'))
        for ruta in sorted({p for d_ in dirs for p in self._ancestros(d_)}, key=lambda x: x.count('/')):
            try:
                self.EXA.crear_directorio(ruta)
            except self.ErrorApi:
                pass
        # Borrar funciones opcionales que ya no se usan (consejo/noche/muerte)
        for opc in ('consejo', 'noche', 'muerte'):
            ruta = 'data/%s/functions/%s.mcfunction' % (NS, opc)
            if ruta not in archivos:
                archivos[ruta] = '# desactivado\n'
        for ruta, texto in archivos.items():
            self.EXA.escribir_archivo(DP_BOT + '/' + ruta, texto.encode('utf-8'))
        self.EXA.escribir_archivo(ARCH_CONFIG, json.dumps(cfg, ensure_ascii=False, indent=2).encode('utf-8'))
        self.EXA.comando('reload')
        self._cfg, self._cfg_t = dict(cfg, instalado=True), time.time()
        return {'ok': True, 'config': self._cfg}

    @staticmethod
    def _ancestros(ruta):
        partes = ruta.split('/')
        return ['/'.join(partes[:k]) for k in range(1, len(partes) + 1)]

    # ---- misiones ----
    def capitulos(self):
        """Lista ordenada de misiones: dicts con id, titulo, capitulo, deps, recompensas."""
        if self._capitulos is not None and time.time() - self._capitulos_t < 1800:
            return self._capitulos
        caps = []
        for h in self.EXA.info_archivo(DIR_CAPITULOS).get('children') or []:
            nombre = h.get('name', '')
            if not nombre.endswith('.snbt'):
                continue
            try:
                caps.append(parse_ftb(self.EXA.leer_archivo(DIR_CAPITULOS + '/' + nombre).decode('utf-8')))
            except Exception:
                self.registrar_error('bot: no se pudo leer el capitulo %s' % nombre)
        try:
            grupos = [g.get('id') for g in parse_ftb(self.EXA.leer_archivo(
                DIR_CAPITULOS.rsplit('/', 1)[0] + '/chapter_groups.snbt').decode('utf-8')).get('chapter_groups') or []]
        except Exception:
            grupos = []

        def grupo(c):  # sin grupo -> primero; grupos en su orden
            return grupos.index(c.get('group')) + 1 if c.get('group') in grupos else 0
        caps.sort(key=lambda c: (grupo(c), int(c.get('order_index') or 0)))
        principal = min((grupo(c) for c in caps), default=0)
        misiones = []
        for c in caps:
            cap = limpiar_color(c.get('title') or c.get('filename') or '')
            guia = int(c.get('order_index') or 0) < 0 or grupo(c) != principal  # p. ej. "Guia Mods"
            for q in c.get('quests') or []:
                if not isinstance(q, dict) or not q.get('id'):
                    continue
                misiones.append({
                    'id': q['id'].upper(), 'titulo': limpiar_color(q.get('title') or '') or '(sin título)',
                    'subtitulo': limpiar_color(q.get('subtitle') or ''), 'capitulo': cap,
                    'deps': [x.upper() for x in q.get('dependencies') or [] if isinstance(x, str)],
                    'recompensas': [r['id'].upper() for r in q.get('rewards') or [] if isinstance(r, dict) and r.get('id')],
                    'tareas': len(q.get('tasks') or []), 'guia': guia,
                })
        self._capitulos, self._capitulos_t = misiones, time.time()
        return misiones

    def uuids(self):
        """Nombre -> UUID segun usercache.json; si un nombre sale dos veces gana el mas reciente."""
        try:
            cache = json.loads(self.EXA.leer_archivo('usercache.json').decode('utf-8'))
        except (self.ErrorApi, ValueError):
            return {v: k for k, v in self.mapa_uuid_nombre().items()}
        cache.sort(key=lambda x: x.get('expiresOn') or '')
        return {x['name']: x['uuid'].lower() for x in cache if x.get('name') and x.get('uuid')}

    def _equipo_de(self, uuid):
        """Archivo de progreso de un jugador: su equipo (party) si tiene, si no el personal."""
        mapa, t = self._equipos
        if time.time() - t > 300:
            mapa = {}
            try:
                for h in self.EXA.info_archivo(DIR_EQUIPOS).get('children') or []:
                    nombre = h.get('name', '')
                    if nombre.endswith('.snbt'):
                        d = parse_ftb(self.EXA.leer_archivo(DIR_EQUIPOS + '/' + nombre).decode('utf-8'))
                        for miembro in (d.get('ranks') or {}):
                            mapa[miembro.lower()] = nombre[:-5]
            except self.ErrorApi:
                pass
            self._equipos = (mapa, time.time())
        return mapa.get(uuid.lower(), uuid.lower())

    def progreso(self, nombre, uuid):
        crudo = self.EXA.leer_archivo('%s/%s.snbt' % (DIR_PROGRESO, self._equipo_de(uuid)))
        d = parse_ftb(crudo.decode('utf-8'))
        completadas = {k.upper() for k in (d.get('completed') or {})}
        sin_guion = uuid.replace('-', '').lower()
        reclamadas = set()
        for k in d.get('claimed_rewards') or {}:
            quien, _, rid = k.partition(':')
            if quien.lower() == sin_guion or not rid:
                reclamadas.add((rid or quien).upper())
        misiones = self.capitulos()
        disponibles = [m for m in misiones if m['id'] not in completadas and m['tareas']
                       and all(dep in completadas for dep in m['deps'])]
        disponibles.sort(key=lambda m: m['guia'])  # la guia, al final
        sin_reclamar = [m for m in misiones if m['id'] in completadas
                        and any(r not in reclamadas for r in m['recompensas'])]
        return {'jugador': nombre, 'completadas': completadas, 'total': len(misiones),
                'siguientes': disponibles[:3], 'sin_reclamar': sin_reclamar}

    # ---- mensajes ----
    def _tellraw(self, destino, partes):
        cfg = self.config()
        self.EXA.comando('tellraw %s %s' % (destino, json.dumps(
            ['', {'text': '[%s] ' % cfg['nombre'], 'color': 'aqua', 'bold': True}] + partes, ensure_ascii=False)))

    def avisar_misiones(self, p):
        partes = []
        if p['siguientes']:
            m = p['siguientes'][0]
            partes += [{'text': 'Tu siguiente misión: ', 'color': 'yellow', 'bold': False},
                       {'text': m['titulo'], 'color': 'gold', 'bold': True},
                       {'text': ' (%s)' % m['capitulo'], 'color': 'gray', 'bold': False}]
            if m['subtitulo']:
                partes.append({'text': ' — ' + m['subtitulo'], 'color': 'white', 'italic': True, 'bold': False})
            if len(p['siguientes']) > 1:
                partes.append({'text': '. También: ' + ', '.join(x['titulo'] for x in p['siguientes'][1:]),
                               'color': 'gray', 'bold': False})
        elif len(p['completadas']) >= p['total']:
            partes.append({'text': '¡Has completado todas las misiones!', 'color': 'green', 'bold': False})
        if p['sin_reclamar']:
            n = len(p['sin_reclamar'])
            partes.append({'text': ('\n' if partes else '') + 'Tienes %d misión%s con recompensa sin reclamar: ábrelas en el libro de misiones.'
                           % (n, '' if n == 1 else 'es'), 'color': 'green', 'bold': False})
        if partes:
            self._tellraw(p['jugador'], partes)
            self._ultimo_aviso[p['jugador']] = time.time()
        return bool(partes)

    def avisar_ahora(self, jugador):
        uuid = self.uuids().get(jugador)
        if not uuid:
            raise ValueError('Jugador desconocido.')
        p = self.progreso(jugador, uuid)
        enviado = self.avisar_misiones(p)
        return {'ok': True, 'enviado': enviado,
                'siguientes': [m['titulo'] for m in p['siguientes']],
                'sin_reclamar': len(p['sin_reclamar']), 'completadas': len(p['completadas']), 'total': p['total']}

    # ---- bucle ----
    def vuelta(self):
        cfg = self.config()
        if not (cfg.get('instalado') and cfg.get('activo') and cfg.get('misiones_activo')):
            return
        st = self.EXA.estado()
        if st.get('status') != 1:
            self._conectados = set()
            return
        ahora = set((st.get('players') or {}).get('list') or [])
        nuevos = ahora - self._conectados
        self._conectados = ahora
        if not ahora:
            return
        uuids = self.uuids()
        cada = cfg['misiones_min'] * 60
        for nombre in sorted(ahora):
            uuid = uuids.get(nombre)
            if not uuid:
                continue
            try:
                p = self.progreso(nombre, uuid)
            except self.ErrorApi:
                continue  # aun sin progreso guardado
            antes = self._completadas.get(nombre)
            self._completadas[nombre] = p['completadas']
            if antes is not None and cfg.get('anunciar_completadas'):
                titulos = {m['id']: m['titulo'] for m in self.capitulos()}
                for qid in sorted(p['completadas'] - antes):
                    if qid in titulos:
                        self._tellraw('@a', [{'text': nombre, 'color': 'gold', 'bold': False},
                                             {'text': ' ha completado la misión ', 'color': 'yellow', 'bold': False},
                                             {'text': titulos[qid], 'color': 'green', 'bold': True}])
            if nombre in nuevos or time.time() - self._ultimo_aviso.get(nombre, 0) >= cada:
                self.avisar_misiones(p)

    def bucle(self):
        time.sleep(20)
        while True:
            try:
                self.vuelta()
            except Exception:
                self.registrar_error('bot: %s' % traceback.format_exc())
            time.sleep(90)
