package com.blighteddreams.helper.client.brain;

import com.blighteddreams.helper.ai.MiniJson;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-session persistent companion memory for Emily.
 * Tracks shared adventures, player deaths, clumsy blunders, boss encounters,
 * and key facts in a local JSON file so Emily remembers the player over time.
 */
public final class HelperMemory {
    private static final Logger LOGGER = LoggerFactory.getLogger("blighted_enhancements/memory");
    private static final String MEMORY_FILE = "blighted_helper_memory.json";

    private static int playerDeaths = 0;
    private static int clumsyFalls = 0;
    private static int bossKills = 0;
    private static final List<String> defeatedBosses = new ArrayList<>();
    private static boolean loaded = false;

    private HelperMemory() {}

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = getMemoryPath();
        if (!Files.isRegularFile(path)) return;
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Object json = MiniJson.parse(content);
            Double deaths = (Double) MiniJson.path(json, "playerDeaths");
            if (deaths != null) playerDeaths = deaths.intValue();
            Double falls = (Double) MiniJson.path(json, "clumsyFalls");
            if (falls != null) clumsyFalls = falls.intValue();
            Double bosses = (Double) MiniJson.path(json, "bossKills");
            if (bosses != null) bossKills = bosses.intValue();
            LOGGER.info("Loaded Emily memory: {} deaths, {} falls, {} boss kills", playerDeaths, clumsyFalls, bossKills);
        } catch (Exception e) {
            LOGGER.warn("Could not load Emily memory file: {}", e.getMessage());
        }
    }

    public static synchronized void save() {
        Path path = getMemoryPath();
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\n");
            sb.append("  \"playerDeaths\": ").append(playerDeaths).append(",\n");
            sb.append("  \"clumsyFalls\": ").append(clumsyFalls).append(",\n");
            sb.append("  \"bossKills\": ").append(bossKills).append("\n");
            sb.append("}\n");
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not save Emily memory file: {}", e.getMessage());
        }
    }

    public static synchronized void recordDeath() {
        ensureLoaded();
        playerDeaths++;
        save();
    }

    public static synchronized void recordFall() {
        ensureLoaded();
        clumsyFalls++;
        save();
    }

    public static synchronized void recordBossKill(String bossName) {
        ensureLoaded();
        bossKills++;
        if (bossName != null && !defeatedBosses.contains(bossName)) {
            defeatedBosses.add(bossName);
        }
        save();
    }

    public static synchronized int getPlayerDeaths() {
        ensureLoaded();
        return playerDeaths;
    }

    public static synchronized int getClumsyFalls() {
        ensureLoaded();
        return clumsyFalls;
    }

    public static synchronized int getBossKills() {
        ensureLoaded();
        return bossKills;
    }

    /**
     * Generates a 1-sentence shared history context summary for Emily's state block.
     */
    public static synchronized String summary() {
        ensureLoaded();
        if (playerDeaths == 0 && clumsyFalls == 0 && bossKills == 0) {
            return "Shared history: You two are beginning your journey together.";
        }
        StringBuilder sb = new StringBuilder("Shared history: You have seen the player die ");
        sb.append(playerDeaths).append(playerDeaths == 1 ? " time" : " times");
        if (clumsyFalls > 0) {
            sb.append(", take clumsy tumble falls ").append(clumsyFalls).append(clumsyFalls == 1 ? " time" : " times");
        }
        if (bossKills > 0) {
            sb.append(", and together slain ").append(bossKills).append(" great boss").append(bossKills == 1 ? "" : "es");
        }
        sb.append(".");
        return sb.toString();
    }

    private static Path getMemoryPath() {
        return FMLPaths.CONFIGDIR.get().resolve(MEMORY_FILE);
    }
}
