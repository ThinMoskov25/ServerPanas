"""
Lector y escritor de NBT (formato de datos de Minecraft).

- load(bytes)  -> nodo (tipo, valor), descomprime gzip si hace falta.
- snbt(nodo)   -> texto SNBT exacto, apto para usar en /give o /data.

Cada nodo es una tupla (tipo, valor). Las listas guardan
(tipo_elemento, [nodos]) y los compuestos un dict {clave: nodo}.
Se conservan los tipos para poder reconstruir el item identico.
"""
import gzip
import math
import re
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class _Lector:
    def __init__(self, datos):
        self.b = datos
        self.p = 0

    def _toma(self, fmt):
        v = struct.unpack_from(fmt, self.b, self.p)
        self.p += struct.calcsize(fmt)
        return v

    def u8(self):
        return self._toma('>B')[0]

    def texto(self):
        n = self._toma('>H')[0]
        crudo = self.b[self.p:self.p + n]
        self.p += n
        return crudo.decode('utf-8', errors='replace')

    def valor(self, t):
        if t == BYTE:
            return self._toma('>b')[0]
        if t == SHORT:
            return self._toma('>h')[0]
        if t == INT:
            return self._toma('>i')[0]
        if t == LONG:
            return self._toma('>q')[0]
        if t == FLOAT:
            return self._toma('>f')[0]
        if t == DOUBLE:
            return self._toma('>d')[0]
        if t == BYTE_ARRAY:
            n = self._toma('>i')[0]
            v = list(struct.unpack_from('>%db' % n, self.b, self.p))
            self.p += n
            return v
        if t == STRING:
            return self.texto()
        if t == LIST:
            et = self.u8()
            n = self._toma('>i')[0]
            return (et, [(et, self.valor(et)) for _ in range(max(n, 0))])
        if t == COMPOUND:
            d = {}
            while True:
                tt = self.u8()
                if tt == END:
                    break
                k = self.texto()
                d[k] = (tt, self.valor(tt))
            return d
        if t == INT_ARRAY:
            n = self._toma('>i')[0]
            v = list(struct.unpack_from('>%di' % n, self.b, self.p))
            self.p += 4 * n
            return v
        if t == LONG_ARRAY:
            n = self._toma('>i')[0]
            v = list(struct.unpack_from('>%dq' % n, self.b, self.p))
            self.p += 8 * n
            return v
        raise ValueError('Tipo NBT desconocido: %s' % t)


def load(datos):
    if datos[:2] == b'\x1f\x8b':
        datos = gzip.decompress(datos)
    r = _Lector(datos)
    t = r.u8()
    r.texto()
    return (t, r.valor(t))


def get(nodo, ruta):
    """Navega claves dentro de compuestos. Devuelve None si no existe."""
    for k in ruta:
        if nodo is None or nodo[0] != COMPOUND:
            return None
        nodo = nodo[1].get(k)
    return nodo


# ---------------- SNBT ----------------
_CLAVE_LIBRE = re.compile(r'^[A-Za-z0-9._+\-]+$')


