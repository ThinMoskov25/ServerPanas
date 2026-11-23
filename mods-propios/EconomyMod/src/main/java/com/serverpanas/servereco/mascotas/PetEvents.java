package com.serverpanas.servereco.mascotas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sigue a las mascotas de los jugadores y registra los comandos de consola
 * (los usa el panel; nivel de operador, respuestas solo para quien lo ejecuta):
 *
 *   /mascotas lista <jugador>
 *   /mascotas traer <jugador> <uuid|todas>
 *   /mascotas ir <jugador> <uuid>
 *   /mascotas curar <uuid>
 *   /mascotas sentar <uuid> si|no
 *   /mascotas nombre <uuid> <texto>
 */
public class PetEvents {
    private int contador = 0;

    // ---------------------------------------------------------------- seguimiento

    @SubscribeEvent
    public void alEntrar(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        UUID d = PetRegistry.dueno(e.getEntity());
        if (d != null) PetRegistry.get(e.getLevel().getServer()).actualizar(e.getEntity(), d, true);
    }

    @SubscribeEvent
    public void alSalir(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        UUID d = PetRegistry.dueno(en);
        if (d == null) return;
        PetRegistry reg = PetRegistry.get(e.getLevel().getServer());
        Entity.RemovalReason r = en.getRemovalReason();
        if (r == Entity.RemovalReason.KILLED || r == Entity.RemovalReason.DISCARDED) {
            reg.quitar(en.getUUID()); // murio o se guardo en un objeto (cuerno de dragon)
        } else if (r != Entity.RemovalReason.CHANGED_DIMENSION) {
            reg.actualizar(en, d, false);
        }
    }

    @SubscribeEvent
    public void alDomesticar(AnimalTameEvent e) {
        if (!e.getAnimal().level().isClientSide() && e.getTamer() != null) {
            PetRegistry.get(e.getAnimal().getServer()).actualizar(e.getAnimal(), e.getTamer().getUUID(), true);
        }
    }

