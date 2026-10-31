package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.data.BalanceData;
import com.serverpanas.servereco.data.MarketData;
import com.serverpanas.servereco.economy.EcoConfig;
import com.serverpanas.servereco.economy.ItemValues;
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
 * Modulo VENDER (interactivo por arrastre), 100% server-side (ChestMenu vanilla
 * GENERIC_9x6 via DepositMenu).
 *
 * El jugador ARRASTRA al AREA DE DEPOSITO los items que quiera vender. El menu
 * muestra EN VIVO el valor total (ItemValues * cantidades).
 *
 * Dos acciones (distintas a proposito de AGREGAR CREDITO, que paga el 100%):
 *   - VENDER AL SISTEMA: consume lo depositado y suma al SALDO el valor -10%
 *     (90%). Pago inmediato al saldo.
 *   - PONER EN VENTA: publica cada pila al mercado global a su valor COMPLETO
 *     (100%); cuando otro jugador la compra, el vendedor recibe el 100% en
 *     CREDITOS a su saldo.
 *
 * Modulo AISLADO: el unico boton de navegacion es REGRESAR, que vuelve al menu
 * principal. No muestra botones de otros modulos.
 *
 * Nunca se pierden items: REGRESAR y el cierre del menu devuelven lo no
 * confirmado al inventario (o lo sueltan si no cabe).
 */
public class SellMenu extends DepositMenu {

    // Fila inferior: botones de accion y navegacion.
    private static final int SLOT_SISTEMA = 47;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_MERCADO = 51;
    private static final int SLOT_REGRESAR = 45;

    public SellMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
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
        // Las primeras 5 filas (0..44) son area de deposito; la fila inferior
        // (45..53) son botones.
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
        int alSistema = (int) Math.floor(valorTotal * EcoConfig.get().porcentajeVentaSistema);
        boolean hay = !depositoVacio();
        long saldo = BalanceData.get(dueno.server).saldo(dueno);

        ItemStack info = new ItemStack(Items.PAPER);
        MenuUtil.nombre(info, "&6&lVENDER");
        MenuUtil.lore(info, List.of(
                "&7Arrastra aqui los items que quieras",
                "&7vender (parte pilas con clic derecho).",
                "",
                "&7Valor total: &e" + valorTotal + " &7creditos",
                "&7Al sistema (-10%): &6" + alSistema,
                "&7Tu saldo: &e" + saldo,
                "",
                hay ? "&aElige una opcion abajo" : "&8Area de deposito vacia"));
        muestra.setItem(SLOT_INFO, info);

        ItemStack bSistema = new ItemStack(Items.GOLD_INGOT);
        MenuUtil.nombre(bSistema, "&6&lVENDER AL SISTEMA");
        MenuUtil.lore(bSistema, List.of(
                "&7Venta instantanea de lo depositado.",
                "&7Recibes el valor &cmenos 10%&7 en tu saldo.",
                "",
                "&7Pago al saldo: &6" + alSistema + " &7creditos",
                "",
                hay ? "&eClic para vender" : "&8Deposita items primero"));
        muestra.setItem(SLOT_SISTEMA, bSistema);

        ItemStack bMercado = new ItemStack(Items.EMERALD);
        MenuUtil.nombre(bMercado, "&a&lPONER EN VENTA");
        MenuUtil.lore(bMercado, List.of(
                "&7Publica cada pila en el mercado global",
                "&7al valor &acompleto&7. Cobras el 100%",
                "&7a tu saldo cuando otro la compre.",
                "",
                "&7Valor publicado: &6" + valorTotal + " &7creditos",
                "",
                hay ? "&eClic para publicar" : "&8Deposita items primero"));
        muestra.setItem(SLOT_MERCADO, bMercado);

        muestra.setItem(SLOT_REGRESAR, MenuUtil.boton(Items.ARROW, "&7&lREGRESAR",
                List.of("&7Regresa al menu principal.",
                        "&7Te devuelve lo que no confirmaste.")));
    }

    @Override
    protected void alClicBoton(int slotId, int button, ClickType type) {
        switch (slotId) {
            case SLOT_SISTEMA -> { venderAlSistema(); alCambiarDeposito(); broadcastChanges(); }
            case SLOT_MERCADO -> { ponerEnVenta(); alCambiarDeposito(); broadcastChanges(); }
            case SLOT_REGRESAR -> { devolverDeposito(); Menus.abrirPrincipal(dueno); }
            default -> { /* nada */ }
        }
    }

    /** Vende TODO lo depositado al sistema: consume primero, suma saldo despues (90%). */
    private void venderAlSistema() {
        int valorTotal = valorTotalDeposito();
        if (valorTotal <= 0) {
            dueno.sendSystemMessage(Component.literal("Deposita items vendibles primero.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int pago = (int) Math.floor(valorTotal * EcoConfig.get().porcentajeVentaSistema);
        if (pago <= 0) {
            dueno.sendSystemMessage(Component.literal("El valor es demasiado bajo para vender.")
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
        BalanceData.get(dueno.server).sumar(dueno, pago); // sumar al saldo despues
        long saldo = BalanceData.get(dueno.server).saldo(dueno);
        dueno.sendSystemMessage(Component.literal("Vendiste " + items + " items al sistema por "
                + pago + " creditos. Saldo: " + saldo).withStyle(ChatFormatting.GREEN));
    }

    /** Publica cada stack depositado al mercado global a su valor completo. */
    private void ponerEnVenta() {
        if (depositoVacio()) {
            dueno.sendSystemMessage(Component.literal("Deposita items primero.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        MarketData market = MarketData.get(dueno.server);
        int publicados = 0;
        int totalCreditos = 0;
        boolean seLleno = false;
        for (int i = 0; i < 45; i++) {
            ItemStack s = muestra.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int valorUnidad = ItemValues.valorDe(s);
            if (valorUnidad <= 0) {
                continue; // no vendible: lo dejamos en el deposito
            }
            if (market.lleno()) {
                seLleno = true;
                break;
            }
            int precio = valorUnidad * s.getCount();
            ItemStack copia = s.copy();
            market.publicar(dueno.getUUID(), dueno.getGameProfile().getName(), copia, precio);
            muestra.setItem(i, ItemStack.EMPTY); // consumir del deposito
            publicados++;
            totalCreditos += precio;
        }
        if (publicados == 0) {
            dueno.sendSystemMessage(Component.literal(seLleno
                            ? "El mercado global esta lleno. Intenta mas tarde."
                            : "No habia items vendibles para publicar.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        dueno.sendSystemMessage(Component.literal("Pusiste en venta " + publicados
                + " pila(s) por un total de " + totalCreditos + " creditos.")
                .withStyle(ChatFormatting.GOLD));
        if (seLleno) {
            dueno.sendSystemMessage(Component.literal("El mercado se lleno; el resto sigue en el deposito.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
