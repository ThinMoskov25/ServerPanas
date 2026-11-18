package com.serverpanas.dailyquests;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
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
 * Barra arriba en la pantalla (como la de vida de los jefes) que le recuerda a
 * cada jugador sus misiones diarias: cuantas le quedan, cuantas estan listas
 * para reclamar y que se abren con /misiones. La barra avanza con las misiones
 * completadas. Cada jugador ve solo la suya. Desaparece cuando ya reclamo todas.
 */
public class QuestBar {

    private static final int CADA_TICKS = 100; // se actualiza cada 5 s
    private final Map<UUID, ServerBossEvent> barras = new HashMap<>();
    private int contador = 0;

    @SubscribeEvent
    public void alEntrar(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            actualizar(p);
        }
    }

    @SubscribeEvent
    public void alSalir(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerBossEvent b = barras.remove(event.getEntity().getUUID());
        if (b != null) {
            b.removeAllPlayers();
        }
    }

    @SubscribeEvent
    public void alTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++contador < CADA_TICKS) {
            return;
        }
        contador = 0;
        MinecraftServer server = event.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            actualizar(p);
        }
    }

    private void actualizar(ServerPlayer p) {
        QuestData data = QuestData.get(p.server);
        int total = 0, completadas = 0, reclamadas = 0;
        for (QuestData.Entrada e : data.misionesDeHoy(p.getUUID())) {
            QuestDef def = QuestRegistry.porId(e.questId);
            if (def == null) {
                continue;
            }
            total++;
            if (e.reclamada) {
                reclamadas++;
                completadas++;
            } else if (e.progreso >= def.cantidad()) {
                completadas++;
            }
        }
        int pendientes = total - reclamadas;
        int listas = completadas - reclamadas;
        ServerBossEvent barra = barras.get(p.getUUID());
        if (pendientes <= 0) {
            if (barra != null) {
                barra.removeAllPlayers();
                barras.remove(p.getUUID());
            }
            return;
        }
        if (barra == null) {
            barra = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
            barras.put(p.getUUID(), barra);
        }
        MutableComponent nombre = Component.literal("📜 Misiones diarias  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(completadas + " / " + total).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  ·  Tienes " + pendientes + (pendientes == 1 ? " misión pendiente" : " misiones pendientes"))
                        .withStyle(ChatFormatting.YELLOW));
        if (listas > 0) {
            nombre.append(Component.literal("  ·  " + listas + (listas == 1 ? " lista para reclamar" : " listas para reclamar"))
                    .withStyle(ChatFormatting.GREEN));
        }
        nombre.append(Component.literal("  —  búscalas con /misiones").withStyle(ChatFormatting.AQUA));
        barra.setName(nombre);
        barra.setColor(listas > 0 ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW);
        barra.setProgress(total > 0 ? (float) completadas / total : 0f);
        if (!barra.getPlayers().contains(p)) {
            barra.removeAllPlayers();
            barra.addPlayer(p);
        }
    }
}
