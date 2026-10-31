# ============================================================
#  Detecta si hay un Forge 1.20.1 instalado que cumpla la
#  version MINIMA requerida por el pack.
#
#  El pack exige 47.4.0 o superior: varios mods (rightclickharvest,
#  irons_spellbooks, jamlib) lo piden y crashean con 47.3.x.
#  Validar esto en un .bat es un dolor porque compara texto, asi
#  que "47.3.1" le parece mas nuevo que "47.4.0". Aqui se compara
#  como version de verdad.
#
#  Codigos de salida (los lee el .bat):
#    0 = hay un Forge valido instalado (>= minimo)
#    1 = no hay ningun Forge 1.20.1
#    2 = hay Forge 1.20.1 pero TODOS por debajo del minimo
#
#  Imprime en stdout la carpeta de version elegida (si codigo 0)
#  o la version vieja encontrada (si codigo 2), para que el .bat
#  pueda mostrarla.
#
#  USO:
#    .\_detectar-forge.ps1
#    .\_detectar-forge.ps1 -MinForge 47.4.0 -MinecraftDir "C:\...\.minecraft"
# ============================================================

param(
    [string]$MinForge = "47.4.0",
    [string]$MinecraftDir = ""
)

$ErrorActionPreference = "Stop"

if (-not $MinecraftDir) { $MinecraftDir = Join-Path $env:APPDATA ".minecraft" }
$versiones = Join-Path $MinecraftDir "versions"

$min = [version]$MinForge

if (-not (Test-Path $versiones)) {
    # ni siquiera hay carpeta versions: no hay Forge
    exit 1
}

# Recoger todas las carpetas 1.20.1-forge-XX.Y.Z y parsear su version
$encontrados = @()
foreach ($d in (Get-ChildItem $versiones -Directory)) {
    if ($d.Name -match '^1\.20\.1-forge-(\d+\.\d+\.\d+)$') {
        $encontrados += [PSCustomObject]@{
            Nombre = $d.Name
            Ver    = [version]$Matches[1]
        }
    }
}

if ($encontrados.Count -eq 0) {
    # no hay ningun Forge 1.20.1
    exit 1
}

# El mas nuevo instalado
$masNuevo = $encontrados | Sort-Object Ver -Descending | Select-Object -First 1

# Se imprime "ESTADO|NOMBRE" en una sola linea, porque el .bat no
# puede leer de forma fiable el codigo de salida de un comando que
# corre dentro de un 'for /f' (el errorlevel que queda es el del
# propio for, no el del powershell). Asi el .bat parte la linea y
# saca el estado del texto, sin depender del errorlevel.
if ($masNuevo.Ver -ge $min) {
    Write-Output ("0|" + $masNuevo.Nombre)   # hay uno valido
    exit 0
} else {
    Write-Output ("2|" + $masNuevo.Nombre)   # hay Forge pero viejo
    exit 2
}
