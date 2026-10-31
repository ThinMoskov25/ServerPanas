package com.serverpanas.invbackup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Manejadores de eventos del servidor:
 * - Al morir un jugador  -> respaldo "muerte".
 * - Al salir un jugador  -> respaldo "salida".
 * - Registro del comando /invrestore (y /invbackups).
 */
public class BackupEvents {

    @SubscribeEvent
    public void alMorir(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BackupStorage.guardar(player, "muerte");
        }
    }

    @SubscribeEvent
    public void alSalir(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BackupStorage.guardar(player, "salida");
        }
    }

    @SubscribeEvent
    public void registrarComandos(RegisterCommandsEvent event) {
        RestoreCommand.register(event.getDispatcher());
    }
}
