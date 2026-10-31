@echo off
REM ============================================================
REM  SERVER PANAS - Centro de comandos
REM  Doble clic: abre el menu.   Con accion: SERVERPANAS.bat estado
REM ============================================================
chcp 65001 >nul
title SERVER PANAS - Centro de comandos
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0herramientas\ServerPanas.ps1" %*
if "%~1"=="" exit /b %errorlevel%
echo.
pause
