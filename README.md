# SERVER PANAS

Modpack de supervivencia y aventura para **Minecraft 1.20.1 (Forge 47.4)**, alojado en **exaroton**, con mods propios, panel web de administración y misiones en español.

| | |
|---|---|
| Dirección | `ShadowBlack77-rEez.exaroton.me:23574` (el puerto es obligatorio) |
| Mods | 110 en el cliente · 103 en el servidor (99 del pack + 4 propios) |
| Misiones | 11 capítulos (80 misiones) + 30 capítulos de guía de mods |
| Requisitos del administrador | Windows, PowerShell 5.1, Python 3.10+, JDK 17 (Temurin), Gradle 8.1.1 |

---

## Inicio rápido

**Doble clic en `SERVERPANAS.bat`.** Abre el centro de comandos con todas las acciones.

Para una acción concreta sin pasar por el menú, usa la carpeta **`comandos/`**: cada `.bat` es un clic.

```bat
SERVERPANAS.bat estado
SERVERPANAS.bat comando "time set day"
SERVERPANAS.bat subir-misiones -Simular
```

- `-Simular` muestra lo que haría sin tocar el servidor.
- `-Si` omite las confirmaciones (útil para tareas programadas).

### Acciones disponibles

| Acción | Qué hace |
|---|---|
| `estado` | Estado, versión de Forge y jugadores conectados |
| `encender` | Enciende el servidor y espera a que esté listo |
| `apagar` | Avisa a los jugadores, guarda el mundo y apaga |
| `reiniciar` | Avisa, guarda y reinicia |
| `comando` | Envía un comando de consola y muestra la respuesta |
| `log` | Últimas líneas del log del servidor |
| `panel` | Abre el panel web (`http://127.0.0.1:8765`) |
| `respaldo` | Descarga misiones, economía, datapacks y configuración a `respaldos/` |
| `compilar` | Compila los mods propios (todos o los indicados) |
| `desplegar-mods` | Sube los mods propios compilados a `mods/` del servidor |
| `validar` | Valida dependencias de mods y el árbol de misiones |
| `generar-misiones` | Regenera los capítulos desde `misiones/textos` |
| `subir-misiones` | Valida, respalda el servidor y sube los capítulos |
| `empaquetar` | Genera los zips para repartir a los amigos |
| `probar` | Prueba la conexión al servidor desde fuera |

---

## Estructura

```
ServerPanas/
├─ SERVERPANAS.bat          Centro de comandos (doble clic)
├─ comandos/                Accesos de un clic a las acciones más usadas
├─ herramientas/
│  ├─ ServerPanas.ps1       Lógica del centro de comandos
│  ├─ lib/Exaroton.ps1      Cliente de la API de exaroton
│  ├─ misiones/             Generador, validador y vista previa de FTB Quests (Python)
│  ├─ pack/                 Construcción del pack, instalador del cliente, CurseForge
│  ├─ validacion/           Dependencias, compatibilidad, versiones
│  ├─ traduccion/           Cobertura y reparación de traducciones al español
│  └─ servidor/             Prueba de conexión
├─ mods-propios/            Código fuente de los mods Forge propios
├─ misiones/
│  ├─ textos/               FUENTE EDITABLE de los 11 capítulos
│  ├─ capitulos/            Capítulos generados (.snbt), lo que se sube al servidor
│  ├─ guia-mods/            Capítulos de guía de mods
│  └─ vista-previa.html     Mapa visual de los capítulos
├─ datapacks/               Datapacks del mundo (fuentes y zips)
├─ configuracion-servidor/  Archivos .toml ajustados para el servidor
├─ panel/                   Panel web de administración (Python, solo 127.0.0.1)
├─ automatizacion/          Credenciales cifradas de exaroton y scripts heredados
├─ cliente/                 Instancia completa del cliente (mods, config, idioma)
├─ servidor/                Mods y configuración de referencia del servidor
├─ distribucion/            Zips listos para los amigos (CurseForge e instalador)
├─ respaldos/               Copias descargadas del servidor (no se versiona)
├─ docs/                    Guías, análisis técnico y documentación histórica
└─ archivo/                 Material histórico y script para deshacer la reestructuración
```

### Mods propios

| Mod | En el servidor | Función |
|---|---|---|
| `EconomyMod` (servereco) | Sí | Economía con monedas físicas, tienda por descubrimiento y subastas |
| `DailyQuestsMod` | Sí | Misiones diarias por jugador |
| `InvBackupMod` | Sí | Respaldo de inventario al morir/salir y `/invrestore` |
| `ServerGuiaMod` | Sí | Guía del servidor dentro del juego |
| `GuideInjectorMod` | No | Herramienta que genera los capítulos de guía de mods |

