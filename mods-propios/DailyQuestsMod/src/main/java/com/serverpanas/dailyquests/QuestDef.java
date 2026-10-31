package com.serverpanas.dailyquests;

import java.util.List;

/**
 * Definicion de una mision del pool (plantilla, no el progreso).
 *
 * @param id         identificador unico y estable (se guarda en los datos).
 * @param tipo       MINE / KILL / DELIVER.
 * @param objetivo   id del bloque, entidad o item objetivo (p.ej. minecraft:coal_ore).
 * @param cantidad   cuantas veces hay que cumplir el objetivo.
 * @param iconoItem  id del item vanilla que representa la mision en el cofre.
 * @param titulo     nombre que se muestra en el item.
 * @param descripcion linea de objetivo que va en el lore.
 * @param recompensas comandos de consola a ejecutar al completar (%player% = nombre).
 */
public record QuestDef(
        String id,
        QuestType tipo,
        String objetivo,
        int cantidad,
        String iconoItem,
        String titulo,
        String descripcion,
        List<String> recompensas
) {
}
