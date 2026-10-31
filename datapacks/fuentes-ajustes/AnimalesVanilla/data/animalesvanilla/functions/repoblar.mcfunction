# Para cada jugador en el Overworld, si hay pocos animales pasivos cerca,
# intenta generar un grupo pequeno en superficie sobre hierba.
# El limite evita saturar: solo repuebla si hay MENOS de 8 animales en 48 bloques.
execute as @a[gamemode=!spectator] at @s if dimension minecraft:overworld unless entity @e[type=#animalesvanilla:pasivos,distance=..48,limit=8] run function animalesvanilla:intento_spawn
