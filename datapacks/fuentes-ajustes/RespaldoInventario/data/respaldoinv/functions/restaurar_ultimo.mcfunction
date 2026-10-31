# Restaura al jugador que ejecuta el inventario de la foto MAS ANTIGUA
# del historial (la mas lejana en el tiempo, hasta 20 fotos / ~2 min atras).
# Copia Inventory, EnderItems y Curios de su entrada en historial[0].
# OJO: busca su entrada por estar en la foto; como no hay macros, se asume
# que el jugador que ejecuta es el primero de la foto si juega solo. Para
# multijugador se usa restaurar por indice (ver restaurar_de).
data modify entity @s Inventory set from storage respaldoinv:data historial[0].jugadores[0].Inventory
data modify entity @s EnderItems set from storage respaldoinv:data historial[0].jugadores[0].EnderItems
