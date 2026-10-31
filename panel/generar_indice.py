"""
Generador del CATALOGO de items (id + nombre legible) para el buscador de la
Tienda del panel.

Que produce
-----------
Escribe  panel/datos/items_index.json  con una lista de objetos:
    [{"id": "minecraft:diamond", "nombre": "Diamante"}, ...]
para VANILLA + todos los mods de los que encontremos un archivo de idioma.

De donde saca los nombres (todo LOCAL, sin red)
-----------------------------------------------
1) VANILLA: del cliente de Minecraft 1.20.1 ya instalado.
   - El idioma espanol (es_es) de vanilla NO viene dentro del .jar: Mojang lo
     descarga como "asset" con nombre de hash. Lo localizamos leyendo el indice
     de assets (%APPDATA%/.minecraft/assets/indexes/5.json, que es el de 1.20.1)
     y de ahi el objeto minecraft/lang/es_es.json en assets/objects/<ab>/<hash>.
   - Si no hay es_es, caemos al en_us que SI viene dentro de versions/1.20.1/1.20.1.jar.
   Tomamos las claves item.minecraft.* y block.minecraft.* -> id minecraft:<x>.

2) MODS: de los .jar que haya en  ServerPanas/cliente/mods/  (son los mismos
   mods que corren en el servidor). De cada .jar leemos
   assets/<modid>/lang/es_es.json (y si no, en_us.json) y tomamos las claves
   item.<modid>.* y block.<modid>.* -> id <modid>:<x>.
   Ademas, si existe el resourcepack de traduccion
   ServerPanas/cliente/resourcepacks/Espanol-ServerPanas/assets/<modid>/lang/es_es.json
   lo usamos con PRIORIDAD (es la traduccion al espanol curada del server).

Como regenerarlo
----------------
    cd ServerPanas/panel
    python generar_indice.py
Opcional:
    python generar_indice.py --mods "ruta/a/mods" --salida datos/items_index.json

El panel carga el JSON ya generado en cada arranque; si el archivo no existe,
el buscador avisa que hay que regenerarlo. Volver a ejecutar este script
sobreescribe el indice (operacion barata, ~1-2 s).
"""
import argparse
import json
import os
import sys
import zipfile

BASE = os.path.dirname(os.path.abspath(__file__))
RAIZ_SP = os.path.normpath(os.path.join(BASE, '..'))
APPDATA = os.environ.get('APPDATA', '')
MC = os.path.join(APPDATA, '.minecraft')

# El indice de assets de 1.20.1 es el "5". Dejamos candidatos por si cambia.
INDICES_VANILLA = ['5', '8', '12', '17', '18']
JAR_VANILLA = os.path.join(MC, 'versions', '1.20.1', '1.20.1.jar')
MODS_DEFECTO = os.path.join(RAIZ_SP, 'cliente', 'mods')
RESOURCEPACK_ES = os.path.join(RAIZ_SP, 'cliente', 'resourcepacks',
                               'Espanol-ServerPanas', 'assets')
SALIDA_DEFECTO = os.path.join(BASE, 'datos', 'items_index.json')


def _nombres_desde_lang(texto_json, modid):
    """Devuelve {id -> nombre} para las claves item.<modid>.* / block.<modid>.*
    de un archivo de idioma ya leido como texto JSON. El id resultante es
    <modid>:<resto_con_puntos_a_barras>. Las claves de item tienen prioridad
    sobre las de block (muchos bloques tienen un item con el mismo nombre)."""
    try:
        datos = json.loads(texto_json)
    except Exception:
        return {}
    items, bloques = {}, {}
    pref_item = 'item.' + modid + '.'
    pref_block = 'block.' + modid + '.'
    for clave, valor in datos.items():
        if not isinstance(valor, str) or not valor.strip():
            continue
        if clave.startswith(pref_item):
            resto = clave[len(pref_item):]
            items[modid + ':' + resto.replace('.', '/')] = valor.strip()
        elif clave.startswith(pref_block):
            resto = clave[len(pref_block):]
            bloques[modid + ':' + resto.replace('.', '/')] = valor.strip()
    # item.* manda sobre block.* para el mismo id.
    combinado = dict(bloques)
    combinado.update(items)
    return combinado


def _leer_zip_texto(zf, nombre):
    try:
        with zf.open(nombre) as f:
            return f.read().decode('utf-8-sig', errors='replace')
    except KeyError:
        return None
    except Exception:
        return None


# ----------------------------- VANILLA -----------------------------
def _ruta_vanilla_es_es():
    """Localiza el objeto minecraft/lang/es_es.json via el indice de assets."""
    dir_idx = os.path.join(MC, 'assets', 'indexes')
    if not os.path.isdir(dir_idx):
        return None
    clave = 'minecraft/lang/es_es.json'
    for nombre in INDICES_VANILLA:
        ruta = os.path.join(dir_idx, nombre + '.json')
        if not os.path.exists(ruta):
            continue
        try:
            with open(ruta, 'r', encoding='utf-8') as f:
                idx = json.load(f)
        except Exception:
            continue
        obj = (idx.get('objects') or {}).get(clave)
        if obj and obj.get('hash'):
            h = obj['hash']
            p = os.path.join(MC, 'assets', 'objects', h[:2], h)
            if os.path.exists(p):
                return p
    return None


