# Reinicia el reloj y, si hay alguien conectado, guarda una copia.
scoreboard players set #t hi_sys 0
execute if entity @a run function historialinv:copiar_todos
