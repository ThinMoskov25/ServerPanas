# Auditoría READ-ONLY de misiones FTB Quests — Server Panas

**Modo:** Solo diagnóstico. No se modificó ningún archivo del servidor ni local.
**Alcance:** Grupo "Crónicas de Server Panas" (`group: 1000000000000001`), capítulos 01–11.
**Fuente de verdad:** archivos descargados en vivo del servidor Exaroton (`<serverId>`) vía `GET /files/data/...` el día de esta auditoría.
**Método de obtención de datos:** descarga de `config/ftbquests/quests/chapters/` y `config/` (ZIP) con la librería `automatizacion/exaroton-lib.ps1`. Archivos temporales en `automatizacion/_audit_tmp/` (pueden borrarse).

---

## 1. Respuesta resumida (lo que importa)

**No se detectó ninguna tarea de misión actualmente rota por un flag de config deshabilitado.** En particular, el fallo histórico ya está corregido: en el servidor en vivo `config/tombstone-server.toml` tiene `allow_grave_key = true`, por lo que la misión **"Seguro de Vida"** (cap. 01) que pide `tombstone:grave_key` es obtenible.

Puntos a vigilar (no son fallos, son avisos):

- **`tombstone:grave_key` (cap. 01, "Seguro de Vida")** — OBTENIBLE, pero con matiz: la forma normal de conseguirla es que **aparece sola en el inventario la primera vez que el jugador muere** (también es crafteable). La tarea de FTB Quests tipo `item` se marca cuando el jugador *tiene* el ítem. Es decir: un jugador que nunca ha muerto y no la craftea no la tendrá "a mano". No es un bug de config, pero es la misión más propensa a confusión del jugador ("no me sale crafteo"). Recomendación de redacción abajo.
- **Ítems que solo se obtienen por drop de jefe/mob o por botín de estructura** (no crafteables): varios. Están marcados como **OBTENIBLE (drop)** y son el diseño intencionado del capítulo, pero conviene que la descripción de la misión deje claro de dónde cae, porque un jugador no verá receta en JEI. Lista completa en la tabla.

**Cobertura de la revisión anti-regresión (lo que pidió el usuario: "verifica que cosas como esta no vuelvan a ocurrir"):** se revisó, para cada ítem pedido, no solo que el ID exista, sino (a) que no esté detrás de un flag `allow_*/enable_*/disable_*` en `false` en la config de su mod, y (b) que tenga vía de obtención (receta/drop/compra). Se auditaron las configs de: tombstone, apotheosis, iceandfire, chococraft, deeperdarker, cataclysm, twilightforest, waystones, toolbelt, travelersbackpack, tradingpost, farmersdelight, reliquary, relics, mowziesmobs, charmofundying, irons_spellbooks. **Ningún flag de habilitación afecta negativamente a un ítem pedido en los capítulos 01–11.**

---

## 2. Tabla de auditoría (capítulos 01–11, solo tareas tipo `item`)

Leyenda de estado:
- **OBTENIBLE** = receta activa o vía clara, sin flag que la bloquee.
- **OBTENIBLE (drop)** = no crafteable; se consigue como drop de mob/jefe o botín de estructura (diseño intencionado).
- **DESHABILITADO EN CONFIG** = bloqueado por un flag en `false`/`disabled`. (Ninguno encontrado.)
- **DUDOSO / VERIFICAR** = obtenible pero con matiz de UX que conviene aclarar.

