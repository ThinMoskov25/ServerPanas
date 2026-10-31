package com.serverpanas.servereco.shop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base de los cofres INTERACTIVOS, 100% server-side (ChestMenu vanilla,
 * GENERIC_9x6, SIN MenuType propio). Divide las 54 ranuras superiores en:
 *
 *   - RANURAS DE DEPOSITO: el jugador puede meter y sacar items libremente,
 *     igual que un cofre normal (arrastrar, clic derecho para partir pila,
 *     shift-clic). Son Slot NORMALES (mayPickup=true, mayPlace=true) respaldadas
 *     por el Container temporal del menu.
 *   - RANURAS DE BOTON/INFO: solo-muestra. Son Slot bloqueados
 *     (mayPickup=false, mayPlace=false): el arrastre/shift-clic vanilla las
 *     ignora y el clic directo dispara la accion del boton (no mueve items).
 *
 * NO se sobreescribe clicked() para cancelar TODO: la interaccion vanilla pasa
 * en las ranuras de deposito y en el inventario del jugador; solo se intercepta
 * el clic en ranuras-boton. El drag vanilla respeta mayPlace(), asi que los
 * items nunca caen en los botones.
 *
 * CRITICO (no perder items): al cerrar el menu (removed) o al pulsar VOLVER se
 * devuelve TODO lo depositado al inventario del jugador, soltandolo al suelo si
 * no cabe. El Container de deposito es temporal del menu: lo que esta ahi es
 * del jugador hasta que una accion lo consuma/publique.
 */
public abstract class DepositMenu extends ChestMenu {

    protected static final int RANURAS = 54;

    protected final Container muestra;
    protected final ServerPlayer dueno;

    /** true si la ranura superior i es de deposito; false si es boton/info. */
    private final boolean[] esDeposito = new boolean[RANURAS];

    /** Evita devolver dos veces el deposito (al navegar + al cerrar). */
    private boolean depositoDevuelto = false;

