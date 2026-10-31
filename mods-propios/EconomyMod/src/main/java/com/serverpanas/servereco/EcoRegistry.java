package com.serverpanas.servereco;

/**
 * Este mod NO registra ningun MenuType propio. Las tiendas y subastas usan el
 * cofre VANILLA (MenuType.GENERIC_9x6 via ChestMenu), de modo que el cliente
 * no necesita el mod. Registrar un MenuType propio romperia el server-only.
 *
 * Se deja la clase para no tocar el resto del arranque; register() no hace nada.
 */
public final class EcoRegistry {

    private EcoRegistry() {
    }

    public static void register() {
        // Sin registros sincronizados: el mod es de verdad solo-servidor.
    }
}
