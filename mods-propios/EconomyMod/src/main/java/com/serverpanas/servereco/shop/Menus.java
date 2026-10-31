package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.auction.AuctionMenu;
import com.serverpanas.servereco.auction.AuctionSellMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Apertura centralizada de los cofres virtuales (todos ChestMenu vanilla,
 * GENERIC_9x6). Reutilizado por los comandos y por los botones de los menus,
 * de modo que un boton pueda abrir otra vista server-side.
 */
public final class Menus {

    private Menus() {
    }

    public static void abrirPrincipal(ServerPlayer p) {
        Container cont = MainMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Tienda").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new MainMenu(id, inv, p, cont);
            }
        });
    }

    public static void abrirAgregarCredito(ServerPlayer p) {
        Container cont = AddCreditMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Agregar Credito").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new AddCreditMenu(id, inv, p, cont);
            }
        });
    }

    public static void abrirComprar(ServerPlayer p) {
        Container cont = ShopMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Comprar").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new ShopMenu(id, inv, p, cont);
            }
        });
    }

    public static void abrirVender(ServerPlayer p) {
        Container cont = SellMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Vender").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new SellMenu(id, inv, p, cont);
            }
        });
    }

    public static void abrirSubastar(ServerPlayer p) {
        Container cont = AuctionMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Casa de Subastas").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new AuctionMenu(id, inv, p, cont);
            }
        });
    }

    /** Abre la vista interactiva (por arrastre) para PONER EN SUBASTA. */
    public static void abrirSubastarPublicar(ServerPlayer p) {
        Container cont = AuctionSellMenu.nuevoContenedor();
        p.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.literal("Subastar (arrastra tus items)")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new AuctionSellMenu(id, inv, p, cont);
            }
        });
    }
}
