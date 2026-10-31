"""Genera un HTML con el mapa de cada capitulo (formas, colores, lineas de
dependencia y textos con los codigos de color de Minecraft)."""
import glob
import html
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ftbsnbt import loads, num

src, salida = sys.argv[1], sys.argv[2]
COLORES = {'0': '#000', '1': '#00a', '2': '#0a0', '3': '#0aa', '4': '#a00', '5': '#a0a', '6': '#fa0', '7': '#aaa',
           '8': '#555', '9': '#55f', 'a': '#5f5', 'b': '#5ff', 'c': '#f55', 'd': '#f5f', 'e': '#ff5', 'f': '#fff'}
FORMAS = {'circle': 'border-radius:50%', 'rsquare': 'border-radius:22%', 'octagon': 'clip-path:polygon(30% 0,70% 0,100% 30%,100% 70%,70% 100%,30% 100%,0 70%,0 30%)',
          'hexagon': 'clip-path:polygon(25% 0,75% 0,100% 50%,75% 100%,25% 100%,0 50%)', 'diamond': 'clip-path:polygon(50% 0,100% 50%,50% 100%,0 50%)'}
UNIDAD = 46


def mc(texto):
    """Convierte codigos &x de Minecraft a HTML."""
    out, color, neg, cur = [], None, False, False
    for parte in re.split(r'(&[0-9a-fk-or])', texto):
        if re.fullmatch(r'&[0-9a-fk-or]', parte):
            c = parte[1]
            if c in COLORES:
                color, neg, cur = COLORES[c], False, False
            elif c == 'l':
                neg = True
            elif c == 'o':
                cur = True
            elif c == 'r':
                color, neg, cur = None, False, False
            continue
        if parte:
            est = ''.join([('color:%s;' % color) if color else '', 'font-weight:700;' if neg else '', 'font-style:italic;' if cur else ''])
            out.append('<span style="%s">%s</span>' % (est, html.escape(parte)))
    return ''.join(out)


bloques = []
for ruta in sorted(glob.glob(os.path.join(src, '[01]*.snbt'))):
    c = loads(open(ruta, encoding='utf-8').read())
    qs = c['quests']
    xs = [num(q['x']) for q in qs]
    ys = [num(q['y']) for q in qs]
    minx, miny = min(xs) - 1.5, min(ys) - 1.5
    ancho, alto = (max(xs) - minx + 1.5) * UNIDAD, (max(ys) - miny + 1.5) * UNIDAD
    pos = {q['id']: ((num(q['x']) - minx) * UNIDAD, (num(q['y']) - miny) * UNIDAD) for q in qs}
    lineas = []
    for q in qs:
        for d in q.get('dependencies', []):
            if d in pos:
                (x1, y1), (x2, y2) = pos[d], pos[q['id']]
                lineas.append('<line x1="%.0f" y1="%.0f" x2="%.0f" y2="%.0f"/>' % (x1, y1, x2, y2))
    nodos, fichas = [], []
    for k, q in enumerate(qs):
        x, y = pos[q['id']]
        s = num(q.get('size', 1)) * UNIDAD * 0.8
        color = '#f5b942' if q['title'].startswith('&6') else '#ffe066' if q['title'].startswith('&e') else '#ff6b6b' if q['title'].startswith('&c') else '#9fb4c7'
        nodos.append('<div class="nodo" title="%s" style="left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;background:%s;%s"><b>%d</b></div>'
                     % (html.escape(re.sub('&.', '', q['title'])), x - s / 2, y - s / 2, s, s, color, FORMAS.get(q.get('shape'), ''), k + 1))
        nodos.append('<div class="etq" style="left:%.0fpx;top:%.0fpx">%s</div>' % (x, y + s / 2 + 2, mc(q['title'])))
        tareas = ''.join('<li>%s</li>' % html.escape(t.get('title') or (t.get('item', {}).get('id') if isinstance(t.get('item'), dict) else t.get('item')) or t.get('entity') or t.get('dimension') or t['type']) for t in q['tasks'])
        fichas.append('<details><summary><b>%d.</b> %s <i>%s</i></summary><div class="desc">%s</div><ul>%s</ul></details>' % (
            k + 1, mc(q['title']), html.escape(q.get('subtitle', '')),
            '<br>'.join(mc(l) if l else '&nbsp;' for l in q.get('description', [])), tareas))
    bloques.append('<section><h2>%s</h2><p class="sub">%s</p><div class="mapa" style="width:%.0fpx;height:%.0fpx"><svg width="%.0f" height="%.0f">%s</svg>%s</div>%s</section>' % (
        mc(c['title']), html.escape(' '.join(c.get('subtitle', []))), ancho, alto, ancho, alto, ''.join(lineas), ''.join(nodos), ''.join(fichas)))

pagina = '''<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Misiones Server Panas</title>
<style>
body{margin:0;padding:24px;background:#1d1d24;color:#ddd;font-family:'Segoe UI',system-ui,sans-serif}
h1{margin:0 0 4px}section{background:#26262f;border:1px solid #3a3a46;border-radius:12px;padding:16px 18px;margin:18px 0}
h2{margin:0}.sub{color:#999;margin:4px 0 10px}
.mapa{position:relative;background:#16161c;border-radius:8px;overflow:visible;margin-bottom:12px;max-width:100%}
svg{position:absolute;inset:0}line{stroke:#5b6b7d;stroke-width:2}
.nodo{position:absolute;display:grid;place-items:center;color:#111;font-size:12px;box-shadow:0 0 0 2px #0006}
.etq{position:absolute;transform:translateX(-50%);font-size:11px;white-space:nowrap;text-align:center}
details{border-top:1px solid #33333d;padding:6px 0}summary{cursor:pointer}summary i{color:#999;font-size:13px;margin-left:6px}
.desc{background:#0d0d11;color:#fff;padding:10px 12px;border-radius:4px;margin:8px 0;font-size:13px;line-height:1.5;max-width:560px}
ul{margin:4px 0 6px;color:#aaa;font-size:13px}
</style></head><body><h1>Crónicas de Server Panas</h1><p class="sub">Vista previa de los 11 capítulos. Pulsa una misión para ver su texto.</p>@@BLOQUES@@</body></html>'''.replace('@@BLOQUES@@', ''.join(bloques))
open(salida, 'w', encoding='utf-8').write(pagina)
print('ok', salida)
