package com.serverpanas.servereco.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.serverpanas.servereco.ServerEcoMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CONFIG RECARGABLE EN CALIENTE de la valoracion economica.
 *
 * ===========================================================================
 *  QUE ES
 * ===========================================================================
 * TODOS los NUMEROS que usa {@link ItemValues} (anclas de materias primas,
 * multiplicadores por rareza, modids premium, pisos de loot, pesos de
 * encantamientos/afijos/sockets/atributos, factor de durabilidad on/off,
 * tabla de valores fijos por item, y el porcentaje de "vender al sistema")
 * viven en un archivo JSON EDITABLE A MANO:
 *
 *   config/servereco/valores.json
 *
 * El admin edita ese JSON (via panel/archivos de Exaroton) y ejecuta en el
 * juego el comando  /tienda reload  (o  /servereco reload ) con permiso de OP
 * (nivel 2+). El comando relee el JSON y aplica los nuevos valores EN VIVO, SIN
 * reiniciar el servidor.
 *
 * El unico calculo que sigue en CODIGO es la resolucion de recetas via
 * RecipeManager (coste por ingredientes); pero los NUMEROS base que alimentan
 * esa formula (anclas, multiplicadores, pisos, pesos) salen de este JSON y se
 * pueden tunear sin recompilar.
 *
 * ===========================================================================
 *  ARRANQUE
 * ===========================================================================
 *   - Si el archivo NO existe -> se genera con los valores por defecto (los
 *     actuales, ya corregidos).
 *   - Si existe -> se carga. Cualquier campo ausente conserva su valor por
 *     defecto (merge tolerante), y el archivo se reescribe completo para que el
 *     admin siempre vea todas las opciones disponibles.
 *
 * Esta clase NO toca el RecipeManager ni el servidor: solo lee/escribe el
 * archivo y guarda numeros. Es seguro invocarla en cualquier momento.
 */
