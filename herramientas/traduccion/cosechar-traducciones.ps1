# ============================================================
#  COSECHA traducciones al espanol desde TODOS tus modpacks
#
#  Idea: el mismo mod, en otra version (o en otro pack), suele
#  traer MAS traducciones al espanol, porque la comunidad las va
#  completando con el tiempo. En vez de traducir a mano, primero
#  recogemos lo que ya existe en tu disco.
#
#  Proceso:
#   1. Para cada mod de MI pack, saca sus claves en_us (las que
#      hay que cubrir) y las que ya estan en espanol.
#   2. Rastrea TODOS los jars de todas tus instancias y respaldos
#      buscando assets/<modid>/lang/es_es.json.
#   3. Se queda con las traducciones de claves que me faltan.
#      Ignora valores identicos al ingles (no son traduccion).
#   4. Escribe/actualiza el es_es.json de nuestro resourcepack.
#
#  Uso:
#    .\_cosechar-traducciones.ps1            -> informe
#    .\_cosechar-traducciones.ps1 -aplicar   -> escribe
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base     = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$misMods  = Join-Path $base "cliente\mods"
$rpAssets = Join-Path $base "cliente\resourcepacks\Espanol-ServerPanas\assets"
$aplicar  = $args -contains '-aplicar'

function LeerZip($z,$ruta){
    $e=$z.Entries|Where-Object{$_.FullName -eq $ruta}|Select-Object -First 1
    if(-not $e){return $null}
    $sr=New-Object System.IO.StreamReader($e.Open(),[System.Text.Encoding]::UTF8)
    $t=$sr.ReadToEnd(); $sr.Close(); return $t
}
function Pares($t){
    # regex, no ConvertFrom-Json: hay mods con JSON invalido
    $h=@{}
    if ($t) { foreach ($m in [regex]::Matches($t, '"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')) { $h[$m.Groups[1].Value]=$m.Groups[2].Value } }
    return $h
}
function EsTraduccion($vEs,$vEn){
    if ([string]::IsNullOrWhiteSpace($vEs)) { return $false }
    if ($vEs -eq $vEn) { return $false }          # copia del ingles
    return $true
}

# ---------- 1. Que necesito ----------
Write-Host "Analizando mi pack..."
$necesito = @{}   # modid -> @{ en = @{k=v}; ya = @{k=v} }

