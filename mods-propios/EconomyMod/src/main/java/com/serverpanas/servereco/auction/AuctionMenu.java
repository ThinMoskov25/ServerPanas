package com.serverpanas.servereco.auction;

import com.serverpanas.servereco.data.BalanceData;
import com.serverpanas.servereco.shop.MenuUtil;
import com.serverpanas.servereco.shop.Menus;
import com.serverpanas.servereco.shop.VirtualMenuContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Cofre virtual de la CASA DE SUBASTAS (vista VER/COMPRAR), 100% server-side
 * (ChestMenu vanilla GENERIC_9x6, sin MenuType propio).
 *
 * Muestra los lotes publicos; al pulsar uno, el comprador paga desde su SALDO
 * de creditos, recibe el item y el precio se ABONA al SALDO del vendedor
 * (persistente aunque este offline). Los clics en los lotes/botones se cancelan
 * (no se roban las muestras). El boton PONER EN SUBASTA abre la vista
 * interactiva de publicacion por arrastre (AuctionSellMenu).
 *
 * Modulo AISLADO: el unico boton de navegacion hacia otro menu es REGRESAR, que
 * vuelve al menu principal. PONER EN SUBASTA es parte del propio modulo de
 * subastas (no es otro modulo).
 */
public class AuctionMenu extends ChestMenu {

    private static final int RANURAS = 54;
    private static final int FILA_NAV = 45;
    private static final int SLOT_REGRESAR = 45;
    private static final int SLOT_SALDO = 49;
    private static final int SLOT_PUBLICAR = 53;

    private final Container muestra;
    private final ServerPlayer dueno;
    private final List<Long> lotePorRanura = new ArrayList<>();

