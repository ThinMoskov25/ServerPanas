package com.serverpanas.serverguia.gui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenido de la guia: la lista de mods del pack y, por cada mod, su arbol de
 * pasos de progresion. Todo es texto estatico en espanol (codigos &, sin
 * tildes problematicas). No depende de que los mods esten instalados: se usan
 * iconos vanilla como representacion.
 */
public final class GuiaData {

    private GuiaData() {
    }

    /** Un paso dentro del arbol de un mod. */
    public static final class Paso {
        public final Item icono;
        public final String nombre;      // con codigos &
        public final List<String> lore;  // con codigos &

        public Paso(Item icono, String nombre, List<String> lore) {
            this.icono = icono;
            this.nombre = nombre;
            this.lore = lore;
        }
    }

    /** Un mod de la guia: icono para la lista + su arbol de pasos. */
    public static final class Mod {
        public final Item icono;
        public final String nombre;        // con codigos &
        public final String descripcion;   // 1 linea para la vista lista
        public final List<Paso> pasos;

        public Mod(Item icono, String nombre, String descripcion, List<Paso> pasos) {
            this.icono = icono;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.pasos = pasos;
        }
    }

    public static final List<Mod> MODS = new ArrayList<>();

    private static void mod(Item icono, String nombre, String descripcion, Paso... pasos) {
        MODS.add(new Mod(icono, nombre, descripcion, List.of(pasos)));
    }

    private static Paso paso(Item icono, String nombre, String... lore) {
        return new Paso(icono, nombre, List.of(lore));
    }

