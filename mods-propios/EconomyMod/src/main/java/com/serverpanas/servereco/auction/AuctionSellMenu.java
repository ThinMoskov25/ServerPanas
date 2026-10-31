package com.serverpanas.servereco.auction;

import com.serverpanas.servereco.economy.ItemValues;
import com.serverpanas.servereco.shop.DepositMenu;
import com.serverpanas.servereco.shop.MenuUtil;
import com.serverpanas.servereco.shop.Menus;
import com.serverpanas.servereco.shop.VirtualMenuContainer;
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
 * Vista PONER EN SUBASTA (interactiva por arrastre), 100% server-side
 * (ChestMenu vanilla GENERIC_9x6 via DepositMenu).
 *
 * El jugador ARRASTRA al area de deposito (filas 0..44) el/los item(s) que
 * quiere subastar, en las cantidades que quiera (parte pilas con clic derecho).
 * El menu calcula EN VIVO el valor estimado (ItemValues).
 *
 * PRECIO SIN TECLADO (decision documentada): como el cofre vanilla no permite
 * escribir numeros, el precio sugerido es el VALOR CALCULADO por ItemValues.
 * Se ofrecen tres botones de publicacion que aplican un MULTIPLICADOR al valor
 * de cada pila:
 *   - PUBLICAR x1   : precio = valor justo estimado.
 *   - PUBLICAR x1.5 : precio = valor estimado con +50% de margen.
 *   - PUBLICAR x2   : precio = el doble del valor estimado.
 * Cada pila depositada se publica como un LOTE independiente con su propio
 * precio. Comandos de siempre (&f/ah sell <precio>&r) siguen disponibles para
 * precio exacto a mano.
 *
 * Nunca se pierden items: VOLVER y el cierre del menu devuelven lo no publicado.
 */
public class AuctionSellMenu extends DepositMenu {

    private static final int SLOT_INFO = 49;
    private static final int SLOT_X1 = 47;
    private static final int SLOT_X15 = 50;
    private static final int SLOT_X2 = 51;
    private static final int SLOT_VOLVER = 45;

    public AuctionSellMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
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

        int valor = valorTotalDeposito();
        boolean hay = !depositoVacio();

        ItemStack info = new ItemStack(Items.NETHER_STAR);
        MenuUtil.nombre(info, "&d&lPONER EN SUBASTA");
        MenuUtil.lore(info, List.of(
                "&7Arrastra aqui los items a subastar",
                "&7(parte pilas con clic derecho).",
                "",
                "&7Valor estimado: &e" + valor + " &7creditos",
                "&7Cada pila se publica como un lote.",
                "",
                hay ? "&aElige un precio abajo" : "&8Area de deposito vacia"));
        muestra.setItem(SLOT_INFO, info);

        muestra.setItem(SLOT_X1, MenuUtil.boton(Items.EMERALD, "&a&lPUBLICAR x1",
                List.of("&7Precio = valor justo estimado.",
                        "&7Total: &6" + valor + " &7creditos",
                        "",
                        hay ? "&eClic para publicar" : "&8Deposita items primero")));
        muestra.setItem(SLOT_X15, MenuUtil.boton(Items.GOLD_INGOT, "&6&lPUBLICAR x1.5",
                List.of("&7Precio = valor estimado +50%.",
                        "&7Total: &6" + (int) Math.floor(valor * 1.5) + " &7creditos",
                        "",
                        hay ? "&eClic para publicar" : "&8Deposita items primero")));
        muestra.setItem(SLOT_X2, MenuUtil.boton(Items.DIAMOND, "&b&lPUBLICAR x2",
                List.of("&7Precio = el doble del valor estimado.",
                        "&7Total: &6" + (valor * 2) + " &7creditos",
                        "",
                        hay ? "&eClic para publicar" : "&8Deposita items primero")));

        muestra.setItem(SLOT_VOLVER, MenuUtil.boton(Items.ARROW, "&7&lVOLVER",
                List.of("&7Regresa a la casa de subastas.",
                        "&7Te devuelve lo que no publicaste.")));
    }

    @Override
    protected void alClicBoton(int slotId, int button, ClickType type) {
        switch (slotId) {
            case SLOT_X1 -> { publicar(1.0); alCambiarDeposito(); broadcastChanges(); }
            case SLOT_X15 -> { publicar(1.5); alCambiarDeposito(); broadcastChanges(); }
            case SLOT_X2 -> { publicar(2.0); alCambiarDeposito(); broadcastChanges(); }
            case SLOT_VOLVER -> { devolverDeposito(); Menus.abrirSubastar(dueno); }
            default -> { /* nada */ }
        }
    }

    /** Publica cada pila depositada como un lote con precio = valor*mult. */
    private void publicar(double mult) {
        if (depositoVacio()) {
            dueno.sendSystemMessage(Component.literal("Deposita items primero.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        AuctionData ah = AuctionData.get(dueno.server);
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
                continue; // sin valor: se queda en el deposito
            }
            if (ah.lleno()) {
                seLleno = true;
                break;
            }
            int precio = Math.max(1, (int) Math.floor(valorUnidad * s.getCount() * mult));
            ItemStack copia = s.copy();
            ah.publicar(dueno.getUUID(), dueno.getGameProfile().getName(), copia, precio);
            muestra.setItem(i, ItemStack.EMPTY); // consumir del deposito
            publicados++;
            totalCreditos += precio;
        }
        if (publicados == 0) {
            dueno.sendSystemMessage(Component.literal(seLleno
                            ? "La casa de subastas esta llena. Intenta mas tarde."
                            : "No habia items con valor para subastar.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        dueno.sendSystemMessage(Component.literal("Subaste " + publicados
                + " lote(s) por un total de " + totalCreditos + " creditos.")
                .withStyle(ChatFormatting.GOLD));
        if (seLleno) {
            dueno.sendSystemMessage(Component.literal("La casa se lleno; el resto sigue en el deposito.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
