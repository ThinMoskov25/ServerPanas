# Posicionado en el jugador. Prueba varios puntos alrededor (24-40 bloques).
# En cada punto llama a buscar_suelo, que recorre alturas buscando hierba.
execute positioned ~24 ~ ~8 run function animalesvanilla:buscar_suelo
execute positioned ~-24 ~ ~-8 run function animalesvanilla:buscar_suelo
execute positioned ~8 ~ ~-28 run function animalesvanilla:buscar_suelo
execute positioned ~-8 ~ ~28 run function animalesvanilla:buscar_suelo
execute positioned ~30 ~ ~-20 run function animalesvanilla:buscar_suelo
execute positioned ~-30 ~ ~20 run function animalesvanilla:buscar_suelo
