package com.serverpanas.invbackup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modo reconstruccion (solo servidor, el cliente no necesita nada, sin mensajes).
 *
 * El jugador sigue en supervivencia con su inventario normal:
 *  - Mientras mira un bloque, el servidor deja una copia en el espacio reservado
 *    (ultimo de la mochila) para que el clic de la rueda de supervivencia la encuentre.
 *  - Al usar la rueda, la copia va a la casilla de la barra rapida en la que esta
 *    el jugador: esa es la "casilla de construccion". Lo que hubiera ahi se guarda
 *    y se devuelve al apagar el modo. Cada uso de la rueda cambia el bloque de esa
 *    misma casilla: nunca se acumulan copias.
 *  - La copia (marcada con una etiqueta) no se gasta al colocarla, no se puede
 *    tirar, no se puede guardar en cofres y desaparece si otro la recoge.
 *  - Al apagar el modo se borran todas las copias y se devuelve lo guardado.
 *
 * Comando de operador / consola (lo usa el panel):
 *   /reconstruir <jugador> si | no
 *   /reconstruir <jugador>          (muestra si esta activo)
 */
public final class Reconstruir {
    static final String CLAVE = "serverpanas_reconstruir";
    static final String CLAVE_CASILLA = "serverpanas_reconstruir_casilla";
    static final String CLAVE_GUARDADO = "serverpanas_reconstruir_guardado";
    static final String CLAVE_DADOS = "serverpanas_reconstruir_dados"; // version anterior
    static final String MARCA = "serverpanas_rc";
    /** Espacio reservado: ultimo de la mochila. */
    static final int RESERVADO = 35;

    private static final class Estado {
        ItemStack enReservado = ItemStack.EMPTY; // copia sin marca que el modo dejo en RESERVADO
        int selAnterior = -1;
        boolean usando = false;                  // se intento colocar la copia este tick
    }

    private final Map<UUID, Estado> estados = new HashMap<>();

    // ---------------------------------------------------------------- utilidades

