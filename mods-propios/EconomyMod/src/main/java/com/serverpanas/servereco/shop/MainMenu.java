package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.data.BalanceData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * MENU PRINCIPAL de la tienda (/tienda), 100% server-side (ChestMenu vanilla,
 * GENERIC_9x6, sin MenuType propio).
 *
 * Cuatro botones-item (modulos), cada uno aislado:
 *   - AGREGAR CREDITO (bloque esmeralda) -> carga items como saldo al 100%.
 *   - COMPRAR         (esmeralda)        -> compra con el saldo.
 *   - VENDER          (cofre)            -> vende al sistema (90%) o publica.
 *   - SUBASTAR        (bloque de oro)    -> casa de subastas.
 *
 * Ademas un item-info FIJO con el SALDO de creditos del jugador.
 *
 * Todos los clics se cancelan; solo disparan la apertura del modulo o nada.
 */
public class MainMenu extends ChestMenu {

    private static final int RANURAS = 54;

    // Ranuras centrales para los 4 botones (fila central del cofre 9x6).
    private static final int SLOT_AGREGAR = 19;
    private static final int SLOT_COMPRAR = 21;
    private static final int SLOT_VENDER = 23;
    private static final int SLOT_SUBASTAR = 25;
    private static final int SLOT_PODERES = 31;

    // Item-info fijo con el saldo (fila inferior, centrado).
    private static final int SLOT_SALDO = 49;

    private final Container muestra;
    private final ServerPlayer dueno;

    public MainMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
        super(MenuType.GENERIC_9x6, id, invJugador, muestra, 6);
        this.muestra = muestra;
        this.dueno = dueno;
        ((VirtualMenuContainer) muestra).excludeFromSorting();
        // Todas las ranuras superiores son botones/muestra: las bloqueamos
        // (LockedSlot) para que ni el jugador ni Inventory Sorter puedan sacar
        // un boton ni dejar un item encima.
        MenuUtil.bloquearRanurasSuperiores(this, muestra, RANURAS);
        if (dueno != null) {
            construir();
        }
    }

    public static Container nuevoContenedor() {
        return new VirtualMenuContainer(RANURAS);
    }

    private void construir() {
        for (int i = 0; i < RANURAS; i++) {
            muestra.setItem(i, ItemStack.EMPTY);
        }

        ItemStack agregar = new ItemStack(Items.EMERALD_BLOCK);
        MenuUtil.nombre(agregar, "&b&lAGREGAR CREDITO");
        MenuUtil.lore(agregar, List.of(
                "&7Convierte items en saldo de creditos.",
                "&7Arrastra items y recibe el &a100%&7 de",
                "&7su valor en tu saldo.",
                "",
                "&eClic para abrir"));
        muestra.setItem(SLOT_AGREGAR, agregar);

        ItemStack comprar = new ItemStack(Items.EMERALD);
        MenuUtil.nombre(comprar, "&a&lCOMPRAR");
        MenuUtil.lore(comprar, List.of(
                "&7Compra items de la tienda del server",
                "&7y de lo que otros jugadores pusieron",
                "&7en venta. Pagas con tu &esaldo&7.",
                "",
                "&eClic para abrir"));
        muestra.setItem(SLOT_COMPRAR, comprar);

        ItemStack vender = new ItemStack(Items.CHEST);
        MenuUtil.nombre(vender, "&6&lVENDER");
        MenuUtil.lore(vender, List.of(
                "&7Vende lo que quieras: el juego calcula",
                "&7su valor en creditos.",
                "",
                "&7- Al sistema: pago al saldo (-10%).",
                "&7- Poner en venta: valor completo al",
                "&7  mercado global.",
                "",
                "&eClic para abrir"));
        muestra.setItem(SLOT_VENDER, vender);

        ItemStack subastar = new ItemStack(Items.GOLD_BLOCK);
        MenuUtil.nombre(subastar, "&d&lSUBASTAR");
        MenuUtil.lore(subastar, List.of(
                "&7Mira los items en subasta de todo",
                "&7el servidor y compralos con tu saldo.",
                "",
                "&7Sube los tuyos por arrastre.",
                "",
                "&eClic para abrir"));
        muestra.setItem(SLOT_SUBASTAR, subastar);

        ItemStack poderes = new ItemStack(Items.NETHER_STAR);
        MenuUtil.nombre(poderes, "&5&lPODERES");
        MenuUtil.lore(poderes, List.of(
                "&7Compra ayudas con tu saldo:",
                "&7- Ir con un compañero o traerlo",
                "&7- Volver a tu cama",
                "&7- Curación y efectos",
                "",
                "&eClic para abrir"));
        muestra.setItem(SLOT_PODERES, poderes);

        long saldo = BalanceData.get(dueno.server).saldo(dueno);
        ItemStack saldoIcon = new ItemStack(Items.GOLD_NUGGET);
        MenuUtil.nombre(saldoIcon, "&6&lCreditos: &e" + saldo);
        MenuUtil.lore(saldoIcon, List.of(
                "&7Tu saldo de creditos.",
                "&7Tambien con &f/creditos &7o &f/bal&7.",
                "",
                "&7Agrega credito o vende para ganar mas."));
        muestra.setItem(SLOT_SALDO, saldoIcon);

        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (dueno == null) {
            return;
        }
        // Este menu es SOLO botones: nunca movemos items. Cualquier arrastre
        // (QUICK_CRAFT), shift (QUICK_MOVE), swap/hotbar (SWAP), tirar (THROW),
        // clone, etc. se ignora por completo: no se llama a super.clicked().
        // Solo reaccionamos al clic directo sobre uno de los 4 botones.
        switch (slotId) {
            case SLOT_AGREGAR -> Menus.abrirAgregarCredito(dueno);
            case SLOT_COMPRAR -> Menus.abrirComprar(dueno);
            case SLOT_VENDER -> Menus.abrirVender(dueno);
            case SLOT_SUBASTAR -> Menus.abrirSubastar(dueno);
            case SLOT_PODERES -> Menus.abrirPoderes(dueno, null);
            default -> { /* clic cancelado: ni botones ni items se mueven */ }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
