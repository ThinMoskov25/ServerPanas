<#
    leer-inventario.ps1
    --------------------------------------------------------------
    Lee un archivo .dat de jugador (NBT gzip) con un parser NBT real
    y lista SOLO los items: inventario, armadura, cofre de Ender,
    ranuras de Curios (amuletos/anillos) y lo que haya dentro de
    mochilas u otros contenedores. Incluye encantamientos y nombre.

    Tambien genera, por cada item, el comando /give exacto para
    reponerlo con el mismo NBT (encantamientos, afijos, etc.).

    Uso:
      .\leer-inventario.ps1 -Archivo "ruta\uuid.dat" [-Jugador SrBalduR]
      .\leer-inventario.ps1 -Uuid <uuid>   (usa el snapshot mas reciente)

    El reporte se guarda en <archivo>.txt
#>

param(
    [string] $Uuid,
    [string] $Archivo,
    [string] $Jugador = "@p"
)

$ErrorActionPreference = "Stop"
$baseSnap = Join-Path $PSScriptRoot "snapshots"

if (-not $Archivo) {
    if (-not $Uuid) { Write-Host "Indica -Uuid o -Archivo"; return }
    $cand = Get-ChildItem $baseSnap -Recurse -Filter "$Uuid.dat" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $cand) { Write-Host "No hay snapshots para $Uuid"; return }
    $Archivo = $cand.FullName
}
$Archivo = (Resolve-Path $Archivo).Path

# ---------- Descomprimir ----------
$raw = [IO.File]::ReadAllBytes($Archivo)
$ms  = New-Object IO.MemoryStream(,$raw)
$gz  = New-Object IO.Compression.GzipStream($ms, [IO.Compression.CompressionMode]::Decompress)
$o   = New-Object IO.MemoryStream
$gz.CopyTo($o); $gz.Close()
$script:d = $o.ToArray()
$script:p = 0

# ---------- Lector NBT (big-endian) ----------
function RB { $v = $script:d[$script:p]; $script:p++; return [int]$v }
function RN([int]$n) {
    $a = New-Object byte[] $n
    [Array]::Copy($script:d, $script:p, $a, 0, $n)
    $script:p += $n
    [Array]::Reverse($a)
    return ,$a
}
function RUShort { return [BitConverter]::ToUInt16((RN 2), 0) }
function RStr {
    $len = RUShort
    $s = [Text.Encoding]::UTF8.GetString($script:d, $script:p, $len)
    $script:p += $len
    return $s
}
function RPayload([int]$t) {
    switch ($t) {
        1  { $x = RB; if ($x -gt 127) { $x -= 256 }; return [sbyte]$x }
        2  { return [BitConverter]::ToInt16((RN 2), 0) }
        3  { return [BitConverter]::ToInt32((RN 4), 0) }
        4  { return [BitConverter]::ToInt64((RN 8), 0) }
        5  { return [BitConverter]::ToSingle((RN 4), 0) }
        6  { return [BitConverter]::ToDouble((RN 8), 0) }
        7  { $n = [BitConverter]::ToInt32((RN 4), 0); $script:p += $n; return ,([byte[]]@()) }
        8  { return RStr }
        9  {
            $et = RB
            $n = [BitConverter]::ToInt32((RN 4), 0)
            $l = New-Object System.Collections.Generic.List[object]
            for ($i = 0; $i -lt $n; $i++) { $l.Add((RPayload $et)) }
            return ,$l
        }
        10 {
            $c = New-Object 'System.Collections.Generic.Dictionary[string,object]'
            while ($true) {
                $tt = RB
                if ($tt -eq 0) { break }
                $name = RStr
                $c[$name] = (RPayload $tt)
            }
            return ,$c
        }
        11 {
            $n = [BitConverter]::ToInt32((RN 4), 0)
            $a = New-Object int[] $n
            for ($i = 0; $i -lt $n; $i++) { $a[$i] = [BitConverter]::ToInt32((RN 4), 0) }
            return ,$a
        }
        12 {
            $n = [BitConverter]::ToInt32((RN 4), 0)
            $a = New-Object long[] $n
            for ($i = 0; $i -lt $n; $i++) { $a[$i] = [BitConverter]::ToInt64((RN 8), 0) }
            return ,$a
        }
        default { throw "Tipo NBT desconocido $t en posicion $($script:p)" }
    }
}

$rootType = RB
[void](RStr)
$root = RPayload $rootType

