"""Combinaciones de 2 teclas: lo util con Alt izq / Bloq Mayus, lo raro con Alt der / Ctrl der.
Shift izq = correr + cargar (Carry On) + detalles (Jade). Ctrl izq = agacharse.
Barra de hechizos -> C (antes en Alt izq)."""
import sys, shutil, datetime, re, json
sys.stdout.reconfigure(encoding='utf-8')
I = r'C:/Users/David Meza/curseforge/minecraft/Instances/SERVER PANAS'
F = I + '/options.txt'
APLICAR = len(sys.argv) > 1 and sys.argv[1] == 'aplicar'
K = 'key.keyboard.'
ALT, CAPS, RALT, RCTRL, RSHIFT = K + 'left.alt', K + 'caps.lock', K + 'right.alt', K + 'right.control', K + 'right.shift'
BASES = ([K + 'keypad.%d' % i for i in (1, 2, 3, 4, 5, 6, 7, 8, 9, 0)] +
         [K + 'keypad.' + x for x in ('decimal', 'add', 'subtract', 'multiply', 'divide', 'enter')] +
         [K + x for x in ('f4', 'f6', 'f8', 'f9', 'f10', 'f12', 'insert', 'home', 'end', 'delete', 'page.up', 'page.down', 'up', 'num.lock')])
UTILES_HUECOS = [([c], b) for c in (ALT, CAPS) for b in BASES if not (c == ALT and b == K + 'f4')]  # Alt+F4 cierra el juego
RAROS_HUECOS = [([c], b) for c in (RALT, RCTRL, RSHIFT) for b in BASES]

lineas = open(F, encoding='utf-8').read().splitlines()
act = {}
for l in lineas:
    if l.startswith('key_'):
        n, _, v = l[4:].partition(':')
        p = v.split(':')
        act[n] = [p[0], [x for x in (p[2].split('+') if len(p) > 2 and p[2] else [])]]
ctx = lambda n: 'GUI' if n.startswith('key.jei.') or n == 'key.inventorysorter.sort' else 'JUEGO'

# Fijas (una tecla)
FIJAS = {'key.sprint': K + 'left.shift', 'key.carry.desc': K + 'left.shift', 'key.jade.show_details': K + 'left.shift',
         'key.sneak': K + 'left.control', 'key.irons_spellbooks.spell_bar_modifier': K + 'c'}
for n, t in FIJAS.items():
    act[n] = [t, []]
LIBRES = {ALT, CAPS, RALT, RCTRL, RSHIFT} | set(BASES)
mover = [n for n, (t, c) in act.items() if ctx(n) == 'JUEGO' and n not in FIJAS and (c or t in LIBRES)]
mover.append('key.saveToolbarActivator')   # la C pasa a la barra de hechizos
RAROS = ('quark.emote', 'quark.keybind.patreon', 'quark.keybind.narrator', 'gui.xaero', 'key.entityculling', 'key.modernfix',
         'tombstone.message.config', 'key.jade.config', 'key.jade.narrate', 'key.structure_gel', 'info.structure_gel',
         'key.apotheosis.world_tiers', 'key.spectatorOutlines', 'placebo', 'key.push_to_talk', 'quark.keybind.camera_mode')
ORDEN_ALT = ['key.irons_spellbooks.spell_quick', 'key.relics', 'key.ftbteams', 'key.voice_chat_settings', 'key.voice_chat_adjust',
             'key.jade.show_recipes', 'key.jade.show_uses', 'key.jade.show_overlay', 'tombstone.message.compendium',
             'tombstone.message.knowledge', 'key.travelersbackpack.sort']
ORDEN_CAPS = ['key.apotheosis.toggle_radial', 'quark.keybind.shift_lock', 'quark.keybind.autorun', 'key.whisper',
              'key.voice_chat_group', 'key.mute', 'key.voice_chat_toggle', 'key.travelersbackpack.toggle_upgrade',
              'key.toolbelt.cycle', 'quark.keybind.sort', 'quark.keybind.transfer', 'quark.keybind.change_hotbar', 'key.cataclysm',
              'keybind.reliquary', 'key.smoothCamera', 'key.saveToolbarActivator', 'key.jade.toggle_liquid']
def orden(n, lista):
    i = next((k for k, o in enumerate(lista) if n.startswith(o)), len(lista))
    m = re.search(r'_(\d+)$', n)
    return (i, int(m.group(1)) if m else 0, n)
