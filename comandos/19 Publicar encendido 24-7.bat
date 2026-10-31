@echo off
REM Publica en Cloudflare (gratis) la pagina de encendido 24/7 para tus amigos
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..ncendido-nube\publicar.ps1"
echo.
pause
