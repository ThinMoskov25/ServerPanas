package com.serverpanas.invbackup;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Inventory Backup (Server-Side).
 *
 * Mod exclusivo del servidor: guarda respaldos del inventario de cada jugador
 * (al morir y al salir) y permite restaurarlos con un comando de operador.
 * Los clientes NO necesitan tener este mod instalado.
 *
 * La clave "server-only" es el IExtensionPoint.DisplayTest con IGNORESERVERONLY:
 * le dice a Forge que este mod puede faltar en el cliente, para que no bloquee
 * la conexion con un error de "mismatched mods".
 */
@Mod(InvBackupMod.MOD_ID)
public class InvBackupMod {

    public static final String MOD_ID = "invbackup";
    public static final Logger LOG = LogUtils.getLogger();

    public InvBackupMod() {
        // El cliente ignora la ausencia de este mod (mod solo-servidor).
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        // Registrar los manejadores de eventos de juego (muerte, salida) y
        // el registro de comandos. Funciona en servidor dedicado e integrado.
        MinecraftForge.EVENT_BUS.register(new BackupEvents());
    }
}
