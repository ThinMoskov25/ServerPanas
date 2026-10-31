# ============================================================
#  Cobertura de espanol UTIL (solo lo que el jugador vera)
#
#  Por que hace falta: muchos mods traen claves de compatibilidad
#  para OTROS mods que no tenemos instalados. Ejemplo real:
#  Enchantment Descriptions trae 183 claves, pero 150 describen
#  encantamientos de mods ausentes (Shields+, Improved EXP...).
#  Esas nunca aparecen en pantalla, asi que contarlas distorsiona
#  la cifra y hace perder el tiempo traduciendolas.
#
#  Este medidor descarta las claves cuyo namespace no corresponde
#  a ningun mod instalado.
#
#  Uso:
#    .\_cobertura-util.ps1              -> ranking
#    .\_cobertura-util.ps1 <jar> -detalle -> claves pendientes
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base     = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods     = Join-Path $base "cliente\mods"
$rpAssets = Join-Path $base "cliente\resourcepacks\Espanol-ServerPanas\assets"
$filtro   = if ($args.Count -ge 1 -and $args[0] -ne '-detalle') { $args[0] } else { "" }
$detalle  = $args -contains '-detalle'

function LeerZip($z,$r){
    $e=$z.Entries|Where-Object{$_.FullName -eq $r}|Select-Object -First 1
    if(-not $e){return $null}
    $sr=New-Object System.IO.StreamReader($e.Open(),[System.Text.Encoding]::UTF8)
    $t=$sr.ReadToEnd(); $sr.Close(); return $t
}
function Pares($t){
    $h=@{}
    if ($t) { foreach ($m in [regex]::Matches($t, '"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')) { $h[$m.Groups[1].Value]=$m.Groups[2].Value } }
    return $h
}

# ---- namespaces instalados ----
$instalados = New-Object System.Collections.Generic.HashSet[string]
[void]$instalados.Add('minecraft')
[void]$instalados.Add('forge')
foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    try {
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        foreach ($e in $z.Entries) {
            if ($e.FullName -match '^assets/([a-z0-9_.-]+)/') { [void]$instalados.Add($Matches[1]) }
            if ($e.FullName -match '^data/([a-z0-9_.-]+)/')   { [void]$instalados.Add($Matches[1]) }
        }
        $z.Dispose()
    } catch {}
}

# ---- una clave es "util" si su namespace esta instalado ----
function EsUtil([string]$k){
    # patrones tipo prefijo.<namespace>.resto
    # OJO: el namespace NO puede contener puntos. Con [a-z0-9_.-]+ el regex
    # era codicioso y capturaba "improved_exp.absorption" como namespace,
    # fallaba la comprobacion y colaba claves de mods ausentes.
    if ($k -match '^(item|block|entity|biome|effect|enchantment|advancements|fluid|attribute|potion|painting|sound|subtitles|death|stat|container|gui|key|itemGroup|curios|structure|trim_material|trim_pattern|upgrade|tooltips|jei|config|text|message|commands|misc)\.([a-z0-9_]+)\.') {
        $ns = $Matches[2]
        if ($ns.Length -gt 2) { return $instalados.Contains($ns) }
    }
    # claves internas o comentarios
    if ($k -match '^__') { return $false }
    return $true
}

$filas = @()
foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    if ($filtro -and $j.Name -notlike "*$filtro*") { continue }
    try {
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $mids = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$' } |
                ForEach-Object { if ($_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$') { $Matches[1] } } | Sort-Object -Unique
        foreach ($mid in $mids) {
            $en = Pares (LeerZip $z "assets/$mid/lang/en_us.json")
            if ($en.Count -lt 5) { continue }
            $es = Pares (LeerZip $z "assets/$mid/lang/es_es.json")
            $mio = Join-Path $rpAssets "$mid\lang\es_es.json"
            if (Test-Path $mio) { foreach ($kv in (Pares (Get-Content $mio -Raw -Encoding UTF8)).GetEnumerator()) { $es[$kv.Key]=$kv.Value } }

            $utiles = 0; $sin = 0; $pendientes = @()
            foreach ($k in $en.Keys) {
                if (-not (EsUtil $k)) { continue }
                $utiles++
                $vEn = $en[$k]
                $falta = $false
                if (-not $es.ContainsKey($k)) { $falta = $true }
                elseif ($es[$k] -eq $vEn -and $vEn.Length -gt 3 -and $vEn -match '[A-Za-z]{3}') { $falta = $true }
                if ($falta) { $sin++; $pendientes += [PSCustomObject]@{ K=$k; V=$vEn } }
            }
            if ($utiles -eq 0) { continue }
            $filas += [PSCustomObject]@{
                Pct=[math]::Round((($utiles-$sin)/$utiles)*100,0); Sin=$sin; Utiles=$utiles
                Mod=$j.Name; ModId=$mid; Pendientes=$pendientes
            }
        }
        $z.Dispose()
    } catch {}
}

if ($detalle -and $filtro) {
    foreach ($f in $filas) {
        Write-Host ""
        Write-Host "##### $($f.Mod) [$($f.ModId)]  -> $($f.Sin) pendientes de $($f.Utiles) utiles" -ForegroundColor Cyan
        foreach ($p in ($f.Pendientes | Sort-Object K)) { Write-Host ('	"{0}": "{1}",' -f $p.K, $p.V) }
    }
    return
}

Write-Host "============================================================"
Write-Host " COBERTURA UTIL DE ESPANOL (solo mods instalados)"
Write-Host "============================================================"
$filas | Where-Object { $_.Sin -gt 0 } | Sort-Object Sin -Descending | ForEach-Object {
    "  {0,3}%   pendientes {1,5} de {2,5} utiles   {3}" -f $_.Pct,$_.Sin,$_.Utiles,$_.Mod
}
Write-Host ""
Write-Host ("TOTAL pendiente UTIL : {0}" -f (($filas | Measure-Object Sin -Sum).Sum))
Write-Host ("TOTAL textos utiles  : {0}" -f (($filas | Measure-Object Utiles -Sum).Sum))
$t=($filas | Measure-Object Utiles -Sum).Sum; $s=($filas | Measure-Object Sin -Sum).Sum
if ($t -gt 0) { Write-Host ("COBERTURA ACTUAL     : {0}%" -f [math]::Round((($t-$s)/$t)*100,1)) }
