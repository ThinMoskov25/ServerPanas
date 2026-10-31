# ============================================================
#  Genera el archivo servers.dat de Minecraft
#
#  Para que el servidor aparezca YA en la lista de Multijugador
#  de tus amigos, sin que tengan que escribir la direccion.
#
#  servers.dat es un archivo NBT SIN comprimir. Se escribe a mano
#  porque no hay forma de generarlo desde el juego.
#
#  Estructura:
#    TAG_Compound ""            (raiz)
#      TAG_List "servers"       (lista de compounds)
#        TAG_Compound
#          TAG_String "ip"
#          TAG_String "name"
#        TAG_End
#      TAG_End
# ============================================================

param(
    [string]$Direccion = "ShadowBlack77-rEez.exaroton.me:23574",
    [string]$Nombre    = "SERVER PANAS",
    [string]$Salida    = ""
)

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
if (-not $Salida) { $Salida = Join-Path $base "cliente\servers.dat" }

$bytes = New-Object System.Collections.Generic.List[byte]

function Add-Byte([byte]$b) { $bytes.Add($b) }
function Add-UShortBE([int]$v) {
    $bytes.Add([byte](($v -shr 8) -band 0xFF))
    $bytes.Add([byte]($v -band 0xFF))
}
function Add-IntBE([int]$v) {
    $bytes.Add([byte](($v -shr 24) -band 0xFF))
    $bytes.Add([byte](($v -shr 16) -band 0xFF))
    $bytes.Add([byte](($v -shr 8)  -band 0xFF))
    $bytes.Add([byte]($v -band 0xFF))
}
function Add-NbtString([string]$s) {
    # cadena NBT: longitud (2 bytes) + UTF-8
    $u = [System.Text.Encoding]::UTF8.GetBytes($s)
    Add-UShortBE $u.Length
    $bytes.AddRange($u)
}
function Add-NamedString([string]$nombre, [string]$valor) {
    Add-Byte 0x08           # TAG_String
    Add-NbtString $nombre   # nombre de la etiqueta
    Add-NbtString $valor    # valor
}

# --- raiz: TAG_Compound con nombre vacio ---
Add-Byte 0x0A
Add-NbtString ""

# --- TAG_List "servers" ---
Add-Byte 0x09
Add-NbtString "servers"
Add-Byte 0x0A               # tipo de los elementos: TAG_Compound
Add-IntBE 1                 # cantidad de elementos

# --- elemento 1 (compound sin nombre, por estar en lista) ---
Add-NamedString "ip"   $Direccion
Add-NamedString "name" $Nombre
Add-Byte 0x00               # TAG_End del compound del servidor

# --- fin de la raiz ---
Add-Byte 0x00

New-Item -ItemType Directory -Force -Path (Split-Path $Salida -Parent) | Out-Null
[System.IO.File]::WriteAllBytes($Salida, $bytes.ToArray())

Write-Host "servers.dat generado:"
Write-Host "   archivo   : $Salida"
Write-Host "   tamano    : $($bytes.Count) bytes"
Write-Host "   servidor  : $Nombre"
Write-Host "   direccion : $Direccion"

# --- verificacion: releer y comprobar que las cadenas estan ahi ---
$leido = [System.IO.File]::ReadAllBytes($Salida)
$txt = [System.Text.Encoding]::UTF8.GetString($leido)
$okIp = $txt.Contains($Direccion)
$okNom = $txt.Contains($Nombre)
Write-Host ""
Write-Host "Verificacion:"
Write-Host "   contiene la direccion : $okIp"
Write-Host "   contiene el nombre    : $okNom"
if (-not ($okIp -and $okNom)) { Write-Host "   [FALLO] el archivo no quedo bien" -ForegroundColor Red }
else { Write-Host "   [OK]" -ForegroundColor Green }
