package com.serverpanas.guideinjector.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Un nodo del arbol de guia = una quest de FTB Quests.
 *
 * Los ids hex de 16 caracteres (quest y task) se asignan automaticamente por
 * {@link ModGuide} a partir de un prefijo, para no colisionar con los ids que
 * el usuario ya tiene a mano.
 */
public final class GuideNode {

    public final String key;             // clave logica para encadenar dependencias
    public final String title;           // con codigos de color &
    public final String subtitle;
    public final String icon;            // p.ej. "twilightforest:naga_trophy"
    public final List<String> description = new ArrayList<>();
    public final List<String> dependsOn = new ArrayList<>(); // claves de nodos previos
    public double x;
    public double y;
    public double size = 1.0d;
    public String shape = "";            // "" = por defecto; p.ej. "gear"

    // Ids asignados por ModDefinition al construir el capitulo.
    String questId;
    String taskId;

    public GuideNode(String key, String title, String subtitle, String icon, double x, double y) {
        this.key = key;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.x = x;
        this.y = y;
    }

    public GuideNode desc(String... lines) {
        for (String line : lines) {
            this.description.add(line);
        }
        return this;
    }

    public GuideNode after(String... nodeKeys) {
        for (String k : nodeKeys) {
            this.dependsOn.add(k);
        }
        return this;
    }

    public GuideNode size(double s) {
        this.size = s;
        return this;
    }

    public GuideNode shape(String s) {
        this.shape = s;
        return this;
    }

    public String questId() {
        return questId;
    }

    public String taskId() {
        return taskId;
    }
}
