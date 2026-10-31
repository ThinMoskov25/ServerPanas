package com.serverpanas.guideinjector.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa un mod documentado: su ficha de resumen en el indice (Nivel 1) y
 * su capitulo detallado invisible (Nivel 2) con el arbol de pasos.
 *
 * Esta es la pieza reutilizable: para documentar otro mod basta con crear otro
 * ModGuide, rellenar la ficha de indice y anadir sus GuideNode. Solo Twilight
 * Forest viene con el arbol completo; los demas pueden quedar como adorno.
 *
 * Asignacion de ids (hex de 16 chars) para no colisionar con los existentes
 * del usuario (2000.../3000.../4000...):
 *   - chapterId:  prefijo 2A.. + indice del mod   (p.ej. 2A00000000000001)
 *   - quest ids:  prefijo 7.. dentro del capitulo
 *   - task ids:   prefijo 8.. dentro del capitulo
 *   - quest id de la ficha del indice: prefijo 7E.. (dentro del capitulo indice)
 */
public final class ModGuide {

    // --- Ficha del indice (Nivel 1) ---
    public final String indexKey;          // clave de la ficha en el capitulo indice
    public final String indexTitle;        // titulo de la ficha (con &-codigos)
    public final String indexSubtitle;
    public final String indexIcon;
    public final List<String> indexSummary = new ArrayList<>(); // resumen breve
    public double indexX;
    public double indexY;
    public final List<String> indexDependsOn = new ArrayList<>();

    // --- Capitulo detallado (Nivel 2) ---
    public final String chapterFilename;   // p.ej. "auto_twilightforest"
    public final String chapterId;         // hex 16 chars unico
    public final String chapterTitle;      // titulo del capitulo detallado
    public final String chapterIcon;
    public final int orderIndex;
    private final Map<String, GuideNode> nodes = new LinkedHashMap<>();

    // Prefijos de id para este capitulo (se derivan del indice del mod).
    private final String questPrefix; // 15 chars, falta 1 para el contador
    private final String taskPrefix;

    // Ids de la ficha de este mod DENTRO del capitulo indice (Nivel 1).
    // Prefijo 7E.. para quests de indice y 8E.. para sus tasks, un char por mod.
    private final String indexQuestId;
    private final String indexTaskId;

    public ModGuide(int modIndex,
                    String chapterFilename,
                    String chapterTitle,
                    String chapterIcon,
                    int orderIndex,
                    String indexKey,
                    String indexTitle,
                    String indexSubtitle,
                    String indexIcon) {
        this.chapterFilename = chapterFilename;
        this.chapterTitle = chapterTitle;
        this.chapterIcon = chapterIcon;
        this.orderIndex = orderIndex;
        this.indexKey = indexKey;
        this.indexTitle = indexTitle;
        this.indexSubtitle = indexSubtitle;
        this.indexIcon = indexIcon;

        // Un digito hex por mod (1..F) para separar los espacios de id.
        String slot = Integer.toHexString(modIndex & 0xF).toUpperCase();
        // chapterId: 2A0000000000000X
        this.chapterId = "2A0000000000000" + slot;
        // quest ids: 7X000000000000N  (16 chars: 7 + slot + 13 ceros + contador)
        this.questPrefix = "7" + slot + "0000000000000";
        // task ids:  8X000000000000N
        this.taskPrefix = "8" + slot + "0000000000000";
        // Ficha en el indice: 7E000000000000X / 8E000000000000X
        this.indexQuestId = "7E000000000000" + slot + "0";
        this.indexTaskId = "8E000000000000" + slot + "0";
    }

    public String indexQuestId() {
        return indexQuestId;
    }

    public String indexTaskId() {
        return indexTaskId;
    }

    public ModGuide indexAt(double x, double y) {
        this.indexX = x;
        this.indexY = y;
        return this;
    }

    public ModGuide indexAfter(String... keys) {
        for (String k : keys) {
            this.indexDependsOn.add(k);
        }
        return this;
    }

    public ModGuide summary(String... lines) {
        for (String line : lines) {
            this.indexSummary.add(line);
        }
        return this;
    }

    /** Anade un nodo al arbol y le asigna ids hex unicos dentro del capitulo. */
    public GuideNode node(GuideNode node) {
        int n = nodes.size() + 1; // 1..15 por capitulo (suficiente para un arbol)
        String h = Integer.toHexString(n & 0xF).toUpperCase();
        node.questId = questPrefix + h;
        node.taskId = taskPrefix + h;
        nodes.put(node.key, node);
        return node;
    }

    public List<GuideNode> nodes() {
        return new ArrayList<>(nodes.values());
    }

    public GuideNode byKey(String key) {
        return nodes.get(key);
    }

    /** El quest id del primer nodo: destino del enlace "Ver detalle" del indice. */
    public String entryQuestId() {
        for (GuideNode n : nodes.values()) {
            return n.questId;
        }
        return null;
    }

    public boolean hasTree() {
        return !nodes.isEmpty();
    }
}
