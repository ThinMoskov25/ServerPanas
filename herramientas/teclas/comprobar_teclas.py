"""Comprueba el options.txt del cliente (formato Key Binding Patch): acciones sin tecla,
combinaciones de mas de 2 teclas y conflictos (en rojo) segun las reglas del mod.
Uso: python comprobar_teclas.py "<ruta>/options.txt"  (no modifica nada)"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
L = open(sys.argv[1], encoding='utf-8').read().splitlines()
act = {}
for l in L:
    if l.startswith('key_'):
        n, _, v = l[4:].partition(':')
        p = v.split(':')
        act[n] = (p[0], frozenset(x for x in (p[2].split('+') if len(p) > 2 and p[2] else [])))
ctx = lambda n: 'GUI' if n.startswith('key.jei.') or n == 'key.inventorysorter.sort' else 'JUEGO'
SHIFT_OK = {'key.sprint', 'key.carry.desc', 'key.jade.show_details'}   # comparten Shift a proposito
it = [(n, t, c) for n, (t, c) in act.items() if t != 'key.keyboard.unknown']
rojo = []
for i, a in enumerate(it):
    for b in it[i + 1:]:
        if ctx(a[0]) != ctx(b[0]) or {a[0], b[0]} <= SHIFT_OK:
            continue
        if b[1] in a[2] or a[1] in b[2] or (a[1] == b[1] and a[2] == b[2]):
            rojo.append((a[0], b[0]))
print('acciones:', len(act), '| sin tecla:', sum(1 for t, c in act.values() if t == 'key.keyboard.unknown'),
      '| de 3+ teclas:', sum(1 for t, c in act.values() if len(c) > 1), '| en rojo:', len(rojo))
for r in rojo:
    print('  ', r)
