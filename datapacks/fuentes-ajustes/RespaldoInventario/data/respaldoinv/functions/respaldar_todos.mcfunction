# Reinicia la lista de respaldos y vuelve a llenarla con el inventario
# actual de cada jugador conectado, etiquetado con su nombre.
data modify storage respaldoinv:data backups set value []
execute as @a run function respaldoinv:respaldar_uno
