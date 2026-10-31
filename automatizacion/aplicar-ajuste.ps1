<#
    aplicar-ajuste.ps1
    --------------------------------------------------------------
    Sube un archivo local al servidor Exaroton y (opcional) ejecuta
    un comando de consola para aplicar el cambio en vivo.

    El token se lee CIFRADO (DPAPI) desde exaroton-token.xml.
    Nunca se escribe en texto plano.

    Ejemplos:
        # Subir la mision corregida y recargar FTB Quests
        .\aplicar-ajuste.ps1 `
            -LocalFile "..\misiones\capitulos\01_primeros_pasos.snbt" `
            -RemotePath "config/ftbquests/quests/chapters/01_primeros_pasos.snbt" `
            -Reload

        # Subir cualquier archivo sin recargar
        .\aplicar-ajuste.ps1 -LocalFile "ruta\archivo" -RemotePath "config/.../archivo"

        # Enviar solo un comando a la consola
        .\aplicar-ajuste.ps1 -SoloComando "ftbquests reload"
#>

param(
    [string] $LocalFile,
    [string] $RemotePath,
    [switch] $Reload,
    [string] $ComandoReload = "ftbquests reload",
    [string] $SoloComando
)

$ErrorActionPreference = "Stop"
$carpeta    = $PSScriptRoot
$rutaToken  = Join-Path $carpeta "exaroton-token.xml"
$rutaConfig = Join-Path $carpeta "config.json"

. (Join-Path $carpeta "exaroton-lib.ps1")

$token    = Get-ExarotonToken  -RutaToken  $rutaToken
$config   = Get-ExarotonConfig -RutaConfig $rutaConfig
$serverId = $config.serverId

try {
    # Chequeo de estado (informativo)
    try {
        $status = Get-ServerStatus -Token $token -ServerId $serverId
        Write-Host "Servidor: $($status.name)  |  estado: $($status.status)" -ForegroundColor Cyan
    } catch {
        Write-Host "Aviso: no se pudo leer el estado del servidor (continuo igual)." -ForegroundColor Yellow
    }

    # Modo: solo comando
    if ($SoloComando) {
        Write-Host "Enviando comando: $SoloComando" -ForegroundColor Cyan
        Invoke-ServerCommand -Token $token -ServerId $serverId -Command $SoloComando
        Write-Host "Comando enviado." -ForegroundColor Green
        return
    }

    # Modo: subir archivo
    if (-not $LocalFile -or -not $RemotePath) {
        throw "Faltan -LocalFile y/o -RemotePath (o usa -SoloComando)."
    }

    $rutaAbs = Resolve-Path $LocalFile
    Write-Host "Subiendo: $rutaAbs" -ForegroundColor Cyan
    Write-Host "   ->  $RemotePath" -ForegroundColor Cyan
    Write-ServerFile -Token $token -ServerId $serverId -RemotePath $RemotePath -LocalFile $rutaAbs
    Write-Host "Archivo subido correctamente." -ForegroundColor Green

    if ($Reload) {
        Write-Host "Ejecutando: $ComandoReload" -ForegroundColor Cyan
        Invoke-ServerCommand -Token $token -ServerId $serverId -Command $ComandoReload
        Write-Host "Recarga enviada. El cambio deberia estar en vivo." -ForegroundColor Green
    } else {
        Write-Host "Nota: no se recargo. Si hace falta, corre con -Reload o envia el comando manualmente." -ForegroundColor Yellow
    }
}
finally {
    # Limpia el token de memoria
    if ($token) { $token = $null; [System.GC]::Collect() }
}
