# ============================================================
#  REPARA archivos es_es.json ROTOS dentro de los mods
#
#  El problema: algunos mods traen su traduccion al espanol
#  completa, pero con un error de sintaxis JSON (una coma que
#  falta, una coma sobrante...). Minecraft no puede parsear el
#  archivo y DESCARTA LA TRADUCCION ENTERA, mostrando ingles.
#
#  Caso real encontrado: Ice and Fire tenia 1322 traducciones
#  perfectas y una coma faltante al final las anulaba todas.
#
#  Solucion: extraer los pares clave/valor con regex (que no se
#  rompe por el error de sintaxis), y escribir un es_es.json
#  VALIDO en nuestro resourcepack, que tiene prioridad sobre el
#  del mod.
#
#  Uso:
#    .\_reparar-traducciones.ps1              -> solo informa
#    .\_reparar-traducciones.ps1 -aplicar     -> escribe los fixes
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base    = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods    = Join-Path $base "cliente\mods"
$rpAssets= Join-Path $base "cliente\resourcepacks\Espanol-ServerPanas\assets"
$aplicar = $args -contains '-aplicar'

function Leer($z,$r){
    $e=$z.Entries|Where-Object{$_.FullName -eq $r}|Select-Object -First 1
    if(-not $e){return $null}
    $sr=New-Object System.IO.StreamReader($e.Open(),[System.Text.Encoding]::UTF8)
    $t=$sr.ReadToEnd(); $sr.Close(); return $t
}

$roticos = @()

foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    try {
        $z=[System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $mids = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/es_es\.json$' } |
                ForEach-Object { if ($_.FullName -match '^assets/([a-z0-9_.-]+)/lang/es_es\.json$') { $Matches[1] } } |
                Sort-Object -Unique
        foreach ($mid in $mids) {
            $txt = Leer $z "assets/$mid/lang/es_es.json"
            if (-not $txt) { continue }
            $valido = $true
            $err = ""
            try { $null = $txt | ConvertFrom-Json } catch { $valido = $false; $err = $_.Exception.Message }
            if ($valido) { continue }

            # extraer pares con regex (tolera el error de sintaxis)
            $pares = [ordered]@{}
            foreach ($m in [regex]::Matches($txt, '"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')) {
                $pares[$m.Groups[1].Value] = $m.Groups[2].Value   # duplicados: gana el ultimo
            }
            $roticos += [PSCustomObject]@{
                Jar    = $j.Name
                ModId  = $mid
                Pares  = $pares.Count
                Error  = ($err -split "`n")[0]
                Datos  = $pares
            }
        }
        $z.Dispose()
    } catch {}
}

Write-Host "============================================================"
Write-Host " TRADUCCIONES AL ESPANOL ROTAS DENTRO DE LOS MODS"
Write-Host "============================================================"
if ($roticos.Count -eq 0) {
    Write-Host "  Ninguna. Todos los es_es.json de los mods son JSON valido." -ForegroundColor Green
    return
}

foreach ($r in $roticos) {
    Write-Host ""
    Write-Host "  $($r.Jar)  [$($r.ModId)]" -ForegroundColor Yellow
    Write-Host "     traducciones recuperables : $($r.Pares)"
    Write-Host "     Minecraft las descarta TODAS por el error de sintaxis"
}

Write-Host ""
if (-not $aplicar) {
    Write-Host "  (modo informe. Ejecuta con -aplicar para escribir los arreglos)"
    return
}

Write-Host "--- Aplicando arreglos en el resourcepack ---"
foreach ($r in $roticos) {
    $destDir = Join-Path $rpAssets "$($r.ModId)\lang"
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $dest = Join-Path $destDir "es_es.json"

    # si ya habia un es_es nuestro, conservar sus valores (tienen prioridad)
    $mios = @{}
    if (Test-Path $dest) {
        $t = Get-Content $dest -Raw -Encoding UTF8
        foreach ($m in [regex]::Matches($t, '"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')) { $mios[$m.Groups[1].Value]=$m.Groups[2].Value }
    }

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("{")
    $claves = @($r.Datos.Keys)
    for ($i=0; $i -lt $claves.Count; $i++) {
        $k = $claves[$i]
        $v = if ($mios.ContainsKey($k)) { $mios[$k] } else { $r.Datos[$k] }
        $coma = if ($i -lt $claves.Count-1) { "," } else { "" }
        [void]$sb.AppendLine(('	"{0}": "{1}"{2}' -f $k, $v, $coma))
    }
    [void]$sb.AppendLine("}")

    # escribir en UTF-8 sin BOM
    [System.IO.File]::WriteAllText($dest, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

    # verificar que ahora SI es valido
    try {
        $null = Get-Content $dest -Raw -Encoding UTF8 | ConvertFrom-Json
        Write-Host "  [OK] $($r.ModId): $($claves.Count) traducciones recuperadas y validadas" -ForegroundColor Green
    } catch {
        Write-Host "  [FALLO] $($r.ModId): el archivo generado sigue invalido" -ForegroundColor Red
    }
}
