<#
    detectar-perdida.ps1
    --------------------------------------------------------------
    Descarga el storage del datapack de respaldo (historial de 20
    fotos del inventario), lo parsea como NBT real y compara la foto
    MAS VIEJA con la MAS NUEVA para decir que items perdio el jugador.

    No necesitas saber que perdiste: el script lo dice.

    Uso: .\detectar-perdida.ps1
#>
$ErrorActionPreference = "Stop"
$carpeta = $PSScriptRoot
. (Join-Path $carpeta "exaroton-lib.ps1")
$t   = Get-ExarotonToken  -RutaToken  (Join-Path $carpeta "exaroton-token.xml")
$cfg = Get-ExarotonConfig -RutaConfig (Join-Path $carpeta "config.json")
$sid = $cfg.serverId
$hc  = @{ Authorization = "Bearer $t"; "Content-Type" = "application/json" }
$hg  = @{ Authorization = "Bearer $t" }
$ProgressPreference = 'SilentlyContinue'

# 1. Forzar guardado y descargar el storage del datapack
$body = @{ command = 'save-all flush' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "https://api.exaroton.com/v1/servers/$sid/command/" -Headers $hc -Body $body | Out-Null
Start-Sleep -Seconds 6
$dat = Join-Path $carpeta "snapshots\command_storage.dat"
Invoke-RestMethod -Method Get -Uri "https://api.exaroton.com/v1/servers/$sid/files/data/world/data/command_storage_respaldoinv.dat" -Headers $hg -OutFile $dat

# 2. Parser NBT
$b = [IO.File]::ReadAllBytes($dat)
$ms = New-Object IO.MemoryStream(,$b)
try { $gz = New-Object IO.Compression.GzipStream($ms,[IO.Compression.CompressionMode]::Decompress); $o = New-Object IO.MemoryStream; $gz.CopyTo($o); $script:data = $o.ToArray() }
catch { $script:data = $b }
$script:pos = 0

function Byte1 { $v = $script:data[$script:pos]; $script:pos++; return [int]$v }
function Rev([int]$n) { $a = New-Object byte[] $n; [Array]::Copy($script:data,$script:pos,$a,0,$n); $script:pos += $n; [Array]::Reverse($a); return ,$a }
function U16 { return [BitConverter]::ToUInt16((Rev 2),0) }
function Str1 { $len = U16; $s = [Text.Encoding]::UTF8.GetString($script:data,$script:pos,$len); $script:pos += $len; return $s }
function Payload([int]$ty) {
    switch ($ty) {
        1 { $x = Byte1; if ($x -gt 127) { $x -= 256 }; return [int]$x }
        2 { return [BitConverter]::ToInt16((Rev 2),0) }
        3 { return [BitConverter]::ToInt32((Rev 4),0) }
        4 { return [BitConverter]::ToInt64((Rev 8),0) }
        5 { return [BitConverter]::ToSingle((Rev 4),0) }
        6 { return [BitConverter]::ToDouble((Rev 8),0) }
        7 { $n = [BitConverter]::ToInt32((Rev 4),0); $script:pos += $n; return @() }
        8 { return Str1 }
        9 { $et = Byte1; $n = [BitConverter]::ToInt32((Rev 4),0); $l = New-Object System.Collections.ArrayList; for ($i=0;$i -lt $n;$i++){ [void]$l.Add((Payload $et)) }; return ,$l }
        10 { $c = @{}; while ($true) { $tt = Byte1; if ($tt -eq 0) { break }; $nm = Str1; $c[$nm] = (Payload $tt) }; return $c }
        11 { $n = [BitConverter]::ToInt32((Rev 4),0); $script:pos += 4*$n; return @() }
        12 { $n = [BitConverter]::ToInt32((Rev 4),0); $script:pos += 8*$n; return @() }
        default { throw "tipo NBT $ty" }
    }
}

[void](Byte1); [void](Str1)
$root = Payload 10
$hist = $root.data.contents.data.historial
Write-Host ("Fotos en el historial: {0}" -f $hist.Count)
if ($hist.Count -lt 2) { Write-Host "No hay suficientes fotos para comparar."; return }

function ItemsDeFoto($foto) {
    $d = @{}
    foreach ($jug in $foto.jugadores) {
        foreach ($it in $jug.Inventory)  { if ($it.id) { $d[$it.id] = [int]$it.Count + [int]$d[$it.id] } }
        foreach ($it in $jug.EnderItems) { if ($it.id) { $d[$it.id] = [int]$it.Count + [int]$d[$it.id] } }
        if ($jug.Curios -and $jug.Curios.Curios) {
            foreach ($slot in $jug.Curios.Curios) {
                if ($slot.StacksHandler -and $slot.StacksHandler.Stacks -and $slot.StacksHandler.Stacks.Items) {
                    foreach ($it in $slot.StacksHandler.Stacks.Items) { if ($it.id) { $d[$it.id] = [int]$it.Count + [int]$d[$it.id] } }
                }
            }
        }
    }
    return $d
}

$vieja = ItemsDeFoto $hist[0]
$nueva = ItemsDeFoto $hist[$hist.Count - 1]

Write-Host ""
Write-Host "=== ITEMS QUE PERDISTE (foto mas vieja -> foto mas nueva) ===" -ForegroundColor Yellow
$hay = $false
foreach ($k in ($vieja.Keys | Sort-Object)) {
    if ($k -eq 'minecraft:air') { continue }
    $now = [int]$nueva[$k]
    if ($now -lt $vieja[$k]) {
        "  {0}   (antes {1}, ahora {2})" -f $k, $vieja[$k], $now
        $hay = $true
    }
}
if (-not $hay) { Write-Host "  Sin diferencias entre la foto mas vieja y la mas nueva." }
