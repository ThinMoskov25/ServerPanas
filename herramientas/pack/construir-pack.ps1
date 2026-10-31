# ============================================================
#  Constructor del modpack "SERVER PANAS" - Forge 1.20.1
#  Supervivencia + Aventura + Misiones (ligero)
#  Copia los mods seleccionados desde tus packs ya descargados.
# ============================================================

$ErrorActionPreference = "Stop"
$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$cliente = Join-Path $base "cliente\mods"

$inst = "$env:USERPROFILE\curseforge\minecraft\Instances"
$mc   = "$env:APPDATA\.minecraft"

# Carpetas fuente (orden de prioridad para encontrar cada mod)
$fuentes = @(
    "$mc\back\1.20.1",
    "$inst\Cursed Walking - A Modern Zombie Apocalypse\mods",
    "$inst\All the Mods 9 - ATM9\mods",
    "$inst\Epic fight and more\mods",
    "$mc\back\011026\1.20.1"
)

# ---- Lista curada de mods (por prefijo de nombre de archivo) ----
# Cada entrada se busca en las fuentes y se copia la 1a coincidencia.
$deseados = @(
    # --- Librerias base (obligatorias) ---
    "architectury-9", "cloth-config-11", "Bookshelf-Forge-1.20.1",
    "citadel-2", "geckolib-forge-1.20.1", "konkrete_forge",
    "Placebo-1.20.1", "puffish_attributes", "resourcefullib-forge-1.20.1",
    "framework-forge-1.20.1", "moonlight-1.20", "balm-forge-1.20.1",
    "collective-1.20.1", "CorgiLib", "SmartBrainLib", "YungsApi-1.20",
    "TerraBlender-forge-1.20.1", "structure_gel", "cupboard-1.20.1",
    "Searchables-forge", "lithostitched",

    # --- Rendimiento (ligero) ---
    "embeddium-0", "ferritecore-6", "modernfix-forge", "Clumps-forge",
    "entityculling-forge", "FastSuite", "ImmediatelyFast-Forge",
    "AI-Improvements", "canary-mc1.20.1",

    # --- Mundo / exploracion ---
    "Terralith_1.20", "YungsBetterDungeons", "YungsBetterMineshafts",
    "YungsBetterStrongholds", "YungsBetterDesertTemples",
    "YungsBetterJungleTemples", "YungsBetterOceanMonuments",
    "YungsBetterNetherFortresses", "YungsBetterWitchHuts",
    "DungeonsArise", "repurposed_structures", "Structory_1.20",
    "twilightforest-1.20.1", "Explorify",

    # --- Mobs / criaturas (sin zombis) ---
    "alexsmobs-1.22", "mowziesmobs", "guardvillagers", "goblintraders-forge",
    "naturalist-forge", "creeperoverhaul",

    # --- Misiones (lo que pediste) ---
    "ftb-library-forge", "ftb-quests-forge", "ftb-teams-forge",

    # --- Combate / RPG ligero ---
    "ApothicAttributes", "irons_spellbooks", "artifacts-forge",
    "reliquary-1.20.1",

    # --- Supervivencia / calidad de vida ---
    "FarmersDelight-1.20.1", "Croptopia", "SereneSeasons",
    "waystones-forge-1.20.1", "travelersbackpack-forge",
    "sophisticatedbackpacks", "sophisticatedcore",
    "comforts-forge", "corpse-forge", "carryon-forge",
    "rightclickharvest", "fallingtrees", "ToolBelt-1.20.1",
    "appleskin-forge", "EnchantmentDescriptions",

    # --- UI / utilidades (cliente) ---
    "jei-1.20.1-forge", "Jade-1.20.1-Forge", "justzoom_forge",
    "Xaeros_Minimap", "XaerosWorldMap", "Controlling-forge",
    "MouseTweaks-forge", "inventorysorter",

    # --- Multijugador / voz ---
    "voicechat-forge-1.20.1"
)

$copiados = @()
$noEncontrados = @()

foreach ($d in $deseados) {
    $encontrado = $null
    foreach ($src in $fuentes) {
        $match = Get-ChildItem "$src\$d*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($match) { $encontrado = $match; break }
    }
    if ($encontrado) {
        Copy-Item $encontrado.FullName -Destination $cliente -Force
        $copiados += $encontrado.Name
    } else {
        $noEncontrados += $d
    }
}

Write-Host "===================================================="
Write-Host ("MODS COPIADOS: {0}" -f $copiados.Count)
Write-Host "===================================================="
$copiados | Sort-Object | ForEach-Object { Write-Host "  OK  $_" }
Write-Host ""
Write-Host "===================================================="
Write-Host ("NO ENCONTRADOS: {0}" -f $noEncontrados.Count)
Write-Host "===================================================="
$noEncontrados | ForEach-Object { Write-Host "  --  $_" }
