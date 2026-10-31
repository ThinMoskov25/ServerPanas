<#
    instalar-override.ps1
    Sube el jar de ServerEco (con overrides + /tienda valor) a mods/, reinicia
    el servidor, espera a que vuelva online y verifica en los logs que no hay
    errores y que el mod cargo. One-shot para esta entrega.
#>
$ErrorActionPreference = "Stop"
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $base "exaroton-lib.ps1")

$token    = Get-ExarotonToken  -RutaToken  (Join-Path $base "exaroton-token.xml")
$cfg      = Get-ExarotonConfig -RutaConfig (Join-Path $base "config.json")
$serverId = $cfg.serverId
$apiBase  = Get-ApiBase $serverId
$headers  = @{ Authorization = "Bearer $token" }

$jarLocal   = Join-Path $base "..\mods-propios\EconomyMod\build\libs\servereco-forge-1.20.1-1.0.0.jar"
$jarLocal   = (Resolve-Path $jarLocal).Path
$remotePath = "mods/servereco-forge-1.20.1-1.0.0.jar"

Write-Host "Subiendo jar a $remotePath ..."
$bytes = [System.IO.File]::ReadAllBytes($jarLocal)
$uri = "$apiBase/files/data/$remotePath"
Invoke-RestMethod -Method Put -Uri $uri -Headers $headers -ContentType "application/octet-stream" -Body $bytes | Out-Null
Write-Host ("Jar subido ({0} KB)." -f [int]($bytes.Length/1024))

$estado = (Get-ServerStatus -Token $token -ServerId $serverId).status
Write-Host "Estado actual: $estado"

if ($estado -eq 1) {
    Write-Host "Avisando y guardando..."
    try { Invoke-ServerCommand -Token $token -ServerId $serverId -Command "say [Panel] Reinicio para aplicar valores personalizados de la tienda. Volvemos en ~1 min." } catch {}
    try { Invoke-ServerCommand -Token $token -ServerId $serverId -Command "save-all" } catch {}
    Start-Sleep -Seconds 4
    Write-Host "Reiniciando..."
    Invoke-RestMethod -Method Post -Uri "$apiBase/restart/" -Headers $headers | Out-Null
} else {
    Write-Host "Servidor apagado. Encendiendo..."
    Invoke-RestMethod -Method Post -Uri "$apiBase/start/" -Headers $headers | Out-Null
}

Write-Host "Esperando a que el servidor vuelva a estar online..."
$fin = (Get-Date).AddMinutes(8)
$online = $false
while ((Get-Date) -lt $fin) {
    Start-Sleep -Seconds 8
    try {
        $st = (Get-ServerStatus -Token $token -ServerId $serverId).status
        Write-Host ("  status={0}" -f $st)
        if ($st -eq 1) { $online = $true; break }
    } catch { Write-Host "  (status no disponible aun)" }
}
if (-not $online) { throw "El servidor no volvio a encender a tiempo." }
Write-Host "Servidor ONLINE."

Write-Host "Esperando a que el mundo cargue (30 s) antes de probar comandos..."
Start-Sleep -Seconds 30

Write-Host "Probando /tienda valor minecraft:diamond ..."
Invoke-ServerCommand -Token $token -ServerId $serverId -Command "tienda valor minecraft:diamond"
Start-Sleep -Seconds 3
Write-Host "Probando /tienda reload ..."
Invoke-ServerCommand -Token $token -ServerId $serverId -Command "tienda reload"
Start-Sleep -Seconds 3

Write-Host "Leyendo logs..."
$logs = (Invoke-RestMethod -Method Get -Uri "$apiBase/logs/" -Headers $headers).data.content
$lineas = $logs -split "`n"
$cargo   = $lineas | Where-Object { $_ -match "servereco" } | Select-Object -Last 15
$errores = $lineas | Where-Object { $_ -match "Exception|ERROR.*servereco|Failed to load" } | Select-Object -Last 15

Write-Host "`n=== Lineas servereco ==="
$cargo | ForEach-Object { Write-Host $_ }
Write-Host "`n=== Posibles errores ==="
if ($errores) { $errores | ForEach-Object { Write-Host $_ } } else { Write-Host "(ninguno relevante)" }
Write-Host "`nLISTO."
