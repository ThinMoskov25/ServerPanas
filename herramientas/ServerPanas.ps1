<#
    ServerPanas.ps1 - Centro de comandos del servidor SERVER PANAS
    ------------------------------------------------------------
    Sin argumentos abre el menú. Con argumento ejecuta una acción:

        .\ServerPanas.ps1 estado
        .\ServerPanas.ps1 comando "time set day"
        .\ServerPanas.ps1 subir-misiones -Simular

    -Si        no pide confirmación (para automatizar)
    -Simular   muestra lo que haría sin cambiar nada en el servidor
#>
param(
    [Parameter(Position = 0)] [string] $Accion = "",
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)] [string[]] $Resto = @(),
    [switch] $Si,
    [switch] $Simular
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$env:PYTHONIOENCODING = "utf-8"
. (Join-Path $PSScriptRoot "lib\Exaroton.ps1")

$RAIZ = Split-Path -Parent $PSScriptRoot
$DIR_MODS = Join-Path $RAIZ "mods-propios"
$DIR_MISIONES = Join-Path $RAIZ "misiones"
$DIR_HERR_MIS = Join-Path $PSScriptRoot "misiones"
$DIR_RESPALDOS = Join-Path $RAIZ "respaldos"
$PANEL_URL = "http://127.0.0.1:8765"
$DIRECCION = "ShadowBlack77-rEez.exaroton.me:23574"
$GRADLE = Join-Path $env:USERPROFILE "gradle-dist\gradle-8.1.1\bin\gradle.bat"
# Mods propios que corren en el servidor (GuideInjectorMod es una herramienta local)
$MODS_SERVIDOR = @("DailyQuestsMod", "EconomyMod", "InvBackupMod", "ServerGuiaMod")
$REMOTO_CAPITULOS = "config/ftbquests/quests/chapters"

# ---------------------------------------------------------------- utilidades
function Titulo([string] $t) { Write-Host ""; Write-Host "== $t ==" -ForegroundColor Cyan }
function Ok([string] $t) { Write-Host "  [OK] $t" -ForegroundColor Green }
function Aviso([string] $t) { Write-Host "  [!]  $t" -ForegroundColor Yellow }
function Fallo([string] $t) { Write-Host "  [X]  $t" -ForegroundColor Red }
function Info([string] $t) { Write-Host "       $t" }

function Confirmar([string] $pregunta) {
    if ($Si) { return $true }
    $r = Read-Host "  $pregunta (s/n)"
    return $r -match '^(s|si|sí|y|yes)$'
}

function Ejecutar([string] $descripcion, [scriptblock] $bloque) {
    if ($Simular) { Aviso "SIMULACIÓN: $descripcion"; return }
    & $bloque
}

function Get-Python {
    foreach ($c in @("python", "py")) {
        $cmd = Get-Command $c -ErrorAction SilentlyContinue
        if ($cmd -and $cmd.Source -notmatch 'WindowsApps\\python.exe$') { return $cmd.Source }
    }
    throw "No encontré Python 3. Instálalo desde python.org."
}

function Get-Jdk17 {
    $base = "C:\Program Files\Eclipse Adoptium"
    $jdk = Get-ChildItem $base -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    if (-not $jdk) { throw "No encontré el JDK 17 en $base." }
    return $jdk.FullName
}

function Get-JarMod([string] $mod) {
    Get-ChildItem (Join-Path $DIR_MODS "$mod\build\libs") -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|slim|dev)\.jar$' } |
        Sort-Object LastWriteTime | Select-Object -Last 1
}

function Mostrar-Estado {
    $e = Get-ExaEstado
    $nombre = $ESTADOS_EXAROTON[[int]$e.status]
    $color = if ($e.status -eq 1) { "Green" } elseif ($e.status -eq 0) { "DarkGray" } else { "Yellow" }
    Write-Host ("  Estado    : {0}" -f $nombre) -ForegroundColor $color
    Write-Host ("  Dirección : {0}" -f $DIRECCION)
    Write-Host ("  Software  : {0} {1}" -f $e.software.name, $e.software.version)
    $lista = @($e.players.list)
    Write-Host ("  Jugadores : {0}/{1} {2}" -f $e.players.count, $e.players.max, ($(if ($lista.Count) { "(" + ($lista -join ", ") + ")" } else { "" })))
    return $e
}

function Jugadores-Conectados {
    $e = Get-ExaEstado
    if ($e.status -ne 1) { return @() }
    return @($e.players.list)
}

