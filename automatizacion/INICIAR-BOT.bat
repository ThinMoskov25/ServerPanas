@echo off
title Bot de Inventarios - SERVER PANAS
echo ============================================================
echo    BOT DE RESPALDO DE INVENTARIOS - SERVER PANAS
echo ============================================================
echo.
echo  Este bot toma un snapshot del inventario de cada jugador
echo  cada 5 minutos, SOLO cuando el servidor esta encendido.
echo.
echo  Los snapshots quedan en la carpeta "snapshots".
echo  Si alguien pierde algo, se puede ver que tenia y reponerlo.
echo.
echo  DEJA ESTA VENTANA ABIERTA mientras quieras que respalde.
echo  Para detenerlo, cierra la ventana o pulsa Ctrl+C.
echo.
echo ============================================================
echo.
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -File ".\bot-inventarios.ps1" -IntervaloMin 5
pause
