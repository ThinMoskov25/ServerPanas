#!/usr/bin/env bash
# ------------------------------------------------------------------
# Instala el panel SERVER PANAS en un servidor Linux (Ubuntu/Debian,
# p. ej. Oracle Cloud "Always Free") para que funcione 24/7 sin tu PC.
#
# Lo ejecuta desplegar.ps1 por SSH; no hace falta lanzarlo a mano.
# Espera en /tmp/serverpanas/:
#   panel/            codigo del panel (con datos/usuarios.json)
#   panel.env         EXAROTON_TOKEN, EXAROTON_SERVER_ID, PANEL_URL_PUBLICA
#   ngrok.env         NGROK_AUTHTOKEN, NGROK_DOMINIO
# ------------------------------------------------------------------
set -euo pipefail
ORIGEN=/tmp/serverpanas
DESTINO=/opt/serverpanas
ETC=/etc/serverpanas

echo "== Paquetes del sistema =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq python3 curl ca-certificates gnupg >/dev/null

echo "== ngrok =="
if ! command -v ngrok >/dev/null; then
  curl -fsSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc | gpg --dearmor -o /usr/share/keyrings/ngrok.gpg
  echo "deb [signed-by=/usr/share/keyrings/ngrok.gpg] https://ngrok-agent.s3.amazonaws.com buster main" > /etc/apt/sources.list.d/ngrok.list
  apt-get update -qq && apt-get install -y -qq ngrok >/dev/null
fi

echo "== Usuario de servicio y archivos =="
id serverpanas >/dev/null 2>&1 || useradd --system --home "$DESTINO" --shell /usr/sbin/nologin serverpanas
mkdir -p "$DESTINO" "$ETC"
# Conserva los datos que ya tenga el servidor (copias de inventario, logs)
if [ -d "$DESTINO/panel/datos" ]; then cp -a "$DESTINO/panel/datos/." "$ORIGEN/panel/datos/" 2>/dev/null || true; fi
rm -rf "$DESTINO/panel"
cp -a "$ORIGEN/panel" "$DESTINO/panel"
install -m 600 -o root -g root "$ORIGEN/panel.env" "$ETC/panel.env"
install -m 600 -o root -g root "$ORIGEN/ngrok.env" "$ETC/ngrok.env"
chown -R serverpanas:serverpanas "$DESTINO"

echo "== Servicios =="
cat > /etc/systemd/system/serverpanas-panel.service <<'EOF'
[Unit]
Description=Panel SERVER PANAS
After=network-online.target
Wants=network-online.target

[Service]
User=serverpanas
WorkingDirectory=/opt/serverpanas/panel
EnvironmentFile=/etc/serverpanas/panel.env
ExecStart=/usr/bin/python3 -u app.py
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/opt/serverpanas/panel/datos
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/systemd/system/serverpanas-ngrok.service <<'EOF'
[Unit]
Description=Tunel ngrok hacia el panel SERVER PANAS
After=serverpanas-panel.service network-online.target
Wants=network-online.target

[Service]
User=serverpanas
EnvironmentFile=/etc/serverpanas/ngrok.env
# ngrok toma el token de la variable NGROK_AUTHTOKEN (no aparece en la lista de procesos)
ExecStart=/bin/sh -c 'exec /usr/bin/ngrok http 127.0.0.1:8765 --url=https://${NGROK_DOMINIO} --log=stdout'
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now serverpanas-panel.service serverpanas-ngrok.service
systemctl restart serverpanas-panel.service serverpanas-ngrok.service
rm -rf "$ORIGEN"

sleep 6
systemctl is-active --quiet serverpanas-panel && echo "[OK] Panel en marcha" || { echo "[X] El panel no arranco"; journalctl -u serverpanas-panel -n 20 --no-pager; exit 1; }
systemctl is-active --quiet serverpanas-ngrok && echo "[OK] Tunel en marcha" || { echo "[X] El tunel no arranco"; journalctl -u serverpanas-ngrok -n 20 --no-pager; exit 1; }
echo "LISTO"