public final class EcoConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Instancia viva. Se reemplaza en bloque al recargar (lectura sin locks). */
    private static volatile EcoConfig INSTANCIA = conPorDefecto();

    private EcoConfig() {
    }

    // =====================================================================
    //  CAMPOS CONFIGURABLES (se serializan tal cual al JSON)
    // =====================================================================

    /** On/off del factor de durabilidad sobre la BASE (material/equipo). */
    public boolean durabilidadActiva = true;

    /** Porcentaje que paga "VENDER AL SISTEMA" (0.90 = valor -10%). */
    public double porcentajeVentaSistema = 0.90;

    /** Profundidad maxima al resolver recetas (evita coste y ciclos). */
    public int maxProfundidadRecetas = 3;

    /** Techo de seguridad global del valor por unidad. */
    public int techoValor = 60000;

    /** Piso minimo: un item real nunca vale menos que esto. */
    public int pisoValorMinimo = 2;

    /**
     * MULTIPLICADOR PORCENTUAL por CATEGORIA (nombre de categoria -> porcentaje).
     *
     * 100 = 100% = sin cambio; 50 = mitad; 150 = +50%. Tras calcular el valor
     * base del item (receta/loot/durabilidad/extras/pisos) el motor determina la
     * CATEGORIA del item (ver {@link ItemValues#categoriaDe}) y multiplica el
     * resultado por (porcentaje_categoria / 100). Asi el admin sube o baja TODA
     * una categoria con un solo numero desde el panel. Por defecto todas en 100.
     *
     * Claves = nombres de {@link ItemValues.Categoria}. Se recarga en caliente
     * con /tienda reload igual que el resto de la config.
     */
    public Map<String, Integer> categorias = new LinkedHashMap<>();

    /**
     * OVERRIDES de VALOR PERSONALIZADO por item  (modid:item -> creditos).
     *
     * Es el "valor personalizado" que el admin fija a mano desde el panel para
     * un item concreto. Tiene la MAXIMA PRECEDENCIA sobre toda la valoracion:
     *
     *   overrides  >  valoresFijos  >  heuristica (receta/loot/durabilidad/extras)
     *
     * Cuando un item tiene override, ese numero es el VALOR FINAL fijo del item:
     * NO se le aplica el factor de durabilidad, ni los extras de equipo, ni el
     * piso por rareza, NI el MULTIPLICADOR PORCENTUAL de su CATEGORIA. El admin
     * ya decidio exactamente cuanto quiere que valga, asi que mandan sus
     * creditos tal cual (solo acotados por el techo de seguridad global).
     *
     * Por defecto esta VACIO: si el admin no define ningun override, la
     * valoracion se comporta igual que antes (nada cambia). Se recarga en
     * caliente con /tienda reload como el resto de la config.
     */
    public Map<String, Integer> overrides = new LinkedHashMap<>();

    /**
     * CATALOGO BASE DE COMPRA de la tienda  (modid:item -> precio de compra en
     * creditos). Es lo que un jugador puede COMPRAR desde /shop (COMPRAR) una
     * vez lo descubrio. Fusiona (y puede sobreescribir) el catalogo por defecto
     * del codigo ({@link com.serverpanas.servereco.shop.ShopCatalog}):
     *
     *   - item con precio > 0  -> EN VENTA a ese precio (anade o cambia).
     *   - item con precio <= 0 -> QUITADO del catalogo (aunque este por defecto).
     *
     * Por defecto esta VACIO: manda el catalogo del codigo. El admin lo edita
     * desde el panel (buscador de item) y se aplica en caliente con
     * /tienda reload, sin reiniciar. Separado de "overrides" (que fija el VALOR
     * de venta/analitico del item), esto controla QUE y a que PRECIO se compra.
     */
    public Map<String, Integer> catalogoCompra = new LinkedHashMap<>();

    /**
     * PODERES que se compran en /tienda -> PODERES (id -> precio en creditos).
     * 0 o menos = desactivado (no aparece). Los que falten se rellenan con el
     * precio por defecto. Se recarga en caliente con /tienda reload.
     */
    public Map<String, Integer> poderes = poderesPorDefecto();

    public static Map<String, Integer> poderesPorDefecto() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("tp_ir", 3000);
        m.put("tp_traer", 3000);
        m.put("tp_cama", 2000);
        m.put("curar", 1000);
        m.put("vision_nocturna", 800);
        m.put("prisa", 1500);
        m.put("velocidad", 1200);
        m.put("resistencia_fuego", 1500);
        m.put("respirar_agua", 800);
        return m;
    }

    /**
     * TABLA ANCLA de materias primas  (modid:item -> creditos). Fondo coherente
     * de la recursion de recetas y patron oro de la economia.
     */
    public Map<String, Integer> anclasMateriasPrimas = new LinkedHashMap<>();

    /**
     * VALORES FIJOS por item (modid:item -> creditos). Se aplican ANTES que
     * cualquier otra rama (receta/loot). Aqui viven los DROPS COMUNES DE MOB
     * baratos (carne podrida, hueso, cuerda, etc.) para que NUNCA caigan en la
     * rama de loot=alto por "no tener receta".
     */
    public Map<String, Integer> valoresFijos = new LinkedHashMap<>();

    /** Multiplicadores por rareza del item (COMMON/UNCOMMON/RARE/EPIC). */
    public Map<String, Double> multiplicadoresRareza = new LinkedHashMap<>();

    /** Modids PREMIUM (mods de aventura) -> multiplicador endgame. */
    public Map<String, Double> modidsPremium = new LinkedHashMap<>();

    /** Pisos de LOOT por rareza para items SIN receta de mod PREMIUM. */
    public Map<String, Integer> pisosLootPremium = new LinkedHashMap<>();

    /** Pisos de LOOT por rareza para items SIN receta VANILLA / no premium. */
    public Map<String, Integer> pisosLootVanilla = new LinkedHashMap<>();

    /** Pisos FINALES por rareza para items de mod PREMIUM. */
    public Map<String, Integer> pisosFinalPremium = new LinkedHashMap<>();

    /** Pisos FINALES por rareza para items VANILLA / no premium. */
    public Map<String, Integer> pisosFinalVanilla = new LinkedHashMap<>();

    // --- Pesos de EXTRAS (encantamientos, afijos, sockets, atributos) ---
    public int enchBase = 40;       // por encantamiento presente
    public int enchNivel = 35;      // por nivel del encantamiento
    public int afijoBase = 320;     // por afijo (no socket)
    public int afijoNivel = 120;    // extra segun potencia del roll (0..1)
    public int socketVacio = 300;   // por ranura vacia
    public int socketConGema = 600; // por ranura ocupada
    public int attrProxy = 220;     // por AttributeModifier extra (proxy)

    /** Factor de potencia por rareza del encantamiento (peso en el enchanting). */
    public Map<String, Double> factorRarezaEnchant = new LinkedHashMap<>();

    /** Multiplicador por rareza de loot de afijos de Apotheosis (sobre el extra). */
    public Map<String, Double> multRarezaLootAfijo = new LinkedHashMap<>();

    /** Piso de valor por rareza de loot de afijos de Apotheosis. */
    public Map<String, Integer> pisoRarezaLootAfijo = new LinkedHashMap<>();

    // =====================================================================
    //  API PUBLICA
    // =====================================================================

    /** Instancia viva de la config. Nunca null. */
    public static EcoConfig get() {
        return INSTANCIA;
    }

    /** Ruta del archivo de config: config/servereco/valores.json */
    public static Path ruta() {
        return FMLPaths.CONFIGDIR.get().resolve("servereco").resolve("valores.json");
    }

    /**
     * Carga el JSON desde disco. Si no existe, lo genera con los valores por
     * defecto. Rellena campos ausentes con defaults y reescribe el archivo.
     * Reemplaza la instancia viva en bloque. Devuelve true si se cargo/genero
     * bien; false si hubo un error irrecuperable (se conserva lo anterior).
     */
    public static synchronized boolean cargar() {
        Path ruta = ruta();
        try {
            Files.createDirectories(ruta.getParent());

            EcoConfig cfg;
            boolean generado = false;
            if (Files.exists(ruta)) {
                try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                    cfg = GSON.fromJson(r, EcoConfig.class);
                }
                if (cfg == null) {
                    cfg = conPorDefecto();
                    generado = true;
                }
            } else {
                cfg = conPorDefecto();
                generado = true;
            }

            cfg.rellenarAusentes();
            // Reescribe siempre: normaliza y muestra todas las opciones.
            guardar(cfg);

            INSTANCIA = cfg;
            if (generado) {
                ServerEcoMod.LOG.info("[servereco] config/servereco/valores.json generado con valores por defecto.");
            } else {
                ServerEcoMod.LOG.info("[servereco] config/servereco/valores.json cargado ({} valores fijos, {} anclas).",
                        cfg.valoresFijos.size(), cfg.anclasMateriasPrimas.size());
            }
            return true;
        } catch (Exception e) {
            ServerEcoMod.LOG.error("[servereco] Error al cargar config/servereco/valores.json; se conservan los valores previos.", e);
            return false;
        }
    }

    /** Escribe la config dada al archivo JSON (pretty printed, UTF-8). */
    private static void guardar(EcoConfig cfg) throws IOException {
        Path ruta = ruta();
        Files.createDirectories(ruta.getParent());
        try (Writer w = Files.newBufferedWriter(ruta, StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, w);
        }
    }

    /**
     * Rellena los mapas/valores que falten tras deserializar un JSON parcial,
     * tomando los defaults. Asi un JSON editado a medias nunca rompe el motor.
     */
    private void rellenarAusentes() {
        EcoConfig def = conPorDefecto();
        // overrides: VACIO es un estado valido (el admin aun no fijo ninguno).
        // Solo se garantiza que el mapa exista; NUNCA se rellena con defaults.
        if (overrides == null) overrides = new LinkedHashMap<>();
        // catalogoCompra: VACIO es valido (manda el catalogo del codigo). Solo
        // garantizamos que el mapa exista; nunca se rellena con defaults.
        if (catalogoCompra == null) catalogoCompra = new LinkedHashMap<>();
        if (anclasMateriasPrimas == null || anclasMateriasPrimas.isEmpty()) anclasMateriasPrimas = def.anclasMateriasPrimas;
        if (valoresFijos == null || valoresFijos.isEmpty()) valoresFijos = def.valoresFijos;
        if (multiplicadoresRareza == null || multiplicadoresRareza.isEmpty()) multiplicadoresRareza = def.multiplicadoresRareza;
        if (modidsPremium == null || modidsPremium.isEmpty()) modidsPremium = def.modidsPremium;
        if (pisosLootPremium == null || pisosLootPremium.isEmpty()) pisosLootPremium = def.pisosLootPremium;
        if (pisosLootVanilla == null || pisosLootVanilla.isEmpty()) pisosLootVanilla = def.pisosLootVanilla;
        if (pisosFinalPremium == null || pisosFinalPremium.isEmpty()) pisosFinalPremium = def.pisosFinalPremium;
        if (pisosFinalVanilla == null || pisosFinalVanilla.isEmpty()) pisosFinalVanilla = def.pisosFinalVanilla;
        if (factorRarezaEnchant == null || factorRarezaEnchant.isEmpty()) factorRarezaEnchant = def.factorRarezaEnchant;
        if (multRarezaLootAfijo == null || multRarezaLootAfijo.isEmpty()) multRarezaLootAfijo = def.multRarezaLootAfijo;
        if (pisoRarezaLootAfijo == null || pisoRarezaLootAfijo.isEmpty()) pisoRarezaLootAfijo = def.pisoRarezaLootAfijo;
        // Categorias: rellena las ausentes con 100% para no perder ninguna si el
        // admin edito el JSON a mano (merge por clave, no se descarta lo suyo).
        if (categorias == null) categorias = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : def.categorias.entrySet()) {
            categorias.putIfAbsent(e.getKey(), e.getValue());
        }
        if (poderes == null) poderes = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : poderesPorDefecto().entrySet()) {
            poderes.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    // =====================================================================
    //  DEFAULTS  (los valores "actuales ya corregidos")
    // =====================================================================

    private static EcoConfig conPorDefecto() {
        EcoConfig c = new EcoConfig();

        c.durabilidadActiva = true;
        c.porcentajeVentaSistema = 0.90;
        c.maxProfundidadRecetas = 3;
        c.techoValor = 60000;
        c.pisoValorMinimo = 2;

        // --- Anclas de materias primas (patron oro) ---
        Map<String, Integer> a = c.anclasMateriasPrimas;
        a.put("minecraft:coal", 8);
        a.put("minecraft:charcoal", 6);
        a.put("minecraft:raw_copper", 6);
        a.put("minecraft:copper_ingot", 8);
        a.put("minecraft:raw_iron", 20);
        a.put("minecraft:iron_ingot", 22);
        a.put("minecraft:iron_nugget", 2);
        a.put("minecraft:raw_gold", 10);
        a.put("minecraft:gold_ingot", 9);
        a.put("minecraft:gold_nugget", 1);
        a.put("minecraft:redstone", 10);
        a.put("minecraft:lapis_lazuli", 12);
        a.put("minecraft:quartz", 14);
        a.put("minecraft:amethyst_shard", 16);
        a.put("minecraft:emerald", 20);
        a.put("minecraft:diamond", 100);
        a.put("minecraft:ancient_debris", 400);
        a.put("minecraft:netherite_scrap", 350);
        a.put("minecraft:netherite_ingot", 1600);
        a.put("minecraft:leather", 8);
        a.put("minecraft:feather", 3);
        a.put("minecraft:bone", 5);
        a.put("minecraft:string", 5);
        a.put("minecraft:gunpowder", 12);
        a.put("minecraft:slime_ball", 6);
        a.put("minecraft:flint", 2);
        a.put("minecraft:clay_ball", 1);
        a.put("minecraft:stick", 1);
        a.put("minecraft:ender_pearl", 60);
        a.put("minecraft:blaze_rod", 40);
        a.put("minecraft:obsidian", 45);
        a.put("minecraft:ghast_tear", 80);
        a.put("minecraft:prismarine_shard", 8);
        a.put("minecraft:prismarine_crystals", 12);
        a.put("minecraft:nautilus_shell", 70);
        a.put("minecraft:shulker_shell", 120);
        a.put("minecraft:oak_log", 4);
        a.put("minecraft:spruce_log", 4);
        a.put("minecraft:birch_log", 4);
        a.put("minecraft:jungle_log", 4);
        a.put("minecraft:acacia_log", 4);
        a.put("minecraft:dark_oak_log", 4);
        a.put("minecraft:mangrove_log", 4);
        a.put("minecraft:cherry_log", 5);
        a.put("minecraft:crimson_stem", 5);
        a.put("minecraft:warped_stem", 5);
        a.put("minecraft:stone", 2);
        a.put("minecraft:cobblestone", 1);
        a.put("minecraft:deepslate", 2);
        a.put("minecraft:cobbled_deepslate", 2);
        a.put("minecraft:granite", 2);
        a.put("minecraft:diorite", 2);
        a.put("minecraft:andesite", 2);
        a.put("minecraft:dirt", 1);
        a.put("minecraft:gravel", 1);
        a.put("minecraft:sand", 2);
        a.put("minecraft:red_sand", 2);
        a.put("minecraft:netherrack", 1);
        a.put("minecraft:blackstone", 2);
        a.put("minecraft:basalt", 2);
        a.put("minecraft:end_stone", 4);
        a.put("minecraft:soul_sand", 4);
        a.put("minecraft:soul_soil", 4);
        a.put("minecraft:wheat", 3);
        a.put("minecraft:sugar_cane", 2);
        a.put("minecraft:bamboo", 2);
        a.put("minecraft:nether_wart", 6);

        // --- VALORES FIJOS: drops comunes de mob = BARATOS (basura comun) ---
        // Un item no-crafteable, COMMON y vanilla NO es valioso. Aqui se fijan
        // explicitamente los drops comunes para que valgan poco (unidades a
        // pocas decenas), NO cientos.
        Map<String, Integer> f = c.valoresFijos;
        f.put("minecraft:rotten_flesh", 3);   // carne podrida: basura de zombi
        f.put("minecraft:bone", 5);
        f.put("minecraft:string", 5);
        f.put("minecraft:spider_eye", 6);
        f.put("minecraft:gunpowder", 12);
        f.put("minecraft:slime_ball", 6);
        f.put("minecraft:feather", 3);
        f.put("minecraft:leather", 8);
        f.put("minecraft:ender_pearl", 60);   // algo mas (util y menos comun)
        f.put("minecraft:rabbit_hide", 3);
        f.put("minecraft:ink_sac", 4);
        f.put("minecraft:glow_ink_sac", 10);
        f.put("minecraft:phantom_membrane", 20);
        f.put("minecraft:magma_cream", 14);
        f.put("minecraft:blaze_powder", 20);
        // Carnes crudas (drops comunes de animales):
        f.put("minecraft:rotten_flesh", 3);
        f.put("minecraft:beef", 4);
        f.put("minecraft:porkchop", 4);
        f.put("minecraft:chicken", 4);
        f.put("minecraft:mutton", 4);
        f.put("minecraft:rabbit", 4);
        f.put("minecraft:cod", 4);
        f.put("minecraft:salmon", 4);
        f.put("minecraft:egg", 2);
        f.put("minecraft:wheat_seeds", 1);

        // --- Multiplicadores por rareza del item ---
        Map<String, Double> mr = c.multiplicadoresRareza;
        mr.put("COMMON", 1.0);
        mr.put("UNCOMMON", 1.4);
        mr.put("RARE", 1.8);
        mr.put("EPIC", 2.4);

        // --- Modids premium ---
        Map<String, Double> mp = c.modidsPremium;
        mp.put("artifacts", 6.0);
        mp.put("relics", 7.0);
        mp.put("reliquary", 5.0);
        mp.put("apotheosis", 6.0);
        mp.put("irons_spellbooks", 5.0);
        mp.put("cataclysm", 9.0);
        mp.put("l_ender", 9.0);
        mp.put("mowziesmobs", 7.0);
        mp.put("iceandfire", 7.0);
        mp.put("twilightforest", 6.0);

        // --- Pisos de LOOT por rareza (items sin receta) ---
        Map<String, Integer> plp = c.pisosLootPremium;
        plp.put("COMMON", 250);   // COMMON de mod premium (gema suelta, trofeo...)
        plp.put("UNCOMMON", 500);
        plp.put("RARE", 1200);
        plp.put("EPIC", 3000);

        Map<String, Integer> plv = c.pisosLootVanilla;
        plv.put("COMMON", 0);     // COMMON vanilla sin receta: manda la categoria/fijo (BARATO)
        plv.put("UNCOMMON", 60);
        plv.put("RARE", 250);
        plv.put("EPIC", 600);

        // --- Pisos FINALES por rareza ---
        Map<String, Integer> pfp = c.pisosFinalPremium;
        pfp.put("COMMON", 150);
        pfp.put("UNCOMMON", 300);
        pfp.put("RARE", 800);
        pfp.put("EPIC", 2000);

        Map<String, Integer> pfv = c.pisosFinalVanilla;
        pfv.put("COMMON", 0);
        pfv.put("UNCOMMON", 20);
        pfv.put("RARE", 120);
        pfv.put("EPIC", 300);

        // --- Pesos de EXTRAS ---
        c.enchBase = 40;
        c.enchNivel = 35;
        c.afijoBase = 320;
        c.afijoNivel = 120;
        c.socketVacio = 300;
        c.socketConGema = 600;
        c.attrProxy = 220;

        Map<String, Double> fre = c.factorRarezaEnchant;
        fre.put("COMMON", 1.0);
        fre.put("UNCOMMON", 1.4);
        fre.put("RARE", 1.8);
        fre.put("VERY_RARE", 2.4);

        Map<String, Double> mrla = c.multRarezaLootAfijo;
        mrla.put("uncommon", 1.12);
        mrla.put("rare", 1.25);
        mrla.put("epic", 1.5);
        mrla.put("mythic", 1.9);
        mrla.put("ancient", 2.2);

        Map<String, Integer> prla = c.pisoRarezaLootAfijo;
        prla.put("uncommon", 120);
        prla.put("rare", 250);
        prla.put("epic", 600);
        prla.put("mythic", 1100);
        prla.put("ancient", 1700);

        // --- Multiplicador PORCENTUAL por CATEGORIA (100 = sin cambio) ---
        // Una clave por cada ItemValues.Categoria. Por defecto todas en 100%,
        // asi el modulo no altera nada hasta que el admin mueva un porcentaje.
        Map<String, Integer> cat = c.categorias;
        for (ItemValues.Categoria categoria : ItemValues.Categoria.values()) {
            cat.put(categoria.name(), 100);
        }

        return c;
    }

    // =====================================================================
    //  Helpers de lectura (tolerantes a claves ausentes)
    // =====================================================================

    public int rarezaInt(Map<String, Integer> mapa, String clave, int porDefecto) {
        Integer v = mapa.get(clave);
        return v != null ? v : porDefecto;
    }

    public double rarezaDouble(Map<String, Double> mapa, String clave, double porDefecto) {
        Double v = mapa.get(clave);
        return v != null ? v : porDefecto;
    }
}
