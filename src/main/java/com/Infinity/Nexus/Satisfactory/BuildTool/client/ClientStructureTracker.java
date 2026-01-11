package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PlacedStructure;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClientStructureTracker {
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("InfinityNexus");
    private static final Path DATA_FILE = CONFIG_DIR.resolve("placed_structures.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<PlacedStructure> placedStructures = new ArrayList<>();

    public record SerializedPlacedStructure(UUID id, String structureName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int startX, int startY, int startZ, float rotation, String levelDim) {}

    public static void load() {
        if (!Files.exists(DATA_FILE)) {
            SatisfactoryBuildTool.LOGGER.info("No placed structures file found at {}", DATA_FILE);
            placedStructures = new ArrayList<>();
            return;
        }

        try (FileReader reader = new FileReader(DATA_FILE.toFile())) {
            List<SerializedPlacedStructure> serializedList = GSON.fromJson(reader, new TypeToken<List<SerializedPlacedStructure>>(){}.getType());
            
            if (serializedList == null) {
                placedStructures = new ArrayList<>();
                return;
            }

            placedStructures = serializedList.stream().map(s -> new PlacedStructure(
                    s.id(),
                    s.structureName(),
                    new BlockPos(s.minX(), s.minY(), s.minZ()),
                    new BlockPos(s.maxX(), s.maxY(), s.maxZ()),
                    new BlockPos(s.startX(), s.startY(), s.startZ()),
                    s.rotation(),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.levelDim()))
            )).collect(Collectors.toList());

            SatisfactoryBuildTool.LOGGER.info("Loaded {} placed structures from disk.", placedStructures.size());
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to load placed structures", e);
            placedStructures = new ArrayList<>();
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            List<SerializedPlacedStructure> serializedList = placedStructures.stream().map(s -> new SerializedPlacedStructure(
                    s.id(),
                    s.structureName(),
                    s.minPos().getX(), s.minPos().getY(), s.minPos().getZ(),
                    s.maxPos().getX(), s.maxPos().getY(), s.maxPos().getZ(),
                    s.startPos().getX(), s.startPos().getY(), s.startPos().getZ(),
                    s.rotation(),
                    s.level().location().toString()
            )).collect(Collectors.toList());

            try (Writer writer = new FileWriter(DATA_FILE.toFile())) {
                GSON.toJson(serializedList, writer);
                SatisfactoryBuildTool.LOGGER.info("Saved {} placed structures to disk.", placedStructures.size());
            }
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save placed structures", e);
        }
    }

    public static void addStructure(PlacedStructure structure) {
        placedStructures.add(structure);
        SatisfactoryBuildTool.LOGGER.info("Added structure: {}", structure.structureName());
        save();
    }

    public static void removeStructure(UUID id) {
        placedStructures.removeIf(s -> s.id().equals(id));
        SatisfactoryBuildTool.LOGGER.info("Removed structure: {}", id);
        save();
    }

    public static List<PlacedStructure> getFormattedStructures(Level level) {
         if (level == null) return new ArrayList<>();
         ResourceKey<Level> dimKey = level.dimension();
         return placedStructures.stream()
                 .filter(s -> s.level().equals(dimKey))
                 .collect(Collectors.toList());
    }

    public static PlacedStructure findStructureAt(BlockPos pos, Level level) {
        ResourceKey<Level> dimKey = level.dimension();
        for (PlacedStructure s : placedStructures) {
            if (s.level().equals(dimKey)) {
                if (pos.getX() >= s.minPos().getX() && pos.getX() <= s.maxPos().getX() &&
                    pos.getY() >= s.minPos().getY() && pos.getY() <= s.maxPos().getY() &&
                    pos.getZ() >= s.minPos().getZ() && pos.getZ() <= s.maxPos().getZ()) {
                    return s;
                }
            }
        }
        return null;
    }
}