| Cap | Quest (title) | Ítem pedido | Estado | Nota |
|-----|---------------|-------------|--------|------|
| 01 | &6El Despertar | `minecraft:crafting_table` | OBTENIBLE | Vanilla, receta base. |
| 01 | Herramientas de Piedra | `minecraft:stone_pickaxe` | OBTENIBLE | Vanilla. |
| 01 | Hierro en las Venas | `minecraft:iron_ingot` ×8 | OBTENIBLE | Vanilla, fundición. |
| 01 | &6El Cinturón de Herramientas | `toolbelt:belt` | OBTENIBLE | `toolbelt-common.toml`: `enableGridCraftingRecipes="AUTO"` y `anvilUpgrading="AUTO"`; con Sewing Kit cambia a receta de costura, pero sigue siendo crafteable. Sin bloqueo. |
| 01 | El Arte de Cocinar | `farmersdelight:cooking_pot` | OBTENIBLE | `farmersdelight-common.toml`: `enableCookingPotRecipeBook=true`; receta activa. |
| 01 | &7Seguro de Vida | `tombstone:grave_key` | **OBTENIBLE (verificar UX)** | `tombstone-server.toml` → `[allowedMagicItems] allow_grave_key = true` (el fallo histórico `false` ya está corregido). Matiz: la llave aparece sola al primer fallecimiento; también es crafteable. Ver recomendación §4. |
| 02 | &6Cartógrafo Novato | `minecraft:map` | OBTENIBLE | Vanilla (papel + brújula). |
| 02 | Piedras de Viaje | `waystones:waystone` | OBTENIBLE | `waystones-common.toml`: `restrictToCreative=false` ("This does NOT disable the crafting recipe"). Crafteable. |
| 02 | Piedra de Deformación | `waystones:warp_stone` | OBTENIBLE | Crafteable; mismo mod, sin flag de bloqueo. |
| 02 | &5Saqueador de Mazmorras | `minecraft:emerald` ×6 | OBTENIBLE (drop/comercio) | Mena + comercio + botín Lootr. |
| 02 | Ruinas en Pie | `minecraft:diamond` ×4 | OBTENIBLE | Vanilla. |
| 02 | &6Mochila del Viajero | `travelersbackpack:standard` | OBTENIBLE | `travelersbackpack-common.toml` sin flag que deshabilite la estándar; crafteable. |
| 03 | &2Primeros Tratos | `minecraft:emerald` ×3 | OBTENIBLE (comercio) | — |
| 03 | Puesto Comercial | `tradingpost:trading_post` | OBTENIBLE | `tradingpost-server.toml` sin flag de bloqueo de receta. |
| 03 | Biblioteca Viviente | `minecraft:enchanted_book` | OBTENIBLE (comercio) | Bibliotecario. |
| 03 | &6&lTu Propia Tienda | `simpleshops:simple_shop` | OBTENIBLE | SimpleShops sin config de bloqueo de ítem; crafteable. |
| 04 | &6&lEl Bestiario | `iceandfire:bestiary` | OBTENIBLE | Crafteable con manuscritos (drop de estructura/nido). `iceandfire-common.toml` sin flag de ítem. |
| 04 | &cEl Nido | `iceandfire:dragonbone` ×8 | OBTENIBLE (drop) | Drop de dragón (`Dragons Drop ...`=true). |
| 04 | Espada de Hueso de Dragón | `iceandfire:dragonbone_sword` | OBTENIBLE | Crafteable con dragonbone. |
| 04 | &5Escamas | tag `iceandfire:scales/dragon` ×12 | OBTENIBLE (drop) | Tarea por tag vía `itemfilters:tag`. Drop de dragón. El tag existe en el mod. |
| 04 | &6&lEl Huevo | *checkmark* | n/a | No es tarea `item`. |
| 04 | Criar a la Bestia | `iceandfire:dragon_meal` ×4 | OBTENIBLE | Crafteable. |
| 04 | Cuerno de Dragón | `iceandfire:dragon_horn` | OBTENIBLE | Crafteable. |
| 04 | &cLa Forja de Dragón | `iceandfire:dragonforge_fire_core` | OBTENIBLE | Crafteable (bloque de forja). |
| 04 | &6&lACERO DE DRAGON | `iceandfire:dragonsteel_fire_ingot` | OBTENIBLE (drop+craft) | Requiere corazón de dragón adulto (drop) + forja. Diseño de endgame. |
| 04 | &6&lSEÑOR DE DRAGONES | `iceandfire:dragonsteel_fire_sword` | OBTENIBLE | Crafteable con dragonsteel. |
| 05 | &aSemillas de Gysahl | `chococraft:gysahl_green_seeds` ×3 | OBTENIBLE | `chococraft-common.toml` sin flag de ítem; cultivo/botín. |
| 05 | Chocopedia | `chococraft:chocopedia` | OBTENIBLE | Crafteable. |
| 05 | &6Silla de Chocobo | `chococraft:chocobo_saddle` | OBTENIBLE | Crafteable. |
| 05 | Equipo de Jinete | `chococraft:chocobo_saddle_bags`, `chococraft:chocobo_whistle` | OBTENIBLE | Crafteables (2 tareas). |
| 05 | &5Huevo de Hipogrifo (opcional) | `iceandfire:hippogryph_egg` | OBTENIBLE (drop) | Botín/hipogrifo. Misión `optional:true`. |
| 06 | &dEl Primer Hechizo | `irons_spellbooks:spell_book` | OBTENIBLE | Sin config de bloqueo de ítem/receta; crafteable. |
| 06 | Libro de Hierro | `irons_spellbooks:iron_spell_book` | OBTENIBLE | Crafteable. |
| 06 | &bLibro de Diamante | `irons_spellbooks:diamond_spell_book` | OBTENIBLE | Crafteable. |
| 06 | &6&lLibro de Piel de Dragón | `irons_spellbooks:dragonskin_spell_book` | OBTENIBLE | Crafteable con escamas de dragón. |
| 06 | Artefactos Antiguos (opcional) | *checkmark* | n/a | No es tarea `item`. |
| 06 | &6Mesa de Investigación | `relics:researching_table` | OBTENIBLE | Crafteable. Relics sin flag de bloqueo de este ítem. |
| 06 | &eRelicario Sagrado | `relics:holy_locket` | OBTENIBLE (drop) | Reliquia; botín de mazmorra. Existe `config/relics/holy_locket.json`. No crafteable → ver §4 (aviso UX). |
| 06 | &6Tótem en el Amuleto (opcional) | `minecraft:totem_of_undying` | OBTENIBLE (drop) | Drop de capitán saqueador (vanilla). `charmofundying-server.toml` solo cambia dónde funciona, no su obtención. |
| 07 | &5El Portal Prohibido | `minecraft:diamond` ×4 | OBTENIBLE | Vanilla. |
| 07 | &5Entrar al Crepúsculo | *dimension* | n/a | Tarea tipo `dimension`. |
| 07 | Mapa Mágico | `twilightforest:magic_map_focus` | OBTENIBLE | Crafteable. `twilightforest-common.toml`: los flags `disableUncrafting*` son todos `false` y solo afectan a la mesa de descrafteo, no a este ítem. |
| 07 | &cEl Naga / &cEl Lich (opcional) | *checkmark* | n/a | No son tareas `item`. |
| 08 | &5La Mesa Mejorada | `minecraft:enchanting_table` | OBTENIBLE | Vanilla. |
| 08 | &cEstanterías Infernales | `apotheosis:hellshelf` ×4 | OBTENIBLE | Módulo Enchantment activo (ver §3). Crafteable. |
| 08 | Biblioteca de Encantamientos | `apotheosis:library` | OBTENIBLE | Crafteable. |
| 08 | &6Botín con Afijos | `apotheosis:common_material` ×4 | OBTENIBLE (drop) | Módulo Adventure activo. Cae de mobs/botín afijado. |
| 08 | Mesa de Reforja | `apotheosis:reforging_table` | OBTENIBLE | Crafteable. |
| 08 | &bGemas y Engarces | `apotheosis:gem_cutting_table`, `apotheosis:sigil_of_socketing` | OBTENIBLE | Crafteables (2 tareas). Módulo Adventure activo. |
| 08 | &d&lMaterial Mítico | `apotheosis:mythic_material` | OBTENIBLE (drop) | Drop de jefes Invasores/mejor botín. Diseño endgame. |
| 08 | &aEncantamientos Nuevos (opcional) | *checkmark* | n/a | No es tarea `item`. |
| 09 | &6Metal Antiguo | `cataclysm:ancient_metal_ingot` ×3 | OBTENIBLE (botín) | Estructuras Cataclysm. `cataclysm.toml` no deshabilita ítems. |
| 09 | &eMaledictus | `cataclysm:wrath_of_the_desert` | OBTENIBLE (drop) | Drop del jefe Maledictus. |
| 09 | &cMonstruosidad de Netherita | `cataclysm:monstrous_horn` | OBTENIBLE (drop) | Drop del jefe. |
| 09 | &6&lIGNIS | `cataclysm:ignitium_ingot` ×2 | OBTENIBLE (drop+craft) | Material del jefe Ignis. |
| 09 | Armadura de Ignitium | `cataclysm:ignitium_chestplate` | OBTENIBLE | Crafteable con ignitium. |
| 09 | &3El Heraldo (opcional) | *checkmark* | n/a | No es tarea `item`. |
| 09 | &5&lGUARDIÁN DEL END | `cataclysm:void_core` | OBTENIBLE (drop) | Drop del jefe final. |
| 09 | &5&lLA FORJA DEL VACÍO | `cataclysm:void_forge` | OBTENIBLE | Crafteable con void_core. |
| 09 | &eLos Jefes Ocultos (opcional) | *checkmark* | n/a | No es tarea `item`. |
| 10 | &3La Ciudad Silenciosa | `minecraft:echo_shard` ×3 | OBTENIBLE (botín) | Ancient City (vanilla). |
| 10 | &5&lEL CORAZÓN DEL GUARDIÁN | `deeperdarker:heart_of_the_deep` | OBTENIBLE (drop) | Drop del Warden. `deeperdarker-common.toml` no deshabilita ítems. |
| 10 | &3La Llave | `deeperdarker:reinforced_echo_shard` | OBTENIBLE | Crafteable (echo_shard + heart_of_the_deep). |
| 10 | &5&lEL OTRO LADO | *dimension* | n/a | Tarea tipo `dimension`. |
| 10 | &bResonarium | `deeperdarker:resonarium` ×8 | OBTENIBLE (minería) | Mena en la dimensión Otherside. |
| 10 | &c&lEL ACECHADOR | `deeperdarker:warden_carapace` | OBTENIBLE (drop) | Drop del Stalker/Warden. |
| 10 | &6Armadura de Guardián | `deeperdarker:warden_chestplate` | OBTENIBLE | Crafteable con carapace + plantilla. |
| 10 | &5Elitros de Almas (opcional) | `deeperdarker:soul_elytra` | OBTENIBLE | Crafteable. Misión `optional:true`. |
| 11 | &6&lCazador Entre Mundos | *checkmark* | n/a | No es tarea `item`. |
| 11 | &cCazar a Ignis / Dragón / Naga / Warden | *kill* | n/a | Tareas tipo `kill` (entidad), no `item`. Las entidades existen. |
| 11 | &6&lCAZADOR ENTRE MUNDOS | *checkmark* | n/a | No es tarea `item`. |

