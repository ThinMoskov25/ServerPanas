# Para cada jugador cuyo contador de muertes sea >= 1, registrar la muerte
# y resetear su contador. Esto corre cada tick pero solo actua al morir.
execute as @a[scores={rm_muertes=1..}] at @s run function registromuertes:registrar