# ---------------------------------------------------------------- acciones
function Accion-Estado { Titulo "Estado del servidor"; Mostrar-Estado | Out-Null }

function Accion-Encender {
    Titulo "Encender servidor"
    $e = Get-ExaEstado
    if ($e.status -eq 1) { Ok "Ya está encendido."; return }
    Ejecutar "encender el servidor" {
        Start-ExaServidor
        Info "Arrancando (Forge con 100+ mods tarda 2-4 minutos)..."
        if (Wait-ExaEstado -Objetivo 1) { Ok "Servidor encendido: $DIRECCION" } else { Aviso "Sigue arrancando; revisa con 'estado'." }
    }
}

function Accion-Apagar {
    Titulo "Apagar servidor"
    $e = Get-ExaEstado
    if ($e.status -eq 0) { Ok "Ya está apagado."; return }
    $j = Jugadores-Conectados
    if ($j.Count -and -not (Confirmar "Hay $($j.Count) jugador(es) conectados ($($j -join ', ')). ¿Apagar igualmente?")) { Aviso "Cancelado."; return }
    Ejecutar "guardar el mundo y apagar" {
        if ($j.Count) { Send-ExaComando "say El servidor se apagará en 10 segundos."; Start-Sleep 10 }
        Send-ExaComando "save-all"; Start-Sleep 3
        Stop-ExaServidor
        if (Wait-ExaEstado -Objetivo 0 -TimeoutSeg 180) { Ok "Servidor apagado." } else { Aviso "Sigue apagándose." }
    }
}

function Accion-Reiniciar {
    Titulo "Reiniciar servidor"
    $j = Jugadores-Conectados
    if ($j.Count -and -not (Confirmar "Hay $($j.Count) jugador(es) conectados. ¿Reiniciar igualmente?")) { Aviso "Cancelado."; return }
    Ejecutar "avisar, guardar y reiniciar" {
        if ($j.Count) { Send-ExaComando "say Reinicio del servidor en 15 segundos. Vuelve en 3 minutos."; Start-Sleep 15 }
        Send-ExaComando "save-all"; Start-Sleep 3
        Restart-ExaServidor
        Info "Reiniciando..."
        Start-Sleep 15
        if (Wait-ExaEstado -Objetivo 1) { Ok "Servidor de nuevo en línea." } else { Aviso "Sigue arrancando." }
    }
}

function Accion-Comando {
    Titulo "Enviar comando"
    $cmd = ($Resto -join " ").Trim()
    if (-not $cmd) { $cmd = Read-Host "  Comando (sin /)" }
    if (-not $cmd) { Aviso "Comando vacío."; return }
    if ((Get-ExaEstado).status -ne 1) { throw "El servidor no está encendido." }
    $antes = @((Get-ExaLog) -split "`n")
    Ejecutar "enviar '$cmd'" {
        Send-ExaComando $cmd
        Start-Sleep -Seconds 2
        $nuevas = @((Get-ExaLog) -split "`n") | Where-Object { $antes -notcontains $_ -and $_.Trim() }
        Ok "Enviado: /$cmd"
        $nuevas | Select-Object -Last 15 | ForEach-Object { Info $_ }
    }
}

function Accion-Log {
    Titulo "Últimas líneas del log"
    $n = if ($Resto.Count) { [int]$Resto[0] } else { 40 }
    ((Get-ExaLog) -split "`n") | Select-Object -Last $n | ForEach-Object { Write-Host "  $_" }
}

function Accion-Panel {
    Titulo "Panel web"
    try { Invoke-WebRequest "$PANEL_URL/" -UseBasicParsing -TimeoutSec 3 | Out-Null; Ok "El panel ya está en marcha." }
    catch {
        Info "Arrancando el panel (tarea programada ServerPanas-Panel)..."
        Start-ScheduledTask -TaskName "ServerPanas-Panel"
        Start-Sleep -Seconds 4
    }
    Start-Process $PANEL_URL
    Ok "Abierto: $PANEL_URL"
}

