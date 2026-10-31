package com.serverpanas.dailyquests;

/**
 * Tipos de mision y como se rastrean:
 *  - MINE    -> BlockEvent.BreakEvent (objetivo = id del bloque roto).
 *  - KILL    -> LivingDeathEvent      (objetivo = id de la entidad muerta).
 *  - DELIVER -> boton en el menu virtual / recogida (objetivo = id del item).
 */
public enum QuestType {
    MINE,
    KILL,
    DELIVER
}
