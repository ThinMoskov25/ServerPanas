<#
    exaroton-lib.ps1
    --------------------------------------------------------------
    Funciones compartidas para hablar con la API de Exaroton.
    No se ejecuta sola; la cargan los otros scripts con dot-sourcing.
#>

$ErrorActionPreference = "Stop"

function Get-ExarotonToken {
    param([Parameter(Mandatory)] [string] $RutaToken)

    if (-not (Test-Path $RutaToken)) {
        throw "No existe el archivo de token ($RutaToken). Ejecuta primero 1-configurar-token.ps1"
    }
    $secure = Import-Clixml -Path $RutaToken
    # Descifra en memoria solo para construir el header
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Get-ExarotonConfig {
    param([Parameter(Mandatory)] [string] $RutaConfig)

    if (-not (Test-Path $RutaConfig)) {
        throw "No existe config.json ($RutaConfig). Ejecuta primero 1-configurar-token.ps1"
    }
    return Get-Content $RutaConfig -Raw | ConvertFrom-Json
}

function Get-ApiBase {
    param([Parameter(Mandatory)] [string] $ServerId)
    return "https://api.exaroton.com/v1/servers/$ServerId"
}

function Get-ServerStatus {
    param([string] $Token, [string] $ServerId)
    $headers = @{ Authorization = "Bearer $Token" }
    $r = Invoke-RestMethod -Method Get -Uri (Get-ApiBase $ServerId) -Headers $headers
    return $r.data
}

function Write-ServerFile {
    param(
        [string] $Token,
        [string] $ServerId,
        [string] $RemotePath,   # ej: config/ftbquests/quests/chapters/01_primeros_pasos.snbt
        [string] $LocalFile
    )
    if (-not (Test-Path $LocalFile)) { throw "No existe el archivo local: $LocalFile" }

    $headers = @{ Authorization = "Bearer $Token" }
    $uri = "$(Get-ApiBase $ServerId)/files/data/$RemotePath"
    # Leer el archivo como bytes y enviarlo como cuerpo (compatible con PS 5.1)
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $LocalFile).Path)
    Invoke-RestMethod -Method Put -Uri $uri -Headers $headers `
        -ContentType "application/octet-stream" -Body $bytes | Out-Null
}

function Invoke-ServerCommand {
    param([string] $Token, [string] $ServerId, [string] $Command)
    $headers = @{
        Authorization  = "Bearer $Token"
        "Content-Type" = "application/json"
    }
    $body = @{ command = $Command } | ConvertTo-Json
    $uri = "$(Get-ApiBase $ServerId)/command/"
    Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -Body $body | Out-Null
}
