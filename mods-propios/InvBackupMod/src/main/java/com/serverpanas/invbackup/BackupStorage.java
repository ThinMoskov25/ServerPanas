package com.serverpanas.invbackup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Guarda y lee respaldos de inventario en archivos NBT dentro del mundo:
 *   world/inventory_backups/<UUID>/<fecha_hora>.nbt
 *
 * Cada respaldo guarda el inventario completo (incluye armadura y mano
 * secundaria, porque Inventory.save serializa las tres listas: items,
 * armor y offhand) mas datos utiles (nombre y motivo).
 *
 * No usa librerias externas: solo NbtIo y la API nativa de Forge 1.20.1.
 */
public final class BackupStorage {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    /** Cuantos respaldos conservar por jugador (los mas viejos se borran). */
    private static final int MAX_POR_JUGADOR = 40;

    private BackupStorage() {
    }

    /** Carpeta raiz de respaldos, dentro de la carpeta del mundo. */
    public static File carpetaRaiz(MinecraftServer server) {
        File mundo = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        File dir = new File(mundo, "inventory_backups");
        if (!dir.exists() && !dir.mkdirs()) {
            InvBackupMod.LOG.warn("No se pudo crear la carpeta de respaldos: {}", dir);
        }
        return dir;
    }

    public static File carpetaJugador(MinecraftServer server, String uuid) {
        File dir = new File(carpetaRaiz(server), uuid);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Guarda un respaldo del inventario del jugador. Devuelve el archivo o null si fallo. */
    public static File guardar(ServerPlayer player, String motivo) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        try {
            CompoundTag raiz = new CompoundTag();
            raiz.putString("player", player.getGameProfile().getName());
            raiz.putString("uuid", player.getUUID().toString());
            raiz.putString("motivo", motivo);
            raiz.putLong("epoch", System.currentTimeMillis());

            // Inventory.save() escribe items, armadura y mano secundaria en una ListTag.
            ListTag inv = new ListTag();
            player.getInventory().save(inv);
            raiz.put("Inventory", inv);
            raiz.putInt("SelectedSlot", player.getInventory().selected);

            // Cofre de Ender.
            raiz.put("EnderItems", player.getEnderChestInventory().createTag());

            // ForgeCaps: aqui viven Curios (amuletos/anillos/mochila en slot curio),
            // Traveler's Backpack y cualquier otra capability por jugador. Lo tomamos
            // del NBT completo del jugador para no depender de esos mods en compilacion.
            CompoundTag jugadorNbt = player.serializeNBT();
            if (jugadorNbt.contains("ForgeCaps")) {
                raiz.put("ForgeCaps", jugadorNbt.getCompound("ForgeCaps").copy());
            }

            File dir = carpetaJugador(server, player.getUUID().toString());
            String base = LocalDateTime.now().format(FMT);
            File destino = new File(dir, base + ".nbt");
            // Evitar colision si se guardan dos en el mismo segundo.
            int n = 1;
            while (destino.exists()) {
                destino = new File(dir, base + "_" + (n++) + ".nbt");
            }
            NbtIo.writeCompressed(raiz, destino);
            podar(dir);
            InvBackupMod.LOG.info("Respaldo de {} ({}): {}", raiz.getString("player"), motivo, destino.getName());
            return destino;
        } catch (IOException e) {
            InvBackupMod.LOG.error("Error guardando respaldo de {}", player.getGameProfile().getName(), e);
            return null;
        }
    }

    /** Lista los respaldos de un jugador, del mas reciente al mas antiguo. */
    public static List<File> listar(MinecraftServer server, String uuid) {
        File dir = new File(carpetaRaiz(server), uuid);
        File[] archivos = dir.listFiles((d, nombre) -> nombre.endsWith(".nbt"));
        List<File> lista = new ArrayList<>();
        if (archivos != null) {
            lista.addAll(Arrays.asList(archivos));
            lista.sort((a, b) -> b.getName().compareTo(a.getName()));
        }
        return lista;
    }

    /** Lee un respaldo concreto. */
    public static CompoundTag leer(File archivo) throws IOException {
        return NbtIo.readCompressed(archivo);
    }

    /**
     * Restaura TODO el inventario de un respaldo al jugador (sobrescribe el actual):
     * inventario vanilla + armadura + mano secundaria + cofre de Ender +
     * ForgeCaps (Curios: amuletos/anillos/mochila, Traveler's Backpack, etc.).
     */
    public static void restaurar(ServerPlayer player, CompoundTag respaldo) {
        // 1) Si el jugador tiene un contenedor abierto (cofre, mesa de trabajo, etc.),
        //    lo cerramos: cambiar el inventario con un menu abierto deja items fantasma.
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        // 2) Inventario vanilla (items, armadura, mano secundaria).
        Inventory inventario = player.getInventory();
        inventario.clearContent();
        ListTag inv = respaldo.getList("Inventory", 10); // 10 = CompoundTag
        inventario.load(inv);
        if (respaldo.contains("SelectedSlot")) {
            inventario.selected = Math.max(0, Math.min(8, respaldo.getInt("SelectedSlot")));
        }
        inventario.setChanged();

        // 3) Cofre de Ender.
        if (respaldo.contains("EnderItems")) {
            player.getEnderChestInventory().fromTag(respaldo.getList("EnderItems", 10));
        }

        // 4) ForgeCaps: Curios (amuletos, anillos, mochila en slot curio),
        //    Traveler's Backpack y demas capabilities por jugador. Se restauran
        //    mezclando el ForgeCaps guardado en el NBT actual del jugador y
        //    reaplicandolo con deserializeNBT (no acopla el mod a Curios).
        if (respaldo.contains("ForgeCaps")) {
            CompoundTag actual = player.serializeNBT();
            actual.put("ForgeCaps", respaldo.getCompound("ForgeCaps").copy());
            player.deserializeNBT(actual);
        }

        // 5) Forzar el reenvio COMPLETO al cliente. broadcastChanges() solo manda
        //    los slots que "cambiaron" segun el menu, y en 1.20.1 los slots distintos
        //    del seleccionado no siempre se detectan; sendAllDataToRemote() reenvia
        //    el estado entero y evita que el cliente siga viendo lo viejo. Los slots
        //    de Curios los resincroniza el propio Curios al cambiar sus capabilities.
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.sendAllDataToRemote();
        player.getServer().getPlayerList().sendAllPlayerInfo(player);
    }

    /** Deja como maximo MAX_POR_JUGADOR respaldos, borrando los mas viejos. */
    private static void podar(File dir) {
        File[] archivos = dir.listFiles((d, nombre) -> nombre.endsWith(".nbt"));
        if (archivos == null || archivos.length <= MAX_POR_JUGADOR) {
            return;
        }
        Arrays.sort(archivos, (a, b) -> a.getName().compareTo(b.getName()));
        int sobran = archivos.length - MAX_POR_JUGADOR;
        for (int i = 0; i < sobran; i++) {
            if (!archivos[i].delete()) {
                InvBackupMod.LOG.warn("No se pudo borrar respaldo viejo: {}", archivos[i]);
            }
        }
    }
}
