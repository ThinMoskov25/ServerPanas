# ============================================================
#  Instala Forge 1.20.1 SIN ventanas ni clics.
#
#  El problema que resuelve: el instalador de Forge es un .jar que
#  necesita Java 17, y el pana puede no tener Java, o tener el 8
#  (como pasa a menudo). Si falta Java, el .jar ni arranca.
#
#  Por eso este script:
#    1. Busca un Java 17+ ya presente (el runtime del propio
#       launcher de Minecraft suele tenerlo).
#    2. Si no lo encuentra, descarga un Java 17 PORTATIL de
#       Adoptium (un zip, no se instala nada en el sistema).
#    3. Corre el instalador de Forge en modo --installClient, que
#       NO abre ventana: instala y crea el perfil directamente.
#    4. Borra el Java portatil y el instalador al terminar.
#
#  No toca el Java del sistema. No deja rastro. No pide nada.
#
#  Codigos de salida:
#    0 = Forge instalado y validado
#    1 = no se pudo conseguir Java
#    2 = fallo la descarga del instalador de Forge
#    3 = el instalador de Forge fallo
#    4 = termino pero no quedo una version valida
#
#  USO:
#    .\_instalar-forge.ps1
#    .\_instalar-forge.ps1 -ForgeVer 47.4.0 -MinForge 47.4.0 -MinecraftDir "C:\...\.minecraft"
# ============================================================

param(
    [string]$ForgeVer = "47.4.0",
    [string]$MinForge = "47.4.0",
    [string]$MinecraftDir = ""
)

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"

function OK($t)   { Write-Host "    [OK]    $t" -ForegroundColor Green }
function FAIL($t) { Write-Host "    [FALLO] $t" -ForegroundColor Red }
function INFO($t) { Write-Host "    $t" }

if (-not $MinecraftDir) { $MinecraftDir = Join-Path $env:APPDATA ".minecraft" }
$min = [version]$MinForge

$forgeUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-$ForgeVer/forge-1.20.1-$ForgeVer-installer.jar"
$javaUrl  = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"

# carpeta de trabajo temporal propia, se borra al final
$tmp = Join-Path $env:TEMP ("serverpanas-forge-" + [guid]::NewGuid().ToString("N").Substring(0,8))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Limpiar {
    try { if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue } } catch {}
}

Write-Host "============================================================"
Write-Host " INSTALANDO FORGE $ForgeVer  (sin ventanas)"
Write-Host "============================================================"

# launcher_profiles.json tiene que existir para que Forge cree el perfil
$lp = Join-Path $MinecraftDir "launcher_profiles.json"
if (-not (Test-Path $lp)) {
    New-Item -ItemType Directory -Force -Path $MinecraftDir | Out-Null
    '{"profiles":{},"settings":{},"version":3}' | Set-Content $lp -Encoding UTF8
    INFO "Cree un launcher_profiles.json vacio (no existia)."
}

# ---------- 1. Conseguir Java 17+ ----------
Write-Host ""
Write-Host "--- 1) Buscando Java 17" -ForegroundColor Cyan
$java = $null

