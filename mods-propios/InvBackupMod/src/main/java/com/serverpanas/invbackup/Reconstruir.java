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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modo reconstruccion (solo servidor, el cliente no necesita nada).
 *
 * El jugador sigue en supervivencia con su inventario normal, pero:
 *  - Mientras mira un bloque que no tiene, el servidor le pone una copia en el
 *    espacio reservado (ultimo de la mochila). Asi el clic de la rueda de
 *    supervivencia lo encuentra y lo lleva al espacio de la barra rapida en el
 *    que esta el jugador.
 *  - Los bloques que coloca no se gastan.
 *  - Al desactivarlo se retiran las copias que dio el modo.
 *
 * Comando de operador / consola (lo usa el panel):
 *   /reconstruir <jugador> si | no
 *   /reconstruir <jugador>          (muestra si esta activo)
 */
public final class Reconstruir {
    static final String CLAVE = "serverpanas_reconstruir";
    static final String CLAVE_DADOS = "serverpanas_reconstruir_dados";
    /** Espacio reservado: ultimo de la mochila (fila de abajo, a la derecha). */
    static final int RESERVADO = 35;

    /** Estado en memoria de cada jugador con el modo activo. */
    private static final class Estado {
        ItemStack copia = ItemStack.EMPTY; // lo que el modo puso en el espacio reservado
        int selAnterior = -1;              // espacio de la barra rapida del tick anterior
        int ranuraUso = -1;                // ranura del bloque que se intento colocar
        ItemStack antesDeUsar = ItemStack.EMPTY;
        boolean avisoLleno = false;
    }

    private final Map<UUID, Estado> estados = new HashMap<>();

    // ---------------------------------------------------------------- datos guardados

    private static CompoundTag persistido(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static boolean activo(Player p) {
        return persistido(p).getBoolean(CLAVE);
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
        CompoundTag t = persistido(p);
        if (si) {
            t.putBoolean(CLAVE, true);
            p.displayClientMessage(Component.literal("§aModo reconstruccion activado: §fla rueda del raton te da el bloque que miras y colocar no gasta bloques."), false);
        } else {
            retirarCopias(p);
            t.remove(CLAVE);
            estados.remove(p.getUUID());
            p.displayClientMessage(Component.literal("§eModo reconstruccion desactivado."), false);
        }
        c.getSource().sendSuccess(() -> Component.literal("Modo reconstruccion " + (si ? "activado" : "desactivado")
                + " para " + p.getGameProfile().getName()), true);
        return 1;
    }

    /** Quita del inventario las copias que entrego el modo (hasta la cantidad registrada). */
    private void retirarCopias(ServerPlayer p) {
        CompoundTag t = persistido(p);
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
        inv.setChanged();
    }

    private static void anotarDado(Player p, ItemStack s) {
        CompoundTag t = persistido(p);
        CompoundTag dados = t.getCompound(CLAVE_DADOS);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return;
        dados.putInt(id.toString(), dados.getInt(id.toString()) + s.getCount());
        t.put(CLAVE_DADOS, dados);
    }

    // ---------------------------------------------------------------- eventos

    /** Recuerda el bloque que se va a colocar para devolverlo despues. */
    @SubscribeEvent
    public void alUsarEnBloque(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !activo(p)) return;
        ItemStack s = e.getItemStack();
        if (!(s.getItem() instanceof BlockItem)) return;
        Estado st = estados.computeIfAbsent(p.getUUID(), k -> new Estado());
        st.ranuraUso = e.getHand() == InteractionHand.MAIN_HAND ? p.getInventory().selected : 40;
        st.antesDeUsar = s.copy();
    }

    @SubscribeEvent
    public void alSalir(PlayerEvent.PlayerLoggedOutEvent e) {
        estados.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void alTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!activo(p)) return;
        Estado st = estados.computeIfAbsent(p.getUUID(), k -> new Estado());
        Inventory inv = p.getInventory();

        devolverBloqueColocado(inv, st);
        corregirRueda(p, inv, st);
        prepararBloqueMirado(p, inv, st);

        st.selAnterior = inv.selected;
    }

    /** Colocar no gasta: si el bloque usado bajo de cantidad, se repone. */
    private void devolverBloqueColocado(Inventory inv, Estado st) {
        if (st.ranuraUso < 0) return;
        ItemStack ahora = inv.getItem(st.ranuraUso);
        ItemStack antes = st.antesDeUsar;
        if (ahora.isEmpty() || (ItemStack.isSameItemSameTags(ahora, antes) && ahora.getCount() < antes.getCount())) {
            inv.setItem(st.ranuraUso, antes);
        }
        st.ranuraUso = -1;
        st.antesDeUsar = ItemStack.EMPTY;
    }

    /**
     * La rueda de supervivencia lleva el bloque a un espacio VACIO de la barra
     * si lo hay. Aqui se mueve al espacio en el que estaba el jugador.
     */
    private void corregirRueda(ServerPlayer p, Inventory inv, Estado st) {
        if (st.copia.isEmpty()) return;
        if (ItemStack.isSameItemSameTags(inv.getItem(RESERVADO), st.copia)) return;
        int sel = inv.selected;
        int ant = st.selAnterior;
        if (ant >= 0 && ant != sel && ItemStack.isSameItemSameTags(inv.getItem(sel), st.copia)) {
            ItemStack enAnt = inv.getItem(ant);
            inv.setItem(ant, inv.getItem(sel));
            inv.setItem(sel, enAnt);
            inv.selected = ant;
            p.connection.send(new ClientboundSetCarriedItemPacket(ant));
        }
        st.copia = ItemStack.EMPTY;
    }

    /** Deja en el espacio reservado una copia del bloque que el jugador mira. */
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
        bloque.setCount(1);

        // Ya lo tiene: la rueda funciona sola.
        if (inv.findSlotMatchingItem(bloque) >= 0) return;

        ItemStack enReservado = inv.getItem(RESERVADO);
        boolean esCopia = !st.copia.isEmpty() && ItemStack.isSameItemSameTags(enReservado, st.copia);
        if (!enReservado.isEmpty() && !esCopia) {
            // Hay algo del jugador: moverlo a otro espacio libre; si no hay, no tocar nada.
            int libre = -1;
            for (int i = 9; i < 36; i++) if (i != RESERVADO && inv.getItem(i).isEmpty()) { libre = i; break; }
            if (libre < 0) for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) { libre = i; break; }
            if (libre < 0) {
                if (!st.avisoLleno) {
                    p.displayClientMessage(Component.literal("§cReconstruccion: libera un espacio del inventario."), true);
                    st.avisoLleno = true;
                }
                return;
            }
            inv.setItem(libre, enReservado);
        } else if (esCopia) {
            // La copia anterior no se uso: se retira (no cuenta como entregada).
            restarDado(p, st.copia);
        }
        st.avisoLleno = false;
        inv.setItem(RESERVADO, bloque.copy());
        st.copia = bloque.copy();
        anotarDado(p, bloque);
    }

    private static void restarDado(Player p, ItemStack s) {
        CompoundTag t = persistido(p);
        CompoundTag dados = t.getCompound(CLAVE_DADOS);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return;
        int n = dados.getInt(id.toString()) - s.getCount();
        if (n > 0) dados.putInt(id.toString(), n); else dados.remove(id.toString());
        t.put(CLAVE_DADOS, dados);
    }
}
