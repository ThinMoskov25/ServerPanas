"""Verifica los capitulos de FTB Quests.
Uso: python verificar.py <carpeta_chapters> <items.json> [carpetas_extra...] [--detalle]
Sale con codigo 1 si hay errores."""
import glob
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ftbsnbt import loads, num

args = [a for a in sys.argv[1:] if not a.startswith('--')]
carpeta, ruta_idx, extras = args[0], args[1], args[2:]
DETALLE = '--detalle' in sys.argv
idx = json.load(open(ruta_idx, encoding='utf-8'))
ITEMS = {i['id'] for i in idx['items']}
MODS_CON_ITEMS = {i.split(':')[0] for i in ITEMS}

caps = {}
for ruta in sorted(glob.glob(os.path.join(carpeta, '*.snbt'))) + sorted(r for e in extras for r in glob.glob(os.path.join(e, '*.snbt'))):
    caps[os.path.basename(ruta)] = loads(open(ruta, encoding='utf-8').read())

errores, avisos = [], []
ids = {}
quests = {}


def reg_id(i, donde):
    if i in ids:
        errores.append('ID duplicado %s en %s y %s' % (i, ids[i], donde))
    ids[i] = donde


def item_id(it):
    if isinstance(it, dict):
        return it.get('id')
    return it


def check_item(it, donde):
    iid = item_id(it)
    if not iid:
        return
    mod = iid.split(':')[0]
    if iid not in ITEMS:
        if mod in MODS_CON_ITEMS or mod == 'minecraft':
            errores.append('Item inexistente %s (%s)' % (iid, donde))
        else:
            avisos.append('Item de mod sin indice, no comprobable: %s (%s)' % (iid, donde))


for nombre, c in caps.items():
    reg_id(c['id'], nombre)
    for q in c.get('quests', []):
        reg_id(q['id'], nombre + ' quest')
        quests[q['id']] = (nombre, q)
        for t in q.get('tasks', []):
            reg_id(t['id'], nombre + ' task')
        for r in q.get('rewards', []):
            reg_id(r['id'], nombre + ' reward')

SIN_TILDE = re.compile(r'\b(mas|esta|aqui|tambien|despues|asi|dragon|mision|cancion|camino?s?|raton|cinturon|cual|donde|como|que|facil|dificil|ultimo|unica?|tecnica|magia|arbol|corazon|jefe|hechizo|sonar|ano|nino|montana|senor|companero|espanol|dano|pequeno|dueno|sueno|ensena|diseno|tamano|extrano|manana|otono|cabana)\b', re.I)
REVISAR = {'mas', 'aqui', 'tambien', 'despues', 'asi', 'dragon', 'mision', 'raton', 'cinturon', 'facil', 'dificil', 'ultimo', 'unico', 'unica', 'arbol', 'corazon', 'sonar', 'ano', 'montana', 'senor', 'companero', 'dano', 'pequeno', 'dueno', 'sueno', 'ensena', 'manana', 'cabana', 'tecnica', 'extrano', 'tamano'}

for nombre, c in caps.items():
    if not re.match(r'\d\d_', nombre):
        continue
    qs = c.get('quests', [])
    pos = []
    sin_acento = set()
    for q in qs:
        qn = '%s "%s"' % (nombre, re.sub('&.', '', q.get('title', q['id'])))
        for d in q.get('dependencies', []):
            if d not in quests:
                errores.append('Dependencia rota %s -> %s' % (qn, d))
        if not q.get('tasks'):
            errores.append('Mision sin tareas: %s' % qn)
        if not q.get('title'):
            avisos.append('Mision sin titulo: %s' % qn)
        if not q.get('description'):
            avisos.append('Mision sin descripcion: %s' % qn)
        if not q.get('subtitle'):
            avisos.append('Mision sin subtitulo: %s' % qn)
        if not q.get('rewards'):
            avisos.append('Mision sin recompensa: %s' % qn)
        for t in q.get('tasks', []):
            if t.get('type') == 'item':
                check_item(t.get('item'), qn)
            if t.get('icon'):
                check_item(t['icon'], qn + ' icono tarea')
        for r in q.get('rewards', []):
            if r.get('type') == 'item':
                check_item(r.get('item'), qn + ' recompensa')
        if q.get('icon'):
            check_item(q['icon'], qn + ' icono')
        texto = ' '.join([q.get('title', ''), q.get('subtitle', '')] + q.get('description', []))
        for w in SIN_TILDE.findall(texto):
            if w.lower() in REVISAR:
                sin_acento.add(w.lower())
        x, y, s = num(q.get('x', 0)), num(q.get('y', 0)), num(q.get('size', 1))
        for (x2, y2, s2, n2) in pos:
            if math.hypot(x - x2, y - y2) < (s + s2) / 2 * 0.9:
                errores.append('Misiones superpuestas en %s: "%s" y "%s"' % (nombre, re.sub('&.', '', q.get('title', '')), n2))
        pos.append((x, y, s, re.sub('&.', '', q.get('title', ''))))
    if sin_acento:
        avisos.append('Palabras sin tilde/ene en %s: %s' % (nombre, ', '.join(sorted(sin_acento))))

# Ciclos de dependencias
def ciclo(qid, pila, vistos):
    if qid in pila:
        return pila[pila.index(qid):]
    if qid in vistos:
        return None
    vistos.add(qid)
    for d in quests.get(qid, (None, {}))[1].get('dependencies', []):
        r = ciclo(d, pila + [qid], vistos)
        if r:
            return r
    return None

vistos = set()
for qid in quests:
    r = ciclo(qid, [], vistos)
    if r:
        errores.append('Ciclo de dependencias: %s' % ' -> '.join(r))
        break

# Resumen por capitulo
for nombre, c in caps.items():
    if not re.match(r'\d\d_', nombre):
        continue
    qs = c.get('quests', [])
    raices = [q for q in qs if not [d for d in q.get('dependencies', []) if quests.get(d, ('',))[0] == nombre]]
    print('%-26s %-34s %2d misiones, %d de inicio, icono %s' % (
        nombre, re.sub('&.', '', c.get('title', '')), len(qs), len(raices), c.get('icon')))
    if DETALLE:
        for q in qs:
            print('    (%5s,%5s) s=%-4s %-8s %-38s deps=%s' % (q.get('x'), q.get('y'), q.get('size', '1'), q.get('shape', ''),
                  re.sub('&.', '', q.get('title', '')), [re.sub('&.', '', quests[d][1].get('title', d)) if d in quests else d for d in q.get('dependencies', [])]))

print('\nERRORES (%d):' % len(errores))
for e in errores:
    print('  - ' + e)
print('AVISOS (%d):' % len(avisos))
for a in avisos:
    print('  - ' + a)
sys.exit(1 if errores else 0)
