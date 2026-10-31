<#
    monitor-jugadores.ps1
    --------------------------------------------------------------
    Monitor local y log permanente de actividad de jugadores.

    - Lee el log del servidor cada pocos segundos.
    - Guarda TODO permanentemente en logs\<fecha>-completo.log
      (el log del servidor se borra al reiniciar; este NO).
    - Extrae EVENTOS IMPORTANTES de jugadores a logs\<fecha>-eventos.log:
      conexion (con coords), desconexion, muerte (con causa),
      logros, items recibidos, teleports, tumbas, efectos.
    - Deduplica por firma de linea: cada linea del servidor tiene
      marca de tiempo unica, asi que no se guarda dos veces.

    Uso:
      .\monitor-jugadores.ps1 [-IntervaloSeg 5]
#>

param(
    [int] $IntervaloSeg = 5
)

$ErrorActionPreference = "Stop"
$carpeta  = $PSScriptRoot
. (Join-Path $carpeta "exaroton-lib.ps1")

$rutaToken  = Join-Path $carpeta "exaroton-token.xml"
$rutaConfig = Join-Path $carpeta "config.json"
$baseLog    = Join-Path $carpeta "logs"
if (-not (Test-Path $baseLog)) { New-Item -ItemType Directory -Path $baseLog -Force | Out-Null }

# Conjunto de firmas de lineas ya guardadas (para no duplicar).
# Se reconstruye del log completo del dia al iniciar.
$vistas = New-Object 'System.Collections.Generic.HashSet[string]'
$fechaHoy = Get-Date -Format "yyyy-MM-dd"
$fullHoy = Join-Path $baseLog "$fechaHoy-completo.log"
if (Test-Path $fullHoy) {
    foreach ($l in (Get-Content $fullHoy)) { [void]$vistas.Add($l) }
}

$ProgressPreference = 'SilentlyContinue'

# Solo se consideran eventos de jugadores reales de la consola del servidor.
# Cada patron exige contexto que descarta mensajes de mods/recetas.
$muertes = 'was slain by|was killed by|was shot by|was blown up|was doomed to fall|was pricked to death|was impaled|was squished|was fireballed|was stung to death|was frozen to death|was burnt to a crisp|was killed|hit the ground too hard|fell from a high place|fell off|tried to swim in lava|drowned|starved to death|suffocated in a wall|withered away|went off with a bang|walked into a cactus|walked into fire|discovered the floor was lava|experienced kinetic energy|froze to death|was burned to death'

function TagDe($l) {
    if ($l -match 'logged in with entity id .* at \(')              { return 'CONEXION' }
    if ($l -match ': [A-Za-z0-9_]+ joined the game$')               { return 'ENTRO' }
    if ($l -match ': [A-Za-z0-9_]+ left the game$')                 { return 'SALIO' }
    if ($l -match 'name=[A-Za-z0-9_]+.* lost connection')           { return 'DESCONECTADO' }
    if ($l -match $muertes)                                         { return 'MUERTE' }
    if ($l -match 'has made the advancement|has reached the goal|has completed the challenge') { return 'LOGRO' }
    if ($l -match ': Gave .* to [A-Za-z0-9_]+')                     { return 'ITEM-RECIBIDO' }
    if ($l -match 'Teleported [A-Za-z0-9_]+ to|teleported to the location') { return 'TELEPORT' }
    if ($l -match 'The player [A-Za-z0-9_]+ has successfully been saved')   { return 'TUMBA' }
    if ($l -match ': Applied effect .* to [A-Za-z0-9_]+|Removed .* effect.* from [A-Za-z0-9_]+') { return 'EFECTO' }
    return $null
}

function LimpiarMsg($l) {
    # quitar "[fecha] [thread/NIVEL] [fuente]: " y dejar el mensaje
    $m = $l -replace '^\[[0-9A-Za-z: \.]+\]\s*', ''
    $m = $m -replace '^\[[^\]]+\]\s*', ''
    $m = $m -replace '^\[[^\]]+\]:\s*', ''
    return $m.Trim()
}

function HoraDe($l) {
    if ($l -match '^\[([0-9]{2}[A-Za-z]{3}[0-9]{4} [0-9:\.]+)\]') { return $Matches[1] }
    return (Get-Date -Format 'HH:mm:ss')
}

function Procesar {
    $token = Get-ExarotonToken  -RutaToken  $rutaToken
    $cfg   = Get-ExarotonConfig -RutaConfig $rutaConfig
    $sid   = $cfg.serverId
    $h     = @{ Authorization = "Bearer $token" }

    try { $log = Invoke-RestMethod -Method Get -Uri "https://api.exaroton.com/v1/servers/$sid/logs/" -Headers $h }
    catch { return }

    $lineas = ($log.data.content -split "`r?`n") | Where-Object { $_ -ne "" }
    if (-not $lineas) { return }

    $fecha = Get-Date -Format "yyyy-MM-dd"
    $fullLog = Join-Path $baseLog "$fecha-completo.log"
    $evtLog  = Join-Path $baseLog "$fecha-eventos.log"

    $nuevasFull = New-Object System.Collections.Generic.List[string]
    $nuevasEvt  = New-Object System.Collections.Generic.List[string]

    foreach ($l in $lineas) {
        if ($vistas.Contains($l)) { continue }   # ya guardada: saltar
        [void]$vistas.Add($l)
        $nuevasFull.Add($l)
        $tag = TagDe $l
        if ($tag) {
            $nuevasEvt.Add(("[{0}] {1,-14} {2}" -f (HoraDe $l), $tag, (LimpiarMsg $l)))
        }
    }

    if ($nuevasFull.Count -gt 0) { Add-Content -Path $fullLog -Value $nuevasFull }
    if ($nuevasEvt.Count  -gt 0) { Add-Content -Path $evtLog  -Value $nuevasEvt }
}

Write-Host "Monitor de jugadores iniciado. Logs en: $baseLog"
Write-Host "Intervalo: $IntervaloSeg s. Ctrl+C para detener."
while ($true) {
    try { Procesar } catch {}
    Start-Sleep -Seconds $IntervaloSeg
}