    private static CompoundTag persistido(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static boolean activo(Player p) {
        return persistido(p).getBoolean(CLAVE);
    }

    static boolean esCopia(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean(MARCA);
    }

    private static ItemStack marcar(ItemStack s) {
        ItemStack c = s.copyWithCount(1);
        c.getOrCreateTag().putBoolean(MARCA, true);
        return c;
    }

    private static int casilla(Player p) {
        CompoundTag t = persistido(p);
        return t.contains(CLAVE_CASILLA) ? t.getInt(CLAVE_CASILLA) : -1;
    }

    /** Devuelve un objeto al jugador sin perderlo: hueco libre o, si no hay, a sus pies. */
    private static void devolver(ServerPlayer p, ItemStack s) {
        if (s.isEmpty()) return;
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }

    // ---------------------------------------------------------------- comando

    public void registrar(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("reconstruir")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(this::consultar)
                        .then(Commands.literal("si").executes(c -> cambiar(c, true)))
                        .then(Commands.literal("no").executes(c -> cambiar(c, false)))));
    }

    private int consultar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
        boolean a = activo(p);
        c.getSource().sendSuccess(() -> Component.literal("Modo reconstruccion de " + p.getGameProfile().getName()
                + ": " + (a ? "activo" : "inactivo")), false);
        return a ? 1 : 0;
    }

    private int cambiar(CommandContext<CommandSourceStack> c, boolean si) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
        if (si) {
            persistido(p).putBoolean(CLAVE, true);
        } else {
            apagar(p);
        }
        c.getSource().sendSuccess(() -> Component.literal("Modo reconstruccion " + (si ? "activado" : "desactivado")
                + " para " + p.getGameProfile().getName()), false);
        return 1;
    }

    /** Apaga el modo: borra copias, vacia el reservado y devuelve lo guardado. */
    private void apagar(ServerPlayer p) {
        Inventory inv = p.getInventory();
        Estado st = estados.remove(p.getUUID());
        if (st != null && !st.enReservado.isEmpty()
                && ItemStack.isSameItemSameTags(inv.getItem(RESERVADO), st.enReservado)) {
            inv.setItem(RESERVADO, ItemStack.EMPTY);
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (esCopia(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        if (esCopia(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        retirarDadosAntiguos(p);

        CompoundTag t = persistido(p);
        int cas = casilla(p);
        if (t.contains(CLAVE_GUARDADO)) {
            ItemStack guardado = ItemStack.of(t.getCompound(CLAVE_GUARDADO));
            if (cas >= 0 && cas < 9 && inv.getItem(cas).isEmpty()) inv.setItem(cas, guardado);
            else devolver(p, guardado);
        }
        t.remove(CLAVE);
        t.remove(CLAVE_CASILLA);
        t.remove(CLAVE_GUARDADO);
        inv.setChanged();
        p.containerMenu.broadcastChanges();
    }

    /** Limpia las copias sin marca que entrego la version anterior del modo. */
    private static void retirarDadosAntiguos(ServerPlayer p) {
        CompoundTag t = persistido(p);
        if (!t.contains(CLAVE_DADOS)) return;
        CompoundTag dados = t.getCompound(CLAVE_DADOS);
        Inventory inv = p.getInventory();
        for (String id : dados.getAllKeys()) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            int falta = dados.getInt(id);
            if (item == null) continue;
            for (int i = 0; i < inv.getContainerSize() && falta > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item) && !s.hasTag()) {
                    int quitar = Math.min(falta, s.getCount());
                    s.shrink(quitar);
                    falta -= quitar;
                }
            }
        }
        t.remove(CLAVE_DADOS);
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    public void alUsarEnBloque(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !activo(p)) return;
        if (e.getHand() == InteractionHand.MAIN_HAND && esCopia(e.getItemStack())) {
            estados.computeIfAbsent(p.getUUID(), k -> new Estado()).usando = true;
        }
    }

    /** Las copias no se tiran: la copia vuelve a su casilla y no aparece en el suelo. */
    @SubscribeEvent
    public void alTirar(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        ItemStack s = e.getEntity().getItem();
        Estado st = estados.get(p.getUUID());
        boolean copiaReservada = st != null && !st.enReservado.isEmpty() && !s.hasTag() && s.getCount() == 1
                && s.is(st.enReservado.getItem())
                && !ItemStack.isSameItemSameTags(p.getInventory().getItem(RESERVADO), st.enReservado);
        if (!esCopia(s) && !copiaReservada) return;
        e.setCanceled(true); // el objeto no llega al suelo
        if (copiaReservada) st.enReservado = ItemStack.EMPTY;
        if (esCopia(s)) {
            int cas = casilla(p);
            if (activo(p) && cas >= 0 && cas < 9 && p.getInventory().getItem(cas).isEmpty()) {
                p.getInventory().setItem(cas, marcar(s));
            }
        }
    }

    /** Nadie puede recoger una copia del suelo: se borra. */
    @SubscribeEvent
    public void alRecoger(EntityItemPickupEvent e) {
        if (esCopia(e.getItem().getItem())) {
            e.setCanceled(true);
            e.getItem().discard();
        }
    }

    /** Las copias no se guardan en cofres ni otros contenedores. */
    @SubscribeEvent
    public void alCerrarContenedor(PlayerContainerEvent.Close e) {
        AbstractContainerMenu menu = e.getContainer();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            if (esCopia(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
    }

    /** Al morir, las copias no caen al suelo. */
    @SubscribeEvent
    public void alSoltarAlMorir(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Estado st = estados.get(p.getUUID());
        final Item reservada = (st == null || st.enReservado.isEmpty()) ? null : st.enReservado.getItem();
        boolean[] quitada = {reservada == null};
        e.getDrops().removeIf(it -> {
            ItemStack s = it.getItem();
            if (esCopia(s)) return true;
            if (!quitada[0] && !s.hasTag() && s.getCount() == 1 && s.is(reservada)) {
                quitada[0] = true;
                return true;
            }
            return false;
        });
        if (st != null) st.enReservado = ItemStack.EMPTY;
    }

    /** Ninguna copia fuera del inventario propio: ni cofres, ni mesa de crafteo, ni hornos, ni tiendas. */
    private static void limpiarContenedores(ServerPlayer p) {
        for (AbstractContainerMenu menu : new AbstractContainerMenu[]{p.inventoryMenu, p.containerMenu}) {
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory) continue;
                if (esCopia(slot.getItem())) slot.set(ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public void alSalir(PlayerEvent.PlayerLoggedOutEvent e) {
        estados.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void alTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Inventory inv = p.getInventory();
        if (!activo(p)) return;
        limpiarContenedores(p);
        Estado st = estados.computeIfAbsent(p.getUUID(), k -> new Estado());
        int cas = casilla(p);

        // 1) Colocar la copia no la gasta.
        if (st.usando) {
            st.usando = false;
            if (cas >= 0 && cas < 9 && inv.getItem(cas).isEmpty()) {
                CompoundTag t = persistido(p);
                if (t.contains("serverpanas_reconstruir_bloque")) {
                    ItemStack b = ItemStack.of(t.getCompound("serverpanas_reconstruir_bloque"));
                    if (!b.isEmpty()) inv.setItem(cas, marcar(b));
                }
            }
        }

        // 2) Se uso la rueda: la copia salio del reservado.
        if (!st.enReservado.isEmpty() && !ItemStack.isSameItemSameTags(inv.getItem(RESERVADO), st.enReservado)) {
            usarRueda(p, inv, st);
            cas = casilla(p);
        }

        // 3) Solo puede haber una copia y solo en la casilla de construccion.
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i != cas && esCopia(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }

        // 4) Con un cofre u otro contenedor abierto no se deja la copia en el inventario:
        //    asi no se puede guardar ni transferir (Quark, Shift+clic, arrastrar...).
        if (p.containerMenu != p.inventoryMenu) {
            if (!st.enReservado.isEmpty() && ItemStack.isSameItemSameTags(inv.getItem(RESERVADO), st.enReservado)) {
                inv.setItem(RESERVADO, ItemStack.EMPTY);
            }
            st.enReservado = ItemStack.EMPTY;
            st.selAnterior = inv.selected;
            return;
        }

        // 5) Preparar el bloque que mira para la proxima vez que use la rueda.
        prepararBloqueMirado(p, inv, st);
        st.selAnterior = inv.selected;
    }

    private void usarRueda(ServerPlayer p, Inventory inv, Estado st) {
        ItemStack bloque = st.enReservado;
        st.enReservado = ItemStack.EMPTY;
        int sel = inv.selected;
        if (!ItemStack.isSameItemSameTags(inv.getItem(sel), bloque)) {
            // No fue la rueda: el jugador movio la copia a mano. Se borra donde este.
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.hasTag() && s.is(bloque.getItem()) && s.getCount() == 1 && i != RESERVADO) {
                    inv.setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
            ItemStack cursor = p.containerMenu.getCarried();
            if (!cursor.hasTag() && cursor.is(bloque.getItem())) {
                p.containerMenu.setCarried(ItemStack.EMPTY);
                return;
            }
            // Si la copia acabo en un cofre, la mesa de crafteo u otro contenedor, se quita
            // de alli una unidad (es la copia; si no, se duplicaria en cada transferencia).
            for (AbstractContainerMenu menu : new AbstractContainerMenu[]{p.containerMenu, p.inventoryMenu}) {
                for (Slot slot : menu.slots) {
                    if (slot.container instanceof Inventory) continue;
                    ItemStack s = slot.getItem();
                    if (!s.isEmpty() && !s.hasTag() && s.is(bloque.getItem())) {
                        s.shrink(1);
                        slot.setChanged();
                        menu.broadcastChanges();
                        return;
                    }
                }
            }
            return;
        }
        // La rueda de supervivencia intercambio el reservado con la casilla "sel"
        // (prefiere una casilla vacia). La casilla de construccion es la que tenia el jugador.
        int destino = (st.selAnterior >= 0 && st.selAnterior < 9) ? st.selAnterior : sel;
        ItemStack desplazado;
        if (destino != sel) {
            inv.setItem(sel, inv.getItem(RESERVADO)); // lo que habia en "sel" vuelve a su sitio
            desplazado = inv.getItem(destino);
            inv.selected = destino;
            p.connection.send(new ClientboundSetCarriedItemPacket(destino));
        } else {
            desplazado = inv.getItem(RESERVADO);
        }
        inv.setItem(RESERVADO, ItemStack.EMPTY);

        CompoundTag t = persistido(p);
        int anterior = casilla(p);
        if (anterior != destino && anterior >= 0 && anterior < 9) {
            // Cambio de casilla de construccion: la anterior recupera lo que tenia.
            ItemStack enAnterior = inv.getItem(anterior);
            if (esCopia(enAnterior)) inv.setItem(anterior, ItemStack.EMPTY);
            if (t.contains(CLAVE_GUARDADO)) {
                ItemStack g = ItemStack.of(t.getCompound(CLAVE_GUARDADO));
                t.remove(CLAVE_GUARDADO);
                if (inv.getItem(anterior).isEmpty()) inv.setItem(anterior, g); else devolver(p, g);
            }
        }
        if (!desplazado.isEmpty() && !esCopia(desplazado)) {
            if (t.contains(CLAVE_GUARDADO)) devolver(p, desplazado);
            else t.put(CLAVE_GUARDADO, desplazado.save(new CompoundTag()));
        }
        ItemStack copia = marcar(bloque);
        inv.setItem(destino, copia);
        t.putInt(CLAVE_CASILLA, destino);
        t.put("serverpanas_reconstruir_bloque", bloque.copyWithCount(1).save(new CompoundTag()));
    }

    private void prepararBloqueMirado(ServerPlayer p, Inventory inv, Estado st) {
        HitResult hit = p.pick(p.getBlockReach(), 1.0F, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bh.getBlockPos();
        BlockState estado = p.level().getBlockState(pos);
        if (estado.isAir()) return;
        ItemStack bloque;
        try {
            bloque = estado.getCloneItemStack(hit, p.level(), pos, p);
        } catch (Exception ex) {
            return;
        }
        if (bloque.isEmpty() || !(bloque.getItem() instanceof BlockItem)) return;
        bloque = bloque.copyWithCount(1);

        if (!st.enReservado.isEmpty() && ItemStack.isSameItemSameTags(st.enReservado, bloque)) return;
        // Si ya tiene ese bloque de verdad, la rueda normal lo encuentra.
        if (inv.findSlotMatchingItem(bloque) >= 0) return;

        ItemStack enRes = inv.getItem(RESERVADO);
        boolean esNuestra = !st.enReservado.isEmpty() && ItemStack.isSameItemSameTags(enRes, st.enReservado);
        if (!enRes.isEmpty() && !esNuestra) {
            // Algo del jugador ocupa el reservado: moverlo a otro hueco o no hacer nada.
            int libre = -1;
            for (int i = 9; i < 36; i++) if (i != RESERVADO && inv.getItem(i).isEmpty()) { libre = i; break; }
            if (libre < 0) return;
            inv.setItem(libre, enRes);
        }
        inv.setItem(RESERVADO, bloque.copy());
        st.enReservado = bloque;
    }
}
