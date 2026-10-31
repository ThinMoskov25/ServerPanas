package com.serverpanas.guideinjector;

import com.serverpanas.guideinjector.model.ModGuide;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Escucha el arranque del servidor y escribe las guias de FTB Quests como
 * archivos .snbt en config/ftbquests/quests/chapters/.
 *
 * SOBRESCRITURA de las guias auto-generadas:
 * Estos archivos (00_guia_auto.snbt y auto_*.snbt) son propiedad EXCLUSIVA de
 * este mod, asi que en cada arranque se REESCRIBEN a partir del contenido del
 * codigo. Esto es necesario porque FTB Quests reescribe/re-guarda sus .snbt al
 * apagar el servidor (serializa su estado en memoria), de modo que un simple
 * "crear solo si no existe" nunca aplicaria una correccion: al reiniciar el
 * archivo ya volvio a existir con el contenido viejo. Reescribir siempre
 * garantiza que cualquier arreglo del codigo (p.ej. el enlace "Ver detalle")
 * tome efecto en el siguiente arranque sin tener que borrar nada a mano.
 *
 * El 00_guia.snbt propio del usuario NUNCA se toca: usamos nombres distintos
 * (00_guia_auto.snbt y capitulos auto_*). Solo se sobrescriben los archivos
 * cuyo nombre genera este mod.
 */
public class GuideInjector {

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            MinecraftServer server = event.getServer();

            // En 1.20.1 (official mappings) getServerDirectory() devuelve un File.
            File serverDir = server.getServerDirectory();
            Path chaptersDir = serverDir.toPath()
                    .resolve("config")
                    .resolve("ftbquests")
                    .resolve("quests")
                    .resolve("chapters");

            Files.createDirectories(chaptersDir);
            GuideInjectorMod.LOG.info("[GuideInjector] Carpeta de capitulos: {}", chaptersDir);

            List<ModGuide> guides = GuideContent.allGuides();

            // Nivel 1: capitulo indice.
            String indexSnbt = SnbtWriter.indexChapter(
                    GuideContent.GROUP_ID,
                    GuideContent.INDEX_CHAPTER_ID,
                    GuideContent.INDEX_COVER_QUEST,
                    GuideContent.INDEX_COVER_TASK,
                    guides);
            writeAlways(chaptersDir, "00_guia_auto.snbt", indexSnbt);

            // Nivel 2: un capitulo detallado (invisible) por cada mod con arbol.
            for (ModGuide g : guides) {
                if (!g.hasTree()) {
                    continue;
                }
                String detailSnbt = SnbtWriter.detailChapter(GuideContent.GROUP_ID, g);
                writeAlways(chaptersDir, g.chapterFilename + ".snbt", detailSnbt);
            }
        } catch (Exception e) {
            // Nunca romper el arranque del servidor por un fallo de guia.
            GuideInjectorMod.LOG.error("[GuideInjector] No se pudieron generar las guias", e);
        }
    }

    /**
     * Escribe (o reescribe) un archivo de guia propiedad de este mod. Siempre
     * sobrescribe: ver la nota de la cabecera de la clase sobre por que no sirve
     * el "crear solo si no existe".
     */
    private void writeAlways(Path dir, String filename, String content) throws IOException {
        Path target = dir.resolve(filename);
        boolean existed = Files.exists(target);
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
        GuideInjectorMod.LOG.info("[GuideInjector] {}: {}",
                existed ? "Reescrito" : "Creado", filename);
    }
}
