package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.data.BalanceData;
import com.serverpanas.servereco.economy.EcoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PODERES que se compran con el saldo de creditos (100% server-side).
 *
 *  - tp_ir / tp_traer: piden permiso al compañero, que acepta con un clic en
 *    el chat (/tpaceptar). Se cobra SOLO cuando acepta. La solicitud caduca a
 *    los 60 s y hay 30 s de espera entre solicitudes del mismo jugador.
 *  - tp_cama: vuelve a la cama/ancla (o al spawn del mundo si no tiene).
 *  - curar y efectos de 10 minutos: se aplican y cobran al instante.
 *
 * Precios en valores.json -> "poderes" (0 = desactivado).
 */
public final class Powers {

    public record Poder(String id, String nombre, Item icono, String descripcion, boolean conCompanero,
                        MobEffect efecto, int nivel) {
    }

    public static final Map<String, Poder> PODERES = new LinkedHashMap<>();
    private static final int DIEZ_MIN = 20 * 60 * 10;

    static {
        agregar(new Poder("tp_ir", "Ir con un compañero", Items.ENDER_PEARL,
                "Te teletransporta junto a un compañero (si acepta).", true, null, 0));
        agregar(new Poder("tp_traer", "Traer a un compañero", Items.ENDER_EYE,
                "Trae a un compañero a tu lado (si acepta).", true, null, 0));
        agregar(new Poder("tp_cama", "Volver a mi cama", Items.RED_BED,
                "Vuelves a tu cama o ancla; si no tienes, al spawn.", false, null, 0));
        agregar(new Poder("curar", "Curación completa", Items.GOLDEN_APPLE,
                "Vida y hambre al máximo, y apaga el fuego.", false, null, 0));
        agregar(new Poder("vision_nocturna", "Visión nocturna (10 min)", Items.GOLDEN_CARROT,
                "Ves en la oscuridad durante 10 minutos.", false, MobEffects.NIGHT_VISION, 0));
        agregar(new Poder("prisa", "Prisa II (10 min)", Items.GOLDEN_PICKAXE,
                "Picas mucho más rápido durante 10 minutos.", false, MobEffects.DIG_SPEED, 1));
        agregar(new Poder("velocidad", "Velocidad II (10 min)", Items.SUGAR,
                "Corres más rápido durante 10 minutos.", false, MobEffects.MOVEMENT_SPEED, 1));
        agregar(new Poder("resistencia_fuego", "Resistencia al fuego (10 min)", Items.MAGMA_CREAM,
                "Inmune al fuego y la lava durante 10 minutos.", false, MobEffects.FIRE_RESISTANCE, 0));
        agregar(new Poder("respirar_agua", "Respirar bajo el agua (10 min)", Items.PUFFERFISH,
                "Respiras bajo el agua durante 10 minutos.", false, MobEffects.WATER_BREATHING, 0));
    }

    private static void agregar(Poder p) {
        PODERES.put(p.id(), p);
    }

    private Powers() {
    }

    /** Precio actual (0 = desactivado). */
    public static int precio(String id) {
        Integer v = EcoConfig.get().poderes.get(id);
        return v == null ? 0 : Math.max(0, v);
    }

