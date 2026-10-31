# Refuerza la vida maxima (x2) de los jefes objetivo la primera vez que aparecen.
# Usa el tag "jd_reforzado" para no aplicarlo dos veces al mismo jefe.
# La vida nueva se fija con un modificador de atributo (add_value sobre el base),
# luego se cura al maximo para que suba de golpe.

# --- Ignis (Cataclysm) : base ~550 -> +550 = ~1100 ---
execute as @e[type=cataclysm:ignis,tag=!jd_reforzado] run function jefesduros:buff/ignis

# --- Dragon de Ender (vanilla) : base 200 -> +200 = 400 ---
execute as @e[type=minecraft:ender_dragon,tag=!jd_reforzado] run function jefesduros:buff/dragon

# --- Naga (Twilight Forest) : base ~120 -> +120 = ~240 ---
execute as @e[type=twilightforest:naga,tag=!jd_reforzado] run function jefesduros:buff/naga

# --- Warden (vanilla) : base 500 -> +500 = 1000 ---
execute as @e[type=minecraft:warden,tag=!jd_reforzado] run function jefesduros:buff/warden
