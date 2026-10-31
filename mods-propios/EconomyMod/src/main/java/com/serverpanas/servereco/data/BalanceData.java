package com.serverpanas.servereco.data;

import com.serverpanas.servereco.ServerEcoMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SALDO de creditos persistente por jugador (world/data/servereco_balance.dat).
 *
 * El "dinero" del servidor es este SALDO virtual, NO las monedas fisicas del
 * inventario. El inventario del jugador (oro, diamantes, etc.) son items
 * normales que YA NO se consumen ni se asumen como saldo.
 *
 * El saldo empieza en 0 por jugador y nunca puede quedar negativo. Solo NBT
 * nativo; cero dependencias externas.
 */
public class BalanceData extends SavedData {

    public static final String NOMBRE = ServerEcoMod.MOD_ID + "_balance";

    private final Map<UUID, Long> saldos = new HashMap<>();

    public static BalanceData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                BalanceData::cargar, BalanceData::new, NOMBRE);
    }

    // ---------------- Consulta ----------------

    /** Saldo actual del jugador (0 si nunca ha tenido creditos). */
    public long saldo(UUID jugador) {
        return saldos.getOrDefault(jugador, 0L);
    }

    public long saldo(ServerPlayer jugador) {
        return saldo(jugador.getUUID());
    }

    /** true si el jugador tiene al menos "monto" creditos. */
    public boolean tiene(UUID jugador, long monto) {
        return saldo(jugador) >= monto;
    }

    // ---------------- Operaciones ----------------

    /** Suma "monto" al saldo (ignora montos <= 0). */
    public void sumar(UUID jugador, long monto) {
        if (monto <= 0) {
            return;
        }
        saldos.merge(jugador, monto, Long::sum);
        setDirty();
    }

    public void sumar(ServerPlayer jugador, long monto) {
        sumar(jugador.getUUID(), monto);
    }

    /**
     * Resta "monto" del saldo de forma ATOMICA: solo descuenta si alcanza.
     * Devuelve true si se pudo descontar; nunca deja el saldo negativo.
     */
    public boolean restar(UUID jugador, long monto) {
        if (monto <= 0) {
            return true;
        }
        long actual = saldo(jugador);
        if (actual < monto) {
            return false;
        }
        saldos.put(jugador, actual - monto);
        setDirty();
        return true;
    }

    public boolean restar(ServerPlayer jugador, long monto) {
        return restar(jugador.getUUID(), monto);
    }

    // ---------------- Persistencia ----------------

    public static BalanceData cargar(CompoundTag tag) {
        BalanceData data = new BalanceData();
        ListTag arr = tag.getList("saldos", Tag.TAG_COMPOUND);
        for (int i = 0; i < arr.size(); i++) {
            CompoundTag jt = arr.getCompound(i);
            UUID uuid = jt.getUUID("uuid");
            long saldo = jt.getLong("saldo");
            if (saldo > 0) {
                data.saldos.put(uuid, saldo);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag arr = new ListTag();
        for (Map.Entry<UUID, Long> en : saldos.entrySet()) {
            if (en.getValue() == null || en.getValue() <= 0) {
                continue;
            }
            CompoundTag jt = new CompoundTag();
            jt.putUUID("uuid", en.getKey());
            jt.putLong("saldo", en.getValue());
            arr.add(jt);
        }
        tag.put("saldos", arr);
        return tag;
    }
}