    protected DepositMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
        super(MenuType.GENERIC_9x6, id, invJugador, muestra, 6);
        this.muestra = muestra;
        this.dueno = dueno;
        ((VirtualMenuContainer) muestra).excludeFromSorting();
        // El hijo define que ranuras son de deposito ANTES de reconstruir slots.
        marcarDeposito(esDeposito);
        reconstruirRanurasSuperiores();
    }

    /**
     * El hijo marca en el arreglo que ranuras superiores (0..53) son de deposito
     * (true). El resto queda como boton/info (solo-muestra).
     */
    protected abstract void marcarDeposito(boolean[] deposito);

    /** Maneja el clic en una ranura-boton (0..53). No mueve items. */
    protected abstract void alClicBoton(int slotId, int button, ClickType type);

    /**
     * Se llama tras cualquier cambio en el deposito (meter/sacar items) para
     * actualizar en vivo los iconos de info (valor total, etc.). El hijo debe
     * escribir SOLO en ranuras de boton/info (nunca en ranuras de deposito).
     */
    protected abstract void alCambiarDeposito();

    /** Reemplaza los Slot superiores por slots de deposito o bloqueados. */
    private void reconstruirRanurasSuperiores() {
        for (int i = 0; i < RANURAS; i++) {
            Slot original = this.slots.get(i);
            int cont = original.getContainerSlot();
            int x = original.x;
            int y = original.y;
            Slot nuevo;
            if (esDeposito[i]) {
                nuevo = new Slot(muestra, cont, x, y); // normal: mete/saca libre
            } else {
                // Bloqueado: solo-muestra. Usa la clase CON NOMBRE LockedSlot
                // (mayPickup=false, mayPlace=false) para que Inventory Sorter la
                // respete y, ademas, quede excluida por slotBlacklist.
                nuevo = new LockedSlot(muestra, cont, x, y);
            }
            nuevo.index = original.index;
            this.slots.set(i, nuevo);
        }
    }

    protected boolean esRanuraDeposito(int slotId) {
        return slotId >= 0 && slotId < RANURAS && esDeposito[slotId];
    }

    /** Suma el valor (ItemValues) de todo lo depositado. */
    protected int valorTotalDeposito() {
        int total = 0;
        for (int i = 0; i < RANURAS; i++) {
            if (!esDeposito[i]) {
                continue;
            }
            ItemStack s = muestra.getItem(i);
            if (!s.isEmpty()) {
                total += com.serverpanas.servereco.economy.ItemValues.valorDe(s) * s.getCount();
            }
        }
        return total;
    }

    /** true si el deposito no tiene ningun item. */
    protected boolean depositoVacio() {
        for (int i = 0; i < RANURAS; i++) {
            if (esDeposito[i] && !muestra.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Vacia el icono de info/boton SIN tocar las ranuras de deposito. */
    protected void limpiarBotones() {
        for (int i = 0; i < RANURAS; i++) {
            if (!esDeposito[i]) {
                muestra.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Devuelve TODO lo depositado al inventario del jugador (o al suelo si no
     * cabe) y vacia las ranuras de deposito. Idempotente.
     */
    protected void devolverDeposito() {
        if (depositoDevuelto || dueno == null) {
            return;
        }
        depositoDevuelto = true;
        for (int i = 0; i < RANURAS; i++) {
            if (!esDeposito[i]) {
                continue;
            }
            ItemStack s = muestra.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            muestra.setItem(i, ItemStack.EMPTY);
            if (!dueno.getInventory().add(s)) {
                dueno.drop(s, false);
            }
        }
        dueno.inventoryMenu.broadcastChanges();
    }

    /**
     * Marca el deposito como ya devuelto, para cuando una navegacion lo
     * devolvio manualmente y no queremos que removed() lo repita.
     */
    protected void marcarDepositoDevuelto() {
        depositoDevuelto = true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (dueno == null) {
            return;
        }
        // Clic directo en una ranura-boton (solo-muestra): disparar accion.
        // (Los arrastres usan slotId negativo y no entran aqui.)
        if (type != ClickType.QUICK_CRAFT && slotId >= 0 && slotId < RANURAS && !esDeposito[slotId]) {
            alClicBoton(slotId, button, type);
            return;
        }
        // Resto: interaccion vanilla (deposito, inventario del jugador, drag).
        super.clicked(slotId, button, type, player);
        // Tras mover items, recomputar iconos de info en vivo.
        alCambiarDeposito();
        broadcastChanges();
    }

    /**
     * Shift-clic: del inventario del jugador -> al deposito; de una ranura de
     * deposito -> al inventario del jugador. Nunca mueve hacia ranuras-boton.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copiaRetorno = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack enSlot = slot.getItem();
        copiaRetorno = enSlot.copy();

        int invIni = RANURAS;            // primera ranura del inventario del jugador
        int invFin = this.slots.size();  // fin exclusivo

        if (index < RANURAS) {
            // Viene de una ranura superior. Solo movemos si es de deposito.
            if (!esRanuraDeposito(index)) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(enSlot, invIni, invFin, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Viene del inventario del jugador: meter SOLO en ranuras de deposito.
            if (!moverAlDeposito(enSlot)) {
                return ItemStack.EMPTY;
            }
        }

        if (enSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        alCambiarDeposito();
        broadcastChanges();
        return copiaRetorno;
    }

    /** Mueve stack hacia las ranuras de deposito (respetando apilado). */
    private boolean moverAlDeposito(ItemStack stack) {
        boolean movio = false;
        // 1) Apilar sobre ranuras de deposito con el mismo item.
        for (int i = 0; i < RANURAS && !stack.isEmpty(); i++) {
            if (!esDeposito[i]) {
                continue;
            }
            Slot s = this.slots.get(i);
            ItemStack destino = s.getItem();
            if (!destino.isEmpty() && ItemStack.isSameItemSameTags(destino, stack)) {
                int max = Math.min(destino.getMaxStackSize(), s.getMaxStackSize());
                int suma = destino.getCount() + stack.getCount();
                if (suma <= max) {
                    stack.setCount(0);
                    destino.setCount(suma);
                    s.setChanged();
                    movio = true;
                } else if (destino.getCount() < max) {
                    stack.shrink(max - destino.getCount());
                    destino.setCount(max);
                    s.setChanged();
                    movio = true;
                }
            }
        }
        // 2) Colocar en ranuras de deposito vacias.
        for (int i = 0; i < RANURAS && !stack.isEmpty(); i++) {
            if (!esDeposito[i]) {
                continue;
            }
            Slot s = this.slots.get(i);
            if (s.getItem().isEmpty()) {
                int max = Math.min(stack.getMaxStackSize(), s.getMaxStackSize());
                ItemStack puesto = stack.copy();
                int n = Math.min(stack.getCount(), max);
                puesto.setCount(n);
                s.set(puesto);
                stack.shrink(n);
                movio = true;
            }
        }
        return movio;
    }

    @Override
    public void removed(Player player) {
        // Nunca perder items: devolver lo depositado antes de cerrar.
        devolverDeposito();
        super.removed(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