    public AuctionMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
        super(MenuType.GENERIC_9x6, id, invJugador, muestra, 6);
        this.muestra = muestra;
        this.dueno = dueno;
        ((VirtualMenuContainer) muestra).excludeFromSorting();
        for (int i = 0; i < RANURAS; i++) {
            lotePorRanura.add(null);
        }
        // Todas las ranuras superiores son muestra/boton: bloqueadas (LockedSlot)
        // contra extraccion por clic o por Inventory Sorter.
        MenuUtil.bloquearRanurasSuperiores(this, muestra, RANURAS);
        if (dueno != null) {
            refrescar();
        }
    }

    public static Container nuevoContenedor() {
        return new VirtualMenuContainer(RANURAS);
    }

    public void refrescar() {
        if (dueno == null) {
            return;
        }
        AuctionData ah = AuctionData.get(dueno.server);
        long creditos = BalanceData.get(dueno.server).saldo(dueno);

        for (int i = 0; i < RANURAS; i++) {
            muestra.setItem(i, ItemStack.EMPTY);
            lotePorRanura.set(i, null);
        }

        List<AuctionData.Lote> lotes = ah.listar();
        for (int i = 0; i < lotes.size() && i < FILA_NAV; i++) {
            AuctionData.Lote l = lotes.get(i);
            muestra.setItem(i, iconoLote(l, creditos));
            lotePorRanura.set(i, l.id);
        }
        if (lotes.isEmpty()) {
            ItemStack vacio = new ItemStack(Items.BARRIER);
            vacio.setHoverName(Component.literal("No hay subastas activas")
                    .withStyle(ChatFormatting.RED));
            aplicarLore(vacio, List.of(Component.literal("Usa PONER EN SUBASTA para subir tus items.")
                    .withStyle(ChatFormatting.GRAY)));
            muestra.setItem(22, vacio);
        }
        barraNavegacion(creditos);
        broadcastChanges();
    }

    private void barraNavegacion(long creditos) {
        muestra.setItem(SLOT_REGRESAR, MenuUtil.boton(Items.ARROW, "&7&lREGRESAR",
                List.of("&7Regresa al menu principal.")));
        muestra.setItem(SLOT_SALDO, MenuUtil.boton(Items.GOLD_NUGGET, "&6&lCreditos: &e" + creditos,
                List.of("&7Pagas con tu saldo de creditos.")));
        muestra.setItem(SLOT_PUBLICAR, MenuUtil.boton(Items.NETHER_STAR, "&d&lPONER EN SUBASTA",
                List.of(
                        "&7Abre el area de subasta por arrastre:",
                        "&7mete tus items y elige el precio.",
                        "",
                        "&eClic para abrir")));
    }

    private ItemStack iconoLote(AuctionData.Lote l, long creditosJugador) {
        ItemStack s = l.item.copy();
        boolean alcanza = creditosJugador >= l.precio;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Vendedor: " + l.vendedorNombre).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Precio: " + l.precio + " creditos").withStyle(ChatFormatting.GOLD));
        lore.add(Component.literal("Tu saldo: " + creditosJugador).withStyle(
                alcanza ? ChatFormatting.GREEN : ChatFormatting.RED));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Clic para COMPRAR").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        aplicarLore(s, lore);
        return s;
    }

    static void aplicarLore(ItemStack stack, List<Component> lineas) {
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        for (Component c : lineas) {
            lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        display.put("Lore", lore);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (dueno == null || slotId < 0 || slotId >= RANURAS) {
            return; // clic cancelado
        }
        if (type == ClickType.QUICK_CRAFT) {
            return; // el arrastre nunca mueve muestras ni dispara compras
        }
        switch (slotId) {
            case SLOT_REGRESAR -> { Menus.abrirPrincipal(dueno); return; }
            case SLOT_PUBLICAR -> { Menus.abrirSubastarPublicar(dueno); return; }
            default -> { /* sigue */ }
        }
        Long loteId = lotePorRanura.get(slotId);
        if (loteId != null) {
            comprar(loteId);
            refrescar();
        }
        // clic cancelado
    }

    private void comprar(long loteId) {
        AuctionData ah = AuctionData.get(dueno.server);
        AuctionData.Lote lote = ah.porId(loteId);
        if (lote == null) {
            dueno.sendSystemMessage(Component.literal("Esa subasta ya no existe.").withStyle(ChatFormatting.RED));
            return;
        }
        if (lote.vendedor.equals(dueno.getUUID())) {
            dueno.sendSystemMessage(Component.literal("No puedes comprar tu propia subasta. Usa /ah cancel.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BalanceData bal = BalanceData.get(dueno.server);
        // 1) Descontar del SALDO del comprador (ATOMICO: valida y resta a la vez).
        if (!bal.restar(dueno, lote.precio)) {
            dueno.sendSystemMessage(Component.literal("No tienes creditos suficientes ("
                    + lote.precio + ").").withStyle(ChatFormatting.RED));
            return;
        }
        // 2) Entregar el item comprado.
        ItemStack comprado = lote.item.copy();
        if (!dueno.getInventory().add(comprado)) {
            dueno.drop(comprado, false);
        }
        dueno.inventoryMenu.broadcastChanges();
        // 3) Abonar el precio al SALDO del vendedor (persistente aunque este offline).
        pagarVendedor(bal, lote);
        // 4) Quitar el lote.
        ah.quitar(lote.id);
        dueno.sendSystemMessage(Component.literal("Compraste " + comprado.getHoverName().getString()
                + " por " + lote.precio + " creditos.").withStyle(ChatFormatting.GREEN));
    }

    private void pagarVendedor(BalanceData bal, AuctionData.Lote lote) {
        bal.sumar(lote.vendedor, lote.precio);
        ServerPlayer vendedor = dueno.server.getPlayerList().getPlayer(lote.vendedor);
        if (vendedor != null) {
            vendedor.sendSystemMessage(Component.literal("Vendiste " + lote.item.getHoverName().getString()
                    + " por " + lote.precio + " creditos (abonado a tu saldo).")
                    .withStyle(ChatFormatting.GOLD));
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
