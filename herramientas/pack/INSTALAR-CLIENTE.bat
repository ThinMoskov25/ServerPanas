@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title Instalador SERVER PANAS - Minecraft 1.20.1 Forge
color 0B

set "ORIGEN=%~dp0cliente"
set "SCRIPTS=%~dp0"
set "MCBASE=%APPDATA%\.minecraft"
set "FORGE_VER=47.4.0"
set "FORGE_MIN=47.4.0"
set "FORGE_URL=https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-%FORGE_VER%/forge-1.20.1-%FORGE_VER%-installer.jar"
set "JAVA_URL=https://adoptium.net/temurin/releases/?version=17"
set "RAM=8"

cls
echo ============================================================
echo    SERVER PANAS  -  Instalador del modpack
echo ============================================================
echo.
echo   Minecraft : 1.20.1
echo   Loader    : Forge 47.4.0 o superior
echo   Mods      : 110
echo   Misiones  : 74 en espanol
echo   Servidor  : ShadowBlack77-rEez.exaroton.me:23574
echo.
echo   No necesitas CurseForge.
echo.
pause

REM ============================================================
REM  QUE LAUNCHER USAS  (decide como se crea el perfil al final)
REM ============================================================
cls
echo ============================================================
echo    QUE LAUNCHER VAS A USAR?
echo ============================================================
echo.
echo   Esto es IMPORTANTE: cada launcher guarda los perfiles en
echo   un sitio distinto. Si elijes mal, se instala todo pero
echo   NO te aparece la instancia para jugar.
echo.
echo ------------------------------------------------------------
echo   [1]  MINECRAFT ORIGINAL  (launcher oficial de Mojang)
echo ------------------------------------------------------------
echo        - El que descargas de minecraft.net
echo        - Necesita cuenta de pago
echo        - Creo el perfil "SERVER PANAS" automaticamente con
echo          la RAM ya puesta
echo.
echo ------------------------------------------------------------
echo   [2]  TLAUNCHER
echo ------------------------------------------------------------
echo        - El launcher no oficial gratuito
echo        - Guarda los perfiles en otro archivo, por eso aqui
echo          hay que configurarlo de forma distinta
echo        - Te dejo los pasos exactos al final
echo.
echo ------------------------------------------------------------
echo.
set "LAUNCHER="
set /p "LAUNCHER=  Elige 1 (Original) o 2 (TLauncher) (Enter = 1): "
if "!LAUNCHER!"=="" set "LAUNCHER=1"
if "!LAUNCHER!"=="1" (
    set "ESTL=0"
    echo.
    echo  [OK] Configurare el launcher OFICIAL.
) else if "!LAUNCHER!"=="2" (
    set "ESTL=1"
    echo.
    echo  [OK] Configurare para TLAUNCHER.
) else (
    color 0C
    echo.
    echo  [ERROR] Opcion no valida. Vuelve a ejecutar el instalador.
    pause
    exit /b 1
)
echo.
pause

REM ============================================================
REM  PASO 0 - comprobaciones
REM ============================================================
cls
echo [PASO 0/6]  Comprobando requisitos...
echo.

if not exist "%ORIGEN%\mods" (
    color 0C
    echo  [ERROR] No encuentro "%ORIGEN%\mods"
    echo.
    echo  Descomprime el ZIP completo y ejecuta este archivo DENTRO
    echo  de la carpeta que salio, sin moverlo a otro sitio.
    echo.
    pause
    exit /b 1
)
for /f %%A in ('dir /b "%ORIGEN%\mods\*.jar" 2^>nul ^| find /c /v ""') do set "NORIGEN=%%A"
echo  [OK] Modpack encontrado: !NORIGEN! mods.

if not exist "%MCBASE%" (
    color 0E
    echo  [AVISO] No existe %MCBASE%
    echo.
    echo  Abre tu launcher, juega una vez a Minecraft 1.20.1 y cierralo.
    echo  Eso crea la carpeta. Luego vuelve a ejecutar esto.
    echo.
    pause
    exit /b 1
)
echo  [OK] Carpeta .minecraft encontrada.

REM --- PowerShell, que hace falta para el perfil y la descarga ---
set "PS_OK=0"
powershell -NoProfile -Command "exit 0" >nul 2>&1
if not errorlevel 1 set "PS_OK=1"
if "!PS_OK!"=="1" (
    echo  [OK] PowerShell disponible.
) else (
    echo  [AVISO] No detecto PowerShell. Tendras que configurar la
    echo          RAM a mano al final.
)

