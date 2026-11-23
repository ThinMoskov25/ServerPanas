# Guía de trabajo para Kiro: ServerPanas

Esta guía explica cómo hago los ajustes del servidor, del panel, de los mods y del cliente. Léela completa antes de tocar algo. Si algo no está aquí, **pregunta antes de improvisar**.

---

## 0. Reglas de oro (no negociables)

1. **El repo de GitHub es PÚBLICO.** Nunca subas estos archivos:
   - `automatizacion/exaroton-token.xml`, `automatizacion/config.json` (serverId) y cualquier `*.env`
   - `panel/datos/usuarios.json`, `panel/datos/nube.json` y `panel/datos/tunel.json`

   Antes de cada commit, ejecuta `git status` y revisa la lista. El `.gitignore` ya los cubre; **no lo edites para incluirlos**.
2. **Nunca escribas ni muestres tokens ni contraseñas.** El token se descifra solo en memoria (DPAPI) a través de `panel/exaroton.py`.
3. **Haz un respaldo antes de editar cualquier archivo del servidor o del cliente.** Guárdalo en `respaldos/` con fecha (`nombre.antes-AAAAMMDD-HHMMSS`).
4. **Nunca desactives una función o un mod para "arreglar" un bug sin preguntar.** Ejemplo real: un cofre duplicaba ítems y se culpó a Quark ("Easy Transfering"). El culpable era el modo reconstrucción del mod InvBackup. Busca la causa real.
5. **Los datapacks se aplican con `/reload`; no reinicies el servidor por eso.** Solo los mods (`.jar`) y algunas configs de mods necesitan reinicio. Antes de reiniciar, avisa en el chat (ver §4).
6. **Antes de editar `options.txt` del cliente, Minecraft debe estar CERRADO.** Si está abierto, el juego lo sobrescribe al salir.
7. **Hora de referencia: Bogotá (UTC-5).** Todas las fechas que se le muestran al usuario van en hora Bogotá.

## 1. Cómo trabaja el usuario (preferencias)

- Habla en español. Quiere **respuestas muy cortas** y que **lo hagas tú directamente**: no le pidas que descargue ni que ejecute cosas si puedes hacerlas tú.
- Cuando algo está listo, díselo en 1 o 2 líneas. Si algo falló, dilo tal cual.
- Teclas: **Ctrl = agacharse** y **Shift = correr**. No se cambian.
- Máximo **dos teclas** por combinación. Las acciones útiles van a la **izquierda** del teclado (Alt, Caps) y las raras a la derecha.

## 2. Mapa del proyecto

```
ServerPanas/
├─ panel/                 Panel web (Python, sin frameworks). Desplegado en Render.
│  ├─ app.py              Servidor HTTP + todas las rutas /api/*
│  ├─ exaroton.py         Cliente de la API de exaroton (con caché)
│  ├─ datapacks.py        Generador de datapacks (jefes, élites, vida, programador)
│  ├─ bot.py              PanelBot: avisos de misiones a cada jugador
│  └─ static/index.html   Toda la interfaz (una sola página)
├─ mods-propios/          Mods Forge 1.20.1 propios (Gradle)
│  ├─ EconomyMod/         Tienda, /tienda, poderes, barra de tienda
│  ├─ DailyQuestsMod/     Misiones diarias + barra de misiones
│  ├─ InvBackupMod/       Respaldos de inventario + modo reconstrucción
│  ├─ GuideInjectorMod/ ServerGuiaMod/
├─ misiones/              Copias locales de capítulos FTB Quests
├─ herramientas/
│  ├─ misiones/ftbsnbt.py Parser/validador SNBT de FTB Quests
│  ├─ teclas/             asignar_teclas.py y comprobar_teclas.py (cliente)
│  └─ panel-pruebas/      panel_prueba.py (panel local sin enviar nada) y medir_panel.py
├─ docs/                  MIS-TECLAS.txt, GUIA-EXAROTON.md, etc.
├─ respaldos/             (ignorado por git) copias antes de editar
└─ render.yaml            Despliegue del panel en Render
```

**Datos clave:**

| Cosa | Valor |
|---|---|
| Servidor | exaroton `ShadowBlack77-rEez.exaroton.me:23574`, Forge 47.4, MC 1.20.1 |
| Panel en línea | https://serverpanas-panel.onrender.com (se despliega con `git push` a `main`) |
| Instancia cliente | `C:\Users\David Meza\curseforge\minecraft\Instances\SERVER PANAS` |
| Jugador admin | **SrBalduR**, UUID `1919b047-e263-33be-b91e-a9701d08e948` = `[I;421113927,-496815170,-1189172880,487123272]` |
| Otro jugador | TsubasaDraws (UUID en `usercache.json` del servidor) |
| Casa de SrBalduR | ~(-2206, 96, 95). Bibliotecarios en (-2200,96,87) y (-2199,96,86) |

