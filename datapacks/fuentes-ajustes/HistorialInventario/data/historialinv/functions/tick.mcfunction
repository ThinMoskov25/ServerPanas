# Cada 30 segundos (600 ticks) toma una copia de todos los jugadores conectados.
scoreboard players add #t hi_sys 1
execute if score #t hi_sys matches 600.. run function historialinv:copiar
