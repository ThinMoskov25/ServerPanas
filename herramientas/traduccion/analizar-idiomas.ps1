# ============================================================
#  Analiza que mods del pack traen traduccion al español
#  Busca archivos assets/<modid>/lang/es_es.json dentro de cada .jar
# ============================================================

Add-Type -AssemblyName System.IO.Compression.FileSystem

$carpeta = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))) "cliente\mods"
if ($args.Count -ge 1) { $carpeta = $args[0] }

$conEs = @(); $sinEs = @(); $sinLang = @()

foreach ($j in (Get-ChildItem "$carpeta\*.jar")) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $langs = $z.Entries | Where-Object { $_.FullName -match '/lang/[a-z]{2}_[a-z]{2}\.json$' }
        if ($langs.Count -eq 0) {
            $sinLang += $j.Name
        } else {
            $es = $langs | Where-Object { $_.FullName -match '/lang/es_(es|mx|ar|cl|uy|ve)\.json$' }
            if ($es) {
                # medir cobertura: nº de claves es vs en
                $en = $langs | Where-Object { $_.FullName -match '/lang/en_us\.json$' } | Select-Object -First 1
                # Contamos claves con regex: ConvertFrom-Json falla con
                # algunos JSON de mods (BOM, comentarios, duplicados).
                $kEs = 0; $kEn = 0
                try {
                    $sr = New-Object System.IO.StreamReader($es[0].Open(), [System.Text.Encoding]::UTF8)
                    $kEs = ([regex]::Matches($sr.ReadToEnd(), '"\s*:\s*"')).Count
                    $sr.Close()
                } catch {}
                try {
                    if ($en) {
                        $sr = New-Object System.IO.StreamReader($en.Open(), [System.Text.Encoding]::UTF8)
                        $kEn = ([regex]::Matches($sr.ReadToEnd(), '"\s*:\s*"')).Count
                        $sr.Close()
                    }
                } catch {}
                $pct = if ($kEn -gt 0) { [math]::Round(($kEs / $kEn) * 100, 0) } else { 100 }
                $conEs += [PSCustomObject]@{ Mod = $j.Name; ClavesES = $kEs; ClavesEN = $kEn; Cobertura = $pct }
            } else {
                $sinEs += $j.Name
            }
        }
        $z.Dispose()
    } catch {
        $sinLang += "$($j.Name) [error]"
    }
}

$total = $conEs.Count + $sinEs.Count + $sinLang.Count

Write-Host "============================================================"
Write-Host " ANALISIS DE IDIOMA ESPAÑOL - $total mods"
Write-Host "============================================================"
Write-Host ""
Write-Host "CON traduccion al español : $($conEs.Count)"
Write-Host "SIN traduccion (solo ingles): $($sinEs.Count)"
Write-Host "Sin textos traducibles     : $($sinLang.Count)   (librerias, APIs)"
Write-Host ""

$traducibles = $conEs.Count + $sinEs.Count
if ($traducibles -gt 0) {
    $p = [math]::Round(($conEs.Count / $traducibles) * 100, 0)
    Write-Host "=> De los mods CON textos, el $p% tiene español."
}

Write-Host ""
Write-Host "--- MODS SIN ESPAÑOL (quedarian en ingles) ---"
$sinEs | Sort-Object | ForEach-Object { Write-Host "   $_" }

Write-Host ""
Write-Host "--- COBERTURA PARCIAL (menos del 80% traducido) ---"
$conEs | Where-Object { $_.Cobertura -lt 80 } | Sort-Object Cobertura |
    ForEach-Object { Write-Host ("   {0,3}%  {1}" -f $_.Cobertura, $_.Mod) }
