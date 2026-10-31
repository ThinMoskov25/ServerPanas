<#
    comparar-perdida.ps1  (v2 - sobre capturas EN VIVO)
    --------------------------------------------------------------
    Detecta que item perdio un jugador comparando sus DOS capturas
    en vivo mas recientes (archivos <jugador>.txt del bot v2).
    No necesitas saber que se perdio.

    Uso:
      .\comparar-perdida.ps1 -Jugador SrBalduR
#>

param(
    [Parameter(Mandatory)] [string] $Jugador
)

$ErrorActionPreference = "Stop"
$baseSnap = Join-Path $PSScriptRoot "snapshots"

$caps = Get-ChildItem $baseSnap -Recurse -Filter "$Jugador.txt" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 2
if ($caps.Count -lt 2) { Write-Host "Hacen falta al menos 2 capturas de $Jugador."; return }

$actual   = $caps[0].FullName
$anterior = $caps[1].FullName
Write-Host "ANTES: $anterior"
Write-Host "AHORA: $actual"

function Items($archivo) {
    $txt = Get-Content $archivo -Raw
    # Extrae cada item como id + encantamientos/afijos clave para distinguir
    $lista = New-Object System.Collections.Generic.List[string]
    foreach ($m in ([regex]'id:\s*"([a-z0-9_]+:[a-z0-9_/]+)"').Matches($txt)) {
        $id = $m.Groups[1].Value
        if ($id -eq 'minecraft:air') { continue }
        # contexto alrededor para distinguir items encantados del mismo id
        $ini = [Math]::Max(0, $m.Index - 180)
        $ctx = $txt.Substring($ini, [Math]::Min(360, $txt.Length - $ini))
        $ench = ([regex]'"?(ensorcellation|tombstone|minecraft|apotheosis|irons_spellbooks):[a-z_]+"?,\s*lvl').Matches($ctx) | ForEach-Object { $_.Value }
        $firma = $id + "|" + (($ench | Sort-Object) -join ",")
        $lista.Add($firma)
    }
    return $lista
}

$a = Items $anterior
$c = Items $actual

# contar ocurrencias
$ca = @{}; foreach($x in $a){ $ca[$x] = 1 + [int]$ca[$x] }
$cc = @{}; foreach($x in $c){ $cc[$x] = 1 + [int]$cc[$x] }

Write-Host ""
Write-Host "=== ITEMS QUE PERDISTE (estaban antes, ya no) ===" -ForegroundColor Yellow
$hay = $false
foreach ($k in ($ca.Keys | Sort-Object)) {
    $now = if ($cc.ContainsKey($k)) { $cc[$k] } else { 0 }
    if ($now -lt $ca[$k]) {
        $partes = $k -split '\|'
        $msg = "  " + $partes[0]
        if ($partes[1]) { $msg += "  [ench: " + $partes[1] + "]" }
        $msg += "   (antes $($ca[$k]), ahora $now)"
        Write-Host $msg
        $hay = $true
    }
}
if (-not $hay) { Write-Host "  Sin diferencias entre las dos ultimas capturas." }
