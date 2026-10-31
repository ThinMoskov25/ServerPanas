# ============================================================
#  Validador del arbol de misiones (FTB Quests)
#   1. Comprueba que cada item referenciado EXISTA en el pack
#      (mods + minecraft vanilla)
#   2. Comprueba que no haya IDs duplicados
#   3. Comprueba que las dependencias apunten a quests existentes
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base    = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods    = Join-Path $base "cliente\mods"
$quests  = Join-Path $base "cliente\config\ftbquests\quests"

# ---------- 1. Construir catalogo de items validos ----------
$validos = New-Object System.Collections.Generic.HashSet[string]

# items de los mods (nombre del modelo = id del item)
foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        foreach ($e in $z.Entries) {
            if ($e.FullName -match '^assets/([a-z0-9_.-]+)/models/item/(.+)\.json$') {
                [void]$validos.Add("$($Matches[1]):$($Matches[2])")
            }
        }
        $z.Dispose()
    } catch {}
}

# items vanilla, desde el jar de minecraft
$mcJar = "$env:APPDATA\.minecraft\versions\1.20.1\1.20.1.jar"
if (Test-Path $mcJar) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($mcJar)
        foreach ($e in $z.Entries) {
            if ($e.FullName -match '^assets/minecraft/models/item/(.+)\.json$') {
                [void]$validos.Add("minecraft:$($Matches[1])")
            }
        }
        $z.Dispose()
        Write-Host "Catalogo vanilla cargado desde 1.20.1.jar"
    } catch { Write-Host "[aviso] no se pudo leer el jar de minecraft" }
} else {
    Write-Host "[aviso] no encontre $mcJar - los items minecraft: no se validan"
}

Write-Host "Items validos en el catalogo: $($validos.Count)"
Write-Host ""

# ---------- 2. Analizar los ficheros de misiones ----------
$archivos = Get-ChildItem "$quests\chapters\*.snbt" -ErrorAction SilentlyContinue
if (-not $archivos) { Write-Host "No hay capitulos en $quests\chapters"; exit 1 }

$itemsMal   = @()
$todosIds   = @{}
$idsDup     = @()
$questIds   = New-Object System.Collections.Generic.HashSet[string]
$deps       = @{}
$nQuests    = 0

foreach ($f in $archivos) {
    $txt = Get-Content $f.FullName -Raw

    # items referenciados:  item: "modid:nombre"
    foreach ($m in [regex]::Matches($txt, 'item:\s*"([a-z0-9_.-]+:[a-z0-9_./-]+)"')) {
        $id = $m.Groups[1].Value
        if (-not $validos.Contains($id)) {
            $itemsMal += [PSCustomObject]@{ Item = $id; Archivo = $f.Name }
        }
    }

    # todos los ids declarados
    foreach ($m in [regex]::Matches($txt, 'id:\s*"([0-9A-Fa-f]{16})"')) {
        $id = $m.Groups[1].Value.ToUpper()
        if ($todosIds.ContainsKey($id)) { $idsDup += $id }
        else { $todosIds[$id] = $f.Name }
    }

    # ids de quest: el 'id:' que aparece junto a 'title:' de quest
    # (aproximacion: ids que empiezan por 3)
    foreach ($m in [regex]::Matches($txt, 'id:\s*"(3[0-9A-Fa-f]{15})"')) {
        [void]$questIds.Add($m.Groups[1].Value.ToUpper())
        $nQuests++
    }

    # dependencias
    foreach ($m in [regex]::Matches($txt, 'dependencies:\s*\[([^\]]*)\]')) {
        foreach ($d in [regex]::Matches($m.Groups[1].Value, '"([0-9A-Fa-f]{16})"')) {
            $dep = $d.Groups[1].Value.ToUpper()
            if (-not $deps.ContainsKey($dep)) { $deps[$dep] = @() }
            $deps[$dep] += $f.Name
        }
    }
}

# dimensiones referenciadas (solo informativo)
$dims = @()
foreach ($f in $archivos) {
    $txt = Get-Content $f.FullName -Raw
    foreach ($m in [regex]::Matches($txt, 'dimension:\s*"([a-z0-9_.:-]+)"')) {
        $dims += $m.Groups[1].Value
    }
}

# ---------- 3. Reporte ----------
Write-Host "============================================================"
Write-Host " VALIDACION DEL ARBOL DE MISIONES"
Write-Host "============================================================"
Write-Host "Capitulos : $($archivos.Count)"
Write-Host "Misiones  : $nQuests"
Write-Host "IDs totales: $($todosIds.Count)"
Write-Host ""

if ($itemsMal.Count -eq 0) {
    Write-Host "  [OK] Todos los items referenciados existen." -ForegroundColor Green
} else {
    Write-Host "  [FALLO] Items que NO existen: $($itemsMal.Count)" -ForegroundColor Red
    $itemsMal | Sort-Object Item -Unique | ForEach-Object {
        Write-Host "     >> $($_.Item)   ($($_.Archivo))"
    }
}
Write-Host ""

if ($idsDup.Count -eq 0) {
    Write-Host "  [OK] No hay IDs duplicados." -ForegroundColor Green
} else {
    Write-Host "  [FALLO] IDs duplicados: $(($idsDup | Sort-Object -Unique) -join ', ')" -ForegroundColor Red
}
Write-Host ""

$depsMal = $deps.Keys | Where-Object { -not $questIds.Contains($_) }
if (-not $depsMal) {
    Write-Host "  [OK] Todas las dependencias apuntan a misiones existentes." -ForegroundColor Green
} else {
    Write-Host "  [FALLO] Dependencias roturas:" -ForegroundColor Red
    $depsMal | ForEach-Object { Write-Host "     >> $_  (usada en $($deps[$_] -join ', '))" }
}
Write-Host ""
Write-Host "Dimensiones referenciadas: $(($dims | Sort-Object -Unique) -join ', ')"