    static {
        // ------------------------------------------------------------------
        // 1. TWILIGHT FOREST  (arbol COMPLETO: 8 pasos, cada jefe abre la zona siguiente)
        // ------------------------------------------------------------------
        mod(Items.FERN, "&a&lTwilight Forest", "&7Dimension de jefes y mazmorras",
                paso(Items.WATER_BUCKET, "&b&lPaso 1: El Portal",
                        "&7Cava un hoyo de &f2x2&7 y llenalo de &bagua.",
                        "&7Rodea el agua con &dflores &7y setas.",
                        "&7Tira un &bdiamante &7al agua: cae un",
                        "&7rayo y se abre el portal al Bosque.",
                        "",
                        "&eEste es el primer paso obligatorio."),
                paso(Items.BONE, "&2&lPaso 2: La Naga",
                        "&7Busca el &aLaberinto de la Naga &7(muro",
                        "&7de piedra verde con cabeza de serpiente).",
                        "&7Derrotala corriendo en circulos y",
                        "&7golpeando cuando choca contra el muro.",
                        "",
                        "&6Suelta: Escamas de Naga (armadura).",
                        "&cDesbloquea: avanzar hacia el Lich."),
                paso(Items.WITHER_SKELETON_SKULL, "&5&lPaso 3: El Lich",
                        "&7Entra a la &5Torre del Lich&7.",
                        "&7Refleja sus bolas de energia con tu",
                        "&7espada hasta romper su escudo, luego",
                        "&7acaba con el. Mata sus esbirros.",
                        "",
                        "&6Suelta: Cetro del Lich.",
                        "&cDesbloquea: Laberinto y Minoshroom."),
                paso(Items.BEEF, "&6&lPaso 4: El Minoshroom",
                        "&7Explora el &6Laberinto de Minotauros&7.",
                        "&7En el nivel inferior esta el Minoshroom.",
                        "&7Usa el Mapa del Laberinto para no",
                        "&7perderte. Cuidado con sus embestidas.",
                        "",
                        "&6Suelta: Hacha del Minotauro.",
                        "&cDesbloquea: la Colmena de la Hidra."),
                paso(Items.FERMENTED_SPIDER_EYE, "&4&lPaso 5: La Hidra",
                        "&7Ve a la &4Colina de la Hidra&7.",
                        "&7Jefe de 3 cabezas: fuego, mordida y",
                        "&7mordida mortal. Ataca las cabezas una",
                        "&7a una y cubrete del aliento de fuego.",
                        "",
                        "&6Suelta: Chuleta de Hidra.",
                        "&cDesbloquea: la Torre Oscura (Ur-Ghast)."),
                paso(Items.GHAST_TEAR, "&8&lPaso 6: El Ur-Ghast",
                        "&7Sube la &8Torre Oscura&7 hasta la cima.",
                        "&7El Ur-Ghast llueve fuego y llama ghasts.",
                        "&7Reflejale sus bolas de fuego y destruye",
                        "&7los pilares que lo protegen.",
                        "",
                        "&6Suelta: Lagrima de Ur-Ghast.",
                        "&cDesbloquea: las Tierras Nevadas."),
                paso(Items.SNOWBALL, "&f&lPaso 7: El Yeti Alfa",
                        "&7En las montanas nevadas busca la",
                        "&fCueva del Yeti Alfa&7.",
                        "&7Te lanza contra las paredes; golpealo",
                        "&7cuando quede aturdido tras embestir.",
                        "",
                        "&6Suelta: Armadura de Yeti Alfa.",
                        "&cDesbloquea: el Glaciar de la Reina."),
                paso(Items.BLUE_ICE, "&b&lPaso 8: La Reina de las Nieves",
                        "&7Jefe FINAL. En el &bGlaciar&7, al final",
                        "&7del palacio de hielo.",
                        "&7Esquiva sus carambanos y la tormenta;",
                        "&7golpeala cuando aterriza a descansar.",
                        "",
                        "&6Suelta: Cetro de Hielo y Nieve.",
                        "&aFIN del arbol de Twilight Forest."));

        // ------------------------------------------------------------------
        // 2. TOMBSTONE
        // ------------------------------------------------------------------
        mod(Items.PLAYER_HEAD, "&7&lTombstone", "&7Recupera tus cosas al morir",
                paso(Items.OAK_SIGN, "&7Lapida automatica",
                        "&7Al morir aparece una &flapida &7en el",
                        "&7sitio con todo tu inventario guardado."),
                paso(Items.COMPASS, "&7Encuentra tu tumba",
                        "&7Recibes un &fpergamino de conocimiento",
                        "&7que marca donde quedo tu lapida."),
                paso(Items.BONE, "&7No pierdes XP",
                        "&7La lapida guarda tambien tu experiencia.",
                        "&7Rompela o haz clic para recuperar todo."),
                paso(Items.ENCHANTED_BOOK, "&7Hechizos de la tumba",
                        "&7Subiendo de nivel con el mod desbloqueas",
                        "&7magias: teletransporte, no caer items, etc."));

        // ------------------------------------------------------------------
        // 3. WAYSTONES
        // ------------------------------------------------------------------
        mod(Items.LODESTONE, "&d&lWaystones", "&7Viaje rapido entre puntos",
                paso(Items.STONE_BRICKS, "&dCraftea una Waystone",
                        "&7Necesitas piedra y una warpstone.",
                        "&7Colocala en el suelo para activarla."),
                paso(Items.NAME_TAG, "&dActiva y nombra",
                        "&7Haz clic derecho para activarla y",
                        "&7ponerle un nombre."),
                paso(Items.ENDER_PEARL, "&dViaja",
                        "&7Usa cualquier waystone activada para",
                        "&7viajar a otras ya descubiertas."),
                paso(Items.PAPER, "&dPergamino de regreso",
                        "&7El Warp Scroll te lleva a una waystone",
                        "&7sin limite de usos."));

        // ------------------------------------------------------------------
        // 4. TRAVELERS BACKPACK
        // ------------------------------------------------------------------
        mod(Items.LEATHER, "&6&lTravelers Backpack", "&7Mochila con tanques y mas",
                paso(Items.LEATHER, "&6Craftea la mochila",
                        "&7Usa cuero y lana del color que quieras."),
                paso(Items.CHEST, "&6Mucho almacenamiento",
                        "&7Gran inventario + 2 tanques de liquidos."),
                paso(Items.CRAFTING_TABLE, "&6Mesa de crafteo integrada",
                        "&7Craftea sin colocar nada en el suelo."),
                paso(Items.WATER_BUCKET, "&6Tanques y utilidades",
                        "&7Lleva agua para beber o rellenar, y",
                        "&7equipala en la espalda."));

        // ------------------------------------------------------------------
        // 5. TOOL BELT
        // ------------------------------------------------------------------
        mod(Items.LEATHER_HORSE_ARMOR, "&e&lTool Belt", "&7Cinturon de herramientas",
                paso(Items.LEATHER, "&eCraftea el cinturon",
                        "&7Con cuero; se mejora para mas ranuras."),
                paso(Items.IRON_PICKAXE, "&eGuarda herramientas",
                        "&7Mete picos, hachas y palas en el cinturon."),
                paso(Items.CLOCK, "&eCambio rapido",
                        "&7Rueda del raton (tecla configurable) para",
                        "&7sacar la herramienta al instante."),
                paso(Items.DIAMOND, "&eMejoras",
                        "&7Usa materiales mejores para subir el",
                        "&7numero de ranuras del cinturon."));

        // ------------------------------------------------------------------
        // 6. IRONS SPELLBOOKS
        // ------------------------------------------------------------------
        mod(Items.ENCHANTED_BOOK, "&5&lIrons Spellbooks", "&7Magia con hechizos y mana",
                paso(Items.BOOK, "&5Consigue un grimorio",
                        "&7Aparecen en cofres de mazmorras o",
                        "&7los compras a magos."),
                paso(Items.LAPIS_LAZULI, "&5Pergaminos de hechizo",
                        "&7Usa pergaminos para aprender hechizos",
                        "&7de fuego, hielo, rayo, sangre, etc."),
                paso(Items.POTION, "&5Mana",
                        "&7La armadura magica da mana y regen.",
                        "&7Lanzar hechizos consume mana."),
                paso(Items.BLAZE_ROD, "&5Sube de poder",
                        "&7Mejora el nivel de los hechizos y",
                        "&7combina escuelas para builds fuertes."));

        // ------------------------------------------------------------------
        // 7. ICE AND FIRE
        // ------------------------------------------------------------------
        mod(Items.DRAGON_HEAD, "&c&lIce and Fire", "&7Dragones de fuego, hielo y rayo",
                paso(Items.EGG, "&cHuevos de dragon",
                        "&7Mata dragones o saquea nidos para",
                        "&7conseguir huevos (fuego/hielo/rayo)."),
                paso(Items.CAMPFIRE, "&cIncuba y cria",
                        "&7Incuba el huevo (fuego junto a lava,",
                        "&7hielo en agua) y cria al dragon."),
                paso(Items.SADDLE, "&cDomestica y monta",
                        "&7Dale de comer carne; cuando crezca",
                        "&7ponle silla y vuela con el."),
                paso(Items.NETHERITE_SCRAP, "&cMateriales legendarios",
                        "&7Con escamas y huesos haces armaduras",
                        "&7y armas muy poderosas."),
                paso(Items.GHAST_TEAR, "&cOtras criaturas",
                        "&7Cuidado con cockatrices, hidras,",
                        "&7sirenas y gigantes del mod."));

        // ------------------------------------------------------------------
        // 8. DEEPER DARKER
        // ------------------------------------------------------------------
        mod(Items.ECHO_SHARD, "&3&lDeeper Darker", "&7La dimension Otherside",
                paso(Items.SCULK_CATALYST, "&3Prepara el portal",
                        "&7Construye el portal con bloques de",
                        "&7sculk y activalo con un fragmento de eco."),
                paso(Items.ECHO_SHARD, "&3Explora el Otherside",
                        "&7Dimension oscura llena de sculk,",
                        "&7estructuras antiguas y peligros."),
                paso(Items.SPYGLASS, "&3Nuevos recursos",
                        "&7Consigue materiales unicos y el catalejo",
                        "&7mejorado para explorar sin ruido."),
                paso(Items.WARDEN_SPAWN_EGG, "&3Cuidado con el Warden",
                        "&7El sculk reacciona al ruido. Muevete",
                        "&7agachado para no atraer al Warden."));

        // ------------------------------------------------------------------
        // 9. L_ENDERS CATACLYSM
        // ------------------------------------------------------------------
        mod(Items.NETHERITE_SWORD, "&4&lL_Enders Cataclysm", "&7Jefes y mazmorras epicas",
                paso(Items.MAP, "&4Encuentra las mazmorras",
                        "&7Explora para hallar estructuras como",
                        "&7la Prision Sepultada o el Santuario."),
                paso(Items.NETHERITE_SCRAP, "&4Jefes duros",
                        "&7Netherite Monstrosity, Ignis, Maledictus,",
                        "&7Ender Guardian... prepara buen equipo."),
                paso(Items.SHIELD, "&4Botin unico",
                        "&7Cada jefe suelta armas y armaduras",
                        "&7poderosas y materiales raros."),
                paso(Items.TOTEM_OF_UNDYING, "&4Progresion",
                        "&7Vence a los jefes menores antes de",
                        "&7ir por los finales. Lleva totems."));

        // ------------------------------------------------------------------
        // 10. MOWZIES MOBS
        // ------------------------------------------------------------------
        mod(Items.LEATHER_CHESTPLATE, "&2&lMowzies Mobs", "&7Jefes y mobs con animaciones",
                paso(Items.FROGSPAWN, "&2Foliaath",
                        "&7Planta carnivora en la jungla; cuidado",
                        "&7con las lianas que te atrapan."),
                paso(Items.BONE, "&2Ferrous Wroughtnaut",
                        "&7Caballero de hierro en salas de piedra.",
                        "&7Esquiva su hacha y golpea entre ataques."),
                paso(Items.TOTEM_OF_UNDYING, "&2Naga y Barako",
                        "&7Barako el jefe del sol; derrotalo para",
                        "&7obtener el Sol Blessing."),
                paso(Items.SUNFLOWER, "&2Recompensas",
                        "&7Capas, totems y poderes unicos por",
                        "&7cada jefe que venzas."));

        // ------------------------------------------------------------------
        // 11. APOTHEOSIS
        // ------------------------------------------------------------------
        mod(Items.ENCHANTING_TABLE, "&b&lApotheosis", "&7Encantamientos y gemas",
                paso(Items.BOOKSHELF, "&bMesa de encantar mejorada",
                        "&7Rodeala de estanterias y bloques",
                        "&7especiales para niveles mas altos."),
                paso(Items.EMERALD, "&bGemas y engarces",
                        "&7Pon gemas en equipo con ranuras para",
                        "&7bonus de dano, vida, velocidad, etc."),
                paso(Items.ENCHANTED_BOOK, "&bAfijos y rarezas",
                        "&7El botin trae items con afijos unicos;",
                        "&7mejoralos en el yunque y la rejilla."),
                paso(Items.NETHER_STAR, "&bJefes y spawners",
                        "&7Modifica spawners y enfrenta jefes",
                        "&7para botin de alto nivel."));

        // ------------------------------------------------------------------
        // 12. ARTIFACTS / RELICS
        // ------------------------------------------------------------------
        mod(Items.GOLDEN_BOOTS, "&6&lArtifacts / Relics", "&7Objetos pasivos equipables",
                paso(Items.CHEST_MINECART, "&6Cofres de campamento",
                        "&7Busca Campsite Chests enterrados para",
                        "&7encontrar artefactos."),
                paso(Items.FEATHER, "&6Equipa en ranuras Curios",
                        "&7Anillos, capas, botas y amuletos van",
                        "&7en ranuras especiales (tecla de curios)."),
                paso(Items.RABBIT_FOOT, "&6Efectos pasivos",
                        "&7Doble salto, inmunidad al fuego, mas",
                        "&7velocidad... segun el artefacto."),
                paso(Items.NETHER_STAR, "&6Combina artefactos",
                        "&7Lleva varios a la vez para builds",
                        "&7de movilidad o supervivencia."));

        // ------------------------------------------------------------------
        // 13. RELIQUARY
        // ------------------------------------------------------------------
        mod(Items.GLASS_BOTTLE, "&5&lReliquary", "&7Objetos magicos de utilidad",
                paso(Items.ROTTEN_FLESH, "&5Recolecta esencias",
                        "&7Mata mobs para llenar frascos con sus",
                        "&7esencias (mortero y maja)."),
                paso(Items.BLAZE_POWDER, "&5El Handgun",
                        "&7Craftea el arma de fuego y municion",
                        "&7magica de distintos efectos."),
                paso(Items.NETHER_STAR, "&5Pendiente del Angel",
                        "&7Te salva de morir una vez consumiendo",
                        "&7recursos; muy util para jefes."),
                paso(Items.ENDER_PEARL, "&5Utilidades varias",
                        "&7Linternas, pergaminos y amuletos con",
                        "&7efectos pasivos."));

        // ------------------------------------------------------------------
        // 14. ALEXS MOBS
        // ------------------------------------------------------------------
        mod(Items.COD, "&a&lAlexs Mobs", "&7Nuevos animales por bioma",
                paso(Items.GRASS_BLOCK, "&aExplora biomas",
                        "&7Cada bioma tiene criaturas nuevas:",
                        "&7elefantes, orcas, osos, etc."),
                paso(Items.WHEAT, "&aDomestica y cria",
                        "&7Muchos mobs son domesticables o",
                        "&7montables con el alimento correcto."),
                paso(Items.LEATHER, "&aMateriales nuevos",
                        "&7Colmillos, cuernos y pieles para",
                        "&7crafteos exclusivos del mod."),
                paso(Items.NAME_TAG, "&aMascotas utiles",
                        "&7Algunos ayudan en combate o mineria."));

        // ------------------------------------------------------------------
        // 15. NATURALIST
        // ------------------------------------------------------------------
        mod(Items.APPLE, "&2&lNaturalist", "&7Fauna y flora realista",
                paso(Items.OAK_LEAVES, "&2Nuevos animales",
                        "&7Serpientes, luciernagas, ciervos,",
                        "&7osos y mas por los bosques."),
                paso(Items.GLASS_BOTTLE, "&2Caza y recoleccion",
                        "&7Atrapa bichos en frascos y recoge",
                        "&7recursos nuevos de la naturaleza."),
                paso(Items.HONEY_BOTTLE, "&2Comidas nuevas",
                        "&7Recetas y alimentos a partir de la",
                        "&7fauna y flora del mod."),
                paso(Items.BONE, "&2Comportamientos",
                        "&7Los animales actuan de forma mas",
                        "&7natural segun el dia y el bioma."));

        // ------------------------------------------------------------------
        // 16. CREEPER OVERHAUL
        // ------------------------------------------------------------------
        mod(Items.CREEPER_HEAD, "&a&lCreeper Overhaul", "&7Creepers segun el bioma",
                paso(Items.GRASS_BLOCK, "&aVariantes por bioma",
                        "&7Cada bioma tiene su propio creeper con",
                        "&7aspecto y comportamiento unico."),
                paso(Items.GUNPOWDER, "&aMismo peligro",
                        "&7Siguen explotando: mantente alerta."),
                paso(Items.SHEARS, "&aDetalles visuales",
                        "&7Animaciones y texturas mejoradas para",
                        "&7cada tipo."));

        // ------------------------------------------------------------------
        // 17. DUNGEONS ARISE / YUNGS
        // ------------------------------------------------------------------
        mod(Items.CHEST, "&6&lDungeons Arise / YUNGs", "&7Mazmorras y estructuras nuevas",
                paso(Items.MAP, "&6Explora el mundo",
                        "&7Barcos, torres, ciudadelas y mas",
                        "&7estructuras generadas por el mapa."),
                paso(Items.IRON_SWORD, "&6Limpia la estructura",
                        "&7Estan llenas de enemigos y trampas."),
                paso(Items.GOLD_INGOT, "&6Botin exclusivo",
                        "&7Cofres con items raros y materiales."),
                paso(Items.SPYGLASS, "&6YUNGs mejora vanilla",
                        "&7Pozos, minas, fortalezas y mansiones",
                        "&7mas grandes y detalladas."));

        // ------------------------------------------------------------------
        // 18. TERRALITH
        // ------------------------------------------------------------------
        mod(Items.GRASS_BLOCK, "&2&lTerralith", "&7Mas de 100 biomas nuevos",
                paso(Items.SPYGLASS, "&2Mundo transformado",
                        "&7Montanas, desiertos de cristal, valles",
                        "&7y biomas imposibles en vanilla."),
                paso(Items.OAK_SAPLING, "&2Nuevos recursos",
                        "&7Nuevos arboles, plantas y vistas."),
                paso(Items.MAP, "&2Compatible con todo",
                        "&7Funciona junto a otros mods de mundo",
                        "&7sin romper la generacion."));

        // ------------------------------------------------------------------
        // 19. CARRY ON
        // ------------------------------------------------------------------
        mod(Items.BARREL, "&e&lCarry On", "&7Carga cofres y mobs llenos",
                paso(Items.CHEST, "&eLevanta bloques con cosas",
                        "&7Agachado + clic sobre un cofre o barril",
                        "&7para cargarlo con su contenido."),
                paso(Items.COW_SPAWN_EGG, "&eCarga mobs",
                        "&7Tambien puedes levantar animales y",
                        "&7moverlos a tu base."),
                paso(Items.HOPPER, "&eMueve maquinaria",
                        "&7Traslada tolvas y contenedores sin",
                        "&7vaciarlos antes."));

        // ------------------------------------------------------------------
        // 20. FARMERS DELIGHT / CROPTOPIA
        // ------------------------------------------------------------------
        mod(Items.COOKED_BEEF, "&6&lFarmers Delight / Croptopia", "&7Cocina y cultivos nuevos",
                paso(Items.WOODEN_HOE, "&6La sarten de cocina",
                        "&7Craftea la Cooking Pot y la tabla de",
                        "&7cortar para recetas nuevas."),
                paso(Items.WHEAT_SEEDS, "&6Muchos cultivos",
                        "&7Croptopia anade decenas de frutas,",
                        "&7verduras y especias."),
                paso(Items.BREAD, "&6Comidas potentes",
                        "&7Platos que dan mas saciedad y efectos",
                        "&7buenos al comerlos."),
                paso(Items.COMPOSTER, "&6Granja eficiente",
                        "&7Cuchillos, abono organico y cultivo",
                        "&7en 4 etapas para mejor cosecha."));

        // ------------------------------------------------------------------
        // 21. CHOCOCRAFT
        // ------------------------------------------------------------------
        mod(Items.FEATHER, "&e&lChocoCraft", "&7Chocobos montables",
                paso(Items.WHEAT_SEEDS, "&eGyshal Greens",
                        "&7Cultiva Gyshal Greens para atraer y",
                        "&7criar chocobos."),
                paso(Items.EGG, "&eCria chocobos",
                        "&7Alimenta y cruza para obtener colores",
                        "&7con habilidades distintas."),
                paso(Items.SADDLE, "&eMonta y vuela",
                        "&7Con silla puedes montarlos; algunos",
                        "&7planean o nadan."),
                paso(Items.DIAMOND, "&eChocobos raros",
                        "&7Colores especiales dan mas velocidad",
                        "&7y capacidades unicas."));

        // ------------------------------------------------------------------
        // 22. GOBLIN TRADERS
        // ------------------------------------------------------------------
        mod(Items.EMERALD, "&a&lGoblin Traders", "&7Comerciantes goblin",
                paso(Items.EMERALD, "&aEncuentra goblins",
                        "&7Aparecen bajo tierra (goblin) y en el",
                        "&7Nether (goblin veterano)."),
                paso(Items.GOLD_INGOT, "&aIntercambios utiles",
                        "&7Venden items raros a cambio de",
                        "&7esmeraldas y materiales."),
                paso(Items.ENDER_PEARL, "&aReputacion",
                        "&7Comercia mucho para desbloquear",
                        "&7mejores ofertas."));

        // ------------------------------------------------------------------
        // 23. MORE VILLAGERS / GUARD VILLAGERS
        // ------------------------------------------------------------------
        mod(Items.EMERALD, "&2&lMore Villagers / Guard Villagers", "&7Aldeanos nuevos y guardias",
                paso(Items.BELL, "&2Nuevas profesiones",
                        "&7Pescador, florista, hechicero y mas",
                        "&7aldeanos con comercios unicos."),
                paso(Items.IRON_SWORD, "&2Guardias",
                        "&7Contrata guardias para defender tu",
                        "&7aldea de monstruos y saqueos."),
                paso(Items.SHIELD, "&2Protege la aldea",
                        "&7Equipa a los guardias con armas y",
                        "&7armaduras para hacerlos mas fuertes."));

        // ------------------------------------------------------------------
        // 24. SERENE SEASONS
        // ------------------------------------------------------------------
        mod(Items.BIRCH_LEAVES, "&6&lSerene Seasons", "&7Estaciones del ano",
                paso(Items.WHEAT, "&6Cultiva por temporada",
                        "&7Cada cultivo crece mejor en su estacion;",
                        "&7revisa el calendario del mod."),
                paso(Items.SNOWBALL, "&6Clima cambiante",
                        "&7Invierno congela cultivos y cae nieve",
                        "&7en biomas que antes no tenian."),
                paso(Items.OAK_SAPLING, "&6Planifica",
                        "&7Usa invernaderos o estaciones para",
                        "&7cultivar fuera de temporada."));

        // ------------------------------------------------------------------
        // 25. SIMPLE SHOPS / TRADING POST
        // ------------------------------------------------------------------
        mod(Items.EMERALD_BLOCK, "&a&lSimple Shops / Trading Post", "&7Tiendas entre jugadores",
                paso(Items.OAK_SIGN, "&aCrea tu tienda",
                        "&7Coloca el bloque de tienda y define",
                        "&7que vendes y a que precio."),
                paso(Items.EMERALD, "&aComercia",
                        "&7Otros jugadores compran de tu tienda",
                        "&7aunque no estes conectado."),
                paso(Items.CHEST, "&aTrading Post",
                        "&7Centraliza varios comercios de aldeanos",
                        "&7en un solo punto comodo."));

        // ------------------------------------------------------------------
        // 26. COMFORTS
        // ------------------------------------------------------------------
        mod(Items.WHITE_BED, "&f&lComforts", "&7Sacos de dormir y hamacas",
                paso(Items.WHITE_WOOL, "&fSaco de dormir",
                        "&7Craftea un sleeping bag: pasa la noche",
                        "&7sin fijar tu punto de reaparicion."),
                paso(Items.STRING, "&fHamacas",
                        "&7Tambien hay hamacas para descansar",
                        "&7al aire libre."),
                paso(Items.CLOCK, "&fViajes largos",
                        "&7Ideal para expediciones: duermes y",
                        "&7sigues reapareciendo en tu base."));

        // ------------------------------------------------------------------
        // 27. JEI
        // ------------------------------------------------------------------
        mod(Items.BOOK, "&e&lJEI (Just Enough Items)", "&7Recetas y usos de todo",
                paso(Items.CRAFTING_TABLE, "&eVer recetas",
                        "&7Pasa el raton sobre un item y pulsa R",
                        "&7para ver como se craftea."),
                paso(Items.FURNACE, "&eVer usos",
                        "&7Pulsa U para ver en que se usa un item."),
                paso(Items.COMPASS, "&eBuscador",
                        "&7Usa la barra de busqueda para encontrar",
                        "&7cualquier item del pack."),
                paso(Items.BARRIER, "&eNota",
                        "&7JEI es del lado del cliente; esta guia",
                        "&7solo explica como se usa."));

        // ------------------------------------------------------------------
        // 28. VOICE CHAT
        // ------------------------------------------------------------------
        mod(Items.NOTE_BLOCK, "&b&lSimple Voice Chat", "&7Chat de voz por proximidad",
                paso(Items.NOTE_BLOCK, "&bHabla por voz",
                        "&7Oyes a quien tienes cerca; el volumen",
                        "&7baja con la distancia."),
                paso(Items.LEVER, "&bConfigura tu microfono",
                        "&7Ajusta tecla de hablar y dispositivo",
                        "&7en los ajustes del mod (cliente)."),
                paso(Items.BELL, "&bGrupos",
                        "&7Crea grupos para hablar con tus panas",
                        "&7a cualquier distancia."));

        // ------------------------------------------------------------------
        // 29. QUARK / SUPPLEMENTARIES
        // ------------------------------------------------------------------
        mod(Items.FLOWER_POT, "&d&lQuark / Supplementaries", "&7Decoracion y calidad de vida",
                paso(Items.CHEST, "&dMejoras de vanilla",
                        "&7Cofres mejorados, bloques nuevos y",
                        "&7atajos de inventario muy comodos."),
                paso(Items.ITEM_FRAME, "&dDecoracion",
                        "&7Faroles, letreros, velas, estanterias",
                        "&7y mucha decoracion util."),
                paso(Items.HOPPER, "&dUtilidades",
                        "&7Tuberias, candados, cuerdas y mecanismos",
                        "&7sencillos para automatizar."));

        // ------------------------------------------------------------------
        // 30. MORE DRAGON EGGS
        // ------------------------------------------------------------------
        mod(Items.DRAGON_EGG, "&5&lMore Dragon Eggs", "&7Mas huevos y recompensas de dragon",
                paso(Items.DRAGON_EGG, "&5Varios huevos",
                        "&7El Ender Dragon puede soltar mas de",
                        "&7un huevo al vencerlo repetidas veces."),
                paso(Items.END_CRYSTAL, "&5Reaparicion del dragon",
                        "&7Compatible con revivir al dragon para",
                        "&7farmear huevos y recompensas."),
                paso(Items.NETHER_STAR, "&5Usos de los huevos",
                        "&7Decora o usa los huevos en recetas",
                        "&7segun la configuracion del pack."));
    }

}
