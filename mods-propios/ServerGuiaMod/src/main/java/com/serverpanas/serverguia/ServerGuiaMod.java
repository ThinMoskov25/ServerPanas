package com.serverpanas.serverguia;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Server Guia (Server-Side PURO).
 *
 * Muestra una GUIA navegable del servidor dentro de un COFRE VIRTUAL vanilla
 * (ChestMenu con MenuType.GENERIC_9x6). No registra ningun MenuType propio ni
 * DeferredRegister, de modo que el cliente vanilla abre el cofre como uno
 * normal y NO necesita instalar nada.
 *
 * Navegacion:
 *   - VISTA LISTA (nivel 1): los mods del pack como iconos.
 *   - Clic en un mod -> VISTA DETALLE (nivel 2): el arbol/pasos de ese mod,
 *     reconstruyendo el contenido del MISMO cofre.
 *   - Boton VOLVER -> regresa a la VISTA LISTA.
 *
 * El IGNORESERVERONLY permite que los clientes vanilla entren sin el mod.
 */
@Mod(ServerGuiaMod.MOD_ID)
public class ServerGuiaMod {

    public static final String MOD_ID = "serverguia";
    public static final Logger LOG = LogUtils.getLogger();

    public ServerGuiaMod() {
        // Mod solo-servidor: el cliente ignora su ausencia.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        // Comando /guia y atajo por item (clic derecho). Sin registros de red.
        MinecraftForge.EVENT_BUS.register(new GuiaEvents());
    }
}
