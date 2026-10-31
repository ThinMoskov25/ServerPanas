<#
    restaurar.ps1
    --------------------------------------------------------------
    Restaura a un jugador: vida (regeneracion), comida (saturacion)
    y limpia efectos negativos (veneno, wither, hambre).

    Uso:
        .\restaurar.ps1                 -> restaura a SrBalduR (por defecto)
        .\restaurar.ps1 -Jugador Pepe   -> restaura a otro jugador
#>

param(
    [string] $Jugador = "SrBalduR"
)

$ErrorActionPreference = "Stop"
$carpeta = $PSScriptRoot
. (Join-Path $carpeta "exaroton-lib.ps1")

$t   = Get-ExarotonToken  -RutaToken  (Join-Path $carpeta "exaroton-token.xml")
$cfg = Get-ExarotonConfig -RutaConfig (Join-Path $carpeta "config.json")
$sid = $cfg.serverId

Write-Host "Restaurando a $Jugador ..." -ForegroundColor Cyan

Invoke-ServerCommand -Token $t -ServerId $sid -Command "effect give $Jugador minecraft:regeneration 10 9 true"
Invoke-ServerCommand -Token $t -ServerId $sid -Command "effect give $Jugador minecraft:saturation 5 9 true"
Invoke-ServerCommand -Token $t -ServerId $sid -Command "effect clear $Jugador minecraft:poison"
Invoke-ServerCommand -Token $t -ServerId $sid -Command "effect clear $Jugador minecraft:wither"
Invoke-ServerCommand -Token $t -ServerId $sid -Command "effect clear $Jugador minecraft:hunger"

Write-Host "Listo: vida + comida restauradas y efectos negativos limpiados." -ForegroundColor Green
