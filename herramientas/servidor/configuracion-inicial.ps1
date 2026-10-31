<#
    configuracion-inicial.ps1
    ------------------------------------------------------------
    Todo lo necesario para que tus amigos enciendan el servidor desde tu
    panel, en una sola ventana:
      1. Tu cuenta de administrador del panel.
      2. Una cuenta "amigos" (rol jugador: solo ver estado y encender).
      3. El enlace fijo de ngrok con arranque automatico.
    Al final deja en el Escritorio un archivo con el enlace y el usuario.
#>
$ErrorActionPreference = "Stop"
$RAIZ = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$auth = Join-Path $RAIZ "panel\auth.py"
$py = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $py) { throw "No encontre Python." }

Clear-Host
Write-Host "============================================================" -ForegroundColor DarkCyan
Write-Host "  SERVER PANAS - Configuracion del acceso para tus amigos" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor DarkCyan
Write-Host "  Solo te pide 3 cosas. Las contrasenas no se ven al escribir."
Write-Host ""

function Crear([string] $usuario, [string] $rol, [string] $explicacion) {
    Write-Host ""
    Write-Host "--- $explicacion ---" -ForegroundColor Yellow
    while ($true) {
        & $py -I $auth crear $usuario $rol
        if ($LASTEXITCODE -eq 0) { return }
        Write-Host "  Intentalo otra vez." -ForegroundColor Red
    }
}

Crear "admin" "admin" "1/3  Tu contrasena de administrador (usuario: admin)"
Crear "amigos" "jugador" "2/3  Contrasena para tus amigos (usuario: amigos, solo pueden ENCENDER)"

Write-Host ""
Write-Host "--- 3/3  Enlace fijo de ngrok ---" -ForegroundColor Yellow
Start-Process "https://dashboard.ngrok.com/get-started/your-authtoken"
Write-Host "  Te abri la pagina de ngrok: copia tu authtoken y pegalo aqui (clic derecho)."
& (Join-Path $PSScriptRoot "configurar-ngrok.ps1")

$t = Get-Content (Join-Path $RAIZ "panel\datos\tunel.json") -Raw | ConvertFrom-Json
$nota = Join-Path ([Environment]::GetFolderPath("Desktop")) "Enlace panel SERVER PANAS.txt"
@"
PANEL SERVER PANAS
==================
Enlace:   $($t.url)

Para tus amigos:  usuario  amigos   (con la contrasena que pusiste)
Para ti:          usuario  admin

Al abrir el enlace por primera vez, ngrok muestra un aviso: pulsa "Visit Site".
El panel funciona mientras este PC este encendido.
"@ | Set-Content -Path $nota -Encoding UTF8

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  LISTO.  Enlace: $($t.url)" -ForegroundColor Green
Write-Host "  Pasa a tus amigos el enlace, el usuario 'amigos' y su contrasena." -ForegroundColor Green
Write-Host "  Lo tienes guardado en el Escritorio: Enlace panel SERVER PANAS.txt" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