function VersionDeJava($exe) {
    # "java" (del PATH) no es una ruta de archivo; para los demas,
    # exigir que el .exe exista de verdad antes de ejecutarlo.
    if ($exe -ne "java" -and -not (Test-Path $exe)) { return 0 }

    # IMPORTANTE: java escribe la version en STDERR. Si se ejecuta
    # con el operador '&', con $ErrorActionPreference='Stop' (activo
    # en este script) ese stderr se convierte en excepcion y la
    # lectura devuelve 0 SIEMPRE, aunque el java sea correcto.
    # 'cmd /c "... 2>&1"' redirige stderr a stdout a nivel de cmd,
    # asi PowerShell recibe texto plano y no dispara la excepcion.
    $s = (cmd /c "`"$exe`" -version 2>&1") | Out-String

    if ($s -match 'version "(\d+)\.(\d+)') {
        $a = [int]$Matches[1]; $b = [int]$Matches[2]
        # "1.8" -> major real 8 ; "17.0" -> major 17
        if ($a -eq 1) { return $b } else { return $a }
    }
    if ($s -match 'version "(\d+)"') { return [int]$Matches[1] }
    return 0
}

# 1a. el runtime del launcher de Minecraft (varias rutas posibles)
$candidatos = @()
$rutasRuntime = @(
    (Join-Path $env:APPDATA ".minecraft\runtime"),
    "$env:LOCALAPPDATA\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime",
    "C:\Program Files (x86)\Minecraft Launcher\runtime",
    "C:\Program Files\Minecraft Launcher\runtime"
)
foreach ($r in $rutasRuntime) {
    if (Test-Path $r) {
        $candidatos += Get-ChildItem $r -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    }
}
# 1b. el java del PATH, por si acaso
$candidatos += "java"

foreach ($c in $candidatos) {
    $v = VersionDeJava $c
    if ($v -ge 17) { $java = $c; break }
}

if ($java) {
    OK "Java 17+ encontrado, lo reutilizo (no descargo nada)."
    INFO ($java -replace [regex]::Escape($env:USERNAME),'<usuario>')
} else {
    INFO "No hay Java 17 en el sistema. Descargo uno portatil de Adoptium."
    INFO "Son ~42 MB. No se instala nada: se usa y se borra."
    $jzip = Join-Path $tmp "java17.zip"
    try {
        Invoke-WebRequest -Uri $javaUrl -OutFile $jzip -UseBasicParsing -TimeoutSec 600
    } catch {
        FAIL "No se pudo descargar Java. Revisa tu conexion."
        INFO $_.Exception.Message
        Limpiar; exit 1
    }

    # comprobar que el zip llego completo (Adoptium ronda los 40 MB)
    $zipMB = [math]::Round((Get-Item $jzip).Length/1MB,1)
    if ($zipMB -lt 20) {
        FAIL "El zip de Java llego incompleto ($zipMB MB). Reintenta."
        Limpiar; exit 1
    }

    $jdir = Join-Path $tmp "java"
    New-Item -ItemType Directory -Force -Path $jdir | Out-Null
    # Extraer con .NET en vez de Expand-Archive: tolera mejor los
    # nombres con '+' y las rutas largas del JDK de Adoptium.
    $extraido = $false
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($jzip, $jdir)
        $extraido = $true
    } catch {
        # segundo intento con Expand-Archive por si el .NET fallo
        try { Expand-Archive -Path $jzip -DestinationPath $jdir -Force; $extraido = $true } catch {}
    }
    if (-not $extraido) {
        FAIL "No se pudo descomprimir Java."
        INFO "Puede ser una ruta demasiado larga. Prueba a mover la"
        INFO "carpeta del instalador mas cerca de la raiz, ej: C:\\SP"
        Limpiar; exit 1
    }

    $java = Get-ChildItem $jdir -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName

    $verJava = 0
    if ($java) { $verJava = VersionDeJava $java }
    if (-not $java) {
        FAIL "Se descomprimio Java pero no encuentro java.exe dentro."
        INFO "Contenido de la carpeta extraida:"
        Get-ChildItem $jdir -Directory -ErrorAction SilentlyContinue |
            Select-Object -First 3 | ForEach-Object { INFO ("  " + $_.Name) }
        Limpiar; exit 1
    }
    if ($verJava -lt 17) {
        FAIL "El java.exe extraido no responde con version 17 (leido: $verJava)."
        INFO "Ruta: $java"
        Limpiar; exit 1
    }
    OK "Java 17 portatil listo (version $verJava)."
}

# ---------- 2. Descargar el instalador de Forge ----------
Write-Host ""
Write-Host "--- 2) Descargando el instalador de Forge $ForgeVer" -ForegroundColor Cyan
$finst = Join-Path $tmp "forge-installer.jar"
try {
    Invoke-WebRequest -Uri $forgeUrl -OutFile $finst -UseBasicParsing -TimeoutSec 600
    OK "Descargado ($([math]::Round((Get-Item $finst).Length/1MB,1)) MB)."
} catch {
    FAIL "No se pudo descargar el instalador de Forge."
    INFO $_.Exception.Message
    Limpiar; exit 2
}

# ---------- 3. Instalar en silencio ----------
Write-Host ""
Write-Host "--- 3) Instalando (modo silencioso, sin ventana)" -ForegroundColor Cyan
INFO "Esto baja las librerias de Forge, puede tardar 1-3 minutos..."
$outLog = Join-Path $tmp "forge-out.log"
$errLog = Join-Path $tmp "forge-err.log"
$proc = Start-Process -FilePath $java `
    -ArgumentList "-jar","`"$finst`"","--installClient","`"$MinecraftDir`"" `
    -WorkingDirectory $tmp -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog
if ($proc.ExitCode -ne 0) {
    FAIL "El instalador de Forge devolvio un error (codigo $($proc.ExitCode))."
    Get-Content $errLog -ErrorAction SilentlyContinue | Select-Object -Last 5 | ForEach-Object { INFO $_ }
    Limpiar; exit 3
}
OK "Instalador terminado."

# ---------- 4. Validar que quedo una version >= minimo ----------
Write-Host ""
Write-Host "--- 4) Comprobando el resultado" -ForegroundColor Cyan
$versiones = Join-Path $MinecraftDir "versions"
$mejor = $null
if (Test-Path $versiones) {
    $mejor = Get-ChildItem $versiones -Directory |
             Where-Object { $_.Name -match '^1\.20\.1-forge-(\d+\.\d+\.\d+)$' } |
             ForEach-Object {
                 [void]($_.Name -match '^1\.20\.1-forge-(\d+\.\d+\.\d+)$')
                 [PSCustomObject]@{ Nombre = $_.Name; Ver = [version]$Matches[1] }
             } |
             Sort-Object Ver -Descending | Select-Object -First 1
}
if ($mejor -and $mejor.Ver -ge $min) {
    OK "Forge instalado y validado: $($mejor.Nombre)"
    Limpiar
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " FORGE LISTO" -ForegroundColor Green
    Write-Host "============================================================"
    exit 0
} else {
    FAIL "Termino, pero no encuentro un Forge >= $MinForge."
    if ($mejor) { INFO "Lo mas nuevo que veo: $($mejor.Nombre)" }
    Limpiar; exit 4
}
