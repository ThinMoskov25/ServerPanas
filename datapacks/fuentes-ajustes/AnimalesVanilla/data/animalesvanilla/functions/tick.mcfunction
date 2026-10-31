# Corre cada tick. Ejecuta la repoblacion 1 vez cada 30 segundos (600 ticks).
scoreboard players add #av_timer av_sys 1
execute if score #av_timer av_sys matches 600.. run function animalesvanilla:repoblar
execute if score #av_timer av_sys matches 600.. run scoreboard players set #av_timer av_sys 0
