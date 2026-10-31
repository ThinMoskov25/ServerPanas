# ============================================================
#  Detecta NoClassDefFoundError ANTES de arrancar el juego
#
#  Como funciona:
#   - Los mods traen clases de "compat" para integrarse con otros
#     (ej: sophisticatedcore/compat/reliquary/ReliquaryCompat.class)
#   - Esas clases referencian clases del otro mod por su ruta exacta
#   - Si el otro mod cambio de version y movio/renombro esa clase,
#     salta NoClassDefFoundError al cargar
#
#  Filtro clave: solo avisa si el mod destino ESTA instalado pero
#  la clase concreta NO existe. (Si el mod no esta, la compat no
#  se ejecuta y no pasa nada.)
# ============================================================

Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods = Join-Path $base "cliente\mods"
if ($args.Count -ge 1) { $mods = $args[0] }

Write-Host "Indexando clases de todos los jars..."

$todasClases = New-Object System.Collections.Generic.HashSet[string]
$raices      = New-Object System.Collections.Generic.HashSet[string]
$compatDeJar = @{}

foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        foreach ($e in $z.Entries) {
            if ($e.FullName -like "*.class") {
                $sin = $e.FullName -replace '\.class$',''
                [void]$todasClases.Add($sin)
                $r = ($sin -split '/')[0]
                if ($r -and $r -notmatch '^(META-INF|module-info)') { [void]$raices.Add($r) }
                if ($e.FullName -match '/compat/') {
                    if (-not $compatDeJar.ContainsKey($j.Name)) { $compatDeJar[$j.Name] = @() }
                    $compatDeJar[$j.Name] += $e.FullName
                }
            }
        }
        $z.Dispose()
    } catch {}
}

Write-Host "Clases indexadas : $($todasClases.Count)"
Write-Host "Paquetes raiz    : $($raices.Count)"
Write-Host "Jars con compat  : $($compatDeJar.Count)"
Write-Host ""
Write-Host "Analizando clases de compatibilidad..."

$problemas = @()

foreach ($jarName in $compatDeJar.Keys) {
    $z = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $mods $jarName))
    foreach ($ruta in $compatDeJar[$jarName]) {
        $e = $z.GetEntry($ruta)
        if (-not $e) { continue }
        try {
            $ms = New-Object System.IO.MemoryStream
            $e.Open().CopyTo($ms)
            $txt = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
            $ms.Dispose()

            # referencias que parecen rutas de clase
            foreach ($m in [regex]::Matches($txt, '\b([a-z][a-z0-9_]{2,})((?:/[A-Za-z0-9_$]+){2,})\b')) {
                $raiz = $m.Groups[1].Value
                $full = $raiz + $m.Groups[2].Value
                # descartar paquetes de Minecraft, Forge, Java y librerias:
                # ahi los falsos positivos son constantes porque no
                # indexamos el jar del juego ni las libs de Forge.
                if ($raiz -in @('net','org','com','java','javax','io','it',
                                'kotlin','sun','jdk','mojang','oshi','joml',
                                'cpw','dev','top','me','cc','gg','xyz','de',
                                'fr','uk','ca','eu','mod','api','lib')) { continue }
                # solo si el mod destino esta instalado
                if (-not $raices.Contains($raiz)) { continue }
                # ignorar auto-referencias al propio arbol del jar
                if ($ruta.StartsWith($raiz + '/')) { continue }
                if (-not $todasClases.Contains($full)) {
                    $problemas += [PSCustomObject]@{
                        Jar    = $jarName
                        Compat = (Split-Path $ruta -Leaf)
                        Falta  = $full
                    }
                }
            }
        } catch {}
    }
    $z.Dispose()
}

$unicos = $problemas | Sort-Object Jar, Falta -Unique

Write-Host ""
Write-Host "============================================================"
Write-Host " RESULTADO"
Write-Host "============================================================"
if ($unicos.Count -eq 0) {
    Write-Host "  [OK] Ninguna clase de compat apunta a una clase inexistente." -ForegroundColor Green
} else {
    Write-Host "  [AVISO] $($unicos.Count) referencias rotas:" -ForegroundColor Yellow
    Write-Host "  (pueden ser falsos positivos: clases internas, genericos...)"
    Write-Host ""
    foreach ($p in $unicos) {
        Write-Host "   $($p.Jar)"
        Write-Host "      $($p.Compat)  ->  FALTA  $($p.Falta)"
    }
}
