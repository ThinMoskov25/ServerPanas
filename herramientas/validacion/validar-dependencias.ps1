# ============================================================
#  Validador de dependencias del modpack
#  Lee META-INF/mods.toml de cada .jar, saca los modId que provee
#  y los que exige, y reporta dependencias faltantes.
# ============================================================

Add-Type -AssemblyName System.IO.Compression.FileSystem

$carpeta = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))) "cliente\mods"
if ($args.Count -ge 1) { $carpeta = $args[0] }

$provee   = New-Object System.Collections.Generic.HashSet[string]
$exige    = @{}   # modId requerido -> lista de quien lo pide
$ignorar  = @("forge","minecraft","java","neoforge")

$jars = Get-ChildItem "$carpeta\*.jar"
Write-Host "Analizando $($jars.Count) mods en: $carpeta"
Write-Host ""

foreach ($j in $jars) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        $e = $z.Entries | Where-Object { $_.FullName -eq "META-INF/mods.toml" } | Select-Object -First 1
        if ($e) {
            $sr  = New-Object System.IO.StreamReader($e.Open())
            $txt = $sr.ReadToEnd(); $sr.Close()

            # modIds que este jar PROVEE: lineas [[mods]] seguidas de modId
            $enMods = $false
            foreach ($line in ($txt -split "`r?`n")) {
                $t = $line.Trim()
                if ($t -match '^\[\[mods\]\]')        { $enMods = $true;  continue }
                if ($t -match '^\[\[dependencies')     { $enMods = $false; continue }
                if ($enMods -and $t -match '^modId\s*=\s*"([^"]+)"') {
                    [void]$provee.Add($Matches[1].ToLower())
                }
            }
            # modIds que EXIGE: dentro de bloques [[dependencies...]]
            $enDep = $false
            $depId = $null
            foreach ($line in ($txt -split "`r?`n")) {
                $t = $line.Trim()
                if ($t -match '^\[\[dependencies') { $enDep = $true; $depId = $null; continue }
                if ($t -match '^\[\[mods\]\]')     { $enDep = $false; continue }
                if ($enDep) {
                    if ($t -match '^modId\s*=\s*"([^"]+)"') { $depId = $Matches[1].ToLower() }
                    if ($t -match '^mandatory\s*=\s*true' -and $depId) {
                        if ($ignorar -notcontains $depId) {
                            if (-not $exige.ContainsKey($depId)) { $exige[$depId] = @() }
                            $exige[$depId] += $j.Name
                        }
                        $depId = $null
                    }
                }
            }
        } else {
            Write-Host "  [aviso] sin mods.toml: $($j.Name)"
        }
        $z.Dispose()
    } catch {
        Write-Host "  [ERROR] no se pudo leer: $($j.Name)"
    }
}

Write-Host ""
Write-Host "============================================================"
Write-Host " RESULTADO"
Write-Host "============================================================"
Write-Host "modIds provistos por el pack: $($provee.Count)"

$faltantes = @()
foreach ($k in $exige.Keys) {
    if (-not $provee.Contains($k)) { $faltantes += $k }
}

if ($faltantes.Count -eq 0) {
    Write-Host ""
    Write-Host "  OK: no falta ninguna dependencia obligatoria."
} else {
    Write-Host ""
    Write-Host "  DEPENDENCIAS FALTANTES: $($faltantes.Count)"
    foreach ($f in ($faltantes | Sort-Object)) {
        Write-Host ""
        Write-Host "   >> FALTA: $f"
        Write-Host "      lo piden: $(($exige[$f] | Select-Object -Unique) -join ', ')"
    }
}
