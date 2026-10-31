package com.serverpanas.guideinjector;

import com.serverpanas.guideinjector.model.GuideNode;
import com.serverpanas.guideinjector.model.ModGuide;

import java.util.List;

/**
 * Convierte el modelo de guia en el texto SNBT exacto que entiende FTB Quests.
 *
 * El formato se replica a partir de un capitulo real del usuario
 * (config/ftbquests/quests/chapters/00_guia.snbt y 07_crepusculo.snbt):
 *   - Indentacion con TAB.
 *   - x/y/size llevan sufijo d (double).
 *   - Los ids son cadenas hex de 16 caracteres entre comillas.
 *   - description es una lista de strings (una linea por elemento).
 *   - tasks es una lista con {id, title, type:"checkmark"}.
 *
 * ENLACE INTERNO "Ver detalle" (Nivel 1 -> Nivel 2):
 *   FTB Quests reutiliza el clickEvent "change_page" de los libros vanilla para
 *   saltar a una quest por su id hex dentro del libro de quests. Esto lo
 *   confirma el equipo de FTB (desht, contribuidor) en el issue oficial
 *   FTBTeam/FTB-Mods-Issues #940: "change_page" normalmente solo lo usan los
 *   libros vanilla, pero FTB Quests lo reaprovecha para saltar a una quest
 *   dentro de la GUI del libro de quests, usando el id hex de la quest destino.
 *   Confirmado implementado y usable en 2001.4.18 (FTB Quests Forge, MC 1.20.1).
 *
 *   SINTAXIS EXACTA QUE FUNCIONA (y por que el enlace viejo NO abria nada):
 *   La linea de description debe ser JSON crudo que contenga UN SOLO elemento
 *   con SOLO los campos "text" y "clickEvent", nada mas:
 *
 *     [{"text":"Ver detalle","clickEvent":{"action":"change_page","value":"<QUEST_ID>"}}]
 *
 *   El bug anterior metia codigos de color "&6&l...&r" DENTRO del campo "text"
 *   del JSON. Esos "&" son el sistema de "substitutes" de FTB Quests; al
 *   mezclarlos dentro del JSON crudo FTB lanza "Invalid formatting! Can't nest
 *   multiple substitutes!" y la linea se queda como texto literal (se veia el
 *   JSON crudo con el id hex, de ahi "los numeros que quedaron mal") en vez de
 *   convertirse en un enlace clicable. Varios usuarios confirman en el mismo
 *   issue #940 que el enlace SOLO funciona si la linea es EXACTAMENTE
 *   [{"text":"...","clickEvent":{...}}] sin color ni formato extra y sola en su
 *   propia linea. Por eso aqui el "text" va en texto plano, sin codigos "&".
 *
 *   Fuente: https://github.com/FTBTeam/FTB-Mods-Issues/issues/940
 *   (contenido reescrito por cumplimiento de licencias).
 *
 *   NAVEGACION: change_page enfoca/abre la quest destino por su id hex aunque
 *   su capitulo sea always_invisible (se navega por id, no por visibilidad del
 *   capitulo), asi que el arbol de Twilight se mantiene invisible y el enlace
 *   igual salta a el.
 */
public final class SnbtWriter {

    private SnbtWriter() {
    }

    /**
     * Linea de description con enlace interno a una quest por su id hex.
     *
     * IMPORTANTE: el campo "text" va en TEXTO PLANO, sin codigos de color "&".
     * Meter "&" dentro del JSON crudo rompe el parser de FTB Quests
     * ("Can't nest multiple substitutes!") y el enlace deja de ser clicable.
     */
    public static String detailLink(String targetQuestId) {
        return "[{\"text\":\">> Ver detalle de la guia <<\",\"clickEvent\":"
                + "{\"action\":\"change_page\",\"value\":\"" + targetQuestId + "\"}}]";
    }

    // ------------------------------------------------------------------
    // Capitulo INDICE (Nivel 1)
    // ------------------------------------------------------------------

    /**
     * Genera el capitulo indice "00_guia_auto": una portada y una ficha por mod.
     * Cada ficha de mod lleva en su description el enlace "Ver detalle" al primer
     * nodo de su capitulo detallado.
     *
     * @param group      id del chapter group ("Cronicas de Server Panas")
     * @param chapterId  id hex del capitulo indice
     * @param coverQuest id hex de la quest portada
     * @param coverTask  id hex de la task de la portada
     * @param guides     mods documentados
     */
    public static String indexChapter(String group,
                                      String chapterId,
                                      String coverQuest,
                                      String coverTask,
                                      List<ModGuide> guides) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("\tdefault_hide_dependency_lines: false\n");
        sb.append("\tdefault_quest_shape: \"\"\n");
        sb.append("\tfilename: \"00_guia_auto\"\n");
        sb.append("\tgroup: \"").append(group).append("\"\n");
        sb.append("\ticon: \"minecraft:writable_book\"\n");
        sb.append("\tid: \"").append(chapterId).append("\"\n");
        sb.append("\torder_index: 0\n");
        sb.append("\tquests: [\n");