REM --- Java ---
java -version >nul 2>&1
if not errorlevel 1 (
    echo  [OK] Java detectado en el sistema.
) else (
    echo  [AVISO] No detecto Java en el PATH.
    echo          Los launchers suelen traer el suyo, asi que
    echo          normalmente da igual. Si el juego no arranca por
    echo          Java, instala el 17 desde:
    echo          %JAVA_URL%
)
echo.
pause

REM ============================================================
REM  PASO 1 - donde instalar
REM ============================================================
cls
echo [PASO 1/6]  Donde quieres instalarlo?
echo.
if "!ESTL!"=="1" (
    echo   NOTA PARA TLAUNCHER: la opcion mas sencilla y a prueba de
    echo   fallos en TLauncher es la [2] CARPETA BASE, porque TLauncher
    echo   usa .minecraft por defecto y vera los mods sin configurar
    echo   rutas raras. La [1] carpeta separada tambien sirve, pero
    echo   tendras que apuntar el "Directorio del juego" a mano.
    echo.
)
echo ------------------------------------------------------------
echo   [1]  CARPETA SEPARADA   (recomendado para launcher ORIGINAL)
echo ------------------------------------------------------------
echo        %MCBASE%\SERVER-PANAS
echo.
echo        - No toca nada de lo que ya tienes
echo        - Tus otros mods, mundos y configuraciones siguen igual
echo        - El pack queda aislado, facil de borrar
echo        - Se crea un perfil propio en el launcher
echo.
echo ------------------------------------------------------------
echo   [2]  CARPETA BASE DE MINECRAFT   (recomendado para TLAUNCHER)
echo ------------------------------------------------------------
echo        %MCBASE%
echo.
echo        - Se instala encima de lo que ya hay
echo        - Se hace COPIA DE SEGURIDAD de todo antes de tocar
echo        - Ojo: la carpeta mods es compartida por todos los
echo          perfiles, asi que otros perfiles con mods pueden
echo          dejar de funcionar
echo.
echo ------------------------------------------------------------
echo.
set "DESTINO="
set /p "OPCION=  Elige 1 o 2 (Enter = 1): "
if "!OPCION!"=="" set "OPCION=1"
if "!OPCION!"=="1" (
    set "DESTINO=%MCBASE%\SERVER-PANAS"
    set "ESBASE=0"
) else if "!OPCION!"=="2" (
    set "DESTINO=%MCBASE%"
    set "ESBASE=1"
) else (
    color 0C
    echo.
    echo  [ERROR] Opcion no valida. Vuelve a ejecutar el instalador.
    pause
    exit /b 1
)
echo.
echo  Se instalara en: !DESTINO!
echo.
pause

REM ============================================================
REM  PASO 2 - Forge  (exige 47.4.0 o superior)
REM ============================================================
cls
echo [PASO 2/6]  Forge 1.20.1  (minimo %FORGE_MIN%)
echo.

REM --- comprobar con el validador de PowerShell ---
REM  El script imprime "ESTADO|NOMBRE":
REM    0 = hay un Forge ^>= minimo
REM    2 = hay Forge pero por debajo del minimo (p.ej. 47.3.1)
REM  Si no imprime nada -> no hay ningun Forge (estado 1).
REM  OJO: NO se usa !errorlevel! tras un 'for /f', porque ahi el
REM  errorlevel es el del for, no el del powershell. Por eso el
REM  estado viaja dentro del texto.
set "FORGE_ESTADO=1"
set "FORGE_DETECTADO="
set "FORGE_RAW="
if "!PS_OK!"=="1" (
    if exist "%SCRIPTS%_detectar-forge.ps1" (
        for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPTS%_detectar-forge.ps1" -MinForge %FORGE_MIN%`) do set "FORGE_RAW=%%V"
        if defined FORGE_RAW (
            for /f "tokens=1,2 delims=|" %%A in ("!FORGE_RAW!") do (
                set "FORGE_ESTADO=%%A"
                set "FORGE_DETECTADO=%%B"
            )
        )
    ) else (
        echo  [AVISO] No encuentro _detectar-forge.ps1 junto al .bat.
        echo          Compruebo Forge de forma basica.
        for /d %%D in ("%MCBASE%\versions\1.20.1-forge-47.*") do set "FORGE_ESTADO=0"
    )
) else (
    REM sin PowerShell, comprobacion basica (no puede validar version)
    for /d %%D in ("%MCBASE%\versions\1.20.1-forge-47.*") do set "FORGE_ESTADO=0"
)

