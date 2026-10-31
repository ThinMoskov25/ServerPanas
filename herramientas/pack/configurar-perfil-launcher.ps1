# ============================================================
#  Crea o actualiza el perfil de SERVER PANAS en el launcher
#  OFICIAL de Minecraft, con la RAM ya puesta.
#
#  Por que hace falta: el launcher oficial crea el perfil de Forge
#  con 2 GB por defecto, y con este pack eso CRASHEA al arrancar.
#  Es el fallo numero uno, y nadie se acuerda de cambiarlo a mano.
#
#  Toca launcher_profiles.json, asi que:
#    - hace copia de seguridad antes
#    - valida el JSON antes de escribir
#    - si algo falla, deja el archivo original intacto
#
#  USO:
#    .\_configurar-perfil-launcher.ps1
#    .\_configurar-perfil-launcher.ps1 -RamGB 6
#    .\_configurar-perfil-launcher.ps1 -GameDir "C:\...\.minecraft\SERVER-PANAS"
# ============================================================

param(
    [int]$RamGB = 8,
    [string]$GameDir = "",
    [string]$Nombre = "SERVER PANAS",
    [string]$MinecraftDir = ""
)

$ErrorActionPreference = "Stop"

function OK($t)   { Write-Host "    [OK]    $t" -ForegroundColor Green }
function FAIL($t) { Write-Host "    [FALLO] $t" -ForegroundColor Red }
function INFO($t) { Write-Host "    $t" }

if (-not $MinecraftDir) { $MinecraftDir = Join-Path $env:APPDATA ".minecraft" }
$perfiles = Join-Path $MinecraftDir "launcher_profiles.json"

Write-Host "============================================================"
Write-Host " CONFIGURANDO EL PERFIL DEL LAUNCHER OFICIAL"
Write-Host "============================================================"

if (-not (Test-Path $perfiles)) {
    FAIL "No existe $perfiles"
    INFO "Abre el launcher oficial de Minecraft una vez y cierralo,"
    INFO "para que se cree el archivo. Luego vuelve a ejecutar esto."
    exit 1
}

# ---------- 1. Buscar la version de Forge 1.20.1 instalada ----------
Write-Host ""
Write-Host "--- 1) Buscando Forge 1.20.1" -ForegroundColor Cyan
$versiones = Join-Path $MinecraftDir "versions"
$forge = $null
if (Test-Path $versiones) {
    # Ordenar por la version REAL de Forge (47.4.0 > 47.3.1).
    # OJO: no se puede partir el nombre por puntos y mirar el ultimo
    # trozo, porque "47.3.1" ganaria a "47.4.0" (1 > 0).
    $forge = Get-ChildItem $versiones -Directory |
             Where-Object { $_.Name -match '^1\.20\.1-forge-(\d+\.\d+\.\d+)$' } |
             ForEach-Object {
                 [void]($_.Name -match '^1\.20\.1-forge-(\d+\.\d+\.\d+)$')
                 [PSCustomObject]@{ Nombre = $_.Name; Ver = [version]$Matches[1] }
             } |
             Sort-Object Ver -Descending |
             Select-Object -First 1 -ExpandProperty Nombre
}
if (-not $forge) {
    FAIL "No encuentro ninguna version 1.20.1-forge-47.x instalada."
    INFO "Instala Forge 1.20.1 primero (el .bat lo hace por ti)."
    INFO "Descarga directa del 47.4.0:"
    INFO "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-installer.jar"
    exit 2
}
OK "Encontrado: $forge"

# ---------- 2. Copia de seguridad ----------
Write-Host ""
Write-Host "--- 2) Copia de seguridad" -ForegroundColor Cyan
$sello  = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backup = "$perfiles.backup_$sello"
Copy-Item $perfiles $backup -Force
OK "Guardada en: $(Split-Path $backup -Leaf)"

