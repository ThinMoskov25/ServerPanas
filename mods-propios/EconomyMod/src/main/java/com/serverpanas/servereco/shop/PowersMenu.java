package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.data.BalanceData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu de PODERES (cofre vanilla 9x6, solo botones).
 *  - poder == null: lista de poderes con su precio.
 *  - poder != null: cabezas de los compañeros conectados para elegir a quien.
 */
public class PowersMenu extends ChestMenu {

    private static final int RANURAS = 54;
    private static final int SLOT_VOLVER = 45;
    private static final int SLOT_SALDO = 49;
    private static final int[] SLOTS_PODERES = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final Container muestra;
    private final ServerPlayer dueno;
    private final String poder;
    private final Map<Integer, String> poderPorSlot = new HashMap<>();
    private final Map<Integer, UUID> jugadorPorSlot = new HashMap<>();

    public PowersMenu(int id, Inventory inv, ServerPlayer dueno, Container muestra, String poder) {
        super(MenuType.GENERIC_9x6, id, inv, muestra, 6);
        this.muestra = muestra;
        this.dueno = dueno;
        this.poder = poder;
        ((VirtualMenuContainer) muestra).excludeFromSorting();
        MenuUtil.bloquearRanurasSuperiores(this, muestra, RANURAS);
        if (dueno != null) {
            construir();
        }
    }

    public static Container nuevoContenedor() {
        return new VirtualMenuContainer(RANURAS);
    }

    private void construir() {
        for (int i = 0; i < RANURAS; i++) {
            muestra.setItem(i, ItemStack.EMPTY);
        }
        long saldo = BalanceData.get(dueno.server).saldo(dueno);
        if (poder == null) {
            List<Powers.Poder> lista = Powers.activos();
            for (int k = 0; k < lista.size() && k < SLOTS_PODERES.length; k++) {
                Powers.Poder p = lista.get(k);
                int precio = Powers.precio(p.id());
                List<String> lore = new ArrayList<>();
                lore.add("&7" + p.descripcion());
                lore.add("");
                lore.add("&6Precio: &e" + precio + " créditos");
                lore.add(saldo >= precio ? "&aClic para comprar" : "&cNo te alcanza el saldo");
                if (p.conCompanero()) {
                    lore.add("&8Solo se cobra si tu compañero acepta.");
                }
                muestra.setItem(SLOTS_PODERES[k], MenuUtil.boton(p.icono(), "&d&l" + p.nombre(), lore));
                poderPorSlot.put(SLOTS_PODERES[k], p.id());
            }
            if (lista.isEmpty()) {
                muestra.setItem(22, MenuUtil.boton(Items.BARRIER, "&cNo hay poderes disponibles", List.of()));
            }
        } else {
            Powers.Poder p = Powers.PODERES.get(poder);
            int i = 0;
            for (ServerPlayer otro : dueno.server.getPlayerList().getPlayers()) {
                if (otro == dueno || i >= 45) {
                    continue;
                }
                ItemStack cabeza = new ItemStack(Items.PLAYER_HEAD);
                CompoundTag perfil = NbtUtils.writeGameProfile(new CompoundTag(), otro.getGameProfile());
                cabeza.getOrCreateTag().put("SkullOwner", perfil);
                MenuUtil.nombre(cabeza, "&e&l" + otro.getGameProfile().getName());
                MenuUtil.lore(cabeza, List.of(
                        "&7" + (p != null ? p.nombre() : ""),
                        "&6Precio: &e" + Powers.precio(poder) + " créditos",
                        "&aClic para enviarle la solicitud"));
                muestra.setItem(i, cabeza);
                jugadorPorSlot.put(i, otro.getUUID());
                i++;
            }
            if (i == 0) {
                muestra.setItem(22, MenuUtil.boton(Items.BARRIER, "&cNo hay nadie más conectado", List.of()));
            }
        }
        muestra.setItem(SLOT_VOLVER, MenuUtil.boton(Items.ARROW, "&7&lVolver", List.of()));
        muestra.setItem(SLOT_SALDO, MenuUtil.boton(Items.GOLD_NUGGET, "&6&lCreditos: &e" + saldo, List.of()));
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (dueno == null || type != ClickType.PICKUP) {
            return;
        }
        if (slotId == SLOT_VOLVER) {
            if (poder == null) {
                Menus.abrirPrincipal(dueno);
            } else {
                Menus.abrirPoderes(dueno, null);
            }
            return;
        }
        if (poder == null) {
            String id = poderPorSlot.get(slotId);
            Powers.Poder p = id == null ? null : Powers.PODERES.get(id);
            if (p == null) {
                return;
            }
            if (p.conCompanero()) {
                Menus.abrirPoderes(dueno, id);
            } else {
                dueno.closeContainer();
                Powers.usarInmediato(dueno, p);
            }
        } else {
            UUID otro = jugadorPorSlot.get(slotId);
            if (otro != null) {
                dueno.closeContainer();
                Powers.solicitar(dueno, poder, otro);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
