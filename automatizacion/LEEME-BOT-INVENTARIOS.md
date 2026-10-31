# Bot de respaldo de inventarios - SERVER PANAS

Este bot evita el problema de "alguien perdio sus cosas y no hay forma de saber que tenia".
Toma fotos (snapshots) del inventario de cada jugador cada pocos minutos, con fecha y hora,
leyendo los archivos de datos del mundo (`world/playerdata/*.dat`) via la API de Exaroton.

## Como funciona

- Solo captura cuando el servidor esta ENCENDIDO.
- Guarda cada snapshot en: `snapshots\<fecha>\<hora>\<uuid>.dat`
- Mantiene historial: puedes volver a cualquier momento respaldado.
- Registra actividad en `snapshots\bot.log`.

## Como usarlo

### Opcion A - Ventana abierta (lo mas simple)
Doble clic en **INICIAR-BOT.bat**. Deja la ventana abierta. Respaldara cada 10 min.

### Opcion B - Captura puntual (una sola foto ahora)
```
powershell -ExecutionPolicy Bypass -File .\bot-inventarios.ps1 -Once
```

### Opcion C - Automatico al encender el PC (recomendado)
Registrar en el Programador de tareas de Windows para que arranque solo:
1. Abre "Programador de tareas".
2. Crear tarea basica -> nombre "Bot Inventarios Server Panas".
3. Desencadenador: "Al iniciar sesion".
4. Accion: Iniciar un programa ->
   - Programa: `powershell.exe`
   - Argumentos: `-WindowStyle Hidden -ExecutionPolicy Bypass -File "<ruta>\bot-inventarios.ps1" -IntervaloMin 10`
   - (reemplaza `<ruta>` por la ruta completa a esta carpeta `automatizacion`)
5. Guardar. A partir de ahi corre solo en segundo plano.

## Si alguien pierde algo

1. Mira el UUID del jugador (aparece en los nombres de archivo de `snapshots`).
2. Lee su ultimo inventario respaldado:
   ```
   powershell -ExecutionPolicy Bypass -File .\leer-inventario.ps1 -Uuid <uuid>
   ```
   Te muestra la lista de items/IDs que tenia.
3. Con esa lista, se le reponen los items por consola (give) o restaurando su `.dat`.

## Restaurar el inventario completo de un jugador (avanzado)

Si quieres devolver EXACTAMENTE el inventario de un snapshot:
- Se sube el `.dat` guardado de vuelta a `world/playerdata/<uuid>.dat` con el jugador
  DESCONECTADO, y al reconectar tendra ese inventario.
- IMPORTANTE: hazlo con el jugador offline, o el servidor sobrescribe el archivo al salir.
- Pide ayuda para este paso para no corromper datos.

## Limitaciones honestas

- El snapshot es cada 10 min: si alguien pierde algo justo despues de un snapshot,
  se recupera el estado de hasta 10 min antes (no el segundo exacto). Puedes bajar el
  intervalo a 5 min si quieres mas precision (mas descargas).
- El bot debe estar corriendo (ventana abierta o tarea programada) para respaldar.
  Si el PC esta apagado, no captura.
