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
            // Criar diretório se não existir
            Path dirPath = Paths.get(STRUCTURES_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Determinar os limites da estrutura
            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX());
            int maxY = Math.max(pos1.getY(), pos2.getY());
            int maxZ = Math.max(pos1.getZ(), pos2.getZ());

            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            int length = maxZ - minZ + 1;

            // Coletar blocos e criar padrão com estados completos
            // REMOVER o sistema de chaves e usar diretamente as strings dos estados
            List<List<List<String>>> pattern3D = new ArrayList<>();

            // Para cada camada Y
            for (int y = minY; y <= maxY; y++) {
                List<List<String>> layer = new ArrayList<>();

                // Para cada linha Z
                for (int z = minZ; z <= maxZ; z++) {
                    List<String> row = new ArrayList<>();

                    // Para cada coluna X
                    for (int x = minX; x <= maxX; x++) {
                        BlockPos currentPos = new BlockPos(x, y, z);
                        BlockState blockState = level.getBlockState(currentPos);

                        // Pular blocos de ar
                        if (blockState.isAir()) {
                            row.add(" "); // Usar espaço para ar
                            continue;
                        }

                        // Gerar string do estado completo do bloco
                        String stateString = getBlockStateString(blockState);
                        row.add(stateString);
                    }
                    layer.add(row);
                }
                pattern3D.add(layer);
            }

            // Criar objeto de estrutura sem o sistema de chaves
            StructureData structureData = new StructureData(
                    name,
                    author,
                    description,
                    category,
                    null, // guiPath - will default to category
                    null, // iconPath - NEW
                    0, // Default Tier
                    null, // Default Subtier
                    0, // Default energyConsumption
                    false, // Default onlyItems
                    new ArrayList<>(), // Default RequiredItems
                    width,
                    length,
                    height,
                    pattern3D
            );

            // Salvar como JSON
            String fileName = name.toLowerCase().replace(" ", "_") + ".json";
            File outputFile = new File(STRUCTURES_DIR + fileName);

            try (FileWriter writer = new FileWriter(outputFile)) {
                GSON.toJson(structureData, writer);
            }

            if (level.getServer() != null) {
                if (player != null && player instanceof ServerPlayer serverPlayer) {
                    StructureSyncUtils.sendStructureToPlayer(serverPlayer, structureData);
                }
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Converte BlockState para string no formato "block_id[property1=value1,property2=value2]"
     */
    private static String getBlockStateString(BlockState state) {
        StringBuilder sb = new StringBuilder();

        // Adiciona o ID do bloco
        String blockId = state.getBlock().builtInRegistryHolder().key().location().toString();
        sb.append(blockId);

        // Adiciona as propriedades se existirem
        if (!state.getProperties().isEmpty()) {
            sb.append("[");
            List<String> properties = new ArrayList<>();

            for (Property<?> property : state.getProperties()) {
                String value = getPropertyValue(state, property);
                properties.add(property.getName() + "=" + value);
            }

            sb.append(String.join(",", properties));
            sb.append("]");
        }

        return sb.toString();
    }

    /**
     * Obtém o valor de uma propriedade como string
     */
    private static <T extends Comparable<T>> String getPropertyValue(BlockState state, Property<T> property) {
        return state.getValue(property).toString();
    }

    public static class StructureData {
        public String name;
        public String author;
        public String description;
        public String category;
        public String guiPath; // NEW
        public String iconPath; // NEW
        public int tier;
        public String subtier; // NEW
        public int energyConsumption; // NEW
        public boolean onlyItems;
        public List<StructureLoader.RequiredItem> requiredItems;
        public int width;
        public int length;
        public int height;
        public List<List<List<String>>> pattern; // Novo formato: Y → Z → X com strings completas

        public StructureData(String name, String author, String description, String category, String guiPath, String iconPath, int tier, String subtier, int energyConsumption, boolean onlyItems, List<StructureLoader.RequiredItem> requiredItems, int width, int length, int height,
                             List<List<List<String>>> pattern) {
            this.name = name;
            this.author = author;
            this.description = description;
            this.category = category;
            this.guiPath = guiPath;
            this.iconPath = iconPath;
            this.tier = tier;
            this.subtier = subtier;
            this.energyConsumption = energyConsumption;
            this.onlyItems = onlyItems;
            this.requiredItems = requiredItems != null ? requiredItems : new ArrayList<>();
            this.width = width;
            this.length = length;
            this.height = height;
            this.pattern = pattern;
        }

        // Getters
        public String getName() { return name != null ? name : "Unknown"; }
        public String getAuthor() { return author != null ? author : "Unknown"; }
        public String getDescription() { return description != null ? description : ""; }
        public String getCategory() { return category != null ? category : "unknown"; }
        public String getGuiPath() { return guiPath != null ? guiPath : category; } // NEW
        public String getIconPath() { return iconPath; } // NEW
        public int getTier() { return tier; }
        public String getSubtier() { return subtier; } // NEW
        public int getEnergyConsumption() { return energyConsumption; } // NEW
        public boolean isOnlyItems() { return onlyItems; }
        public List<StructureLoader.RequiredItem> getRequiredItems() { return requiredItems; }
        public int getWidth() { return width; }
        public int getLength() { return length; }
        public int getHeight() { return height; }
        public List<List<List<String>>> getPattern() { return pattern != null ? pattern : new ArrayList<>(); }
    }
}