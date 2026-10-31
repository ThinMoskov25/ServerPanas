package com.serverpanas.servereco.economy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tabla de VALORES de la economia fisica. La "moneda" son items reales.
 *
 *   1 pepita de oro        = 1 credito
 *   1 lingote de oro       = 9 creditos
 *   1 esmeralda            = 20 creditos
 *   1 diamante             = 100 creditos
 *   1 bloque de diamante   = 900 creditos
 *
 * El mapa esta ordenado de MAYOR a MENOR valor: asi, al cobrar, se usan
 * primero las monedas grandes (algoritmo tipo "cambio de monedas").
 */
public final class Currency {

    /** Item -> creditos por unidad. Orden: de mayor a menor valor. */
    public static final Map<Item, Integer> VALORES = new LinkedHashMap<>();

    static {
        VALORES.put(Items.DIAMOND_BLOCK, 900);
        VALORES.put(Items.DIAMOND, 100);
        VALORES.put(Items.EMERALD, 20);
        VALORES.put(Items.GOLD_INGOT, 9);
        VALORES.put(Items.GOLD_NUGGET, 1);
    }

    private Currency() {
    }

    /** Valor en creditos de un item de moneda (0 si no es moneda). */
    public static int valorDe(Item item) {
        return VALORES.getOrDefault(item, 0);
    }

    public static boolean esMoneda(Item item) {
        return VALORES.containsKey(item);
    }
}
