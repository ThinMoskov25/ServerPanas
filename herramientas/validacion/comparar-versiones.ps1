# ============================================================
#  Detecta MEZCLAS DE VERSIONES entre packs
#
#  Problema real: si tomo el mod A de un pack y el mod B de otro,
#  puede que esas dos versiones nunca se hayan probado juntas
#  (fue el caso de Sophisticated Core + Reliquary).
#
#  Este script compara la version que uso con la que tiene cada
#  pack de origen, y marca las diferencias.
# ============================================================

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$inst = "$env:USERPROFILE\curseforge\minecraft\Instances"
$mc   = "$env:APPDATA\.minecraft"

$fuentes = [ordered]@{
    "ATM9"      = "$inst\All the Mods 9 - ATM9\mods"
    "Cursed"    = "$inst\Cursed Walking - A Modern Zombie Apocalypse\mods"
    "Deceased"  = "$inst\DeceasedCraft - Urban Zombie Apocalypse\mods"
    "EpicFight" = "$inst\Epic fight and more\mods"
    "Perfect"   = "$inst\The Perfect create\mods"
    "Zeefski"   = "$inst\Zeefski Jujutsu Kaisen Craft\mods"
    "back1201"  = "$mc\back\1.20.1"
    "back153"   = "$mc\back\011026\1.20.1"
}

# nombre base sin version
function Base-Name([string]$n) {
    $x = $n -replace '\.jar$',''
    $x = $x -replace '[-_]?v?\d+[\d\.\+\-]*(-[a-z0-9]+)?$',''
    return $x.ToLower().Trim('-','_',' ','.')
}

# indice: nombreBase -> @{ pack = nombreArchivo }
$idx = @{}
foreach ($k in $fuentes.Keys) {
    $p = $fuentes[$k]
    if (-not (Test-Path $p)) { continue }
    foreach ($j in (Get-ChildItem "$p\*.jar" -EA SilentlyContinue)) {
        $b = Base-Name $j.Name
        if (-not $idx.ContainsKey($b)) { $idx[$b] = [ordered]@{} }
        if (-not $idx[$b].Contains($k)) { $idx[$b][$k] = $j.Name }
    }
}

$mios = Get-ChildItem "$base\cliente\mods\*.jar"
$difs = @()

foreach ($m in $mios) {
    $b = Base-Name $m.Name
    if (-not $idx.ContainsKey($b)) { continue }
    $packs = $idx[$b]
    # packs donde la version es DISTINTA a la mia
    $otras = @()
    foreach ($k in $packs.Keys) {
        if ($packs[$k] -ne $m.Name) { $otras += "$k=$($packs[$k])" }
    }
    # de que pack viene la mia
    $mio = @()
    foreach ($k in $packs.Keys) { if ($packs[$k] -eq $m.Name) { $mio += $k } }

    if ($otras.Count -gt 0) {
        $difs += [PSCustomObject]@{
            Mod      = $m.Name
            Viene_de = ($mio -join ',')
            Otras    = $otras
        }
    }
}

Write-Host "============================================================"
Write-Host " MODS CON VERSIONES DISTINTAS EN OTROS PACKS"
Write-Host "============================================================"
Write-Host "Mis mods: $($mios.Count)   |   Con diferencias: $($difs.Count)"
Write-Host ""
Write-Host "Esto NO es necesariamente un error. Solo importa cuando dos"
Write-Host "mods que se integran entre si vienen de packs distintos."
Write-Host ""

foreach ($d in ($difs | Sort-Object Mod)) {
    Write-Host "--- $($d.Mod)"
    Write-Host "    uso la de : $($d.Viene_de)"
    foreach ($o in $d.Otras) { Write-Host "    otra      : $o" }
}
