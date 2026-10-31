# ============================================================
#  Empaqueta lo que hay que REPARTIR a los amigos
#
#  La carpeta ServerPanas pesa 1 GB porque contiene tres copias
#  de los mods (cliente, servidor y el zip). Los amigos solo
#  necesitan UNA, segun el launcher que usen:
#
#   - Con CurseForge        -> SERVER-PANAS v1.0.0.zip
#   - Oficial / TLauncher  -> SERVER-PANAS-INSTALADOR.zip
#
#  Y la carpeta servidor NO se reparte: es solo para
#  el dueno del servidor, para subirla a exaroton.
# ============================================================

$ErrorActionPreference = "Stop"
$base   = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$salida = Join-Path $base "distribucion"
$trabajo= Join-Path $base "_tmp-amigos"
$zipTL  = Join-Path $salida "SERVER-PANAS-INSTALADOR.zip"
$zipCF  = Join-Path $salida "SERVER-PANAS v1.0.0.zip"

if (Test-Path $trabajo) { Remove-Item $trabajo -Recurse -Force }
New-Item -ItemType Directory -Force -Path $salida  | Out-Null
New-Item -ItemType Directory -Force -Path $trabajo | Out-Null

Write-Host "Preparando el paquete del instalador manual..."

# --- cliente completo ---
Copy-Item (Join-Path $base "cliente") -Destination $trabajo -Recurse -Force
# --- instalador y documentos ---
# Los dos .ps1 son obligatorios: el .bat los llama para validar
# la version de Forge y para dejar la RAM puesta en el launcher.
# Dentro del zip conservan su nombre historico (INSTALAR-CLIENTE.bat los
# llama asi), aunque en el proyecto vivan en herramientas\pack y docs.
$paraZip = [ordered]@{
    "INSTALAR-CLIENTE.bat"            = "herramientas\pack\INSTALAR-CLIENTE.bat"
    "_configurar-perfil-launcher.ps1" = "herramientas\pack\configurar-perfil-launcher.ps1"
    "_detectar-forge.ps1"             = "herramientas\pack\detectar-forge.ps1"
    "_instalar-forge.ps1"             = "herramientas\pack\instalar-forge.ps1"
    "ENLACES-Y-REQUISITOS.md"         = "docs\ENLACES-Y-REQUISITOS.md"
}
foreach ($f in $paraZip.Keys) {
    $p = Join-Path $base $paraZip[$f]
    if (Test-Path $p) { Copy-Item $p -Destination (Join-Path $trabajo $f) -Force }
    else { Write-Host "  [AVISO] falta $($paraZip[$f]), el instalador quedara incompleto" -ForegroundColor Yellow }
}

# --- leeme simple para el amigo ---
$leeme = @"
LEEME - SERVER PANAS
====================

Servidor: ShadowBlack77-rEez.exaroton.me:23574
Minecraft 1.20.1 con Forge

OJO: la direccion lleva ":23574" al final. Es obligatorio.
Si escribes solo el dominio, NO conecta.

NO NECESITAS CURSEFORGE. Sirve con el launcher oficial de
Minecraft, con TLauncher o con Prism Launcher.

QUE HACER (1 paso)
------------------

Ejecuta INSTALAR-CLIENTE.bat  y sigue lo que te diga.

Hace TODO solo, sin ventanas de instalacion ni pasos manuales:
instala Forge 1.20.1 (descargando un Java temporal si no tienes
ninguno), copia los 110 mods, las 74 misiones en espanol, la
traduccion, anade el servidor a tu lista de Multijugador, y crea
un perfil en el launcher con los 8 GB de RAM ya puestos.

Lo unico que te pregunta es DONDE instalar. Nada mas.

Te preguntara DONDE instalar:

  [1] Carpeta separada (recomendado)
      Queda en .minecraft\SERVER-PANAS y no toca nada de lo que
      ya tengas. Tus otros mods y mundos siguen intactos.

  [2] Carpeta base de Minecraft
      Se instala encima de lo que hay, haciendo copia de
      seguridad de todo antes. Elige esta solo si quieres que el
      pack quede en la instalacion principal.

LA RAM
------
El instalador la pone en 8 GB solo, en el launcher oficial.
Si usas TLauncher tienes que ponerla a mano:
   Ajustes > Configuracion de Java > Memoria asignada > 8 GB
Con 4 GB el juego CRASHEA al arrancar. Es el fallo mas comun.

LISTO
-----
Abre el launcher, elige el perfil "SERVER PANAS", dale a jugar y
entra a Multijugador. El servidor ya deberia estar en la lista.
El primer arranque tarda 2-5 minutos, es normal con 110 mods.

Al aparecer en el mundo llevaras un LIBRO DE MISIONES en la mano.
Abrelo con clic derecho: tiene 10 capitulos en espanol.

SI ALGO FALLA
-------------
- Crashea al arrancar  -> te falto RAM, sube a 8 GB
- "Mod rejected"       -> vuelve a ejecutar el .bat sin cambiar nada
- No conecta           -> revisa que pusiste el puerto :23574.
                          Si esta bien, el servidor esta apagado:
                          pideselo al dueno
- Sale en ingles       -> Opciones > Paquetes de recursos y activa
                          "Traduccion al Espanol"

Mas detalles y enlaces de descarga: ENLACES-Y-REQUISITOS.md
"@
$leeme | Set-Content (Join-Path $trabajo "LEEME.txt") -Encoding UTF8

# --- comprimir con rutas correctas (barras "/") ---
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $zipTL) { Remove-Item $zipTL -Force }

$fs  = [System.IO.File]::Open($zipTL, [System.IO.FileMode]::Create)
$zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)
$raiz = (Resolve-Path $trabajo).Path.TrimEnd('\')
$n = 0
foreach ($f in (Get-ChildItem $trabajo -Recurse -File)) {
    $rel = $f.FullName.Substring($raiz.Length + 1).Replace('\','/')
    $e = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $e.Open(); $src = [System.IO.File]::OpenRead($f.FullName)
    $src.CopyTo($es); $src.Close(); $es.Close()
    $n++
}
$zip.Dispose(); $fs.Close()
Remove-Item $trabajo -Recurse -Force

# --- el zip de CurseForge lo genera crear-modpack-curseforge.ps1 en la misma carpeta ---
if (-not (Test-Path $zipCF)) { Write-Host "  [AVISO] falta el zip de CurseForge: ejecuta crear-modpack-curseforge.ps1" -ForegroundColor Yellow }

Write-Host ""
Write-Host "============================================================"
Write-Host " PAQUETES LISTOS PARA REPARTIR"
Write-Host "============================================================"
Write-Host " Carpeta: $salida"
Write-Host ""
foreach ($f in (Get-ChildItem $salida -File)) {
    "   {0,-34} {1,7} MB" -f $f.Name, [math]::Round($f.Length/1MB,1)
}
Write-Host ""
Write-Host " A QUIEN LE PASAS CADA UNO:"
Write-Host "   SERVER-PANAS v1.0.0.zip        -> amigos con CurseForge"
Write-Host "                                     (Minecraft original)"
Write-Host "   SERVER-PANAS-INSTALADOR.zip    -> launcher OFICIAL, TLauncher"
Write-Host "                                     o Prism. Sin CurseForge."
Write-Host ""
Write-Host " Archivos en el paquete del instalador: $n"
