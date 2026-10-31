package com.serverpanas.servereco.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * "Billetera fisica": opera directamente sobre el inventario del jugador.
 * Nunca crea dinero de la nada; cuenta y consume items reales de Currency.
 */
public final class Wallet {

    private Wallet() {
    }

    /** Suma el valor en creditos de TODAS las monedas del inventario. */
    public static int totalCreditos(ServerPlayer player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                total += Currency.valorDe(s.getItem()) * s.getCount();
            }
        }
        return total;
    }

    /**
     * Cobra "costo" creditos consumiendo monedas fisicas del inventario.
     * Devuelve true si se pudo pagar. Es ATOMICO: si no alcanza, no toca nada.
     *
     * Estrategia:
     *  1) Verifica que total >= costo (si no, falla sin tocar el inventario).
     *  2) Consume de mayor a menor denominacion sin pasarse; si al final
     *     "sobro-pago" (p.ej. pagar 100 con un diamante para un costo de 90),
     *     devuelve el cambio en monedas pequenas.
     */
    public static boolean cobrar(ServerPlayer player, int costo) {
        if (costo <= 0) {
            return true;
        }
        if (totalCreditos(player) < costo) {
            return false;
        }
        Inventory inv = player.getInventory();

        int restante = costo;
        int pagadoDeMas = 0; // creditos pagados por encima del costo (para dar cambio)

        // Recorremos denominaciones de mayor a menor.
        for (Map.Entry<Item, Integer> den : Currency.VALORES.entrySet()) {
            Item moneda = den.getKey();
            int valor = den.getValue();
            if (restante <= 0) {
                break;
            }
            // Cuantas de esta moneda hacen falta como maximo.
            for (int i = 0; i < inv.getContainerSize() && restante > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || s.getItem() != moneda) {
                    continue;
                }
                // Cuantas usar de esta pila: las que quepan sin dejar negativo
                // el restante mas alla de una unidad (permitimos sobrepago en la
                // ultima moneda y damos cambio).
                int necesarias = (restante + valor - 1) / valor; // techo
                int usar = Math.min(s.getCount(), necesarias);
                if (usar <= 0) {
                    continue;
                }
                int aporte = usar * valor;
                s.shrink(usar);
                restante -= aporte;
                if (restante < 0) {
                    pagadoDeMas = -restante;
                    restante = 0;
                }
            }
        }

        // Si por redondeo quedo algo sin pagar (no deberia, ya validamos total),
        // revertir seria complejo; como validamos total>=costo y damos cambio,
        // restante llega a 0. Damos el cambio si pagamos de mas.
        if (pagadoDeMas > 0) {
            darCreditos(player, pagadoDeMas);
        }
        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
        return restante <= 0;
    }

    /**
     * Entrega "creditos" en monedas fisicas al jugador (de mayor a menor),
     * soltando al suelo lo que no quepa en el inventario.
     */
    public static void darCreditos(ServerPlayer player, int creditos) {
        int restante = creditos;
        for (Map.Entry<Item, Integer> den : Currency.VALORES.entrySet()) {
            int valor = den.getValue();
            if (valor <= 0) {
                continue;
            }
            int cuantas = restante / valor;
            restante -= cuantas * valor;
            entregarPila(player, den.getKey(), cuantas);
        }
        // Lo que sobre (menos de 1 nugget) se pierde; con nugget=1 no sobra nada.
        if (restante > 0) {
            entregarPila(player, Items.GOLD_NUGGET, restante);
        }
    }

    /** Entrega N unidades de un item, en pilas, al inventario o al suelo. */
    public static void entregarPila(ServerPlayer player, Item item, int cantidad) {
        while (cantidad > 0) {
            int lote = Math.min(cantidad, item.getMaxStackSize(new ItemStack(item)));
            ItemStack pila = new ItemStack(item, lote);
            if (!player.getInventory().add(pila)) {
                player.drop(pila, false);
            }
            cantidad -= lote;
        }
        player.inventoryMenu.broadcastChanges();
    }
}
