package com.serverpanas.dailyquests;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Cofre VIRTUAL de misiones, 100% server-side.
 *
 * CLAVE: extiende ChestMenu usando el MenuType VANILLA (ChestMenu.sixRows),
 * NO un MenuType propio. Asi el cliente vanilla lo reconoce como un cofre
 * normal y el mod NO hace falta en el cliente (no rompe IGNORESERVERONLY).
 *
 * Interceptamos clicked() para que el cofre funcione como menu de botones:
 * las ranuras de misiones no se pueden sacar y al pulsarlas se reclama/entrega.
 */
public class QuestMenu extends ChestMenu {

    private final Container contenido;
    private final ServerPlayer dueno;
    private final List<String> questPorRanura = new ArrayList<>();

    public QuestMenu(int id, Inventory invJugador, ServerPlayer dueno, Container contenido) {
        // sixRows usa el MenuType.GENERIC_9x6 VANILLA.
        super(net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, invJugador, contenido, 6);
        this.contenido = contenido;
        this.dueno = dueno;
        for (int i = 0; i < 54; i++) {
            questPorRanura.add(null);
        }
        if (dueno != null) {
            refrescar();
        }
    }

    /** Crea el contenedor (se pasa al super). Se llama antes del constructor. */
    public static Container nuevoContenedor() {
        return new SimpleContainer(54);
    }

