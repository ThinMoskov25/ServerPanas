# ============================================================
#  AJUSTE v2 del modpack "SERVER PANAS"
#  + Encantamientos : Apotheosis, Ensorcellation
#  + Jefes overworld: L_Ender's Cataclysm
#  + Amuletos       : Relics, Jewelry, Charm of Undying
#  - QUITA          : Easy Villagers (demasiado chetado)
# ============================================================

$ErrorActionPreference = "Stop"
$base    = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$cliente = Join-Path $base "cliente\mods"

$inst = "$env:USERPROFILE\curseforge\minecraft\Instances"
$mc   = "$env:APPDATA\.minecraft"

$fuentes = @(
    "$inst\All the Mods 9 - ATM9\mods",
    "$inst\Cursed Walking - A Modern Zombie Apocalypse\mods",
    "$inst\DeceasedCraft - Urban Zombie Apocalypse\mods",
    "$inst\Epic fight and more\mods",
    "$mc\back\011026\1.20.1",
    "$mc\back\1.20.1"
)

# ---- AÑADIR ----
$agregar = @(
    # Encantamientos
    "Apotheosis-1.20.1-7",       # mesa mejorable, biblioteca, gemas, afijos
    "ensorcellation-1.20.1",     # encantamientos nuevos estilo vanilla
    "cofh_core",                 # dependencia de Ensorcellation

    # Jefes del overworld
    "L_Enders_Cataclysm-3",      # 6 jefes con mazmorras propias

    # Amuletos y trinkets
    "relics-1.20.1",
    "Jewelry-1",
    "charmofundying-forge"
)

# ---- QUITAR ----
$quitar = @("easy-villagers-forge")

Write-Host "============================================================"
Write-Host " AJUSTE v2"
Write-Host "============================================================"

foreach ($q in $quitar) {
    $f = Get-ChildItem "$cliente\$q*.jar" -ErrorAction SilentlyContinue
    if ($f) { $f | ForEach-Object { Write-Host "  QUITADO  $($_.Name)" -ForegroundColor Yellow }; $f | Remove-Item -Force }
}

$ok = @(); $falta = @()
foreach ($a in $agregar) {
    if (Get-ChildItem "$cliente\$a*.jar" -ErrorAction SilentlyContinue) { Write-Host "  YA ESTABA $a"; continue }
    $hit = $null
    foreach ($src in $fuentes) {
        $m = Get-ChildItem "$src\$a*.jar" -ErrorAction SilentlyContinue |
             Sort-Object Name -Descending | Select-Object -First 1
        if ($m) { $hit = $m; break }
    }
    if ($hit) {
        Copy-Item $hit.FullName -Destination $cliente -Force
        $ok += $hit.Name
        Write-Host "  AÑADIDO  $($hit.Name)" -ForegroundColor Green
    } else {
        $falta += $a
        Write-Host "  FALTA    $a" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Añadidos: $($ok.Count)  |  No encontrados: $($falta.Count)"
Write-Host "Total mods cliente: $(@(Get-ChildItem "$cliente\*.jar").Count)"