## 3. Hablar con el servidor (exaroton)

**Atajo:** `SERVERPANAS.bat estado|comando "..."|log|reiniciar|compilar|desplegar-mods|respaldo` hace lo más común sin escribir código. Agrega `-Simular` para ver qué haría sin tocar nada. Más detalle en el `README.md`.

Todo pasa por `panel/exaroton.py`. Úsalo así desde un script en la carpeta `panel/`:

```python
import sys; sys.path.insert(0, r'...\ServerPanas\panel')
import exaroton, time
EXA = exaroton.Exaroton()            # lee el token cifrado (solo con el usuario de Windows)

EXA.estado()                          # dict; status 1 = encendido
EXA.comando('say hola')               # ejecuta un comando de consola
time.sleep(15); print(EXA.logs()[-3000:])   # los logs llegan con 5-30 s de retraso

EXA.leer_archivo('ruta', cache=False) # bytes; usa cache=False si acabas de cambiar algo
EXA.escribir_archivo('ruta', datos)   # bytes
EXA.info_archivo('carpeta', cache=False)
EXA.crear_directorio('ruta')
EXA.accion('restart')                 # reinicio
```

**Caché:** las lecturas se guardan 90 s (`CACHE_SEG`). Escribir un archivo invalida su entrada. Para leer siempre fresco, usa `cache=False` o el bloque `with EXA.fresco(): ...`.

**Patrón para verificar un comando:**
1. Ejecútalo con `comando`.
2. Espera 15–30 s.
3. Lee `logs()` y busca la línea de respuesta.

Si el log no la muestra, espera más antes de concluir que falló.

## 4. Reinicios del servidor (solo cuando hace falta)

```
say [Servidor] Reinicio en 10 segundos para aplicar cambios
save-all
(esperar 10 s)
EXA.accion('restart')
esperar hasta estado()['status'] == 1  (2-4 min)
revisar logs: sin "Exception"/"Error" del mod tocado
```

## 5. Datapacks

- Viven en `world/datapacks/` del servidor. Para aplicarlos: `EXA.comando('reload')`, sin reiniciar.
- El panel genera los suyos desde `panel/datapacks.py` (PanelJefes, PanelUtil, PanelBot...). Si cambias el generador, despliega el panel (§12) y vuelve a generar desde el panel.

## 6. Configs de mods en el servidor

| Archivo | Nota |
|---|---|
| `config/iceandfire-common.toml` | `"Tamed Dragon Griefing" = false` (dragones domados no rompen bloques) |
| `config/quark-common.toml` | `"Easy Transfering" = true`: **NO desactivar** |
| `config/servereco/valores.json` | Precios de la tienda, `poderes`, `catalogoCompra` (botella XP = 500). Aplica con `tienda reload` o reinicio |

Siempre: leer → respaldar → editar solo la línea → escribir → releer con `cache=False` para confirmar.

## 7. Mods propios (compilar y desplegar)

**Compilar** (JDK 17 + Gradle 8.1.1 local):

```powershell
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-17*" | Select-Object -First 1).FullName
cd "...\ServerPanas\mods-propios\EconomyMod"
& "$env:USERPROFILE\gradle-dist\gradle-8.1.1\bin\gradle.bat" build --no-daemon --quiet
# resultado: build\libs\<mod>-x.y.jar
```

- Todos los `build.gradle` tienen `options.encoding = 'UTF-8'`. Mantenlo, o las tildes salen rotas en el juego.
- **Desplegar:**
  1. Haz el aviso de §4.
  2. Sube el jar a `mods/` con `escribir_archivo` (borra antes el jar viejo si cambió de nombre).
  3. Reinicia.
  4. Revisa el log hasta ver el mod cargado.
- Los mods que tocan la interfaz (barras, menús) también deben actualizarse en el cliente: copia el jar a `<instancia>/mods/`.

**Qué hace cada mod (lo más reciente):**
- **EconomyMod:**
  - `shop/Powers.java` y `PowersMenu.java`: compra de poderes, p. ej. TP hacia un compañero.
  - `ShopBar.java`: barra "Visita la tienda usando comando /tienda" cada 40 min durante 30 s.
  - Se quitó "AGREGAR CRÉDITO" del menú principal y los precios se multiplicaron x10.
