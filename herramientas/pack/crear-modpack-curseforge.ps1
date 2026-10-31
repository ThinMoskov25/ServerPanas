# ============================================================
#  Genera un modpack IMPORTABLE en CurseForge (.zip oficial)
#  Estructura: manifest.json + overrides/mods/*.jar
#  Luego: CurseForge -> Mis modpacks -> Importar -> elegir el zip
# ============================================================

$ErrorActionPreference = "Stop"
$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$nombrePack   = "SERVER PANAS"
$versionPack  = "1.0.0"
$autor        = "David Meza"
$mcVersion    = "1.20.1"
$forgeVersion = "forge-47.4.0"   # Forge 47.4.0 para MC 1.20.1

$origenCli  = Join-Path $base "cliente"
$origenMods = Join-Path $origenCli "mods"
$trabajo    = Join-Path $base "_build-curseforge"
$ovRaiz     = Join-Path $trabajo "overrides"
$overrides  = Join-Path $ovRaiz "mods"
$zipFinal   = Join-Path (Join-Path $base "distribucion") ("{0} v{1}.zip" -f ($nombrePack -replace ' ','-'), $versionPack)

if (-not (Test-Path $origenMods)) {
    Write-Host "[ERROR] No existe: $origenMods" -ForegroundColor Red
    exit 1
}

# --- Limpiar y preparar ---
if (Test-Path $trabajo)  { Remove-Item $trabajo -Recurse -Force }
if (Test-Path $zipFinal) { Remove-Item $zipFinal -Force }
New-Item -ItemType Directory -Force -Path $overrides | Out-Null

# --- Copiar mods a overrides ---
$mods = Get-ChildItem "$origenMods\*.jar"
Copy-Item "$origenMods\*.jar" -Destination $overrides -Force
Write-Host "Mods copiados a overrides: $($mods.Count)"

# --- Copiar misiones, traducciones y opciones ---
if (Test-Path "$origenCli\config") {
    Copy-Item "$origenCli\config" -Destination $ovRaiz -Recurse -Force
    $nq = @(Get-ChildItem "$origenCli\config\ftbquests" -Recurse -File -ErrorAction SilentlyContinue).Count
    Write-Host "Config copiada (incluye $nq archivos de misiones)"
}
if (Test-Path "$origenCli\resourcepacks") {
    Copy-Item "$origenCli\resourcepacks" -Destination $ovRaiz -Recurse -Force
    Write-Host "Resourcepacks copiados (traduccion al espanol)"
}
if (Test-Path "$origenCli\options.txt") {
    Copy-Item "$origenCli\options.txt" -Destination $ovRaiz -Force
    Write-Host "options.txt copiado (idioma espanol por defecto)"
}
if (Test-Path "$origenCli\servers.dat") {
    Copy-Item "$origenCli\servers.dat" -Destination $ovRaiz -Force
    Write-Host "servers.dat copiado (el servidor aparece ya en Multijugador)"
}

# --- manifest.json ---
# files vacio = todos los mods viajan dentro de overrides (no se descargan)
$manifest = [ordered]@{
    minecraft = [ordered]@{
        version    = $mcVersion
        modLoaders = @(
            [ordered]@{ id = $forgeVersion; primary = $true }
        )
    }
    manifestType    = "minecraftModpack"
    manifestVersion = 1
    name            = $nombrePack
    version         = $versionPack
    author          = $autor
    files           = @()
    overrides       = "overrides"
}

$manifestPath = Join-Path $trabajo "manifest.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8
Write-Host "manifest.json generado"

# --- modlist.html (opcional, CurseForge lo muestra) ---
$html = New-Object System.Text.StringBuilder
[void]$html.AppendLine("<html><head><meta charset='utf-8'><title>$nombrePack</title></head><body>")
[void]$html.AppendLine("<h1>$nombrePack v$versionPack</h1>")
[void]$html.AppendLine("<p>Minecraft $mcVersion - $forgeVersion - $($mods.Count) mods</p><ul>")
foreach ($m in ($mods | Sort-Object Name)) {
    [void]$html.AppendLine("<li>$($m.Name)</li>")
}
[void]$html.AppendLine("</ul></body></html>")
$html.ToString() | Set-Content -Path (Join-Path $trabajo "modlist.html") -Encoding UTF8

# --- Comprimir ---
# OJO: CreateFromDirectory en Windows escribe rutas con "\", lo que rompe el
# estandar ZIP y CurseForge no reconoce la carpeta overrides.
# Por eso creamos cada entrada a mano forzando "/".
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$fs  = [System.IO.File]::Open($zipFinal, [System.IO.FileMode]::Create)
$zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)

$raiz = (Resolve-Path $trabajo).Path.TrimEnd('\')
foreach ($f in (Get-ChildItem $trabajo -Recurse -File)) {
    # ruta relativa con separador "/" (requisito del formato ZIP)
    $rel = $f.FullName.Substring($raiz.Length + 1).Replace('\','/')
    $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $src = [System.IO.File]::OpenRead($f.FullName)
    $src.CopyTo($es)
    $src.Close()
    $es.Close()
}
$zip.Dispose()
$fs.Close()

Remove-Item $trabajo -Recurse -Force

$mb = [math]::Round((Get-Item $zipFinal).Length / 1MB, 1)
Write-Host ""
Write-Host "============================================================"
Write-Host " MODPACK LISTO PARA IMPORTAR EN CURSEFORGE"
Write-Host "============================================================"
Write-Host " Archivo: $zipFinal"
Write-Host " Tamano : $mb MB"
Write-Host " Mods   : $($mods.Count)"
Write-Host ""
Write-Host " COMO IMPORTARLO:"
Write-Host "   1. Abre CurseForge"
Write-Host "   2. Minecraft -> Mis modpacks"
Write-Host "   3. Boton 'Importar'"
Write-Host "   4. Elige el archivo .zip de arriba"
Write-Host "   5. Espera a que instale Forge $forgeVersion y los mods"
Write-Host ""
