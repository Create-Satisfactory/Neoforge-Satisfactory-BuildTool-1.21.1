package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class StructureSaver {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STRUCTURES_DIR = "config/InfinityNexus/structures/client/";

    public static boolean saveStructureFromWorld(Level level, BlockPos pos1, BlockPos pos2,
                                                 String name, String author, String description,
                                                 String category, Player player) {
        try {
            Path dirPath = Paths.get(STRUCTURES_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX());
            int maxY = Math.max(pos1.getY(), pos2.getY());
            int maxZ = Math.max(pos1.getZ(), pos2.getZ());

            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            int length = maxZ - minZ + 1;

            List<List<List<String>>> pattern3D = new ArrayList<>();
            for (int y = minY; y <= maxY; y++) {
                List<List<String>> layer = new ArrayList<>();
                for (int z = minZ; z <= maxZ; z++) {
                    List<String> row = new ArrayList<>();
                    for (int x = minX; x <= maxX; x++) {
                        BlockPos currentPos = new BlockPos(x, y, z);
                        BlockState blockState = level.getBlockState(currentPos);
                        if (blockState.isAir()) {
                            row.add(" ");
                        } else {
                            String stateStr = getBlockStateString(blockState);
                            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(currentPos);
                            if (be != null) {
                                net.minecraft.nbt.CompoundTag beNbt = be.saveWithFullMetadata(level.registryAccess());
                                beNbt.remove("x");
                                beNbt.remove("y");
                                beNbt.remove("z");
                                if (!beNbt.isEmpty()) {
                                    stateStr += beNbt.toString();
                                }
                            }
                            row.add(stateStr);
                        }
                    }
                    layer.add(row);
                }
                pattern3D.add(layer);
            }

            String baseName = name.toLowerCase().replace(" ", "_");
            String schematicFileName = baseName + ".nbt";

            // 1. Write the Create .nbt schematic file
            SchematicNbtHelper.writePatternToNbtFile(pattern3D, dirPath.resolve(schematicFileName));

            List<String> schematicsList = new ArrayList<>(List.of("Default:" + schematicFileName));
            Map<String, List<List<List<String>>>> materialMap = new LinkedHashMap<>();
            materialMap.put("default", pattern3D);

            // 2. Write the metadata JSON file with the "schematics" array
            StructureData structureData = new StructureData(
                    name, author, description, category,
                    null, null, schematicsList,
                    0, null, 0, false, new ArrayList<>(),
                    width, length, height, pattern3D, materialMap
            );

            File outputFile = new File(STRUCTURES_DIR + baseName + ".json");
            try (FileWriter writer = new FileWriter(outputFile)) {
                GSON.toJson(structureData, writer);
            }

            if (level.getServer() != null && player instanceof ServerPlayer serverPlayer) {
                StructureSyncUtils.sendStructureToPlayer(serverPlayer, structureData);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String getBlockStateString(BlockState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.getBlock().builtInRegistryHolder().key().location().toString());
        if (!state.getProperties().isEmpty()) {
            sb.append("[");
            List<String> properties = new ArrayList<>();
            for (Property<?> property : state.getProperties()) {
                properties.add(property.getName() + "=" + getPropertyValue(state, property));
            }
            sb.append(String.join(",", properties));
            sb.append("]");
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String getPropertyValue(BlockState state, Property<T> property) {
        return state.getValue(property).toString();
    }

    public static class StructureData {
        public String name;
        public String author;
        public String description;
        public String category;
        public String guiPath;
        public String iconPath;
        public List<String> schematics;
        public int tier;
        public String subtier;
        public int energyConsumption;
        public boolean onlyItems;
        public List<StructureLoader.RequiredItem> requiredItems;
        public int width;
        public int length;
        public int height;
        public transient List<List<List<String>>> pattern;
        public transient Map<String, List<List<List<String>>>> materialPatterns;

        public StructureData(String name, String author, String description, String category, String guiPath,
                             String iconPath, int tier, String subtier, int energyConsumption, boolean onlyItems,
                             List<StructureLoader.RequiredItem> requiredItems, int width, int length, int height,
                             List<List<List<String>>> pattern) {
            this(name, author, description, category, guiPath, iconPath,
                    new ArrayList<>(List.of("Default:" + (name != null ? name.toLowerCase().replace(" ", "_") + ".nbt" : "structure.nbt"))),
                    tier, subtier, energyConsumption, onlyItems, requiredItems, width, length, height, pattern, new LinkedHashMap<>());
        }

        public StructureData(String name, String author, String description, String category, String guiPath,
                             String iconPath, List<String> schematics, int tier, String subtier, int energyConsumption,
                             boolean onlyItems, List<StructureLoader.RequiredItem> requiredItems,
                             int width, int length, int height, List<List<List<String>>> pattern,
                             Map<String, List<List<List<String>>>> materialPatterns) {
            this.name = name;
            this.author = author;
            this.description = description;
            this.category = category;
            this.guiPath = guiPath;
            this.iconPath = iconPath;
            this.schematics = schematics != null ? new ArrayList<>(schematics) : new ArrayList<>();
            this.tier = tier;
            this.subtier = subtier;
            this.energyConsumption = energyConsumption;
            this.onlyItems = onlyItems;
            this.requiredItems = requiredItems != null ? requiredItems : new ArrayList<>();
            this.width = width;
            this.length = length;
            this.height = height;
            this.pattern = pattern;
            this.materialPatterns = materialPatterns != null ? materialPatterns : new LinkedHashMap<>();
        }

        public String getName() { return name != null ? name : "Unknown"; }
        public String getAuthor() { return author != null ? author : "Unknown"; }
        public String getDescription() { return description != null ? description : ""; }
        public String getCategory() { return category != null ? category : "unknown"; }
        public String getGuiPath() { return guiPath != null ? guiPath : category; }
        public String getIconPath() { return iconPath; }
        public List<String> getSchematics() { return schematics != null ? schematics : new ArrayList<>(); }
        public int getTier() { return tier; }
        public String getSubtier() { return subtier; }
        public int getEnergyConsumption() { return energyConsumption; }
        public boolean isOnlyItems() { return onlyItems; }
        public List<StructureLoader.RequiredItem> getRequiredItems() { return requiredItems; }
        public int getWidth() { return width; }
        public int getLength() { return length; }
        public int getHeight() { return height; }
        public List<List<List<String>>> getPattern() { return pattern != null ? pattern : new ArrayList<>(); }
        public Map<String, List<List<List<String>>>> getMaterialPatterns() {
            return materialPatterns != null ? materialPatterns : new LinkedHashMap<>();
        }
    }
}