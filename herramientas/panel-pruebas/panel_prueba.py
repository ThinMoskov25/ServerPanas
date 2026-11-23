import sys, os, tempfile
PANEL, REG = sys.argv[1], sys.argv[2]
sys.path.insert(0, PANEL)
import exaroton
exaroton.Exaroton.comando = lambda self, t: open(REG, 'a', encoding='utf-8').write(t + '\n')  # NO se envia
exaroton.Exaroton.accion = lambda self, *a: {}
exaroton.Exaroton.escribir_archivo = lambda self, *a: None
exaroton.Exaroton.crear_directorio = lambda self, *a: None
exaroton.Exaroton.logs = lambda self: ''
import auth
auth.ARCH_USUARIOS = os.path.join(tempfile.mkdtemp(), 'u.json')
import app
app.time.sleep = lambda s: None
app.CONFIG['puerto'] = 8793
import threading; threading.Thread(target=app.bucle_precarga, daemon=True).start()
app.ThreadingHTTPServer(('127.0.0.1', 8793), app.Manejador).serve_forever()
