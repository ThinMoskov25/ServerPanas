# ============================================================
#  Probador de conexion al servidor (exaroton, Aternos u otro)
#  Hace 3 comprobaciones REALES:
#    1. Resuelve el DNS del dominio
#    2. Prueba el puerto TCP
#    3. Consulta el estado con el protocolo Server List Ping
#       (devuelve version, MOTD y jugadores conectados)
#
#  USO (sin argumentos usa el SERVER PANAS de exaroton):
#    .\PROBAR-SERVIDOR.ps1
#    .\PROBAR-SERVIDOR.ps1 ShadowBlack77-rEez.exaroton.me 23574
#
#  OJO: si tu servidor usa un puerto NO estandar (exaroton y Aternos
#  siempre dan uno), pasalo como segundo argumento.
# ============================================================

param(
    [string]$Servidor = "ShadowBlack77-rEez.exaroton.me",
    [int]$Puerto = 23574
)

# Panel segun el proveedor, para los mensajes de ayuda
$panel = switch -Regex ($Servidor) {
    'exaroton\.me$' { "https://exaroton.com" }
    'aternos\.me$'  { "https://aternos.org" }
    default         { "el panel de tu servidor" }
}

function Write-Paso($txt) { Write-Host ""; Write-Host "--- $txt" -ForegroundColor Cyan }
function OK($txt)   { Write-Host "    [OK]    $txt" -ForegroundColor Green }
function FAIL($txt) { Write-Host "    [FALLO] $txt" -ForegroundColor Red }
function INFO($txt) { Write-Host "    $txt" }

Write-Host "============================================================"
Write-Host " PROBANDO: $Servidor : $Puerto"
Write-Host "============================================================"

# ---------- 1. DNS ----------
Write-Paso "1) Resolucion DNS"
$ips = @()
try {
    $ips = [System.Net.Dns]::GetHostAddresses($Servidor) |
           Where-Object { $_.AddressFamily -eq 'InterNetwork' } |
           ForEach-Object { $_.IPAddressToString }
    if ($ips.Count -gt 0) {
        OK "El dominio resuelve a: $($ips -join ', ')"
    } else {
        FAIL "No se obtuvo ninguna IPv4."
    }
} catch {
    FAIL "No se pudo resolver el dominio. Revisa que este bien escrito."
    Write-Host ""
    Write-Host "Resultado: SERVIDOR NO ENCONTRADO" -ForegroundColor Red
    exit 1
}

# ---------- 2. Puerto TCP ----------
Write-Paso "2) Puerto TCP $Puerto"
$tcp = New-Object System.Net.Sockets.TcpClient
$abierto = $false
try {
    $iar = $tcp.BeginConnect($Servidor, $Puerto, $null, $null)
    if ($iar.AsyncWaitHandle.WaitOne(5000, $false) -and $tcp.Connected) {
        $tcp.EndConnect($iar)
        $abierto = $true
        OK "Puerto abierto, el servidor acepta conexiones."
    } else {
        FAIL "Puerto cerrado o sin respuesta (timeout 5s)."
    }
} catch {
    FAIL "No se pudo conectar al puerto."
}

if (-not $abierto) {
    try { $tcp.Close() } catch {}
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " RESULTADO: EL SERVIDOR ESTA APAGADO" -ForegroundColor Yellow
    Write-Host "============================================================"
    Write-Host " El dominio existe pero nadie responde en el puerto."
    Write-Host " Esto es NORMAL si no has encendido el servidor."
    Write-Host ""
    Write-Host " Que hacer:"
    Write-Host "   1. Entra a $panel"
    Write-Host "   2. Abre tu servidor y pulsa INICIAR / START"
    Write-Host "   3. Espera a que el estado este en verde (Online)"
    Write-Host "   4. Vuelve a ejecutar este script"
    Write-Host ""
    Write-Host " Comprueba tambien que el PUERTO sea el correcto."
    Write-Host " Estas probando el $Puerto."
    exit 2
}

# ---------- 3. Server List Ping ----------
Write-Paso "3) Estado del servidor (protocolo Minecraft)"

function New-VarInt([int]$valor) {
    $bytes = New-Object System.Collections.Generic.List[byte]
    $v = [uint32]$valor
    do {
        $b = [byte]($v -band 0x7F)
        $v = $v -shr 7
        if ($v -ne 0) { $b = $b -bor 0x80 }
        $bytes.Add($b)
    } while ($v -ne 0)
    return $bytes.ToArray()
}

