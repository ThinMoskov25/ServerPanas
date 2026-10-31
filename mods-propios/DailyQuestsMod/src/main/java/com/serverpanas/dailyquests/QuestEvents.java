package com.serverpanas.dailyquests;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Manejadores de eventos y comando del mod.
 *
 *  - BlockEvent.BreakEvent  -> progreso de misiones MINE.
 *  - LivingDeathEvent       -> progreso de misiones KILL (por el atacante jugador).
 *  - /misiones              -> abre el cofre virtual.
 *
 * La ENTREGA de items (DELIVER) y el RECLAMO de recompensa se disparan al
 * pulsar el item en el cofre (ver QuestMenu.clicked -> alPulsarMision).
 */
public class QuestEvents {

    // ---------------- Rastreo de progreso ----------------

    @SubscribeEvent
    public void alRomperBloque(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockState estado = event.getState();
        ResourceLocation idBloque = BuiltInRegistries.BLOCK.getKey(estado.getBlock());
        avanzar(player, QuestType.MINE, idBloque.toString(), 1);
    }

    @SubscribeEvent
    public void alMorirEntidad(LivingDeathEvent event) {
        LivingEntity muerto = event.getEntity();
        if (muerto.level().isClientSide()) {
            return;
        }
        // Solo cuenta si el que mata es un jugador del servidor.
        if (event.getSource().getEntity() instanceof ServerPlayer asesino) {
            ResourceLocation idEntidad = BuiltInRegistries.ENTITY_TYPE.getKey(muerto.getType());
            avanzar(asesino, QuestType.KILL, idEntidad.toString(), 1);
        }
    }

    private void avanzar(ServerPlayer player, QuestType tipo, String objetivo, int cantidad) {
        QuestData data = QuestData.get(player.server);
        boolean cambio = data.avanzar(player.getUUID(), tipo, objetivo, cantidad);
        if (!cambio) {
            return;
        }
        // Aviso discreto si alguna mision acaba de completarse.
        for (QuestData.Entrada e : data.misionesDeHoy(player.getUUID())) {
            QuestDef def = QuestRegistry.porId(e.questId);
            if (def != null && def.tipo() == tipo && def.objetivo().equals(objetivo)
                    && !e.reclamada && e.progreso >= def.cantidad()) {
                player.sendSystemMessage(Component.literal("Mision completada: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(def.titulo()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" - usa /misiones para reclamar.").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    // ---------------- Comando /misiones ----------------

    @SubscribeEvent
    public void registrarComandos(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("misiones")
                .executes(ctx -> abrir(ctx.getSource()))
                // /misiones reset <jugador>  (OP): fuerza regenerar sus misiones
                .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("jugador", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(QuestEvents::resetear)))
                // /misiones dar <jugador> <facil|medio|dificil>  (OP nivel 2+):
                // asigna una mision personalizada al azar del pool del nivel.
                .then(Commands.literal("dar")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("jugador", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.literal("facil")
                                        .executes(ctx -> dar(ctx, QuestRegistry.Nivel.FACIL)))
                                .then(Commands.literal("medio")
                                        .executes(ctx -> dar(ctx, QuestRegistry.Nivel.MEDIO)))
                                .then(Commands.literal("dificil")
                                        .executes(ctx -> dar(ctx, QuestRegistry.Nivel.DIFICIL))))));
    }

    private static int abrir(CommandSourceStack src) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Solo un jugador puede abrir las misiones."));
            return 0;
        }
        abrirCofre(player);
        return 1;
    }