    public void refrescar() {
        if (dueno == null) {
            return;
        }
        QuestData data = QuestData.get(dueno.server);
        List<QuestData.Entrada> hoy = data.misionesDeHoy(dueno.getUUID());

        for (int i = 0; i < 54; i++) {
            contenido.setItem(i, ItemStack.EMPTY);
            questPorRanura.set(i, null);
        }

        // Las misiones (diarias + personalizadas) se reparten en las filas
        // interiores del cofre empezando en la ranura 10, saltando de dos en dos
        // y bajando de fila cuando se llena, para que quepan todas sin pisarse.
        //
        // IMPORTANTE (BUG cofre vacio): recorremos TODAS las misiones del jugador
        // (diarias + personalizada). Cada icono se pinta de forma AISLADA: si una
        // mision no resuelve su QuestDef (p.ej. un id antiguo o una personalizada
        // cuyo id no esta en los pools) usamos un icono por defecto en vez de
        // saltarla, y si construir un icono lanzara una excepcion la capturamos
        // por-mision para que NUNCA vacie el cofre entero: las demas se pintan.
        int[] ranuras = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};
        for (int i = 0; i < hoy.size(); i++) {
            QuestData.Entrada e = hoy.get(i);
            int ranura = i < ranuras.length ? ranuras[i] : (10 + i);
            if (ranura >= 54) {
                ranura = i;
            }
            try {
                QuestDef def = QuestRegistry.porId(e.questId);
                ItemStack icono = def != null ? construirIcono(def, e) : iconoPorDefecto(e);
                contenido.setItem(ranura, icono);
                questPorRanura.set(ranura, e.questId);
                // EVIDENCIA EN LOG: id de mision, def resuelta o no, y el icono real
                // que acaba en el cofre. Si "iconoResuelto" es minecraft:paper y la
                // mision NO es DELIVER de paper, es el fallback (bug de id huerfano).
                String iconoDefId = def != null ? def.iconoItem() : "(sin def)";
                ResourceLocation iconoReal = BuiltInRegistries.ITEM.getKey(icono.getItem());
                DailyQuestsMod.LOG.info(
                        "[/misiones icono] jugador={} ranura={} questId={} def={} iconoItem={} -> iconoReal={}",
                        dueno.getGameProfile().getName(), ranura, e.questId,
                        def != null ? "OK" : "NULL(fallback paper)", iconoDefId, iconoReal);
            } catch (Exception ex) {
                DailyQuestsMod.LOG.error("No se pudo pintar la mision '{}' en el cofre: {}",
                        e.questId, ex.toString());
            }
        }
        broadcastChanges();
    }

    /**
     * Icono de respaldo para una mision cuyo id no resuelve su QuestDef en los
     * pools. Evita que una personalizada "huerfana" rompa o vacie el cofre:
     * se muestra con el progreso guardado y un papel como icono.
     */
    private ItemStack iconoPorDefecto(QuestData.Entrada e) {
        ItemStack icono = new ItemStack(Items.PAPER);
        icono.setHoverName(Component.literal("Mision: " + e.questId)
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Progreso: " + e.progreso).withStyle(ChatFormatting.AQUA));
        if (e.reclamada) {
            lore.add(Component.literal("Recompensa YA reclamada").withStyle(ChatFormatting.DARK_GRAY));
        }
        aplicarLore(icono, lore);
        return icono;
    }

    private ItemStack construirIcono(QuestDef def, QuestData.Entrada e) {
        ItemStack icono = itemDesdeId(def.iconoItem());
        boolean completa = e.progreso >= def.cantidad();

        ChatFormatting colorTitulo = e.reclamada ? ChatFormatting.DARK_GRAY
                : completa ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        icono.setHoverName(Component.literal(def.titulo()).withStyle(colorTitulo, ChatFormatting.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(def.descripcion()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Progreso: " + e.progreso + " / " + def.cantidad())
                .withStyle(completa ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        lore.add(Component.literal(barra(e.progreso, def.cantidad())).withStyle(ChatFormatting.DARK_GREEN));
        lore.add(Component.literal(""));
        if (e.reclamada) {
            lore.add(Component.literal("Recompensa YA reclamada").withStyle(ChatFormatting.DARK_GRAY));
        } else if (completa) {
            lore.add(Component.literal("Clic para RECLAMAR la recompensa")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else if (def.tipo() == QuestType.DELIVER) {
            lore.add(Component.literal("Clic para ENTREGAR " + def.objetivo().replace("minecraft:", ""))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            lore.add(Component.literal(def.tipo() == QuestType.MINE ? "Sigue minando" : "Sigue cazando")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        aplicarLore(icono, lore);
        return icono;
    }

    private static String barra(int progreso, int total) {
        int casillas = 20;
        int llenas = total <= 0 ? casillas : Math.min(casillas, Math.round(casillas * (progreso / (float) total)));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < casillas; i++) {
            sb.append(i < llenas ? '|' : '.');
        }
        return sb.append(']').toString();
    }

    private static ItemStack itemDesdeId(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            DailyQuestsMod.LOG.warn("[/misiones icono] id de icono no parseable '{}', uso PAPER", id);
            return new ItemStack(Items.PAPER);
        }
        // En 1.20.1 BuiltInRegistries.ITEM.get(rl) devuelve AIR (no null) para un
        // id desconocido. Comprobamos explicitamente si el registro CONTIENE el
        // id: si no, caemos a PAPER para que al menos se vea algo (y lo logueamos).
        if (!BuiltInRegistries.ITEM.containsKey(rl)) {
            DailyQuestsMod.LOG.warn("[/misiones icono] id de icono '{}' no existe en el pack, uso PAPER", id);
            return new ItemStack(Items.PAPER);
        }
        var item = BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(item == null ? Items.PAPER : item);
    }

    private static void aplicarLore(ItemStack stack, List<Component> lineas) {
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        for (Component c : lineas) {
            lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        display.put("Lore", lore);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // Ranuras del cofre = 0..53. Interceptamos para reclamar/entregar.
        if (dueno != null && slotId >= 0 && slotId < 54) {
            String questId = questPorRanura.get(slotId);
            if (questId != null) {
                QuestEvents.alPulsarMision(dueno, questId);
                refrescar();
            }
            return; // nunca movemos items (no se roban muestras)
        }
        // Las demas ranuras (inventario del jugador) tampoco operan por este menu.
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
