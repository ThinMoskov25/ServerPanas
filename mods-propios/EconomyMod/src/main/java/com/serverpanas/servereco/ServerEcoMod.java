package com.serverpanas.servereco;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Server Economy (Server-Side).
 *
 * Integra los sistemas de economia, todos del lado del servidor:
 *   1. SALDO de creditos persistente por jugador (BalanceData). El "dinero" es
 *      este saldo virtual, NO las monedas del inventario. Se ve en /tienda y
 *      con /creditos (alias /bal). Se gana AGREGANDO CREDITO (100% del valor de
 *      items) o VENDIENDO al sistema (90%).
 *   2. Tienda por DESCUBRIMIENTO (/shop -> COMPRAR): un cofre virtual que solo
 *      muestra los items que el jugador ya obtuvo alguna vez. Comprar descuenta
 *      del saldo y entrega el item (no consume inventario fisico).
 *   3. Mercado global y casa de SUBASTAS (/ah): los jugadores suben items; el
 *      comprador paga desde su saldo y el precio se abona al saldo del vendedor
 *      (persistente aunque este offline).
 *
 * Toda la UI se hace con AbstractContainerMenu (cofre), sin GUIs de cliente.
 * El IGNORESERVERONLY permite que los clientes vanilla entren sin el mod.
 */
@Mod(ServerEcoMod.MOD_ID)
public class ServerEcoMod {

    public static final String MOD_ID = "servereco";
    public static final Logger LOG = LogUtils.getLogger();

    public ServerEcoMod() {
        // Mod solo-servidor: el cliente ignora su ausencia.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        // Registrar los MenuType de los cofres virtuales (bus del mod).
        EcoRegistry.register();

        // Eventos (descubrimiento) y comandos (/shop, /ah, /buzon).
        MinecraftForge.EVENT_BUS.register(new EcoEvents());
    }
}
