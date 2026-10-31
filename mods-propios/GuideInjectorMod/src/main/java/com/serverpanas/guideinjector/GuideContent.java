package com.serverpanas.guideinjector;

import com.serverpanas.guideinjector.model.GuideNode;
import com.serverpanas.guideinjector.model.ModGuide;

import java.util.ArrayList;
import java.util.List;

/**
 * Definicion del contenido de las guias.
 *
 * Arquitectura reutilizable: para documentar un mod nuevo basta con crear otro
 * {@link ModGuide} en {@link #allGuides()}, rellenar su ficha de indice y, si se
 * quiere arbol detallado, anadir sus {@link GuideNode}. SOLO Twilight Forest
 * viene con el arbol completo; el resto son fichas de adorno en el indice.
 *
 * Ids del capitulo indice:
 *   - group:  "1000000000000001" (Cronicas de Server Panas)
 *   - chapter indice: "2A00000000000000"
 *   - portada quest:  "7E00000000000000" / task "8E00000000000000"
 * Las fichas de cada mod usan 7E..X / 8E..X (ver ModGuide).
 */
public final class GuideContent {

    public static final String GROUP_ID = "1000000000000001";
    public static final String INDEX_CHAPTER_ID = "2A00000000000000";
    public static final String INDEX_COVER_QUEST = "7E00000000000000";
    public static final String INDEX_COVER_TASK = "8E00000000000000";

    private GuideContent() {
    }

    /** Lista de todos los mods documentados (fichas del indice). */
    public static List<ModGuide> allGuides() {
        List<ModGuide> guides = new ArrayList<>();
        guides.add(twilightForest()); // demostracion: arbol completo
        return guides;
    }

