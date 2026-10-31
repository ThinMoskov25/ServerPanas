"""
Modulo DATAPACKS del panel (uso admin).
=======================================

Reune tres herramientas que trabajan contra los datapacks del MUNDO
(world/datapacks/) via la API de archivos de Exaroton y aplican los cambios en
caliente con /reload (sin reiniciar el servidor). Todo es server-side: una vez
escrito y recargado, funciona aunque el panel este cerrado.

1) LISTAR datapacks del mundo
   - Lee world/datapacks/ (EXA.info_archivo). Muestra lo que hay en el servidor
     (zips y carpetas), que es lo que el admin realmente tiene cargado.

2) VIDA DE MOBS (jefes x2 vida)
   - El datapack original "JefesDuros" (JefesDuros.zip) refuerza la vida de unos
     pocos jefes. No lo tocamos (sigue intacto). En su lugar, el panel gestiona
     un datapack COMPANERO desempaquetado:  world/datapacks/PanelVidaMobs/  que
     añade mas mobs al ajuste de vida sin romper el original.
   - El admin elige el mob por NOMBRE REAL (indice de entidades) y un
     MULTIPLICADOR de vida (x2, x3...). El panel escribe la funcion del mob y la
     registra en reforzar.mcfunction del datapack companero, y hace /reload.
   - Tecnica (1.20.1): a cada mob objetivo sin el tag se le aplica
       attribute @s minecraft:generic.max_health modifier add <uuid> pvm_vida <k> multiply_base
     donde k = multiplicador-1 (x2 -> k=1.0). Luego se le cura a tope. Funciona
     para CUALQUIER mob sin saber su vida base. En 1.20.1 la operacion se llama
     "multiply_base" (en 1.20.5+ paso a "add_multiplied_base").

3) PROGRAMADOR DE COMANDOS (por SEGUNDOS, server-side)
   - El admin escribe un COMANDO y un INTERVALO/RETARDO en SEGUNDOS. Dos modos:
       (i)  una vez tras X s   (retardo)
       (ii) repetir cada X s   (la funcion se re-agenda a si misma al final)
   - Implementacion: datapack de functions  world/datapacks/PanelProgramador/ .
     El panel escribe una funcion por tarea (panelprog:tarea_<id>) con el comando
     y, si es repetitiva, un  "schedule function panelprog:tarea_<id> <ticks>t"
     al final (ticks = segundos * 20). Tras /reload, el panel ARMA el primer
     disparo ejecutando el comando  "schedule function ... <ticks>t"  por API.
   - Los schedule de Minecraft se guardan en el mundo: sobreviven al cierre del
     panel (y a un reinicio del servidor). NO sobreviven a un /reload: por eso el
     panel re-arma el schedule DESPUES de recargar. Cancelar una tarea borra su
     funcion, reescribe el indice, hace /reload y manda "schedule ... clear".

4) SPAWNEADOR DE JEFES (con NIVELES 1-5)
   - Spawn inmediato: summon directo por consola (en coords o en la posicion de
     un jugador con execute at).
   - Spawn programado por SEGUNDOS: reutiliza el PROGRAMADOR (tarea de un solo
     disparo cuyo comando es el summon). Asi "spawnear ender dragon en 2000 s"
     ocurre aunque el panel este cerrado.
   - NIVEL 1 a 5: cada jefe se puede reforzar de nivel 1 (normal, sin cambios) a
     nivel 5 (muy reforzado). El escalado NO necesita conocer la vida/dano base
     del jefe: se aplica con MODIFICADORES de atributo multiply_base (el mismo
     metodo general de "Vida de mobs"). El summon marca al jefe con un Tag unico
     y un CustomName "<Nombre> [Nv N]" visible; luego se le aplican:
         attribute @e[tag=...,limit=1] minecraft:generic.max_health   modifier add <uuid> nivel_vida <kv> multiply_base
         attribute @e[tag=...,limit=1] minecraft:generic.attack_damage modifier add <uuid> nivel_dano <kd> multiply_base
     y se le cura a tope (effect instant_health). multiply_base suma k*base, con
     k = multiplicador-1, asi que funciona sobre CUALQUIER base. Escala por nivel
     N (NIVEL_VIDA/NIVEL_DANO mas abajo):
         vida  = 1 + (N-1)*0.75   -> N1 x1, N2 x1.75, N3 x2.5, N4 x3.25, N5 x4
         dano  = 1 + (N-1)*0.40   -> N1 x1, N2 x1.4,  N3 x1.8, N4 x2.2,  N5 x2.6
     Nivel 1 solo pone el CustomName (sin modificadores: stats normales).
   - Tanto el spawn INMEDIATO como el PROGRAMADO aplican el nivel: el inmediato
     ejecuta la secuencia de comandos por consola; el programado la escribe
     completa dentro de la funcion de la tarea (una linea por comando).

Nombres de tarea/mob se sanean para que sean nombres de funcion validos
([a-z0-9_]). Toda escritura usa EXA.escribir_archivo; todo /reload y schedule
usa EXA.comando.
"""
import json
import random
import re
import time
import uuid as uuidlib

# ---- Rutas de datapacks en el mundo ----
DP_BASE = 'world/datapacks'
DP_VIDA = DP_BASE + '/PanelVidaMobs'
DP_PROG = DP_BASE + '/PanelProgramador'
DP_JEFES = DP_BASE + '/PanelJefes'

# Namespaces de las funciones.
NS_VIDA = 'panelvidamobs'
NS_PROG = 'panelprog'
NS_JEFES = 'paneljefes'

# Indice interno de tareas/mobs que mantiene el panel DENTRO del datapack, como
# un .json de datos propios (no es leido por Minecraft, solo por el panel). Asi
# el estado vive junto al datapack en el servidor y no depende del PC del admin.
ARCH_PROG_INDICE = DP_PROG + '/panel_tareas.json'
ARCH_VIDA_INDICE = DP_VIDA + '/panel_mobs.json'

PACK_FORMAT = 15  # 1.20.1

ID_ENTIDAD_OK = re.compile(r'^[a-z0-9_.\-]+:[a-z0-9_./\-]+$')
NOMBRE_OK = re.compile(r'^[A-Za-z0-9_]{1,16}$')

# ---- Niveles de jefe (1 a 5) ----
# Multiplicador sobre la base del jefe por NIVEL. Nivel 1 = normal (x1, sin
# cambios). multiply_base suma k*base con k = mult - 1, por eso no hace falta
# conocer la vida/dano base de cada jefe: funciona sobre cualquier entidad.
#   vida = 1 + (N-1)*0.75  -> 1, 1.75, 2.5, 3.25, 4
#   dano = 1 + (N-1)*0.40  -> 1, 1.4,  1.8, 2.2,  2.6
NIVEL_MIN, NIVEL_MAX = 1, 5


def nivel_vida_mult(n):
    return 1.0 + (int(n) - 1) * 0.75


def nivel_dano_mult(n):
    return 1.0 + (int(n) - 1) * 0.40


