# Ejecutado AS un jugador. Anade su UUID, inventario, ender y curios
# a la foto actual (ultimo elemento que se esta construyendo).
data modify storage respaldoinv:data uno set value {UUID:[I;0,0,0,0], Inventory:[], EnderItems:[], Curios:{}}
data modify storage respaldoinv:data uno.UUID set from entity @s UUID
data modify storage respaldoinv:data uno.Inventory set from entity @s Inventory
data modify storage respaldoinv:data uno.EnderItems set from entity @s EnderItems
data modify storage respaldoinv:data uno.Curios set from entity @s ForgeCaps."curios:inventory"
data modify storage respaldoinv:data foto.jugadores append from storage respaldoinv:data uno
