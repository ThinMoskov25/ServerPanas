package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.data.BalanceData;
import com.serverpanas.servereco.data.DiscoveryData;
import com.serverpanas.servereco.data.MarketData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Modulo COMPRAR (cofre virtual), 100% server-side.
 *
 * CLAVE: extiende ChestMenu con el MenuType VANILLA (GENERIC_9x6), sin
 * registrar un MenuType propio. Asi el cliente vanilla lo abre como un cofre
 * normal y el mod NO hace falta en el cliente.
 *
 * Muestra lo en venta en el servidor: catalogo base (solo lo descubierto) mas
 * los listados del mercado global de otros jugadores. La CANTIDAD a comprar es
 * interactiva por tipo de clic: clic izq = 1, clic der = una pila, shift-clic =
 * lo maximo que alcance el SALDO.
 *
 * El pago se DESCUENTA del SALDO de creditos del comprador (no consume su
 * inventario fisico) y se entrega el item. Si es listado de jugador, el precio
 * se ABONA al SALDO del vendedor.
 *
 * Modulo AISLADO: el unico boton de navegacion es REGRESAR, que vuelve al menu
 * principal. No muestra botones de otros modulos.
 */
public class ShopMenu extends ChestMenu {

    private static final int RANURAS = 54;
    // La fila inferior (45..53) se reserva para navegacion/saldo.
    private static final int FILA_NAV = 45;
    private static final int SLOT_REGRESAR = 45;
    private static final int SLOT_SALDO = 49;

    private final Container muestra;
    private final ServerPlayer dueno;
    // Catalogo base: ranura -> Item (precio fijo del server).
    private final List<Item> itemPorRanura = new ArrayList<>();
    // Listados de jugadores: ranura -> id del listado en MarketData.
    private final List<Long> listadoPorRanura = new ArrayList<>();

    public ShopMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
        super(MenuType.GENERIC_9x6, id, invJugador, muestra, 6);
        this.muestra = muestra;
        this.dueno = dueno;
        ((VirtualMenuContainer) muestra).excludeFromSorting();
        for (int i = 0; i < RANURAS; i++) {
            itemPorRanura.add(null);
            listadoPorRanura.add(null);
        }
        // Todas las ranuras superiores son muestra/boton: las bloqueamos
        // (LockedSlot) para que ni el jugador ni Inventory Sorter saquen una
        // muestra ni dejen un item encima. La compra NO mueve el item de la
        // muestra: entrega una copia y cobra del saldo.
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
        DiscoveryData disc = DiscoveryData.get(dueno.server);
        var descubiertos = disc.descubiertosDe(dueno.getUUID());
        long creditos = BalanceData.get(dueno.server).saldo(dueno);

        for (int i = 0; i < RANURAS; i++) {
            muestra.setItem(i, ItemStack.EMPTY);
            itemPorRanura.set(i, null);
            listadoPorRanura.set(i, null);
        }

        int ranura = 0;

