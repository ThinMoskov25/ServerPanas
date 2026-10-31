# ============================================================
#  Lista las claves que un mod tiene en INGLES pero NO en ESPANOL
#  (traducciones incompletas dentro del propio jar)
#
#  Uso: .\_claves-sin-traducir.ps1 <filtroJar> [filtroClave]
# ============================================================
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mods = Join-Path $base "cliente\mods"
$rp   = Join-Path $base "cliente\resourcepacks\Espanol-ServerPanas\assets"

$filtroJar   = if ($args.Count -ge 1) { $args[0] } else { "" }
$filtroClave = if ($args.Count -ge 2) { $args[1] } else { "" }

function Leer-Lang($zip, $ruta) {
    $e = $zip.Entries | Where-Object { $_.FullName -eq $ruta } | Select-Object -First 1
    if (-not $e) { return $null }
    $sr = New-Object System.IO.StreamReader($e.Open(), [System.Text.Encoding]::UTF8)
    $t = $sr.ReadToEnd(); $sr.Close()
    return $t
}

function Claves($texto) {
    $h = New-Object System.Collections.Generic.HashSet[string]
    if (-not $texto) { return $h }
    foreach ($m in [regex]::Matches($texto, '"((?:[^"\\]|\\.)+)"\s*:')) { [void]$h.Add($m.Groups[1].Value) }
    return $h
}

foreach ($j in (Get-ChildItem "$mods\*.jar")) {
    if ($filtroJar -and $j.Name -notlike "*$filtroJar*") { continue }
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        # modids con lang en este jar
        $modids = $z.Entries | Where-Object { $_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$' } |
                  ForEach-Object { if ($_.FullName -match '^assets/([a-z0-9_.-]+)/lang/en_us\.json$') { $Matches[1] } } | Sort-Object -Unique
        foreach ($mid in $modids) {
            $en = Leer-Lang $z "assets/$mid/lang/en_us.json"
            $es = Leer-Lang $z "assets/$mid/lang/es_es.json"
            $kEn = Claves $en
            $kEs = Claves $es

            # tambien cuentan las que YO ya traduje en el resourcepack
            $mio = Join-Path $rp "$mid\lang\es_es.json"
            if (Test-Path $mio) {
                foreach ($k in (Claves (Get-Content $mio -Raw -Encoding UTF8))) { [void]$kEs.Add($k) }
            }

            $faltan = @($kEn | Where-Object { -not $kEs.Contains($_) })
            if ($filtroClave) { $faltan = @($faltan | Where-Object { $_ -like "*$filtroClave*" }) }
            if ($faltan.Count -eq 0) { continue }

            Write-Host ""
            Write-Host "##### $($j.Name)  [$mid]" -ForegroundColor Cyan
            Write-Host "   en_us: $($kEn.Count)   es_es: $($kEs.Count)   SIN TRADUCIR: $($faltan.Count)"
            # imprimir con su texto en ingles
            $jsonEn = $null
            try { $jsonEn = $en | ConvertFrom-Json } catch {}
            foreach ($k in ($faltan | Sort-Object)) {
                $val = ""
                if ($jsonEn) { $p = $jsonEn.PSObject.Properties[$k]; if ($p) { $val = $p.Value } }
                Write-Host ('   "{0}": "{1}",' -f $k, $val)
            }
        }
        $z.Dispose()
    } catch {}
}
