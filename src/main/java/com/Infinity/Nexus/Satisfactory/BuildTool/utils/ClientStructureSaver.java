package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureSyncS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureSaver.StructureData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ClientStructureSaver {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean saveStructureLocally(StructureSyncS2CPacket.StructureData structureData) {
        try {
            List<String> syncedSchematics = new ArrayList<>(structureData.schematics());
            Map<String, List<List<List<String>>>> matPatterns = new LinkedHashMap<>(structureData.materialPatterns());

            StructureData localStructureData = new StructureData(
                    structureData.name(),
                    structureData.author(),
                    structureData.description(),
                    structureData.category(),
                    structureData.guiPath(),
                    structureData.iconPath(),
                    syncedSchematics,
                    structureData.tier(),
                    structureData.subtier(),
                    structureData.energyConsumption(),
                    structureData.onlyItems(),
                    structureData.requiredItems(),
                    getWidth(structureData.pattern()),
                    getLength(structureData.pattern()),
                    getHeight(structureData.pattern()),
                    structureData.pattern(),
                    matPatterns
            );

            // Only write to config/InfinityNexus/structures/client/ if it is a player-created Blueprint (!isOfficial)
            String baseName = structureData.name().toLowerCase().replace(" ", "_");
            String jsonFileName = baseName + ".json";

            if (!structureData.isOfficial()) {
                Path dirPath = StructureLoader.getClientStructuresDir();
                if (!Files.exists(dirPath)) {
                    Files.createDirectories(dirPath);
                }
                String fallbackNbt = baseName + ".nbt";
                SchematicNbtHelper.writePatternToNbtFile(structureData.pattern(), dirPath.resolve(fallbackNbt));

                File outputFile = new File(dirPath.toFile(), jsonFileName);
                try (FileWriter writer = new FileWriter(outputFile)) {
                    GSON.toJson(localStructureData, writer);
                }
            }

            // Register in client memory and refresh the Build Tool list
            StructureLoader.StructureData loaderData = StructureConverter.convertToLoaderFormat(localStructureData);
            if (loaderData != null) {
                loaderData.setFileName(jsonFileName);
                StructureLoader.addClientStructure(loaderData, structureData.isOfficial());
                BuildTool.loadStructures();
            }
            return true;
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed processing synced structure", e);
            return false;
        }
    }

    private static int getWidth(List<List<List<String>>> pattern) {
        if (pattern.isEmpty() || pattern.get(0).isEmpty()) return 0;
        return pattern.get(0).get(0).size();
    }

    private static int getLength(List<List<List<String>>> pattern) {
        if (pattern.isEmpty()) return 0;
        return pattern.get(0).size();
    }

    private static int getHeight(List<List<List<String>>> pattern) {
        return pattern.size();
    }
}