def nombres_vanilla():
    """Mapa id->nombre de items/bloques vanilla, en espanol si es posible."""
    # 1) Espanol real de Mojang (asset hash).
    p_es = _ruta_vanilla_es_es()
    if p_es:
        with open(p_es, 'r', encoding='utf-8') as f:
            return _nombres_desde_lang(f.read(), 'minecraft')
    # 2) Respaldo: en_us de dentro del .jar del cliente.
    if os.path.exists(JAR_VANILLA):
        with zipfile.ZipFile(JAR_VANILLA) as zf:
            txt = _leer_zip_texto(zf, 'assets/minecraft/lang/en_us.json')
            if txt:
                return _nombres_desde_lang(txt, 'minecraft')
    return {}


# ----------------------------- MODS -----------------------------
def _modids_en_jar(zf):
    """Devuelve el conjunto de modids que tienen carpeta de lang en el .jar."""
    modids = set()
    for n in zf.namelist():
        # assets/<modid>/lang/<algo>.json
        partes = n.split('/')
        if len(partes) >= 4 and partes[0] == 'assets' and partes[2] == 'lang' \
                and n.endswith('.json'):
            modids.add(partes[1])
    return modids


def _lang_del_resourcepack(modid):
    """Texto es_es del resourcepack de traduccion, si existe (prioridad)."""
    p = os.path.join(RESOURCEPACK_ES, modid, 'lang', 'es_es.json')
    if os.path.exists(p):
        try:
            with open(p, 'r', encoding='utf-8-sig', errors='replace') as f:
                return f.read()
        except Exception:
            return None
    return None


def nombres_mods(dir_mods):
    """Recorre los .jar de dir_mods y arma id->nombre para cada mod.
    Prioridad por mod: resourcepack es_es > jar es_es > jar en_us."""
    salida = {}
    if not os.path.isdir(dir_mods):
        return salida, 0, []
    jars = sorted(x for x in os.listdir(dir_mods) if x.lower().endswith('.jar'))
    procesados = 0
    sin_lang = []
    for jar in jars:
        ruta = os.path.join(dir_mods, jar)
        try:
            with zipfile.ZipFile(ruta) as zf:
                modids = _modids_en_jar(zf)
                if not modids:
                    sin_lang.append(jar)
                    continue
                for modid in modids:
                    if modid == 'minecraft':
                        continue
                    # 1) resourcepack es_es (traduccion curada del server)
                    mapa = {}
                    rp = _lang_del_resourcepack(modid)
                    if rp:
                        mapa = _nombres_desde_lang(rp, modid)
                    # 2) es_es dentro del jar
                    if not mapa:
                        txt = _leer_zip_texto(zf, 'assets/%s/lang/es_es.json' % modid)
                        if txt:
                            mapa = _nombres_desde_lang(txt, modid)
                    # 3) en_us dentro del jar
                    if not mapa:
                        txt = _leer_zip_texto(zf, 'assets/%s/lang/en_us.json' % modid)
                        if txt:
                            mapa = _nombres_desde_lang(txt, modid)
                    for k, v in mapa.items():
                        # No pisar un nombre ya puesto por un jar anterior salvo
                        # que el nuevo venga del resourcepack (ya priorizado arriba).
                        salida.setdefault(k, v)
            procesados += 1
        except zipfile.BadZipFile:
            sin_lang.append(jar + ' (zip invalido)')
        except Exception as ex:
            sin_lang.append('%s (%s)' % (jar, ex))
    return salida, procesados, sin_lang


def generar(dir_mods, salida):
    van = nombres_vanilla()
    mods, n_jars, sin_lang = nombres_mods(dir_mods)

    combinado = dict(van)
    for k, v in mods.items():
        combinado.setdefault(k, v)

    lista = [{'id': k, 'nombre': v} for k, v in combinado.items()]
    lista.sort(key=lambda x: x['id'])

    os.makedirs(os.path.dirname(salida), exist_ok=True)
    doc = {
        'version': 1,
        'total': len(lista),
        'vanilla': len(van),
        'mods_jars': n_jars,
        'items': lista,
    }
    with open(salida, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False)
    return doc, sin_lang


def main(argv=None):
    ap = argparse.ArgumentParser(description='Genera el indice de nombres de items para el panel.')
    ap.add_argument('--mods', default=MODS_DEFECTO, help='Carpeta con los .jar de los mods.')
    ap.add_argument('--salida', default=SALIDA_DEFECTO, help='Ruta del items_index.json a escribir.')
    args = ap.parse_args(argv)

    print('Mods:   %s' % args.mods)
    print('Salida: %s' % args.salida)
    doc, sin_lang = generar(args.mods, args.salida)
    print('Vanilla: %d nombres' % doc['vanilla'])
    print('Mods:    %d .jar procesados' % doc['mods_jars'])
    print('TOTAL:   %d items en el indice' % doc['total'])
    if sin_lang:
        print('Sin lang (%d): %s' % (len(sin_lang), ', '.join(sin_lang[:15])
                                     + ('...' if len(sin_lang) > 15 else '')))
    if doc['total'] == 0:
        print('ADVERTENCIA: el indice quedo vacio. Revisa las rutas.')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
