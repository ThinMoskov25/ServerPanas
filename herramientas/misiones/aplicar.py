"""Aplica los textos nuevos, el estilo por rol y una disposicion ordenada a los
capitulos de FTB Quests. No cambia IDs, dependencias, items ni recompensas
(salvo las tareas 'matar' indicadas explicitamente en los textos).
Uso: python aplicar.py <chapters_origen> <textos_nuevos> <chapters_destino>"""
import copy
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ftbsnbt import Num, dumps, loads

src, txt_dir, dst = sys.argv[1:4]
os.makedirs(dst, exist_ok=True)

# Estilo por rol: (prefijo de color del titulo, forma, tamano)
ROLES = {
    'inicio': ('&6', 'circle', 1.5),
    'normal': ('', 'rsquare', 1.0),
    'hito': ('&e', 'octagon', 1.25),
    'jefe': ('&c', 'hexagon', 1.25),
    'final': ('&6&l', 'diamond', 1.75),
}


def leer_textos(ruta):
    cap = {'quests': {}}
    actual = None
    en_desc = False
    for linea in open(ruta, encoding='utf-8').read().split('\n'):
        if linea.startswith('# CAPITULO '):
            cap['id'] = linea.split()[-1]
            continue
        if linea.startswith('## '):
            actual = {'desc': [], 'tareas': {}, 'matar': []}
            cap['quests'][linea[3:].strip()] = actual
            en_desc = False
            continue
        if en_desc:
            actual['desc'].append(linea)
            continue
        if actual is None:
            if linea.startswith('titulo: '):
                cap['titulo'] = linea[8:]
            elif linea.startswith('subtitulo: '):
                cap['subtitulo'] = linea[11:]
            continue
        if linea == 'desc:':
            en_desc = True
        elif linea.startswith('rol: '):
            actual['rol'] = linea[5:].strip()
        elif linea.startswith('titulo: '):
            actual['titulo'] = linea[8:]
        elif linea.startswith('subtitulo: '):
            actual['subtitulo'] = linea[11:]
        elif linea.startswith('icono: '):
            actual['icono'] = linea[7:].strip()
        elif linea.startswith('tarea '):
            tid, texto = linea[6:].split(': ', 1)
            actual['tareas'][tid] = texto
        elif linea.startswith('matar '):
            cabeza, titulo = linea[6:].split(' | ', 1)
            viejo, nuevo, entidad = cabeza.split()
            actual['matar'].append((viejo, nuevo, entidad, titulo))
        elif linea.strip():
            raise ValueError('Linea no reconocida en %s: %r' % (ruta, linea))
    for q in cap['quests'].values():
        while q['desc'] and not q['desc'][-1].strip():
            q['desc'].pop()
        assert q.get('rol') in ROLES, 'rol invalido en %s' % ruta
    return cap


def f(x):
    return Num(('%.2f' % x).rstrip('0').rstrip('.') + ('.0' if float(x) == int(x) else '') + 'd')


def disponer(quests):
    """Capas por camino mas largo (x) y baricentro de los padres (y)."""
    ids = {q['id'] for q in quests}
    por_id = {q['id']: q for q in quests}
    padres = {q['id']: [d for d in q.get('dependencies', []) if d in ids] for q in quests}
    capa = {}

    def prof(i):
        if i not in capa:
            capa[i] = 0 if not padres[i] else 1 + max(prof(p) for p in padres[i])
        return capa[i]

    for q in quests:
        prof(q['id'])
    tam = {q['id']: ROLES[q['_rol']][2] for q in quests}
    capas = {}
    for q in quests:  # mantiene el orden original como desempate
        capas.setdefault(capa[q['id']], []).append(q['id'])
    pos = {}
    x = 0.0
    tam_prev = None
    for n in sorted(capas):
        miembros = capas[n]
        t_max = max(tam[i] for i in miembros)
        if tam_prev is not None:
            x += 1.0 + (tam_prev + t_max) / 2
        tam_prev = t_max
        pref = {}
        for i in miembros:
            ps = [pos[p][1] for p in padres[i]]
            pref[i] = sum(ps) / len(ps) if ps else 0.0
        orden = sorted(miembros, key=lambda i: (pref[i], float(str(por_id[i].get('y', '0')).rstrip('d'))))
        ys = []
        for k, i in enumerate(orden):
            y = pref[i]
            if k:
                hueco = 0.75 + (tam[orden[k - 1]] + tam[i]) / 2
                y = max(y, ys[-1] + hueco)
            ys.append(y)
        desplazar = sum(pref[i] for i in orden) / len(orden) - sum(ys) / len(ys)
        for i, y in zip(orden, ys):
            pos[i] = (x, round((y + desplazar) * 4) / 4)
    return pos


for ruta in sorted(glob.glob(os.path.join(src, '[01]*.snbt'))):
    nombre = os.path.basename(ruta)
    cap = loads(open(ruta, encoding='utf-8').read())
    t = leer_textos(os.path.join(txt_dir, nombre.replace('.snbt', '.txt')))
    assert t['id'] == cap['id'], nombre
    ids_q = [q['id'] for q in cap['quests']]
    assert sorted(ids_q) == sorted(t['quests']), 'misiones distintas en %s: %s' % (nombre, set(ids_q) ^ set(t['quests']))

    cap['title'] = t['titulo']
    cap['subtitle'] = [t['subtitulo']]
    cap['default_hide_dependency_lines'] = False
    for q in cap['quests']:
        tq = t['quests'][q['id']]
        color, forma, tam = ROLES[tq['rol']]
        q['_rol'] = tq['rol']
        q['title'] = color + tq['titulo']
        q['subtitle'] = tq['subtitulo']
        q['description'] = tq['desc']
        q['shape'] = forma
        if tam == 1.0:
            q.pop('size', None)
        else:
            q['size'] = f(tam)
        if 'icono' in tq:
            q['icon'] = tq['icono']
        tareas = {x['id']: x for x in q['tasks']}
        for tid, texto in tq['tareas'].items():
            assert tid in tareas, 'tarea %s no existe en %s' % (tid, q['id'])
            tareas[tid]['title'] = texto
        for viejo, nuevo, entidad, titulo in tq['matar']:
            if nuevo in tareas:  # ya convertida en una pasada anterior
                tareas[nuevo]['title'] = titulo
                continue
            assert viejo in tareas and tareas[viejo]['type'] == 'checkmark', 'solo se cambian checkmarks: %s' % viejo
            k = [x['id'] for x in q['tasks']].index(viejo)
            q['tasks'][k] = {'id': nuevo, 'type': 'kill', 'entity': entidad, 'value': Num('1L'), 'title': titulo}

    pos = disponer(cap['quests'])
    for q in cap['quests']:
        q['x'], q['y'] = f(pos[q['id']][0]), f(pos[q['id']][1])
        del q['_rol']

    with open(os.path.join(dst, nombre), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(dumps(cap) + '\n')
    print('%-24s %d misiones' % (nombre, len(cap['quests'])))
