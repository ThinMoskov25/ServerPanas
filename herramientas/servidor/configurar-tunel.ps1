<#
    configurar-tunel.ps1
    ------------------------------------------------------------
    Deja el panel publicado con una URL FIJA mediante un tunel con nombre
    de Cloudflare. Requiere haber autorizado antes cloudflared en tu cuenta
    (cloudflared tunnel login -> crea %USERPROFILE%\.cloudflared\cert.pem).

    Hace, sin preguntar:
      1. Crea el tunel "serverpanas-panel" (si no existe).
      2. Crea el registro DNS <Subdominio>.<tu dominio> apuntando al tunel.
      3. Escribe la configuracion del tunel hacia http://127.0.0.1:8765.
      4. Autoriza ese dominio en el panel (hosts_publicos) y lo reinicia.
      5. Crea la tarea programada "ServerPanas-Tunel" (arranca al iniciar sesion)
         y la lanza ahora.

    Uso:  .\configurar-tunel.ps1 [-Subdominio panel]
#>
param([string] $Subdominio = "panel")

$ErrorActionPreference = "Stop"
$RAIZ = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$NOMBRE = "serverpanas-panel"
$dirCf = Join-Path $env:USERPROFILE ".cloudflared"
$cert = Join-Path $dirCf "cert.pem"
$cf = @("${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe", "${env:ProgramFiles}\cloudflared\cloudflared.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $cf) { throw "No encontre cloudflared. Instalalo con: winget install Cloudflare.cloudflared" }
if (-not (Test-Path $cert)) { throw "Falta $cert. Ejecuta antes: cloudflared tunnel login" }

function CF([string[]] $a) {
    $prev = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { $o = & $cf @a 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $prev }
    return @($o)
}

# 1) Tunel
$lista = (CF @("tunnel", "list", "--output", "json") | Where-Object { $_ -notmatch 'RemoteException' }) -join "`n"
$tunel = ($lista | ConvertFrom-Json) | Where-Object { $_.name -eq $NOMBRE } | Select-Object -First 1
if (-not $tunel) {
    Write-Host "Creando el tunel $NOMBRE..."
    CF @("tunnel", "create", $NOMBRE) | Out-Host
    $lista = (CF @("tunnel", "list", "--output", "json") | Where-Object { $_ -notmatch 'RemoteException' }) -join "`n"
    $tunel = ($lista | ConvertFrom-Json) | Where-Object { $_.name -eq $NOMBRE } | Select-Object -First 1
}
if (-not $tunel) { throw "No se pudo crear el tunel." }
$id = $tunel.id
$cred = Join-Path $dirCf "$id.json"
if (-not (Test-Path $cred)) { throw "Falta el archivo de credenciales del tunel: $cred" }
Write-Host "Tunel: $NOMBRE ($id)"

# 2) DNS: cloudflared completa el subdominio con el dominio autorizado
$salidaDns = (CF @("tunnel", "route", "dns", "--overwrite-dns", $NOMBRE, $Subdominio)) -join "`n"
$hosts = [regex]::Matches($salidaDns, '\b[a-z0-9-]+(\.[a-z0-9-]+){1,}\b', 'IgnoreCase') |
    ForEach-Object { $_.Value.ToLower() } | Where-Object { $_ -like "$($Subdominio.ToLower()).*" -and $_ -notmatch 'cfargotunnel' }
$hostname = $hosts | Select-Object -First 1
if (-not $hostname) { Write-Host $salidaDns; throw "No pude determinar el nombre DNS creado." }
Write-Host "DNS: $hostname -> tunel"

# 3) Configuracion del tunel
$yml = Join-Path $dirCf "$NOMBRE.yml"
@"
tunnel: $id
credentials-file: $cred
no-autoupdate: true
ingress:
  - hostname: $hostname
    service: http://127.0.0.1:8765
  - service: http_status:404
"@ | Set-Content -Path $yml -Encoding ASCII
Write-Host "Configuracion: $yml"

# 4) Autorizar el dominio en el panel y reiniciarlo
$cfgPanel = Join-Path $RAIZ "panel\datos\config-panel.json"
$cfg = if (Test-Path $cfgPanel) { Get-Content $cfgPanel -Raw | ConvertFrom-Json } else { [pscustomobject]@{} }
$lista = @(); if ($cfg.PSObject.Properties.Name -contains 'hosts_publicos') { $lista = @($cfg.hosts_publicos) }
if ($lista -notcontains $hostname) { $lista += $hostname }
$cfg | Add-Member -NotePropertyName hosts_publicos -NotePropertyValue $lista -Force
$cfg | ConvertTo-Json | Set-Content -Path $cfgPanel -Encoding UTF8
schtasks /End /TN "ServerPanas-Panel" | Out-Null
Start-Sleep 2
Get-NetTCPConnection -LocalPort 8765 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
schtasks /Run /TN "ServerPanas-Panel" | Out-Null
Write-Host "Panel reiniciado con $hostname autorizado."

# 5) Tarea programada del tunel (al iniciar sesion, sin ventana)
$accion = New-ScheduledTaskAction -Execute $cf -Argument "tunnel --config `"$yml`" run $NOMBRE"
$disparador = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName "ServerPanas-Tunel" -Action $accion -Trigger $disparador -Settings $ajustes -Description "Tunel de Cloudflare hacia el panel SERVER PANAS" -Force | Out-Null
Stop-ScheduledTask -TaskName "ServerPanas-Tunel" -ErrorAction SilentlyContinue
Start-ScheduledTask -TaskName "ServerPanas-Tunel"
Write-Host "Tarea ServerPanas-Tunel creada y en marcha."

[pscustomobject]@{ hostname = $hostname; url = "https://$hostname"; tunel = $id } | ConvertTo-Json |
    Set-Content -Path (Join-Path $RAIZ "panel\datos\tunel.json") -Encoding UTF8
Write-Host ""
Write-Host "PANEL PUBLICADO EN: https://$hostname" -ForegroundColor Green
