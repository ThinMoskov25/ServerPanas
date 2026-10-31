@echo off
REM Configuracion unica: cuentas del panel y enlace fijo de ngrok
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\herramientas\servidor\configuracion-inicial.ps1"
echo.
pause
