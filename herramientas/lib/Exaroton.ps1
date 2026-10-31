<#
    Exaroton.ps1
    ------------------------------------------------------------
    Cliente de la API de Exaroton para los comandos de ServerPanas.
    Reutiliza las credenciales de automatizacion\:
      - exaroton-token.xml : token cifrado con DPAPI (solo tu usuario lo abre)
      - config.json        : serverId
    Se carga con dot-sourcing:  . "$PSScriptRoot\lib\Exaroton.ps1"
#>

$script:RaizSP = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script:DirAuto = Join-Path $script:RaizSP "automatizacion"
$script:ExaToken = $null
$script:ExaServerId = $null

$ESTADOS_EXAROTON = @{
    0 = "Apagado"; 1 = "Encendido"; 2 = "Encendiendo"; 3 = "Apagándose"; 4 = "Reiniciando"
    5 = "Guardando"; 6 = "Cargando"; 7 = "Caído"; 8 = "Preparando"; 10 = "Preparando"
}

function Initialize-Exaroton {
    if ($script:ExaToken) { return }
    $rutaToken = Join-Path $script:DirAuto "exaroton-token.xml"
    $rutaCfg = Join-Path $script:DirAuto "config.json"
    if (-not (Test-Path $rutaToken)) {
        throw "No hay token de Exaroton. Ejecuta automatizacion\1-configurar-token.ps1"
    }
    $seguro = Import-Clixml -Path $rutaToken
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try { $script:ExaToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    $script:ExaServerId = (Get-Content $rutaCfg -Raw | ConvertFrom-Json).serverId
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
}

function Invoke-ExaApi {
    param(
        [string] $Metodo = "GET",
        [string] $Ruta = "",
        [object] $Cuerpo = $null,
        [string] $ContentType = "application/json",
        [switch] $Crudo
    )
    Initialize-Exaroton
    $uri = "https://api.exaroton.com/v1/servers/$($script:ExaServerId)/$Ruta"
    $cab = @{ Authorization = "Bearer $($script:ExaToken)" }
    if ($Crudo) {
        # Descarga binaria (archivos del servidor)
        $wc = New-Object Net.WebClient
        $wc.Headers.Add("Authorization", "Bearer $($script:ExaToken)")
        try { return $wc.DownloadData($uri) } finally { $wc.Dispose() }
    }
    $p = @{ Method = $Metodo; Uri = $uri; Headers = $cab; UseBasicParsing = $true }
    if ($null -ne $Cuerpo) { $p.Body = $Cuerpo; $p.ContentType = $ContentType }
    $r = Invoke-RestMethod @p
    if ($r.PSObject.Properties.Name -contains "success" -and -not $r.success) {
        throw "Exaroton respondió con error: $($r.error)"
    }
    return $r.data
}

function Get-ExaEstado { Invoke-ExaApi -Ruta "" }
function Start-ExaServidor { Invoke-ExaApi -Ruta "start/" | Out-Null }
function Stop-ExaServidor { Invoke-ExaApi -Ruta "stop/" | Out-Null }
function Restart-ExaServidor { Invoke-ExaApi -Ruta "restart/" | Out-Null }
function Get-ExaLog {
    # exaroton limita la frecuencia de lectura del log y responde 400 si se pide muy seguido
    for ($intento = 1; $intento -le 3; $intento++) {
        try { return (Invoke-ExaApi -Ruta "logs/").content }
        catch {
            if ($intento -eq 3 -or $_.Exception.Message -notmatch '\(400\)') { throw }
            Start-Sleep -Seconds 10
        }
    }
}

function Send-ExaComando {
    param([Parameter(Mandatory)] [string] $Comando)
    $cuerpo = @{ command = $Comando.TrimStart('/') } | ConvertTo-Json -Compress
    Invoke-ExaApi -Metodo POST -Ruta "command/" -Cuerpo $cuerpo | Out-Null
}

function Get-ExaInfoArchivo {
    param([Parameter(Mandatory)] [string] $Ruta)
    Invoke-ExaApi -Ruta ("files/info/" + $Ruta.TrimStart('/'))
}

function Get-ExaArchivo {
    param([Parameter(Mandatory)] [string] $Ruta, [Parameter(Mandatory)] [string] $Destino)
    $bytes = Invoke-ExaApi -Ruta ("files/data/" + $Ruta.TrimStart('/')) -Crudo
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destino) | Out-Null
    [IO.File]::WriteAllBytes($Destino, $bytes)
}

function Get-ExaCarpeta {
    # Descarga recursiva de una carpeta del servidor
    param([Parameter(Mandatory)] [string] $Ruta, [Parameter(Mandatory)] [string] $Destino)
    $info = Get-ExaInfoArchivo $Ruta
    if (-not $info.isDirectory) { Get-ExaArchivo $Ruta $Destino; return 1 }
    $n = 0
    foreach ($h in @($info.children)) {
        $n += Get-ExaCarpeta -Ruta "$($Ruta.TrimEnd('/'))/$($h.name)" -Destino (Join-Path $Destino $h.name)
    }
    return $n
}

function Set-ExaArchivo {
    param([Parameter(Mandatory)] [string] $Ruta, [Parameter(Mandatory)] [string] $Local)
    $bytes = [IO.File]::ReadAllBytes((Resolve-Path $Local).Path)
    Invoke-ExaApi -Metodo PUT -Ruta ("files/data/" + $Ruta.TrimStart('/')) -Cuerpo $bytes -ContentType "application/octet-stream" | Out-Null
}

function Wait-ExaEstado {
    # Espera a que el servidor llegue a un estado (1 = encendido, 0 = apagado)
    param([int] $Objetivo, [int] $TimeoutSeg = 420)
    $limite = (Get-Date).AddSeconds($TimeoutSeg)
    while ((Get-Date) -lt $limite) {
        $e = Get-ExaEstado
        if ($e.status -eq $Objetivo) { return $true }
        Write-Host ("   ... {0}" -f $ESTADOS_EXAROTON[[int]$e.status])
        Start-Sleep -Seconds 8
    }
    return $false
}
