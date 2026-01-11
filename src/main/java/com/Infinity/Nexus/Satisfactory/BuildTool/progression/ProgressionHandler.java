package com.Infinity.Nexus.Satisfactory.BuildTool.progression;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProgressionHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, PlayerProgression> CACHE = new HashMap<>();
    private static final String FOLDER_PATH = FMLPaths.CONFIGDIR.get().resolve("InfinityNexus/progression").toString();

    public static PlayerProgression getProgression(UUID playerId) {
        if (CACHE.containsKey(playerId)) {
            return CACHE.get(playerId);
        }
        return load(playerId);
    }

    public static PlayerProgression load(UUID playerId) {
        File folder = new File(FOLDER_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, playerId.toString() + ".json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                PlayerProgression progression = GSON.fromJson(reader, PlayerProgression.class);
                if (progression != null) {
                    progression.ensureDefaults(); // Ensure backward compatibility
                    CACHE.put(playerId, progression);
                    return progression;
                }
            } catch (IOException e) {
                SatisfactoryBuildTool.LOGGER.error("Failed to load progression for player {}", playerId, e);
            }
        }

        // Create new if not exists
        PlayerProgression newProgression = new PlayerProgression(playerId);
        CACHE.put(playerId, newProgression);
        save(newProgression);
        return newProgression;
    }

    public static void save(PlayerProgression progression) {
        File folder = new File(FOLDER_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, progression.getPlayer().toString() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(progression, writer);
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save progression for player {}", progression.getPlayer(), e);
        }
    }
    
    public static void clearCache(UUID playerId) {
        CACHE.remove(playerId);
    }
}