function Read-VarInt($stream) {
    $num = 0; $shift = 0
    while ($true) {
        $b = $stream.ReadByte()
        if ($b -lt 0) { throw "conexion cerrada" }
        $num = $num -bor (($b -band 0x7F) -shl $shift)
        if (($b -band 0x80) -eq 0) { break }
        $shift += 7
        if ($shift -ge 35) { throw "VarInt invalido" }
    }
    return $num
}

try {
    $stream = $tcp.GetStream()
    $stream.ReadTimeout  = 8000
    $stream.WriteTimeout = 8000

    # --- Handshake ---
    $hostBytes = [System.Text.Encoding]::UTF8.GetBytes($Servidor)
    $cuerpo = New-Object System.Collections.Generic.List[byte]
    $cuerpo.AddRange([byte[]](New-VarInt 0))        # packet id 0x00 = handshake
    $cuerpo.AddRange([byte[]](New-VarInt 763))      # protocol version (1.20.1)
    $cuerpo.AddRange([byte[]](New-VarInt $hostBytes.Length))
    $cuerpo.AddRange($hostBytes)
    $cuerpo.Add([byte](($Puerto -shr 8) -band 0xFF))  # puerto (big endian)
    $cuerpo.Add([byte]($Puerto -band 0xFF))
    $cuerpo.AddRange([byte[]](New-VarInt 1))        # next state = 1 (status)

    $paquete = New-Object System.Collections.Generic.List[byte]
    $paquete.AddRange([byte[]](New-VarInt $cuerpo.Count))
    $paquete.AddRange($cuerpo)
    $arr = $paquete.ToArray()
    $stream.Write($arr, 0, $arr.Length)

    # --- Status request (paquete vacio id 0x00) ---
    $req = [byte[]]@(0x01, 0x00)
    $stream.Write($req, 0, $req.Length)
    $stream.Flush()

    # --- Respuesta ---
    $null     = Read-VarInt $stream      # longitud total del paquete
    $packetId = Read-VarInt $stream      # packet id (debe ser 0)
    if ($packetId -ne 0) { throw "respuesta inesperada (id=$packetId)" }
    $len      = Read-VarInt $stream      # longitud del JSON

    $buf = New-Object byte[] $len
    $leido = 0
    while ($leido -lt $len) {
        $n = $stream.Read($buf, $leido, $len - $leido)
        if ($n -le 0) { break }
        $leido += $n
    }
    $json = [System.Text.Encoding]::UTF8.GetString($buf, 0, $leido)
    $est  = $json | ConvertFrom-Json

    # ----------------------------------------------------------
    #  Caso Aternos con el servidor detenido:
    #  su proxy SI responde el protocolo, pero con un marcador:
    #     version.name = "§c● Offline"   protocol = -1
    #     MOTD         = "This server is offline."
    #  Sin detectarlo, el script decia "ONLINE" por error.
    # ----------------------------------------------------------
    # El MOTD puede venir como texto plano o como objeto con 'text' y 'extra'.
    # Hay que armarlo por partes: interpolar el objeto directamente imprime
    # su volcado (@{color=red; extra=...}) en vez del texto.
    $motdCrudo = ""
    if ($est.description -is [string]) {
        $motdCrudo = $est.description
    } else {
        if ($est.description.text)  { $motdCrudo += $est.description.text }
        if ($est.description.extra) { $motdCrudo += (($est.description.extra | ForEach-Object { $_.text }) -join '') }
    }
    $motdCrudo = ($motdCrudo -replace '\s+',' ').Trim()

    $parado = ($est.version.protocol -eq -1) -or
              ("$($est.version.name)" -match '(?i)offline') -or
              ($motdCrudo -match '(?i)server is offline') -or
              ($motdCrudo -match '(?i)sleeping')

    if ($parado) {
        Write-Host ""
        Write-Host "============================================================"
        Write-Host " RESULTADO: EL SERVIDOR ESTA DETENIDO" -ForegroundColor Yellow
        Write-Host "============================================================"
        Write-Host " El proxy del proveedor responde, pero devuelve su"
        Write-Host " marcador de servidor apagado:"
        Write-Host ""
        INFO "version  : $($est.version.name)"
        INFO "protocolo: $($est.version.protocol)   (-1 = sin servidor detras)"
        INFO "MOTD     : $($motdCrudo -replace '§.','')"
        Write-Host ""
        Write-Host " Esto NO es un error del pack. Solo significa que el"
        Write-Host " servidor no esta iniciado."
        Write-Host ""
        Write-Host " Que hacer:"
        Write-Host "   1. Entra a $panel"
        Write-Host "   2. Abre tu servidor y pulsa INICIAR / START"
        Write-Host "   3. Espera a que el estado este en verde"
        Write-Host "   4. Vuelve a ejecutar este script"
        try { $tcp.Close() } catch {}
        exit 3
    }

    OK "El servidor respondio correctamente."
    Write-Host ""
    INFO "Version   : $($est.version.name)"
    INFO "Protocolo : $($est.version.protocol)"
    INFO "Jugadores : $($est.players.online) / $($est.players.max)"

    # ----------------------------------------------------------
    #  Forge anade un bloque 'forgeData' al JSON de estado.
    #  Si NO esta, lo que contesta es un servidor vanilla o la
    #  sala de espera del proveedor, no tu servidor con mods.
    #  Sin esta comprobacion el script decia "ONLINE" igual.
    # ----------------------------------------------------------
    $hayForge = $json.Contains('forgeData')
    Write-Host ""
    if ($hayForge) {
        OK "Forge esta cargado (el estado incluye 'forgeData')."
    } else {
        FAIL "NO se detecto Forge en la respuesta."
        INFO "Lo que contesta es un servidor vanilla o la sala de"
        INFO "espera del proveedor. Tus 99 mods no estan cargados."
        INFO "Revisa en el panel que el software sea Forge 1.20.1 y"
        INFO "que la carpeta mods tenga los 99 .jar."
    }

    if ($est.players.sample) {
        INFO "Conectados: $(($est.players.sample | ForEach-Object { $_.name }) -join ', ')"
    }

    # MOTD puede venir como texto o como objeto con 'extra'
    $motd = ""
    if ($est.description -is [string]) { $motd = $est.description }
    elseif ($est.description.text)     { $motd = $est.description.text }
    if ($est.description.extra) {
        $motd += ($est.description.extra | ForEach-Object { $_.text }) -join ''
    }
    if ($motd) { INFO "MOTD      : $($motd -replace '§.','')" }

    # Comparar version con la del modpack
    Write-Host ""
    if ($est.version.name -match '1\.20\.1') {
        OK "La version coincide con el modpack (1.20.1)."
    } else {
        FAIL "ATENCION: el servidor NO es 1.20.1, es '$($est.version.name)'."
        INFO "El modpack es Forge 1.20.1. Corrige la version en el panel."
    }

    Write-Host ""
    Write-Host "============================================================"
    Write-Host " RESULTADO: SERVIDOR ONLINE Y ACCESIBLE" -ForegroundColor Green
    Write-Host "============================================================"
    Write-Host " Ya puedes pasarle esta direccion a tus amigos:"
    Write-Host "   ${Servidor}:${Puerto}"
    Write-Host ""
    Write-Host " El puerto es OBLIGATORIO. Sin el no conectan."
    Write-Host ""
    Write-Host " Nota: este script NO puede comprobar si online-mode esta en"
    Write-Host " false. Eso se verifica entrando con una cuenta de TLauncher."

} catch {
    Write-Host ""
    Write-Host "============================================================"
    if ($Servidor -match '(aternos|exaroton)\.me$') {
        Write-Host " RESULTADO: EL SERVIDOR ESTA APAGADO O ARRANCANDO" -ForegroundColor Yellow
        Write-Host "============================================================"
        Write-Host " El puerto responde pero no hay servidor de Minecraft detras."
        Write-Host ""
        Write-Host " Esto es NORMAL: el proveedor mantiene un proxy escuchando"
        Write-Host " en tu puerto aunque el servidor este detenido. Por eso el"
        Write-Host " puerto aparece abierto pero no contesta el protocolo."
        Write-Host ""
        Write-Host " Que hacer:"
        Write-Host "   1. Entra a $panel"
        Write-Host "   2. Abre tu servidor y pulsa INICIAR / START"
        Write-Host "   3. Espera a que el estado este en verde"
        Write-Host "   4. Vuelve a ejecutar este script"
        Write-Host ""
        Write-Host " Si YA lo iniciaste y sigue asi:"
        Write-Host "   - Con 99 mods el arranque tarda. Dale 3-5 minutos."
        Write-Host "   - O un mod esta fallando y no termina de cargar."
        Write-Host "     Revisa la CONSOLA en el panel y busca"
        Write-Host "     'Missing or unsupported mandatory dependencies'"
        Write-Host "     o 'NoClassDefFoundError'."
        Write-Host "     Pasame esa linea y te digo que mod falta."
    } else {
        Write-Host " RESULTADO: PUERTO ABIERTO PERO SIN RESPUESTA" -ForegroundColor Yellow
        Write-Host "============================================================"
        Write-Host " Algo escucha en el puerto, pero no es un servidor de"
        Write-Host " Minecraft que responda, o esta arrancando todavia."
        Write-Host ""
        INFO "Detalle tecnico: $(($_.Exception.Message -split "`n")[0])"
    }
} finally {
    try { $tcp.Close() } catch {}
}