# ==========================================================================
#  ARENA / PLATAFORMA ALEATORIA POR DIMENSION
# ==========================================================================
# El jefe ya no cae sobre un jugador: se construye una PLATAFORMA CIRCULAR
# (arena pequena) en una UBICACION ALEATORIA de la DIMENSION APROPIADA y se
# anuncia a TODO el servidor con el lugar.
#
# DIMENSIONES SOPORTADAS. Cada una lleva:
#   - id        : id real de la dimension en el juego (/execute in <id> run ...)
#   - nombre    : etiqueta legible para el anuncio global
#   - y         : altura FIJA segura donde se construye la plataforma y aparece
#                 el jefe. Se elige robusta sin conocer el terreno:
#                   overworld  Y=120  (por encima de casi todo el relieve)
#                   nether     Y=60   (sobre el mar de lava, lejos del techo Y=128)
#                   end        Y=70   (altura de la isla central / plataforma propia)
#                   otras      Y=100  (neutro)
#   - rango     : radio del cuadrado donde se eligen X/Z aleatorias alrededor del
#                 centro (0,0). Mas chico en dimensiones chicas (end).
#   - suelo     : bloque de relleno de la plataforma (tematico).
#   - borde     : bloque del anillo exterior / pilares (tematico).
# Las dimensiones de MODS son opcionales: si el pack no las tiene, el comando
# "/execute in <dim>" falla silenciosamente para ESE spawn, por eso el mapeo
# jefe->dimension cae a overworld de forma segura cuando no se garantiza la dim.
DIMENSIONES = {
    'overworld': {
        'id': 'minecraft:overworld', 'nombre': 'el Mundo Superior',
        'y': 120, 'rango': 6000, 'suelo': 'minecraft:stone_bricks',
        'borde': 'minecraft:chiseled_stone_bricks',
    },
    'nether': {
        'id': 'minecraft:the_nether', 'nombre': 'el Nether',
        'y': 60, 'rango': 3000, 'suelo': 'minecraft:nether_bricks',
        'borde': 'minecraft:basalt',
    },
    'end': {
        'id': 'minecraft:the_end', 'nombre': 'el End',
        'y': 70, 'rango': 400, 'suelo': 'minecraft:end_stone',
        'borde': 'minecraft:obsidian',
    },
    # Dimensiones de mods conocidas del pack (si existen; si no, el mapeo cae a
    # overworld). Materiales neutros/tematicos.
    'twilight': {
        'id': 'twilightforest:twilight_forest', 'nombre': 'el Bosque Crepuscular',
        'y': 100, 'rango': 5000, 'suelo': 'minecraft:mossy_stone_bricks',
        'borde': 'minecraft:stone_bricks',
    },
    'otherside': {
        'id': 'deeperdarker:otherside', 'nombre': 'el Otro Lado',
        'y': 100, 'rango': 2000, 'suelo': 'minecraft:deepslate_bricks',
        'borde': 'minecraft:polished_deepslate',
    },
}

# Dimensiones que el admin puede FORZAR desde la UI (override manual). El valor
# 'auto' deja que decida el mapeo jefe->dimension.
DIM_OVERRIDE_UI = ('auto', 'overworld', 'nether', 'end', 'twilight', 'otherside')

# MAPEO jefe -> dimension objetivo (habitat natural de cada jefe). Lo que no
# este aqui cae a 'overworld'. Para jefes de mods cuya dimension no se garantiza
# por id, se usa overworld de forma segura (ver nota de DIMENSIONES).
JEFE_DIMENSION = {
    # ---- Vanilla ----
    'minecraft:ender_dragon': 'end',
    'minecraft:wither': 'overworld',          # seguro en overworld (evita techo del nether)
    'minecraft:warden': 'overworld',
    'minecraft:elder_guardian': 'overworld',
    # ---- Cataclysm (estructuras overworld/nether/end segun el jefe) ----
    'cataclysm:ignis': 'nether',
    'cataclysm:netherite_monstrosity': 'nether',
    'cataclysm:the_leviathan': 'overworld',   # oceano del overworld
    'cataclysm:ender_guardian': 'end',
    'cataclysm:the_harbinger': 'overworld',
    'cataclysm:maledictus': 'overworld',
    'cataclysm:wadjet': 'overworld',
    'cataclysm:scylla': 'overworld',
    'cataclysm:coralssus': 'overworld',
    'cataclysm:cindaria': 'overworld',
    # ---- Twilight Forest -> su dimension (si existe; si no, overworld) ----
    'twilightforest:naga': 'twilight',
    'twilightforest:lich': 'twilight',
    'twilightforest:hydra': 'twilight',
    'twilightforest:ur_ghast': 'twilight',
    'twilightforest:snow_queen': 'twilight',
    'twilightforest:alpha_yeti': 'twilight',
    'twilightforest:minoshroom': 'twilight',
    'twilightforest:knight_phantom': 'twilight',
    # ---- Ice and Fire (overworld) ----
    'iceandfire:fire_dragon': 'overworld',
    'iceandfire:ice_dragon': 'overworld',
    'iceandfire:lightning_dragon': 'overworld',
    'iceandfire:cyclops': 'overworld',
    'iceandfire:hydra': 'overworld',
    'iceandfire:gorgon': 'overworld',
    'iceandfire:sea_serpent': 'overworld',
    # ---- Mowzie's Mobs (overworld) ----
    'mowziesmobs:ferrous_wroughtnaut': 'overworld',
    'mowziesmobs:frostmaw': 'overworld',
    'mowziesmobs:umvuthi': 'overworld',
    'mowziesmobs:naga': 'overworld',
    'mowziesmobs:sculptor': 'overworld',
    # ---- Deeper Darker ----
    'deeperdarker:stalker': 'otherside',
}

ARENA_RADIO = 8  # radio de la plataforma circular (bloques); diametro ~17
ARENA_MURO_ALTO = 4  # alto del muro/borde de contencion alrededor del circulo


# ==========================================================================
#  LOOT POR (DIMENSION x NIVEL)  -- Tarea 2
# ==========================================================================
# Al morir un jefe spawneado por el panel, se sueltan items acordes a SU NIVEL
# (1-5) y a la DIMENSION donde estaba la plataforma. Regla de oro: NUNCA dar
# loot de otra dimension (nada de netherita por un jefe de overworld, etc.).
#
# Cada tabla es una lista de (item_id, cantidad) que crecen con el nivel. La
# funcion de muerte suelta estos items como entidades de item en la posicion de
# la plataforma (summon item ... en el centro) + una recompensa de XP escalada.
#
# Diseno por dimension:
#   OVERWORLD -> hierro, oro, diamante, esmeralda, lapis, carbon (escala por Nv)
#   NETHER    -> quartz, oro, blaze_rod, magma, glowstone; ancient_debris/
#                netherite_scrap SOLO en niveles altos (4-5)
#   END       -> ender_pearl, chorus_fruit, shulker_shell, dragon_breath;
#                elytra SOLO en nivel 5 (raro)
#   OTRAS     -> loot neutro estilo overworld (sin items de nether/end)
#
# Formato: LOOT[dimkey][nivel] = [(item, cantidad), ...]
def _loot_overworld(n):
    tabla = [
        ('minecraft:coal', 4 + n * 4),
        ('minecraft:iron_ingot', n * 3),
        ('minecraft:gold_ingot', max(0, (n - 1) * 2)),
        ('minecraft:lapis_lazuli', max(0, (n - 1) * 3)),
    ]
    if n >= 3:
        tabla.append(('minecraft:diamond', n - 2))        # N3:1, N4:2, N5:3
        tabla.append(('minecraft:emerald', n - 2))
    if n >= 5:
        tabla.append(('minecraft:diamond', 3))            # extra en N5 (total 6)
        tabla.append(('minecraft:emerald', 4))
    return tabla


