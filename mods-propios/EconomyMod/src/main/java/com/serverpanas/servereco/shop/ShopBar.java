package com.serverpanas.servereco.shop;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aviso de la tienda: una barra arriba en la pantalla que dice "Visita la tienda
 * usando comando /tienda". Sale 30 segundos cada 40 minutos (contando desde que
 * el jugador entro), a mitad de ciclo para no coincidir con la barra de misiones.
 * La barra se va vaciando mientras se muestra.
 */
public class ShopBar {

    private static final long CICLO = 40L * 60 * 20;     // cada 40 minutos
    private static final long INICIO = 20L * 60 * 20;    // a los 20 min de cada ciclo
    private static final long DURACION = 30L * 20;       // visible 30 segundos
    private final Map<UUID, Long> entrada = new HashMap<>();
    private final Map<UUID, ServerBossEvent> barras = new HashMap<>();
    private long ticks = 0;

    @SubscribeEvent
    public void alEntrar(PlayerEvent.PlayerLoggedInEvent event) {
        entrada.put(event.getEntity().getUUID(), ticks);
    }

    @SubscribeEvent
    public void alSalir(PlayerEvent.PlayerLoggedOutEvent event) {
        entrada.remove(event.getEntity().getUUID());
        ServerBossEvent b = barras.remove(event.getEntity().getUUID());
        if (b != null) {
            b.removeAllPlayers();
        }
    }

    @SubscribeEvent
    public void alTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (ticks % 20 != 0) {
            return; // se revisa cada segundo
        }
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            long t = (ticks - entrada.computeIfAbsent(p.getUUID(), k -> ticks)) % CICLO;
            ServerBossEvent barra = barras.get(p.getUUID());
            if (t >= INICIO && t < INICIO + DURACION) {
                if (barra == null) {
                    barra = new ServerBossEvent(
                            Component.literal("🛒 Visita la tienda usando comando ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                    .append(Component.literal("/tienda").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)),
                            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
                    barra.addPlayer(p);
                    barras.put(p.getUUID(), barra);
                }
                barra.setProgress(1f - (float) (t - INICIO) / DURACION);
            } else if (barra != null) {
                barra.removeAllPlayers();
                barras.remove(p.getUUID());
            }
        }
    }
}
