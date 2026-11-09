package com.serverpanas.servereco;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.serverpanas.servereco.auction.AuctionData;
import com.serverpanas.servereco.data.BalanceData;
import com.serverpanas.servereco.data.DiscoveryData;
import com.serverpanas.servereco.data.MarketData;
import com.serverpanas.servereco.economy.EcoConfig;
import com.serverpanas.servereco.economy.ItemValues;
import com.serverpanas.servereco.shop.Menus;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Descubrimiento de items + comandos del mod.
 *
 * Descubrimiento:
 *   - Al RECOGER un item (EntityItemPickupEvent).
 *   - Al CRAFTEAR un item (PlayerEvent.ItemCraftedEvent).
 *   - Por BARRIDO del INVENTARIO: cada pocos segundos se escanea el inventario
 *     de cada jugador y se marca como descubierto todo lo que tenga. Asi, lo
 *     que ya traias antes de instalar el mod tambien se desbloquea en la tienda.
 * Lo descubierto es lo unico que luego aparece en su /shop.
 *
 * Comandos:
 *   /shop                 abre la tienda (solo descubiertos).
 *   /ah                   abre la casa de subastas.
 *   /ah sell <precio>     sube el item de la mano principal.
 *   /ah cancel            retira tu subasta mas reciente y te devuelve el item.
 *   /buzon                reclama pagos pendientes de subastas.
 *   /tienda reload        (OP nivel 2+) relee config/servereco/valores.json y
 *                         aplica los nuevos valores EN VIVO, sin reiniciar.
 *   /servereco reload     alias del anterior.
 *
 * Config: al ARRANCAR el servidor (ServerStartingEvent) se carga/genera el JSON
 * de valoracion (EcoConfig). El RecipeManager ya esta listo en ese punto.
 */
public class EcoEvents {

    // ---------------- Config: carga al arrancar ----------------

    @SubscribeEvent
    public void alArrancarServidor(ServerStartingEvent event) {
        EcoConfig.cargar();
        ItemValues.invalidarCache();
    }

    // ---------------- Descubrimiento ----------------

