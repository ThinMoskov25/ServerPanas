# Reloj interno. El tick decide cuando respaldar.
scoreboard objectives add ri_sys dummy
scoreboard players set #timer ri_sys 0
# Inicializa el historial como lista vacia si no existe.
data modify storage respaldoinv:data historial append value {placeholder:1b}
data remove storage respaldoinv:data historial[-1]
