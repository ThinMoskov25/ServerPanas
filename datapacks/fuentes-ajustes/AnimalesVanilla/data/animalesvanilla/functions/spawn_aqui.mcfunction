# Spawnea animales pasivos en la posicion actual (suelo valido ya verificado).
# 1.20.1 NO tiene el comando /random, asi que se usa un contador rotativo
# (#av_rot) que va 0->1->2->3->0 para variar el tipo de animal.
scoreboard players set #av_spawned av_sys 1

scoreboard players add #av_rot av_sys 1
scoreboard players operation #av_rot av_sys %= #av_cuatro av_sys

execute if score #av_rot av_sys matches 0 run summon minecraft:cow ~ ~ ~
execute if score #av_rot av_sys matches 1 run summon minecraft:sheep ~ ~ ~
execute if score #av_rot av_sys matches 2 run summon minecraft:pig ~ ~ ~
execute if score #av_rot av_sys matches 3 run summon minecraft:chicken ~ ~ ~

# Segundo animal del mismo tipo al lado, para formar grupos como en vanilla.
execute if score #av_rot av_sys matches 0 run summon minecraft:cow ~1 ~ ~
execute if score #av_rot av_sys matches 1 run summon minecraft:sheep ~1 ~ ~
execute if score #av_rot av_sys matches 2 run summon minecraft:pig ~1 ~ ~
execute if score #av_rot av_sys matches 3 run summon minecraft:chicken ~1 ~ ~