function Accion-Compilar {
    Titulo "Compilar mods propios"
    if (-not (Test-Path $GRADLE)) { throw "No encontré Gradle en $GRADLE" }
    $jdk = Get-Jdk17
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk\bin;$env:PATH"
    $lista = if ($Resto.Count) { $Resto } else { Get-ChildItem $DIR_MODS -Directory | Select-Object -ExpandProperty Name }
    $fallos = 0
    foreach ($mod in $lista) {
        $dir = Join-Path $DIR_MODS $mod
        if (-not (Test-Path (Join-Path $dir "build.gradle"))) { Fallo "$mod no es un proyecto Gradle"; $fallos++; continue }
        Write-Host "  Compilando $mod..." -NoNewline
        Push-Location $dir
        $prefPrevia = $ErrorActionPreference
        $ErrorActionPreference = "Continue"  # Gradle escribe avisos en stderr; manda el codigo de salida
        try {
            $salida = & $GRADLE build --no-daemon --console=plain --quiet 2>&1
            $ok = $LASTEXITCODE -eq 0
        } finally { $ErrorActionPreference = $prefPrevia; Pop-Location }
        if ($ok) { Write-Host " OK  -> $((Get-JarMod $mod).Name)" -ForegroundColor Green }
        else { Write-Host " FALLÓ" -ForegroundColor Red; $salida | Select-Object -Last 15 | ForEach-Object { Info $_ }; $fallos++ }
    }
    if ($fallos) { throw "$fallos mod(s) no compilaron." }
}

function Accion-DesplegarMods {
    Titulo "Desplegar mods propios en el servidor"
    $jars = foreach ($m in $MODS_SERVIDOR) {
        $j = Get-JarMod $m
        if (-not $j) { throw "Falta el .jar de $m. Ejecuta 'compilar' primero." }
        Info ("{0,-16} {1}  ({2:yyyy-MM-dd HH:mm})" -f $m, $j.Name, $j.LastWriteTime)
        $j
    }
    if (-not (Confirmar "¿Subir estos $($jars.Count) jars a mods/ del servidor?")) { Aviso "Cancelado."; return }
    Ejecutar "subir $($jars.Count) jars a mods/" {
        foreach ($j in $jars) { Set-ExaArchivo -Ruta "mods/$($j.Name)" -Local $j.FullName; Ok "Subido $($j.Name)" }
        Aviso "Los mods se cargan al reiniciar el servidor: usa 'reiniciar'."
    }
}