        // Portada.
        sb.append("\t\t{\n");
        sb.append("\t\t\tdescription: [\n");
        appendDescLine(sb, "&6Guia automatica de Server Panas&r.");
        appendDescLine(sb, "");
        appendDescLine(sb, "Cada ficha es un &emod&r del pack. Pulsa el enlace");
        appendDescLine(sb, "&eVer detalle de la guia&r dentro de una ficha para");
        appendDescLine(sb, "saltar a su &earbol de pasos&r completo.");
        appendDescLine(sb, "");
        appendDescLine(sb, "&7Marca cada ficha cuando la hayas leido. No dan");
        appendDescLine(sb, "premio: son solo para aprender.&r");
        sb.append("\t\t\t]\n");
        sb.append("\t\t\ticon: \"minecraft:writable_book\"\n");
        sb.append("\t\t\tid: \"").append(coverQuest).append("\"\n");
        sb.append("\t\t\tsize: 1.5d\n");
        sb.append("\t\t\tsubtitle: \"Indice de guias\"\n");
        appendCheckmarkTask(sb, coverTask, "Leer la guia");
        sb.append("\t\t\ttitle: \"&6&lGUIA AUTOMATICA\"\n");
        sb.append("\t\t\tx: 0.0d\n");
        sb.append("\t\t\ty: 0.0d\n");
        sb.append("\t\t}\n");

        // Una ficha por mod.
        for (ModGuide g : guides) {
            sb.append("\t\t{\n");
            // dependencias (de la portada u otras fichas).
            if (!g.indexDependsOn.isEmpty()) {
                sb.append("\t\t\tdependencies: [").append(hexList(g.indexDependsOn)).append("]\n");
            }
            sb.append("\t\t\tdescription: [\n");
            for (String line : g.indexSummary) {
                appendDescLine(sb, line);
            }
            // Enlace interno solo si el mod tiene arbol detallado.
            if (g.hasTree()) {
                appendDescLine(sb, "");
                appendDescLineRaw(sb, detailLink(g.entryQuestId()));
            }
            sb.append("\t\t\t]\n");
            sb.append("\t\t\ticon: \"").append(g.indexIcon).append("\"\n");
            sb.append("\t\t\tid: \"").append(g.indexQuestId()).append("\"\n");
            sb.append("\t\t\tsubtitle: \"").append(escape(g.indexSubtitle)).append("\"\n");
            appendCheckmarkTask(sb, g.indexTaskId(), "Entendido");
            sb.append("\t\t\ttitle: \"").append(escape(g.indexTitle)).append("\"\n");
            sb.append("\t\t\tx: ").append(g.indexX).append("d\n");
            sb.append("\t\t\ty: ").append(g.indexY).append("d\n");
            sb.append("\t\t}\n");
        }

        sb.append("\t]\n");
        sb.append("\ttitle: \"&6Guia Automatica\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Capitulo DETALLADO (Nivel 2) con always_invisible
    // ------------------------------------------------------------------

    public static String detailChapter(String group, ModGuide g) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("\talways_invisible: true\n");
        sb.append("\tdefault_hide_dependency_lines: false\n");
        sb.append("\tdefault_quest_shape: \"\"\n");
        sb.append("\tfilename: \"").append(g.chapterFilename).append("\"\n");
        sb.append("\tgroup: \"").append(group).append("\"\n");
        sb.append("\ticon: \"").append(g.chapterIcon).append("\"\n");
        sb.append("\tid: \"").append(g.chapterId).append("\"\n");
        sb.append("\torder_index: ").append(g.orderIndex).append("\n");
        sb.append("\tquests: [\n");

        for (GuideNode n : g.nodes()) {
            sb.append("\t\t{\n");
            if (!n.dependsOn.isEmpty()) {
                sb.append("\t\t\tdependencies: [")
                        .append(hexListKeys(g, n.dependsOn)).append("]\n");
            }
            sb.append("\t\t\tdescription: [\n");
            for (String line : n.description) {
                appendDescLine(sb, line);
            }
            sb.append("\t\t\t]\n");
            sb.append("\t\t\ticon: \"").append(n.icon).append("\"\n");
            sb.append("\t\t\tid: \"").append(n.questId()).append("\"\n");
            if (!n.shape.isEmpty()) {
                sb.append("\t\t\tshape: \"").append(n.shape).append("\"\n");
            }
            if (n.size != 1.0d) {
                sb.append("\t\t\tsize: ").append(n.size).append("d\n");
            }
            sb.append("\t\t\tsubtitle: \"").append(escape(n.subtitle)).append("\"\n");
            appendCheckmarkTask(sb, n.taskId(), "Entendido");
            sb.append("\t\t\ttitle: \"").append(escape(n.title)).append("\"\n");
            sb.append("\t\t\tx: ").append(n.x).append("d\n");
            sb.append("\t\t\ty: ").append(n.y).append("d\n");
            sb.append("\t\t}\n");
        }

        sb.append("\t]\n");
        sb.append("\ttitle: \"").append(escape(g.chapterTitle)).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void appendDescLine(StringBuilder sb, String line) {
        sb.append("\t\t\t\t\"").append(escape(line)).append("\"\n");
    }

    /** Linea de description sin escapar las comillas (para el JSON del enlace). */
    private static void appendDescLineRaw(StringBuilder sb, String rawJson) {
        // El JSON del enlace lleva comillas dobles internas; en SNBT se escapan
        // con backslash para que quede como una sola cadena.
        sb.append("\t\t\t\t\"").append(rawJson.replace("\"", "\\\"")).append("\"\n");
    }

    private static void appendCheckmarkTask(StringBuilder sb, String taskId, String title) {
        sb.append("\t\t\ttasks: [{ id: \"").append(taskId)
                .append("\", title: \"").append(escape(title))
                .append("\", type: \"checkmark\" }]\n");
    }

    private static String hexList(List<String> ids) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append("\"").append(ids.get(i)).append("\"");
        }
        return b.toString();
    }

    /** Resuelve claves logicas de nodo a sus quest ids dentro del capitulo. */
    private static String hexListKeys(ModGuide g, List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            GuideNode dep = g.byKey(keys.get(i));
            b.append("\"").append(dep.questId()).append("\"");
        }
        return b.toString();
    }

    /** Escapa comillas dobles y backslash para cadenas SNBT normales. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
