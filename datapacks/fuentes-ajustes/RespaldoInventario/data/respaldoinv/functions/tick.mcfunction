# Cada 6 segundos (120 ticks) toma un respaldo de todos los jugadores.
scoreboard players add #timer ri_sys 1
execute if score #timer ri_sys matches 120.. run function respaldoinv:respaldar
execute if score #timer ri_sys matches 120.. run scoreboard players set #timer ri_sys 0
