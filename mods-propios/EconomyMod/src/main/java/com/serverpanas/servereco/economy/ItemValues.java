package com.serverpanas.servereco.economy;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOTOR ANALITICO DE VALOR de items (creditos, valor de VENTA por unidad).
 *
 * ===========================================================================
 *  FILOSOFIA (peticion expresa del usuario)
 * ===========================================================================
 * NADA de precios "inventados" por el desarrollador. El precio de CUALQUIER
 * item sale de ANALIZAR sus propiedades REALES del juego. La unica tabla de
 * numeros fijos que queda es un ANCLA MINIMA de MATERIAS PRIMAS (materiales
 * que no tienen receta de crafteo y marcan el patron oro de la economia:
 * carbon, hierro, oro, diamante, esmeralda...). Todo lo demas se DERIVA.
 *
 * ===========================================================================
 *  NUMEROS = CONFIG RECARGABLE EN CALIENTE
 * ===========================================================================
 * TODOS los numeros base (anclas, valores fijos, multiplicadores por rareza,
 * modids premium, pisos de loot/finales, pesos de encantamientos/afijos/
 * sockets/atributos, factor de durabilidad on/off y el % de venta al sistema)
 * viven en config/servereco/valores.json ({@link EcoConfig}). El admin edita
 * ese JSON y ejecuta /tienda reload (o /servereco reload) para aplicarlo EN
 * VIVO. El unico calculo que permanece en codigo es la resolucion de recetas
 * via RecipeManager (coste por ingredientes), pero se alimenta de los numeros
 * del JSON. Recargar la config invalida la cache de BASE.
 *
 * ===========================================================================
 *  FORMULA
 * ===========================================================================
 *
 *   valor = round( BASE * FACTOR_DURABILIDAD )  +  EXTRAS
 *   valor = max( valor , PISO_RAREZA_MOD )
 *
 * PRECEDENCIA GLOBAL:  overrides  >  valoresFijos  >  heuristica.
 * Si el item tiene OVERRIDE (JSON "overrides": valor personalizado del admin),
 * ese numero es el VALOR FINAL fijo y se devuelve directamente, SIN durabilidad,
 * extras, piso por rareza NI multiplicador porcentual de categoria (solo el
 * techo global). En ausencia de override se aplica el resto de la formula.
 *
 * A) BASE  (dificultad de obtencion, medida OBJETIVAMENTE)
 *    - VALOR FIJO (JSON "valoresFijos"): si el item esta listado, ese es su
 *      valor base directo (p.ej. drops comunes de mob: carne podrida, hueso...).
 *    - ANCLA de materias primas (JSON "anclasMateriasPrimas"): fondo de la
 *      recursion de recetas.
 *    - SI TIENE RECETA: BASE = suma del valor de los INGREDIENTES / unidades.
 *    - SI NO TIENE RECETA (loot / drops): ver REGLA DE DROPS mas abajo.
 *
 * B) FACTOR_DURABILIDAD (JSON "durabilidadActiva")
 *    Si el item es damageable: factor = restante / maximo. Un item casi roto ->
 *    factor pequeno -> fraccion minima. Items sin durabilidad -> 1. Si se
 *    desactiva en el JSON, factor = 1 siempre.
 *
 * C) EXTRAS (JSON: pesos de encantamientos, afijos, sockets y atributos, mas
 *    factores de rareza): encantamientos, afijos y sockets de Apotheosis,
 *    atributos extra.
 *
 * D) PISO por RAREZA / MOD (JSON "pisosFinal*").
 *
 * ===========================================================================
 *  REGLA DE DROPS: COMUN vs RARO  (FIX pedido)
 * ===========================================================================
 * NO-crafteable NO implica valioso. El piso ALTO de "loot" SOLO aplica si el
 * item es de rareza > COMMON, o de un MODID PREMIUM. En concreto, para un item
 * SIN receta:
 *
 *   - rareza COMMON  +  mod VANILLA (minecraft:)  =>  BARATO. El piso de loot
 *     vanilla COMMON es 0 (JSON "pisosLootVanilla.COMMON"), asi que manda su
 *     VALOR FIJO (si esta listado como drop comun) o la heuristica de categoria.
 *     Los drops comunes de mob (rotten_flesh, bone, string, spider_eye,
 *     gunpowder, slime_ball, feather, leather, carnes crudas, ender_pearl un
 *     poco mas...) estan en "valoresFijos" con valores bajos (unidades a pocas
 *     decenas), de modo que NUNCA se valoran en cientos por "no tener receta".
 *
 *   - rareza UNCOMMON/RARE/EPIC  O  mod PREMIUM  =>  CARO. Se aplican los pisos
 *     altos de "pisosLootPremium"/"pisosLootVanilla" (gemas de Apotheosis,
 *     drops de jefe, materiales de afijo): siguen valiendo alto.
 *
 * ===========================================================================
 *  SEGURIDAD / ARRANQUE
 * ===========================================================================
 * El acceso al RecipeManager es PEREZOSO y solo con el servidor listo
 * (ServerLifecycleHooks.getCurrentServer() != null). Nunca durante el registro.
 *
 * Nota: estos valores son el PRECIO BASE. Al vender AL SISTEMA se paga el
 * porcentaje configurable (JSON "porcentajeVentaSistema", 0.90 por defecto).
 */
