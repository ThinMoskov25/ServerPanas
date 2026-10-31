# Crea una nueva "foto" con el inventario de todos los jugadores conectados
# y la anade al historial. Mantiene como maximo 20 fotos (borra la mas vieja).

# 1. Preparar foto nueva vacia en tmp
data modify storage respaldoinv:data foto set value {jugadores:[]}

# 2. Cada jugador anade su inventario+curios a la foto
execute as @a run function respaldoinv:respaldar_uno

# 3. Anadir la foto al historial
data modify storage respaldoinv:data historial append from storage respaldoinv:data foto

# 4. Si el historial supera 20 fotos, borrar la mas antigua (indice 0)
execute if data storage respaldoinv:data historial[20] run data remove storage respaldoinv:data historial[0]
