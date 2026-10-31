package com.serverpanas.serverguia.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Apertura centralizada del cofre virtual de la guia (ChestMenu vanilla,
 * GENERIC_9x6). Reutilizado por el comando /guia y por el item-atajo.
 */
public final class Menus {

    private Menus() {
    }

    public static void abrirGuia(ServerPlayer p) {
        Container cont = GuiaMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Guia del Servidor")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new GuiaMenu(id, inv, p, cont);
            }
        });
    }
}