    @SubscribeEvent
    public void alTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        PetLoader.tick(s);
        if (++contador < 200) return; // cada 10 s: posicion, vida y estado al dia
        contador = 0;
        PetRegistry reg = PetRegistry.get(s);
        Set<UUID> vistas = new HashSet<>();
        for (ServerLevel l : s.getAllLevels()) {
            for (Entity en : l.getAllEntities()) {
                UUID d = PetRegistry.dueno(en);
                if (d != null && en.isAlive()) {
                    reg.actualizar(en, d, true);
                    vistas.add(en.getUUID());
                }
            }
        }
        for (UUID id : reg.cargadas()) {
            if (!vistas.contains(id)) reg.marcarDescargada(id);
        }
    }

    // ---------------------------------------------------------------- acciones

    public static void sentar(Entity e, boolean si) {
        if (e instanceof TamableAnimal t) {
            t.setOrderedToSit(si);
            t.setInSittingPose(si);
        }
        if (si && e instanceof Mob m) m.getNavigation().stop();
        try { // dragones de Ice and Fire: 1 = quieto, 0 = deambular
            Method m = e.getClass().getMethod("setCommand", int.class);
            m.invoke(e, si ? 1 : 0);
        } catch (ReflectiveOperationException | RuntimeException ignorada) {
            // no es un dragon
        }
        UUID d = PetRegistry.dueno(e);
        if (d != null && e.getServer() != null) PetRegistry.get(e.getServer()).actualizar(e, d, true);
    }

    // ---------------------------------------------------------------- comandos

    @SubscribeEvent
    public void registrarComandos(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("mascotas")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("lista")
                        .then(Commands.argument("jugador", EntityArgument.player()).executes(this::lista)))
                .then(Commands.literal("traer")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("mascota", StringArgumentType.word()).executes(this::traer))))
                .then(Commands.literal("ir")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("mascota", StringArgumentType.word()).executes(this::ir))))
                .then(Commands.literal("curar")
                        .then(Commands.argument("mascota", StringArgumentType.word()).executes(c -> conMascota(c, "curada", en -> {
                            if (en instanceof LivingEntity v) {
                                v.setHealth(v.getMaxHealth());
                                v.clearFire();
                            }
                        }))))
                .then(Commands.literal("sentar")
                        .then(Commands.argument("mascota", StringArgumentType.word())
                                .then(Commands.literal("si").executes(c -> conMascota(c, "quieta", en -> sentar(en, true))))
                                .then(Commands.literal("no").executes(c -> conMascota(c, "libre", en -> sentar(en, false))))))
                .then(Commands.literal("nombre")
                        .then(Commands.argument("mascota", StringArgumentType.word())
                                .then(Commands.argument("texto", StringArgumentType.greedyString()).executes(c -> {
                                    String texto = StringArgumentType.getString(c, "texto").trim();
                                    return conMascota(c, "renombrada", en -> en.setCustomName(
                                            texto.isEmpty() ? null : Component.literal(texto.length() > 32 ? texto.substring(0, 32) : texto)));
                                })))));
    }

    private static void decir(CommandSourceStack src, String texto) {
        src.sendSuccess(() -> Component.literal(texto), false);
    }

    private static UUID uuid(CommandContext<CommandSourceStack> c) {
        try {
            return UUID.fromString(StringArgumentType.getString(c, "mascota"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int lista(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer j = EntityArgument.getPlayer(c, "jugador");
        List<CompoundTag> l = PetRegistry.get(j.server).de(j.getUUID());
        decir(c.getSource(), "[mascotas] " + j.getGameProfile().getName() + " tiene " + l.size() + " mascota(s)");
        for (CompoundTag t : l) {
            decir(c.getSource(), "[mascotas] " + t.getString("uuid") + " " + t.getString("tipo")
                    + (t.getString("nombre").isEmpty() ? "" : " \"" + t.getString("nombre") + "\"")
                    + " en " + t.getString("dim") + " " + (int) t.getDouble("x") + " " + (int) t.getDouble("y") + " " + (int) t.getDouble("z")
                    + (t.getBoolean("cargada") ? "" : " (no cargada)"));
        }
        return l.size();
    }

    private int traer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer j = EntityArgument.getPlayer(c, "jugador");
        CommandSourceStack src = c.getSource();
        String arg = StringArgumentType.getString(c, "mascota");
        List<UUID> ids = new java.util.ArrayList<>();
        if (arg.equalsIgnoreCase("todas")) {
            for (CompoundTag t : PetRegistry.get(j.server).de(j.getUUID())) ids.add(UUID.fromString(t.getString("uuid")));
        } else {
            UUID id = uuid(c);
            if (id == null) {
                decir(src, "[mascotas] UUID no valido");
                return 0;
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            decir(src, "[mascotas] " + j.getGameProfile().getName() + " no tiene mascotas registradas");
            return 0;
        }
        for (UUID id : ids) {
            PetLoader.con(j.server, id,
                    en -> decir(src, "[mascotas] " + (PetLoader.moverA(en, j) ? "Traida " : "No se pudo mover ") + id),
                    () -> decir(src, "[mascotas] No se encontro la mascota " + id));
        }
        return ids.size();
    }

    private int ir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer j = EntityArgument.getPlayer(c, "jugador");
        UUID id = uuid(c);
        if (id == null) {
            decir(c.getSource(), "[mascotas] UUID no valido");
            return 0;
        }
        Entity en = PetLoader.buscarCargada(j.server, id);
        if (en != null) {
            j.teleportTo((ServerLevel) en.level(), en.getX(), en.getY(), en.getZ(), j.getYRot(), j.getXRot());
        } else {
            CompoundTag t = PetRegistry.get(j.server).info(id);
            ServerLevel l = t == null ? null : j.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("dim"))));
            if (l == null) {
                decir(c.getSource(), "[mascotas] No se encontro la mascota " + id);
                return 0;
            }
            j.teleportTo(l, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), j.getYRot(), j.getXRot());
        }
        j.fallDistance = 0;
        decir(c.getSource(), "[mascotas] " + j.getGameProfile().getName() + " llevado junto a " + id);
        return 1;
    }

    private int conMascota(CommandContext<CommandSourceStack> c, String hecho, Consumer<Entity> accion) {
        UUID id = uuid(c);
        CommandSourceStack src = c.getSource();
        if (id == null) {
            decir(src, "[mascotas] UUID no valido");
            return 0;
        }
        PetLoader.con(src.getServer(), id, en -> {
            accion.accept(en);
            decir(src, "[mascotas] Mascota " + hecho + ": " + id);
        }, () -> decir(src, "[mascotas] No se encontro la mascota " + id));
        return 1;
    }
}
