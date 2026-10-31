# Dragon de Ender: base 200 -> 400. El dragon no acepta instant_health,
# asi que se le fija la vida directamente con /data tras subir el maximo.
attribute @s minecraft:generic.max_health base set 400
tag @s add jd_reforzado
data modify entity @s Health set value 400.0f