def _loot_nether(n):
    tabla = [
        ('minecraft:quartz', 4 + n * 3),
        ('minecraft:gold_ingot', n * 2),
        ('minecraft:glowstone', n * 2),
        ('minecraft:magma_block', n),
    ]
    if n >= 2:
        tabla.append(('minecraft:blaze_rod', n - 1))      # N2:1 ... N5:4
    if n >= 4:
        # Recursos de netherita SOLO en niveles altos (4-5).
        tabla.append(('minecraft:netherite_scrap', n - 3))  # N4:1, N5:2
    if n >= 5:
        tabla.append(('minecraft:ancient_debris', 1))
    return tabla


def _loot_end(n):
    tabla = [
        ('minecraft:ender_pearl', n),
        ('minecraft:chorus_fruit', 2 + n * 2),
    ]
    if n >= 2:
        tabla.append(('minecraft:dragon_breath', n - 1))
    if n >= 3:
        tabla.append(('minecraft:shulker_shell', n - 2))   # N3:1, N4:2, N5:3
    if n >= 5:
        tabla.append(('minecraft:elytra', 1))              # raro, solo N5
    return tabla


def tabla_loot(dim_key, nivel):
    """Devuelve la lista [(item, cantidad), ...] para una dimension y nivel.
    Dimensiones de mods / desconocidas -> loot neutro estilo overworld."""
    n = max(NIVEL_MIN, min(NIVEL_MAX, int(nivel)))
    if dim_key == 'nether':
        items = _loot_nether(n)
    elif dim_key == 'end':
        items = _loot_end(n)
    else:
        items = _loot_overworld(n)  # overworld + otras dimensiones (neutro)
    # Compactar cantidades repetidas del mismo item en una sola entrada.
    acum = {}
    orden = []
    for it, c in items:
        if c <= 0:
            continue
        if it not in acum:
            orden.append(it)
        acum[it] = acum.get(it, 0) + int(c)
    return [(it, acum[it]) for it in orden]


def xp_por_nivel(n):
    """XP (orbes) que suelta el jefe al morir, escalada por nivel."""
    return 20 * int(max(NIVEL_MIN, min(NIVEL_MAX, n))) ** 2  # N1:20 ... N5:500


def _dim_de_jefe(eid, override='auto'):
    """Decide la clave de dimension para un jefe. override != 'auto' manda."""
    ov = (override or 'auto').strip().lower()
    if ov in DIMENSIONES:
        return ov
    return JEFE_DIMENSION.get(eid, 'overworld')


def _coords_aleatorias(dim_cfg):
    """X/Z aleatorias dentro del rango de la dimension (centradas en 0,0). La Y
    es la altura fija segura de la dimension. Se evita quedar pegado al centro
    (minimo 500 bloques en overworld/nether) para no spawnear sobre el spawn."""
    rango = int(dim_cfg['rango'])
    minimo = 500 if rango > 1500 else 80
    def _coord():
        v = random.randint(minimo, rango)
        return v if random.random() < 0.5 else -v
    return _coord(), int(dim_cfg['y']), _coord()


def _slug(texto):
    """Convierte un texto a un identificador valido de funcion ([a-z0-9_])."""
    s = re.sub(r'[^a-z0-9_]+', '_', (texto or '').strip().lower())
    s = re.sub(r'_+', '_', s).strip('_')
    return s or 'x'


def _pack_mcmeta(descripcion):
    return json.dumps({'pack': {'pack_format': PACK_FORMAT, 'description': descripcion}},
                      ensure_ascii=False, indent=2).encode('utf-8')


def _tag_funcion(valores):
    return json.dumps({'values': valores}, ensure_ascii=False, indent=2).encode('utf-8')