---

## Flujos de trabajo

### Editar misiones

1. Edita el capítulo en `misiones/textos/NN_nombre.txt`. Cada misión tiene `rol` (`inicio`, `normal`, `hito`, `jefe`, `final`), título, subtítulo y descripción.
2. `SERVERPANAS.bat generar-misiones`: aplica textos, estilo y disposición, y valida.
3. Revisa `misiones/vista-previa.html`.
4. `SERVERPANAS.bat subir-misiones`: respalda las del servidor, sube las nuevas y las recarga en vivo.

El rol define forma, tamaño y color, así que todos los capítulos tienen el mismo aspecto. Los IDs, dependencias, ítems y recompensas viven en los `.snbt` y el generador no los cambia.

### Cambiar un mod propio

1. Edita el código en `mods-propios/<Mod>/src`.
2. `SERVERPANAS.bat compilar <Mod>`.
3. `SERVERPANAS.bat desplegar-mods` y después `reiniciar`.

### Repartir el pack

`SERVERPANAS.bat empaquetar` genera en `distribucion/`:

- `SERVER-PANAS v1.0.0.zip` → importar en CurseForge.
- `SERVER-PANAS-INSTALADOR.zip` → launcher oficial, TLauncher o Prism (ejecutar `INSTALAR-CLIENTE.bat`).

---

### Dar acceso al panel a un compañero

Todo se hace desde el panel, en la pestaña **Acceso y usuarios** (solo administradores):

1. **Primera vez:** el panel se abre en esa pestaña. Crea tu cuenta de administrador.
2. **Enlace:** pega tu authtoken de ngrok y pulsa *Activar enlace* (solo desde tu PC). El panel detecta tu dominio gratuito, deja el túnel arrancando solo con Windows y muestra el enlace con un botón para copiarlo.
3. **Usuarios:** añade, cambia la contraseña o el rol, o borra a quien quieras con botones.

Los `.bat` 16, 17 y 18 de `comandos/` hacen lo mismo por consola, por si el panel no estuviera disponible.

El panel solo está disponible mientras tu PC esté encendido. La primera visita de cada navegador muestra un aviso de ngrok con el botón *Visit Site*. Sin el paso 2, el comando 17 abre un túnel temporal de Cloudflare cuyo enlace cambia cada vez.

| Rol | Puede | No puede |
|---|---|---|
| `admin` | Todo, incluida la gestión de usuarios y del enlace | — |
| `colaborador` | Estado, consola, inventarios, misiones, tienda, datapacks, jefes, registros | Editor de archivos, ajustes del panel, instalar el mod |
| `jugador` | Ver el estado del servidor y **encenderlo** | Todo lo demás (ni apagar, ni reiniciar, ni consola) |

Requiere `ngrok` (`winget install Ngrok.Ngrok` y después `ngrok update`) o, para el túnel temporal, `cloudflared`. En cuanto existe algún usuario, el panel pide sesión también desde tu PC.

## Seguridad

- El token de exaroton se guarda **cifrado con DPAPI** (`automatizacion/exaroton-token.xml`): solo tu usuario de Windows puede usarlo. Para cambiarlo: `automatizacion/1-configurar-token.ps1`.
- El panel escucha solo en `127.0.0.1` y rechaza peticiones de otras webs (CSRF y DNS rebinding). Con usuarios creados exige inicio de sesión (contraseñas con PBKDF2, cookie `HttpOnly`/`SameSite=Strict`, bloqueo tras 5 intentos) y sin usuarios rechaza todo acceso desde Internet.
- Lee `docs/ANALISIS.md` para ver los riesgos pendientes del servidor (modo offline sin lista blanca).

## Documentación

- [`docs/ANALISIS.md`](docs/ANALISIS.md): análisis técnico, cambios hechos y recomendaciones.
- [`docs/GUIA-EXAROTON.md`](docs/GUIA-EXAROTON.md): montaje del servidor paso a paso.
- [`docs/ENLACES-Y-REQUISITOS.md`](docs/ENLACES-Y-REQUISITOS.md): Java, Forge, launchers y RAM.
- [`docs/LISTA-MODS-SERVIDOR.md`](docs/LISTA-MODS-SERVIDOR.md): mods del servidor.
- `archivo/MOVIMIENTOS.txt`: dónde fue cada archivo en la reestructuración (`deshacer-reestructura.ps1` la revierte).
