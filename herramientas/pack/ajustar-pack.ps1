# ============================================================
#  AJUSTE del modpack "SERVER PANAS" - Forge 1.20.1
#  Añade: dragones, monturas, tradeo, QoL + dependencias
#  Quita: Corpse (reemplazado por Tombstone, para no duplicar)
# ============================================================

$ErrorActionPreference = "Stop"
$base    = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$cliente = Join-Path $base "cliente\mods"

$inst = "$env:USERPROFILE\curseforge\minecraft\Instances"
$mc   = "$env:APPDATA\.minecraft"

$fuentes = @(
    "$inst\Epic fight and more\mods",
    "$inst\All the Mods 9 - ATM9\mods",
    "$inst\Cursed Walking - A Modern Zombie Apocalypse\mods",
    "$inst\Zeefski Jujutsu Kaisen Craft\mods",
    "$mc\back\011026\1.20.1",
    "$mc\back\1.20.1"
)

# ---- Mods a AÑADIR (prefijo de archivo) ----
$agregar = @(
    # Dragones y monturas
    "iceandfire-2",                  # Ice and Fire: dragones MONTABLES
    "ChocoCraft",                    # Chocobos: monturas terrestres
    "moredragoneggs",                # mas huevos de dragon

    # Sistema de tradeo
    "simpleshops-",                  # tiendas de jugadores (server)
    "TradingPost-",                  # puesto comercial
    "easy-villagers-forge",          # aldeanos en bloque, tradeo facil
    "morevillagers-forge",           # mas tipos de aldeanos

    # No perder cosas al morir (mejor que Corpse)
    "tombstone-1.20.1-9",            # tumbas protegidas por dueño

    # Calidad de vida grande
    "Quark-4",                       # cientos de mejoras vanilla-friendly
    "supplementaries-1.20",          # utilidades y decoracion
    "xaeros_waystones_compatibility",# waystones visibles en el mapa

    # ---- DEPENDENCIAS obligatorias de lo anterior ----
    "Zeta-1",                        # requerida por Quark
    "PuzzlesLib-v8"                  # requerida por Trading Post
)

# ---- Mods a QUITAR (para no duplicar funcion) ----
$quitar = @(
    "corpse-forge"                   # Tombstone ya cubre esto
)

Write-Host "============================================================"
Write-Host " AJUSTANDO EL PACK"
Write-Host "============================================================"

# --- Quitar ---
foreach ($q in $quitar) {
    $f = Get-ChildItem "$cliente\$q*.jar" -ErrorAction SilentlyContinue
    if ($f) { $f | Remove-Item -Force; $f | ForEach-Object { Write-Host "  QUITADO  $($_.Name)" } }
}

# --- Añadir ---
$ok = @(); $falta = @()
foreach ($a in $agregar) {
    # si ya existe, no duplicar
    if (Get-ChildItem "$cliente\$a*.jar" -ErrorAction SilentlyContinue) {
        Write-Host "  YA ESTABA $a"
        continue
    }
    $hit = $null
    foreach ($src in $fuentes) {
        $m = Get-ChildItem "$src\$a*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($m) { $hit = $m; break }
    }
    if ($hit) {
        Copy-Item $hit.FullName -Destination $cliente -Force
        $ok += $hit.Name
        Write-Host "  AÑADIDO  $($hit.Name)"
    } else {
        $falta += $a
        Write-Host "  FALTA    $a"
    }
}

Write-Host ""
Write-Host "Añadidos: $($ok.Count)   |   No encontrados: $($falta.Count)"
