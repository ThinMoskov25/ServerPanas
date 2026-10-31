# Posicionado en un punto (x,z fijos; y = la del jugador). Busca hierba con
# aire encima probando varias alturas relativas, y spawnea en la primera valida.
# Para evitar spawnear varios en la misma columna, usa un flag por ejecucion.
scoreboard players set #av_spawned av_sys 0

execute if score #av_spawned av_sys matches 0 positioned ~ ~5 ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
execute if score #av_spawned av_sys matches 0 positioned ~ ~3 ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
execute if score #av_spawned av_sys matches 0 positioned ~ ~1 ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
execute if score #av_spawned av_sys matches 0 positioned ~ ~ ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
execute if score #av_spawned av_sys matches 0 positioned ~ ~-1 ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
execute if score #av_spawned av_sys matches 0 positioned ~ ~-3 ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
execute if score #av_spawned av_sys matches 0 positioned ~ ~-5 ~ if block ~ ~-1 ~ minecraft:grass_block if block ~ ~ ~ minecraft:air run function animalesvanilla:spawn_aqui
