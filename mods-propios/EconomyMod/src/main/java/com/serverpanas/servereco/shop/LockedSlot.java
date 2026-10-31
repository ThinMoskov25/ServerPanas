package com.serverpanas.servereco.shop;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ranura BLOQUEADA de solo-muestra para los cofres virtuales de la tienda
 * (botones, iconos de info, muestras). Es una clase CON NOMBRE (no anonima) a
 * proposito: su nombre completo de clase es el identificador estable que se usa
 * para la DOBLE proteccion anti-Inventory Sorter.
 *
 * ===========================================================================
 *  POR QUE EXISTE (bug reportado)
 * ===========================================================================
 * Los menus de /tienda son ChestMenu vanilla (GENERIC_9x6). Las ranuras de
 * boton/muestra ya cancelan los clics, PERO el mod Inventory Sorter
 * (modid "inventorysorter") reordena el CONTENEDOR por fuera del flujo normal
 * de clics: mueve ItemStacks directamente entre las ranuras del menu. Si esas
 * ranuras fueran Slot normales, el sorter sacaria el item-boton al inventario
 * del jugador y lo convertiria en un item real (que luego se puede vender).
 *
 * ===========================================================================
 *  COMO PROTEGE (ver tambien el codigo fuente de Inventory Sorter)
 * ===========================================================================
 * El Inventory Sorter 23.0.8 ordena desde el servidor y llama directamente a
 * {@code Slot.set(ItemStack.EMPTY)} para vaciar ranuras, sin usar el flujo
 * normal de clics. Por eso {@code mayPickup}/{@code mayPlace} y {@code
 * slotBlacklist} no bastan como unica proteccion: esta ranura tambien ignora
 * escrituras hechas a traves de {@code Slot.set}. Los menus actualizan los
 * iconos escribiendo directamente en el Container, por lo que siguen pudiendo
 * refrescar su contenido visual normalmente.
 *
 * La lista {@code slotBlacklist} de inventorysorter-server.toml sigue siendo
 * una defensa adicional, sin afectar a los cofres vanilla normales.
 */
public class LockedSlot extends Slot {

    public LockedSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    /** Nunca se puede SACAR el item de esta ranura (ni por sorter ni por clic). */
    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    /** Nunca se puede DEJAR un item en esta ranura (ni por sorter ni por drag). */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /** Impide que Inventory Sorter vacie o reordene directamente esta ranura. */
    @Override
    public void set(ItemStack stack) {
    }
}
