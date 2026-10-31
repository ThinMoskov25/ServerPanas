/*
 * Encendido 24/7 de SERVER PANAS (Cloudflare Worker).
 *
 * Pagina minima para que los amigos vean el estado del servidor de exaroton
 * y lo enciendan, sin depender del PC del administrador.
 *
 *   https://<worker>/<CLAVE_ENLACE>          pagina con estado y boton Encender
 *   GET  /<CLAVE_ENLACE>/estado              estado en JSON
 *   POST /<CLAVE_ENLACE>/encender            enciende (solo si esta apagado)
 *
 * Secretos (wrangler secret put): EXAROTON_TOKEN, EXAROTON_SERVER_ID, CLAVE_ENLACE.
 * Cualquier otra ruta responde 404, asi que sin el enlace completo no hay nada que ver.
 * No se puede apagar, reiniciar ni usar la consola desde aqui.
 */

const ESTADOS = {
  0: 'Apagado', 1: 'Encendido', 2: 'Encendiendo', 3: 'Apagándose', 4: 'Reiniciando',
  5: 'Guardando', 6: 'Cargando', 7: 'Caído', 8: 'Preparando', 10: 'Preparando',
};
const DIRECCION = 'ShadowBlack77-rEez.exaroton.me:23574';
let ultimoEncendido = 0; // por instancia: evita pulsaciones repetidas

function igualSeguro(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

// Los secretos pueden llegar con un salto de linea final si se guardaron desde una tuberia
const limpio = (v) => (v || '').trim();

async function exaroton(env, ruta) {
  const r = await fetch(`https://api.exaroton.com/v1/servers/${limpio(env.EXAROTON_SERVER_ID)}/${ruta}`, {
    headers: { Authorization: `Bearer ${limpio(env.EXAROTON_TOKEN)}` },
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.success === false) throw new Error(d.error || `exaroton respondió ${r.status}`);
  return d.data;
}

function json(datos, codigo = 200) {
  return new Response(JSON.stringify(datos), {
    status: codigo,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
  });
}

async function estado(env) {
  const s = await exaroton(env, '');
  return {
    estado: s.status,
    texto: ESTADOS[s.status] || `Estado ${s.status}`,
    jugadores: (s.players && s.players.list) || [],
    max: (s.players && s.players.max) || 0,
    direccion: DIRECCION,
  };
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const partes = url.pathname.split('/').filter(Boolean);
    const clave = limpio(env.CLAVE_ENLACE);
    if (!partes.length || clave.length < 20 || !igualSeguro(partes[0], clave)) {
      return new Response('No encontrado', { status: 404 });
    }
    const accion = partes[1] || '';
    try {
      if (req.method === 'GET' && accion === '') return new Response(PAGINA, {
        headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store',
          'Referrer-Policy': 'no-referrer', 'X-Robots-Tag': 'noindex' },
      });
      if (req.method === 'GET' && accion === 'estado') return json(await estado(env));
      if (req.method === 'POST' && accion === 'encender') {
        if (req.headers.get('X-SP') !== '1') return json({ error: 'Petición no válida' }, 400);
        const e = await estado(env);
        if (e.estado !== 0 && e.estado !== 7) return json({ ok: true, mensaje: `El servidor ya está: ${e.texto}` });
        if (Date.now() - ultimoEncendido < 60000) return json({ ok: true, mensaje: 'Ya se pidió encenderlo hace un momento. Espera.' });
        await exaroton(env, 'start/');
        ultimoEncendido = Date.now();
        return json({ ok: true, mensaje: 'Encendiendo. Tarda 2-4 minutos en estar listo.' });
      }
      return new Response('No encontrado', { status: 404 });
    } catch (ex) {
      return json({ error: ex.message }, 502);
    }
  },
};

