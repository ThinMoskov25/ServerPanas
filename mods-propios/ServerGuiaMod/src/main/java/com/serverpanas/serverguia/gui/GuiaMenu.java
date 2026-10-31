package com.serverpanas.serverguia.gui;

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
 * Cofre virtual de la GUIA, 100% server-side.
 *
 * CLAVE: extiende ChestMenu con el MenuType VANILLA (GENERIC_9x6), sin
 * registrar un MenuType propio. El cliente vanilla lo abre como un cofre
 * normal y el mod NO hace falta en el cliente.
 *
 * Dos vistas sobre el MISMO contenedor:
 *   - LISTA (nivel 1): un icono por cada mod del pack.
 *   - DETALLE (nivel 2): los pasos del mod seleccionado + boton VOLVER.
 *
 * La navegacion reconstruye el contenido del Container y llama a
 * broadcastChanges(). Todos los clics se CANCELAN (no se roban las muestras).
 */
public class GuiaMenu extends ChestMenu {

    private static final int RANURAS = 54;
    // Ranura fija del boton VOLVER en la vista detalle (esquina inferior izq).
    private static final int SLOT_VOLVER = 45;

    private final Container muestra;
    private final ServerPlayer dueno;

    // Estado de navegacion: -1 = vista lista; >=0 = indice del mod en detalle.
    private int modDetalle = -1;
    // Mapa ranura -> indice de mod (solo valido en vista lista).
    private final int[] modPorRanura = new int[RANURAS];

    public GuiaMenu(int id, Inventory invJugador, ServerPlayer dueno, Container muestra) {
        super(MenuType.GENERIC_9x6, id, invJugador, muestra, 6);
        this.muestra = muestra;
        this.dueno = dueno;
        if (dueno != null) {
            mostrarLista();
        }
    }

    public static Container nuevoContenedor() {
        return new SimpleContainer(RANURAS);
    }

    private void limpiar() {
        for (int i = 0; i < RANURAS; i++) {
            muestra.setItem(i, ItemStack.EMPTY);
            modPorRanura[i] = -1;
        }
    }

    // --------------------------- VISTA LISTA ---------------------------

    public void mostrarLista() {
        if (dueno == null) {
            return;
        }
        modDetalle = -1;
        limpiar();

        // Cabecera informativa en la ranura 4 (centro superior).
        ItemStack cabecera = new ItemStack(Items.KNOWLEDGE_BOOK);
        MenuUtil.nombre(cabecera, "&b&lGUIA DEL SERVIDOR");
        MenuUtil.lore(cabecera, List.of(
                "&7Haz clic en un mod para ver su",
                "&7guia paso a paso.",
                "",
                "&7Abrela con &f/guia &7cuando quieras."));
        muestra.setItem(4, cabecera);

        List<GuiaData.Mod> mods = GuiaData.MODS;
        // Rellenamos desde la ranura 9 (segunda fila) hacia abajo, dejando la
        // primera fila para la cabecera. Hasta 45 ranuras para mods.
        int ranura = 9;
        for (int i = 0; i < mods.size() && ranura < RANURAS; i++, ranura++) {
            GuiaData.Mod m = mods.get(i);
            ItemStack icono = new ItemStack(m.icono);
            MenuUtil.nombre(icono, m.nombre);
            List<String> lore = new ArrayList<>();
            lore.add(m.descripcion);
            lore.add("");
            lore.add("&eClic para ver la guia");
            MenuUtil.lore(icono, lore);
            muestra.setItem(ranura, icono);
            modPorRanura[ranura] = i;
        }

        broadcastChanges();
    }

    // -------------------------- VISTA DETALLE --------------------------

    public void mostrarDetalle(int indiceMod) {
        if (dueno == null || indiceMod < 0 || indiceMod >= GuiaData.MODS.size()) {
            return;
        }
        modDetalle = indiceMod;
        limpiar();

        GuiaData.Mod m = GuiaData.MODS.get(indiceMod);

        // Titulo del mod en la ranura 4.
        ItemStack titulo = new ItemStack(m.icono);
        MenuUtil.nombre(titulo, m.nombre);
        MenuUtil.lore(titulo, List.of(
                m.descripcion,
                "",
                "&7Pasos de progresion / uso:"));
        muestra.setItem(4, titulo);

        // Pasos: fila(s) a partir de la ranura 9. Cada paso con su lore.
        int ranura = 9;
        for (GuiaData.Paso p : m.pasos) {
            if (ranura >= SLOT_VOLVER) {
                break; // reservamos la ultima fila para el boton VOLVER
            }
            ItemStack icono = new ItemStack(p.icono);
            MenuUtil.nombre(icono, p.nombre);
            MenuUtil.lore(icono, p.lore);
            muestra.setItem(ranura, icono);
            ranura++;
        }

        // Boton VOLVER en ranura fija (esquina inferior izquierda).
        ItemStack volver = new ItemStack(Items.ARROW);
        MenuUtil.nombre(volver, "&c&lVOLVER");
        MenuUtil.lore(volver, List.of(
                "&7Regresa a la lista de mods."));
        muestra.setItem(SLOT_VOLVER, volver);

        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (dueno == null) {
            return; // clic cancelado
        }
        if (slotId < 0 || slotId >= RANURAS) {
            return; // clics en inventario del jugador: cancelados
        }

        if (modDetalle >= 0) {
            // Estamos en DETALLE: solo responde el boton VOLVER.
            if (slotId == SLOT_VOLVER) {
                mostrarLista();
            }
            return; // los pasos no hacen nada (su guia ya esta en el lore)
        }

        // Estamos en LISTA: clic sobre un mod abre su detalle.
        int indiceMod = modPorRanura[slotId];
        if (indiceMod >= 0) {
            mostrarDetalle(indiceMod);
        }
        // cualquier otro clic queda cancelado
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
