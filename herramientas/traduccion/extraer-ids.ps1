# ============================================================
#  Extrae IDs REALES de items leyendo assets/<modid>/models/item/*.json
#  (el nombre del archivo de modelo ES el id del item)
#  Uso:  .\_extraer-ids.ps1 <filtroJar> [filtroId]
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$cli = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))) "cliente\mods"
$filtroJar = if ($args.Count -ge 1) { $args[0] } else { "" }
$filtroId  = if ($args.Count -ge 2) { $args[1] } else { "" }

foreach ($j in (Get-ChildItem "$cli\*.jar")) {
    if ($filtroJar -and $j.Name -notlike "*$filtroJar*") { continue }
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $ids = $z.Entries |
            Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/models/item/(.+)\.json$' } |
            ForEach-Object {
                if ($_.FullName -match '^assets/([a-z0-9_.-]+)/models/item/(.+)\.json$') {
                    "$($Matches[1]):$($Matches[2])"
                }
            } | Sort-Object -Unique
        if ($filtroId) { $ids = $ids | Where-Object { $_ -like "*$filtroId*" } }
        if ($ids) {
            Write-Host ""
            Write-Host "########## $($j.Name)  ($($ids.Count) items)" -ForegroundColor Cyan
            $ids | ForEach-Object { Write-Host "   $_" }
        }
        $z.Dispose()
    } catch {}
}