public final class ItemValues {

    private ItemValues() {
    }

    // =====================================================================
    //  CONTROL DE RECURSION / CACHE
    // =====================================================================

    /** Cache de BASE por Item (resolver recetas es costoso). */
    private static final Map<Item, Integer> CACHE_BASE = new ConcurrentHashMap<>();

    /**
     * RecipeManager con el que se lleno la cache. Si el servidor expone otro
     * (reload de datapacks o reinicio), se vacia la cache para recomputar.
     */
    private static volatile RecipeManager recipeManagerCache = null;

    // =====================================================================
    //  API PUBLICA (firmas usadas por los menus de /tienda: NO cambiar)
    // =====================================================================

    /** Valor en creditos de 1 unidad del item (stack por defecto, sin NBT). */
    public static int valorDe(Item item) {
        if (item == null || item == Items.AIR) {
            return 0;
        }
        return valorDe(new ItemStack(item));
    }

    /**
     * Valor de 1 unidad considerando el ItemStack concreto (durabilidad,
     * encantamientos, afijos, sockets). Entrada principal de los menus.
     */
    public static int valorDe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        EcoConfig cfg = EcoConfig.get();

        // --- 0) OVERRIDE de VALOR PERSONALIZADO (maxima precedencia) ---------
        // Si el admin fijo un valor para este item en la seccion "overrides"
        // del JSON, ese numero es el VALOR FINAL: no se aplica durabilidad, ni
        // extras, ni piso por rareza, NI el % de categoria. Solo el techo global.
        Integer override = overrideDe(stack.getItem());
        if (override != null) {
            int fijo = override;
            if (fijo <= 0) {
                fijo = cfg.pisoValorMinimo;
            }
            return Math.min(fijo, cfg.techoValor);
        }

        // --- A) BASE por dificultad de obtencion (receta o loot), cacheada ---
        int base = baseDeItem(stack.getItem());

        // --- B) FACTOR_DURABILIDAD sobre la BASE (material/equipo) ---
        double factor = factorDurabilidad(stack);
        int valor = (int) Math.round(base * factor);

        // --- C) EXTRAS (encantamientos + afijos/sockets/atributos) ---
        valor += valorExtraEquipo(stack);

        // --- D) PISO por rareza / modid premium ---
        valor = Math.max(valor, pisoFinal(stack));

        // --- E) MULTIPLICADOR PORCENTUAL por CATEGORIA (modulo admin) ---
        // Se aplica SOBRE el valor ya calculado (base+durabilidad+extras+piso).
        // 100 = sin cambio; el admin lo ajusta por categoria desde el panel.
        valor = (int) Math.round(valor * (porcentajeCategoria(stack) / 100.0));

