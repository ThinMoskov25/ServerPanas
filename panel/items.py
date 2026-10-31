"""
Extraccion de items de una copia de inventario y comparacion entre copias.

Cada item se aplana (incluye lo que hay dentro de mochilas y cajas) y se
identifica por una "firma": id + datos del item sin durabilidad y sin el
contenido interno. Asi un pico que se gasto sigue siendo el mismo pico, y
la mochila se compara por lo que lleva dentro, item por item.
"""
import json
from collections import Counter

import nbt

RANURAS = {100: 'Botas', 101: 'Grebas', 102: 'Pechera', 103: 'Casco', -106: 'Mano secundaria'}


def es_item(nodo):
    if nodo is None or nodo[0] != nbt.COMPOUND:
        return False
    v = nodo[1]
    return 'id' in v and v['id'][0] == nbt.STRING and 'Count' in v


def _es_lista_items(nodo):
    return (nodo[0] == nbt.LIST and nodo[1][0] == nbt.COMPOUND
            and any(es_item(e) for e in nodo[1][1]))


def _contiene_items(nodo):
    t, v = nodo
    if t == nbt.COMPOUND:
        return any(_es_lista_items(c) or _contiene_items(c) for c in v.values())
    if t == nbt.LIST:
        return any(_contiene_items(e) for e in v[1])
    return False


def _quitar_items(nodo):
    """Copia del nodo sin las listas de items internas (contenido de mochilas/cajas)."""
    t, v = nodo
    if t == nbt.COMPOUND:
        return (t, {k: _quitar_items(c) for k, c in v.items() if not _es_lista_items(c)})
    if t == nbt.LIST:
        return (t, (v[0], [_quitar_items(e) for e in v[1]]))
    return nodo


def _texto_json(crudo):
    try:
        o = json.loads(crudo)
    except Exception:
        return crudo

    def plano(x):
        if isinstance(x, str):
            return x
        if isinstance(x, list):
            return ''.join(plano(e) for e in x)
        if isinstance(x, dict):
            s = x.get('text', '') + ''.join(plano(e) for e in x.get('extra', []))
            if not s and 'translate' in x:
                s = x['translate']
            return s
        return ''
    return plano(o)


def _describir(item_nodo):
    v = item_nodo[1]
    tag = v.get('tag')
    nombre = ''
    encant = []
    if tag and tag[0] == nbt.COMPOUND:
        disp = nbt.get(tag, ['display', 'Name'])
        if disp and disp[0] == nbt.STRING:
            nombre = _texto_json(disp[1])
        for clave in ('Enchantments', 'StoredEnchantments'):
            lista = tag[1].get(clave)
            if lista and lista[0] == nbt.LIST:
                for e in lista[1][1]:
                    eid = nbt.get(e, ['id'])
                    lvl = nbt.get(e, ['lvl'])
                    if eid:
                        encant.append('%s %s' % (eid[1], lvl[1] if lvl else ''))
    return nombre, encant


def _registro(item_nodo, ubicacion, dentro_de):
    v = item_nodo[1]
    iid = v['id'][1]
    cantidad = int(v['Count'][1])
    tag = v.get('tag')
    contenedor = bool(tag) and _contiene_items(tag)
    if tag:
        tag_dar = _quitar_items(tag)
        canon = (tag_dar[0], {k: c for k, c in tag_dar[1].items() if k != 'Damage'})
        firma = iid + '|contenedor' if contenedor else iid + '|' + nbt.snbt(canon)
        tag_txt = nbt.snbt(tag_dar)
        if tag_txt == '{}':
            tag_txt = ''
    else:
        firma = iid + '|'
        tag_txt = ''
    nombre, encant = _describir(item_nodo)
    return {
        'id': iid,
        'cantidad': cantidad,
        'ubicacion': ubicacion,
        'dentro_de': dentro_de,
        'nombre': nombre,
        'encantamientos': encant,
        'contenedor': contenedor,
        'tag': tag_txt,
        'firma': firma,
    }


def _recorrer(nodo, ubicacion, dentro_de, salida):
    if nodo is None:
        return
    t, v = nodo
    if t == nbt.COMPOUND:
        if es_item(nodo):
            if v['id'][1] == 'minecraft:air' or int(v['Count'][1]) <= 0:
                return
            reg = _registro(nodo, ubicacion, dentro_de)
            salida.append(reg)
            tag = v.get('tag')
            if tag:
                _recorrer(tag, ubicacion, reg['nombre'] or reg['id'], salida)
            return
        for c in v.values():
            _recorrer(c, ubicacion, dentro_de, salida)
    elif t == nbt.LIST:
        for e in v[1]:
            _recorrer(e, ubicacion, dentro_de, salida)


def extraer(jugador_nodo):
    """jugador_nodo: compuesto con Inventory, EnderItems y Curios."""
    salida = []
    curios = nbt.get(jugador_nodo, ['Curios', 'Curios'])
    hay_curios = bool(curios and curios[0] == nbt.LIST)
    inv = nbt.get(jugador_nodo, ['Inventory'])
    if inv and inv[0] == nbt.LIST:
        for it in inv[1][1]:
            slot = nbt.get(it, ['Slot'])
            s = int(slot[1]) if slot else 0
            if s in RANURAS:
                ub = RANURAS[s]
            elif 0 <= s <= 8:
                ub = 'Barra rapida %d' % (s + 1)
            elif 9 <= s <= 35:
                ub = 'Inventario'
            elif hay_curios:
                # Ranuras ocultas (p. ej. 92, 94) que repiten lo equipado en Curios:
                # ya se cuentan en la seccion de amuletos, no se duplican.
                continue
            else:
                ub = 'Ranura extra %d' % s
            _recorrer(it, ub, None, salida)
    ender = nbt.get(jugador_nodo, ['EnderItems'])
    if ender:
        _recorrer(ender, 'Cofre de Ender', None, salida)
    if hay_curios:
        for entrada in curios[1][1]:
            ident = nbt.get(entrada, ['Identifier'])
            ub = 'Amuleto (%s)' % (ident[1] if ident else 'curios')
            _recorrer(entrada, ub, None, salida)
    return salida


def huella(items):
    """Resumen comparable de una copia (para saber si cambio)."""
    c = Counter()
    for it in items:
        c[it['firma']] += it['cantidad']
    return sorted(c.items())


def comparar(antes, despues):
    ca, cd = Counter(), Counter()
    info_a, info_d = {}, {}
    for it in antes:
        ca[it['firma']] += it['cantidad']
        info_a.setdefault(it['firma'], it)
    for it in despues:
        cd[it['firma']] += it['cantidad']
        info_d.setdefault(it['firma'], it)
    faltan = []
    for f, n in ca.items():
        if n > cd[f]:
            d = dict(info_a[f])
            d['faltan'] = n - cd[f]
            faltan.append(d)
    nuevos = []
    for f, n in cd.items():
        if n > ca[f]:
            d = dict(info_d[f])
            d['sobran'] = n - ca[f]
            nuevos.append(d)
    faltan.sort(key=lambda x: x['id'])
    nuevos.sort(key=lambda x: x['id'])
    return faltan, nuevos
