<#
    publicar.ps1 - Publica la pagina de encendido 24/7 en Cloudflare (gratis)
    ------------------------------------------------------------------------
    1. Si hace falta, abre Cloudflare para que pulses "Allow" (una sola vez).
    2. Publica el Worker y le pone los secretos: token de exaroton (sale del
       almacen cifrado de este PC, no se muestra) y una clave de enlace nueva.
    3. Guarda el enlace en panel\datos\nube.json para que el panel lo muestre.

    Uso:  .\publicar.ps1            (publica o actualiza conservando el enlace)
          .\publicar.ps1 -NuevoEnlace   (cambia el enlace: el anterior deja de funcionar)
#>
param([switch] $NuevoEnlace)

$ErrorActionPreference = "Stop"
$RAIZ = Split-Path -Parent $PSScriptRoot
$ARCH_NUBE = Join-Path $RAIZ "panel\datos\nube.json"
. (Join-Path $RAIZ "herramientas\lib\Exaroton.ps1")
Set-Location $PSScriptRoot

# Version de wrangler fijada; se llama siempre directamente (sin funcion envoltorio)
$WR = "wrangler@4.156.0"
function Paso([string] $t) { Write-Host ""; Write-Host "== $t ==" -ForegroundColor Cyan }

Paso "Cuenta de Cloudflare"
$prev = $ErrorActionPreference; $ErrorActionPreference = "Continue"
$quien = (npx -y $WR whoami 2>&1 | Out-String)
$ErrorActionPreference = $prev
if ($quien -notmatch 'associated with the email') {
    Write-Host "  Se abrira Cloudflare en tu navegador: pulsa ALLOW." -ForegroundColor Yellow
    npx -y $WR login
    if ($LASTEXITCODE -ne 0) { throw "No se completo la autorizacion de Cloudflare. Vuelve a intentarlo y pulsa Allow." }
}
Write-Host "  [OK] Conectado a Cloudflare." -ForegroundColor Green

Paso "Publicando la pagina"
$prev = $ErrorActionPreference; $ErrorActionPreference = "Continue"
$salida = (npx -y $WR deploy 2>&1 | Out-String)
$codigo = $LASTEXITCODE
$ErrorActionPreference = $prev
Write-Host $salida
if ($codigo -ne 0) {
    if ($salida -match 'workers\.dev subdomain') {
        Start-Process "https://dash.cloudflare.com/?to=/:account/workers-and-pages"
        throw "Tu cuenta aun no tiene subdominio workers.dev. Te abri Cloudflare: entra en Workers & Pages, elige un nombre de subdominio y vuelve a ejecutar esto."
    }
    throw "La publicacion fallo (mira el mensaje de arriba)."
}
$m = [regex]::Match($salida, 'https://[a-z0-9.-]+\.workers\.dev')
if (-not $m.Success) { throw "No encontre la URL publicada en la salida de wrangler." }
$base = $m.Value

Paso "Secretos"
$previo = if (Test-Path $ARCH_NUBE) { Get-Content $ARCH_NUBE -Raw | ConvertFrom-Json } else { $null }
$clave = if ($previo -and $previo.clave -and -not $NuevoEnlace) { $previo.clave } else {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    [Convert]::ToBase64String($bytes).Replace('+', 'A').Replace('/', 'B').TrimEnd('=')
}
Initialize-Exaroton
$prev = $ErrorActionPreference; $ErrorActionPreference = "Continue"
foreach ($s in @(@('EXAROTON_TOKEN', $script:ExaToken), @('EXAROTON_SERVER_ID', $script:ExaServerId), @('CLAVE_ENLACE', $clave))) {
    $r = ($s[1] | npx -y $WR secret put $s[0] 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { $ErrorActionPreference = $prev; throw "No se pudo guardar el secreto $($s[0]): $r" }
}
$ErrorActionPreference = $prev
Write-Host "  [OK] Secretos guardados en Cloudflare." -ForegroundColor Green

$enlace = "$base/$clave"
@{ url = $enlace; base = $base; clave = $clave; publicado = (Get-Date -Format s) } | ConvertTo-Json |
    Set-Content $ARCH_NUBE -Encoding UTF8

Paso "Comprobando"
Start-Sleep 5
$e = Invoke-RestMethod "$enlace/estado" -TimeoutSec 30
Write-Host "  [OK] Responde desde Cloudflare. Servidor: $($e.texto)" -ForegroundColor Green
Write-Host ""
Write-Host "  ENLACE PARA TUS AMIGOS:  $enlace" -ForegroundColor Green
try { Set-Clipboard -Value $enlace; Write-Host "  (copiado al portapapeles; tambien lo ves en el panel, en Acceso y usuarios)" } catch {}