- **DailyQuestsMod:** `QuestBar.java` muestra la barra cada 40 min durante 1 min, y avisa cuando se regeneran las misiones (nuevo día).
- **InvBackupMod:**
  - Respaldos en `world/inventory_backups/<uuid>/`.
  - `Reconstruir.java` es el modo reconstrucción (lo hizo Kiro). Tenía un **bug de duplicación**: aparecía un cofre "fantasma" del que se podían sacar copias. Se arregló así:
    - En `alTick`, si el jugador tiene un contenedor abierto (`p.containerMenu != p.inventoryMenu`), se limpia la copia reservada y **no** se prepara nada.
    - En `usarRueda`, al colocar un bloque que no viene de la rueda, se descuenta (`shrink(1)`) el ítem real.
  - **Si vuelves a tocar este modo, prueba que no se pueda duplicar** (abrir cofre, sacar, colocar).

## 8. Misiones FTB Quests

- Edita **la copia del SERVIDOR** (`config/ftbquests/quests/chapters/*.snbt`). Los IDs del servidor son distintos de los locales, así que no subas la copia local encima.
- Pasos:
  1. Descarga el archivo y respáldalo.
  2. Edítalo.
  3. Valida con `herramientas/misiones/ftbsnbt.py` (`ftbsnbt.loads(texto)` no debe fallar).
  4. Súbelo.
  5. Ejecuta `EXA.comando('ftbquests reload')`.
- Guarda una copia local actualizada en `misiones/` (p. ej. `misiones/guia-mods/g06_iceandfire.snbt`, que explica los huevos de dragón: eclosión, montura y vuelo).

## 9. Sistema de jefes (PanelJefes, `panel/datapacks.py`)

**Cómo funciona:**
- **Aparición:** modo `cerca` (aleatorio cerca de un jugador) o coordenadas fijas. Hay una opción de **silencio**, sin avisos en el chat.
- **Dragones:** salen adultos gracias a `AgeTicks` en `_nbt_extra`. Nivel 1 = dragón normal, nunca bebé.
- **Plataforma:** disco de **1 bloque de grosor, sin paredes**, de `reinforced_deepslate`; se limpia el aire por encima. El radio es `ARENA_RADIO=12` (`ARENA_RADIO_DRAGON=24` para dragones). Es indestructible, incluso para el jefe, y **solo se elimina cuando el jefe muere de verdad**.
- **Barra de vida (bossbar):** `paneljefes:<token>`, visible solo a **500 bloques** (`RADIO_BARRA`) del punto de aparición. Muestra el número de vida y, al pasar el mouse en el chat, las estadísticas (`texto_stats`).

**Marcadores:** `pj_<token>` (jefe), `pjc_` (centro de la plataforma), `pjf_` (seguidor con forceload), `pjo_` (origen para el modo solo barra). Funciones `watch_`/`dead_`/`obra_`; scoreboard `pjv`.

**⚠ Trampa conocida:** la detección de muerte usa el **seguidor** (`pjf_`). Si añades un forceload temporal para encontrar una entidad, libéralo **en la misma función** en la que se vuelve a poner el forceload del seguidor. Si no, el vigilante cree que el jefe murió y borra la plataforma.

## 10. Comandos útiles (dragones, ítems, aldeanos)

**Dar algo en silencio**, sin mensaje de "Se ha dado...":
```
gamerule sendCommandFeedback false
give SrBalduR <item>{nbt} 1
gamerule sendCommandFeedback true
```
Los anuncios de logros no se pueden ocultar.

**Encontrar o mover el dragón de SrBalduR:**
- Si está montado: `execute as SrBalduR on vehicle run ...`
- Por dueño: `@e[type=iceandfire:...,nbt={Owner:[I;421113927,-496815170,-1189172880,487123272]}]`
- Si el chunk no está cargado:
  1. `forceload add` en la zona.
  2. Ejecuta el comando.
  3. `forceload remove`.
- Llevarlo hasta el jugador: `tp @e[...dueño...,limit=1] SrBalduR`.

**Recuperar ítems de un respaldo:**
1. Lee el `.dat` o el NBT del respaldo con el panel (Jugadores → Recuperar).
2. Da el ítem con su **NBT exacto**.
3. Para añadir encantamientos, agrega `Enchantments:[{id:"minecraft:mending",lvl:1s},{id:"minecraft:unbreaking",lvl:3s}]`.