> Nota: las recompensas (`rewards`) no se auditan como "obtenibles por el jugador"; son entregadas por el sistema de quests y no dependen de recetas. Solo se auditaron las `tasks`.

---

## 3. Evidencia clave (citas de archivo)

- **`config/tombstone-server.toml`** — sección `[allowedMagicItems]`:
  `allow_grave_key = true` → la Llave de Tumba está HABILITADA (el fallo histórico estaba aquí en `false`).
  Además `[magic_item] disable_enchanted_grave_key_recipe = false` y `chance_enchanted_grave_key = 0` (afectan solo a la variante *encantada*, no a la básica que pide la misión).
- **`config/apotheosis/apotheosis.cfg`** — `general { ... }`: todos los módulos en `true`, incluidos `"Enable Enchantment Module"=true` (hellshelf, library) y `"Enable Adventure Module"=true` (common_material, mythic_material, gem_cutting_table, sigil_of_socketing, reforging_table). Sin estos módulos, esos ítems no existirían; están activos.
- **`config/iceandfire-common.toml`** — sin toggles de ítem/receta. `Dragons Drop Skull/Heart/Blood = true` y `Generate Dragon Skeletons/Cave/Roost` activos → dragonbone, escamas y corazón de dragón obtenibles.
- **`config/chococraft-common.toml`** — sin flags de ítem; solo spawn/tameo/nombres. Ítems de chocobo crafteables.
- **`config/waystones-common.toml`** — `[restrictions] restrictToCreative = false` con comentario explícito "This does NOT disable the crafting recipe".
- **`config/toolbelt-common.toml`** — `enableGridCraftingRecipes = "AUTO"`, `anvilUpgrading = "AUTO"`: `belt` crafteable (vía rejilla o Sewing Kit si presente).
- **`config/farmersdelight-common.toml`** — `[crafting] enableCookingPotRecipeBook = true`; `cooking_pot` crafteable.
- **`config/reliquary-common.toml`** — contiene `dropCraftingRecipesEnabled = false` y `charms = false`, pero afectan SOLO a ítems del mod Reliquary, que **no se piden en los capítulos 01–11** (Reliquary tiene su propio capítulo de guía `g14`, fuera de alcance). No impacta misiones principales.
- **`config/deeperdarker-common.toml`**, **`config/cataclysm.toml`**, **`config/mowziesmobs-common.toml`**, **`config/twilightforest-common.toml`** — revisados; ningún flag deshabilita un ítem pedido. En twilightforest los `disableUncrafting*` están en `false` y solo afectan a la mesa de descrafteo.
- **`config/irons_spellbooks`** — no existe toml de servidor con toggles de ítem/receta; los spellbooks son crafteables por defecto.

