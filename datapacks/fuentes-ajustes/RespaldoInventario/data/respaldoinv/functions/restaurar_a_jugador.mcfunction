# Restaura al jugador el inventario y curios de la foto mas antigua del
# historial (historial[0]). Se usa con el jugador DESCONECTADO para que
# tome efecto, o con item replace si esta conectado.
# NOTA: para jugador conectado, "data modify entity Inventory" esta bloqueado,
# por eso esta funcion se aplica mejor justo al reconectar.
data modify entity @s Inventory set from storage respaldoinv:data historial[0].jugadores[0].Inventory
data modify entity @s ForgeCaps."curios:inventory" set from storage respaldoinv:data historial[0].jugadores[0].Curios
