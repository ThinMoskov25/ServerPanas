package com.serverpanas.servereco.mascotas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Encuentra una mascota aunque su chunk no este cargado: carga ese chunk un
 * momento (forceload), espera a que aparezca la entidad, hace la accion y lo
 * libera. Si el chunk ya estaba forzado por otra cosa (p. ej. los jefes del
 * panel), NO lo libera.
 */
public final class PetLoader {
    private static final int ESPERA_TICKS = 200; // 10 s

    private static final class Pedido {
        UUID mascota;
        ResourceKey<Level> dim;
        ChunkPos chunk;
        boolean forzadoPorMi;
        int ticks = ESPERA_TICKS;
        Consumer<Entity> accion;
        Runnable fallo;
    }

    private static final List<Pedido> PEDIDOS = new ArrayList<>();

    private PetLoader() {
    }

    public static Entity buscarCargada(MinecraftServer s, UUID id) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null && e.isAlive()) return e;
        }
        return null;
    }

    /** Ejecuta la accion con la mascota (cargandola si hace falta) o llama a fallo. */
    public static void con(MinecraftServer s, UUID mascota, Consumer<Entity> accion, Runnable fallo) {
        Entity e = buscarCargada(s, mascota);
        if (e != null) {
            accion.accept(e);
            return;
        }
        CompoundTag info = PetRegistry.get(s).info(mascota);
        if (info == null) {
            fallo.run();
            return;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(info.getString("dim")));
        ServerLevel l = s.getLevel(dim);
        if (l == null) {
            fallo.run();
            return;
        }
        Pedido p = new Pedido();
        p.mascota = mascota;
        p.dim = dim;
        p.chunk = new ChunkPos(BlockPos.containing(info.getDouble("x"), 0, info.getDouble("z")));
        p.forzadoPorMi = !l.getForcedChunks().contains(p.chunk.toLong());
        if (p.forzadoPorMi) l.setChunkForced(p.chunk.x, p.chunk.z, true);
        p.accion = accion;
        p.fallo = fallo;
        PEDIDOS.add(p);
    }

    static void tick(MinecraftServer s) {
        if (PEDIDOS.isEmpty()) return;
        Iterator<Pedido> it = PEDIDOS.iterator();
        List<Runnable> despues = new ArrayList<>();
        while (it.hasNext()) {
            Pedido p = it.next();
            ServerLevel l = s.getLevel(p.dim);
            Entity e = l == null ? null : l.getEntity(p.mascota);
            if (e != null && e.isAlive()) {
                it.remove();
                Entity fe = e;
                despues.add(() -> {
                    try {
                        p.accion.accept(fe);
                    } finally {
                        liberar(l, p);
                    }
                });
            } else if (--p.ticks <= 0 || l == null) {
                it.remove();
                liberar(l, p);
                despues.add(p.fallo);
            }
        }
        despues.forEach(Runnable::run);
    }

    private static void liberar(ServerLevel l, Pedido p) {
        if (l != null && p.forzadoPorMi) l.setChunkForced(p.chunk.x, p.chunk.z, false);
    }

    /** Lleva la mascota junto al jugador (las grandes, como dragones, un poco por encima). */
    public static boolean moverA(Entity e, ServerPlayer j) {
        if (e.isPassenger()) e.stopRiding();
        if (e.isVehicle() && !e.hasPassenger(j)) e.ejectPassengers();
        if (e.hasPassenger(j)) return true;
        double y = j.getY() + (e.getBbHeight() > 2.5f ? 3 : 0);
        boolean ok = e.teleportTo(j.serverLevel(), j.getX(), y, j.getZ(), Set.of(), e.getYRot(), e.getXRot());
        if (ok) {
            Entity nueva = j.serverLevel().getEntity(e.getUUID());
            Entity m = nueva != null ? nueva : e;
            m.fallDistance = 0;
            if (m instanceof Mob mob) mob.getNavigation().stop();
            UUID d = PetRegistry.dueno(m);
            if (d != null) PetRegistry.get(j.server).actualizar(m, d, true);
        }
        return ok;
    }
}
