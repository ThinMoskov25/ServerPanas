package com.serverpanas.guideinjector;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Guide Injector (Server-Side).
 *
 * Mod exclusivo del servidor: al arrancar el servidor genera guias dentro del
 * mod FTB Quests escribiendo archivos .snbt en config/ftbquests/quests/chapters/.
 * Los clientes NO necesitan tener este mod instalado.
 *
 * La clave "server-only" es el IExtensionPoint.DisplayTest con IGNORESERVERONLY:
 * le dice a Forge que este mod puede faltar en el cliente, para que no bloquee
 * la conexion con un error de "mismatched mods".
 *
 * IMPORTANTE: este mod NO registra ningun DeferredRegister, MenuType, item,
 * bloque ni entidad. Cualquier registro sincronizado romperia el server-only y
 * bloquearia a los clientes vanilla. El mod solo escucha un evento del servidor
 * (ServerAboutToStartEvent) y escribe archivos de texto.
 */
@Mod(GuideInjectorMod.MOD_ID)
public class GuideInjectorMod {

    public static final String MOD_ID = "guideinjector";
    public static final Logger LOG = LogUtils.getLogger();

    public GuideInjectorMod() {
        // El cliente ignora la ausencia de este mod (mod solo-servidor).
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        // Registrar unicamente el manejador que escucha el arranque del servidor
        // y escribe los .snbt. Sin registros sincronizados.
        MinecraftForge.EVENT_BUS.register(new GuideInjector());
    }
}
