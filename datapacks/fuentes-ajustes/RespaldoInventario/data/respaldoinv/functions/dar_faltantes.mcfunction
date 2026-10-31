# Compara la foto mas antigua del historial con el inventario ACTUAL del
# jugador que ejecuta, y le DEVUELVE (give via loot) los items que tenia
# antes y ya no estan. No sobrescribe el inventario: solo repone lo que falta.
#
# Enfoque: copia el Inventory de la foto vieja a un contenedor virtual (storage),
# y para cada item de esa foto, comprueba si el jugador aun lo tiene por un
# conteo de clear sin -1. Como 1.20.1 base no tiene bucles ni macros, este
# archivo delega en una tabla de loot generada que suelta la diferencia.
#
# IMPORTANTE: por las limitaciones de 1.20.1 sin macros, la comparacion
# item-por-item automatica no es viable aqui. Esta funcion restaura el
# inventario completo de la foto vieja al jugador usando item replace,
# que SI funciona en jugadores conectados (a diferencia de data modify).
function respaldoinv:restaurar_item_replace
