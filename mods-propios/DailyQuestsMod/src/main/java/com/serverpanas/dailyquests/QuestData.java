package com.serverpanas.dailyquests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Almacen persistente de misiones por jugador, adjunto al ServerLevel
 * (overworld) como SavedData. Minecraft lo guarda en:
 *   world/data/dailyquests.dat
 *
 * No usa librerias ni bases de datos externas: todo es NBT nativo.
 *
 * Gestiona la ROTACION diaria: cada registro de jugador guarda el "dia"
 * (epoch en dias, UTC) con el que se generaron sus misiones. Si al consultar
 * el dia actual es distinto, se re-generan 3 misiones al azar del pool.
 */
public class QuestData extends SavedData {

    public static final String NOMBRE = DailyQuestsMod.MOD_ID;
    /** Cuantas misiones diarias se asignan a cada jugador. */
    public static final int MISIONES_POR_DIA = 3;

    /** Progreso de una mision concreta de un jugador. */
    public static class Entrada {
        public String questId;
        public int progreso;
        public boolean reclamada;
        /**
         * Origen de la mision: si es true, la asigno un admin con
         * "/misiones dar" (mision PERSONALIZADA). Las personalizadas NO se
         * borran en la rotacion diaria mientras no se reclamen. Si es false es
         * una mision de la rotacion diaria normal.
         */
        public boolean personalizada;

        public Entrada(String questId, int progreso, boolean reclamada) {
            this(questId, progreso, reclamada, false);
        }

        public Entrada(String questId, int progreso, boolean reclamada, boolean personalizada) {
            this.questId = questId;
            this.progreso = progreso;
            this.reclamada = reclamada;
            this.personalizada = personalizada;
        }
    }

    /** Registro diario de un jugador: el dia y sus misiones. */
    public static class Registro {
        public long dia;                 // epochDay con el que se generaron
        public List<Entrada> misiones = new ArrayList<>();
    }

    private final Map<UUID, Registro> porJugador = new HashMap<>();

    // ---------------- Acceso / creacion ----------------

    public static QuestData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        // Firma de 1.20.1 (official mappings): computeIfAbsent(loader, creator, name).
        return overworld.getDataStorage().computeIfAbsent(
                QuestData::cargar, QuestData::new, NOMBRE);
    }

    /** Dia actual (epochDay UTC). Cambia a medianoche UTC -> rotacion cada 24 h. */
    public static long diaActual() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /**
     * Devuelve las misiones del jugador para HOY, generandolas si es un dia
     * nuevo (o si nunca tuvo). Marca los datos como "dirty" si cambia algo.
     */
    public List<Entrada> misionesDeHoy(UUID jugador) {
        long hoy = diaActual();
        Registro reg = porJugador.get(jugador);
        if (reg == null || reg.dia != hoy) {
            // Guardamos las personalizadas aun NO reclamadas: no caducan con la
            // rotacion diaria, siguen activas hasta que el jugador las reclame.
            List<Entrada> conservar = new ArrayList<>();
            if (reg != null) {
                for (Entrada e : reg.misiones) {
                    if (e.personalizada && !e.reclamada) {
                        conservar.add(e);
                    }
                }
            }
            reg = generar(hoy);
            reg.misiones.addAll(conservar);
            porJugador.put(jugador, reg);
            setDirty();
        }
        return reg.misiones;
    }

    /** Genera un registro nuevo con MISIONES_POR_DIA misiones al azar del pool. */
    private Registro generar(long dia) {
        Registro reg = new Registro();
        reg.dia = dia;
        List<QuestDef> copia = new ArrayList<>(QuestRegistry.POOL);
        Collections.shuffle(copia);
        int n = Math.min(MISIONES_POR_DIA, copia.size());
        for (int i = 0; i < n; i++) {
            reg.misiones.add(new Entrada(copia.get(i).id(), 0, false));
        }
        return reg;
    }

    /**
     * Asigna a un jugador una mision PERSONALIZADA (origen admin) ADEMAS de sus
     * misiones diarias. Garantiza primero que el jugador tenga su registro de
     * hoy generado. Si ya tiene esa misma mision personalizada sin reclamar,
     * reinicia su progreso para que vuelva a estar activa.
     */
    public void agregarPersonalizada(UUID jugador, String questId) {
        List<Entrada> hoy = misionesDeHoy(jugador);
        for (Entrada e : hoy) {
            if (e.personalizada && e.questId.equals(questId) && !e.reclamada) {
                e.progreso = 0;
                setDirty();
                return;
            }
        }
        hoy.add(new Entrada(questId, 0, false, true));
        setDirty();
    }

    /**
     * Suma progreso a todas las misiones del jugador cuyo tipo y objetivo
     * coincidan. Devuelve true si alguna avanzo (para marcar dirty y avisar).
     */
    public boolean avanzar(UUID jugador, QuestType tipo, String objetivo, int cantidad) {
        boolean cambio = false;
        for (Entrada e : misionesDeHoy(jugador)) {
            QuestDef def = QuestRegistry.porId(e.questId);
            if (def == null || e.reclamada) {
                continue;
            }
            if (def.tipo() == tipo && def.objetivo().equals(objetivo) && e.progreso < def.cantidad()) {
                e.progreso = Math.min(def.cantidad(), e.progreso + cantidad);
                cambio = true;
            }
        }
        if (cambio) {
            setDirty();
        }
        return cambio;
    }

    public Entrada entrada(UUID jugador, String questId) {
        for (Entrada e : misionesDeHoy(jugador)) {
            if (e.questId.equals(questId)) {
                return e;
            }
        }
        return null;
    }

    public void marcarReclamada(UUID jugador, String questId) {
        Entrada e = entrada(jugador, questId);
        if (e != null) {
            e.reclamada = true;
            setDirty();
        }
    }

    // ---------------- Persistencia NBT ----------------

    public static QuestData cargar(CompoundTag tag) {
        QuestData data = new QuestData();
        ListTag jugadores = tag.getList("jugadores", Tag.TAG_COMPOUND);
        for (int i = 0; i < jugadores.size(); i++) {
            CompoundTag jt = jugadores.getCompound(i);
            UUID uuid = jt.getUUID("uuid");
            Registro reg = new Registro();
            reg.dia = jt.getLong("dia");
            ListTag ms = jt.getList("misiones", Tag.TAG_COMPOUND);
            for (int j = 0; j < ms.size(); j++) {
                CompoundTag mt = ms.getCompound(j);
                reg.misiones.add(new Entrada(
                        mt.getString("id"),
                        mt.getInt("progreso"),
                        mt.getBoolean("reclamada"),
                        mt.getBoolean("personalizada")));
            }
            data.porJugador.put(uuid, reg);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag jugadores = new ListTag();
        for (Map.Entry<UUID, Registro> en : porJugador.entrySet()) {
            CompoundTag jt = new CompoundTag();
            jt.putUUID("uuid", en.getKey());
            jt.putLong("dia", en.getValue().dia);
            ListTag ms = new ListTag();
            for (Entrada e : en.getValue().misiones) {
                CompoundTag mt = new CompoundTag();
                mt.putString("id", e.questId);
                mt.putInt("progreso", e.progreso);
                mt.putBoolean("reclamada", e.reclamada);
                mt.putBoolean("personalizada", e.personalizada);
                ms.add(mt);
            }
            jt.put("misiones", ms);
            jugadores.add(jt);
        }
        tag.put("jugadores", jugadores);
        return tag;
    }
}
