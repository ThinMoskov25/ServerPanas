package com.serverpanas.servereco.mascotas;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.saveddata.SavedData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de las mascotas de cada jugador (world/data/serverpanas_mascotas.dat).
 * Guarda la ultima informacion conocida de cada mascota, aunque su chunk no este
 * cargado: asi el panel puede mostrarlas y el servidor sabe donde ir a buscarlas.
 */
public class PetRegistry extends SavedData {
    public static final String NOMBRE = "serverpanas_mascotas";

    private final Map<UUID, CompoundTag> mascotas = new LinkedHashMap<>();

    public static PetRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PetRegistry::cargar, PetRegistry::new, NOMBRE);
    }

    /** Dueno de la entidad si es una mascota de un jugador (animal domesticado). */
    public static UUID dueno(Entity e) {
        if (!(e instanceof Animal) || !(e instanceof OwnableEntity o)) {
            return null;
        }
        return o.getOwnerUUID();
    }

    public void actualizar(Entity e, UUID dueno, boolean cargada) {
        CompoundTag t = new CompoundTag();
        t.putString("uuid", e.getUUID().toString());
        t.putString("dueno", dueno.toString());
        ResourceLocation tipo = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        t.putString("tipo", tipo == null ? "?" : tipo.toString());
        t.putString("nombre", e.hasCustomName() && e.getCustomName() != null ? e.getCustomName().getString() : "");
        t.putString("dim", e.level().dimension().location().toString());
        t.putDouble("x", Math.round(e.getX() * 10) / 10.0);
        t.putDouble("y", Math.round(e.getY() * 10) / 10.0);
        t.putDouble("z", Math.round(e.getZ() * 10) / 10.0);
        if (e instanceof LivingEntity v) {
            t.putFloat("vida", v.getHealth());
            t.putFloat("vidaMax", v.getMaxHealth());
            t.putBoolean("bebe", v.isBaby());
        }
        if (e instanceof TamableAnimal ta) {
            t.putBoolean("sentado", ta.isOrderedToSit());
        }
        int etapa = entero(e, "getDragonStage");
        if (etapa >= 0) t.putInt("etapa", etapa);
        int orden = entero(e, "getCommand");
        if (orden >= 0) t.putInt("orden", orden);
        t.putBoolean("montada", e.isVehicle());
        t.putBoolean("cargada", cargada);
        t.putLong("visto", System.currentTimeMillis());
        mascotas.put(e.getUUID(), t);
        setDirty();
    }

    /** Llama por reflexion a un metodo int sin argumentos (dragones de Ice and Fire). */
    static int entero(Entity e, String metodo) {
        try {
            Method m = e.getClass().getMethod(metodo);
            Object r = m.invoke(e);
            return r instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    public void marcarDescargada(UUID id) {
        CompoundTag t = mascotas.get(id);
        if (t != null && t.getBoolean("cargada")) {
            t.putBoolean("cargada", false);
            setDirty();
        }
    }

    public void quitar(UUID id) {
        if (mascotas.remove(id) != null) {
            setDirty();
        }
    }

    public CompoundTag info(UUID id) {
        return mascotas.get(id);
    }

    public List<CompoundTag> de(UUID dueno) {
        String d = dueno.toString();
        List<CompoundTag> out = new ArrayList<>();
        for (CompoundTag t : mascotas.values()) {
            if (d.equals(t.getString("dueno"))) out.add(t);
        }
        return out;
    }

    public List<UUID> cargadas() {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> en : mascotas.entrySet()) {
            if (en.getValue().getBoolean("cargada")) out.add(en.getKey());
        }
        return out;
    }

    // ---------------------------------------------------------------- disco

    public static PetRegistry cargar(CompoundTag tag) {
        PetRegistry r = new PetRegistry();
        ListTag lista = tag.getList("mascotas", Tag.TAG_COMPOUND);
        for (int i = 0; i < lista.size(); i++) {
            CompoundTag t = lista.getCompound(i);
            try {
                r.mascotas.put(UUID.fromString(t.getString("uuid")), t);
            } catch (IllegalArgumentException ignorada) {
                // entrada corrupta: se descarta
            }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag lista = new ListTag();
        lista.addAll(mascotas.values());
        tag.put("mascotas", lista);
        return tag;
    }
}
