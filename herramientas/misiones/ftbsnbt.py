"""Lector/escritor del SNBT que usa FTB Quests (claves sin comillas, valores
separados por saltos de linea o comas, sufijos d/L/b/f en los numeros).
Los numeros se guardan como Num (texto original) para no alterar su tipo."""
import re


class Num(str):
    """Numero con su sufijo original (1.5d, 8L, 0b...)."""


_TOKEN = re.compile(r'\s*(?:(?P<p>[{}\[\]:,])|(?P<s>"(?:[^"\\]|\\.)*")|(?P<w>[^\s{}\[\]:,"]+))', re.S)


def _tokens(texto):
    pos = 0
    while True:
        m = _TOKEN.match(texto, pos)
        if not m or m.end() == pos:
            if texto[pos:].strip():
                raise ValueError('SNBT invalido cerca de: %r' % texto[pos:pos + 40])
            return
        pos = m.end()
        if m.group('p'):
            yield ('p', m.group('p'))
        elif m.group('s') is not None:
            yield ('s', m.group('s')[1:-1].replace('\\"', '"').replace('\\\\', '\\'))
        else:
            yield ('w', m.group('w'))


def loads(texto):
    toks = list(_tokens(texto))
    i = 0

    def valor():
        nonlocal i
        t, v = toks[i]
        i += 1
        if t == 'p' and v == '{':
            d = {}
            while True:
                while toks[i] == ('p', ','):
                    i += 1
                if toks[i] == ('p', '}'):
                    i += 1
                    return d
                clave = toks[i][1]
                i += 1
                assert toks[i] == ('p', ':'), 'falta ":" tras %s' % clave
                i += 1
                d[clave] = valor()
        if t == 'p' and v == '[':
            lst = []
            while True:
                while toks[i] == ('p', ','):
                    i += 1
                if toks[i] == ('p', ']'):
                    i += 1
                    return lst
                lst.append(valor())
        if t == 's':
            return v
        if v in ('true', 'false'):
            return v == 'true'
        return Num(v)

    r = valor()
    return r


def _str(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def dumps(obj, nivel=0):
    tab = '\t'
    if isinstance(obj, dict):
        if not obj:
            return '{ }'
        lineas = ['{']
        for k in sorted(obj):
            lineas.append(tab * (nivel + 1) + '%s: %s' % (k, dumps(obj[k], nivel + 1)))
        lineas.append(tab * nivel + '}')
        return '\n'.join(lineas)
    if isinstance(obj, list):
        if not obj:
            return '[ ]'
        if all(isinstance(x, (str, Num, bool)) and not isinstance(x, dict) for x in obj) and all(not isinstance(x, (dict, list)) for x in obj):
            if all(isinstance(x, Num) for x in obj) or (len(obj) == 1 and not isinstance(obj[0], Num)):
                return '[' + ', '.join(dumps(x) for x in obj) + ']'
        lineas = ['[']
        for x in obj:
            lineas.append(tab * (nivel + 1) + dumps(x, nivel + 1))
        lineas.append(tab * nivel + ']')
        return '\n'.join(lineas)
    if isinstance(obj, bool):
        return 'true' if obj else 'false'
    if isinstance(obj, Num):
        return str(obj)
    if isinstance(obj, str):
        return _str(obj)
    raise TypeError(type(obj))


def num(x):
    return float(str(x).rstrip('dDfFlLbBsS'))
