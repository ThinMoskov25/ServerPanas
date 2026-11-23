import sys, time, urllib.request, json
sys.stdout.reconfigure(encoding='utf-8')
B = 'http://127.0.0.1:8793'
RUTAS = ['/api/estado', '/api/sesion', '/api/tareas', '/api/jugadores', '/api/jugador?jugador=SrBalduR', '/api/consola/jugadores',
         '/api/consola/waystones?jugador=SrBalduR', '/api/mod/jugadores', '/api/mod/respaldos?jugador=TsubasaDraws',
         '/api/mod/ver?jugador=TsubasaDraws&fuente=mod&archivo=2026-11-21_23-42-32', '/api/misiones', '/api/eco/saldos', '/api/eco/mercado',
         '/api/eco/valores', '/api/eco/categorias', '/api/eco/overrides', '/api/eco/catalogo', '/api/eco/buscar-items?q=diamante',
         '/api/eco/item-valor?q=minecraft:diamond', '/api/datapacks', '/api/entidades/mods', '/api/entidades/buscar?q=dragon',
         '/api/bot', '/api/datapacks/elites', '/api/datapacks/vida', '/api/datapacks/programador', '/api/datapacks/dimensiones',
         '/api/archivos?ruta=', '/api/logs?tipo=eventos&n=200', '/api/acceso']
veces = int(sys.argv[1]) if len(sys.argv) > 1 else 2
res = {}
for r in RUTAS:
    ts = []
    for _ in range(veces):
        t = time.time()
        try:
            req = urllib.request.Request(B + r, headers={'X-Panel-SP': '1'})
            with urllib.request.urlopen(req, timeout=120) as f:
                n = len(f.read())
            ok = 'ok'
        except Exception as e:
            n, ok = 0, str(e)[:40]
        ts.append(time.time() - t)
    res[r] = ts
    print('%-75s %s  %s' % (r[:75], ' '.join('%6.2fs' % x for x in ts), ok if ok != 'ok' else '%d B' % n))
json.dump(res, open(sys.argv[2] if len(sys.argv) > 2 else 'medidas.json', 'w'))