# ---------- Serializador SNBT (para /give) ----------
$inv = [Globalization.CultureInfo]::InvariantCulture
function Q([string]$s) { return '"' + ($s -replace '\\','\\' -replace '"','\"') + '"' }
function ToSnbt($v) {
    if ($null -eq $v) { return '""' }
    if ($v -is [string]) { return (Q $v) }
    if ($v -is [sbyte])  { return "$($v)b" }
    if ($v -is [int16])  { return "$($v)s" }
    if ($v -is [int32])  { return "$v" }
    if ($v -is [int64])  { return "$($v)L" }
    if ($v -is [single]) { return $v.ToString("R", $inv) + "f" }
    if ($v -is [double]) { return $v.ToString("R", $inv) + "d" }
    if ($v -is [int[]])  { return "[I;" + (($v | ForEach-Object { "$_" }) -join ",") + "]" }
    if ($v -is [long[]]) { return "[L;" + (($v | ForEach-Object { "$($_)L" }) -join ",") + "]" }
    if ($v -is [byte[]]) { return "[B;]" }
    if ($v -is [System.Collections.Generic.Dictionary[string,object]]) {
        $parts = foreach ($k in $v.Keys) { (Q $k) + ":" + (ToSnbt $v[$k]) }
        return "{" + ($parts -join ",") + "}"
    }
    if ($v -is [System.Collections.Generic.List[object]]) {
        return "[" + (($v | ForEach-Object { ToSnbt $_ }) -join ",") + "]"
    }
    return "$v"
}

# ---------- Recorrer y encontrar items ----------
$items = New-Object System.Collections.Generic.List[object]
function EsItem($n) {
    return ($n -is [System.Collections.Generic.Dictionary[string,object]]) -and
           $n.ContainsKey('id') -and ($n['id'] -is [string]) -and $n.ContainsKey('Count')
}
function Walk($n, [string]$ruta) {
    if ($n -is [System.Collections.Generic.Dictionary[string,object]]) {
        if (EsItem $n) { $items.Add([pscustomobject]@{ Ruta = $ruta; Nodo = $n }) }
        foreach ($k in @($n.Keys)) {
            if ($k -eq 'recipeBook') { continue }   # recetas desbloqueadas: no son items
            Walk $n[$k] "$ruta/$k"
        }
    } elseif ($n -is [System.Collections.Generic.List[object]]) {
        for ($i = 0; $i -lt $n.Count; $i++) { Walk $n[$i] "$ruta[$i]" }
    }
}
Walk $root ""

function Seccion([string]$r) {
    if ($r -match 'curios:inventory') { return 'AMULETOS / CURIOS' }
    if ($r -match '^/EnderItems')     { return 'COFRE DE ENDER' }
    if ($r -match '^/Inventory\[\d+\]$') { return 'INVENTARIO' }
    if ($r -match '^/Inventory')      { return 'DENTRO DE ITEMS DEL INVENTARIO (mochilas, etc.)' }
    return 'OTROS'
}

$lineas = New-Object System.Collections.Generic.List[string]
$lineas.Add("Archivo: $Archivo")
$lineas.Add("Items encontrados: $($items.Count)")
foreach ($grupo in ($items | Group-Object { Seccion $_.Ruta })) {
    $lineas.Add("")
    $lineas.Add("===== $($grupo.Name) =====")
    foreach ($it in $grupo.Group) {
        $n = $it.Nodo
        $desc = "{0} x{1}" -f $n['id'], $n['Count']
        if ($n.ContainsKey('tag')) {
            $tag = $n['tag']
            if ($tag.ContainsKey('display') -and $tag['display'].ContainsKey('Name')) { $desc += "  nombre=" + $tag['display']['Name'] }
            foreach ($ek in @('Enchantments','StoredEnchantments')) {
                if ($tag.ContainsKey($ek)) {
                    $en = ($tag[$ek] | ForEach-Object { "{0} {1}" -f $_['id'], $_['lvl'] }) -join ", "
                    $desc += "  encantamientos=[$en]"
                }
            }
            if ($tag.ContainsKey('affix_data')) { $desc += "  (tiene afijos de Apotheosis)" }
        }
        $lineas.Add("  " + $desc)
        $tagSnbt = if ($n.ContainsKey('tag')) { ToSnbt $n['tag'] } else { "" }
        $lineas.Add("     give $Jugador $($n['id'])$tagSnbt $($n['Count'])")
    }
}

$salida = "$Archivo.txt"
$lineas | Set-Content -Path $salida -Encoding UTF8
Write-Host "Items: $($items.Count). Reporte en: $salida"
