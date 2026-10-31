package com.serverpanas.servereco.data;

import com.serverpanas.servereco.ServerEcoMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SavedData persistente (world/data/servereco_discovery.dat):
 *  - descubiertos: por UUID, el set de ids de item que el jugador ya obtuvo.
 *  - buzon: por UUID, items pendientes de entregar (pagos de subastas cuando
 *    el vendedor estaba desconectado o sin espacio).
 *
 * Solo NBT nativo; cero dependencias externas.
 */
public class DiscoveryData extends SavedData {

    public static final String NOMBRE = ServerEcoMod.MOD_ID + "_discovery";

    private final Map<UUID, Set<String>> descubiertos = new HashMap<>();
    private final Map<UUID, List<ItemStack>> buzon = new HashMap<>();

    public static DiscoveryData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                DiscoveryData::cargar, DiscoveryData::new, NOMBRE);
    }

    // ---------------- Descubrimiento ----------------

    /** Registra un id como descubierto. Devuelve true si es la PRIMERA vez. */
    public boolean descubrir(UUID jugador, String itemId) {
        Set<String> set = descubiertos.computeIfAbsent(jugador, k -> new HashSet<>());
        boolean nuevo = set.add(itemId);
        if (nuevo) {
            setDirty();
        }
        return nuevo;
    }

    public Set<String> descubiertosDe(UUID jugador) {
        return descubiertos.getOrDefault(jugador, Set.of());
    }

    // ---------------- Buzon ----------------

    public void aBuzon(UUID jugador, ItemStack stack) {
        buzon.computeIfAbsent(jugador, k -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    public List<ItemStack> verBuzon(UUID jugador) {
        return buzon.getOrDefault(jugador, List.of());
    }

    public List<ItemStack> vaciarBuzon(UUID jugador) {
        List<ItemStack> items = buzon.remove(jugador);
        if (items != null && !items.isEmpty()) {
            setDirty();
            return items;
        }
        return List.of();
    }

    // ---------------- Persistencia ----------------

    public static DiscoveryData cargar(CompoundTag tag) {
        DiscoveryData data = new DiscoveryData();

        ListTag descs = tag.getList("descubiertos", Tag.TAG_COMPOUND);
        for (int i = 0; i < descs.size(); i++) {
            CompoundTag jt = descs.getCompound(i);
            UUID uuid = jt.getUUID("uuid");
            Set<String> set = new HashSet<>();
            ListTag ids = jt.getList("ids", Tag.TAG_STRING);
            for (int j = 0; j < ids.size(); j++) {
                set.add(ids.getString(j));
            }
            data.descubiertos.put(uuid, set);
        }

        ListTag buzones = tag.getList("buzon", Tag.TAG_COMPOUND);
        for (int i = 0; i < buzones.size(); i++) {
            CompoundTag jt = buzones.getCompound(i);
            UUID uuid = jt.getUUID("uuid");
            List<ItemStack> items = new ArrayList<>();
            ListTag arr = jt.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < arr.size(); j++) {
                items.add(ItemStack.of(arr.getCompound(j)));
            }
            data.buzon.put(uuid, items);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag descs = new ListTag();
        for (Map.Entry<UUID, Set<String>> en : descubiertos.entrySet()) {
            CompoundTag jt = new CompoundTag();
            jt.putUUID("uuid", en.getKey());
            ListTag ids = new ListTag();
            for (String id : en.getValue()) {
                ids.add(net.minecraft.nbt.StringTag.valueOf(id));
            }
            jt.put("ids", ids);
            descs.add(jt);
        }
        tag.put("descubiertos", descs);

        ListTag buzones = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> en : buzon.entrySet()) {
            if (en.getValue().isEmpty()) {
                continue;
            }
            CompoundTag jt = new CompoundTag();
            jt.putUUID("uuid", en.getKey());
            ListTag arr = new ListTag();
            for (ItemStack s : en.getValue()) {
                arr.add(s.save(new CompoundTag()));
            }
            jt.put("items", arr);
            buzones.add(jt);
        }
        tag.put("buzon", buzones);
        return tag;
    }
}