    /** Poderes activos, en orden. */
    public static List<Poder> activos() {
        List<Poder> out = new ArrayList<>();
        for (Poder p : PODERES.values()) {
            if (precio(p.id()) > 0) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Poderes inmediatos
    // ------------------------------------------------------------------
    public static void usarInmediato(ServerPlayer jugador, Poder poder) {
        int precio = precio(poder.id());
        if (precio <= 0) {
            error(jugador, "Ese poder no está disponible.");
            return;
        }
        Vec3 destino = null;
        ServerLevel nivelDestino = null;
        if (poder.id().equals("tp_cama")) {
            MinecraftServer server = jugador.server;
            BlockPos cama = jugador.getRespawnPosition();
            ServerLevel nivel = server.getLevel(jugador.getRespawnDimension());
            if (cama != null && nivel != null) {
                Optional<Vec3> pos = Player.findRespawnPositionAndUseSpawnBlock(
                        nivel, cama, jugador.getRespawnAngle(), jugador.isRespawnForced(), true);
                if (pos.isPresent()) {
                    destino = pos.get();
                    nivelDestino = nivel;
                }
            }
            if (destino == null) {
                nivelDestino = server.overworld();
                BlockPos s = nivelDestino.getSharedSpawnPos();
                destino = Vec3.atBottomCenterOf(s);
            }
        }
        BalanceData bal = BalanceData.get(jugador.server);
        if (!bal.restar(jugador, precio)) {
            error(jugador, "No tienes créditos suficientes (cuesta " + precio + ").");
            return;
        }
        switch (poder.id()) {
            case "tp_cama" -> teletransportar(jugador, nivelDestino, destino.x, destino.y, destino.z);
            case "curar" -> {
                jugador.setHealth(jugador.getMaxHealth());
                jugador.getFoodData().setFoodLevel(20);
                jugador.getFoodData().setSaturation(20f);
                jugador.clearFire();
            }
            default -> {
                if (poder.efecto() != null) {
                    jugador.addEffect(new MobEffectInstance(poder.efecto(), DIEZ_MIN, poder.nivel(), false, true));
                }
            }
        }
        ok(jugador, "Has comprado " + poder.nombre() + " por " + precio + " créditos. Saldo: " + bal.saldo(jugador));
        jugador.level().playSound(null, jugador.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------
    //  Teletransporte con permiso del compañero
    // ------------------------------------------------------------------
    private record Solicitud(UUID solicitante, String poder, long expira) {
    }

    private static final Map<UUID, Solicitud> PENDIENTES = new HashMap<>();   // destinatario -> solicitud
    private static final Map<UUID, Long> ESPERA = new HashMap<>();            // solicitante -> fin de espera
    private static final long CADUCA_MS = 60_000;
    private static final long ESPERA_MS = 30_000;

    public static synchronized void solicitar(ServerPlayer yo, String poderId, UUID companero) {
        Poder poder = PODERES.get(poderId);
        int precio = precio(poderId);
        if (poder == null || !poder.conCompanero() || precio <= 0) {
            error(yo, "Ese poder no está disponible.");
            return;
        }
        ServerPlayer otro = yo.server.getPlayerList().getPlayer(companero);
        if (otro == null || otro == yo) {
            error(yo, "Ese jugador ya no está conectado.");
            return;
        }
        long ahora = System.currentTimeMillis();
        Long fin = ESPERA.get(yo.getUUID());
        if (fin != null && fin > ahora) {
            error(yo, "Espera " + ((fin - ahora) / 1000 + 1) + " s antes de pedir otro teletransporte.");
            return;
        }
        if (!BalanceData.get(yo.server).tiene(yo.getUUID(), precio)) {
            error(yo, "No tienes créditos suficientes (cuesta " + precio + ").");
            return;
        }
        ESPERA.put(yo.getUUID(), ahora + ESPERA_MS);
        PENDIENTES.put(otro.getUUID(), new Solicitud(yo.getUUID(), poderId, ahora + CADUCA_MS));

        String accion = poderId.equals("tp_ir") ? " quiere teletransportarse a tu lado." : " quiere traerte a su lado.";
        MutableComponent msg = Component.literal("[Tienda] ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(yo.getGameProfile().getName()).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(false)))
                .append(Component.literal(accion + " ").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(false)))
                .append(boton("[Aceptar]", ChatFormatting.GREEN, "/tpaceptar"))
                .append(Component.literal(" "))
                .append(boton("[Rechazar]", ChatFormatting.RED, "/tprechazar"));
        otro.sendSystemMessage(msg);
        otro.level().playSound(null, otro.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
        ok(yo, "Solicitud enviada a " + otro.getGameProfile().getName()
                + ". Se cobrarán " + precio + " créditos si acepta (caduca en 60 s).");
    }

    public static synchronized int aceptar(ServerPlayer yo) {
        Solicitud s = PENDIENTES.remove(yo.getUUID());
        if (s == null || s.expira() < System.currentTimeMillis()) {
            error(yo, "No tienes ninguna solicitud de teletransporte pendiente.");
            return 0;
        }
        ServerPlayer otro = yo.server.getPlayerList().getPlayer(s.solicitante());
        if (otro == null) {
            error(yo, "El jugador que lo pidió ya no está conectado.");
            return 0;
        }
        int precio = precio(s.poder());
        BalanceData bal = BalanceData.get(yo.server);
        if (precio <= 0 || !bal.restar(otro, precio)) {
            error(yo, otro.getGameProfile().getName() + " ya no tiene créditos suficientes.");
            error(otro, "No tenías créditos suficientes: el teletransporte se canceló.");
            return 0;
        }
        if (s.poder().equals("tp_ir")) {
            teletransportar(otro, yo.serverLevel(), yo.getX(), yo.getY(), yo.getZ());
        } else {
            teletransportar(yo, otro.serverLevel(), otro.getX(), otro.getY(), otro.getZ());
        }
        ok(yo, "Teletransporte aceptado.");
        ok(otro, yo.getGameProfile().getName() + " aceptó. Pagaste " + precio + " créditos. Saldo: " + bal.saldo(otro));
        return 1;
    }

    public static synchronized int rechazar(ServerPlayer yo) {
        Solicitud s = PENDIENTES.remove(yo.getUUID());
        if (s == null) {
            error(yo, "No tienes ninguna solicitud pendiente.");
            return 0;
        }
        ok(yo, "Solicitud rechazada.");
        ServerPlayer otro = yo.server.getPlayerList().getPlayer(s.solicitante());
        if (otro != null) {
            error(otro, yo.getGameProfile().getName() + " rechazó tu teletransporte. No se te cobró nada.");
        }
        return 1;
    }

    // ------------------------------------------------------------------
    private static void teletransportar(ServerPlayer p, ServerLevel nivel, double x, double y, double z) {
        p.stopRiding();
        p.teleportTo(nivel, x, y, z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        nivel.playSound(null, BlockPos.containing(x, y, z), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1f);
    }

    private static MutableComponent boton(String texto, ChatFormatting color, String comando) {
        return Component.literal(texto).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, comando))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(comando))));
    }

    private static void ok(ServerPlayer p, String texto) {
        p.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.GREEN));
    }

    private static void error(ServerPlayer p, String texto) {
        p.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.RED));
    }
}
