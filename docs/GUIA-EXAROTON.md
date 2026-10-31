# GUÍA DE EXAROTON — SERVER PANAS

Tu servidor ya está creado y con el software correcto:

```
Dirección : ShadowBlack77-rEez.exaroton.me:23574
Software  : Forge             ✅ correcto
Versión   : 1.20.1 (47.4.28)  ✅ correcto
```

⚠️ **El puerto `:23574` es obligatorio.** Si alguien escribe solo el dominio,
no conecta.

---

## ⚠️ HAZ LOS PASOS EN ESTE ORDEN

El paso 1 **borra la whitelist y los datos de jugador**. Si lo haces después
de invitar a tus panas, hay que rehacerlo todo. Respeta el orden.

---

## PASO 1 — Activar "Cracked" (ANTES DE TODO LO DEMÁS)

En exaroton, el `online-mode=false` se llama **Cracked**. Es lo que permite
que entren tus amigos de TLauncher.

1. Panel → **Options** / **Opciones**
2. Busca **Cracked** y actívala
3. Guarda y **reinicia** el servidor (todo cambio en Options lo exige)

[La documentación de exaroton sobre las opciones del servidor](https://support.exaroton.com/hc/en-us/articles/360019857698-The-options-of-your-server-explained)
advierte de esto, y es importante:

> Cambiar esta opción produce cambios de fondo en el servidor. El OP y la
> whitelist pueden dejar de funcionar, y los datos de jugador (inventario,
> posición, logros) probablemente se pierdan. Para que el OP y la whitelist
> vuelvan a funcionar tras cambiarla, hay que **quitar a todos los jugadores
> de esas listas y volverlos a añadir**.
>
> *(Contenido reformulado para cumplir con restricciones de licencia.)*

El motivo técnico: en modo offline los UUID se generan a partir del nombre en
vez de venir de Mojang, así que todas las listas que guardan UUID dejan de
coincidir.

**Por eso esto va primero, con el servidor vacío.** Así no pierdes nada.

---

## PASO 2 — Subir los 99 mods

Aquí sí se arrastran los archivos. exaroton lo permite, a diferencia de Aternos.

1. Panel → **Files** / **Archivos** → carpeta **`mods`**
   (atajo directo: https://exaroton.com/files/mods)
2. Pulsa **Upload**, o arrastra los archivos directamente
3. Selecciona **todos** los `.jar` de:
   ```
   ServerPanas\servidor\mods\
   ```
   Son **99 archivos**, 363 MB en total.
4. Comprueba que la carpeta liste 99 `.jar`

> La carpeta se sigue llamando `servidor` de cuando el plan era
> Aternos. El contenido es correcto, solo el nombre quedó viejo.

**No subas los de `cliente\mods`** (son 110). Los 11 extra son de interfaz y
render (JEI, Jade, mapas, zoom, Embeddium) y en el servidor no sirven de nada.
Están listados al final de `LISTA-MODS-ATERNOS.md`.

> **Si ya habías subido 98 y te dio `mismatched mod channel list`:** faltaban
> estos dos, que los tenía mal clasificados como solo-cliente. Súbelos y
> reinicia:
> ```
> inventorysorter-1.20.1-23.0.8.jar
> xaeros_waystones_compatibility-1.1 - 1.20.1.jar
> ```
> Registran canales de red obligatorios, así que Forge los exige en ambos
> lados. Ya están en `servidor\mods\`.

> Si la subida de 99 archivos se corta a medias, sube por lotes de 20-30 y
> comprueba el total al final. Lo que importa es que acaben los 99 ahí.

---

## PASO 3 — Subir la configuración (misiones y kit inicial)

Sin esto **nadie verá las misiones** y nadie aparecerá con el libro.

En **Files**, sube la carpeta `config` completa desde:

```
ServerPanas\servidor\config\
```

Son 16 archivos y la estructura importa:

```
config/ftbquests/quests/data.snbt
config/ftbquests/quests/chapter_groups.snbt
config/ftbquests/quests/chapters/   ← los 10 capítulos
config/starterkit.json5
config/starterkit/kits/starterkit.txt
config/starterkit/descriptions/starterkit.txt
config/voicechat/voicechat-server.properties   ← chat de voz, ver paso 10
```

Si ya subiste esto a Aternos, aquí hay que volver a hacerlo: son servidores
distintos y no comparten archivos.

---

## PASO 4 — Poner la RAM en 6 GB

Panel → **Options** → el selector de RAM (va de 2 a 16 GB).

**6 GB** es mi recomendación para este pack. Es una estimación razonada, no
algo medido: son 99 mods con Terralith, los YUNG's completos, When Dungeons
Arise, Repurposed Structures y Structory, y la generación de estructuras es
lo que más memoria consume.

Lo que te cuesta cada opción, a 1 crédito por GB y hora:

| RAM | Créditos/hora | 300 créditos (3 €) rinden |
|---|---|---|
| 4 GB | 4 | 75 horas |
| **6 GB** | **6** | **50 horas** |
| 8 GB | 8 | 37,5 horas |

Si con 6 GB va fluido, pruébalo en 4 GB una sesión y compara. Cada GB que
bajes te da un 25% más de horas.

---

## PASO 5 — AutoStop y AutoStart (esto te ahorra plata de verdad)

Las dos son **gratis** y están en **Options**, juntas.

**AutoStop** apaga el servidor cuando se queda vacío. Sin esto pagas por las
horas que el servidor pasa prendido sin nadie dentro, que es la forma más
tonta de quemar créditos. Con AutoStop activo verás un contador en la barra
de estado que indica cuándo se apagará si nadie entra.

**AutoStart** lo prende cuando alguien intenta conectarse. Tus panas no
tienen que pedirte permiso para jugar.

> **Limita AutoStart a la whitelist.** exaroton permite restringir los
> arranques automáticos a jugadores de la whitelist o a operadores. Sin eso,
> cualquiera que conozca tu dirección puede prenderte el servidor y gastarte
> créditos. Los jugadores baneados nunca pueden arrancarlo.
> [Documentación de AutoStart](https://support.exaroton.com/hc/en-us/articles/15338752897181-Start-your-server-automatically-when-a-player-tries-to-join)

También existe una **Offline waiting lobby**, en las mismas opciones: mete al
jugador en una sala de espera mientras el servidor arranca, en vez de darle
error de conexión. Con 99 mods el arranque tarda varios minutos, así que
vale la pena activarla.

---

## PASO 6 — Whitelist (ahora sí, después del paso 1)

Esta es tu única protección real, y además lo que limita el AutoStart.

1. Panel → **Players** → **Whitelist**
   (atajo: https://exaroton.com/players/whitelist)
2. Actívala en **Options** si no lo está
3. Añade los **nombres exactos** de Minecraft de cada pana
4. Añádete tú también

**Si ya habías añadido a alguien antes del paso 1, bórralo y vuélvelo a
añadir.** Su entrada tiene el UUID viejo de Mojang y ya no sirve.

### Por qué la whitelist no es opcional

Con **Cracked** activado el servidor **no puede distinguir** entre un jugador
premium y uno de TLauncher. No es una limitación de exaroton ni del pack: ese
ajuste desactiva la comprobación contra los servidores de Mojang, y la
desactiva **para todos**.

| | Con Cracked activado |
|---|---|
| Entran los de TLauncher | ✅ sí |
| Entran los premium | ✅ sí |
| El servidor verifica la cuenta | ❌ no, para nadie |
| UUID estables | ⚠️ se generan del nombre |
| Alguien puede entrar con el nombre de otro | ⚠️ sí, si sabe la dirección |

Alguien tendría que conocer a la vez tu dirección con puerto **y** el nombre
exacto de uno de tus panas. Para un grupo privado alcanza.

**Lo que no se puede hacer:** un mod de login con contraseña. Esos son
plugins de Bukkit/Paper, y con Forge no corren. Busqué entre tus 24 modpacks
y no hay ninguno de autenticación para Forge 1.20.1.

### Las skins

En modo offline las skins de Mojang no cargan y todos se ven como Steve.
Tienes `tl_skin_cape_forge_1.20_1.20.1-1.32.jar` en `.minecraft\back\1.20.1`
que lo arregla para TLauncher. Hay que ponerlo en **cliente y servidor**.
Dime si lo quieres y lo añado al pack.

---

## PASO 7 — Arrancar y mirar la consola

1. Panel → **Start**
2. Abre la **Console** y déjala a la vista

**Esto es lo importante:** es la primera vez que este pack se ejecuta de
verdad. Todo lo que te entregué está validado estructuralmente (dependencias,
IDs de items, JSON, misiones) pero nunca se ejecutó, porque desde mi entorno
no puedo correr Minecraft.

Con 99 mods el arranque tarda. Dale **3 a 5 minutos** antes de preocuparte.

Lo que busco si algo falla, en la consola:

| Lo que ves | Qué significa |
|---|---|
| `Missing or unsupported mandatory dependencies` | falta un mod del que otro depende |
| `NoClassDefFoundError` | dos mods con versiones incompatibles |
| `has failed to load correctly` | ese mod se cayó, el mensaje de abajo dice por qué |
| `Done (XXs)! For help, type "help"` | ✅ arrancó bien |

**Pásame la línea tal cual y te digo qué mod falta.** Ya resolví tres crashes
así durante la construcción: Croptopia pedía EpheroLib sin declararla,
Sophisticated Core chocaba con Reliquary, y Supplementaries pedía Amendments.

Los 5 créditos gratis del registro te dan unos 50 minutos a 6 GB. De sobra
para esta prueba, **sin pagar nada todavía**.

---

## PASO 8 — Comprobar desde tu PC

Con el servidor en verde:

```powershell
cd "ServerPanas"
.\SERVERPANAS.bat probar
```

Ya no hace falta pasarle argumentos: tiene tu dirección y puerto por defecto.
Te confirma DNS, puerto, versión real, MOTD y jugadores conectados.

---

## PASO 9 — Subir el datapack de estructuras

Sin esto verás muy pocas mazmorras. When Dungeons Arise viene con
`spacing: 60, separation: 50`, o sea **una estructura cada ~960 bloques**,
elegida al azar entre sus 31 estructuras grandes. Encontrar el barco pirata
concreto así te llevaría decenas de miles de bloques de exploración.

El datapack lo baja a `spacing: 26, separation: 14` (una cada ~416 bloques),
más del doble de densidad, sin saturar la generación.

1. Panel → **Files** → carpeta **`world`** → **`datapacks`**
   (si no existe `datapacks`, créala)
2. Sube el archivo:
   ```
   ServerPanas\servidor\datapacks\MasEstructuras-ServerPanas.zip
   ```
3. Reinicia el servidor
4. Comprueba con `/datapack list` en la consola: debe aparecer

### ⚠️ Solo afecta a terreno NUEVO

Un datapack de generación no reescribe chunks que ya existen. Lo que ya
exploraste se queda como está.

Tienes dos salidas:

**Resetear el mundo** (recomendado si llevan poco). Panel → **Worlds** →
borrar y crear de nuevo. Con el datapack puesto antes de generar, toda la
densidad nueva aplica desde el spawn.

**Seguir y explorar lejos.** Lo nuevo saldrá más denso en cuanto salgas del
radio ya generado, que con unos pocos jugadores suele ser de 1-2 mil bloques.

---

## PASO 10 — Arreglar el chat de voz

Si ves el icono del enchufe tachado abajo a la izquierda, el chat de voz no
está conectado. Tiene arreglo y es de una línea.

**El motivo:** Simple Voice Chat quiere su propio puerto UDP (el 24454 por
defecto), y exaroton **solo da un puerto por servidor**. Hay que decirle al
mod que use el mismo puerto del servidor.

1. **Para el servidor** (el archivo solo se puede editar offline)
2. Panel → **Files** → `config/voicechat/voicechat-server.properties`
3. Cambia:
   ```
   port=24454
   ```
   por:
   ```
   port=-1
   ```
4. Arranca el servidor

`-1` significa "usa el puerto del servidor de Minecraft", el 23574.

Ya te dejé el archivo listo en `servidor\config\voicechat\`, así que
también puedes subirlo y sobrescribir el que generó el servidor.

> **Limitación:** con un solo puerto no se puede tener dos mods de voz a la
> vez, ni un mod de voz junto con Geyser (el puente para Bedrock).
> [Documentación de exaroton](https://support.exaroton.com/hc/en-us/articles/360019857998-Installing-Voice-Chat-Mods-Minecraft-Java-Edition)

> **Si a un pana no le funciona la voz pero a los demás sí:** su proveedor de
> internet o su VPN le está bloqueando UDP. Pasa bastante con datos móviles.

---

## PASO 11 — Repartir el pack

| Pana | Qué le pasas |
|---|---|
| Tiene Minecraft original | `distribucion\SERVER-PANAS v1.0.0.zip` → importar en CurseForge |
| No tiene CurseForge (oficial, TLauncher o Prism) | `distribucion\SERVER-PANAS-INSTALADOR.zip` → ejecutar `INSTALAR-CLIENTE.bat` |

El servidor **ya aparece en su lista de Multijugador**: el paquete incluye un
`servers.dat` con tu dirección de exaroton.

Insísteles en los **8 GB de RAM** en el cliente. Con 4 GB crashea al arrancar
y es el fallo más común.

Son 350 MB cada uno, así que Drive, WeTransfer o USB. Discord no sirve.

---

## Lo que cuesta al mes de verdad

**Almacenamiento:** 10 créditos al mes por servidor, pero se descuenta con lo
que gastes corriéndolo. Si gastas 10 o más créditos en el mes, el
almacenamiento **te sale gratis**. Solo pagas si dejas el servidor parado un
mes entero. El límite es 10 GB por servidor y tus mods ocupan 363 MB, así que
espacio te sobra.

**Si van a dejar de jugar un buen rato:** haz un backup y borra el servidor.
Los backups van a tu proveedor de nube conectado y son gratis. Así no pagas
almacenamiento. Y ojo: si te quedas sin créditos, el servidor se borra al
final del periodo.

**El cobro es exacto al segundo**, redondeado a la centésima de crédito. Media
hora se cobra como media hora.

---

## Problemas típicos

**"Mod rejected" al conectar**
La lista de mods del cliente no coincide con la del servidor. El cliente debe
tener los 110 y el servidor los 99. Reinstala el cliente con el `.bat` sin
cambiar nada.

**No veo misiones**
No subiste `config/ftbquests`. Paso 3.

**No aparezco con el libro de misiones**
No subiste `config/starterkit`. Paso 3.

**La whitelist no funciona / perdí el OP**
Activaste **Cracked** después de armar las listas. Borra a todos de whitelist
y OP, y vuélvelos a añadir.

**Perdí mi inventario y mi posición**
Mismo motivo: tu UUID cambió al activar Cracked. Si ya habían jugado antes
de ese cambio, esos datos no vuelven.

**Lag o tirones**
Sube la RAM a 8 GB una sesión y compara. Si sigue, el primer mod que quitaría
es **Ice and Fire** (21 MB, genera nidos y estructuras por todo el mundo).
Hay que quitarlo del servidor **y** del cliente, y dos capítulos de misiones
(`04_senda_dragon` y parte de `05_monturas`) dependen de él. Dímelo y preparo
una versión ligera consistente.

**Se me van los créditos rápido**
Revisa que AutoStop esté activo y que AutoStart esté limitado a la whitelist.
Puedes ver a dónde se fue cada crédito en https://exaroton.com/credits/transactions

---

## Enlaces

- Panel: https://exaroton.com
- Archivos (mods): https://exaroton.com/files/mods
- Whitelist: https://exaroton.com/players/whitelist
- Transacciones: https://exaroton.com/credits/transactions
- [Precios](https://support.exaroton.com/hc/en-us/articles/360019687657-Pricing)
- [Instalar mods](https://support.exaroton.com/hc/en-us/articles/360019857818-Install-mods)
- [Opciones del servidor](https://support.exaroton.com/hc/en-us/articles/360019857698-The-options-of-your-server-explained)
- [AutoStop](https://support.exaroton.com/hc/en-us/articles/360019687297-Stop-your-server-automatically-when-it-s-empty)
- [AutoStart](https://support.exaroton.com/hc/en-us/articles/15338752897181-Start-your-server-automatically-when-a-player-tries-to-join)

---

## Ajustes de jugabilidad aplicados

Cambios pedidos tras la primera sesión de prueba.

### Talar árboles de un toque: mod ELIMINADO

Falling Trees ya no está en el pack. Primero lo había desactivado por
configuración, pero dejar un mod que no aporta solo suma peso y una cosa más
que puede fallar, así que se fue del todo.

Con él se fue **PandaLib**, su única dependencia. Verifiqué antes de borrarla
que ningún otro mod la usa: ni la declara en su `mods.toml`, ni la referencia
en sus clases.

```
cliente  : 112 → 110 mods
servidor : 101 →  99 mods
```

Revalidado después: **0 dependencias faltantes** en ambos lados, y ninguna
misión ni traducción los mencionaba.

Talar vuelve a ser bloque por bloque.

> **Esto cambia la lista de mods**, así que hay que reinstalar el cliente con
> los paquetes nuevos de `distribucion\`. Si alguien entra con la lista vieja
> le saldrá `mismatched mod channel list`.

### Llave de tumba: desactivada

`config/tombstone-server.toml` → `allow_grave_key = false`

Ya no te teletransporta a la tumba al morir. La tumba sigue apareciendo y
conserva todo, pero hay que ir caminando a recuperarla. Eso es lo que
devuelve algo de peso a morirse.

También en `config/tombstone-common.toml`:

| Ajuste | Antes | Ahora | Qué hace |
|---|---|---|---|
| `cooldown_teleport_death` | -1 | 1440 | el comando de teleport a la muerte queda a 1 vez/día |
| `cooldown_request_teleport` | -1 | 1440 | igual para el de pedir teleport |
| `teleport_dim` | true | false | no se teletransporta entre dimensiones |

`-1` significaba "sin límite". Eran otra vía de teleport gratis que dejaba la
llave desactivada sin efecto real.

**Si queda muy duro:** pon `cooldown_teleport_death = 60` (una vez por hora)
en vez de desactivar del todo.

### Más estructuras

Datapack `MasEstructuras-ServerPanas.zip`, paso 9. Densidad de When Dungeons
Arise más que duplicada.

### Aviso de JEI: quitado

El mensaje *"This unknown server does not provide recipes to JEI"* salía porque
JEI estaba solo en el cliente. Ahora JEI va también en el servidor, que manda
las recetas reales. El servidor pasó de 100 a **99 mods**.

No era un error, solo un aviso, pero en un servidor comunitario los textos
rojos asustan sin motivo.

---

## Las estructuras que trae el pack

Para que sepas qué buscar. De **When Dungeons Arise** (31 grandes, 6 menores):

**Barcos y aéreas:** `undead_pirate_ship` (el de la horda), `illager_galley`,
`illager_corsair`, `small_blimp`, `aviary`

**Grandes:** `coliseum`, `shiraz_palace`, `keep_kayra`, `monastery`,
`plague_asylum`, `infested_temple`, `abandoned_temple`, `foundry`,
`mechanical_nest`, `typhon`, `scorched_mines`, `mushroom_mines`,
`mining_system`, `bathhouse`, `lighthouse`, `illager_fort`,
`illager_windmill`, `bandit_towers`, `thornborn_towers`

**Pueblos y menores:** `bandit_village`, `mushroom_village`, `mushroom_house`,
`jungle_tree_house`, `greenwood_pub`, `fishing_hut`, `small_prairie_house`,
`illager_campsite`, `merchant_campsite`, `wishing_well`, `giant_mushroom`

**Jefes:** `heavenly_challenger`, `heavenly_conqueror`, `heavenly_rider`,
`ceryneian_hind`

Más las de **YUNG's** (mazmorras, fortalezas, templos del desierto y de la
jungla, minas, monumentos oceánicos, casas de bruja), **When Dungeons Arise**,
**Repurposed Structures**, **Structory**, **Terralith** y las mazmorras y jefes
de **L_Ender's Cataclysm**.

Si tras el reset del mundo sigues viendo pocas, dímelo y bajo más el spacing o
densifico también Repurposed Structures y Structory.
