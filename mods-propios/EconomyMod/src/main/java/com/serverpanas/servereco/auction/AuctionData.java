package com.serverpanas.servereco.auction;

import com.serverpanas.servereco.ServerEcoMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SavedData de la casa de subastas (world/data/servereco_auction.dat).
 * Guarda las subastas publicas activas. Solo NBT nativo.
 */
public class AuctionData extends SavedData {

    public static final String NOMBRE = ServerEcoMod.MOD_ID + "_auction";
    public static final int MAX_SUBASTAS = 54; // cabe en un cofre de 6 filas

    /** Una subasta publica. */
    public static class Lote {
        public long id;
        public UUID vendedor;
        public String vendedorNombre;
        public ItemStack item;
        public int precio;      // creditos

        public Lote(long id, UUID vendedor, String vendedorNombre, ItemStack item, int precio) {
            this.id = id;
            this.vendedor = vendedor;
            this.vendedorNombre = vendedorNombre;
            this.item = item;
            this.precio = precio;
        }
    }

    private final List<Lote> lotes = new ArrayList<>();
    private long siguienteId = 1;

    public static AuctionData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                AuctionData::cargar, AuctionData::new, NOMBRE);
    }

    public List<Lote> listar() {
        return lotes;
    }

    public boolean lleno() {
        return lotes.size() >= MAX_SUBASTAS;
    }

    public long publicar(UUID vendedor, String nombre, ItemStack item, int precio) {
        long id = siguienteId++;
        lotes.add(new Lote(id, vendedor, nombre, item.copy(), precio));
        setDirty();
        return id;
    }

    public Lote porId(long id) {
        for (Lote l : lotes) {
            if (l.id == id) {
                return l;
            }
        }
        return null;
    }

    public void quitar(long id) {
        if (lotes.removeIf(l -> l.id == id)) {
            setDirty();
        }
    }

    // ---------------- Persistencia ----------------

    public static AuctionData cargar(CompoundTag tag) {
        AuctionData data = new AuctionData();
        data.siguienteId = Math.max(1, tag.getLong("siguienteId"));
        ListTag arr = tag.getList("lotes", Tag.TAG_COMPOUND);
        for (int i = 0; i < arr.size(); i++) {
            CompoundTag lt = arr.getCompound(i);
            data.lotes.add(new Lote(
                    lt.getLong("id"),
                    lt.getUUID("vendedor"),
                    lt.getString("vendedorNombre"),
                    ItemStack.of(lt.getCompound("item")),
                    lt.getInt("precio")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("siguienteId", siguienteId);
        ListTag arr = new ListTag();
        for (Lote l : lotes) {
            CompoundTag lt = new CompoundTag();
            lt.putLong("id", l.id);
            lt.putUUID("vendedor", l.vendedor);
            lt.putString("vendedorNombre", l.vendedorNombre);
            lt.put("item", l.item.save(new CompoundTag()));
            lt.putInt("precio", l.precio);
            arr.add(lt);
        }
        tag.put("lotes", arr);
        return tag;
    }
}
