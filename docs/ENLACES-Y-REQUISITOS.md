# Enlaces y requisitos — SERVER PANAS

Todo lo que tú y tus amigos necesitáis descargar, con enlaces oficiales.

---

## Datos del servidor

```
Dirección : ShadowBlack77-rEez.exaroton.me:23574
Software  : Forge
Versión   : 1.20.1  (Forge 47.4.28 en el servidor)
```

⚠️ **El puerto `:23574` es obligatorio.** Es un puerto no estándar,
así que si alguien escribe solo `ShadowBlack77-rEez.exaroton.me` **no conectará**.
Hay que poner la dirección completa con los dos puntos y el número.

> El instalador ya mete el servidor en la lista de Multijugador, así que
> normalmente no hay que escribir nada.

---

## 1. Java 17 (obligatorio)

Minecraft 1.20.1 funciona con **Java 17**.

**Descarga oficial (Adoptium Temurin, gratis y sin registro):**
https://adoptium.net/temurin/releases/?version=17

- Sistema: Windows
- Arquitectura: x64
- Tipo de paquete: **JDK** o **JRE** (cualquiera sirve)
- Formato: `.msi` (el instalador automático)

**¿Lo necesito de verdad?** Si usas **CurseForge** o **TLauncher**, ambos
descargan su propio Java y suele funcionar sin instalar nada. Instálalo solo
si el juego se niega a arrancar con un error de Java.

**Comprobar qué Java tienes:** abre una consola y escribe `java -version`.

---

## 2. Forge 1.20.1 (obligatorio)

**Página oficial:**
https://files.minecraftforge.net/net/minecraftforge/index.html

Ahí elige **1.20.1** en la columna de la izquierda y descarga el **Installer**
de cualquier versión **47.x**.

**Enlace directo a la 47.4.0** (la que usa el pack):
```
https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-installer.jar
```

Al abrir el instalador, elige **"Install client"** y pulsa OK.

> **Sobre la diferencia de versión:** el servidor tiene Forge
> **47.4.28** y el pack usa **47.4.0**. Son la misma serie 47.4.x y son
> compatibles entre sí. No hace falta que coincidan exactamente.

> El `INSTALAR-CLIENTE.bat` puede descargar e iniciar este instalador por ti.

---

## 3. Launcher

### Opción A — Minecraft oficial (si tienes cuenta de pago)
https://www.minecraft.net/es-es/download

### Opción B — CurseForge (lo más cómodo, requiere cuenta premium)
https://www.curseforge.com/download/app

Con CurseForge solo tienes que importar el archivo
**`SERVER-PANAS v1.0.0.zip`** y se instala todo solo: Forge, los 110 mods,
las misiones y la traducción. Es el método recomendado si tienes Minecraft
original.

### Opción C — TLauncher (para quienes no tienen cuenta de pago)
https://tlauncher.org/

Al abrirlo: elige la versión **1.20.1** con **Forge**, deja que la instale,
cierra el juego, y después ejecuta `INSTALAR-CLIENTE.bat`.

> **Aviso honesto sobre TLauncher:** su instalador ha incluido software
> adicional en algunas versiones y conviene revisar las casillas durante la
> instalación. Una alternativa más limpia con el mismo resultado es
> **Prism Launcher** (https://prismlauncher.org/), que es de código abierto y
> permite perfiles sin cuenta de pago. Funciona igual con este pack.

---

## 4. RAM: esto es lo que más falla

El pack lleva 110 mods, incluidos Ice and Fire, Cataclysm y Apotheosis.

```
Mínimo      : 6 GB
Recomendado : 8 GB
```

Con 4 GB **crashea al arrancar**. Es el error número uno.

**Cómo subirla:**

| Launcher | Dónde |
|---|---|
| TLauncher | Ajustes → Configuración de Java → Memoria asignada |
| CurseForge | los 3 puntos de la instancia → Profile Options → Allocated Memory |
| Oficial | Instalaciones → Editar → Más opciones → cambia `-Xmx2G` por `-Xmx8G` |

---

## 5. Resumen por tipo de jugador

### Tienes Minecraft original
1. Instala **CurseForge**
2. Importa `SERVER-PANAS v1.0.0.zip`
3. Sube la RAM a 8 GB
4. Jugar

### No tienes Minecraft original
1. Instala **TLauncher** (o Prism Launcher)
2. Instala la versión **1.20.1 con Forge 47.x** desde el launcher
3. Ejecuta `INSTALAR-CLIENTE.bat`
4. Sube la RAM a 8 GB
5. Jugar
