package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.data.BalanceData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Modulo AGREGAR CREDITO (interactivo por arrastre), 100% server-side
 * (ChestMenu vanilla GENERIC_9x6 via DepositMenu).
 *
 * El jugador ARRASTRA al area de deposito (filas 0..44) los items que quiere
 * convertir en creditos. El menu muestra EN VIVO el valor total (ItemValues *
 * cantidades). Al CONFIRMAR, se CONSUMEN los items y se suma el 100% del valor
 * al SALDO de creditos del jugador. Esto es "cargar dinero" al 100%.
 *
 * Modulo AISLADO: el unico boton de navegacion es REGRESAR, que vuelve al menu
 * principal de /tienda. No muestra botones de otros modulos.
 *
 * Nunca se pierden items: REGRESAR y el cierre del menu devuelven lo no
 * confirmado al inventario (o lo sueltan si no cabe).
 */
public class AddCreditMenu extends DepositMenu {

    private static final int SLOT_CONFIRMAR = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_SALDO = 50;
    private static final int SLOT_REGRESAR = 45;

    public AddCreditMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
        super(id, invJugador, dueno, muestra);
        if (dueno != null) {
            alCambiarDeposito();
            broadcastChanges();
        }
    }

    public static Container nuevoContenedor() {
        return new VirtualMenuContainer(RANURAS);
    }

    @Override
    protected void marcarDeposito(boolean[] deposito) {
        // Filas 0..44 = deposito; fila inferior 45..53 = info/botones.
        for (int i = 0; i < 45; i++) {
            deposito[i] = true;
        }
    }

    @Override
    protected void alCambiarDeposito() {
        if (dueno == null) {
            return;
        }
        limpiarBotones();

        int valorTotal = valorTotalDeposito();
        boolean hay = !depositoVacio();
        long saldo = BalanceData.get(dueno.server).saldo(dueno);

        ItemStack info = new ItemStack(Items.PAPER);
        MenuUtil.nombre(info, "&b&lAGREGAR CREDITO");
        MenuUtil.lore(info, List.of(
                "&7Arrastra aqui los items que quieras",
                "&7convertir en creditos (parte pilas",
                "&7con clic derecho).",
                "",
                "&7Se paga el &a100%&7 del valor.",
                "&7Valor a cargar: &e" + valorTotal + " &7creditos",
                "",
                hay ? "&aPulsa CONFIRMAR para cargar" : "&8Area de deposito vacia"));
        muestra.setItem(SLOT_INFO, info);

        ItemStack confirmar = new ItemStack(Items.EMERALD_BLOCK);
        MenuUtil.nombre(confirmar, "&a&lCONFIRMAR CARGA");
        MenuUtil.lore(confirmar, List.of(
                "&7Consume lo depositado y suma el",
                "&7&a100%&7 de su valor a tu saldo.",
                "",
                "&7Cargaras: &e" + valorTotal + " &7creditos",
                "",
                hay ? "&eClic para confirmar" : "&8Deposita items primero"));
        muestra.setItem(SLOT_CONFIRMAR, confirmar);

        ItemStack saldoIcon = new ItemStack(Items.GOLD_INGOT);
        MenuUtil.nombre(saldoIcon, "&6&lTU SALDO");
        MenuUtil.lore(saldoIcon, List.of(
                "&7Creditos actuales: &e" + saldo));
        muestra.setItem(SLOT_SALDO, saldoIcon);

        muestra.setItem(SLOT_REGRESAR, MenuUtil.boton(Items.ARROW, "&7&lREGRESAR",
                List.of("&7Vuelve al menu principal.",
                        "&7Te devuelve lo que no confirmaste.")));
    }

    @Override
    protected void alClicBoton(int slotId, int button, ClickType type) {
        switch (slotId) {
            case SLOT_CONFIRMAR -> { cargarCredito(); alCambiarDeposito(); broadcastChanges(); }
            case SLOT_REGRESAR -> { devolverDeposito(); Menus.abrirPrincipal(dueno); }
            default -> { /* nada */ }
        }
    }

    /** Consume lo depositado y suma el 100% del valor al saldo. ATOMICO. */
    private void cargarCredito() {
        int valorTotal = valorTotalDeposito();
        if (valorTotal <= 0) {
            dueno.sendSystemMessage(Component.literal("Deposita items con valor primero.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int items = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack s = muestra.getItem(i);
            if (!s.isEmpty()) {
                items += s.getCount();
                muestra.setItem(i, ItemStack.EMPTY); // consumir primero
            }
        }
        BalanceData.get(dueno.server).sumar(dueno, valorTotal); // sumar al saldo despues
        long saldo = BalanceData.get(dueno.server).saldo(dueno);
        dueno.sendSystemMessage(Component.literal("Cargaste " + items + " items por "
                + valorTotal + " creditos. Saldo: " + saldo).withStyle(ChatFormatting.GREEN));
    }
}