**Aldeanos (ajustar trades):**
- Edita el aldeano existente con `data merge entity <uuid> {Offers:{Recipes:[...]}}`.
- Precio = `buy:{id:"minecraft:emerald",Count:32b}`.
- Para quitar un nombre personalizado: `data merge entity <uuid> {CustomName:'null'}`. `data remove` **no** funciona.
- Para que no pierda la profesión, mantén `Xp` ≥ 1. Nivel novato = `VillagerData.level:1`.
- Estado actual: un bibliotecario novato con **Reparación** a 32 esmeraldas y otro con **Irrompibilidad III** a 24 esmeraldas.

## 11. Teclas del cliente (Key Binding Patch)

- El cliente tiene el mod **Key Binding Patch** (`KeyBindingPatch-1.3.3.5-forge-1.20.1.jar`), que permite combinaciones con cualquier tecla.
- Formato en `options.txt`: `key_<accion>:<tecla>:NONE:<mod1>+<mod2>` (máximo 1 modificador → 2 teclas en total).
- **Reglas para que no salgan en rojo:**
  - No repitas la misma tecla con la misma combinación en el mismo contexto (las de JEI son de contexto GUI).
  - Ninguna acción puede usar como tecla base el modificador de otra. Ej.: si algo es `Alt+X`, nada puede usar `Alt` solo.
  - Evita como base de una combinación una tecla que ya tenga una acción sola en el juego, porque esa acción también se dispara.
  - Ctrl+X y Shift+X dan conflicto con agacharse/correr, así que **no los uses**. Shift solo se comparte a propósito con `carry` (agarrar animales/bloques con Shift + clic derecho con la mano vacía) y `jade`.
  - **Nunca** asignes Alt+F4.
- **Herramientas:**
  - `herramientas/teclas/comprobar_teclas.py "<instancia>\options.txt"` revisa sin modificar nada. Debe dar `en rojo: 0`, `sin tecla: 0` y `de 3+ teclas: 0`.
  - `herramientas/teclas/asignar_teclas.py` reasigna todo con la distribución actual (Alt/Caps para lo útil, RAlt/RCtrl/RShift para lo raro, barra de hechizos en C). Primero haz un respaldo de `options.txt` y **cierra Minecraft**.
- Después de cambiar teclas, actualiza `docs/MIS-TECLAS.txt` y la copia en la instancia (`MIS-TECLAS.txt`).
- Si rompes `options.txt`, restaura el último `options.txt.antes-*` de la carpeta de la instancia.

## 12. Panel web (Render)

- **Desplegar:** `git add` (solo archivos seguros) → `git commit` → `git push origin main`. Render se actualiza solo en unos 2–3 min; compruébalo abriendo la URL y la pestaña que cambiaste.
- **Rendimiento:** el panel debe responder en milisegundos. Se logra con:
  - La caché de `exaroton.py`.
  - Un hilo de precarga, `bucle_precarga`, que cada 45 s (120 s si el servidor está apagado) lee en modo fresco lo que usan los módulos.
  - Respuestas JSON comprimidas con gzip.
  - Lecturas en paralelo (ThreadPool).

  **No agregues lecturas lentas en el camino de una petición.** Si un dato se usa al entrar a un módulo, súmalo a `precargar_una_vez()`.
- Tras una acción que cambia archivos (whitelist, ops, ban...), invalida la caché de esos archivos (`EXA.invalidar_archivo`).
- **Probar en local sin tocar el servidor:**
  ```powershell
  python herramientas\panel-pruebas\panel_prueba.py panel <archivo_registro.txt>
  ```
  Bloquea los comandos y las escrituras (los comandos se anotan en el archivo de registro) y sirve en `http://127.0.0.1:8793`. Con `medir_panel.py` se miden los tiempos. Ciérralo al terminar: deja un hilo de precarga corriendo.
- Pestañas principales: Consola (incluye XP), Tienda, Jugadores (ficha, reparar ítem al 100 %, recuperar inventario por pestañas con barra de progreso y ✔ Recuperado), Jefes, Bot, Datapacks y Misiones.

## 13. Checklist antes de decir "listo"

- [ ] ¿Respaldaste lo que editaste?
- [ ] ¿Verificaste en los logs o releyendo el archivo (`cache=False`) que el cambio quedó?
- [ ] ¿Usaste `/reload` en vez de reiniciar cuando bastaba?
- [ ] Si reiniciaste: ¿avisaste antes y el servidor volvió a status 1 sin errores?
- [ ] Si tocaste el panel: ¿hiciste push y Render ya muestra el cambio?
- [ ] ¿`git status` está limpio de secretos?
- [ ] Respuesta al usuario: corta, en español, diciendo qué se hizo y qué falta.