if "!FORGE_ESTADO!"=="0" (
    echo  [OK] Tienes un Forge valido instalado:
    if defined FORGE_DETECTADO ( echo       !FORGE_DETECTADO! )
    echo.
    echo  El servidor usa 47.4.28 y es compatible.
    echo.
    pause
    goto :FORGE_FIN
)

if "!FORGE_ESTADO!"=="2" (
    color 0E
    echo  [ATENCION] Tienes Forge instalado, pero es DEMASIADO VIEJO:
    echo       !FORGE_DETECTADO!
    echo.
    echo  El pack necesita Forge %FORGE_MIN% o superior. Varios mods
    echo  ^(rightclickharvest, irons_spellbooks, jamlib^) fallan al
    echo  arrancar con versiones anteriores, con un error
    echo  "requires forge %FORGE_MIN% or above".
    echo.
    echo  Hay que instalar el %FORGE_VER%. No borra el que ya tienes,
    echo  se anade al lado.
    echo.
) else (
    echo  No tienes Forge 1.20.1 instalado. Hace falta el %FORGE_VER%.
    echo.
)

REM --- a partir de aqui: hay que instalar (estado 1 o 2) ---
REM  Se hace TODO solo: conseguir Java, bajar Forge e instalar sin
REM  ventana. El pana no tiene que tocar nada.
if "!PS_OK!"=="0" (
    color 0C
    echo  [ERROR] Hace falta PowerShell para instalar Forge solo, y no
    echo          lo detecto. Es muy raro en Windows. Instala Forge
    echo          %FORGE_VER% a mano desde:
    echo             https://files.minecraftforge.net
    echo.
    pause
    goto :FORGE_FIN
)
if not exist "%SCRIPTS%_instalar-forge.ps1" (
    color 0C
    echo  [ERROR] Falta _instalar-forge.ps1 junto a este .bat.
    echo          Descomprime el ZIP completo sin sacar archivos sueltos.
    echo.
    pause
    goto :FORGE_FIN
)

echo  Voy a instalar Forge %FORGE_VER% automaticamente.
echo  Si no tienes Java, descargo uno temporal yo mismo (~42 MB).
echo  No se abre ninguna ventana y no se instala nada permanente.
echo.
echo  Puede tardar unos minutos. No cierres esta ventana.
echo.
pause
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPTS%_instalar-forge.ps1" -ForgeVer %FORGE_VER% -MinForge %FORGE_MIN%
set "FORGE_RC=!errorlevel!"

if "!FORGE_RC!"=="0" (
    echo.
    echo  [OK] Forge %FORGE_VER% instalado.
) else (
    color 0C
    echo.
    echo  [ERROR] No se pudo instalar Forge automaticamente ^(codigo !FORGE_RC!^).
    if "!FORGE_RC!"=="1" echo          Causa: no se pudo conseguir Java. Revisa tu conexion.
    if "!FORGE_RC!"=="2" echo          Causa: fallo la descarga del instalador de Forge.
    if "!FORGE_RC!"=="3" echo          Causa: el instalador de Forge dio error.
    if "!FORGE_RC!"=="4" echo          Causa: termino pero no quedo la version esperada.
    echo.
    echo  Puedes instalarlo a mano desde https://files.minecraftforge.net
    echo  ^(1.20.1, version %FORGE_VER% o superior^) y volver a ejecutar esto.
    echo.
    pause
)

:FORGE_FIN

REM ============================================================
REM  PASO 3 - copia de seguridad
REM ============================================================
cls
echo [PASO 3/6]  Copia de seguridad
echo.