        // (a) Catalogo base del server (codigo + ajustes del admin en caliente):
        //     SOLO los que el jugador ya descubrio.
        for (var entry : ShopCatalog.catalogoEfectivo().entrySet()) {
            Item item = entry.getKey();
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl == null || !descubiertos.contains(rl.toString())) {
                continue; // aun no lo descubrio: no se muestra
            }
            if (ranura >= FILA_NAV) {
                break;
            }
            muestra.setItem(ranura, iconoVenta(item, entry.getValue(), creditos));
            itemPorRanura.set(ranura, item);
            ranura++;
        }

        // (b) Listados de otros jugadores (mercado global): SIEMPRE visibles.
        MarketData market = MarketData.get(dueno.server);
        for (MarketData.Listado l : market.listar()) {
            if (ranura >= FILA_NAV) {
                break;
            }
            muestra.setItem(ranura, iconoListado(l, creditos));
            listadoPorRanura.set(ranura, l.id);
            ranura++;
        }

        if (ranura == 0) {
            // Nada descubierto ni en venta: pista.
            ItemStack pista = new ItemStack(Items.BOOK);
            pista.setHoverName(Component.literal("No hay nada a la venta todavia")
                    .withStyle(ChatFormatting.RED));
            aplicarLore(pista, List.of(
                    Component.literal("Descubre items o espera a que").withStyle(ChatFormatting.GRAY),
                    Component.literal("otros jugadores pongan cosas en venta.").withStyle(ChatFormatting.GRAY)));
            muestra.setItem(22, pista);
        }

        barraNavegacion(creditos);
        broadcastChanges();
    }

    /** Coloca el boton REGRESAR y el info de saldo en la fila inferior. */
    private void barraNavegacion(long creditos) {
        muestra.setItem(SLOT_REGRESAR, MenuUtil.boton(Items.ARROW, "&7&lREGRESAR",
                List.of("&7Regresa al menu principal.")));
        muestra.setItem(SLOT_SALDO, MenuUtil.boton(Items.GOLD_NUGGET, "&6&lCreditos: &e" + creditos,
                List.of("&7Pagas con tu saldo de creditos.")));
    }

    private ItemStack iconoListado(MarketData.Listado l, long creditosJugador) {
        ItemStack s = l.item.copy();
        boolean alcanza = creditosJugador >= l.precio;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Vendedor: " + l.vendedorNombre).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Precio: " + l.precio + " creditos").withStyle(ChatFormatting.GOLD));
        lore.add(Component.literal("Tu saldo: " + creditosJugador).withStyle(
                alcanza ? ChatFormatting.GREEN : ChatFormatting.RED));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Clic para COMPRAR (venta de jugador)")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        aplicarLore(s, lore);
        return s;
    }

    private ItemStack iconoVenta(Item item, int precio, long creditosJugador) {
        ItemStack s = new ItemStack(item);
        boolean alcanza = creditosJugador >= precio;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Precio: " + precio + " creditos")
                .withStyle(ChatFormatting.GOLD));
        lore.add(Component.literal("Tu saldo: " + creditosJugador + " creditos")
                .withStyle(alcanza ? ChatFormatting.GREEN : ChatFormatting.RED));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Clic izq: comprar 1").withStyle(ChatFormatting.AQUA));
        lore.add(Component.literal("Clic der: comprar una pila").withStyle(ChatFormatting.AQUA));
        lore.add(Component.literal("Shift-clic: lo max que alcance tu saldo")
                .withStyle(ChatFormatting.AQUA));
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
            return; // clics en inventario del jugador o fuera de rango: cancelados
        }
        // El arrastre (QUICK_CRAFT) no debe disparar compras ni mover nada.
        if (type == ClickType.QUICK_CRAFT) {
            return;
        }
        // Unico boton de navegacion: REGRESAR al menu principal.
        if (slotId == SLOT_REGRESAR) {
            Menus.abrirPrincipal(dueno);
            return;
        }

        // Listado de jugador: compra directa por su precio (unidad entera del lote).
        Long listadoId = listadoPorRanura.get(slotId);
        if (listadoId != null) {
            comprarListado(listadoId);
            refrescar();
            return;
        }
        // Item del catalogo: la CANTIDAD depende del tipo de clic (interactivo):
        //   clic izq  = 1 unidad
        //   clic der  = una pila (hasta 64, segun stack del item)
        //   shift     = lo maximo que alcance el saldo
        Item item = itemPorRanura.get(slotId);
        if (item != null) {
            int cantidad = cantidadPorClic(item, button, type);
            comprar(item, cantidad);
            refrescar();
        }
        // clic cancelado (no se roban muestras)
    }

    /** Traduce el tipo de clic a la cantidad a comprar del item del catalogo. */
    private int cantidadPorClic(Item item, int button, ClickType type) {
        int precioUnidad = ShopCatalog.precioDe(item);
        if (precioUnidad <= 0) {
            return 0;
        }
        long saldo = BalanceData.get(dueno.server).saldo(dueno);
        long maxPagable = saldo / precioUnidad;
        if (maxPagable <= 0) {
            return 0;
        }
        // Shift (QUICK_MOVE): lo maximo que alcance el saldo.
        if (type == ClickType.QUICK_MOVE) {
            return (int) Math.min(maxPagable, 64L * 36);
        }
        // Clic derecho: una pila completa (hasta el stack del item).
        if (button == 1) {
            int pila = new ItemStack(item).getMaxStackSize();
            return (int) Math.min(pila, maxPagable);
        }
        // Clic izquierdo (o cualquier otro): 1 unidad.
        return (int) Math.min(1, maxPagable);
    }

    /** Compra un listado de jugador: descuenta del saldo y abona al vendedor. */
    private void comprarListado(long listadoId) {
        MarketData market = MarketData.get(dueno.server);
        MarketData.Listado l = market.porId(listadoId);
        if (l == null) {
            dueno.sendSystemMessage(Component.literal("Ese listado ya no existe.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (l.vendedor.equals(dueno.getUUID())) {
            dueno.sendSystemMessage(Component.literal("No puedes comprar tu propio listado.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BalanceData bal = BalanceData.get(dueno.server);
        // 1) Descontar del saldo del comprador (ATOMICO: valida y resta a la vez).
        if (!bal.restar(dueno, l.precio)) {
            dueno.sendSystemMessage(Component.literal("No tienes creditos suficientes ("
                    + l.precio + ").").withStyle(ChatFormatting.RED));
            return;
        }
        // 2) Entregar el item comprado.
        ItemStack comprado = l.item.copy();
        if (!dueno.getInventory().add(comprado)) {
            dueno.drop(comprado, false);
        }
        dueno.inventoryMenu.broadcastChanges();
        // 3) Abonar el 100% al SALDO del vendedor (persistente aunque este offline).
        pagarVendedor(bal, l);
        // 4) Quitar el listado.
        market.quitar(l.id);
        dueno.sendSystemMessage(Component.literal("Compraste " + l.item.getHoverName().getString()
                + " por " + l.precio + " creditos.").withStyle(ChatFormatting.GREEN));
    }

    /** Abona el precio al SALDO del vendedor; avisa si esta online. */
    private void pagarVendedor(BalanceData bal, MarketData.Listado l) {
        bal.sumar(l.vendedor, l.precio);
        ServerPlayer vendedor = dueno.server.getPlayerList().getPlayer(l.vendedor);
        if (vendedor != null) {
            vendedor.sendSystemMessage(Component.literal("Vendiste " + l.item.getHoverName().getString()
                    + " por " + l.precio + " creditos (abonado a tu saldo).")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private void comprar(Item item, int cantidad) {
        int precioUnidad = ShopCatalog.precioDe(item);
        if (precioUnidad <= 0) {
            return;
        }
        if (cantidad <= 0) {
            dueno.sendSystemMessage(Component.literal("No tienes creditos suficientes.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BalanceData bal = BalanceData.get(dueno.server);
        long saldo = bal.saldo(dueno);
        // Validar saldo para la cantidad EXACTA elegida (reajusta por seguridad).
        long maxPagable = saldo / precioUnidad;
        int comprar = (int) Math.min(cantidad, maxPagable);
        if (comprar <= 0) {
            dueno.sendSystemMessage(Component.literal("No tienes creditos suficientes.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        long costo = (long) comprar * precioUnidad;
        // ATOMICO: descontar del saldo antes de entregar.
        if (!bal.restar(dueno, costo)) {
            dueno.sendSystemMessage(Component.literal("No se pudo cobrar el pago.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        entregarPila(item, comprar);
        dueno.sendSystemMessage(Component.literal("Compraste " + comprar + " x "
                + item.getDescription().getString() + " por " + costo + " creditos.")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Entrega N unidades del item al inventario del jugador (o al suelo). */
    private void entregarPila(Item item, int cantidad) {
        while (cantidad > 0) {
            int lote = Math.min(cantidad, item.getMaxStackSize(new ItemStack(item)));
            ItemStack pila = new ItemStack(item, lote);
            if (!dueno.getInventory().add(pila)) {
                dueno.drop(pila, false);
            }
            cantidad -= lote;
        }
        dueno.inventoryMenu.broadcastChanges();
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
