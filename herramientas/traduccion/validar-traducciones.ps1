# ============================================================
#  Valida el resourcepack de traduccion:
#   1. JSON valido
#   2. Que cada clave traducida EXISTA en el en_us del mod
#      (una clave mal escrita falla en silencio)
#   3. Que no falten claves del mod sin traducir
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods = Join-Path $base "cliente\mods"
$rp   = Join-Path $base "cliente\resourcepacks\Espanol-ServerPanas"

function Get-Claves([string]$texto) {
    $h = New-Object System.Collections.Generic.HashSet[string]
    foreach ($m in [regex]::Matches($texto, '"((?:[^"\\]|\\.)+)"\s*:')) {
        [void]$h.Add($m.Groups[1].Value)
    }
    return $h
}

# mapa modid -> claves de en_us dentro del jar
$enUs = @{}
foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        foreach ($e in $z.Entries) {
            if ($e.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$') {
                $mid = $Matches[1]
                $sr = New-Object System.IO.StreamReader($e.Open(), [System.Text.Encoding]::UTF8)
                $txt = $sr.ReadToEnd(); $sr.Close()
                if (-not $enUs.ContainsKey($mid)) { $enUs[$mid] = Get-Claves $txt }
                else { foreach ($k in (Get-Claves $txt)) { [void]$enUs[$mid].Add($k) } }
            }
        }
        $z.Dispose()
    } catch {}
}

Write-Host "============================================================"
Write-Host " VALIDACION DEL RESOURCEPACK DE TRADUCCION"
Write-Host "============================================================"

$errores = 0
$archivos = Get-ChildItem "$rp\assets\*\lang\es_es.json" -ErrorAction SilentlyContinue
if (-not $archivos) { Write-Host "No hay archivos es_es.json"; exit 1 }

# pack.mcmeta
$meta = Join-Path $rp "pack.mcmeta"
try {
    $pm = Get-Content $meta -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host "pack.mcmeta  OK  (pack_format = $($pm.pack.pack_format))" -ForegroundColor Green
} catch { Write-Host "pack.mcmeta  INVALIDO" -ForegroundColor Red; $errores++ }
Write-Host ""

foreach ($f in $archivos) {
    $mid = Split-Path (Split-Path $f.DirectoryName -Parent) -Leaf
    Write-Host "--- $mid"

    $raw = Get-Content $f.FullName -Raw -Encoding UTF8
    try { $null = $raw | ConvertFrom-Json }
    catch { Write-Host "    [FALLO] JSON invalido: $_" -ForegroundColor Red; $errores++; continue }

    $mias = Get-Claves $raw
    if (-not $enUs.ContainsKey($mid)) {
        Write-Host "    [AVISO] no encontre en_us para '$mid' (mod quitado?)" -ForegroundColor Yellow
        continue
    }
    $orig = $enUs[$mid]

    $fantasma = @($mias | Where-Object { -not $orig.Contains($_) })
    $sinTraducir = @($orig | Where-Object { -not $mias.Contains($_) })

    Write-Host "    traducidas: $($mias.Count) / $($orig.Count) del mod"
    if ($fantasma.Count -gt 0) {
        Write-Host "    [FALLO] claves que NO existen en el mod ($($fantasma.Count)):" -ForegroundColor Red
        $fantasma | ForEach-Object { Write-Host "        >> $_" }
        $errores += $fantasma.Count
    } else {
        Write-Host "    [OK] todas las claves existen en el mod" -ForegroundColor Green
    }
    if ($sinTraducir.Count -gt 0) {
        Write-Host "    [info] sin traducir: $($sinTraducir.Count)" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "============================================================"
if ($errores -eq 0) { Write-Host " RESULTADO: OK, sin errores" -ForegroundColor Green }
else { Write-Host " RESULTADO: $errores errores" -ForegroundColor Red }