foreach ($j in (Get-ChildItem "$misMods\*.jar")) {
    try {
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $mids = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$' } |
                ForEach-Object { if ($_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$') { $Matches[1] } } | Sort-Object -Unique
        foreach ($mid in $mids) {
            $en = Pares (LeerZip $z "assets/$mid/lang/en_us.json")
            if ($en.Count -eq 0) { continue }
            $ya = Pares (LeerZip $z "assets/$mid/lang/es_es.json")
            # quitar las que son copia del ingles
            $yaReal = @{}
            foreach ($k in $ya.Keys) { if (EsTraduccion $ya[$k] $en[$k]) { $yaReal[$k]=$ya[$k] } }
            # sumar lo que ya traduje yo
            $mio = Join-Path $rpAssets "$mid\lang\es_es.json"
            if (Test-Path $mio) {
                foreach ($kv in (Pares (Get-Content $mio -Raw -Encoding UTF8)).GetEnumerator()) { $yaReal[$kv.Key]=$kv.Value }
            }
            if (-not $necesito.ContainsKey($mid)) { $necesito[$mid] = @{ en=$en; ya=$yaReal } }
        }
        $z.Dispose()
    } catch {}
}
$totalFaltan = 0
foreach ($mid in $necesito.Keys) { $totalFaltan += ($necesito[$mid].en.Count - $necesito[$mid].ya.Count) }
Write-Host "  mods con textos : $($necesito.Count)"
Write-Host "  textos a cubrir : $totalFaltan"
Write-Host ""

# ---------- 2. Rastrear todas las fuentes ----------
$raices = @(
    "$env:USERPROFILE\curseforge\minecraft\Instances",
    "$env:APPDATA\.minecraft\mods",
    "$env:APPDATA\.minecraft\back"
)
Write-Host "Rastreando jars en tus packs (puede tardar)..."
$jars = @()
foreach ($r in $raices) {
    if (Test-Path $r) { $jars += Get-ChildItem $r -Recurse -Filter "*.jar" -File -EA SilentlyContinue }
}
Write-Host "  jars encontrados: $($jars.Count)"
Write-Host ""

# ---------- 3. Cosechar ----------
Write-Host "Cosechando traducciones..."
$cosecha = @{}   # modid -> @{ k=v }
$fuentesUsadas = @{}
$i = 0
foreach ($j in $jars) {
    $i++
    if ($i % 300 -eq 0) { Write-Host "  ... $i / $($jars.Count)" }
    try {
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $cands = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/es_es\.json$' }
        foreach ($e in $cands) {
            if ($e.FullName -notmatch '^assets/([a-z0-9_.-]+)/lang/es_es\.json$') { continue }
            $mid = $Matches[1]
            if (-not $necesito.ContainsKey($mid)) { continue }
            $en  = $necesito[$mid].en
            $ya  = $necesito[$mid].ya
            $src = Pares (LeerZip $z $e.FullName)
            if (-not $cosecha.ContainsKey($mid)) { $cosecha[$mid] = @{} }
            $nuevas = 0
            foreach ($k in $src.Keys) {
                if (-not $en.ContainsKey($k)) { continue }        # clave que no existe en MI version
                if ($ya.ContainsKey($k)) { continue }             # ya la tengo
                if ($cosecha[$mid].ContainsKey($k)) { continue }  # ya cosechada
                if (-not (EsTraduccion $src[$k] $en[$k])) { continue }
                $cosecha[$mid][$k] = $src[$k]
                $nuevas++
            }
            if ($nuevas -gt 0) {
                $etq = "$($j.Name)"
                if (-not $fuentesUsadas.ContainsKey($mid)) { $fuentesUsadas[$mid]=@() }
                $fuentesUsadas[$mid] += "$etq (+$nuevas)"
            }
        }
        $z.Dispose()
    } catch {}
}

# ---------- 4. Informe ----------
Write-Host ""
Write-Host "============================================================"
Write-Host " COSECHA DE TRADUCCIONES"
Write-Host "============================================================"
$totalCosechado = 0
$filas = @()
foreach ($mid in $cosecha.Keys) {
    $n = $cosecha[$mid].Count
    if ($n -eq 0) { continue }
    $totalCosechado += $n
    $filas += [PSCustomObject]@{ Mod=$mid; Nuevas=$n; Faltaban=($necesito[$mid].en.Count - $necesito[$mid].ya.Count) }
}
$filas | Sort-Object Nuevas -Descending | ForEach-Object {
    "  +{0,5} de {1,5} que faltaban   {2}" -f $_.Nuevas, $_.Faltaban, $_.Mod
}
Write-Host ""
Write-Host "  TOTAL COSECHADO: $totalCosechado traducciones"
Write-Host ""

if (-not $aplicar) { Write-Host "  (modo informe. Ejecuta con -aplicar para escribirlas)"; return }

# ---------- 5. Escribir ----------
Write-Host "--- Escribiendo en el resourcepack ---"
foreach ($mid in $cosecha.Keys) {
    if ($cosecha[$mid].Count -eq 0) { continue }
    $dir = Join-Path $rpAssets "$mid\lang"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $dest = Join-Path $dir "es_es.json"

    # combinar: lo mio existente (prioridad) + lo cosechado
    $final = [ordered]@{}
    if (Test-Path $dest) {
        foreach ($kv in (Pares (Get-Content $dest -Raw -Encoding UTF8)).GetEnumerator()) { $final[$kv.Key]=$kv.Value }
    }
    foreach ($k in ($cosecha[$mid].Keys | Sort-Object)) {
        if (-not $final.Contains($k)) { $final[$k] = $cosecha[$mid][$k] }
    }

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("{")
    $ks = @($final.Keys)
    for ($n=0; $n -lt $ks.Count; $n++) {
        $coma = if ($n -lt $ks.Count-1) { "," } else { "" }
        [void]$sb.AppendLine(('	"{0}": "{1}"{2}' -f $ks[$n], $final[$ks[$n]], $coma))
    }
    [void]$sb.AppendLine("}")
    [System.IO.File]::WriteAllText($dest, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

    try {
        $null = Get-Content $dest -Raw -Encoding UTF8 | ConvertFrom-Json
        Write-Host ("  [OK] {0,-22} {1,5} textos en total" -f $mid, $ks.Count) -ForegroundColor Green
    } catch {
        Write-Host "  [FALLO] $mid genero JSON invalido" -ForegroundColor Red
    }
}
