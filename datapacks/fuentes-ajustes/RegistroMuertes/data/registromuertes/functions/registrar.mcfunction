# Ejecutado AS/AT el jugador que acaba de morir.
# Guarda las coordenadas exactas de la muerte en scoreboards, para poder
# teletransportar al jugador a su punto de muerte luego, aunque la tumba
# falle o el log se borre. Minecraft ya registra la causa en el log del
# servidor ("X was slain by..."), que el monitor local guarda permanente.
execute store result score @s rm_x run data get entity @s Pos[0]
execute store result score @s rm_y run data get entity @s Pos[1]
execute store result score @s rm_z run data get entity @s Pos[2]

# Resetear el contador para detectar la proxima muerte.
scoreboard players set @s rm_muertes 0
