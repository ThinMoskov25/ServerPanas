# Una "foto" = momento (gametime) + inventario completo de cada jugador conectado.
# Se guardan las ultimas 240 fotos (cada 30 s = 2 horas) en storage panelsp:hist,
# que esta en world/data/command_storage_panelsp.dat y el panel descarga.
data modify storage panelsp:hist foto set value {jugadores:[]}
execute store result storage panelsp:hist foto.gt long 1 run time query gametime
execute as @a run function historialinv:copiar_uno
data modify storage panelsp:hist copias append from storage panelsp:hist foto
execute if data storage panelsp:hist copias[240] run data remove storage panelsp:hist copias[0]
