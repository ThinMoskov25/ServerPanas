<#
    configurar-ngrok.ps1
    ------------------------------------------------------------
    Publica el panel con un ENLACE FIJO de ngrok (plan gratuito).

      1. Pide tu authtoken de ngrok (entrada oculta) y lo guarda en la
         configuracion de ngrok. Si ya estaba guardado, puedes saltarlo.
      2. Pide tu dominio fijo gratuito (ngrok.com -> Dashboard -> Domains),
         por ejemplo  algo-algo.ngrok-free.app
      3. Autoriza ese dominio en el panel y lo reinicia.
      4. Crea la tarea programada "ServerPanas-Tunel" (arranca al iniciar
         sesion, sin ventana, se reinicia sola si se cae) y la lanza ahora.

    Uso:  .\configurar-ngrok.ps1 [-Dominio algo.ngrok-free.app]
#>
param(
    [string] $Dominio = "",
    # Lo usa el panel: el authtoken ya lo guardo el, no hay preguntas ni reinicio del panel
    [switch] $DesdePanel
)

$ErrorActionPreference = "Stop"
$RAIZ = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$TAREA = "ServerPanas-Tunel"
$PANEL = "http://127.0.0.1:8765"

$ngrok = @("$env:LOCALAPPDATA\Microsoft\WinGet\Links\ngrok.exe") + @(Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter ngrok.exe -ErrorAction SilentlyContinue | ForEach-Object FullName) |
    Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $ngrok) { throw "No encontre ngrok. Instalalo con: winget install Ngrok.Ngrok" }

function Ngrok([string[]] $a) {
    $prev = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { $o = & $ngrok @a 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $prev }
    return @($o)
}

Write-Host ""
Write-Host "=== Enlace del panel con ngrok ===" -ForegroundColor Cyan

# 1) Authtoken (oculto; nunca se muestra ni se guarda fuera de la config de ngrok)
$cfgNgrok = Join-Path $env:LOCALAPPDATA "ngrok\ngrok.yml"
$yaConfigurado = (Test-Path $cfgNgrok) -and (Select-String -Path $cfgNgrok -Pattern '^\s*authtoken:' -Quiet)
$token = $null
if (-not $DesdePanel) {
    $tokenSeguro = Read-Host -AsSecureString "Pega tu authtoken de ngrok (Dashboard > Your Authtoken). Enter vacio = mantener el actual"
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tokenSeguro)
    try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
if ($token) {
    $r = Ngrok @("config", "add-authtoken", $token.Trim())
    $token = $null
    if (($r -join " ") -notmatch 'saved') { $r | Out-Host; throw "ngrok no acepto el authtoken." }
    Write-Host "  [OK] Authtoken guardado en la configuracion de ngrok." -ForegroundColor Green
} elseif (-not $yaConfigurado) {
    Write-Host "  [!] No hay authtoken guardado todavia; ngrok podria no arrancar." -ForegroundColor Yellow
}

# 2) Dominio: si tienes el dominio fijo gratuito reclamado en ngrok (Dashboard > Domains)
#    se usa ese; si no, ngrok da un enlace automatico y el panel lee siempre el actual.
if (-not $Dominio -and -not $DesdePanel) {
    $Dominio = Read-Host "Dominio fijo de ngrok (Dashboard > Domains). Enter = enlace automatico"
}
$fijo = [bool]$Dominio
if ($fijo) {
    $Dominio = ($Dominio -replace '^https?://', '').Trim().TrimEnd('/').ToLower()
    if ($Dominio -notmatch '^[a-z0-9-]+(\.[a-z0-9-]+)+$') { throw "Dominio no valido: $Dominio" }
}

# 3) Tarea programada del tunel (arranca al iniciar sesion, sin ventana, se reinicia si cae)
$log = Join-Path $RAIZ "panel\datos\ngrok.log"
$argUrl = if ($fijo) { " --url=https://$Dominio" } else { "" }
$accion = New-ScheduledTaskAction -Execute $ngrok -Argument "http $PANEL$argUrl --log=`"$log`""
$disparador = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $TAREA -Action $accion -Trigger $disparador -Settings $ajustes -Description "Tunel ngrok hacia el panel SERVER PANAS" -Force | Out-Null
Stop-ScheduledTask -TaskName $TAREA -ErrorAction SilentlyContinue
Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item $log -ErrorAction SilentlyContinue
Start-ScheduledTask -TaskName $TAREA

# 4) Leer el enlace real que ngrok ha abierto (API local de ngrok)
$url = $null
for ($i = 0; $i -lt 25 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    try {
        $t = (Invoke-RestMethod "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 2).tunnels | Where-Object { $_.public_url -like "https://*" } | Select-Object -First 1
        if ($t) { $url = $t.public_url }
    } catch {}
}
if (-not $url) {
    $err = (Get-Content $log -ErrorAction SilentlyContinue | Select-String 'err="([^"\]+)' | Select-Object -Last 1)
    $detalle = if ($err) { $err.Matches[0].Groups[1].Value } else { "revisa $log" }
    if ($fijo) { throw "ngrok no pudo abrir el dominio fijo ${Dominio}: $detalle" }
    throw "ngrok no pudo abrir el enlace: $detalle"
}
$host_ = ([uri]$url).Host
# El panel lee tunel.json en cada peticion; en modo automatico consulta el enlace vivo a ngrok
[pscustomobject]@{ tipo = "ngrok"; fijo = $fijo; dominio = $host_; url = $url } | ConvertTo-Json |
    Set-Content -Path (Join-Path $RAIZ "panel\datos\tunel.json") -Encoding UTF8
Write-Host "  [OK] Tarea $TAREA en marcha (arranca sola al iniciar sesion)." -ForegroundColor Green

# Comprobacion desde Internet
Start-Sleep 3
try {
    $r = Invoke-WebRequest "$url/login" -UseBasicParsing -TimeoutSec 20 -Headers @{ "ngrok-skip-browser-warning" = "1" }
    Write-Host "  [OK] El panel responde desde Internet (HTTP $($r.StatusCode))." -ForegroundColor Green
} catch {
    Write-Host "  [!] Aun no responde desde Internet: $($_.Exception.Message)" -ForegroundColor Yellow
}
Write-Host ""
Write-Host ("  ENLACE DEL PANEL:  $url" + $(if ($fijo) { "  (fijo)" } else { "  (cambia si se reinicia el PC)" })) -ForegroundColor Green
try { Set-Clipboard -Value $url; Write-Host "  (copiado al portapapeles)" } catch {}