---

## 4. Conclusiones y recomendaciones (nada implementado)

1. **No hay regresión activa.** Ninguna tarea `item` de los capítulos 01–11 está bloqueada por config a día de hoy. El caso "Seguro de Vida" ya está arreglado (`allow_grave_key=true`).

2. **"Seguro de Vida" (cap. 01) — mejorar UX, no es bug.** El jugador que preguntó "no me sale crafteo para la llave" tiene razón: la Llave de Tumba **no tiene receta visible evidente**; aparece al morir. Opciones (que decide el orquestador):
   - (a) Dejar la tarea como está y **ajustar la descripción** para dejar claro "aparece en tu inventario la primera vez que mueres" (la descripción ya lo dice, pero la tarea no da pista de crafteo → confunde).
   - (b) Cambiar la tarea a un `observe`/`checkmark` o a un ítem con receta clara si se quiere evitar depender de que el jugador muera.
   - **Recomendación:** opción (a), mantener `grave_key` y reforzar el texto. Es el diseño intencionado del mod.

3. **Protección anti-regresión futura (lo que pidió el usuario).** La causa raíz del fallo histórico fue revisar solo *existencia de ID*, no *obtenibilidad*. Para que no se repita, cualquier revisión futura de misiones debería comprobar, por cada ítem pedido:
   - que el ID exista;
   - que ningún flag `allow_*/enable_*/disable_*_recipe` del mod dueño esté en un valor que lo bloquee;
   - que tenga al menos una vía (receta activa / drop / compra).
   Los mods del pack con flags de este tipo que hay que vigilar son principalmente **tombstone** (`allowedMagicItems`, `disable_*_recipe`), **apotheosis** (módulos en `apotheosis.cfg`), **reliquary** (`dropCraftingRecipesEnabled`, `charms`) y **toolbelt/waystones/farmersdelight** (toggles de receta). Se sugiere guardar esta lista como checklist.

4. **Ítems "drop-only" (aviso, no fallo).** Varias misiones piden ítems no crafteables que caen de jefes/mobs o botín (ej. `apotheosis:common_material`, `apotheosis:mythic_material`, `cataclysm:*` de jefes, `deeperdarker:heart_of_the_deep`, `relics:holy_locket`, `minecraft:totem_of_undying`). Son obtenibles y es el diseño previsto, pero en JEI no mostrarán receta. Si se quiere minimizar dudas de jugadores, conviene que la descripción de cada una diga explícitamente "cae de X" (la mayoría ya lo hacen). No requieren cambio de config.

**Resultado neto:** el pack de misiones principal está sano respecto a obtenibilidad. La única acción recomendada de verdad es de redacción/UX en "Seguro de Vida", más adoptar la checklist anti-regresión. No se recomienda tocar ninguna config de mod por este motivo.