        // Suelo: un item real nunca vale 0.
        if (valor <= 0) {
            valor = cfg.pisoValorMinimo;
        }
        // Techo de seguridad global.
        return Math.min(valor, cfg.techoValor);
    }

    /** Un item es vendible si su valor por unidad es > 0 (casi todo lo es). */
    public static boolean vendible(Item item) {
        return valorDe(item) > 0;
    }

    /**
     * Invalida la cache de BASE (p.ej. tras un reload de datapacks o tras
     * recargar la config). El motor tambien la invalida al detectar otro
     * RecipeManager.
     */
    public static void invalidarCache() {
        CACHE_BASE.clear();
        recipeManagerCache = null;
    }

    // =====================================================================
    //  A) BASE POR DIFICULTAD DE OBTENCION
    // =====================================================================

    /** Clave "modid:item" del item (null si no registrable). */
    private static String clave(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        return rl == null ? null : rl.toString();
    }

    /**
     * VALOR PERSONALIZADO fijado por el admin (seccion "overrides" del JSON), o
     * null si el item no tiene override. Es el valor FINAL del item (ver
     * {@link #valorDe(ItemStack)}): manda sobre valoresFijos, la heuristica y el
     * % de categoria. Se lee de la config viva, asi que /tienda reload lo
     * actualiza en caliente.
     */
    public static Integer overrideDe(Item item) {
        if (item == null || item == Items.AIR) {
            return null;
        }
        String id = clave(item);
        if (id == null) {
            return null;
        }
        return EcoConfig.get().overrides.get(id);
    }

    /** BASE del item (fijo / ancla / receta / loot), cacheada por Item. */
    private static int baseDeItem(Item item) {
        if (item == null || item == Items.AIR) {
            return 0;
        }
        EcoConfig cfg = EcoConfig.get();
        String id = clave(item);

        // Valor FIJO directo (p.ej. drops comunes de mob baratos).
        if (id != null) {
            Integer fijo = cfg.valoresFijos.get(id);
            if (fijo != null) {
                return fijo;
            }
            // Ancla de materias primas: fondo coherente, sin pasar por recetas.
            Integer ancla = cfg.anclasMateriasPrimas.get(id);
            if (ancla != null) {
                return ancla;
            }
        }

        RecipeManager rm = recipeManagerActual();
        // Invalida la cache si cambio el RecipeManager (reload/reinicio).
        if (rm != recipeManagerCache) {
            CACHE_BASE.clear();
            recipeManagerCache = rm;
        }

        Integer cached = CACHE_BASE.get(item);
        if (cached != null) {
            return cached;
        }

        int base = calcularBase(item, rm, 0, new HashSet<>());
        CACHE_BASE.put(item, base);
        return base;
    }

    /**
     * Calcula la BASE de un item:
     *   - valor fijo / ancla si aplica;
     *   - si tiene receta -> suma de ingredientes / unidades producidas;
     *   - si no -> valor de loot por rareza + modid (REGLA DE DROPS).
     * Protegido contra recursion con profundidad y set de visitados.
     */
    private static int calcularBase(Item item, RecipeManager rm, int profundidad, Set<Item> visitados) {
        if (item == null || item == Items.AIR) {
            return 0;
        }
        EcoConfig cfg = EcoConfig.get();
        String id = clave(item);
        if (id != null) {
            Integer fijo = cfg.valoresFijos.get(id);
            if (fijo != null) {
                return fijo;
            }
            Integer ancla = cfg.anclasMateriasPrimas.get(id);
            if (ancla != null) {
                return ancla;
            }
        }

        // Si no podemos (o no debemos) mirar recetas, caemos a heuristica.
        if (rm == null || profundidad >= cfg.maxProfundidadRecetas || visitados.contains(item)) {
            return baseLootOHeuristica(item);
        }

        visitados.add(item);
        try {
            int mejorReceta = Integer.MAX_VALUE; // la receta MAS BARATA de obtener
            boolean tieneReceta = false;

            RegistryAccess acceso = registryAccessActual();
            for (Recipe<?> receta : rm.getRecipes()) {
                ItemStack resultado;
                try {
                    resultado = receta.getResultItem(acceso);
                } catch (Throwable t) {
                    continue; // recetas de mods con resultado dinamico: se ignoran
                }
                if (resultado == null || resultado.isEmpty() || resultado.getItem() != item) {
                    continue;
                }

                NonNullList<Ingredient> ingredientes = receta.getIngredients();
                if (ingredientes == null || ingredientes.isEmpty()) {
                    continue;
                }

                int costo = 0;
                for (Ingredient ing : ingredientes) {
                    if (ing == null || ing.isEmpty()) {
                        continue;
                    }
                    costo += valorIngredienteMasBarato(ing, rm, profundidad + 1, visitados);
                }

                int unidades = Math.max(1, resultado.getCount());
                int porUnidad = Math.max(1, costo / unidades);
                tieneReceta = true;
                if (porUnidad < mejorReceta) {
                    mejorReceta = porUnidad;
                }
            }

            if (tieneReceta && mejorReceta != Integer.MAX_VALUE) {
                // Un item fabricable no deberia valer menos que el peor de los
                // fabricables triviales; piso suave de 2.
                return Math.max(2, mejorReceta);
            }

            // No tiene receta: es loot / drop. Valor por rareza y modid.
            return baseLootOHeuristica(item);
        } finally {
            visitados.remove(item);
        }
    }

    /**
     * Valor del ingrediente mas BARATO entre sus alternativas (los ingredientes
     * de crafteo aceptan varios items; un jugador usara el mas barato).
     */
    private static int valorIngredienteMasBarato(Ingredient ing, RecipeManager rm, int profundidad, Set<Item> visitados) {
        ItemStack[] opciones = ing.getItems();
        if (opciones == null || opciones.length == 0) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (ItemStack opcion : opciones) {
            if (opcion == null || opcion.isEmpty()) {
                continue;
            }
            int v = calcularBase(opcion.getItem(), rm, profundidad, visitados);
            if (v < min) {
                min = v;
            }
        }
        return (min == Integer.MAX_VALUE) ? 0 : min;
    }

    /**
     * BASE de un item SIN receta (loot/drop) o cuando no se pueden mirar recetas.
     *
     * REGLA DE DROPS (COMUN vs RARO): el piso ALTO de loot SOLO aplica si el
     * item es de rareza > COMMON o de modid PREMIUM. Un drop VANILLA COMMON
     * no-crafteable cae al valor BAJO (piso vanilla COMMON = 0 por defecto, con
     * lo que manda su valor fijo o la heuristica de categoria).
     */
    private static int baseLootOHeuristica(Item item) {
        EcoConfig cfg = EcoConfig.get();
        ItemStack ref = new ItemStack(item);
        Rarity rareza = item.getRarity(ref);
        boolean premium = multModidPremium(item) > 1.0;

        // 1) Valor por categoria (equipo de material sin receta accesible).
        int categoria = valorPorCategoria(item, ref);

        // 2) Valor de LOOT por rareza. Premium o rareza>COMMON -> pisos ALTOS.
        //    Vanilla COMMON -> piso 0 (BARATO: manda categoria/valor fijo).
        String rk = rareza.name(); // COMMON / UNCOMMON / RARE / EPIC
        int loot = premium
                ? cfg.rarezaInt(cfg.pisosLootPremium, rk, 0)
                : cfg.rarezaInt(cfg.pisosLootVanilla, rk, 0);

        return Math.max(categoria, loot);
    }

    /**
     * Valor aproximado por CATEGORIA del item a partir de propiedades reales:
     * armadura (defensa/tenacidad/durabilidad) y herramienta/arma (durabilidad).
     */
    private static int valorPorCategoria(Item item, ItemStack ref) {
        int categoria = 0;

        if (item instanceof ArmorItem armor) {
            int defensa = armor.getDefense();
            float tenacidad = armor.getToughness();
            int durab = armor.getMaxDamage(ref);
            categoria = Math.max(categoria, 40 + defensa * 35 + Math.round(tenacidad * 40f) + durab / 30);
        }

        if (item.canBeDepleted() && ref.getMaxDamage() > 0) {
            categoria = Math.max(categoria, 30 + ref.getMaxDamage() / 40);
        }

        return categoria;
    }

    // =====================================================================
    //  E) CATEGORIAS Y MULTIPLICADOR PORCENTUAL (MODULO ADMIN)
    // =====================================================================
    //
    // Toda la economia se agrupa en un conjunto CERRADO de categorias. El panel
    // muestra cada categoria con un porcentaje editable (100 = sin cambio) y el
    // motor multiplica el valor final del item por (porcentaje / 100). Asi el
    // admin sube o baja TODA una categoria con un solo numero, sin tocar item a
    // item. Los porcentajes viven en valores.json ("categorias") y se recargan
    // en caliente con /tienda reload.

    /**
     * CATEGORIAS para clasificar cualquier item. El orden de evaluacion en
     * {@link #categoriaDe(ItemStack)} define la PRECEDENCIA cuando un item
     * encaja en varias (p.ej. un item de mod premium que ademas es comida se
     * clasifica como ITEMS_MOD_PREMIUM, porque el modid manda).
     */
    public enum Categoria {
        /** Items de mods de aventura/endgame (modid premium). Maxima prioridad. */
        ITEMS_MOD_PREMIUM,
        /** Comida (isEdible). */
        COMIDA,
        /** Herramientas, armas y armaduras (TieredItem/ArmorItem/arcos/escudos...). */
        EQUIPO,
        /** Lingotes, gemas, minerales y materiales de refino. */
        MINERALES_GEMAS,
        /** Drops comunes de mob (carne podrida, hueso, cuerda, polvora...). */
        DROPS_MOB,
        /** Bloques de construccion solidos (piedra, maderas, tierra...). */
        BLOQUES_CONSTRUCCION,
        /** Bloques decorativos (cristal, alfombras, flores, cabezas...). */
        DECORACION,
        /** Resto: utilidades y miscelanea no clasificada arriba. */
        MISC
    }

    /**
     * Conjunto de modids "premium" conocidos para la clasificacion. Es un
     * fallback estatico; la fuente real de multiplicadores premium sigue siendo
     * EcoConfig.modidsPremium. Se usa aqui para la CATEGORIA (clasificacion),
     * no para el valor.
     */
    private static boolean esModidPremium(ResourceLocation rl) {
        if (rl == null) {
            return false;
        }
        // Si el modid tiene multiplicador premium configurado (>1), es premium.
        Double m = EcoConfig.get().modidsPremium.get(rl.getNamespace());
        return m != null && m > 1.0;
    }

    /**
     * Palabras clave en el id de item que delatan un MATERIAL (mineral/gema/
     * lingote). Se comprueban sobre el PATH del ResourceLocation.
     */
    private static boolean pareceMaterial(String path) {
        return path.endsWith("_ingot") || path.endsWith("_nugget")
                || path.startsWith("raw_")
                || path.endsWith("_ore") || path.contains("_ore_")
                || path.endsWith("_gem") || path.endsWith("_crystal") || path.endsWith("_crystals")
                || path.endsWith("_shard") || path.endsWith("_dust")
                || path.equals("diamond") || path.equals("emerald") || path.equals("coal")
                || path.equals("charcoal") || path.equals("redstone") || path.equals("quartz")
                || path.equals("lapis_lazuli") || path.equals("amethyst_shard")
                || path.equals("netherite_scrap") || path.equals("netherite_ingot")
                || path.equals("ancient_debris") || path.equals("flint");
    }

    /**
     * Drops COMUNES de mob (clasificacion). Son los mismos que la config fija
     * como baratos; aqui agrupamos por categoria para el multiplicador.
     */
    private static boolean pareceDropMob(String ns, String path) {
        if (!ns.equals("minecraft")) {
            return false;
        }
        switch (path) {
            case "rotten_flesh": case "bone": case "bone_meal": case "string":
            case "spider_eye": case "gunpowder": case "slime_ball": case "feather":
            case "leather": case "rabbit_hide": case "rabbit_foot": case "ink_sac":
            case "glow_ink_sac": case "phantom_membrane": case "magma_cream":
            case "blaze_powder": case "blaze_rod": case "ender_pearl": case "ghast_tear":
            case "shulker_shell": case "nautilus_shell": case "prismarine_shard":
            case "prismarine_crystals": case "scute": case "honeycomb":
                return true;
            default:
                return false;
        }
    }

    /**
     * Heuristica para separar un BlockItem entre BLOQUES_CONSTRUCCION y
     * DECORACION. Decoracion: bloques no solidos / translucidos (cristal,
     * flores, alfombras, antorchas, vallas decorativas, cabezas, velas...).
     * Construccion: el resto de bloques solidos.
     */
    private static boolean pareceDecoracion(BlockItem bi, String path) {
        // Pistas por nombre (robusto frente a mods).
        if (path.contains("glass") || path.contains("carpet") || path.contains("banner")
                || path.contains("candle") || path.contains("torch") || path.contains("lantern")
                || path.contains("flower") || path.contains("sapling") || path.contains("leaves")
                || path.contains("head") || path.contains("skull") || path.contains("painting")
                || path.contains("pane") || path.contains("rod") || path.contains("chain")
                || path.endsWith("_bed") || path.contains("sign") || path.contains("pot")
                || path.contains("fence") || path.contains("wall") || path.contains("bars")) {
            return true;
        }
        // Pista por propiedad del bloque: no bloquea la vision / no es opaco.
        try {
            Block block = bi.getBlock();
            BlockState st = block.defaultBlockState();
            // Bloques que no ocupan el cubo entero o no son opacos tienden a ser
            // decorativos. Se mira de forma defensiva (sin nivel real).
            if (!st.canOcclude()) {
                return true;
            }
        } catch (Throwable ignored) {
            // Mods con bloques raros: nos quedamos con la heuristica por nombre.
        }
        return false;
    }

    /**
     * Clasifica un item en una {@link Categoria}. Reglas, en orden de
     * precedencia:
     *   1. modid PREMIUM  -> ITEMS_MOD_PREMIUM (el modid manda sobre todo).
     *   2. isEdible       -> COMIDA.
     *   3. herramienta/arma/armadura (TieredItem, ArmorItem, arco, ballesta,
     *      tridente, escudo) -> EQUIPO.
     *   4. material (lingote/gema/mineral/polvo/fragmento, por nombre) -> MINERALES_GEMAS.
     *   5. drop comun de mob conocido -> DROPS_MOB.
     *   6. BlockItem -> DECORACION si parece decorativo, si no BLOQUES_CONSTRUCCION.
     *   7. resto -> MISC.
     */
    public static Categoria categoriaDe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Categoria.MISC;
        }
        Item item = stack.getItem();
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        String ns = rl == null ? "minecraft" : rl.getNamespace();
        String path = rl == null ? "" : rl.getPath();

        // 1) Mod premium: el modid manda.
        if (esModidPremium(rl)) {
            return Categoria.ITEMS_MOD_PREMIUM;
        }
        // 2) Comida.
        if (item.isEdible()) {
            return Categoria.COMIDA;
        }
        // 3) Equipo (herramientas, armas, armaduras).
        if (item instanceof TieredItem || item instanceof ArmorItem
                || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof ShieldItem) {
            return Categoria.EQUIPO;
        }
        // 4) Materiales / gemas / minerales (por nombre).
        if (pareceMaterial(path)) {
            return Categoria.MINERALES_GEMAS;
        }
        // 5) Drops comunes de mob.
        if (pareceDropMob(ns, path)) {
            return Categoria.DROPS_MOB;
        }
        // 6) Bloques: construccion vs decoracion.
        if (item instanceof BlockItem bi) {
            return pareceDecoracion(bi, path) ? Categoria.DECORACION : Categoria.BLOQUES_CONSTRUCCION;
        }
        // 7) Resto.
        return Categoria.MISC;
    }

    /**
     * Porcentaje configurado para la categoria del stack (100 si no esta en el
     * JSON). El motor multiplica el valor por (este valor / 100).
     */
    private static int porcentajeCategoria(ItemStack stack) {
        Categoria cat = categoriaDe(stack);
        Integer pct = EcoConfig.get().categorias.get(cat.name());
        return pct != null ? pct : 100;
    }

    // =====================================================================
    //  B) FACTOR DE DURABILIDAD
    // =====================================================================

    /**
     * factor = durabilidad restante / durabilidad maxima.
     * Items sin durabilidad -> 1. Un item casi roto -> factor pequeno. Si el
     * JSON desactiva la durabilidad, factor = 1 siempre.
     */
    private static double factorDurabilidad(ItemStack stack) {
        if (!EcoConfig.get().durabilidadActiva) {
            return 1.0;
        }
        int max = stack.getMaxDamage();
        if (max <= 0 || !stack.getItem().canBeDepleted()) {
            return 1.0;
        }
        int restante = max - stack.getDamageValue();
        if (restante < 0) {
            restante = 0;
        }
        double factor = (double) restante / (double) max;
        // Pequeno piso: un item roto pero con material conserva ~5% (chatarra).
        return Math.max(0.05, factor);
    }

    // =====================================================================
    //  D) PISO FINAL POR RAREZA / MODID
    // =====================================================================

    /**
     * Piso minimo de valor por rareza del item y modid premium. Es un PISO (no
     * se suma). Se escala por el factor de durabilidad (un item premium casi
     * roto tampoco conserva el piso completo).
     */
    private static int pisoFinal(ItemStack stack) {
        EcoConfig cfg = EcoConfig.get();
        Item item = stack.getItem();
        Rarity rareza = item.getRarity(stack);
        boolean premium = multModidPremium(item) > 1.0;
        String rk = rareza.name();

        int piso = premium
                ? cfg.rarezaInt(cfg.pisosFinalPremium, rk, 0)
                : cfg.rarezaInt(cfg.pisosFinalVanilla, rk, 0);

        return (int) Math.round(piso * factorDurabilidad(stack));
    }

    /** Multiplicador por MODID 'premium'. Si el item no es de esos mods, 1.0. */
    private static double multModidPremium(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl == null) {
            return 1.0;
        }
        return EcoConfig.get().modidsPremium.getOrDefault(rl.getNamespace(), 1.0);
    }

    // =====================================================================
    //  ACCESO SEGURO AL SERVIDOR (lazy; nunca durante el registro)
    // =====================================================================

    private static RecipeManager recipeManagerActual() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null; // servidor no listo: caeremos a heuristica
        }
        try {
            return server.getRecipeManager();
        } catch (Throwable t) {
            return null;
        }
    }

    private static RegistryAccess registryAccessActual() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return RegistryAccess.EMPTY;
        }
        try {
            return server.registryAccess();
        } catch (Throwable t) {
            return RegistryAccess.EMPTY;
        }
    }

    // =====================================================================
    //  C) EXTRA DE EQUIPO: ENCANTAMIENTOS + AFIJOS/SOCKETS + ATRIBUTOS
    // =====================================================================
    //
    // Lee DIRECTAMENTE el NBT de Apotheosis (sin dependencia de compilacion,
    // para mantener el patron server-only). Apotheosis 7.x (1.20.1) guarda:
    //
    //   tag.affix_data               -> compound raiz de afijos
    //   tag.affix_data.affixes       -> compound  { "<affix_id>": nivelFloat, ... }
    //   tag.affix_data.rarity        -> string    "apotheosis:rare" | "...:epic" | ...
    //   tag.affix_data.gems          -> lista de gemas colocadas
    //
    // El afijo SOCKET usa el id "apotheosis:socket" y su valor float = numero de
    // ranuras. PROXY de respaldo: AttributeModifiers EXTRA del stack cuando no
    // hay NBT de Apotheosis identificable.

    private static final String NBT_AFFIX_DATA = "affix_data";
    private static final String NBT_AFFIXES = "affixes";
    private static final String NBT_RARITY = "rarity";
    private static final String NBT_GEMS = "gems";
    private static final String AFFIX_SOCKET_SUFIJO = "socket";

    /**
     * Valor EXTRA del stack por su "poder": encantamientos, afijos de Apotheosis,
     * sockets, rareza de loot de afijos y (proxy) atributos extra. 0 para items
     * lisos. Todos los pesos vienen del JSON (EcoConfig).
     */
    private static int valorExtraEquipo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        EcoConfig cfg = EcoConfig.get();

        int extraEnch = valorEncantamientos(stack);

        int extraAfijos = 0;
        boolean teniaAfijosNbt = false;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_AFFIX_DATA)) {
            CompoundTag afxData = tag.getCompound(NBT_AFFIX_DATA);

            int sockets = 0;
            int gemas = contarGemas(afxData);

            if (afxData.contains(NBT_AFFIXES)) {
                CompoundTag affixes = afxData.getCompound(NBT_AFFIXES);
                for (String key : affixes.getAllKeys()) {
                    float nivel = affixes.getFloat(key);
                    if (esAfijoSocket(key)) {
                        sockets += Math.max(1, Math.round(nivel));
                    } else {
                        teniaAfijosNbt = true;
                        float clamp = Math.max(0f, Math.min(1f, nivel));
                        extraAfijos += cfg.afijoBase + Math.round(cfg.afijoNivel * clamp);
                    }
                }
            }

            int ocupados = Math.min(gemas, sockets);
            int vacios = Math.max(0, sockets - ocupados);
            if (sockets > 0) {
                teniaAfijosNbt = true;
                extraAfijos += vacios * cfg.socketVacio + ocupados * cfg.socketConGema;
            }

            if (teniaAfijosNbt && afxData.contains(NBT_RARITY)) {
                String rarity = afxData.getString(NBT_RARITY);
                extraAfijos += (int) Math.round(extraAfijos * (multRarezaLoot(rarity) - 1.0));
                extraAfijos += pisoRarezaLoot(rarity);
            }
        }

        int extraProxy = teniaAfijosNbt ? 0 : valorAtributosProxy(stack);

        return extraEnch + extraAfijos + extraProxy;
    }

    /** True si la clave de afijo corresponde a un socket de Apotheosis. */
    private static boolean esAfijoSocket(String affixId) {
        int sep = affixId.indexOf(':');
        String path = sep >= 0 ? affixId.substring(sep + 1) : affixId;
        return path.equals(AFFIX_SOCKET_SUFIJO) || path.endsWith("_" + AFFIX_SOCKET_SUFIJO);
    }

    /** Numero de gemas colocadas (lista "gems" dentro de affix_data). */
    private static int contarGemas(CompoundTag afxData) {
        if (!afxData.contains(NBT_GEMS)) {
            return 0;
        }
        int n = afxData.getList(NBT_GEMS, 10).size(); // 10 = CompoundTag
        if (n == 0) {
            n = afxData.getList(NBT_GEMS, 8).size();   // 8 = StringTag (ids)
        }
        return n;
    }

    /** Multiplicador por rareza de loot de Apotheosis (sobre el extra de afijos). */
    private static double multRarezaLoot(String rarityId) {
        String r = normalizarRareza(rarityId);
        return EcoConfig.get().rarezaDouble(EcoConfig.get().multRarezaLootAfijo, r, 1.0);
    }

    /** Piso de valor por rareza de loot de Apotheosis. */
    private static int pisoRarezaLoot(String rarityId) {
        String r = normalizarRareza(rarityId);
        return EcoConfig.get().rarezaInt(EcoConfig.get().pisoRarezaLootAfijo, r, 0);
    }

    /** Extrae el nombre de rareza ("apotheosis:rare" -> "rare"), en minuscula. */
    private static String normalizarRareza(String rarityId) {
        if (rarityId == null) {
            return "";
        }
        int sep = rarityId.indexOf(':');
        String r = sep >= 0 ? rarityId.substring(sep + 1) : rarityId;
        return r.toLowerCase();
    }

    /**
     * Valor PROXY por AttributeModifiers EXTRA del stack (los que anade el NBT
     * por encima de los del item base). Solo cuando no hay NBT de afijos.
     */
    private static int valorAtributosProxy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("AttributeModifiers")) {
            return 0;
        }
        int extraMods = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var base = stack.getItem().getDefaultAttributeModifiers(slot);
            var actuales = stack.getAttributeModifiers(slot);
            int diff = actuales.size() - base.size();
            if (diff > 0) {
                extraMods += diff;
            }
        }
        return extraMods * EcoConfig.get().attrProxy;
    }

    /**
     * Suma de valor por los encantamientos del stack, ponderando NIVEL y RAREZA
     * del encantamiento (un encantamiento fuerte vale cientos de creditos).
     */
    private static int valorEncantamientos(ItemStack stack) {
        EcoConfig cfg = EcoConfig.get();
        int total = 0;
        for (Map.Entry<Enchantment, Integer> en : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            Enchantment ench = en.getKey();
            int nivel = en.getValue();
            if (ench == null || nivel <= 0) {
                continue;
            }
            int base = cfg.enchBase + cfg.enchNivel * nivel;
            total += Math.round(base * factorRarezaEnchant(ench));
        }
        return total;
    }

    /** Factor de potencia segun la rareza del encantamiento (peso en el enchanting). */
    private static float factorRarezaEnchant(Enchantment ench) {
        String r = ench.getRarity().name(); // COMMON / UNCOMMON / RARE / VERY_RARE
        return (float) EcoConfig.get().rarezaDouble(EcoConfig.get().factorRarezaEnchant, r, 1.0);
    }
}
