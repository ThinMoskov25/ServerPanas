<#
    1-configurar-token.ps1
    --------------------------------------------------------------
    Guarda tu token de Exaroton CIFRADO con DPAPI (ligado a tu
    usuario de Windows). Solo TU cuenta en ESTA maquina puede
    descifrarlo. El token nunca se guarda en texto plano.

    Ejecutalo UNA sola vez (o cuando regeneres el token).

    Uso:
        powershell -ExecutionPolicy Bypass -File .\1-configurar-token.ps1
#>

$ErrorActionPreference = "Stop"
$carpeta    = $PSScriptRoot
$rutaToken  = Join-Path $carpeta "exaroton-token.xml"
$rutaConfig = Join-Path $carpeta "config.json"

Write-Host ""
Write-Host "=== Configuracion de credenciales Exaroton ===" -ForegroundColor Cyan
Write-Host "El token se cifra con DPAPI y queda ligado a tu usuario de Windows."
Write-Host "No se guarda en texto plano ni se muestra en pantalla."
Write-Host ""

# --- Token (entrada oculta con verificacion de longitud) ---
# Nota: pegar en Read-Host -AsSecureString a veces falla silenciosamente.
# Por eso leemos, verificamos longitud y confirmamos antes de guardar.
$tokenSeguro = Read-Host -AsSecureString "Pega tu API token de Exaroton y pulsa Enter"

# Medir longitud real del texto capturado (en memoria, sin mostrarlo)
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tokenSeguro)
try {
    $largo = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Length
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

if ($largo -lt 20) {
    Write-Host ""
    Write-Host "ERROR: solo se capturaron $largo caracteres." -ForegroundColor Red
    Write-Host "El token de Exaroton es largo (unas 90 letras/numeros)." -ForegroundColor Yellow
    Write-Host "Es probable que el pegado no funcionara en esta terminal." -ForegroundColor Yellow
    Write-Host "Vuelve a ejecutar el script y pega con Ctrl+V (no clic derecho)." -ForegroundColor Yellow
    exit 1
}

Write-Host "Token capturado ($largo caracteres)." -ForegroundColor Green

# Cifra con DPAPI (CurrentUser) y lo guarda
$tokenSeguro | Export-Clixml -Path $rutaToken
Write-Host "Token cifrado guardado en: $rutaToken" -ForegroundColor Green

# --- Server ID (no es secreto, va en config.json) ---
$serverId = Read-Host "Ingresa el Server ID de tu servidor (ej: ABC123xyz)"
if ([string]::IsNullOrWhiteSpace($serverId)) {
    Write-Host "No se ingreso Server ID. Abortando." -ForegroundColor Red
    exit 1
}

@{ serverId = $serverId.Trim() } | ConvertTo-Json | Set-Content -Path $rutaConfig -Encoding UTF8
Write-Host "Server ID guardado en: $rutaConfig" -ForegroundColor Green

Write-Host ""
Write-Host "Listo. Ya puedes usar aplicar-ajuste.ps1" -ForegroundColor Cyan
Write-Host "Recuerda: si el token se filtro alguna vez, regeneralo en Exaroton y vuelve a correr este script." -ForegroundColor Yellow