function Accion-Respaldo {
    Titulo "Respaldo de configuración del servidor"
    $destino = Join-Path $DIR_RESPALDOS (Get-Date -Format "yyyy-MM-dd_HHmm")
    $rutas = @("config/ftbquests/quests", "config/servereco", "world/datapacks", "server.properties", "ops.json", "whitelist.json")
    $total = 0
    foreach ($r in $rutas) {
        try {
            $n = Get-ExaCarpeta -Ruta $r -Destino (Join-Path $destino ($r -replace '/', '\'))
            Ok ("{0,-26} {1} archivo(s)" -f $r, $n); $total += $n
        } catch { Aviso "$r no se pudo descargar ($($_.Exception.Message))" }
    }
    Ok "Respaldo guardado en $destino ($total archivos)"
    return $destino
}

function Invoke-PyMisiones([string] $script, [string[]] $argumentos) {
    $py = Get-Python
    & $py -I (Join-Path $DIR_HERR_MIS $script) @argumentos | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$script terminó con errores." }
}

function Get-IndiceItems {
    $idx = Join-Path $DIR_HERR_MIS "items_reales.json"
    $mods = Join-Path $RAIZ "cliente\mods"
    $ultimoJar = (Get-ChildItem $mods -Filter *.jar | Sort-Object LastWriteTime | Select-Object -Last 1).LastWriteTime
    if (-not (Test-Path $idx) -or (Get-Item $idx).LastWriteTime -lt $ultimoJar) {
        Info "Generando índice de ítems desde los .jar del cliente..."
        Invoke-PyMisiones "items_jars.py" @($mods, $idx)
    }
    return $idx
}

function Accion-ValidarMisiones {
    Titulo "Validar misiones"
    $idx = Get-IndiceItems
    Invoke-PyMisiones "verificar.py" @((Join-Path $DIR_MISIONES "capitulos"), $idx, (Join-Path $DIR_MISIONES "guia-mods"))
}

function Accion-GenerarMisiones {
    Titulo "Generar misiones desde misiones\textos"
    $cap = Join-Path $DIR_MISIONES "capitulos"
    Invoke-PyMisiones "aplicar.py" @($cap, (Join-Path $DIR_MISIONES "textos"), $cap)
    Invoke-PyMisiones "vista_previa.py" @($cap, (Join-Path $DIR_MISIONES "vista-previa.html"))
    Accion-ValidarMisiones
}

function Accion-SubirMisiones {
    Titulo "Subir misiones al servidor"
    Accion-ValidarMisiones
    $archivos = Get-ChildItem (Join-Path $DIR_MISIONES "capitulos") -Filter "*.snbt" | Sort-Object Name
    Info "$($archivos.Count) capítulos listos: $($archivos[0].BaseName) ... $($archivos[-1].BaseName)"
    if (-not (Confirmar "¿Respaldar las misiones actuales del servidor y subir estas?")) { Aviso "Cancelado."; return }
    Ejecutar "respaldar y subir $($archivos.Count) capítulos" {
        $resp = Join-Path $DIR_RESPALDOS ("misiones_" + (Get-Date -Format "yyyy-MM-dd_HHmm"))
        $n = Get-ExaCarpeta -Ruta $REMOTO_CAPITULOS -Destino $resp
        Ok "Respaldo previo: $n archivos en $resp"
        foreach ($a in $archivos) { Set-ExaArchivo -Ruta "$REMOTO_CAPITULOS/$($a.Name)" -Local $a.FullName }
        Ok "Subidos $($archivos.Count) capítulos."
        # Copias locales coherentes (cliente y servidor de referencia)
        foreach ($d in @("cliente\config\ftbquests\quests\chapters", "servidor\config\ftbquests\quests\chapters")) {
            $dest = Join-Path $RAIZ $d
            if (Test-Path $dest) { $archivos | Copy-Item -Destination $dest -Force }
        }
        Ok "Copias locales de cliente\ y servidor\ actualizadas."
        if ((Get-ExaEstado).status -eq 1) { Send-ExaComando "ftbquests reload"; Ok "Aplicado en vivo con /ftbquests reload." }
        else { Info "El servidor está apagado: las misiones se cargan al encenderlo." }
    }
}

function Accion-Validar {
    Titulo "Validar el pack"
    & (Join-Path $PSScriptRoot "validacion\validar-dependencias.ps1")
    Accion-ValidarMisiones
}

function Accion-Empaquetar {
    Titulo "Empaquetar para los amigos"
    & (Join-Path $PSScriptRoot "pack\crear-modpack-curseforge.ps1")
    & (Join-Path $PSScriptRoot "pack\empaquetar-para-amigos.ps1")
}

function Accion-Probar {
    & (Join-Path $PSScriptRoot "servidor\probar-servidor.ps1")
}

function Invoke-AuthPanel([string[]] $argumentos) {
    $py = Get-Python
    & $py -I (Join-Path $RAIZ "panel\auth.py") @argumentos
    return $LASTEXITCODE
}

function Accion-UsuariosPanel {
    Titulo "Usuarios del panel web"
    Invoke-AuthPanel @("listar") | Out-Null
    if ($Resto.Count) { $op = $Resto[0] } else {
        Write-Host ""
        Write-Host "   1) Crear o cambiar contraseña de un usuario"
        Write-Host "   2) Borrar un usuario"
        Write-Host "   0) Volver"
        $op = Read-Host "  Opción"
    }
    switch ($op) {
        { $_ -in "1", "crear" } {
            $nombre = if ($Resto.Count -gt 1) { $Resto[1] } else { Read-Host "  Nombre de usuario" }
            $rol = if ($Resto.Count -gt 2) { $Resto[2] } else { Read-Host "  Rol: admin (todo) o colaborador (sin archivos ni ajustes) [colaborador]" }
            if (-not $rol) { $rol = "colaborador" }
            if ((Invoke-AuthPanel @("crear", $nombre, $rol)) -ne 0) { throw "No se creó el usuario." }
        }
        { $_ -in "2", "borrar" } {
            $nombre = if ($Resto.Count -gt 1) { $Resto[1] } else { Read-Host "  Usuario a borrar" }
            if ((Invoke-AuthPanel @("borrar", $nombre)) -ne 0) { throw "No se borró el usuario." }
        }
    }
}

function Get-Cloudflared {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @("$env:LOCALAPPDATA\Microsoft\WinGet\Links\cloudflared.exe",
                     "${env:ProgramFiles}\cloudflared\cloudflared.exe",
                     "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe",
                     (Join-Path $PSScriptRoot "bin\cloudflared.exe"))) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Accion-PublicarPanel {
    Titulo "Publicar el panel en Internet"
    $usuarios = Join-Path $RAIZ "panel\datos\usuarios.json"
    if (-not (Test-Path $usuarios) -or -not ((Get-Content $usuarios -Raw | ConvertFrom-Json).usuarios.PSObject.Properties.Name)) {
        throw "El panel no tiene usuarios. Crea al menos uno con 'usuarios-panel' antes de publicarlo."
    }
    # Enlace fijo (ngrok) si ya esta configurado: solo hay que asegurarse de que el tunel corre
    $tunelFijo = Join-Path $RAIZ "panel\datos\tunel.json"
    if (Test-Path $tunelFijo) {
        $t = Get-Content $tunelFijo -Raw | ConvertFrom-Json
        $tarea = Get-ScheduledTask -TaskName "ServerPanas-Tunel" -ErrorAction SilentlyContinue
        if (-not $tarea) { throw "Falta la tarea ServerPanas-Tunel. Ejecuta 'configurar-enlace'." }
        if ($tarea.State -ne "Running") { Start-ScheduledTask -TaskName "ServerPanas-Tunel"; Info "Arrancando el túnel..."; Start-Sleep 6 }
        try {
            Invoke-WebRequest "$($t.url)/login" -UseBasicParsing -TimeoutSec 20 -Headers @{ "ngrok-skip-browser-warning" = "1" } | Out-Null
            Ok "El panel está publicado y responde desde Internet."
        } catch { Aviso "El túnel está arrancado pero aún no responde: $($_.Exception.Message)" }
        Write-Host ""
        Write-Host "   ENLACE FIJO DEL PANEL:  $($t.url)" -ForegroundColor Green
        try { Set-Clipboard -Value $t.url; Info "(copiado al portapapeles)" } catch {}
        Info "Funciona mientras tu PC esté encendido; arranca solo al iniciar sesión en Windows."
        return
    }
    $cf = Get-Cloudflared
    if (-not $cf) { throw "No encontré cloudflared. Instálalo con:  winget install Cloudflare.cloudflared" }
    try { Invoke-WebRequest "$PANEL_URL/login" -UseBasicParsing -TimeoutSec 3 | Out-Null }
    catch { Info "Arrancando el panel..."; Start-ScheduledTask -TaskName "ServerPanas-Panel"; Start-Sleep -Seconds 5 }
    $logTunel = Join-Path $env:TEMP "serverpanas-tunel.log"
    Remove-Item $logTunel -ErrorAction SilentlyContinue
    Ejecutar "abrir el túnel de Cloudflare hacia $PANEL_URL" {
        $proc = Start-Process -FilePath $cf -ArgumentList @("tunnel", "--no-autoupdate", "--url", $PANEL_URL) `
            -RedirectStandardError $logTunel -WindowStyle Hidden -PassThru
        $url = $null
        for ($i = 0; $i -lt 40 -and -not $url; $i++) {
            Start-Sleep -Seconds 1
            if ($proc.HasExited) { break }
            if (Test-Path $logTunel) {
                $m = Select-String -Path $logTunel -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
                if ($m) { $url = $m.Matches[0].Value }
            }
        }
        if (-not $url) {
            if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
            throw "Cloudflare no devolvió un enlace. Revisa $logTunel"
        }
        Write-Host ""
        Write-Host "  ============================================================" -ForegroundColor Green
        Write-Host "   ENLACE DEL PANEL:  $url" -ForegroundColor Green
        Write-Host "  ============================================================" -ForegroundColor Green
        try { Set-Clipboard -Value $url; Info "(copiado al portapapeles)" } catch {}
        Info "Pásale el enlace y su usuario a tu compañero. Entrará con su contraseña."
        Info "El enlace cambia cada vez que publicas el panel."
        Aviso "Mientras esta ventana siga abierta el panel es accesible. Ciérrala (o pulsa Ctrl+C) para cortar el acceso."
        try { Wait-Process -Id $proc.Id } finally { if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force } }
        Ok "Túnel cerrado: el panel vuelve a ser solo local."
    }
}

$ACCIONES = [ordered]@{
    "estado"          = @{ f = { Accion-Estado };           d = "Ver estado, versión y jugadores conectados" }
    "encender"        = @{ f = { Accion-Encender };         d = "Encender el servidor y esperar a que esté listo" }
    "apagar"          = @{ f = { Accion-Apagar };           d = "Avisar, guardar el mundo y apagar" }
    "reiniciar"       = @{ f = { Accion-Reiniciar };        d = "Avisar, guardar y reiniciar" }
    "comando"         = @{ f = { Accion-Comando };          d = "Enviar un comando de consola" }
    "log"             = @{ f = { Accion-Log };              d = "Ver las últimas líneas del log" }
    "panel"           = @{ f = { Accion-Panel };            d = "Abrir el panel web de administración" }
    "respaldo"        = @{ f = { Accion-Respaldo | Out-Null }; d = "Descargar misiones, economía, datapacks y configs" }
    "compilar"        = @{ f = { Accion-Compilar };         d = "Compilar los mods propios (Gradle + JDK 17)" }
    "desplegar-mods"  = @{ f = { Accion-DesplegarMods };    d = "Subir los mods propios compilados al servidor" }
    "validar"         = @{ f = { Accion-Validar };          d = "Validar dependencias de mods y misiones" }
    "generar-misiones"= @{ f = { Accion-GenerarMisiones };  d = "Regenerar capítulos desde misiones\textos" }
    "subir-misiones"  = @{ f = { Accion-SubirMisiones };    d = "Respaldar y subir los capítulos al servidor" }
    "empaquetar"      = @{ f = { Accion-Empaquetar };       d = "Generar los zips para repartir a los amigos" }
    "probar"          = @{ f = { Accion-Probar };           d = "Probar la conexión al servidor desde fuera" }
    "usuarios-panel"  = @{ f = { Accion-UsuariosPanel };    d = "Crear o borrar usuarios del panel web" }
    "publicar-panel"  = @{ f = { Accion-PublicarPanel };    d = "Mostrar o abrir el enlace HTTPS del panel" }
    "configurar-enlace" = @{ f = { & (Join-Path $PSScriptRoot "servidor\configurar-ngrok.ps1") }; d = "Configurar el enlace fijo con ngrok (una sola vez)" }
}

function Mostrar-Ayuda {
    Write-Host ""
    Write-Host "  Uso: SERVERPANAS.bat <acción> [-Si] [-Simular]" -ForegroundColor Cyan
    foreach ($k in $ACCIONES.Keys) { Write-Host ("    {0,-17} {1}" -f $k, $ACCIONES[$k].d) }
}

function Ejecutar-Accion([string] $nombre) {
    if (-not $ACCIONES.Contains($nombre)) { Fallo "Acción desconocida: $nombre"; Mostrar-Ayuda; return $false }
    try { & $ACCIONES[$nombre].f | Out-Host; return $true }
    catch { Fallo $_.Exception.Message; return $false }
}

function Mostrar-Menu {
    while ($true) {
        Clear-Host
        Write-Host "============================================================" -ForegroundColor DarkCyan
        Write-Host "   SERVER PANAS  -  Centro de comandos" -ForegroundColor Cyan
        Write-Host "   Minecraft 1.20.1 Forge  |  $DIRECCION" -ForegroundColor DarkGray
        Write-Host "============================================================" -ForegroundColor DarkCyan
        $claves = @($ACCIONES.Keys)
        $grupos = [ordered]@{ "SERVIDOR" = 0..6; "CONTENIDO" = 7..12; "DISTRIBUCIÓN" = 13..14; "ACCESO AL PANEL" = 15..17 }
        foreach ($g in $grupos.Keys) {
            Write-Host ""; Write-Host "  $g" -ForegroundColor DarkYellow
            foreach ($i in $grupos[$g]) { Write-Host ("   {0,2}) {1,-17} {2}" -f ($i + 1), $claves[$i], $ACCIONES[$claves[$i]].d) }
        }
        Write-Host ""; Write-Host "    0) Salir"
        $op = Read-Host "`n  Elige una opción"
        if ($op -eq "0" -or $op -eq "") { return }
        $idx = 0
        if ([int]::TryParse($op, [ref]$idx) -and $idx -ge 1 -and $idx -le $claves.Count) { Ejecutar-Accion $claves[$idx - 1] | Out-Null }
        elseif ($ACCIONES.Contains($op)) { Ejecutar-Accion $op | Out-Null }
        else { Fallo "Opción no válida." }
        Read-Host "`n  Pulsa Enter para volver al menú" | Out-Null
    }
}

if ($Accion) {
    if ($Accion -in @("ayuda", "help", "-h", "/?")) { Mostrar-Ayuda; exit 0 }
    $ok = Ejecutar-Accion $Accion.ToLower()
    exit ([int](-not $ok))
}
Mostrar-Menu