    @SubscribeEvent
    public void alRecoger(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            registrar(player, event.getItem().getItem());
        }
    }

    @SubscribeEvent
    public void alCraftear(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            registrar(player, event.getCrafting());
        }
    }

    // Barrido del inventario: cada ~5 s (100 ticks) recorre el inventario de
    // cada jugador conectado y descubre todo lo que tenga. Cubre los items que
    // el jugador ya traia antes de instalar el mod.
    private int contadorTicks = 0;

    @SubscribeEvent
    public void alTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++contadorTicks < 100) {
            return;
        }
        contadorTicks = 0;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            escanearInventario(player);
        }
    }

    private void escanearInventario(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                registrar(player, s);
            }
        }
    }

    private void registrar(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (rl == null) {
            return;
        }
        DiscoveryData disc = DiscoveryData.get(player.server);
        boolean nuevo = disc.descubrir(player.getUUID(), rl.toString());
        if (nuevo && com.serverpanas.servereco.shop.ShopCatalog.enVenta(stack.getItem())) {
            player.sendSystemMessage(Component.literal("Nuevo item desbloqueado en la tienda: ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        }
    }

    // ---------------- Comandos ----------------

    @SubscribeEvent
    public void registrarComandos(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // Comando principal unificado. 'reload' (OP nivel 2+) recarga el JSON.
        // 'valor <item_id>' (OP nivel 2+) imprime el valor calculado del item.
        d.register(Commands.literal("tienda")
                .executes(ctx -> abrirPrincipal(ctx.getSource()))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> recargarConfig(ctx.getSource())))
                .then(Commands.literal("valor")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(ctx -> valorDeItem(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "item")))))
                .then(subComandoCreditos())
                .then(subComandoMercado())
                .then(subComandoSubasta()));

        // Comando de administracion dedicado: /servereco reload, valor,
        // creditos (ajuste de saldo) y mercado/subasta (quitar listados).
        d.register(Commands.literal("servereco")
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> recargarConfig(ctx.getSource())))
                .then(Commands.literal("valor")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(ctx -> valorDeItem(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "item")))))
                .then(subComandoCreditos())
                .then(subComandoMercado())
                .then(subComandoSubasta()));

        // Saldo de creditos por chat (/creditos y alias /bal).
        d.register(Commands.literal("creditos").executes(ctx -> verSaldo(ctx.getSource())));
        d.register(Commands.literal("bal").executes(ctx -> verSaldo(ctx.getSource())));

        // Alias existentes que apuntan a las sub-vistas correspondientes.
        d.register(Commands.literal("shop").executes(ctx -> abrirShop(ctx.getSource())));

        d.register(Commands.literal("ah")
                .executes(ctx -> abrirAh(ctx.getSource()))
                .then(Commands.literal("sell")
                        .then(Commands.argument("precio", IntegerArgumentType.integer(1))
                                .executes(EcoEvents::vender)))
                .then(Commands.literal("cancel").executes(ctx -> cancelar(ctx.getSource()))));

        d.register(Commands.literal("buzon").executes(ctx -> reclamarBuzon(ctx.getSource())));

        // Poderes: menu directo y respuesta a las solicitudes de teletransporte.
        d.register(Commands.literal("poderes").executes(ctx -> {
            ServerPlayer p = jugador(ctx.getSource());
            if (p == null) return 0;
            com.serverpanas.servereco.shop.Menus.abrirPoderes(p, null);
            return 1;
        }));
        d.register(Commands.literal("tpaceptar").executes(ctx -> {
            ServerPlayer p = jugador(ctx.getSource());
            return p == null ? 0 : com.serverpanas.servereco.shop.Powers.aceptar(p);
        }));
        d.register(Commands.literal("tprechazar").executes(ctx -> {
            ServerPlayer p = jugador(ctx.getSource());
            return p == null ? 0 : com.serverpanas.servereco.shop.Powers.rechazar(p);
        }));
    }

    // ---------------- Admin: ajuste de creditos (saldo) ----------------
    //
    // /tienda creditos set|add|remove <jugador> <cantidad>   (alias /servereco)
    // Permite al admin FIJAR, SUMAR o RESTAR creditos al SALDO de un jugador
    // (BalanceData). Funciona con jugadores OFFLINE: el nombre se resuelve por
    // la cache de perfiles del servidor (usercache). Requiere OP nivel 2+.
    // El panel lo ejecuta via EXA.comando para devolver/ajustar saldos
    // (p.ej. reponer lo que un jugador perdio por el bug del sorter).

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> subComandoCreditos() {
        return Commands.literal("creditos")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("jugador", StringArgumentType.word())
                                .then(Commands.argument("cantidad", LongArgumentType.longArg(0))
                                        .executes(ctx -> ajustarCreditos(ctx, "set")))))
                .then(Commands.literal("add")
                        .then(Commands.argument("jugador", StringArgumentType.word())
                                .then(Commands.argument("cantidad", LongArgumentType.longArg(1))
                                        .executes(ctx -> ajustarCreditos(ctx, "add")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("jugador", StringArgumentType.word())
                                .then(Commands.argument("cantidad", LongArgumentType.longArg(1))
                                        .executes(ctx -> ajustarCreditos(ctx, "remove")))));
    }

    /** Resuelve el UUID de un jugador por nombre (online u offline via usercache). */
    private static UUID uuidPorNombre(CommandSourceStack src, String nombre) {
        ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(nombre);
        if (online != null) {
            return online.getUUID();
        }
        Optional<com.mojang.authlib.GameProfile> perfil = src.getServer().getProfileCache() == null
                ? Optional.empty()
                : src.getServer().getProfileCache().get(nombre);
        return perfil.map(com.mojang.authlib.GameProfile::getId).orElse(null);
    }

    private static int ajustarCreditos(CommandContext<CommandSourceStack> ctx, String modo) {
        CommandSourceStack src = ctx.getSource();
        String nombre = StringArgumentType.getString(ctx, "jugador");
        long cantidad = LongArgumentType.getLong(ctx, "cantidad");
        UUID uuid = uuidPorNombre(src, nombre);
        if (uuid == null) {
            src.sendFailure(Component.literal("[servereco] creditos: jugador desconocido '" + nombre
                    + "' (nunca ha entrado al servidor)."));
            return 0;
        }
        BalanceData bal = BalanceData.get(src.getServer());
        long antes = bal.saldo(uuid);
        long despues;
        switch (modo) {
            case "set" -> {
                // Fijar exacto: limpiamos y sumamos el objetivo (nunca negativo).
                despues = Math.max(0, cantidad);
                if (despues > antes) {
                    bal.sumar(uuid, despues - antes);
                } else if (despues < antes) {
                    bal.restar(uuid, antes - despues);
                }
            }
            case "add" -> {
                bal.sumar(uuid, cantidad);
                despues = bal.saldo(uuid);
            }
            case "remove" -> {
                long quitar = Math.min(cantidad, antes); // nunca deja negativo
                bal.restar(uuid, quitar);
                despues = bal.saldo(uuid);
            }
            default -> {
                src.sendFailure(Component.literal("[servereco] creditos: modo invalido."));
                return 0;
            }
        }
        // Linea estable y parseable al LOG + feedback humano (la lee el panel).
        final long aAntes = antes;
        final long aDespues = despues;
        String linea = "[servereco] creditos " + modo + " " + nombre + " " + cantidad
                + " -> saldo " + aAntes + " => " + aDespues;
        ServerEcoMod.LOG.info(linea);
        src.sendSuccess(() -> Component.literal(linea).withStyle(ChatFormatting.GOLD), true);
        // Avisar al jugador si esta online.
        ServerPlayer online = src.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            online.sendSystemMessage(Component.literal("Un admin ajusto tus creditos. Saldo actual: "
                    + aDespues).withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }

    // ---------------- Admin: quitar listados de mercado y subastas ----------------
    //
    // /servereco mercado quitar <id>   /servereco subasta quitar <id>
    // (alias /tienda ...). El admin VE los listados en el panel (lee los .dat) y
    // puede ELIMINAR uno por su id. Para no perder el item, se DEVUELVE al buzon
    // del vendedor (lo reclama con /buzon). Requiere OP nivel 2+.

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> subComandoMercado() {
        return Commands.literal("mercado")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("quitar")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(ctx -> quitarMercado(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "id")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> subComandoSubasta() {
        return Commands.literal("subasta")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("quitar")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(ctx -> quitarSubasta(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "id")))));
    }

    private static int quitarMercado(CommandSourceStack src, long id) {
        MarketData market = MarketData.get(src.getServer());
        MarketData.Listado l = market.porId(id);
        if (l == null) {
            src.sendFailure(Component.literal("[servereco] mercado: no existe el listado id " + id + "."));
            return 0;
        }
        DiscoveryData.get(src.getServer()).aBuzon(l.vendedor, l.item.copy());
        market.quitar(id);
        String nombreItem = l.item.getHoverName().getString();
        String linea = "[servereco] mercado quitar " + id + " (" + nombreItem + " de "
                + l.vendedorNombre + ") devuelto al buzon del vendedor";
        ServerEcoMod.LOG.info(linea);
        src.sendSuccess(() -> Component.literal(linea).withStyle(ChatFormatting.GOLD), true);
        ServerPlayer vend = src.getServer().getPlayerList().getPlayer(l.vendedor);
        if (vend != null) {
            vend.sendSystemMessage(Component.literal("Un admin retiro tu listado de " + nombreItem
                    + "; reclamalo con /buzon.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int quitarSubasta(CommandSourceStack src, long id) {
        AuctionData ah = AuctionData.get(src.getServer());
        AuctionData.Lote l = ah.porId(id);
        if (l == null) {
            src.sendFailure(Component.literal("[servereco] subasta: no existe el lote id " + id + "."));
            return 0;
        }
        DiscoveryData.get(src.getServer()).aBuzon(l.vendedor, l.item.copy());
        ah.quitar(id);
        String nombreItem = l.item.getHoverName().getString();
        String linea = "[servereco] subasta quitar " + id + " (" + nombreItem + " de "
                + l.vendedorNombre + ") devuelto al buzon del vendedor";
        ServerEcoMod.LOG.info(linea);
        src.sendSuccess(() -> Component.literal(linea).withStyle(ChatFormatting.GOLD), true);
        ServerPlayer vend = src.getServer().getPlayerList().getPlayer(l.vendedor);
        if (vend != null) {
            vend.sendSystemMessage(Component.literal("Un admin retiro tu subasta de " + nombreItem
                    + "; reclamalo con /buzon.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static ServerPlayer jugador(CommandSourceStack src) {
        try {
            return src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Solo un jugador puede usar esto."));
            return null;
        }
    }

    private static int abrirPrincipal(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        Menus.abrirPrincipal(p);
        return 1;
    }

    private static int verSaldo(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        long saldo = BalanceData.get(p.server).saldo(p);
        src.sendSuccess(() -> Component.literal("Creditos: " + saldo)
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /**
     * Recarga EN CALIENTE config/servereco/valores.json y aplica los nuevos
     * valores sin reiniciar. Requiere OP nivel 2+ (ver requires() del comando).
     */
    private static int recargarConfig(CommandSourceStack src) {
        boolean ok = EcoConfig.cargar();
        ItemValues.invalidarCache(); // fuerza recomputo de BASE con los nuevos numeros
        if (ok) {
            src.sendSuccess(() -> Component.literal("Valores recargados")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        src.sendFailure(Component.literal("Error al recargar valores.json (revisa el formato; se conservan los valores previos)."));
        return 0;
    }

    /**
     * Imprime el VALOR CALCULADO ACTUAL de un item concreto (por su id
     * "modid:item"). Lo usa el panel para que el admin vea de cuanto parte un
     * item ANTES de ponerle un valor personalizado. Emite una linea estable y
     * parseable tanto al chat como al LOG del servidor:
     *
     *   [servereco] valor minecraft:diamond = 100 (override) | base
     *
     * El sufijo indica el origen del precio: "override" si el item ya tiene
     * valor personalizado, "base" si es el valor derivado por la heuristica.
     * Requiere OP nivel 2+ (ver requires() del comando).
     */
    private static int valorDeItem(CommandSourceStack src, String texto) {
        String id = (texto == null ? "" : texto.trim().toLowerCase());
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            String msg = "[servereco] valor " + id + " = ? (item desconocido)";
            ServerEcoMod.LOG.info(msg);
            src.sendFailure(Component.literal(msg));
            return 0;
        }
        var item = BuiltInRegistries.ITEM.get(rl);
        int valor = ItemValues.valorDe(new ItemStack(item));
        Integer override = ItemValues.overrideDe(item);
        String origen = (override != null) ? "override" : "base";
        String linea = "[servereco] valor " + rl + " = " + valor + " (" + origen + ")";
        // Al LOG (lo lee el panel) y al chat/consola (feedback humano).
        ServerEcoMod.LOG.info(linea);
        src.sendSuccess(() -> Component.literal(linea).withStyle(ChatFormatting.GOLD), false);
        return valor;
    }

    private static int abrirShop(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        Menus.abrirComprar(p);
        return 1;
    }

    private static int abrirAh(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        Menus.abrirSubastar(p);
        return 1;
    }

    private static int vender(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = jugador(ctx.getSource());
        if (p == null) {
            return 0;
        }
        int precio = IntegerArgumentType.getInteger(ctx, "precio");
        ItemStack mano = p.getMainHandItem();
        if (mano.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Debes tener el item en la mano principal."));
            return 0;
        }
        AuctionData ah = AuctionData.get(p.server);
        if (ah.lleno()) {
            ctx.getSource().sendFailure(Component.literal("La casa de subastas esta llena. Intenta mas tarde."));
            return 0;
        }
        ItemStack copia = mano.copy();
        ah.publicar(p.getUUID(), p.getGameProfile().getName(), copia, precio);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.inventoryMenu.broadcastChanges();
        ctx.getSource().sendSuccess(() -> Component.literal("Subaste " + copia.getCount() + " x "
                + copia.getHoverName().getString() + " por " + precio + " creditos.")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int cancelar(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        AuctionData ah = AuctionData.get(p.server);
        // Buscar el lote mas reciente de este jugador.
        AuctionData.Lote mio = null;
        for (AuctionData.Lote l : ah.listar()) {
            if (l.vendedor.equals(p.getUUID())) {
                mio = l; // el ultimo que coincida (lista en orden de publicacion)
            }
        }
        if (mio == null) {
            src.sendFailure(Component.literal("No tienes subastas activas."));
            return 0;
        }
        ItemStack devuelto = mio.item.copy();
        if (!p.getInventory().add(devuelto)) {
            p.drop(devuelto, false);
        }
        p.inventoryMenu.broadcastChanges();
        ah.quitar(mio.id);
        src.sendSuccess(() -> Component.literal("Subasta cancelada, item devuelto.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int reclamarBuzon(CommandSourceStack src) {
        ServerPlayer p = jugador(src);
        if (p == null) {
            return 0;
        }
        DiscoveryData disc = DiscoveryData.get(p.server);
        List<ItemStack> pend = disc.vaciarBuzon(p.getUUID());
        if (pend.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Tu buzon esta vacio.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        int n = 0;
        for (ItemStack s : pend) {
            ItemStack copia = s.copy();
            if (!p.getInventory().add(copia)) {
                p.drop(copia, false);
            }
            n += s.getCount();
        }
        p.inventoryMenu.broadcastChanges();
        final int total = n;
        src.sendSuccess(() -> Component.literal("Reclamaste " + total + " items de tu buzon.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