    // ------------------------------------------------------------------
    // TWILIGHT FOREST - Prueba de fuego (arbol completo)
    //
    // Orden de progresion estricto verificado en la wiki oficial de FTB y la
    // wiki de Twilight Forest (1.20.1):
    //   Portal -> Naga -> Lich -> Minoshroom -> Hydra -> Ur-Ghast ->
    //   Alpha Yeti -> Snow Queen.
    // Cada jefe desbloquea la siguiente zona; entrar antes de tiempo castiga
    // con efectos negativos (Hambre en el Pantano, Ceguera en el Bosque
    // Oscuro, etc.).
    // Fuentes: ftb.fandom.com/wiki/Twilight_Forest_Progression ,
    //          wiki.9minecraft.net/the-twilight-forest-mod (progression order).
    //          Contenido reescrito por cumplimiento de licencias.
    // ------------------------------------------------------------------
    private static ModGuide twilightForest() {
        ModGuide g = new ModGuide(
                1,                                   // modIndex (slot de id = 1)
                "auto_twilightforest",               // filename del capitulo detallado
                "&5Twilight Forest - Guia completa", // titulo del capitulo
                "twilightforest:naga_trophy",        // icono del capitulo
                1,                                   // order_index
                "twilightforest",                    // indexKey
                "&5Twilight Forest &7[ver guia]",    // titulo de la ficha del indice
                "Progresion por jefes",              // subtitulo de la ficha
                "twilightforest:twilight_portal_miniature_structure" // icono ficha
        );

        // Ficha del indice: posicion y resumen breve + enlace (lo anade el writer).
        g.indexAt(2.0d, 0.0d)
                .indexAfter(INDEX_COVER_QUEST)
                .summary(
                        "&5Dimension magica con progresion &epor jefes&r:",
                        "cada jefe desbloquea la siguiente zona.",
                        "",
                        "&7Pulsa el enlace para abrir su arbol paso a paso.&r"
                );

        // --- Nodo 1: abrir el portal ---
        g.node(new GuideNode("portal",
                "&5&l1. Abrir el Portal",
                "La puerta al Crepusculo",
                "twilightforest:twilight_portal_miniature_structure",
                0.0d, 0.0d)
                .size(1.5d).shape("gear")
                .desc(
                        "&eQue hacer:&r construye el portal al Bosque",
                        "Crepuscular.",
                        "",
                        "&ePaso a paso:&r",
                        "1. Cava un hoyo de &e2x2&r (4 bloques) y llenalo",
                        "   de &bagua&r (fuente, no corriente).",
                        "2. Rodea el agua de &aflores, setas o plantas&r",
                        "   (hierba alta, helechos, hongos).",
                        "3. Tira un &bdiamante&r dentro del agua.",
                        "4. Caera un rayo y el agua se volvera el portal.",
                        "",
                        "&6Desbloquea:&r el acceso a la dimension. Salta",
                        "al portal para entrar."
                ));

        // --- Nodo 2: Naga ---
        g.node(new GuideNode("naga",
                "&a&l2. El Naga",
                "Primer jefe",
                "twilightforest:naga_trophy",
                2.0d, 0.0d)
                .after("portal")
                .desc(
                        "&eDonde:&r el &eNaga Courtyard&r (patio de piedra",
                        "con pilares de musgo).",
                        "",
                        "&eComo pelear:&r es una serpiente gigante que",
                        "carga y da vueltas. Esquiva su embestida, pega",
                        "cuando se detiene o choca con los pilares. Usa",
                        "pilares para romper su carga.",
                        "",
                        "&6Botin:&r &aEscama de Naga&r -> armadura y escudo",
                        "de escamas (tu primer equipo de la dimension).",
                        "",
                        "&6Desbloquea:&r el &eLaberinto&r (Labyrinth), donde",
                        "vive el Lich. Mata al Naga antes de entrar."
                ));

        // --- Nodo 3: Lich ---
        g.node(new GuideNode("lich",
                "&b&l3. El Lich Crepuscular",
                "La llave del bosque",
                "twilightforest:lich_trophy",
                4.0d, 0.0d)
                .after("naga")
                .desc(
                        "&eDonde:&r en lo alto del &eLaberinto&r (Lich",
                        "Tower), tras la puerta que abre matar al Naga.",
                        "",
                        "&eComo pelear:&r",
                        "- Fase 1: lanza orbes magicos. &cRefleja&r sus",
                        "  orbes con un escudo para herirlo.",
                        "- Fase 2: invoca esbirros; rompe la linea de",
                        "  vision y pega cuerpo a cuerpo.",
                        "- Fase 3: cuerpo a cuerpo directo.",
                        "",
                        "&6Botin:&r un &eCetro&r (Twilight, Life Draining,",
                        "Fortification o Zombie) y el Lich Trophy.",
                        "",
                        "&6Desbloquea:&r el &ePantano&r y el resto de zonas",
                        "intermedias. Es la llave del bosque."
                ));

        // --- Nodo 4: Minoshroom ---
        g.node(new GuideNode("minoshroom",
                "&6&l4. El Minoshroom",
                "El minotauro del laberinto",
                "twilightforest:minoshroom_trophy",
                6.0d, -2.0d)
                .after("lich")
                .desc(
                        "&eDonde:&r el &eLaberinto de Minotauros&r (Minotaur",
                        "Maze), bajo tierra. El Minoshroom esta en una",
                        "camara central con rejas.",
                        "",
                        "&eComo pelear:&r embiste y pega con su hacha en",
                        "area. Golpea y retrocede; usa la columna central",
                        "para cortarle la carga.",
                        "",
                        "&6Botin:&r el &eDiamante Minotauro&r (hacha) y",
                        "&eMeef crudo&r. Cocina y come un &eMeef Stroganoff&r.",
                        "",
                        "&6Desbloquea:&r comer el Meef Stroganoff abre el",
                        "&cPantano de Fuego&r (Fire Swamp)."
                ));

        // --- Nodo 5: Hydra ---
        g.node(new GuideNode("hydra",
                "&c&l5. La Hydra",
                "Tres cabezas de fuego",
                "twilightforest:hydra_trophy",
                8.0d, -2.0d)
                .after("minoshroom")
                .desc(
                        "&eDonde:&r el &cPantano de Fuego&r, en la guarida",
                        "de la Hydra (Hydra Lair).",
                        "",
                        "&eComo pelear:&r 3 cabezas que muerden, lanzan",
                        "fuego y bolas explosivas. Lleva &eResistencia al",
                        "Fuego&r. Pega a las cabezas; quedate a los lados",
                        "para evitar el aliento frontal. Es una pelea",
                        "larga: lleva comida y pociones.",
                        "",
                        "&6Botin:&r &eSangre de Hydra&r y el Hydra Trophy.",
                        "",
                        "&6Desbloquea:&r la &5Torre Oscura&r (Dark Tower)."
                ));

        // --- Nodo 6: Ur-Ghast ---
        g.node(new GuideNode("urghast",
                "&5&l6. El Ur-Ghast",
                "El senor de la Torre Oscura",
                "twilightforest:ur_ghast_trophy",
                10.0d, -2.0d)
                .after("hydra")
                .desc(
                        "&eDonde:&r la cima de la &5Torre Oscura&r (Dark",
                        "Tower), en el Bosque Oscuro.",
                        "",
                        "&eAntes:&r por el camino encontraras &eCaballeros",
                        "Fantasma&r y bloques/trampas de la torre.",
                        "",
                        "&eComo pelear:&r el Ur-Ghast es un ghast gigante",
                        "que llora lagrimas de fuego e invoca ghasts.",
                        "Cuando se posa en las &etorres de altar&r se hace",
                        "vulnerable: devuelvele sus bolas de fuego o pega",
                        "en ese momento.",
                        "",
                        "&6Botin:&r &eLagrima de Fuego&r (Fiery blood/tears)",
                        "y el Ur-Ghast Trophy.",
                        "",
                        "&6Desbloquea:&r el &bBosque Nevado&r (Snow Forest)."
                ));

        // --- Nodo 7: Alpha Yeti ---
        g.node(new GuideNode("yeti",
                "&b&l7. El Yeti Alfa",
                "La bestia de la cueva de hielo",
                "twilightforest:alpha_yeti_trophy",
                10.0d, 2.0d)
                .after("urghast")
                .desc(
                        "&eDonde:&r la &bCueva del Yeti&r (Yeti Cave), en el",
                        "Bosque Nevado.",
                        "",
                        "&eComo pelear:&r te agarra y te lanza, y provoca",
                        "derrumbes de hielo del techo. Pega entre sus",
                        "embestidas y esquiva las piedras que caen.",
                        "",
                        "&6Botin:&r &ePelaje de Yeti Alfa&r -> &eArctic Fur&r",
                        "y la armadura Artica (capucha, chaqueta, grebas,",
                        "botas). Tambien el Alpha Yeti Trophy.",
                        "",
                        "&6Desbloquea:&r el &fGlaciar&r y a la &bReina de",
                        "las Nieves&r."
                ));

        // --- Nodo 8: Snow Queen ---
        g.node(new GuideNode("snowqueen",
                "&f&l8. La Reina de las Nieves",
                "Jefe final",
                "twilightforest:snow_queen_trophy",
                12.0d, 2.0d)
                .size(1.25d).shape("gear")
                .after("yeti")
                .desc(
                        "&eDonde:&r el &fPalacio de Auroras&r (Aurora Palace),",
                        "en el Glaciar.",
                        "",
                        "&eComo pelear:&r vuela y lanza &ebloques de hielo&r",
                        "en tandas; luego baja a embestir. Esquiva la",
                        "lluvia de hielo refugiandote tras columnas y",
                        "golpeala cuando aterriza para su embestida.",
                        "Lleva armadura Artica y comida abundante.",
                        "",
                        "&6Botin:&r el Snow Queen Trophy y materiales de",
                        "hielo. &6Es el jefe final de la progresion.&r",
                        "",
                        "&a&lHas completado la Guia de Twilight Forest.&r"
                ));

        return g;
    }
}
