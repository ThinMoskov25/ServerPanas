@echo off
REM Abre el Panel SERVER PANAS en el navegador.
REM El panel corre solo en segundo plano (tarea "ServerPanas-Panel").
REM Si no estuviera corriendo, lo arranca.
powershell -NoProfile -Command "try { Invoke-WebRequest http://127.0.0.1:8765/api/estado -UseBasicParsing -TimeoutSec 3 | Out-Null } catch { Start-ScheduledTask -TaskName 'ServerPanas-Panel'; Start-Sleep -Seconds 4 }"
start "" http://127.0.0.1:8765
