# Análisis técnico de SERVER PANAS

Revisión del proyecto como modpack Forge y como operación de un servidor en exaroton. Fecha: 30/10/2026.

## 1. Estado del servidor (medido)

| Aspecto | Valor | Valoración |
|---|---|---|
| Software | Forge 1.20.1 (47.4.28) | Correcto; el cliente exige 47.4.0 o superior |
| Mods | 103 (99 del pack + 4 propios) | Coincide con `servidor/mods` + `mods-propios` |
| Optimización de servidor | ModernFix, FerriteCore, Canary, Clumps | Bien cubierto |
| `view-distance` / `simulation-distance` | 10 / 5 | Equilibrado para 103 mods |
| `sync-chunk-writes` | false | Mejor rendimiento de disco |
| `online-mode` | **false** | Necesario para TLauncher, pero con riesgos (ver 4.1) |
| `white-list` | **false** (3 nombres registrados) | Riesgo (ver 4.1) |
| Operadores | ninguno | Correcto: la administración pasa por el panel |
| Datapacks del mundo | 9 | `RespaldoInventario` no está desplegado (no hace falta) |

## 2. Cambios realizados en esta reestructuración

### Organización
- La raíz pasó de 40+ archivos sueltos a carpetas por responsabilidad (ver `README.md`).
- 64 movimientos registrados en `archivo/MOVIMIENTOS.txt`, con script de reversión `archivo/deshacer-reestructura.ps1`. **No se borró nada.**
- Rutas internas actualizadas en: 19 scripts de `herramientas/`, `panel/app.py` (compilación de InvBackupMod), `automatizacion/instalar-override.ps1`, `automatizacion/aplicar-ajuste.ps1` y el empaquetador. Dentro del zip de los amigos los scripts conservan sus nombres históricos, porque `INSTALAR-CLIENTE.bat` los llama así.
- Scripts con tildes ahora en UTF-8 con BOM: antes PowerShell 5.1 mostraba mal los acentos.

### Centro de comandos
- `SERVERPANAS.bat` con menú y 15 acciones; `comandos/` con 15 accesos de un clic.
- Apagar y reiniciar avisan a los jugadores conectados y hacen `save-all` antes.
- Las acciones que escriben en el servidor piden confirmación y admiten `-Simular`.
- `subir-misiones` valida, respalda lo que hay en el servidor, sube y recarga en vivo (`/ftbquests reload`).

### Misiones (11 capítulos)
- Verificadas contra los `.jar` reales: ningún ID duplicado, ninguna dependencia rota ni circular.
- Corregido el icono inexistente `iceandfire:dragonegg` → `iceandfire:dragonegg_red`.
- El Naga, el Lich y el Heraldo pasan de "marcar a mano" a **tareas de matar** que se comprueban solas.
- Ortografía completa, estilo uniforme por rol (inicio, normal, hito, jefe, final) y disposición automática ordenada.
- Corregidos datos falsos: el Tótem lo suelta el Evocador (no el capitán saqueador); el Warden no vive en el Otro Lado ni suelta caparazón; "Barako" ya es Umvuthi; los encantamientos citados de Ensorcellation no existían.
- Flujo editable: `misiones/textos` → `generar-misiones` → `subir-misiones`.

### Panel
- Protección CSRF y DNS rebinding.
- Interfaz reorganizada sin cambiar su lógica.

## 3. Arquitectura de respaldos de inventario

| Mecanismo | Dónde | Qué cubre | Estado |
|---|---|---|---|
| `InvBackupMod` | Servidor | Copia al morir y al salir; `/invrestore` | **Mantener** |
| `HistorialInventario` (datapack) + panel | Servidor + PC | Copia cada 30 s que el panel descarga; reposición exacta con NBT | **Mantener** (el panel depende de él) |
| `automatizacion/bot-inventarios.ps1` | PC | Versión antigua del capturador | **Retirar**: el panel lo sustituye |
| `RespaldoInventario` (datapack) | Solo local | Copia cada 6 s | No desplegado; dejarlo así |

## 4. Recomendaciones pendientes (por prioridad)

### 4.1 Alta: suplantación de jugadores
Con `online-mode=false` el servidor no comprueba la identidad: cualquiera que conozca la dirección puede entrar con el nombre de un amigo y **acceder a su inventario y su tumba**, porque en modo offline el UUID depende solo del nombre.
- Paso inmediato: activar la lista blanca (`white-list=true`, `enforce-whitelist=true`). Los 3 nombres ya están registrados.
- Paso completo: añadir un mod de autenticación con contraseña compatible con Forge 1.20.1, o pasar a `online-mode=true` si todos tienen cuenta oficial.

### 4.2 Media: rendimiento
- **Pregenerar el mundo** con un mod como Chunky en un radio de 3000-5000 bloques: Terralith y las estructuras de YUNG's generan mucho lag al explorar terreno nuevo.
- **spark** en el servidor para medir TPS y encontrar qué mod consume más cuando haya lag.

### 4.3 Media: compilación de mods
Los 5 mods se compilan por separado con un Gradle instalado a mano en `%USERPROFILE%\gradle-dist`. Conviene añadir **Gradle Wrapper** (`gradlew`) a cada mod, o unirlos en un proyecto multi-módulo, para compilar en cualquier PC sin instalar nada.

### 4.4 Baja: limpieza
- `archivo/SERVER-PANAS v1.0.0 (duplicado de distribucion).zip` (362 MB) es idéntico al de `distribucion/` (mismo MD5): se puede borrar.
- `archivo/server_snapshot-2026-10-25/jars` (≈750 MB) son copias de mods que ya están en `cliente/mods`.
- El pack de traducción (`cliente/resourcepacks/Espanol-ServerPanas`) tampoco lleva tildes ("Corazon de las Profundidades").

### 4.5 Control de versiones
El proyecto no tiene historial propio: está como carpeta sin seguimiento dentro del repositorio de `Proyecto Prueba Kiro`, mezclado con otros proyectos. Ya hay un `.gitignore` preparado para crear un repositorio solo de ServerPanas que versione código, misiones y configuración, sin binarios, respaldos ni credenciales.
