$ErrorActionPreference = "Stop"
$dat = Join-Path $PSScriptRoot "snapshots\command_storage.dat"
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
        1 { $x = Byte1; if ($x -gt 127){$x-=256}; return [int]$x }
        2 { return [BitConverter]::ToInt16((Rev 2),0) }
        3 { return [BitConverter]::ToInt32((Rev 4),0) }
        4 { return [BitConverter]::ToInt64((Rev 8),0) }
        5 { return [BitConverter]::ToSingle((Rev 4),0) }
        6 { return [BitConverter]::ToDouble((Rev 8),0) }
        7 { $n=[BitConverter]::ToInt32((Rev 4),0); $script:pos+=$n; return @() }
        8 { return Str1 }
        9 { $et=Byte1; $n=[BitConverter]::ToInt32((Rev 4),0); $l=New-Object System.Collections.ArrayList; for($i=0;$i -lt $n;$i++){[void]$l.Add((Payload $et))}; return ,$l }
        10 { $c=@{}; while($true){ $tt=Byte1; if($tt -eq 0){break}; $nm=Str1; $c[$nm]=(Payload $tt) }; return $c }
        11 { $n=[BitConverter]::ToInt32((Rev 4),0); $script:pos+=4*$n; return @() }
        12 { $n=[BitConverter]::ToInt32((Rev 4),0); $script:pos+=8*$n; return @() }
    }
}
[void](Byte1); [void](Str1)
$root = Payload 10
function Dump($nodo, $prefijo, $prof) {
    if ($prof -gt 4) { return }
    if ($nodo -is [hashtable]) {
        foreach ($k in $nodo.Keys) {
            $v = $nodo[$k]
            $tipo = if ($v -is [hashtable]) { "obj" } elseif ($v -is [System.Collections.ArrayList]) { "lista[$($v.Count)]" } else { "$v" }
            Write-Host ("{0}{1} = {2}" -f $prefijo, $k, $tipo)
            if ($v -is [hashtable] -or $v -is [System.Collections.ArrayList]) { Dump $v ($prefijo + "  ") ($prof+1) }
        }
    } elseif ($nodo -is [System.Collections.ArrayList]) {
        if ($nodo.Count -gt 0) { Write-Host ("{0}[0]:" -f $prefijo); Dump $nodo[0] ($prefijo+"  ") ($prof+1) }
    }
}
Dump $root "" 0
