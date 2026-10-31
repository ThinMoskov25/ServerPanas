# ============================================================
#  Cobertura REAL de traduccion, comparando VALORES
#
#  Un mod puede tener todas las claves en su es_es.json pero con
#  el texto en ingles sin traducir. Comparar solo claves no lo
#  detecta: hay que comparar el valor es vs el valor en.
#
#  Cuenta como "sin traducir":
#    - clave que no existe en es_es
#    - clave cuyo valor es IDENTICO al ingles (y no es un simbolo,
#      numero o nombre propio corto)
#
#  Uso: .\_cobertura-real.ps1 [filtroJar]
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods = Join-Path $base "cliente\mods"
$rp   = Join-Path $base "cliente\resourcepacks\Espanol-ServerPanas\assets"
$filtro = if ($args.Count -ge 1) { $args[0] } else { "" }

function Leer($z,$r){
    $e=$z.Entries|Where-Object{$_.FullName -eq $r}|Select-Object -First 1
    if(-not $e){return $null}
    $sr=New-Object System.IO.StreamReader($e.Open(),[System.Text.Encoding]::UTF8)
    $t=$sr.ReadToEnd(); $sr.Close(); return $t
}
function Mapa($t){
    # OJO: no usar ConvertFrom-Json. Varios mods traen JSON con claves
    # duplicadas o BOM y lanza excepcion, devolviendo un mapa vacio
    # (eso daba un falso 0% de cobertura). Parseamos con regex.
    $h=@{}
    if ($t) {
        foreach ($m in [regex]::Matches($t, '"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')) {
            $h[$m.Groups[1].Value] = $m.Groups[2].Value
        }
    }
    return $h
}

$filas = @()
foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    if ($filtro -and $j.Name -notlike "*$filtro*") { continue }
    try {
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $mids = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$' } |
                ForEach-Object { if ($_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$') { $Matches[1] } } | Sort-Object -Unique
        foreach ($mid in $mids) {
            $en = Mapa (Leer $z "assets/$mid/lang/en_us.json")
            if ($en.Count -lt 15) { continue }
            $es = Mapa (Leer $z "assets/$mid/lang/es_es.json")
            # sumar lo que YO ya traduje
            $mio = Join-Path $rp "$mid\lang\es_es.json"
            if (Test-Path $mio) {
                foreach ($kv in (Mapa (Get-Content $mio -Raw -Encoding UTF8)).GetEnumerator()) { $es[$kv.Key]=$kv.Value }
            }

            $sin = 0
            foreach ($k in $en.Keys) {
                $vEn = $en[$k]
                if (-not $es.ContainsKey($k)) { $sin++; continue }
                $vEs = $es[$k]
                # identico al ingles = probablemente sin traducir,
                # salvo textos muy cortos o solo simbolos/numeros
                if ($vEs -eq $vEn -and $vEn.Length -gt 3 -and $vEn -match '[A-Za-z]{3}') { $sin++ }
            }
            $pct = [math]::Round((($en.Count-$sin)/$en.Count)*100,0)
            $filas += [PSCustomObject]@{ Pct=$pct; Sin=$sin; Total=$en.Count; Mod=$j.Name }
        }
        $z.Dispose()
    } catch {}
}

Write-Host "============================================================"
Write-Host " COBERTURA REAL DE ESPANOL (comparando valores)"
Write-Host "============================================================"
$filas | Sort-Object Pct | ForEach-Object { "  {0,3}%   sin traducir {1,5} de {2,5}   {3}" -f $_.Pct,$_.Sin,$_.Total,$_.Mod }
Write-Host ""
Write-Host ("TOTAL de textos sin traducir: {0}" -f (($filas | Measure-Object Sin -Sum).Sum))
Write-Host ("TOTAL de textos del pack    : {0}" -f (($filas | Measure-Object Total -Sum).Sum))

# ------------------------------------------------------------
#  Modo detalle: .\_cobertura-real.ps1 <jar> -detalle
#  Imprime las claves concretas que siguen en ingles.
# ------------------------------------------------------------
if ($args -contains '-detalle' -and $filtro) {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " CLAVES QUE SIGUEN EN INGLES"
    Write-Host "============================================================"
    foreach ($j in (Get-ChildItem "$mods\*.jar")) {
        if ($j.Name -notlike "*$filtro*") { continue }
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $mids = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$' } |
                ForEach-Object { if ($_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$') { $Matches[1] } } | Sort-Object -Unique
        foreach ($mid in $mids) {
            $en = Mapa (Leer $z "assets/$mid/lang/en_us.json")
            $es = Mapa (Leer $z "assets/$mid/lang/es_es.json")
            $mio = Join-Path $rp "$mid\lang\es_es.json"
            if (Test-Path $mio) { foreach ($kv in (Mapa (Get-Content $mio -Raw -Encoding UTF8)).GetEnumerator()) { $es[$kv.Key]=$kv.Value } }
            foreach ($k in ($en.Keys | Sort-Object)) {
                $vEn=$en[$k]
                $falta = $false
                if (-not $es.ContainsKey($k)) { $falta=$true }
                elseif ($es[$k] -eq $vEn -and $vEn.Length -gt 3 -and $vEn -match '[A-Za-z]{3}') { $falta=$true }
                if ($falta) { Write-Host ('   "{0}": "{1}",' -f $k, $vEn) }
            }
        }
        $z.Dispose()
    }
}