raros = sorted([n for n in mover if n.startswith(RAROS)], key=lambda n: n)
utiles = [n for n in mover if n not in raros]
u_alt = sorted([n for n in utiles if any(n.startswith(o) for o in ORDEN_ALT)], key=lambda n: orden(n, ORDEN_ALT))
u_resto = sorted([n for n in utiles if n not in u_alt], key=lambda n: orden(n, ORDEN_CAPS))
utiles = u_alt + u_resto
sobrantes = utiles[len(UTILES_HUECOS):]       # si no caben a la izquierda, van con las raras
utiles = utiles[:len(UTILES_HUECOS)]
raros = sobrantes + raros
if len(raros) > len(RAROS_HUECOS):
    sys.exit('No caben: %d raras y %d huecos' % (len(raros), len(RAROS_HUECOS)))
asign = {}
for n, (capa, base) in list(zip(utiles, UTILES_HUECOS)) + list(zip(raros, RAROS_HUECOS)):
    act[n] = [base, capa]
    asign[n] = capa + [base]
usados = UTILES_HUECOS[:len(utiles)] + RAROS_HUECOS[:len(raros)]
# JEI (solo inventario): sus combinaciones de teclado reutilizan huecos sin chocar
jei = [n for n, (t, c) in act.items() if ctx(n) == 'GUI' and c and not t.startswith('key.mouse.')]
gui_plano = {t for n, (t, c) in act.items() if ctx(n) == 'GUI' and not c and n not in jei}
huecos_jei = [h for h in UTILES_HUECOS + RAROS_HUECOS if h[1] not in gui_plano]
for n, (capa, base) in zip(jei, huecos_jei):
    act[n] = [base, capa]
    asign[n] = capa + [base]

items = [(n, t, frozenset(c)) for n, (t, c) in act.items() if t != K + 'unknown']
PERMIT = {frozenset({'key.sprint', 'key.carry.desc'}), frozenset({'key.sprint', 'key.jade.show_details'}),
          frozenset({'key.carry.desc', 'key.jade.show_details'})}
rojo, juntas = [], []
for i, a in enumerate(items):
    for b in items[i + 1:]:
        if ctx(a[0]) != ctx(b[0]) or frozenset({a[0], b[0]}) in PERMIT:
            continue
        if b[1] in a[2] or a[1] in b[2] or (a[1] == b[1] and a[2] == b[2]):
            rojo.append((a[0], b[0]))
        elif a[1] == b[1] and (not a[2] or not b[2]) and ctx(a[0]) == 'JUEGO':
            juntas.append((a[0], b[0]))
print('utiles izquierda:', len(utiles), 'de', len(UTILES_HUECOS), '| raras derecha:', len(raros), 'de', len(RAROS_HUECOS), '| JEI:', len(jei))
print('sin tecla:', sum(1 for t, c in act.values() if t == K + 'unknown'), '| de 3 teclas:', sum(1 for t, c in act.values() if len(c) > 1))
print('en rojo:', rojo)
print('se activan juntas:', juntas)
print('a la derecha (utiles que no cupieron):', sobrantes)

def leer(t):
    r = {'caps.lock': 'BloqMayus', 'left.alt': 'Alt', 'right.alt': 'AltDer', 'right.control': 'CtrlDer', 'right.shift': 'ShiftDer',
         'page.up': 'RePag', 'page.down': 'AvPag', 'num.lock': 'BloqNum', 'up': 'Flecha arriba', 'insert': 'Insert', 'home': 'Inicio',
         'end': 'Fin', 'delete': 'Supr'}
    t = t.replace(K + 'keypad.', 'Num ').replace(K, '')
    return r.get(t, t.upper() if len(t) <= 3 else t)
if APLICAR and not rojo and not juntas:
    shutil.copy(F, F + '.antes-izquierda-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    out = []
    for l in lineas:
        if l.startswith('key_'):
            n = l[4:].partition(':')[0]
            t, c = act[n]
            l = 'key_%s:%s:NONE:%s' % (n, t, '+'.join(c))
        out.append(l)
    open(F, 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
    json.dump({n: ' + '.join(leer(x) for x in v) for n, v in asign.items()}, open(I + '/options.txt.asignacion.json', 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('APLICADO')
