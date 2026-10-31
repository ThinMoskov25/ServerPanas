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
import java.util.List;
import java.util.UUID;

/**
 * SavedData del MERCADO GLOBAL (world/data/servereco_market.dat).
 *
 * Guarda los "listados de venta de jugador": items que un jugador puso EN
 * VENTA desde el menu VENDER (opcion "poner en venta"). Cuando otro jugador
 * los compra desde el menu COMPRAR, el vendedor cobra el 100% en fisico
 * (directo si esta online, o a su buzon si esta offline).
 *
 * Mismo patron NBT nativo que AuctionData. Cero dependencias externas.
 */
public class MarketData extends SavedData {

    public static final String NOMBRE = ServerEcoMod.MOD_ID + "_market";
    public static final int MAX_LISTADOS = 512;

    /** Un listado de venta del mercado global. */
    public static class Listado {
        public long id;
        public UUID vendedor;
        public String vendedorNombre;
        public ItemStack item;
        public int precio;      // creditos (valor COMPLETO, sin -10%)

        public Listado(long id, UUID vendedor, String vendedorNombre, ItemStack item, int precio) {
            this.id = id;
            this.vendedor = vendedor;
            this.vendedorNombre = vendedorNombre;
            this.item = item;
            this.precio = precio;
        }
    }

    private final List<Listado> listados = new ArrayList<>();
    private long siguienteId = 1;

    public static MarketData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                MarketData::cargar, MarketData::new, NOMBRE);
    }

    public List<Listado> listar() {
        return listados;
    }

    public boolean lleno() {
        return listados.size() >= MAX_LISTADOS;
    }

    public long publicar(UUID vendedor, String nombre, ItemStack item, int precio) {
        long id = siguienteId++;
        listados.add(new Listado(id, vendedor, nombre, item.copy(), precio));
        setDirty();
        return id;
    }

    public Listado porId(long id) {
        for (Listado l : listados) {
            if (l.id == id) {
                return l;
            }
        }
        return null;
    }

    public void quitar(long id) {
        if (listados.removeIf(l -> l.id == id)) {
            setDirty();
        }
    }

    // ---------------- Persistencia ----------------

    public static MarketData cargar(CompoundTag tag) {
        MarketData data = new MarketData();
        data.siguienteId = Math.max(1, tag.getLong("siguienteId"));
        ListTag arr = tag.getList("listados", Tag.TAG_COMPOUND);
        for (int i = 0; i < arr.size(); i++) {
            CompoundTag lt = arr.getCompound(i);
            data.listados.add(new Listado(
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
        for (Listado l : listados) {
            CompoundTag lt = new CompoundTag();
            lt.putLong("id", l.id);
            lt.putUUID("vendedor", l.vendedor);
            lt.putString("vendedorNombre", l.vendedorNombre);
            lt.put("item", l.item.save(new CompoundTag()));
            lt.putInt("precio", l.precio);
            arr.add(lt);
        }
        tag.put("listados", arr);
        return tag;
    }
}