REM Sello de fecha y hora para nombrar los backups.
REM Se saca con PowerShell a proposito: parsear %date% a mano falla
REM segun la configuracion regional (hay sistemas que meten el dia
REM de la semana delante, u ordenan mes/dia/anno).
set "SELLO="
if "!PS_OK!"=="1" (
    for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "(Get-Date).ToString('yyyy-MM-dd_HH-mm-ss')"`) do set "SELLO=%%T"
)
if "!SELLO!"=="" set "SELLO=backup"

if "!ESBASE!"=="0" (
    echo  Instalas en carpeta separada, asi que no hay nada que
    echo  respaldar: no se toca ninguno de tus archivos actuales.
    echo.
    if not exist "!DESTINO!" mkdir "!DESTINO!"
    echo  [OK] Carpeta creada: !DESTINO!
) else (
    echo  Instalas en la carpeta base, asi que respaldo todo lo que
    echo  voy a tocar antes de hacerlo.
    echo.
    set "BK=%MCBASE%\BACKUP-ANTES-SERVERPANAS_!SELLO!"
    mkdir "!BK!" 2>nul
    set "HUBO=0"

    for %%F in (mods config resourcepacks) do (
        if exist "%MCBASE%\%%F" (
            echo  Respaldando %%F ...
            move "%MCBASE%\%%F" "!BK!\%%F" >nul 2>&1
            if errorlevel 1 (
                color 0C
                echo  [ERROR] No pude mover la carpeta %%F
                echo          Cierra Minecraft y el launcher, y reintenta.
                echo.
                pause
                exit /b 1
            )
            echo  [OK] %%F respaldada.
            set "HUBO=1"
        )
    )
    for %%F in (options.txt servers.dat) do (
        if exist "%MCBASE%\%%F" (
            copy /Y "%MCBASE%\%%F" "!BK!\%%F" >nul 2>&1
            echo  [OK] %%F respaldado.
            set "HUBO=1"
        )
    )

    if "!HUBO!"=="1" (
        echo.
        echo  ------------------------------------------------------
        echo   TU COPIA DE SEGURIDAD ESTA EN:
        echo   !BK!
        echo  ------------------------------------------------------
        echo   Para deshacer todo: borra mods, config y resourcepacks
        echo   de .minecraft y mueve de vuelta lo que hay en esa
        echo   carpeta de backup.
    ) else (
        echo  No habia nada que respaldar. Carpeta limpia.
        rmdir "!BK!" 2>nul
    )
)
echo.
pause

REM ============================================================
REM  PASO 4 - instalar el pack
REM ============================================================
cls
echo [PASO 4/6]  Instalando el modpack...
echo.

mkdir "!DESTINO!\mods" 2>nul
copy /Y "%ORIGEN%\mods\*.jar" "!DESTINO!\mods\" >nul
if errorlevel 1 (
    color 0C
    echo  [ERROR] Fallo al copiar los mods.
    pause
    exit /b 1
)
for /f %%A in ('dir /b "!DESTINO!\mods\*.jar" 2^>nul ^| find /c /v ""') do set "NMODS=%%A"
echo  [OK] !NMODS! mods instalados.

if exist "%ORIGEN%\config" (
    if not exist "!DESTINO!\config" mkdir "!DESTINO!\config"
    xcopy /E /I /Y "%ORIGEN%\config\*" "!DESTINO!\config\" >nul
    echo  [OK] Misiones y configuracion instaladas ^(74 misiones^).
)

if exist "%ORIGEN%\resourcepacks" (
    if not exist "!DESTINO!\resourcepacks" mkdir "!DESTINO!\resourcepacks"
    xcopy /E /I /Y "%ORIGEN%\resourcepacks\*" "!DESTINO!\resourcepacks\" >nul
    echo  [OK] Traduccion al espanol instalada ^(5255 textos^).
)

if exist "%ORIGEN%\options.txt" (
    if not exist "!DESTINO!\options.txt" (
        copy /Y "%ORIGEN%\options.txt" "!DESTINO!\options.txt" >nul
        echo  [OK] Idioma espanol y paquete de traduccion activados.
    ) else (
        echo  [AVISO] Ya hay options.txt, no lo sobrescribo.
        echo          Activa a mano: Opciones ^> Paquetes de recursos
        echo          ^> "Traduccion al Espanol"
    )
)

if exist "%ORIGEN%\servers.dat" (
    if exist "!DESTINO!\servers.dat" (
        copy /Y "!DESTINO!\servers.dat" "!DESTINO!\servers.dat.backup" >nul 2>&1
        echo  [AVISO] Ya tenias servidores guardados.
        echo          Tu lista anterior quedo en servers.dat.backup
        echo.
        set /p "REEMPLAZAR=  Reemplazo la lista por la del servidor? (S/N): "
        if /i "!REEMPLAZAR!"=="S" (
            copy /Y "%ORIGEN%\servers.dat" "!DESTINO!\servers.dat" >nul
            echo  [OK] Servidor anadido.
        ) else (
            echo  Vale. Anadelo a mano con esta direccion:
            echo     ShadowBlack77-rEez.exaroton.me:23574
            echo     ^(el puerto 23574 es OBLIGATORIO^)
        )
    ) else (
        copy /Y "%ORIGEN%\servers.dat" "!DESTINO!\servers.dat" >nul
        echo  [OK] Servidor anadido a tu lista de Multijugador.
    )
)
echo.
pause

REM ============================================================
REM  PASO 5 - perfil del launcher con la RAM puesta
REM ============================================================
cls
echo [PASO 5/6]  Perfil del launcher y memoria RAM
echo.

set "PERFIL_OK=0"

if "!ESTL!"=="1" goto :PERFIL_TLAUNCHER

REM ---------- Launcher OFICIAL ----------
echo  Este paso crea el perfil "SERVER PANAS" en el launcher
echo  OFICIAL con !RAM! GB de RAM ya configurados.
echo.
echo  Importa porque el launcher pone 2 GB por defecto y con
echo  este pack eso CRASHEA al arrancar. Es el fallo numero uno.
echo.
echo  Se hace copia de seguridad de launcher_profiles.json antes.
echo.

if "!PS_OK!"=="1" (
    if exist "%SCRIPTS%_configurar-perfil-launcher.ps1" (
        set /p "HACERLO=  Lo configuro? (S/N, Enter = S): "
        if "!HACERLO!"=="" set "HACERLO=S"
        if /i "!HACERLO!"=="S" (
            echo.
            if "!ESBASE!"=="0" (
                powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPTS%_configurar-perfil-launcher.ps1" -RamGB !RAM! -GameDir "!DESTINO!"
            ) else (
                powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPTS%_configurar-perfil-launcher.ps1" -RamGB !RAM!
            )
            if not errorlevel 1 set "PERFIL_OK=1"
        )
    ) else (
        echo  [AVISO] No encuentro _configurar-perfil-launcher.ps1
        echo          Deberia estar junto a este .bat.
    )
) else (
    echo  [AVISO] Sin PowerShell no puedo configurarlo solo.
)

if "!PERFIL_OK!"=="0" (
    echo.
    echo  ------------------------------------------------------
    echo   CONFIGURA LA RAM A MANO (launcher oficial)
    echo  ------------------------------------------------------
    echo     Instalaciones ^> el perfil de Forge 1.20.1 ^> los 3
    echo     puntos ^> Editar ^> Mas opciones ^> Argumentos de JVM
    echo     Cambia  -Xmx2G  por  -Xmx!RAM!G
    if "!ESBASE!"=="0" (
        echo     Y en "Directorio de juego" pon:
        echo       !DESTINO!
    )
    echo.
)
goto :PERFIL_FIN

:PERFIL_TLAUNCHER
REM ---------- TLAUNCHER ----------
REM  TLauncher NO usa launcher_profiles.json, asi que NO ejecutamos
REM  el script del perfil oficial (fue justo lo que dejo a un pana
REM  sin instancia para jugar). En TLauncher la version de Forge ya
REM  instalada aparece sola en el selector; solo hay que elegirla,
REM  apuntar la carpeta y subir la RAM en Ajustes.
color 0E
echo  TLAUNCHER - configuracion (hazlo tu, es 1 minuto)
echo.
echo  TLauncher guarda los perfiles en su PROPIO archivo, por eso
echo  NO se puede crear el perfil solo como en el oficial. Pero es
echo  facil. Sigue estos pasos EN ORDEN:
echo.
echo  ------------------------------------------------------------
echo   1) ELEGIR LA VERSION FORGE
echo  ------------------------------------------------------------
echo     Abre TLauncher. En el selector de version de abajo a la
echo     izquierda, elige:
echo         Forge 1.20.1  (la 47.4.x que acabamos de instalar)
echo     Si no aparece, pulsa recargar o reinicia TLauncher:
echo     detecta las versiones de la carpeta versions.
echo.
echo  ------------------------------------------------------------
echo   2) EDITAR EL PERFIL  (boton de ajustes junto a ENTRAR)
echo  ------------------------------------------------------------
echo     - Nombre del perfil : SERVER PANAS
echo     - Version           : Forge 1.20.1 (la que elegiste arriba)
if "!ESBASE!"=="0" (
    echo     - Directorio del juego / Game dir : APUNTALO a
    echo           !DESTINO!
    echo       ^(CLAVE: sin esto, TLauncher no ve los mods^)
) else (
    echo     - Directorio del juego: deja el de por defecto
    echo           %MCBASE%
    echo       ^(ahi quedaron los mods, los vera sin tocar nada^)
)
echo.
echo  ------------------------------------------------------------
echo   3) SUBIR LA RAM  (obligatorio, si no, CRASHEA al arrancar)
echo  ------------------------------------------------------------
echo     Ajustes ^(engranaje arriba a la derecha^) ^>
echo       Configuracion ^> Memoria asignada / RAM
echo     Ponla en !RAM! GB.
echo     Comprueba que use Java 17+ (TLauncher trae el suyo).
echo.
echo  ------------------------------------------------------------
echo   4) ENTRAR
echo  ------------------------------------------------------------
echo     Dale a ENTRAR/PLAY con el perfil SERVER PANAS.
echo     El primer arranque tarda 2-5 min. No lo cierres.
echo.
echo  Si "no aparece la instancia para jugar": el perfil no quedo
echo  apuntando a la version Forge 1.20.1, o la carpeta del juego
echo  no es la correcta. Revisa los pasos 1 y 2.
echo.

:PERFIL_FIN
echo.
pause

REM ============================================================
REM  PASO 6 - resumen
REM ============================================================
cls
color 0A
echo ============================================================
echo    INSTALACION TERMINADA
echo ============================================================
echo.
echo   Mods instalados : !NMODS!
echo   Carpeta         : !DESTINO!
if "!PERFIL_OK!"=="1" (
    echo   Perfil launcher : SERVER PANAS  ^(!RAM! GB de RAM^)
) else (
    echo   Perfil launcher : CONFIGURALO A MANO (ver paso 5)
)
echo.
echo ------------------------------------------------------------
echo   AHORA HAZ ESTO
echo ------------------------------------------------------------
echo.
echo   1. Abre tu launcher. Si estaba abierto, cierralo primero
echo      para que lea el perfil nuevo.
echo.
if "!ESTL!"=="1" (
    echo   2. TLAUNCHER: elige la version "Forge 1.20.1", abre el
    echo      perfil "SERVER PANAS" que configuraste en el paso 5
    echo      ^(con !RAM! GB y la carpeta correcta^) y dale a ENTRAR.
) else if "!PERFIL_OK!"=="1" (
    echo   2. Elige el perfil "SERVER PANAS" y dale a JUGAR.
) else (
    echo   2. Elige el perfil de Forge 1.20.1, comprueba que tenga
    echo      !RAM! GB de RAM, y dale a JUGAR.
)
echo.
echo   3. El primer arranque tarda 2-5 minutos. Es normal con
echo      !NMODS! mods. No lo cierres aunque parezca colgado.
echo.
echo   4. Entra a Multijugador. "SERVER PANAS" ya deberia
echo      aparecer en la lista.
echo.
echo   5. Al aparecer en el mundo llevaras un LIBRO DE MISIONES.
echo      Abrelo con clic derecho: 10 capitulos en espanol.
echo.
echo ------------------------------------------------------------
echo   SI ALGO FALLA
echo ------------------------------------------------------------
echo.
echo   - Crashea al arrancar  : te falto RAM. Sube a !RAM! GB.
echo   - "Mod rejected" o      : tu lista de mods no coincide con
echo     "mismatched mod"       la del servidor. Vuelve a ejecutar
echo                            este .bat sin cambiar nada.
echo   - No conecta           : revisa que la direccion lleve el
echo                            puerto :23574. Si esta bien, el
echo                            servidor esta apagado.
echo   - Todo en ingles       : Opciones ^> Paquetes de recursos y
echo                            activa "Traduccion al Espanol".
echo   - No veo el libro      : StarterKit solo lo da la primera
echo                            vez que entras al servidor.
if "!ESTL!"=="1" (
    echo   - TLauncher: no aparece: el perfil no apunta a Forge
    echo                1.20.1, o el "Directorio del juego" no es la
    echo                carpeta con los mods. Revisa paso 5, puntos 1 y 2.
)
echo.
if "!ESBASE!"=="1" (
    echo   - Quiero deshacer todo : tu copia de seguridad esta en
    echo                            !BK!
    echo.
)
echo ============================================================
echo.
pause
endlocal
