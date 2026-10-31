package com.serverpanas.dailyquests;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Pool de misiones del mod.
 *
 * IMPORTANTE: este mod NO registra ningun MenuType propio. La UI usa el cofre
 * VANILLA (MenuType.GENERIC_9x6 via ChestMenu), por lo que el cliente no
 * necesita el mod. Registrar un MenuType propio rompe el server-only.
 *
 * Hay dos usos del pool:
 *   1) La ROTACION DIARIA toma al azar varias misiones de {@link #POOL} (mezcla
 *      de los tres niveles), igual que siempre.
 *   2) Las MISIONES PERSONALIZADAS que un admin asigna con
 *      "/misiones dar <jugador> <facil|medio|dificil>" se eligen al azar dentro
 *      del pool del nivel indicado ({@link #POOL_FACIL}, {@link #POOL_MEDIO},
 *      {@link #POOL_DIFICIL}). El tipo (MINE/KILL/DELIVER) y el objetivo
 *      concreto los decide el azar dentro del nivel.
 */
public final class QuestRegistry {

    private QuestRegistry() {
    }

    private static final Random RNG = new Random();

    /**
     * Alias de ids ANTIGUOS (datos guardados antes de prefijar por nivel) a los
     * ids ACTUALES del pool. Permite que las misiones ya guardadas de los
     * jugadores vuelvan a resolver su QuestDef y muestren su icono real en el
     * cofre en vez del papel generico. Se amplia si aparecen mas ids legacy.
     */
    private static final java.util.Map<String, String> ALIAS = java.util.Map.ofEntries(
            java.util.Map.entry("minar_piedra", "facil_minar_piedra"),
            java.util.Map.entry("minar_carbon", "facil_minar_carbon"),
            java.util.Map.entry("matar_zombis", "facil_matar_zombis"),
            java.util.Map.entry("matar_aranas", "facil_matar_aranas"),
            java.util.Map.entry("entregar_trigo", "facil_entregar_trigo"),
            java.util.Map.entry("entregar_madera", "facil_entregar_madera"),
            java.util.Map.entry("minar_hierro", "medio_minar_hierro"),
            java.util.Map.entry("minar_oro", "medio_minar_oro"),
            java.util.Map.entry("matar_esqueletos", "medio_matar_esqueletos"),
            java.util.Map.entry("entregar_cuero", "medio_entregar_cuero"),
            java.util.Map.entry("entregar_hierro", "medio_entregar_hierro"),
            java.util.Map.entry("minar_diamante", "dificil_minar_diamante"),
            java.util.Map.entry("matar_creepers", "dificil_matar_creepers"),
            java.util.Map.entry("entregar_diamante", "dificil_entregar_diamante"),
            java.util.Map.entry("entregar_oro", "dificil_entregar_oro"));

    /** Niveles de dificultad para las misiones personalizadas. */
    public enum Nivel {
        FACIL,
        MEDIO,
        DIFICIL;

        /** Parsea "facil"/"medio"/"dificil" (sin acentos, cualquier caja). */
        public static Nivel desdeTexto(String texto) {
            if (texto == null) {
                return null;
            }
            switch (texto.trim().toLowerCase(Locale.ROOT)) {
                case "facil":
                    return FACIL;
                case "medio":
                    return MEDIO;
                case "dificil":
                    return DIFICIL;
                default:
                    return null;
            }
        }
    }

    // ======================================================================
    //  POOLS DE MISIONES POR DIFICULTAD
    // ======================================================================
    // Cada definicion tiene: id, tipo, objetivo (id del bloque/entidad/item),
    // cantidad, item que la representa en el cofre, titulo, descripcion y la
    // recompensa como lista de comandos de consola (%player% = nombre).
    //
    // La dificultad se refleja en el OBJETIVO (cantidad) y en la RECOMPENSA:
    //   FACIL   -> objetivos cortos, recompensa pequena (pocos items / poca xp).
    //   MEDIO   -> objetivos intermedios, recompensa media.
    //   DIFICIL -> objetivos grandes, recompensa fuerte (diamantes, mucha xp).
    // Cada pool mezcla los tres TIPOS (MINE / KILL / DELIVER) para que el azar
    // pueda elegir cualquiera de ellos dentro del nivel.

    // ---- FACIL: minar 10 piedra, matar 5 zombis, entregar 8 trigo, ... ----
    public static final List<QuestDef> POOL_FACIL = List.of(
            new QuestDef("facil_minar_piedra", QuestType.MINE, "minecraft:stone", 10,
                    "minecraft:stone", "[Facil] Primeros golpes",
                    "Rompe 10 bloques de piedra",
                    List.of("give %player% minecraft:bread 4", "xp add %player% 20 points")),
            new QuestDef("facil_minar_carbon", QuestType.MINE, "minecraft:coal_ore", 8,
                    "minecraft:coal", "[Facil] Un poco de carbon",
                    "Rompe 8 minerales de carbon",
                    List.of("give %player% minecraft:torch 16", "xp add %player% 25 points")),
            new QuestDef("facil_matar_zombis", QuestType.KILL, "minecraft:zombie", 5,
                    "minecraft:rotten_flesh", "[Facil] Limpieza nocturna",
                    "Elimina 5 zombis",
                    List.of("give %player% minecraft:cooked_beef 6", "xp add %player% 25 points")),
            new QuestDef("facil_matar_aranas", QuestType.KILL, "minecraft:spider", 5,
                    "minecraft:string", "[Facil] Telaranas fuera",
                    "Elimina 5 aranas",
                    List.of("give %player% minecraft:emerald 1", "xp add %player% 20 points")),
            new QuestDef("facil_entregar_trigo", QuestType.DELIVER, "minecraft:wheat", 8,
                    "minecraft:wheat", "[Facil] Pequena cosecha",
                    "Entrega 8 de trigo",
                    List.of("give %player% minecraft:gold_nugget 8", "xp add %player% 20 points")),
            new QuestDef("facil_entregar_madera", QuestType.DELIVER, "minecraft:oak_log", 8,
                    "minecraft:oak_log", "[Facil] Un poco de lena",
                    "Entrega 8 troncos de roble",
                    List.of("give %player% minecraft:iron_ingot 2", "xp add %player% 20 points"))
    );

    // ---- MEDIO: minar 32 hierro, matar 20 esqueletos, entregar 32 cuero, ... ----
    public static final List<QuestDef> POOL_MEDIO = List.of(
            new QuestDef("medio_minar_hierro", QuestType.MINE, "minecraft:iron_ore", 32,
                    "minecraft:iron_ore", "[Medio] Vena de hierro",
                    "Rompe 32 minerales de hierro",
                    List.of("give %player% minecraft:iron_block 4", "xp add %player% 100 points")),
            new QuestDef("medio_minar_oro", QuestType.MINE, "minecraft:gold_ore", 24,
                    "minecraft:gold_ore", "[Medio] Fiebre del oro",
                    "Rompe 24 minerales de oro",
                    List.of("give %player% minecraft:gold_block 2", "xp add %player% 100 points")),
            new QuestDef("medio_matar_esqueletos", QuestType.KILL, "minecraft:skeleton", 20,
                    "minecraft:bone", "[Medio] Rompehuesos",
                    "Elimina 20 esqueletos",
                    List.of("give %player% minecraft:arrow 64", "give %player% minecraft:emerald 3")),
            new QuestDef("medio_matar_zombis", QuestType.KILL, "minecraft:zombie", 25,
                    "minecraft:rotten_flesh", "[Medio] Horda contenida",
                    "Elimina 25 zombis",
                    List.of("give %player% minecraft:emerald 4", "xp add %player% 120 points")),
            new QuestDef("medio_entregar_cuero", QuestType.DELIVER, "minecraft:leather", 32,
                    "minecraft:leather", "[Medio] Curtidor mayor",
                    "Entrega 32 de cuero",
                    List.of("give %player% minecraft:emerald 8", "xp add %player% 100 points")),
            new QuestDef("medio_entregar_hierro", QuestType.DELIVER, "minecraft:iron_ingot", 32,
                    "minecraft:iron_ingot", "[Medio] Fundidor",
                    "Entrega 32 lingotes de hierro",
                    List.of("give %player% minecraft:diamond 2", "xp add %player% 120 points"))
    );

    // ---- DIFICIL: minar 16 diamantes, matar 5 creepers, entregar 64 valioso ----
    public static final List<QuestDef> POOL_DIFICIL = List.of(
            new QuestDef("dificil_minar_diamante", QuestType.MINE, "minecraft:diamond_ore", 16,
                    "minecraft:diamond", "[Dificil] Veta legendaria",
                    "Rompe 16 minerales de diamante",
                    List.of("give %player% minecraft:diamond 8", "xp add %player% 400 points")),
            new QuestDef("dificil_minar_hierro", QuestType.MINE, "minecraft:iron_ore", 128,
                    "minecraft:iron_block", "[Dificil] Mina industrial",
                    "Rompe 128 minerales de hierro",
                    List.of("give %player% minecraft:diamond 6", "xp add %player% 350 points")),
            new QuestDef("dificil_matar_creepers", QuestType.KILL, "minecraft:creeper", 5,
                    "minecraft:gunpowder", "[Dificil] Cazador de creepers",
                    "Elimina 5 creepers",
                    List.of("give %player% minecraft:diamond 5", "give %player% minecraft:tnt 8",
                            "xp add %player% 400 points")),
            new QuestDef("dificil_matar_esqueletos", QuestType.KILL, "minecraft:skeleton", 40,
                    "minecraft:bone", "[Dificil] Masacre de huesos",
                    "Elimina 40 esqueletos",
                    List.of("give %player% minecraft:diamond 5", "xp add %player% 350 points")),
            new QuestDef("dificil_entregar_diamante", QuestType.DELIVER, "minecraft:diamond", 64,
                    "minecraft:diamond_block", "[Dificil] Tributo de diamantes",
                    "Entrega 64 diamantes",
                    List.of("give %player% minecraft:netherite_scrap 2", "xp add %player% 500 points")),
            new QuestDef("dificil_entregar_oro", QuestType.DELIVER, "minecraft:gold_ingot", 64,
                    "minecraft:gold_block", "[Dificil] Cofre del tesoro",
                    "Entrega 64 lingotes de oro",
                    List.of("give %player% minecraft:diamond 10", "xp add %player% 450 points"))
    );

    /** Pool de la ROTACION DIARIA: union de los tres niveles de dificultad. */
    public static final List<QuestDef> POOL = construirPoolDiario();

    private static List<QuestDef> construirPoolDiario() {
        List<QuestDef> todas = new ArrayList<>();
        todas.addAll(POOL_FACIL);
        todas.addAll(POOL_MEDIO);
        todas.addAll(POOL_DIFICIL);
        return List.copyOf(todas);
    }

    /** Devuelve el pool del nivel pedido. */
    public static List<QuestDef> poolDe(Nivel nivel) {
        switch (nivel) {
            case FACIL:
                return POOL_FACIL;
            case MEDIO:
                return POOL_MEDIO;
            case DIFICIL:
            default:
                return POOL_DIFICIL;
        }
    }

    /**
     * Elige AL AZAR una QuestDef del pool del nivel indicado. El tipo
     * (MINE/KILL/DELIVER) y el objetivo concreto quedan a suerte del azar.
     */
    public static QuestDef aleatoriaDe(Nivel nivel) {
        List<QuestDef> pool = poolDe(nivel);
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(RNG.nextInt(pool.size()));
    }

    /**
     * Devuelve la definicion de una mision por su id. Busca EXPLICITAMENTE en
     * los tres pools por dificultad (FACIL/MEDIO/DIFICIL), que es de donde
     * salen tanto las diarias como las personalizadas. No depende del orden de
     * inicializacion de {@link #POOL}: asi una mision personalizada de cualquier
     * nivel siempre resuelve su def y el cofre la puede pintar (ver BUG cofre
     * vacio). Devuelve null si el id no existe en ningun pool.
     */
    public static QuestDef porId(String id) {
        if (id == null) {
            return null;
        }
        // COMPATIBILIDAD: datos antiguos guardaron ids CORTOS (sin prefijo de
        // nivel), p.ej. "matar_creepers", "minar_diamante", "entregar_trigo".
        // Los pools actuales usan ids con prefijo ("dificil_matar_creepers",
        // etc.). Si no se traduce el id, porId() devuelve null y el cofre pinta
        // el icono generico (papel). Traducimos el id antiguo a su equivalente
        // actual ANTES de buscar para que el icono real vuelva a salir.
        id = ALIAS.getOrDefault(id, id);
        QuestDef q = buscarEn(POOL_FACIL, id);
        if (q != null) {
            return q;
        }
        q = buscarEn(POOL_MEDIO, id);
        if (q != null) {
            return q;
        }
        q = buscarEn(POOL_DIFICIL, id);
        if (q != null) {
            return q;
        }
        // Red de seguridad: por si POOL tuviera algo extra no cubierto arriba.
        return buscarEn(POOL, id);
    }

    private static QuestDef buscarEn(List<QuestDef> pool, String id) {
        if (pool == null) {
            return null;
        }
        for (QuestDef q : pool) {
            if (q.id().equals(id)) {
                return q;
            }
        }
        return null;
    }
}