class Datapacks:
    """Fachada del modulo. Recibe el cliente Exaroton ya construido y un par de
    helpers del panel (ejecutar_comando para ver la salida en consola y un
    normalizador de entidad). No guarda estado en el PC: todo vive en el server."""

    def __init__(self, exa, errorapi, registrar_error):
        self.EXA = exa
        self.ErrorApi = errorapi
        self.registrar_error = registrar_error

    # ------------------------------------------------------------------
    #  Utilidades comunes
    # ------------------------------------------------------------------
    def _leer_json(self, ruta, por_defecto):
        try:
            crudo = self.EXA.leer_archivo(ruta)
        except self.ErrorApi:
            return dict(por_defecto)
        try:
            return json.loads(crudo.decode('utf-8'))
        except Exception:
            return dict(por_defecto)

    def _escribir_json(self, ruta, obj):
        self.EXA.escribir_archivo(ruta, json.dumps(obj, ensure_ascii=False, indent=2).encode('utf-8'))

    def _crear_dirs(self, *rutas):
        """Crea cada directorio y todos sus ancestros bajo DP_BASE. La API de
        Exaroton no crea padres automaticamente, asi que los creamos en orden
        de menos a mas profundo. Idempotente."""
        pendientes = set()
        for ruta in rutas:
            partes = ruta.strip('/').split('/')
            acum = ''
            for p in partes:
                acum = (acum + '/' + p) if acum else p
                pendientes.add(acum)
        for d in sorted(pendientes, key=lambda x: x.count('/')):
            try:
                self.EXA.crear_directorio(d)
            except self.ErrorApi:
                # Puede fallar si ya existe o si un ancestro es un .zip; seguimos.
                pass

    def _reload(self):
        """Aplica los datapacks en caliente. Devuelve la salida de consola."""
        return self.EXA.comando('reload')

    def _online(self):
        try:
            return self.EXA.estado().get('status') == 1
        except Exception:
            return False

    def _entidad_valida(self, eid):
        eid = (eid or '').strip()
        if ':' not in eid:
            eid = 'minecraft:' + eid
        eid = eid.lower()
        if not ID_ENTIDAD_OK.match(eid):
            raise ValueError('Id de entidad no valido: %s' % eid)
        return eid

    # ==================================================================
    #  1) LISTAR DATAPACKS DEL MUNDO
    # ==================================================================
    def listar(self):
        """Datapacks presentes en world/datapacks/ (zips y carpetas)."""
        try:
            info = self.EXA.info_archivo(DP_BASE)
        except self.ErrorApi as ex:
            return {'ok': False, 'datapacks': [], 'mensaje': 'No pude leer world/datapacks/ (%s).' % ex}
        hijos = info.get('children') or []
        packs = []
        for h in hijos:
            nombre = h.get('name', '')
            es_dir = bool(h.get('isDirectory'))
            base = nombre[:-4] if nombre.lower().endswith('.zip') else nombre
            packs.append({
                'nombre': nombre,
                'base': base,
                'tipo': 'carpeta' if es_dir else 'zip',
                'gestionable': base in (DP_VIDA.rsplit('/', 1)[-1], DP_PROG.rsplit('/', 1)[-1]),
                'es_vida': base == 'JefesDuros' or base == DP_VIDA.rsplit('/', 1)[-1],
            })
        packs.sort(key=lambda x: x['nombre'].lower())
        return {'ok': True, 'datapacks': packs}

    # ==================================================================
    #  2) VIDA DE MOBS (datapack companero PanelVidaMobs)
    # ==================================================================
    def _vida_fn_dir(self):
        return DP_VIDA + '/data/' + NS_VIDA + '/functions'

    def _asegurar_datapack_vida(self, mobs):
        """(Re)escribe por completo el datapack companero de vida a partir del
        indice de mobs (lista de dicts {id, nombre, mult}). Es idempotente."""
        fn = self._vida_fn_dir()
        self._crear_dirs(fn, fn + '/buff', DP_VIDA + '/data/minecraft/tags/functions')

        # pack.mcmeta
        self.EXA.escribir_archivo(
            DP_VIDA + '/pack.mcmeta',
            _pack_mcmeta('Server Panas - Panel: vida extra de mobs (companero de JefesDuros).'))

        # tags load/tick
        self.EXA.escribir_archivo(
            DP_VIDA + '/data/minecraft/tags/functions/load.json',
            _tag_funcion([NS_VIDA + ':init']))
        self.EXA.escribir_archivo(
            DP_VIDA + '/data/minecraft/tags/functions/tick.json',
            _tag_funcion([NS_VIDA + ':tick']))

        # init: scoreboard-reloj propio
        self.EXA.escribir_archivo(fn + '/init.mcfunction', (
            '# Datapack companero gestionado por el PANEL. No editar a mano.\n'
            'scoreboard objectives add pvm_sys dummy\n'
            'scoreboard players set #pvm_timer pvm_sys 0\n').encode('utf-8'))

        # tick: cada segundo llama a reforzar
        self.EXA.escribir_archivo(fn + '/tick.mcfunction', (
            'scoreboard players add #pvm_timer pvm_sys 1\n'
            'execute if score #pvm_timer pvm_sys matches 20.. run function ' + NS_VIDA + ':reforzar\n'
            'execute if score #pvm_timer pvm_sys matches 20.. run scoreboard players set #pvm_timer pvm_sys 0\n').encode('utf-8'))

        # una funcion buff por mob + el despachador reforzar
        lineas_ref = ['# Refuerza la vida de los mobs que agrego el admin desde el panel.\n']
        for m in mobs:
            eid = m['id']
            slug = _slug(eid.replace(':', '_'))
            tag = 'pvm_' + slug
            lineas_ref.append(
                'execute as @e[type=%s,tag=!%s] run function %s:buff/%s\n' % (eid, tag, NS_VIDA, slug))
            mult = float(m.get('mult', 2.0))
            k = max(0.0, mult - 1.0)  # multiply_base suma k*base
            mod_uuid = m.get('uuid') or '0'
            buff = (
                '# %s (%s): x%s de vida. Aplica modificador multiply_base y cura a tope.\n'
                'attribute @s minecraft:generic.max_health modifier add %s pvm_vida %s multiply_base\n'
                'tag @s add %s\n'
                'effect give @s minecraft:instant_health 1 255 true\n'
            ) % (m.get('nombre', eid), eid, _fmt(mult), mod_uuid, _fmt(k), tag)
            self.EXA.escribir_archivo(fn + '/buff/%s.mcfunction' % slug, buff.encode('utf-8'))
        self.EXA.escribir_archivo(fn + '/reforzar.mcfunction', ''.join(lineas_ref).encode('utf-8'))

        # indice propio del panel
        self._escribir_json(ARCH_VIDA_INDICE, {'mobs': mobs})

    def vida_listar(self):
        """Mobs que el panel añadio al ajuste de vida (datapack companero) +
        los del datapack original JefesDuros (solo lectura, informativo)."""
        idx = self._leer_json(ARCH_VIDA_INDICE, {'mobs': []})
        mobs = idx.get('mobs') or []
        return {
            'ok': True,
            'mobs': mobs,
            'nota_original': 'Los jefes del datapack original (JefesDuros): Ignis, Dragon del End, '
                             'Naga (Twilight), Warden ya tienen x2 vida. Aqui agregas MAS mobs sin tocar el original.',
        }

    def vida_agregar(self, item_id, nombre, mult):
        """Agrega (o actualiza) un mob al ajuste de vida y aplica con /reload."""
        eid = self._entidad_valida(item_id)
        try:
            mult = float(mult)
        except (TypeError, ValueError):
            raise ValueError('El multiplicador debe ser un numero (ej. 2 para x2 vida).')
        if mult < 1.0 or mult > 100.0:
            raise ValueError('Multiplicador fuera de rango (1 a 100).')

        idx = self._leer_json(ARCH_VIDA_INDICE, {'mobs': []})
        mobs = idx.get('mobs') or []
        existente = next((m for m in mobs if m['id'] == eid), None)
        if existente:
            existente['mult'] = mult
            existente['nombre'] = nombre or existente.get('nombre') or eid
            if not existente.get('uuid'):
                existente['uuid'] = str(uuidlib.uuid4())
        else:
            mobs.append({'id': eid, 'nombre': nombre or eid, 'mult': mult,
                         'uuid': str(uuidlib.uuid4())})

        self._asegurar_datapack_vida(mobs)
        salida = self._reload()
        return {
            'ok': True,
            'mensaje': 'Mob %s agregado al ajuste de vida (x%s) y recargado en vivo.' % (eid, _fmt(mult)),
            'enviado': salida.get('enviado') if isinstance(salida, dict) else 'reload',
            'mobs': mobs,
        }

    def vida_quitar(self, item_id):
        """Quita un mob del ajuste de vida gestionado por el panel y recarga.
        Nota: los mobs ya reforzados conservan su vida hasta morir (el tag ya
        esta puesto); los nuevos ya no se reforzaran."""
        eid = self._entidad_valida(item_id)
        idx = self._leer_json(ARCH_VIDA_INDICE, {'mobs': []})
        mobs = [m for m in (idx.get('mobs') or []) if m['id'] != eid]
        self._asegurar_datapack_vida(mobs)
        self._reload()
        return {'ok': True, 'mensaje': 'Mob %s quitado del ajuste de vida y recargado.' % eid, 'mobs': mobs}

    # ==================================================================
    #  3) PROGRAMADOR DE COMANDOS (server-side, por segundos)
    # ==================================================================
    def _prog_fn_dir(self):
        return DP_PROG + '/data/' + NS_PROG + '/functions'

    def _asegurar_base_prog(self):
        """Crea el esqueleto del datapack programador si falta (pack.mcmeta y
        tags vacias). Las funciones de tarea se escriben aparte."""
        self._crear_dirs(self._prog_fn_dir(), DP_PROG + '/data/minecraft/tags/functions')
        self.EXA.escribir_archivo(
            DP_PROG + '/pack.mcmeta',
            _pack_mcmeta('Server Panas - Panel: programador de comandos por segundos (schedule).'))
        # tag load vacia (solo para que el pack sea valido aunque no haya tareas)
        self.EXA.escribir_archivo(
            DP_PROG + '/data/minecraft/tags/functions/load.json',
            _tag_funcion([]))

    def _escribir_funcion_tarea(self, tarea):
        """Escribe panelprog:tarea_<id>.mcfunction con el/los comando(s) y, si
        repite, el re-agendado al final. tarea['comando'] puede ser un string
        (un solo comando) o una lista de strings (secuencia, p.ej. summon de
        jefe con nivel + modificadores)."""
        fn = self._prog_fn_dir()
        tid = tarea['id']
        ticks = int(round(tarea['segundos'] * 20))
        comandos = tarea['comando']
        if isinstance(comandos, str):
            comandos = [comandos]
        lineas = [c.lstrip('/') for c in comandos]
        cuerpo = '# Tarea del panel: %s\n%s\n' % (tarea.get('descripcion', tid), '\n'.join(lineas))
        if tarea['modo'] == 'repetir':
            cuerpo += 'schedule function %s:tarea_%s %dt replace\n' % (NS_PROG, tid, ticks)
        self.EXA.escribir_archivo(fn + '/tarea_%s.mcfunction' % tid, cuerpo.encode('utf-8'))

    def prog_listar(self):
        idx = self._leer_json(ARCH_PROG_INDICE, {'tareas': []})
        return {'ok': True, 'tareas': idx.get('tareas') or []}

    def prog_programar(self, comando, segundos, modo, descripcion=''):
        """Programa un comando. modo 'una_vez' o 'repetir'. Escribe la funcion,
        recarga el datapack y ARMA el schedule por API (persiste aunque el panel
        se cierre)."""
        # comando puede venir como string (una linea) o lista (secuencia, p.ej.
        # el summon de un jefe con nivel + sus modificadores de atributo).
        if isinstance(comando, (list, tuple)):
            comando = [str(c).strip() for c in comando if str(c).strip()]
        else:
            comando = (comando or '').strip()
        if not comando:
            raise ValueError('Escribe un comando.')
        comando_lista = comando if isinstance(comando, list) else [comando]
        if any(c.lstrip('/').lower().startswith('reload') for c in comando_lista):
            raise ValueError('No se puede programar /reload (rompe las propias tareas).')
        try:
            segundos = float(segundos)
        except (TypeError, ValueError):
            raise ValueError('El tiempo debe ser un numero de segundos.')
        if segundos < 1 or segundos > 7 * 24 * 3600:
            raise ValueError('Tiempo fuera de rango (1 segundo a 7 dias).')
        if modo not in ('una_vez', 'repetir'):
            raise ValueError('Modo no valido (una_vez | repetir).')

        tid = '%s_%s' % (int(time.time()), _slug(descripcion)[:16] or 'cmd')
        ticks = int(round(segundos * 20))
        tarea = {
            'id': tid,
            'comando': comando,
            'segundos': segundos,
            'ticks': ticks,
            'modo': modo,
            'descripcion': descripcion or comando,
            'creada': time.strftime('%Y-%m-%d %H:%M:%S'),
        }

        self._asegurar_base_prog()
        self._escribir_funcion_tarea(tarea)

        idx = self._leer_json(ARCH_PROG_INDICE, {'tareas': []})
        tareas = [t for t in (idx.get('tareas') or []) if t.get('id') != tid]
        tareas.append(tarea)
        self._escribir_json(ARCH_PROG_INDICE, {'tareas': tareas})

        # /reload para que el server vea la funcion nueva, y LUEGO armamos el
        # schedule (el reload borra schedules previos; por eso va despues).
        if not self._online():
            return {'ok': False,
                    'mensaje': 'El servidor esta apagado. Enciendelo para programar (el schedule necesita el server en marcha).',
                    'tareas': tareas}
        self._reload()
        time.sleep(0.4)
        self.EXA.comando('schedule function %s:tarea_%s %dt replace' % (NS_PROG, tid, ticks))

        detalle = ('Se ejecutara UNA vez en %s s (%d ticks).' % (_fmt(segundos), ticks)
                   if modo == 'una_vez' else
                   'Se repetira cada %s s (%d ticks).' % (_fmt(segundos), ticks))
        return {'ok': True,
                'mensaje': 'Comando programado. ' + detalle + ' Funciona aunque cierres el panel.',
                'tarea': tarea, 'tareas': tareas}

    def prog_cancelar(self, tid):
        """Cancela una tarea: borra su funcion, reescribe el indice, recarga y
        limpia el schedule pendiente."""
        if not re.match(r'^[A-Za-z0-9_]+$', tid or ''):
            raise ValueError('Id de tarea no valido.')
        idx = self._leer_json(ARCH_PROG_INDICE, {'tareas': []})
        tareas = [t for t in (idx.get('tareas') or []) if t.get('id') != tid]
        self._escribir_json(ARCH_PROG_INDICE, {'tareas': tareas})
        # Vaciar la funcion (deja un no-op) para que el schedule pendiente no haga nada.
        fn = self._prog_fn_dir()
        try:
            self.EXA.escribir_archivo(fn + '/tarea_%s.mcfunction' % tid,
                                      ('# Tarea cancelada desde el panel.\n').encode('utf-8'))
        except self.ErrorApi:
            pass
        if self._online():
            self._reload()
            time.sleep(0.3)
            self.EXA.comando('schedule clear %s:tarea_%s' % (NS_PROG, tid))
        return {'ok': True, 'mensaje': 'Tarea cancelada y schedule limpiado.', 'tareas': tareas}

    # ==================================================================
    #  4) SPAWNEADOR DE JEFES
    # ==================================================================
    def dimensiones_ui(self):
        """Opciones del desplegable de dimension para la UI (override manual):
        'Automatico' + las dimensiones soportadas. 'auto' deja decidir el mapeo
        jefe->dimension."""
        opciones = [{'clave': 'auto', 'nombre': 'Automatico (habitat del jefe)'}]
        for clave in ('overworld', 'nether', 'end', 'twilight', 'otherside'):
            cfg = DIMENSIONES.get(clave)
            if cfg:
                opciones.append({'clave': clave, 'nombre': cfg['nombre'], 'id': cfg['id']})
        return {'ok': True, 'dimensiones': opciones}

    def spawn_inmediato(self, entidad, destino, nivel=1, nombre=None):
        """Spawnea un jefe YA en el nivel indicado (1-5).

        destino puede ser el modo CLASICO (se conserva):
          {'tipo':'jugador','jugador':X} o {'tipo':'coords','x':..,'y':..,'z':..}
        o el modo ARENA (nuevo, el pedido por defecto):
          {'tipo':'arena','dimension':'auto'|'overworld'|...}
        En modo arena se construye una plataforma circular en una ubicacion
        aleatoria de la dimension apropiada y se anuncia a todo el servidor.
        Devuelve (salida_ultima, comando_resumen)."""
        eid = self._entidad_valida(entidad)
        nivel = self._nivel_valido(nivel)
        if not self._online():
            raise ValueError('El servidor esta apagado; enciendelo para spawnear.')
        cmds, info = self._cmds_summon(eid, destino, nivel, nombre)
        if info.get('arena'):
            # Modo arena: la secuencia puede tener ~100 comandos (plataforma +
            # muro). En vez de mandarlos uno a uno por la API (lento y propenso a
            # timeouts del cliente), los METEMOS en una funcion de un solo uso
            # paneljefes:spawn_<token>, hacemos /reload (que ademas carga las
            # watch_/dead_) y la ejecutamos con UN solo 'function'. Asi todo es
            # instantaneo y el schedule del vigilante prende bien.
            token = info['token']
            fn = self._jefes_fn_dir()
            cuerpo = '# Spawn inmediato del panel: %s\n%s\n' % (
                info.get('nombre_jefe', eid), '\n'.join(c.lstrip('/') for c in cmds))
            self.EXA.escribir_archivo(fn + '/spawn_%s.mcfunction' % token, cuerpo.encode('utf-8'))
            self._reload()
            time.sleep(0.6)
            salida = self.EXA.comando('function %s:spawn_%s' % (NS_JEFES, token))
        else:
            salida = None
            for c in cmds:
                salida = self.EXA.comando(c)
                time.sleep(0.15)
        # El comando "resumen" es el summon; el resto son plataforma, modificadores
        # y anuncio. Devolvemos tambien la info de ubicacion para el mensaje del panel.
        return salida, info

    def spawn_programado(self, entidad, destino, segundos, nivel=1, nombre=None):
        """Programa el summon (con nivel) para dentro de X segundos (one-shot)
        reutilizando el programador server-side. TODA la secuencia (forceload +
        plataforma + summon + atributos de nivel + ANUNCIO global) va dentro de
        la funcion de la tarea, asi el tellraw sale en el momento del spawn (no
        al programar)."""
        eid = self._entidad_valida(entidad)
        nivel = self._nivel_valido(nivel)
        cmds, info = self._cmds_summon(eid, destino, nivel, nombre)
        desc = 'spawn_%s_nv%d' % (_slug(eid.split(':')[-1]), nivel)
        r = self.prog_programar(cmds, segundos, 'una_vez', descripcion=desc)
        if r.get('ok'):
            r['info'] = info
            if info.get('arena'):
                r['mensaje'] = ('Spawn de %s [Nv %d] programado en %s s, en %s sobre plataforma '
                                'aleatoria cerca de X:%d Z:%d. Se anunciara a todos. '
                                'Ocurrira aunque cierres el panel.') % (
                    info['nombre_jefe'], nivel, _fmt(float(segundos)),
                    info['dim_nombre'], info['x'], info['z'])
            else:
                r['mensaje'] = 'Spawn de %s [Nv %d] programado en %s s. Ocurrira aunque cierres el panel.' % (
                    eid, nivel, _fmt(float(segundos)))
        return r

    def _nivel_valido(self, nivel):
        try:
            n = int(nivel)
        except (TypeError, ValueError):
            raise ValueError('Nivel no valido (usa 1 a 5).')
        if n < NIVEL_MIN or n > NIVEL_MAX:
            raise ValueError('Nivel fuera de rango (1 a 5).')
        return n

    # ==================================================================
    #  DETECCION DE MUERTE DEL JEFE + LOOT + BORRADO DE PLATAFORMA
    #  (Tareas 2 y 4)  -- datapack PanelJefes
    # ==================================================================
    # METODO (documentado): en datapack 1.20.1 no hay un evento directo de
    # "muerte de entidad". El enfoque fiable es un VIGILANTE por spawn:
    #   1) Al invocar el jefe se le pone un TAG unico del sistema (panelboss +
    #      pj_<token>). Se mantiene forceload del chunk de la plataforma MIENTRAS
    #      el jefe viva, para que la deteccion sea fiable aunque no haya jugadores
    #      cerca (la entidad sigue tickeando y el chunk esta cargado).
    #   2) El panel escribe, por spawn, una funcion paneljefes:watch_<token> que:
    #        - si el jefe (tag pj_<token>) YA NO existe en toda la dimension ->
    #          considera que MURIO y ejecuta paneljefes:dead_<token>.
    #        - si sigue vivo, se RE-AGENDA a si misma (schedule ... 20t) para
    #          volver a comprobar dentro de 1 s.
    #   3) paneljefes:dead_<token> suelta el LOOT por (dimension x nivel) en la
    #      posicion de la plataforma, BORRA la plataforma (rellena de aire suelo
    #      + muro) si se construyo, quita el forceload y se detiene (no re-agenda).
    # La funcion vigilante se ARMA con un schedule inicial tras el summon (se da
    # un margen para que el jefe cargue). Los schedule se guardan en el mundo y
    # sobreviven al cierre del panel; solo se pierden con /reload, pero como el
    # ciclo de vida del jefe es corto y el panel re-arma al spawnear, es robusto.

    def _jefes_fn_dir(self):
        return DP_JEFES + '/data/' + NS_JEFES + '/functions'

    def _asegurar_base_jefes(self):
        """Crea el esqueleto del datapack PanelJefes si falta. Las funciones
        watch_/dead_ por spawn se escriben aparte en cada summon."""
        fn = self._jefes_fn_dir()
        self._crear_dirs(fn, DP_JEFES + '/data/minecraft/tags/functions')
        self.EXA.escribir_archivo(
            DP_JEFES + '/pack.mcmeta',
            _pack_mcmeta('Server Panas - Panel: deteccion de muerte de jefes, loot por dimension/nivel y borrado de plataforma.'))
        # tag load vacia (el pack es valido aunque no haya vigilantes activos).
        self.EXA.escribir_archivo(
            DP_JEFES + '/data/minecraft/tags/functions/load.json',
            _tag_funcion([]))

    @staticmethod
    def _cmds_borrar_plataforma(dim_id, cx, cy, cz, radio=ARENA_RADIO, alto=ARENA_MURO_ALTO):
        """Rellena de AIRE todo el volumen que ocupo la plataforma (suelo + muro
        + base de soporte) para no dejar basura. Usa un cubo que cubre con margen
        el disco de radio dado, desde una Y por debajo del suelo hasta la cima
        del muro. Se ejecuta en la dimension correcta."""
        r = int(radio) + 2
        pre = 'execute in %s positioned %d %d %d run ' % (dim_id, cx, cy, cz)
        # Suelo esta en ~-1..~0; el muro sube hasta ~alto. Limpiamos ~-2..~alto+1.
        return [pre + 'fill ~%d ~-2 ~%d ~%d ~%d ~%d air replace' % (-r, -r, r, int(alto) + 1, r)]

    def _escribir_vigilante(self, token, info, nivel):
        """Escribe las funciones watch_<token> y dead_<token> para un spawn
        concreto. info trae dim_id, x,y,z, arena (plataforma si/no), dim_key.
        Devuelve el comando que ARMA el vigilante (schedule inicial)."""
        fn = self._jefes_fn_dir()
        dim_id = info['dim_id']
        cx, cy, cz = int(info['x']), int(info['y']), int(info['z'])
        tag = 'pj_%s' % token
        plataforma = bool(info.get('arena'))

        # ---- dead_<token>: loot + borrado de plataforma + forceload remove ----
        lineas = ['# Jefe muerto: suelta loot (%s Nv %d) y limpia la plataforma.\n'
                  % (info.get('dim_key', 'overworld'), nivel)]
        # Loot por (dimension x nivel): se suelta en el centro de la plataforma.
        items = tabla_loot(info.get('dim_key', 'overworld'), nivel)
        for it, cant in items:
            # summon del item como entidad en la posicion exacta del centro.
            lineas.append(
                'execute in %s positioned %d %d %d run summon item ~ ~1 ~ {Item:{id:"%s",Count:%db}}\n'
                % (dim_id, cx, cy, cz, it, min(int(cant), 64)))
        # XP escalada.
        lineas.append('execute in %s positioned %d %d %d run summon experience_orb ~ ~1 ~ {Value:%d}\n'
                      % (dim_id, cx, cy, cz, xp_por_nivel(nivel)))
        # Borrar plataforma (solo si se construyo).
        if plataforma:
            for c in self._cmds_borrar_plataforma(dim_id, cx, cy, cz):
                lineas.append(c + '\n')
        # Quitar forceload del chunk de la plataforma (ya no hace falta vigilar).
        lineas.append('execute in %s run forceload remove %d %d\n' % (dim_id, cx, cz))
        # Limpiar cualquier marcador residual de este spawn.
        lineas.append('kill @e[tag=%s]\n' % tag)
        self.EXA.escribir_archivo(fn + '/dead_%s.mcfunction' % token, ''.join(lineas).encode('utf-8'))

        # ---- watch_<token>: comprueba si el jefe sigue vivo ----
        # Si NO existe ninguna entidad con el tag en la dimension -> murio.
        watch = (
            '# Vigilante del jefe pj_%s. Si ya no existe -> dead_%s. Si vive -> re-agenda.\n'
            'execute in %s unless entity @e[tag=%s] run function %s:dead_%s\n'
            'execute in %s if entity @e[tag=%s] run schedule function %s:watch_%s 20t replace\n'
        ) % (token, token, dim_id, tag, NS_JEFES, token, dim_id, tag, NS_JEFES, token)
        self.EXA.escribir_archivo(fn + '/watch_%s.mcfunction' % token, watch.encode('utf-8'))

        # Comando que arma el primer disparo (margen de 2 s para que el jefe
        # cargue antes de empezar a vigilar).
        return 'schedule function %s:watch_%s 40t replace' % (NS_JEFES, token)

    @staticmethod
    def _custom_name_json(texto):
        """CustomName de Minecraft: un JSON-texto, escapado para meterlo dentro
        del NBT del summon (que a su vez es un string JSON entre comillas)."""
        limpio = re.sub(r'[\\"\n\r]', ' ', texto or '').strip()
        # CustomName:'{"text":"<...>"}'  (comillas simples externas en el NBT)
        return "'{\"text\":\"%s\"}'" % limpio

    # -- Construccion de la PLATAFORMA CIRCULAR (arena) con MURO ------------
    @staticmethod
    def _cmds_plataforma(dim_id, cx, cy, cz, suelo, borde, radio=ARENA_RADIO,
                         muro_alto=ARENA_MURO_ALTO):
        """Genera los comandos (prefijados con 'execute in <dim> positioned ...')
        que fuerzan la carga del chunk, construyen una ARENA CIRCULAR de radio
        dado con SUELO solido + MURO de contencion para que el mob no pueda
        salir, y dejan el interior libre (aire) para la pelea.

        - SUELO: disco relleno (dx*dx+dz*dz <= r*r), dos capas (pisada + soporte).
        - INTERIOR: aire despejado hasta bastante alto (volumen de pelea).
        - MURO: anillo exterior (el perimetro del circulo) elevado 'muro_alto'
          bloques con el bloque 'borde', formando una pared que contiene al mob.

        Se devuelve una lista de comandos con el prefijo de dimension/posicion ya
        aplicado, de modo que las coords internas son RELATIVAS (~) al centro.
        NOTA: NO se quita el forceload aqui; se mantiene mientras el jefe viva
        (lo retira la funcion de muerte) para que la deteccion sea fiable."""
        pre = 'execute in %s positioned %d %d %d run ' % (dim_id, cx, cy, cz)
        r = int(radio)
        ah = int(muro_alto)
        alto = 40  # volumen libre encima (dragones voladores / pelea)
        cmds = []
        # 1) Forzar carga del chunk del centro (se mantiene mientras viva el jefe).
        cmds.append('execute in %s run forceload add %d %d' % (dim_id, cx, cz))
        # 2) Despejar aire en todo el volumen interior (desde ~1 hasta arriba).
        cmds.append(pre + 'fill ~%d ~1 ~%d ~%d ~%d ~%d air replace' % (-(r + 2), -(r + 2), (r + 2), alto, (r + 2)))
        # 3) Dibujar el disco de SUELO fila por fila (ancho del circulo por fila).
        for dz in range(-r, r + 1):
            dxmax = int((r * r - dz * dz) ** 0.5)
            cmds.append(pre + 'fill ~%d ~-1 ~%d ~%d ~0 ~%d %s replace' % (-dxmax, dz, dxmax, dz, suelo))
        # 4) MURO de contencion: perimetro del circulo elevado 'ah' bloques. Para
        #    cada fila dz, los bloques en el borde exterior del disco (donde la
        #    fila de arriba o abajo ya no alcanza ese dx) se levantan en columna.
        #    Asi queda una pared circular cerrada de altura ah.
        for dz in range(-r, r + 1):
            dxmax = int((r * r - dz * dz) ** 0.5)
            # dx de la fila vecina (dz+-1): si es menor, esos dx son perimetro.
            dz_vec = dz + 1 if dz >= 0 else dz - 1
            if abs(dz_vec) <= r:
                dxmax_vec = int((r * r - dz_vec * dz_vec) ** 0.5)
            else:
                dxmax_vec = -1
            # Columnas de muro en los extremos de la fila (siempre perimetro).
            if dxmax >= 0:
                cmds.append(pre + 'fill ~%d ~1 ~%d ~%d ~%d ~%d %s replace'
                            % (-dxmax, dz, -dxmax, ah, dz, borde))
                cmds.append(pre + 'fill ~%d ~1 ~%d ~%d ~%d ~%d %s replace'
                            % (dxmax, dz, dxmax, ah, dz, borde))
            # Rellenar el hueco del escalon hacia la fila vecina (de dxmax_vec+1
            # a dxmax) a ambos lados para que el muro no deje aberturas.
            if dxmax_vec < dxmax and dxmax >= 1:
                for dx in range(max(0, dxmax_vec + 1), dxmax):
                    cmds.append(pre + 'fill ~%d ~1 ~%d ~%d ~%d ~%d %s replace'
                                % (-dx, dz, -dx, ah, dz, borde))
                    cmds.append(pre + 'fill ~%d ~1 ~%d ~%d ~%d ~%d %s replace'
                                % (dx, dz, dx, ah, dz, borde))
        return cmds

    def _cmds_summon(self, eid, destino, nivel, nombre):
        """Construye la secuencia de comandos para spawnear al jefe en el nivel
        dado. El summon marca al jefe con un Tag unico + CustomName "<Nombre>
        [Nv N]". Para nivel >= 2 se anaden modificadores multiply_base de vida y
        dano (metodo general, no necesita la base del jefe) y se cura a tope.

        Soporta tres modos de destino:
          - 'arena'   (NUEVO, por defecto): plataforma circular en ubicacion
                       aleatoria de la dimension apropiada + anuncio global.
          - 'coords'  : coordenadas manuales (se conserva).
          - 'jugador' : en la posicion de un jugador conectado (se conserva).

        Devuelve (lista_de_comandos, info) donde info describe la ubicacion para
        el mensaje del panel."""
        destino = destino or {}
        tipo = destino.get('tipo') or 'arena'
        # Toggle de plataforma (Tarea 3): por defecto SI en modo arena. Si el
        # admin elige NO, en modo arena se spawnea en la ubicacion aleatoria sin
        # construir la plataforma ni el muro.
        quiere_plataforma = destino.get('plataforma', True)
        if isinstance(quiere_plataforma, str):
            quiere_plataforma = quiere_plataforma.strip().lower() not in ('no', 'false', '0', '')
        quiere_plataforma = bool(quiere_plataforma)
        nombre_vis = (nombre or eid.split(':')[-1]).strip() or eid.split(':')[-1]
        etiqueta = '%s [Nv %d]' % (nombre_vis, nivel)
        # Token UNICO de este spawn: engancha modificadores de nivel, el sistema
        # de deteccion de muerte (tag pj_<token>) y el marcado de jefe del panel.
        token = _slug('%s_%d_%d' % (eid.split(':')[-1], nivel, int(time.time() * 1000) % 100000000))
        tag = 'spawnnv_%s' % token          # tag de nivel (se quita tras aplicar)
        sys_tag = 'pj_%s' % token           # tag del SISTEMA (persistente, lo vigila PanelJefes)
        nbt = '{Tags:["%s","panelboss","%s"],CustomName:%s,CustomNameVisible:1b}' % (
            tag, sys_tag, self._custom_name_json(etiqueta))

        info = {'arena': False, 'nombre_jefe': nombre_vis, 'token': token,
                'plataforma': False, 'nivel': nivel}
        cmds = []

        if tipo == 'arena':
            dim_key = _dim_de_jefe(eid, destino.get('dimension', 'auto'))
            dcfg = DIMENSIONES.get(dim_key) or DIMENSIONES['overworld']
            x, y, z = _coords_aleatorias(dcfg)
            dim_id = dcfg['id']
            if quiere_plataforma:
                # Plataforma circular con MURO de contencion + forceload (se
                # mantiene mientras el jefe viva; lo quita la funcion de muerte).
                cmds.extend(self._cmds_plataforma(dim_id, x, y, z, dcfg['suelo'], dcfg['borde']))
                info['plataforma'] = True
            else:
                # Sin plataforma: igualmente forzamos la carga del chunk para que
                # la deteccion de muerte sea fiable (la quita la funcion de muerte).
                cmds.append('execute in %s run forceload add %d %d' % (dim_id, x, z))
            # Summon 1-2 bloques sobre el centro (y+2) en la dimension correcta.
            summon = 'execute in %s run summon %s %d %d %d %s' % (dim_id, eid, x, y + 2, z, nbt)
            info.update({'arena': True, 'dim_key': dim_key, 'dim_nombre': dcfg['nombre'],
                         'dim_id': dim_id, 'x': x, 'y': y, 'z': z})
        elif tipo == 'coords':
            try:
                x = float(destino.get('x'))
                y = float(destino.get('y'))
                z = float(destino.get('z'))
            except (TypeError, ValueError):
                raise ValueError('Coordenadas no validas.')
            summon = 'summon %s %s %s %s %s' % (eid, _fmt(x), _fmt(y), _fmt(z), nbt)
            info.update({'x': x, 'y': y, 'z': z})
        elif tipo == 'jugador':
            jug = destino.get('jugador', '')
            if not NOMBRE_OK.match(jug):
                raise ValueError('Nombre de jugador no valido.')
            summon = 'execute at %s run summon %s ~ ~ ~ %s' % (jug, eid, nbt)
            info.update({'jugador': jug})
        else:
            raise ValueError('Destino no valido (arena | coords | jugador).')

        cmds.append(summon)
        if nivel >= 2:
            kv = _fmt(nivel_vida_mult(nivel) - 1.0)  # multiply_base suma k*base
            kd = _fmt(nivel_dano_mult(nivel) - 1.0)
            uuid_vida = str(uuidlib.uuid4())
            uuid_dano = str(uuidlib.uuid4())
            sel = '@e[tag=%s,limit=1]' % tag
            cmds.append('attribute %s minecraft:generic.max_health modifier add %s nivel_vida %s multiply_base'
                        % (sel, uuid_vida, kv))
            cmds.append('attribute %s minecraft:generic.attack_damage modifier add %s nivel_dano %s multiply_base'
                        % (sel, uuid_dano, kd))
            # Curar a tope para que nazca con la vida maxima ya escalada.
            cmds.append('effect give %s minecraft:instant_health 1 255 true' % sel)
            # Quitar el tag para no confundir futuros spawns con este selector.
            cmds.append('tag %s remove %s' % (sel, tag))

        # ANUNCIO GLOBAL a todo el servidor. En modo arena incluye dimension y
        # coords; en los otros modos un aviso mas simple. En el caso PROGRAMADO,
        # este tellraw va DENTRO de la funcion de la tarea (se ejecuta junto al
        # summon), no al programar.
        cmds.append(self._cmd_anuncio(etiqueta, info))

        # DETECCION DE MUERTE + LOOT + BORRADO DE PLATAFORMA (Tareas 2 y 4):
        # solo en modo arena (donde controlamos dimension y posicion exacta). Se
        # escriben las funciones watch_/dead_ del spawn y se ARMA el vigilante.
        # IMPORTANTE: el forceload NO se quita aqui; lo retira dead_<token> al
        # morir el jefe, para que la deteccion y el borrado sean fiables.
        if info.get('arena'):
            self._asegurar_base_jefes()
            arma = self._escribir_vigilante(token, info, nivel)
            # El schedule del vigilante solo "prende" si la funcion ya esta
            # cargada en el server. Para el spawn INMEDIATO el panel hace /reload
            # antes de ejecutar (ver spawn_inmediato); para el PROGRAMADO el
            # arma-schedule va DENTRO de la tarea, que ya pasa por /reload.
            cmds.append(arma)

        return cmds, info

    @staticmethod
    def _cmd_anuncio(etiqueta, info):
        """tellraw @a con el aviso global coloreado: '[Server Panas] Ha aparecido
        <Nombre> [Nv N] en <Dimension> cerca de X:.. Z:.. Ve a por el!'."""
        def _txt(t, color=None, bold=False):
            o = {'text': t}
            if color:
                o['color'] = color
            if bold:
                o['bold'] = True
            return o

        partes = [_txt('[Server Panas] ', 'gold', True)]
        if info.get('arena'):
            partes += [
                _txt('Ha aparecido ', 'yellow'),
                _txt(etiqueta, 'red', True),
                _txt(' en ', 'yellow'),
                _txt(info['dim_nombre'], 'aqua', True),
                _txt(' cerca de X:%d Z:%d. ' % (info['x'], info['z']), 'white'),
                _txt('Ve a por el!', 'green', True),
            ]
        elif 'jugador' in info:
            partes += [
                _txt('Ha aparecido ', 'yellow'),
                _txt(etiqueta, 'red', True),
                _txt(' junto a ', 'yellow'),
                _txt(info['jugador'], 'aqua', True),
                _txt('. Suerte!', 'green', True),
            ]
        else:
            partes += [
                _txt('Ha aparecido ', 'yellow'),
                _txt(etiqueta, 'red', True),
                _txt(' cerca de X:%s Z:%s. ' % (_fmt(info.get('x', 0)), _fmt(info.get('z', 0))), 'white'),
                _txt('Ve a por el!', 'green', True),
            ]
        return 'tellraw @a %s' % json.dumps(partes, ensure_ascii=False)


def _fmt(n):
    """Numero sin decimales de mas (2.0 -> '2', 2.5 -> '2.5')."""
    f = float(n)
    if f == int(f):
        return str(int(f))
    return ('%.4f' % f).rstrip('0').rstrip('.')
