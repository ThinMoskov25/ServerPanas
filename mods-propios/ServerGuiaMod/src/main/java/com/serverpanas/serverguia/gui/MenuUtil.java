package com.serverpanas.serverguia.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades para construir iconos de los menus (cofres vanilla). Traduce
 * codigos de color '&' estilo Bukkit a Component con estilo, y aplica
 * nombre/lore a los ItemStack de muestra. Sin estado; solo helpers estaticos.
 */
public final class MenuUtil {

    private MenuUtil() {
    }

    /** Convierte un texto con codigos '&' (p.ej. "&aGuia") a Component. */
    public static MutableComponent color(String texto) {
        String conSeccion = texto.replace('&', '\u00a7');
        return construirLegacy(conSeccion);
    }

    private static MutableComponent construirLegacy(String s) {
        MutableComponent out = Component.empty();
        net.minecraft.network.chat.Style estilo = net.minecraft.network.chat.Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
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

    /** Fija el nombre usando codigos '&'. Sin cursiva por defecto. */
    public static void nombre(ItemStack stack, String texto) {
        MutableComponent c = color(texto).withStyle(s -> s.withItalic(false));
        stack.setHoverName(c);
    }

    /** Aplica lore (lista de lineas con codigos '&'). */
    public static void lore(ItemStack stack, List<String> lineas) {
        List<Component> comps = new ArrayList<>(lineas.size());
        for (String l : lineas) {
            comps.add(color(l).withStyle(s -> s.withItalic(false)));
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
}