const PAGINA = `<!DOCTYPE html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex"><title>SERVER PANAS</title>
<style>
:root{color-scheme:dark;--bg:#0a1016;--txt:#edf5f4;--suave:#9aadb4;--verde:#42d3a0;--rojo:#f07178;--amar:#efbd63}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:'Segoe UI',system-ui,sans-serif;color:var(--txt);
background:radial-gradient(ellipse at 78% -20%,rgba(38,116,104,.18),transparent 45%),var(--bg);padding:24px 16px}
.caja{width:100%;max-width:420px;background:linear-gradient(150deg,rgba(20,34,44,.98),rgba(15,27,36,.98));border:1px solid rgba(132,170,180,.14);
border-radius:16px;padding:28px 26px;box-shadow:0 14px 34px rgba(0,0,0,.25);display:flex;flex-direction:column;gap:18px}
.marca{display:flex;align-items:center;gap:12px;font-weight:700;letter-spacing:.06em}
.logo{width:40px;height:40px;border-radius:12px;display:grid;place-items:center;font-size:14px;font-weight:800;color:#061810;background:linear-gradient(145deg,#65e2b2,#48a9c2)}
.marca small{display:block;font-size:10px;color:var(--suave);font-weight:500;letter-spacing:.12em}
.estado{display:flex;align-items:center;gap:10px;font-size:20px;font-weight:700}
.punto{width:12px;height:12px;border-radius:50%;background:#555}
.dato{color:var(--suave);font-size:14px;margin:0}
.dir{font-family:Consolas,monospace;font-size:13.5px;background:#091118;border:1px solid #263a45;border-radius:8px;padding:9px 11px;user-select:all;word-break:break-all}
button{padding:13px;border:1px solid #52d6a5;border-radius:10px;background:linear-gradient(135deg,#58dfac,#35bb8e);color:#062017;font:inherit;font-size:16px;font-weight:700;cursor:pointer}
button:disabled{opacity:.5;cursor:default}
button:focus-visible{outline:2px solid #66a9f7;outline-offset:2px}
.msg{min-height:20px;font-size:14px;margin:0}
</style></head><body>
<main class="caja">
  <div class="marca"><span class="logo">SP</span><span>SERVER PANAS<small>MINECRAFT 1.20.1 · FORGE</small></span></div>
  <div class="estado"><span class="punto" id="punto"></span><span id="texto">Consultando...</span></div>
  <p class="dato" id="jug">&nbsp;</p>
  <div><p class="dato" style="margin-bottom:6px">Dirección para conectarte:</p><div class="dir">${DIRECCION}</div></div>
  <button id="btn" disabled>Encender servidor</button>
  <p class="msg" id="msg" role="status"></p>
  <p class="dato" style="font-size:12px">El servidor se apaga solo si se queda sin jugadores.</p>
</main>
<script>
const base=location.pathname.replace(/\\/$/,'');
const $=id=>document.getElementById(id);
async function refrescar(){
  try{
    const r=await fetch(base+'/estado');const d=await r.json();
    if(d.error)throw new Error(d.error);
    $('texto').textContent=d.texto;
    $('punto').style.background=d.estado===1?'var(--verde)':(d.estado===0||d.estado===7)?'var(--rojo)':'var(--amar)';
    $('jug').textContent=d.estado===1?(d.jugadores.length?'Conectados: '+d.jugadores.join(', '):'Nadie conectado ('+d.jugadores.length+'/'+d.max+')'):'';
    $('btn').disabled=!(d.estado===0||d.estado===7);
  }catch(e){$('texto').textContent='No se pudo consultar';$('msg').textContent=e.message;}
}
$('btn').onclick=async()=>{
  $('btn').disabled=true;$('msg').textContent='Enviando...';
  try{const r=await fetch(base+'/encender',{method:'POST',headers:{'X-SP':'1'}});const d=await r.json();
    $('msg').textContent=d.error||d.mensaje;}catch(e){$('msg').textContent='Error: '+e.message;}
  refrescar();
};
refrescar();setInterval(refrescar,10000);
</script></body></html>`;
