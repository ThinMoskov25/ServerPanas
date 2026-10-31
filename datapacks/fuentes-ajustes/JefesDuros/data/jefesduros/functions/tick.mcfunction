# Corre cada tick. Para aligerar, solo ejecuta el chequeo 1 vez por segundo.
scoreboard players add #jd_timer jd_sys 1
execute if score #jd_timer jd_sys matches 20.. run function jefesduros:reforzar
execute if score #jd_timer jd_sys matches 20.. run scoreboard players set #jd_timer jd_sys 0
