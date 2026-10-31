package com.serverpanas.invbackup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Comandos de operador (nivel 2):
 *
 *   /invbackup <jugador>            - fuerza un respaldo ahora.
 *   /invbackups <jugador>          - lista los respaldos guardados de ese jugador.
 *   /invrestore <jugador>          - restaura el respaldo MAS RECIENTE.
 *   /invrestore <jugador> <archivo> - restaura un respaldo concreto por nombre.
 *
 * El jugador a restaurar debe estar conectado (su inventario vive en memoria).
 */
public final class RestoreCommand {

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private RestoreCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Sugerencia: nombres de archivos de respaldo del jugador indicado.
        SuggestionProvider<CommandSourceStack> sugerirArchivos = (ctx, builder) -> {
            try {
                ServerPlayer objetivo = EntityArgument.getPlayer(ctx, "jugador");
                List<File> lista = BackupStorage.listar(objetivo.getServer(), objetivo.getUUID().toString());
                return SharedSuggestionProvider.suggest(
                        lista.stream().map(f -> f.getName().replace(".nbt", "")), builder);
            } catch (Exception e) {
                return builder.buildFuture();
            }
        };

        dispatcher.register(Commands.literal("invbackup")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(RestoreCommand::forzarRespaldo)));

        dispatcher.register(Commands.literal("invbackups")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(RestoreCommand::listar)));

        dispatcher.register(Commands.literal("invrestore")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(ctx -> restaurar(ctx, null))
                        .then(Commands.argument("archivo", StringArgumentType.string())
                                .suggests(sugerirArchivos)
                                .executes(ctx -> restaurar(ctx, StringArgumentType.getString(ctx, "archivo"))))));
    }

    private static int forzarRespaldo(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer objetivo = EntityArgument.getPlayer(ctx, "jugador");
            File f = BackupStorage.guardar(objetivo, "manual");
            if (f == null) {
                ctx.getSource().sendFailure(Component.literal("No se pudo guardar el respaldo."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Respaldo creado para " + objetivo.getGameProfile().getName() + ": " + f.getName()), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int listar(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer objetivo = EntityArgument.getPlayer(ctx, "jugador");
            MinecraftServer server = objetivo.getServer();
            List<File> lista = BackupStorage.listar(server, objetivo.getUUID().toString());
            if (lista.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "No hay respaldos de " + objetivo.getGameProfile().getName() + "."), false);
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Respaldos de " + objetivo.getGameProfile().getName() + " (" + lista.size() + "), del mas nuevo al mas viejo:"), false);
            int mostrar = Math.min(12, lista.size());
            for (int i = 0; i < mostrar; i++) {
                final String nombre = lista.get(i).getName().replace(".nbt", "");
                ctx.getSource().sendSuccess(() -> Component.literal("  " + nombre), false);
            }
            if (lista.size() > mostrar) {
                final int restantes = lista.size() - mostrar;
                ctx.getSource().sendSuccess(() -> Component.literal("  ... y " + restantes + " mas"), false);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Restaurar: /invrestore " + objetivo.getGameProfile().getName() + " <archivo>"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int restaurar(CommandContext<CommandSourceStack> ctx, String archivoNombre) {
        try {
            ServerPlayer objetivo = EntityArgument.getPlayer(ctx, "jugador");
            MinecraftServer server = objetivo.getServer();
            List<File> lista = BackupStorage.listar(server, objetivo.getUUID().toString());
            if (lista.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        "No hay respaldos de " + objetivo.getGameProfile().getName() + "."));
                return 0;
            }

            File elegido;
            if (archivoNombre == null) {
                elegido = lista.get(0); // el mas reciente
            } else {
                String buscado = archivoNombre.endsWith(".nbt") ? archivoNombre : archivoNombre + ".nbt";
                elegido = lista.stream().filter(f -> f.getName().equals(buscado)).findFirst().orElse(null);
                if (elegido == null) {
                    ctx.getSource().sendFailure(Component.literal(
                            "No existe ese respaldo. Usa /invbackups " + objetivo.getGameProfile().getName() + " para ver la lista."));
                    return 0;
                }
            }

            CompoundTag respaldo = BackupStorage.leer(elegido);
            BackupStorage.restaurar(objetivo, respaldo);

            final String cuando = HORA.format(Instant.ofEpochMilli(respaldo.getLong("epoch")));
            final String motivo = respaldo.getString("motivo");
            final String archivo = elegido.getName();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Inventario de " + objetivo.getGameProfile().getName()
                            + " restaurado desde " + archivo + " (" + cuando + ", " + motivo + ")."), true);
            objetivo.sendSystemMessage(Component.literal(
                    "[Inventory Backup] Tu inventario fue restaurado por un operador."));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error al restaurar: " + e.getMessage()));
            return 0;
        }
    }
}