    private static int resetear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer objetivo = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "jugador");
            QuestData data = QuestData.get(objetivo.server);
            // Forzar dia invalido para que se regeneren en la proxima consulta.
            data.misionesDeHoy(objetivo.getUUID()).clear();
            data.setDirty();
            // Volver a generar inmediatamente consultando de nuevo.
            data.misionesDeHoy(objetivo.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Misiones de " + objetivo.getGameProfile().getName() + " reiniciadas."), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * /misiones dar <jugador> <facil|medio|dificil>: elige al azar una QuestDef
     * del pool del nivel y la asigna al jugador como mision PERSONALIZADA activa
     * (ademas de sus 3 diarias). El jugador la vera en su /misiones y la podra
     * reclamar al completarla con la misma mecanica de siempre.
     */
    private static int dar(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                           QuestRegistry.Nivel nivel) {
        try {
            ServerPlayer objetivo = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "jugador");
            QuestDef def = QuestRegistry.aleatoriaDe(nivel);
            if (def == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "No hay misiones en el pool de nivel " + nivel.name().toLowerCase() + "."));
                return 0;
            }
            QuestData data = QuestData.get(objetivo.server);
            data.agregarPersonalizada(objetivo.getUUID(), def.id());

            String nombre = objetivo.getGameProfile().getName();
            String nivelTxt = nivel.name().toLowerCase();
            ctx.getSource().sendSuccess(() -> Component.literal("Mision personalizada (" + nivelTxt + ") ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(def.titulo()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" asignada a " + nombre + ".").withStyle(ChatFormatting.GRAY)), true);

            // Avisar al propio jugador (si esta conectado).
            objetivo.sendSystemMessage(Component.literal("Nueva mision personalizada: ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(def.titulo()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" - " + def.descripcion() + " (usa /misiones).")
                            .withStyle(ChatFormatting.GRAY)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /** Abre el cofre virtual de misiones para el jugador (cofre vanilla). */
    public static void abrirCofre(ServerPlayer player) {
        net.minecraft.world.Container contenido = QuestMenu.nuevoContenedor();
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Misiones Diarias").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new QuestMenu(id, inv, player, contenido);
            }
        });
    }

    // ---------------- Reclamar / entregar (clic en el cofre) ----------------

    /** Llamado por QuestMenu cuando el jugador pulsa el item de una mision. */
    public static void alPulsarMision(ServerPlayer player, String questId) {
        QuestData data = QuestData.get(player.server);
        QuestData.Entrada e = data.entrada(player.getUUID(), questId);
        QuestDef def = QuestRegistry.porId(questId);
        if (e == null || def == null || e.reclamada) {
            return;
        }

        boolean completa = e.progreso >= def.cantidad();

        // Si es DELIVER y aun no esta completa, intentamos cobrar los items.
        if (!completa && def.tipo() == QuestType.DELIVER) {
            int faltan = def.cantidad() - e.progreso;
            int cobrados = cobrarItems(player, def.objetivo(), faltan);
            if (cobrados > 0) {
                data.avanzar(player.getUUID(), QuestType.DELIVER, def.objetivo(), cobrados);
                e = data.entrada(player.getUUID(), questId);
                completa = e != null && e.progreso >= def.cantidad();
                player.sendSystemMessage(Component.literal("Entregaste " + cobrados + " de "
                        + def.objetivo().replace("minecraft:", "")).withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                player.sendSystemMessage(Component.literal("No tienes "
                                + def.objetivo().replace("minecraft:", "") + " para entregar.")
                        .withStyle(ChatFormatting.RED));
            }
        }

        // Si quedo completa (o ya lo estaba), reclamamos la recompensa.
        if (completa && !e.reclamada) {
            otorgar(player, def);
            data.marcarReclamada(player.getUUID(), questId);
            player.sendSystemMessage(Component.literal("Recompensa reclamada: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(def.titulo()).withStyle(ChatFormatting.GOLD)));
        }
    }

    /**
     * Quita del inventario hasta "cantidad" items con el id indicado.
     * Devuelve cuantos se quitaron realmente.
     */
    private static int cobrarItems(ServerPlayer player, String idItem, int cantidad) {
        ResourceLocation rl = ResourceLocation.tryParse(idItem);
        if (rl == null) {
            return 0;
        }
        var item = BuiltInRegistries.ITEM.get(rl);
        int restante = cantidad;
        int quitados = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && restante > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                int toma = Math.min(restante, s.getCount());
                s.shrink(toma);
                restante -= toma;
                quitados += toma;
            }
        }
        if (quitados > 0) {
            inv.setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return quitados;
    }

    /**
     * Otorga la recompensa ejecutando los comandos definidos COMO CONSOLA,
     * sustituyendo %player% por el nombre del jugador. El source tiene nivel
     * 4 (consola) y salida silenciosa.
     */
    private static void otorgar(ServerPlayer player, QuestDef def) {
        MinecraftServer server = player.server;
        String nombre = player.getGameProfile().getName();
        CommandSourceStack consola = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        for (String plantilla : def.recompensas()) {
            String cmd = plantilla.replace("%player%", nombre);
            try {
                server.getCommands().performPrefixedCommand(consola, cmd);
            } catch (Exception ex) {
                DailyQuestsMod.LOG.error("Error ejecutando recompensa '{}': {}", cmd, ex.getMessage());
            }
        }
        DailyQuestsMod.LOG.info("Recompensa de '{}' otorgada a {}", def.id(), nombre);
    }
}
