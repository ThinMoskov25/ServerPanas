<#
    desplegar.ps1 - Lleva el panel SERVER PANAS a tu servidor en la nube
    ------------------------------------------------------------------
    Copia el panel (con tus usuarios), configura los secretos y deja el panel
    y el tunel de ngrok como servicios 24/7. Tu PC deja de ser necesario.

    Uso:  .\desplegar.ps1 -IP 123.45.67.89 [-Usuario ubuntu]

    - Los secretos salen de donde ya estan guardados en este PC (token de
      exaroton cifrado con DPAPI y authtoken de ngrok) y viajan por SSH.
      Las copias temporales se borran al terminar.
    - Al acabar desactiva el panel y el tunel de este PC: el mismo dominio de
      ngrok solo puede estar activo en un sitio, y asi no se duplican las copias
      de inventario. Para volver atras: Enable-ScheduledTask ServerPanas-Panel
      y ServerPanas-Tunel.
#>
param(
    [Parameter(Mandatory)] [string] $IP,
    [string] $Usuario = "ubuntu",
    [string] $Llave = (Join-Path $env:USERPROFILE ".ssh\serverpanas_nube")
)

$ErrorActionPreference = "Stop"
$RAIZ = Split-Path -Parent $PSScriptRoot
$PANEL = Join-Path $RAIZ "panel"
. (Join-Path $RAIZ "herramientas\lib\Exaroton.ps1")

function Paso([string] $t) { Write-Host ""; Write-Host "== $t ==" -ForegroundColor Cyan }

if (-not (Test-Path $Llave)) { throw "No encuentro la llave SSH $Llave" }
$destino = "$Usuario@$IP"
$opSsh = @("-i", $Llave, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15")

Paso "Comprobando acceso al servidor"
& ssh @opSsh $destino "echo conectado" | Out-Host
if ($LASTEXITCODE -ne 0) { throw "No pude entrar por SSH a $destino." }

$tunel = Get-Content (Join-Path $PANEL "datos\tunel.json") -Raw | ConvertFrom-Json
if (-not $tunel.fijo) { throw "El panel no tiene dominio fijo de ngrok todavia." }
$dominio = $tunel.dominio

$exito = $false
$staging = Join-Path $env:TEMP ("serverpanas-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
try {
    Paso "Preparando archivos"
    New-Item -ItemType Directory -Force -Path "$staging\panel\datos" | Out-Null
    Get-ChildItem $PANEL -File | Copy-Item -Destination "$staging\panel"
    Copy-Item (Join-Path $PANEL "static") -Destination "$staging\panel" -Recurse
    foreach ($f in @("usuarios.json", "config-panel.json", "items_index.json", "entities_index.json", "estado-espejo.json")) {
        $p = Join-Path $PANEL "datos\$f"
        if (Test-Path $p) { Copy-Item $p -Destination "$staging\panel\datos" }
    }
    Copy-Item (Join-Path $PSScriptRoot "instalar-en-servidor.sh") -Destination $staging

    # Secretos: desde los almacenes de este PC, sin mostrarlos
    Initialize-Exaroton
    $tokNgrok = (Select-String -Path (Join-Path $env:LOCALAPPDATA "ngrok\ngrok.yml") -Pattern '^\s*authtoken:\s*(\S+)').Matches[0].Groups[1].Value
    $lf = [string][char]10
    [IO.File]::WriteAllText("$staging\panel.env", ("EXAROTON_TOKEN=$($script:ExaToken)", "EXAROTON_SERVER_ID=$($script:ExaServerId)", "PANEL_URL_PUBLICA=https://$dominio") -join $lf)
    [IO.File]::WriteAllText("$staging\ngrok.env", ("NGROK_AUTHTOKEN=$tokNgrok", "NGROK_DOMINIO=$dominio") -join $lf)
    $tokNgrok = $null
    # Saltos de linea Unix en el instalador
    $sh = [IO.File]::ReadAllText("$staging\instalar-en-servidor.sh") -replace "`r`n", $lf
    [IO.File]::WriteAllText("$staging\instalar-en-servidor.sh", $sh)

    Paso "Liberando el dominio en este PC (solo puede estar activo en un sitio)"
    Stop-ScheduledTask -TaskName "ServerPanas-Tunel" -ErrorAction SilentlyContinue
    Disable-ScheduledTask -TaskName "ServerPanas-Tunel" -ErrorAction SilentlyContinue | Out-Null
    Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force

    Paso "Subiendo al servidor"
    & ssh @opSsh $destino "rm -rf /tmp/serverpanas && mkdir -p /tmp/serverpanas && chmod 700 /tmp/serverpanas" | Out-Host
    & scp @opSsh -r -q "$staging\*" "${destino}:/tmp/serverpanas/"
    if ($LASTEXITCODE -ne 0) { throw "Fallo la copia por SCP." }

    Paso "Instalando servicios (tarda 1-2 minutos la primera vez)"
    & ssh @opSsh $destino "sudo bash /tmp/serverpanas/instalar-en-servidor.sh" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "La instalacion en el servidor fallo (mira los mensajes de arriba)." }

    Paso "Comprobando el enlace desde Internet"
    Start-Sleep 5
    try {
        $r = Invoke-WebRequest "https://$dominio/login" -UseBasicParsing -TimeoutSec 25 -Headers @{ "ngrok-skip-browser-warning" = "1" }
        Write-Host "  [OK] https://$dominio responde desde la nube (HTTP $($r.StatusCode))." -ForegroundColor Green
    } catch { throw "El enlace no responde desde la nube: $($_.Exception.Message)" }
    $exito = $true
} finally {
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    if (-not $exito) {
        # Si algo fallo, el panel de este PC vuelve a quedar publicado como estaba
        Write-Host "  [!] Restaurando el enlace en este PC..." -ForegroundColor Yellow
        Enable-ScheduledTask -TaskName "ServerPanas-Tunel" -ErrorAction SilentlyContinue | Out-Null
        Start-ScheduledTask -TaskName "ServerPanas-Tunel" -ErrorAction SilentlyContinue
    }
}

Paso "Desactivando el panel de este PC (ya corre en la nube)"
Stop-ScheduledTask -TaskName "ServerPanas-Panel" -ErrorAction SilentlyContinue
Disable-ScheduledTask -TaskName "ServerPanas-Panel" -ErrorAction SilentlyContinue | Out-Null
Get-NetTCPConnection -LocalPort 8765 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
@{ ip = $IP; usuario = $Usuario; dominio = $dominio; desplegado = (Get-Date -Format s) } | ConvertTo-Json |
    Set-Content (Join-Path $PSScriptRoot "servidor.json") -Encoding UTF8

Write-Host ""
Write-Host "  LISTO: el panel funciona 24/7 en https://$dominio aunque apagues tu PC." -ForegroundColor Green
