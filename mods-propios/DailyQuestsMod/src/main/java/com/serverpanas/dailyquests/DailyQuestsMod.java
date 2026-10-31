package com.serverpanas.dailyquests;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Daily Quests (Server-Side).
 *
 * Mod exclusivo del servidor: genera misiones diarias al azar, rastrea el
 * progreso con eventos de Forge, muestra las misiones en un COFRE VIRTUAL
 * (sin GUIs de cliente) y entrega recompensas ejecutando comandos como
 * consola. Los clientes NO necesitan tener este mod instalado.
 *
 * La clave "server-only" es el IExtensionPoint.DisplayTest con IGNORESERVERONLY:
 * le dice a Forge que este mod puede faltar en el cliente, de modo que no
 * bloquee la conexion con un error de "mismatched mods". Toda la UI se hace
 * con un AbstractContainerMenu (tipo cofre), que el cliente vanilla ya sabe
 * dibujar: el servidor solo le manda "abre un cofre con estos items".
 */
@Mod(DailyQuestsMod.MOD_ID)
public class DailyQuestsMod {

    public static final String MOD_ID = "dailyquests";
    public static final Logger LOG = LogUtils.getLogger();

    public DailyQuestsMod() {
        // 1) Declarar el mod como "solo servidor": el cliente ignora su ausencia.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        // NOTA: no registramos ningun MenuType propio. La UI usa el cofre
        // VANILLA (GENERIC_9x6), para que el mod sea de verdad solo-servidor.

        // Registrar los manejadores de eventos de juego (minar, matar, recoger)
        // y el comando /misiones en el bus principal de Forge.
        MinecraftForge.EVENT_BUS.register(new QuestEvents());
    }
}
