package com.serverpanas.servereco.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades compartidas para construir iconos de los menus (cofres vanilla).
 * Traduce codigos de color '&' estilo Bukkit a Component con estilo, y aplica
 * nombre/lore a los ItemStack de muestra. Sin estado; solo helpers estaticos.
 */
public final class MenuUtil {

    private MenuUtil() {
    }

    /** Convierte un texto con codigos '&' (p.ej. "&aVender") a Component. */
    public static MutableComponent color(String texto) {
        // Reutiliza el parser legacy de Minecraft (seccion §). Cambiamos '&' por §.
        String conSeccion = texto.replace('&', '\u00a7');
        // Component.literal no interpreta §; usamos el formateo legacy manual.
        return construirLegacy(conSeccion);
    }

    /**
     * Parser legacy minimal: corta la cadena por el caracter de seccion y aplica
     * los formatos acumulados a cada tramo. Soporta colores 0-9 a-f y k-o,r.
     */
    private static MutableComponent construirLegacy(String s) {
        MutableComponent out = Component.empty();
        net.minecraft.network.chat.Style estilo = net.minecraft.network.chat.Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                // volcar lo acumulado con el estilo actual
                if (buf.length() > 0) {
                    out.append(Component.literal(buf.toString()).withStyle(estilo));
                    buf.setLength(0);
                }
                char code = Character.toLowerCase(s.charAt(++i));
                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt == net.minecraft.ChatFormatting.RESET) {
                        estilo = net.minecraft.network.chat.Style.EMPTY;
                    } else if (fmt.isColor()) {
                        // un color reinicia los formatos previos (como en vanilla)
                        estilo = net.minecraft.network.chat.Style.EMPTY.withColor(fmt);
                    } else {
                        estilo = estilo.applyFormat(fmt);
                    }
                }
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            out.append(Component.literal(buf.toString()).withStyle(estilo));
        }
        return out;
    }

    /** Fija el nombre usando codigos '&'. */
    public static void nombre(ItemStack stack, String texto) {
        stack.setHoverName(color(texto));
    }

    /** Aplica lore (lista de lineas con codigos '&'). */
    public static void lore(ItemStack stack, List<String> lineas) {
        List<Component> comps = new ArrayList<>(lineas.size());
        for (String l : lineas) {
            comps.add(color(l));
        }
        aplicarLore(stack, comps);
    }

    /** Aplica lore ya en Components. */
    public static void aplicarLore(ItemStack stack, List<Component> lineas) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        for (Component c : lineas) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        display.put("Lore", lore);
    }

    /**
     * Reemplaza TODAS las ranuras superiores (0..ranuras-1) de un ChestMenu por
     * {@link LockedSlot} bloqueadas (mayPickup=false, mayPlace=false). Se usa en
     * los menus que son 100% botones/muestra (MainMenu, ShopMenu): asi ningun
     * item-boton se puede sacar ni se puede dejar un item encima, ni por clic ni
     * por el reordenado de Inventory Sorter. Conserva posicion (x/y), indice de
     * contenedor e index de red de cada ranura original.
     */
    public static void bloquearRanurasSuperiores(
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            net.minecraft.world.Container contenedor,
            int ranuras) {
        for (int i = 0; i < ranuras; i++) {
            net.minecraft.world.inventory.Slot original = menu.slots.get(i);
            int cont = original.getContainerSlot();
            LockedSlot nuevo = new LockedSlot(contenedor, cont, original.x, original.y);
            nuevo.index = original.index;
            menu.slots.set(i, nuevo);
        }
    }

    /** Crea un item-boton con nombre y lore usando codigos '&'. */
    public static ItemStack boton(net.minecraft.world.item.Item item, String nombre, List<String> lore) {
        ItemStack s = new ItemStack(item);
        nombre(s, nombre);
        if (lore != null && !lore.isEmpty()) {
            lore(s, lore);
        }
        return s;
    }
}