# ---------- 3. Leer y validar el JSON actual ----------
Write-Host ""
Write-Host "--- 3) Leyendo perfiles" -ForegroundColor Cyan
try {
    $texto = Get-Content $perfiles -Raw
    $json  = $texto | ConvertFrom-Json
} catch {
    FAIL "launcher_profiles.json no es JSON valido. No toco nada."
    INFO "Detalle: $($_.Exception.Message)"
    exit 3
}
if (-not $json.profiles) {
    FAIL "El archivo no tiene la seccion 'profiles'. No toco nada."
    exit 3
}
$cuantos = @($json.profiles.PSObject.Properties).Count
OK "$cuantos perfiles existentes (no se tocan)"

# ---------- 4. Construir el perfil ----------
Write-Host ""
Write-Host "--- 4) Creando el perfil '$Nombre'" -ForegroundColor Cyan

# Argumentos de Java: RAM + G1GC, que es lo que recomienda la propia
# comunidad de modpacks grandes para evitar tirones de recoleccion.
$javaArgs = "-Xmx${RamGB}G -Xms2G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC " +
            "-XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 " +
            "-XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M"

$ahora = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")

# reutilizar el perfil si ya existe uno con el mismo nombre
$idExistente = $null
foreach ($p in $json.profiles.PSObject.Properties) {
    if ($p.Value.name -eq $Nombre) { $idExistente = $p.Name; break }
}

$perfil = [ordered]@{
    name          = $Nombre
    type          = "custom"
    created       = $ahora
    lastUsed      = $ahora
    lastVersionId = $forge
    javaArgs      = $javaArgs
    icon          = "Chest"
}
if ($GameDir) { $perfil["gameDir"] = $GameDir }

if ($idExistente) {
    # conservar la fecha de creacion original
    if ($json.profiles.$idExistente.created) { $perfil["created"] = $json.profiles.$idExistente.created }
    $json.profiles.$idExistente = [PSCustomObject]$perfil
    OK "Perfil actualizado (id: $idExistente)"
} else {
    $nuevoId = [guid]::NewGuid().ToString("N")
    $json.profiles | Add-Member -MemberType NoteProperty -Name $nuevoId -Value ([PSCustomObject]$perfil)
    OK "Perfil creado (id: $nuevoId)"
}

INFO "version  : $forge"
INFO "RAM      : ${RamGB} GB"
if ($GameDir) { INFO "carpeta  : $GameDir" } else { INFO "carpeta  : .minecraft (la base)" }

# ---------- 5. Escribir, validando antes ----------
Write-Host ""
Write-Host "--- 5) Guardando" -ForegroundColor Cyan
$salida = $json | ConvertTo-Json -Depth 20
try {
    $salida | ConvertFrom-Json | Out-Null
} catch {
    FAIL "El JSON generado no es valido. NO se escribio nada."
    INFO "Tu archivo original sigue intacto."
    exit 4
}
[System.IO.File]::WriteAllText($perfiles, $salida, (New-Object System.Text.UTF8Encoding $false))

# releer para confirmar
try {
    $check = Get-Content $perfiles -Raw | ConvertFrom-Json
    $encontrado = $false
    foreach ($p in $check.profiles.PSObject.Properties) {
        if ($p.Value.name -eq $Nombre -and $p.Value.lastVersionId -eq $forge) { $encontrado = $true }
    }
    if ($encontrado) {
        OK "Escrito y verificado."
    } else {
        FAIL "Se escribio pero no encuentro el perfil al releer."
        INFO "Restaura con: copy `"$backup`" `"$perfiles`""
        exit 5
    }
} catch {
    FAIL "El archivo quedo corrupto. RESTAURANDO la copia..."
    Copy-Item $backup $perfiles -Force
    INFO "Restaurado. No se aplico ningun cambio."
    exit 5
}

Write-Host ""
Write-Host "============================================================"
Write-Host " PERFIL LISTO" -ForegroundColor Green
Write-Host "============================================================"
Write-Host " Abre el launcher oficial de Minecraft."
Write-Host " En 'Instalaciones' veras el perfil: $Nombre"
Write-Host " Ya trae ${RamGB} GB de RAM configurados, no toques nada mas."
Write-Host ""
Write-Host " Si el launcher estaba abierto, cierralo y vuelve a abrirlo"
Write-Host " para que lea el perfil nuevo."
Write-Host ""
