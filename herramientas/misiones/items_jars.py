"""Lista de IDs de item reales sacada de los modelos de item de cada .jar (+ vanilla del indice)."""
import glob, json, os, re, sys, zipfile
mods, salida = sys.argv[1], sys.argv[2]
idx = sys.argv[3] if len(sys.argv) > 3 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'panel', 'datos', 'items_index.json')
ids = {i['id'] for i in json.load(open(idx, encoding='utf-8'))['items'] if i['id'].startswith('minecraft:')}
pat = re.compile(r'^assets/([a-z0-9_.\-]+)/models/item/([a-z0-9_./\-]+)\.json$')
for jar in glob.glob(os.path.join(mods, '*.jar')):
    try:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                m = pat.match(n)
                if m:
                    ids.add('%s:%s' % m.groups())
    except zipfile.BadZipFile:
        pass
json.dump({'items': [{'id': i} for i in sorted(ids)]}, open(salida, 'w', encoding='utf-8'))
print(len(ids), 'ids')
