package com.serverpanas.serverguia;

import com.mojang.brigadier.CommandDispatcher;
import com.serverpanas.serverguia.gui.Menus;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Comando /guia + item-atajo (clic derecho).
 *
 * /guia                 abre el cofre de la guia (forma garantizada).
 * item-atajo            un knowledge_book renombrado "Guia del Servidor":
 *                       al hacer clic derecho abre el mismo cofre. El comando
 *                       /darguia lo entrega (OP), pero /guia es el minimo.
 */
public class GuiaEvents {

    private static final String NOMBRE_ITEM = "Guia del Servidor";

    @SubscribeEvent
    public void registrarComandos(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // Forma garantizada de abrir la guia.
        d.register(Commands.literal("guia").executes(ctx -> abrir(ctx.getSource())));

        // Atajo opcional: entrega el item-guia (solo OP).
        d.register(Commands.literal("darguia")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> darItem(ctx.getSource())));
    }

    private static ServerPlayer jugador(CommandSourceStack src) {
        try {
            return src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Solo un jugador puede usar esto."));
            return null;
        }
    }

    private static int abrir(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        Menus.abrirGuia(p);
        return 1;
    }

    private static int darItem(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        ItemStack guia = new ItemStack(Items.KNOWLEDGE_BOOK);
        guia.setHoverName(Component.literal(NOMBRE_ITEM)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .withStyle(s -> s.withItalic(false)));
        if (!p.getInventory().add(guia)) {
            p.drop(guia, false);
        }
        p.inventoryMenu.broadcastChanges();
        src.sendSuccess(() -> Component.literal("Te entregue la Guia del Servidor. Clic derecho para abrirla.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** Clic derecho con el item-guia en la mano: abre el cofre de la guia. */
    @SubscribeEvent
    public void alUsarItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return; // solo lado servidor
        }
        ItemStack mano = event.getItemStack();
        if (mano.isEmpty() || mano.getItem() != Items.KNOWLEDGE_BOOK) {
            return;
        }
        if (!mano.hasCustomHoverName()) {
            return;
        }
        if (!NOMBRE_ITEM.equals(mano.getHoverName().getString())) {
            return;
        }
        Menus.abrirGuia(p);
        event.setCanceled(true);
    }
}
