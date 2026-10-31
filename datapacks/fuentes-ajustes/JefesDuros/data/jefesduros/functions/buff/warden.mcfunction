# Warden: base 500 -> 1000. Cura a tope.
attribute @s minecraft:generic.max_health base set 1000
tag @s add jd_reforzado
effect give @s minecraft:instant_health 1 255 true
data modify entity @s Health set value 1000.0f
