# Aplica la distribucion de teclas simplificada de docs/MIS-TECLAS.txt
# a options.txt de la instancia de CurseForge. Minecraft debe estar CERRADO
# (al cerrarse reescribe options.txt). Guarda una copia antes de tocar nada.
param(
    [string] $Instancia = (Join-Path $env:USERPROFILE "curseforge\minecraft\Instances\SERVER PANAS"),
    [switch] $SinComprobarJuego
)
$ErrorActionPreference = "Stop"
$opciones = Join-Path $Instancia "options.txt"
if (-not (Test-Path $opciones)) { throw "No encuentro $opciones" }

$abierto = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" |
    Where-Object { $_.CommandLine -match [regex]::Escape($Instancia) -or $_.CommandLine -match 'SERVER PANAS' }
if ($abierto -and -not $SinComprobarJuego) { throw "Minecraft esta abierto. Cierralo y vuelve a ejecutar este script." }

$CTRL = "key.keyboard.right.control"
# accion = tecla, combinacion (vacio = tecla sola). "-" = sin tecla.
$mapa = [ordered]@{
    # --- Teclas sueltas a mano izquierda ---
    "key.carry.desc"                          = @("key.keyboard.c", "")          # Carry On
    "key.push_to_talk"                        = @("key.keyboard.caps.lock", "")  # Pulsar para hablar
    "key.saveToolbarActivator"                = @("-", "")                       # (solo creativo)
    # --- Teclado numerico, sin combinacion ---
    "gui.xaero_instant_waypoint"              = @("key.keyboard.keypad.0", "")
    "key.irons_spellbooks.spell_quick_cast_1" = @("key.keyboard.keypad.1", "")
    "key.irons_spellbooks.spell_quick_cast_2" = @("key.keyboard.keypad.2", "")
    "key.irons_spellbooks.spell_quick_cast_3" = @("key.keyboard.keypad.3", "")
    "key.toolbelt.cycle.left"                 = @("key.keyboard.keypad.4", "")
    "quark.keybind.autorun"                   = @("key.keyboard.keypad.5", "")
    "key.toolbelt.cycle.right"                = @("key.keyboard.keypad.6", "")
    "key.apotheosis.toggle_radial_mining"     = @("key.keyboard.keypad.7", "")
    "key.jade.show_details"                   = @("key.keyboard.keypad.8", "")
    "key.relics.ability_list"                 = @("key.keyboard.keypad.9", "")
    "gui.xaero_zoom_out"                      = @("key.keyboard.keypad.add", "")
    "gui.xaero_zoom_in"                       = @("key.keyboard.keypad.subtract", "")
    "gui.xaero_new_waypoint"                  = @("key.keyboard.keypad.multiply", "")
    "key.ftbteams.open_gui"                   = @("key.keyboard.keypad.divide", "")
    "keybind.reliquary.fortune_coin"          = @("key.keyboard.keypad.decimal", "")
    "tombstone.message.knowledge_of_death"    = @("key.keyboard.keypad.enter", "")
    # --- CtrlDer + teclado numerico ---
    "key.whisper"                             = @("key.keyboard.keypad.0", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_4" = @("key.keyboard.keypad.1", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_5" = @("key.keyboard.keypad.2", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_6" = @("key.keyboard.keypad.3", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_7" = @("key.keyboard.keypad.4", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_8" = @("key.keyboard.keypad.5", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_9" = @("key.keyboard.keypad.6", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_10"= @("key.keyboard.keypad.7", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_11"= @("key.keyboard.keypad.8", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_12"= @("key.keyboard.keypad.9", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_13"= @("key.keyboard.keypad.add", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_14"= @("key.keyboard.keypad.subtract", $CTRL)
    "key.irons_spellbooks.spell_quick_cast_15"= @("key.keyboard.keypad.multiply", $CTRL)
    "quark.keybind.shift_lock"                = @("key.keyboard.keypad.divide", $CTRL)
    "key.travelersbackpack.sort"              = @("key.keyboard.keypad.decimal", $CTRL)
    "tombstone.message.compendium"            = @("key.keyboard.keypad.enter", $CTRL)
}

$lineas = Get-Content $opciones -Encoding UTF8
$copia = "$opciones.antes-teclas-" + (Get-Date -Format "yyyyMMdd-HHmmss")
Copy-Item $opciones $copia

$quitadas = 0; $puestas = 0
$nuevas = foreach ($l in $lineas) {
    if ($l -notmatch '^key_([^:]+):([^:]+):([^:]*):?(.*)$') { $l; continue }
    $accion = $Matches[1]; $tecla = $Matches[2]; $extra = $Matches[4]
    if ($mapa.Contains($accion)) {
        $t, $m = $mapa[$accion]
        if ($t -eq "-") { $t = "key.keyboard.unknown"; $m = "" }
        $puestas++
        "key_${accion}:${t}:NONE:${m}"
    }
    elseif (($tecla -like "key.keyboard.keypad.*") -or ($extra -match 'caps\.lock|right\.alt|right\.control')) {
        # Todo lo que estaba en el teclado numerico o en combinaciones raras queda sin tecla.
        $quitadas++
        "key_${accion}:key.keyboard.unknown:NONE:"
    }
    else { $l }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($opciones, $nuevas, $utf8)
Write-Host "Listo: $puestas teclas asignadas, $quitadas combinaciones quitadas."
Write-Host "Copia de seguridad: $copia"
