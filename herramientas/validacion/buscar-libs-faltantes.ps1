# ============================================================
#  Busca dependencias NO DECLARADAS (como el caso de EpheroLib)
#
#  Idea: los packs de origen (ATM9, Cursed Walking...) son packs
#  que FUNCIONAN. Si uno de ellos incluye una libreria que mi pack
#  no tiene, y mi pack usa mods de ese pack, esa libreria es
#  candidata a ser una dependencia no declarada.
# ============================================================

$base = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$mios = Get-ChildItem "$base\cliente\mods\*.jar" | Select-Object -ExpandProperty Name

$inst = "$env:USERPROFILE\curseforge\minecraft\Instances"
$mc   = "$env:APPDATA\.minecraft"
$fuentes = @(
    "$inst\All the Mods 9 - ATM9\mods",
    "$inst\Cursed Walking - A Modern Zombie Apocalypse\mods",
    "$inst\DeceasedCraft - Urban Zombie Apocalypse\mods",
    "$inst\Epic fight and more\mods",
    "$inst\The Perfect create\mods",
    "$mc\back\011026\1.20.1",
    "$mc\back\1.20.1"
)

# normaliza: quita version para comparar por "nombre base"
function Base-Name([string]$n) {
    $x = $n -replace '\.jar$',''
    $x = $x -replace '[-_](forge|fabric|neoforge)','' 
    $x = $x -replace '[-_]?v?\d+[\d\.\+\-_a-z]*$',''
    return $x.ToLower().Trim('-','_',' ')
}

$misBases = @{}
foreach ($m in $mios) { $misBases[(Base-Name $m)] = $true }

# patrones que indican "libreria / api / core"
$patronLib = 'lib$|lib[-_]|^lib|api|core|^.*corelib|codec|kotlin|architectury|resourceful|puzzles|placebo|collective|framework|moonlight|citadel|geckolib|balm|cupboard|zeta|glitchcore|terrablender|structure_gel|supermartijn|titanium|cofh|epherolib|lionfish|octolib|prickle|monolib|txnilib|jamlib|pandalib|baguettelib|insanelib|temporalapi|tenshilib|theimpossiblelibrary|corgilib|smartbrainlib|tslateffectslib|mcore|atlas|konkrete|searchables|iceberg|mysterious_mountain_lib|lithostitched|cristellib|flib|libx|bookshelf|caelus|curios|expandability|resourcefulconfig|player-anim|item-filters|spell_power|ranged_weapon|jewelry'

$candidatas = @{}
foreach ($f in $fuentes) {
    if (-not (Test-Path $f)) { continue }
    foreach ($j in (Get-ChildItem "$f\*.jar" -EA SilentlyContinue)) {
        $b = Base-Name $j.Name
        if ($misBases.ContainsKey($b)) { continue }
        if ($j.Name -notmatch $patronLib) { continue }
        if (-not $candidatas.ContainsKey($b)) {
            $candidatas[$b] = [PSCustomObject]@{
                Archivo = $j.Name
                Origen  = (Split-Path (Split-Path $f -Parent) -Leaf)
            }
        }
    }
}

Write-Host "============================================================"
Write-Host " LIBRERIAS presentes en tus packs que NO estan en el mio"
Write-Host " (candidatas a dependencias no declaradas)"
Write-Host "============================================================"
Write-Host "Mis mods: $($mios.Count)"
Write-Host "Candidatas: $($candidatas.Count)"
Write-Host ""
$candidatas.Values | Sort-Object Archivo | ForEach-Object {
    "  {0,-55} [{1}]" -f $_.Archivo, $_.Origen
}
Write-Host ""
Write-Host "NOTA: no todas hacen falta. Solo son las que habria que revisar"
Write-Host "si un mod falla con NoClassDefFoundError."
