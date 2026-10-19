package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class StructureFileManager {

    private static final Path SERVER_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/server");
    private static final Path CLIENT_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/client");

    private final Gson gson;

    public StructureFileManager() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public boolean ensureDirectoryExists(Path directory, String purpose) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                SatisfactoryBuildTool.LOGGER.info("Created {} directory: {}", purpose, directory);
            }
            return true;
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to create {} directory: {}", purpose, directory, e);
            return false;
        }
    }

    public File[] getJsonFiles(Path directory) {
        File dir = directory.toFile();
        return dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
    }

    public StructureLoader.StructureData loadStructureFromFile(File file) {
        return StructureLoader.loadStructureFromFile(file, null);
    }

    public boolean saveStructureToFile(StructureLoader.StructureData structure, Path directory) {
        String baseName = sanitizeFileName(structure.getName());
        return saveStructureToFile(structure, directory, baseName);
    }

    /**
     * Saves a blueprint's .nbt schematic and .json metadata, always updating the "schematics" reference to match.
     */
    public boolean saveStructureToFile(StructureLoader.StructureData structure, Path directory, String fileNameWithoutExtension) {
        try {
            ensureDirectoryExists(directory, "save");

            String cleanBase = sanitizeFileName(fileNameWithoutExtension);
            String nbtFileName = cleanBase + ".nbt";
            String jsonFileName = cleanBase + ".json";

            // CRITICAL FIX: Always update the schematics list to point to the new .nbt filename!
            structure.setSchematics(new ArrayList<>(List.of("Default:" + nbtFileName)));
            structure.setFileName(jsonFileName);

            // Write the .nbt schematic file
            if (structure.getPattern() != null && !structure.getPattern().isEmpty()) {
                SchematicNbtHelper.writePatternToNbtFile(structure.getPattern(), directory.resolve(nbtFileName));
            }

            // Write the .json metadata file
            Path jsonPath = directory.resolve(jsonFileName);
            String json = gson.toJson(structure);
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);

            SatisfactoryBuildTool.LOGGER.info("Saved blueprint JSON and NBT to: {}", jsonPath);
            return true;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save structure: {}", structure.getName(), e);
            return false;
        }
    }

    /**
     * Renames both the .json AND .nbt files for a blueprint and removes the old files.
     */
    public boolean renameStructureFile(String oldName, String newName, Path directory) {
        try {
            String oldBase = sanitizeFileName(oldName);
            String newBase = sanitizeFileName(newName);

            if (oldBase.equals(newBase)) {
                return true;
            }

            // 1. Move/Rename the .json file if it exists
            Path oldJsonPath = directory.resolve(oldBase + ".json");
            Path newJsonPath = directory.resolve(newBase + ".json");
            if (Files.exists(oldJsonPath)) {
                Files.move(oldJsonPath, newJsonPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Move/Rename the .nbt file so the old .nbt file is not left behind
            Path oldNbtPath = directory.resolve(oldBase + ".nbt");
            Path newNbtPath = directory.resolve(newBase + ".nbt");
            if (Files.exists(oldNbtPath)) {
                Files.move(oldNbtPath, newNbtPath, StandardCopyOption.REPLACE_EXISTING);
            }

            SatisfactoryBuildTool.LOGGER.info("Renamed blueprint files from '{}' to '{}'", oldBase, newBase);
            return true;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to rename structure file from {} to {}", oldName, newName, e);
            return false;
        }
    }

    /**
     * Deletes both the .json metadata file and all associated .nbt schematic files.
     */
    public boolean deleteStructureFile(StructureLoader.StructureData structure, Path directory) {
        if (structure == null) return false;

        try {
            // 1. Delete any .nbt files referenced in the structure's schematics list
            for (String entry : structure.getSchematics()) {
                if (entry == null || entry.isBlank()) continue;
                String nbtFile = entry.contains(":") ? entry.substring(entry.indexOf(':') + 1).trim() : entry.trim();
                if (!nbtFile.toLowerCase().endsWith(".nbt")) nbtFile += ".nbt";
                Files.deleteIfExists(directory.resolve(nbtFile));
            }

            // 2. Delete by tracked filename if present
            if (structure.getFileName() != null && !structure.getFileName().isEmpty()) {
                String trackedBase = sanitizeFileName(structure.getFileName());
                Files.deleteIfExists(directory.resolve(trackedBase + ".nbt"));
                if (Files.deleteIfExists(directory.resolve(trackedBase + ".json"))) {
                    StructureLoader.loadClientStructures();
                    return true;
                }
            }

            // 3. Fallback to deleting by structure name
            return deleteStructureFile(structure.getName(), directory);
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to delete structure: {}", structure.getName(), e);
            return false;
        }
    }

    public boolean deleteStructureFile(String structureName, Path directory) {
        try {
            String baseName = sanitizeFileName(structureName);
            Path jsonPath = directory.resolve(baseName + ".json");
            Path nbtPath = directory.resolve(baseName + ".nbt");

            Files.deleteIfExists(nbtPath);
            boolean deletedJson = Files.deleteIfExists(jsonPath);

            if (deletedJson) {
                StructureLoader.loadClientStructures();
                SatisfactoryBuildTool.LOGGER.info("Deleted structure files for: {}", baseName);
                return true;
            }
            return false;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to delete structure file: {}", structureName, e);
            return false;
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        String clean = name.trim().toLowerCase().replace(" ", "_");
        if (clean.endsWith(".json")) clean = clean.substring(0, clean.length() - 5);
        if (clean.endsWith(".nbt")) clean = clean.substring(0, clean.length() - 4);
        return clean;
    }

    public Path getServerStructuresDir() { return SERVER_STRUCTURES_DIR; }
    public Path getClientStructuresDir() { return CLIENT_STRUCTURES_DIR; }
}