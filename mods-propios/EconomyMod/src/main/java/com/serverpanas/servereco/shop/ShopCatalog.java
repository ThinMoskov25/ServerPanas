package com.serverpanas.servereco.shop;

import com.serverpanas.servereco.economy.EcoConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogo de la tienda: precio de COMPRA en creditos por unidad.
 *
 * Precios EXIGENTES a proposito (comprar de vuelta cuesta esfuerzo real).
 * Referencia de valor: 1 diamante = 100 creditos, 1 esmeralda = 20.
 * Un jugador solo ve en /shop los items que ya descubrio.
 *
 * ADMIN EN CALIENTE: el catalogo del codigo ({@link #PRECIOS}) es el punto de
 * partida, pero el admin puede AnADIR, CAMBIAR o QUITAR items desde el panel.
 * Esos ajustes viven en la seccion "catalogoCompra" de valores.json
 * (modid:item -> precio) y se aplican con /tienda reload:
 *   - precio > 0  -> en venta a ese precio (anade o reemplaza al del codigo).
 *   - precio <= 0 -> quitado del catalogo (aunque el codigo lo incluya).
 * Asi "editar cualquier item de la tienda" incluye el catalogo base de compra.
 */
public final class ShopCatalog {

    /** Item -> precio de compra (creditos) por 1 unidad. */
    public static final Map<Item, Integer> PRECIOS = new LinkedHashMap<>();

    static {
        // Bloques basicos (caros para desincentivar comprar lo que se farmea facil)
        PRECIOS.put(Items.OAK_LOG, 40);
        PRECIOS.put(Items.STONE, 25);
        PRECIOS.put(Items.COBBLESTONE, 20);
        PRECIOS.put(Items.DIRT, 10);
        PRECIOS.put(Items.SAND, 30);
        PRECIOS.put(Items.GLASS, 60);

        // Recursos
        PRECIOS.put(Items.IRON_INGOT, 150);
        PRECIOS.put(Items.GOLD_INGOT, 120);
        PRECIOS.put(Items.COAL, 50);
        PRECIOS.put(Items.REDSTONE, 40);
        PRECIOS.put(Items.LAPIS_LAZULI, 45);
        PRECIOS.put(Items.DIAMOND, 1200);        // muy caro: 12 diamantes de valor
        PRECIOS.put(Items.EMERALD, 300);
        PRECIOS.put(Items.NETHERITE_SCRAP, 4000);

        // Comida
        PRECIOS.put(Items.BREAD, 35);
        PRECIOS.put(Items.COOKED_BEEF, 60);
        PRECIOS.put(Items.GOLDEN_APPLE, 2500);
        PRECIOS.put(Items.GOLDEN_CARROT, 400);

        // Utiles / combate
        PRECIOS.put(Items.ENDER_PEARL, 500);
        PRECIOS.put(Items.TNT, 800);
        PRECIOS.put(Items.OBSIDIAN, 350);
        PRECIOS.put(Items.EXPERIENCE_BOTTLE, 250);
        PRECIOS.put(Items.ARROW, 20);
        PRECIOS.put(Items.TORCH, 15);
    }

    private ShopCatalog() {
    }

    /** Clave "modid:item" del item (null si no registrable). */
    private static String clave(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        return rl == null ? null : rl.toString();
    }

    /**
     * Precio EFECTIVO de compra del item teniendo en cuenta el ajuste del admin
     * en valores.json ("catalogoCompra"). Devuelve 0 si NO esta en venta.
     *   - override del admin > 0 -> ese precio.
     *   - override del admin <= 0 -> quitado (0), aunque este en el codigo.
     *   - sin override -> el precio del catalogo del codigo (0 si no esta).
     */
    public static int precioDe(Item item) {
        String id = clave(item);
        if (id != null) {
            Integer ajuste = EcoConfig.get().catalogoCompra.get(id);
            if (ajuste != null) {
                return ajuste > 0 ? ajuste : 0;
            }
        }
        return PRECIOS.getOrDefault(item, 0);
    }

    public static boolean enVenta(Item item) {
        return precioDe(item) > 0;
    }

    /**
     * Catalogo EFECTIVO (Item -> precio) = catalogo del codigo fusionado con el
     * ajuste del admin. Lo usa el menu COMPRAR para listar lo que esta en venta.
     * El orden conserva el del codigo y anade al final los items extra del admin.
     */
    public static Map<Item, Integer> catalogoEfectivo() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        // 1) Catalogo del codigo, respetando/aplicando el ajuste del admin.
        for (Map.Entry<Item, Integer> e : PRECIOS.entrySet()) {
            int precio = precioDe(e.getKey());
            if (precio > 0) {
                out.put(e.getKey(), precio);
            }
        }
        // 2) Items EXTRA que el admin anadio y que no estaban en el codigo.
        for (Map.Entry<String, Integer> e : EcoConfig.get().catalogoCompra.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            out.putIfAbsent(item, e.getValue());
        }
        return out;
    }
}
