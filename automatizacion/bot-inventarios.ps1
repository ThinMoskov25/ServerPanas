<#
    bot-inventarios.ps1  (v2 - captura EN VIVO)
    --------------------------------------------------------------
    Respaldo de inventarios leyendo el estado EN VIVO desde la
    memoria del servidor (no el .dat congelado).

    Por cada jugador conectado pide al servidor, via consola/API:
      - data get entity <jugador> Inventory   (inventario + armadura)
      - data get entity <jugador> EnderItems  (cofre de ender)
      - data get entity <jugador> ForgeCaps."curios:inventory"  (amuletos)
    Lee la respuesta del log y la guarda con marca de tiempo en:
      snapshots\<fecha>\<hora>\<jugador>.txt

    Mantiene historial para poder comparar "antes vs ahora" y detectar
    que item perdio un jugador, SIN que el jugador tenga que saberlo
    ni desconectarse.

    Uso:
      .\bot-inventarios.ps1 -Once
      .\bot-inventarios.ps1 -IntervaloSeg 30
#>

param(
    [int]  $IntervaloSeg = 30,
    [int]  $DiasRetencion = 3,
    [switch] $Once
)

$ErrorActionPreference = "Stop"
$carpeta = $PSScriptRoot
. (Join-Path $carpeta "exaroton-lib.ps1")
$rutaToken  = Join-Path $carpeta "exaroton-token.xml"
$rutaConfig = Join-Path $carpeta "config.json"
$baseSnap   = Join-Path $carpeta "snapshots"
$logFile    = Join-Path $baseSnap "bot.log"
if (-not (Test-Path $baseSnap)) { New-Item -ItemType Directory -Path $baseSnap -Force | Out-Null }
$ProgressPreference = 'SilentlyContinue'

function Log($m) { $l = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $m; Write-Host $l; Add-Content -Path $logFile -Value $l }

function Cmd($t,$sid,$c) {
    $hc = @{ Authorization = "Bearer $t"; "Content-Type" = "application/json" }
    $body = @{ command = $c } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "https://api.exaroton.com/v1/servers/$sid/command/" -Headers $hc -Body $body | Out-Null
}

function LeerLog($t,$sid) {
    $hg = @{ Authorization = "Bearer $t" }
    return (Invoke-RestMethod -Method Get -Uri "https://api.exaroton.com/v1/servers/$sid/logs/" -Headers $hg).data.content -split "`r?`n"
}

function CapturarJugador($t,$sid,$jugador,$dir) {
    # Pide las 3 secciones; cada respuesta llega al log como
    # "<jugador> has the following entity data: <NBT>"
    $partes = @{ Inventory='Inventory'; Ender='EnderItems'; Curios='ForgeCaps."curios:inventory"' }
    $resultado = [ordered]@{}
    foreach ($nombre in $partes.Keys) {
        Cmd $t $sid "data get entity $jugador $($partes[$nombre])"
        Start-Sleep -Milliseconds 1200
        $lineas = LeerLog $t $sid
        $l = $lineas | Where-Object { $_ -match [regex]::Escape("$jugador has the following entity data") } | Select-Object -Last 1
        if ($l) {
            $idx = $l.IndexOf('entity data:')
            $resultado[$nombre] = if ($idx -ge 0) { $l.Substring($idx + 12).Trim() } else { $l }
        } else {
            $resultado[$nombre] = "(sin datos)"
        }
    }
    $dest = Join-Path $dir "$jugador.txt"
    $texto = "== $jugador ==`nFecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n`n[INVENTORY]`n$($resultado['Inventory'])`n`n[ENDER]`n$($resultado['Ender'])`n`n[CURIOS]`n$($resultado['Curios'])`n"
    Set-Content -Path $dest -Value $texto -Encoding UTF8
}

function TomarSnapshot {
    $t   = Get-ExarotonToken  -RutaToken  $rutaToken
    $cfg = Get-ExarotonConfig -RutaConfig $rutaConfig
    $sid = $cfg.serverId
    $estado = Get-ServerStatus -Token $t -ServerId $sid
    if ($estado.status -ne 1) { Log "Servidor no online. Omito."; return }
    $jugadores = @($estado.players.list)
    if (-not $jugadores -or $jugadores.Count -eq 0) { Log "Sin jugadores conectados."; return }

    $dir = Join-Path (Join-Path $baseSnap (Get-Date -Format "yyyy-MM-dd")) (Get-Date -Format "HH-mm-ss")
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    foreach ($j in $jugadores) { try { CapturarJugador $t $sid $j $dir } catch { Log "Fallo capturando $j : $($_.Exception.Message)" } }
    Log ("Snapshot EN VIVO en {0}  ({1} jugadores)" -f $dir, $jugadores.Count)

    # Limpieza por retencion
    $limite = (Get-Date).Date.AddDays(-$DiasRetencion)
    Get-ChildItem $baseSnap -Directory | Where-Object { $_.Name -match '^\d{4}-\d{2}-\d{2}$' } | ForEach-Object {
        try { if ([datetime]::ParseExact($_.Name,'yyyy-MM-dd',$null) -lt $limite) { Remove-Item $_.FullName -Recurse -Force } } catch {}
    }
}

Log "=== Bot de inventarios v2 (EN VIVO) iniciado ==="
if ($Once) { TomarSnapshot; Log "Captura unica completada."; return }
Log ("Modo continuo: cada $IntervaloSeg s.")
while ($true) { try { TomarSnapshot } catch { Log "Error: $($_.Exception.Message)" }; Start-Sleep -Seconds $IntervaloSeg }