def _comillas(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def _num(x):
    if not math.isfinite(x):
        return '0'
    return repr(float(x))


def snbt(nodo):
    t, v = nodo
    if t == BYTE:
        return '%db' % v
    if t == SHORT:
        return '%ds' % v
    if t == INT:
        return '%d' % v
    if t == LONG:
        return '%dL' % v
    if t == FLOAT:
        return _num(v) + 'f'
    if t == DOUBLE:
        return _num(v) + 'd'
    if t == BYTE_ARRAY:
        return '[B;' + ','.join('%db' % x for x in v) + ']'
    if t == STRING:
        return _comillas(v)
    if t == LIST:
        return '[' + ','.join(snbt(e) for e in v[1]) + ']'
    if t == COMPOUND:
        partes = []
        for k, c in v.items():
            clave = k if _CLAVE_LIBRE.match(k) else _comillas(k)
            partes.append(clave + ':' + snbt(c))
        return '{' + ','.join(partes) + '}'
    if t == INT_ARRAY:
        return '[I;' + ','.join('%d' % x for x in v) + ']'
    if t == LONG_ARRAY:
        return '[L;' + ','.join('%dL' % x for x in v) + ']'
    raise ValueError('Tipo NBT desconocido: %s' % t)


# ---------------- Escritor NBT binario ----------------
# Escribe un nodo (tipo, valor) al formato binario de Minecraft, con gzip,
# igual al que genera NbtIo.writeCompressed del mod. Sirve para fabricar los
# archivos de respaldo que el mod InvBackup lee en world/inventory_backups/.

def _esc_valor(out, t, v):
    if t == BYTE:
        out.append(struct.pack('>b', int(v)))
    elif t == SHORT:
        out.append(struct.pack('>h', int(v)))
    elif t == INT:
        out.append(struct.pack('>i', int(v)))
    elif t == LONG:
        out.append(struct.pack('>q', int(v)))
    elif t == FLOAT:
        out.append(struct.pack('>f', float(v)))
    elif t == DOUBLE:
        out.append(struct.pack('>d', float(v)))
    elif t == BYTE_ARRAY:
        out.append(struct.pack('>i', len(v)))
        out.append(struct.pack('>%db' % len(v), *v) if v else b'')
    elif t == STRING:
        cod = v.encode('utf-8')
        out.append(struct.pack('>H', len(cod)))
        out.append(cod)
    elif t == LIST:
        et, elementos = v
        if not elementos:
            et = END
        out.append(struct.pack('>B', et))
        out.append(struct.pack('>i', len(elementos)))
        for e in elementos:
            _esc_valor(out, e[0], e[1])
    elif t == COMPOUND:
        for k, c in v.items():
            out.append(struct.pack('>B', c[0]))
            kc = k.encode('utf-8')
            out.append(struct.pack('>H', len(kc)))
            out.append(kc)
            _esc_valor(out, c[0], c[1])
        out.append(struct.pack('>B', END))
    elif t == INT_ARRAY:
        out.append(struct.pack('>i', len(v)))
        out.append(struct.pack('>%di' % len(v), *v) if v else b'')
    elif t == LONG_ARRAY:
        out.append(struct.pack('>i', len(v)))
        out.append(struct.pack('>%dq' % len(v), *v) if v else b'')
    else:
        raise ValueError('Tipo NBT no serializable: %s' % t)


def dump(nodo, nombre_raiz='', comprimir=True):
    """Serializa un nodo al formato binario NBT. Por defecto lo comprime con gzip."""
    out = []
    t, v = nodo
    out.append(struct.pack('>B', t))
    nc = nombre_raiz.encode('utf-8')
    out.append(struct.pack('>H', len(nc)))
    out.append(nc)
    _esc_valor(out, t, v)
    crudo = b''.join(out)
    return gzip.compress(crudo) if comprimir else crudo


# ---------------- Parser SNBT -> nodo ----------------
# Convierte el texto SNBT (el que guarda cada copia del datapack) de vuelta a
# un nodo (tipo, valor), para poder reempaquetarlo en binario para el mod.

class _ParserSnbt:
    def __init__(self, s):
        self.s = s
        self.i = 0
        self.n = len(s)

    def _salta(self):
        while self.i < self.n and self.s[self.i] in ' \t\r\n':
            self.i += 1

    def parse(self):
        self._salta()
        nodo = self._valor()
        return nodo

    def _valor(self):
        self._salta()
        c = self.s[self.i]
        if c == '{':
            return self._compound()
        if c == '[':
            return self._lista()
        if c == '"' or c == "'":
            return (STRING, self._cadena())
        return self._primitivo()

    def _cadena(self):
        q = self.s[self.i]
        self.i += 1
        buf = []
        while self.i < self.n:
            c = self.s[self.i]
            if c == '\\':
                self.i += 1
                buf.append(self.s[self.i])
            elif c == q:
                self.i += 1
                break
            else:
                buf.append(c)
            self.i += 1
        return ''.join(buf)

    def _clave(self):
        self._salta()
        if self.s[self.i] in '"\'':
            return self._cadena()
        j = self.i
        while self.i < self.n and self.s[self.i] not in ':{}[],':
            self.i += 1
        return self.s[j:self.i].strip()

    def _compound(self):
        self.i += 1  # {
        d = {}
        self._salta()
        if self.s[self.i] == '}':
            self.i += 1
            return (COMPOUND, d)
        while True:
            k = self._clave()
            self._salta()
            self.i += 1  # :
            d[k] = self._valor()
            self._salta()
            if self.s[self.i] == ',':
                self.i += 1
                continue
            if self.s[self.i] == '}':
                self.i += 1
                break
        return (COMPOUND, d)

    def _lista(self):
        # Arrays tipados: [B; ...] [I; ...] [L; ...]
        if self.s[self.i + 1:self.i + 3] in ('B;', 'I;', 'L;'):
            tipo = self.s[self.i + 1]
            self.i += 3
            vals = []
            self._salta()
            while self.s[self.i] != ']':
                j = self.i
                while self.s[self.i] not in ',]':
                    self.i += 1
                vals.append(int(re.sub(r'[bBsSlL]$', '', self.s[j:self.i].strip())))
                if self.s[self.i] == ',':
                    self.i += 1
                self._salta()
            self.i += 1  # ]
            return {'B': (BYTE_ARRAY, vals), 'I': (INT_ARRAY, vals), 'L': (LONG_ARRAY, vals)}[tipo]
        # Lista normal
        self.i += 1  # [
        elementos = []
        self._salta()
        if self.s[self.i] == ']':
            self.i += 1
            return (LIST, (END, []))
        while True:
            elementos.append(self._valor())
            self._salta()
            if self.s[self.i] == ',':
                self.i += 1
                self._salta()
                continue
            if self.s[self.i] == ']':
                self.i += 1
                break
        et = elementos[0][0] if elementos else END
        return (LIST, (et, elementos))

    def _primitivo(self):
        j = self.i
        while self.i < self.n and self.s[self.i] not in ',{}[]:':
            self.i += 1
        tok = self.s[j:self.i].strip()
        return _primitivo_desde_texto(tok)


def _primitivo_desde_texto(tok):
    if tok in ('true', 'false'):
        return (BYTE, 1 if tok == 'true' else 0)
    m = re.match(r'^(-?\d+)[bB]$', tok)
    if m:
        return (BYTE, int(m.group(1)))
    m = re.match(r'^(-?\d+)[sS]$', tok)
    if m:
        return (SHORT, int(m.group(1)))
    m = re.match(r'^(-?\d+)[lL]$', tok)
    if m:
        return (LONG, int(m.group(1)))
    m = re.match(r'^(-?\d+)$', tok)
    if m:
        return (INT, int(m.group(1)))
    m = re.match(r'^(-?\d*\.?\d+(?:[eE][-+]?\d+)?)[fF]$', tok)
    if m:
        return (FLOAT, float(m.group(1)))
    m = re.match(r'^(-?\d*\.?\d+(?:[eE][-+]?\d+)?)[dD]?$', tok)
    if m and ('.' in tok or 'e' in tok or 'E' in tok or tok.endswith(('d', 'D'))):
        return (DOUBLE, float(m.group(1)))
    return (STRING, tok)


def parse_snbt(texto):
    return _ParserSnbt(texto).parse()
