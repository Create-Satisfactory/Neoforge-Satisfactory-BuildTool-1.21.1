package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SchematicNbtHelper {

    private static final Path CREATE_SCHEMATICS_DIR = Paths.get("schematics");

    /**
     * Strips any appended "{...}" BlockEntity SNBT from a pattern string so BlockState parsers only see "mod:block[props]".
     */
    public static String stripNbt(String blockString) {
        if (blockString == null) return "";
        int braceIdx = blockString.indexOf('{');
        return braceIdx != -1 ? blockString.substring(0, braceIdx) : blockString;
    }

    /**
     * Extracts and parses the appended "{...}" BlockEntity CompoundTag from a pattern string, if present.
     */
    @Nullable
    public static CompoundTag extractNbt(String blockString) {
        if (blockString == null) return null;
        int braceIdx = blockString.indexOf('{');
        if (braceIdx == -1) return null;

        String snbt = blockString.substring(braceIdx);
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.warn("Failed to parse BlockEntity SNBT: {}", e.getMessage());
            return null;
        }
    }

    @Nullable
    public static List<List<List<String>>> loadSchematicPattern(
            @Nullable String schematicName,
            @Nullable File sourceJsonFile,
            @Nullable ResourceLocation sourceResourceLoc,
            @Nullable ResourceManager resourceManager) {

        String targetFileName = schematicName;
        if (targetFileName == null || targetFileName.trim().isEmpty()) {
            if (sourceJsonFile != null) {
                String jsonName = sourceJsonFile.getName();
                targetFileName = jsonName.endsWith(".json")
                        ? jsonName.substring(0, jsonName.length() - 5) + ".nbt"
                        : jsonName + ".nbt";
            } else if (sourceResourceLoc != null) {
                String path = sourceResourceLoc.getPath();
                targetFileName = path.endsWith(".json")
                        ? path.substring(0, path.length() - 5) + ".nbt"
                        : path + ".nbt";
            }
        }

        if (targetFileName == null || targetFileName.isEmpty()) {
            return null;
        }
        if (!targetFileName.toLowerCase().endsWith(".nbt")) {
            targetFileName += ".nbt";
        }

        // 1. Check relative to the source JSON file on disk
        if (sourceJsonFile != null && sourceJsonFile.getParentFile() != null) {
            Path parentDir = sourceJsonFile.getParentFile().toPath();
            Path directPath = parentDir.resolve(targetFileName);
            if (Files.exists(directPath)) {
                return readPatternFromFile(directPath.toFile());
            }
            Path subFolderPath = parentDir.resolve("schematics").resolve(targetFileName);
            if (Files.exists(subFolderPath)) {
                return readPatternFromFile(subFolderPath.toFile());
            }
        }

        // 2. Check server & client config structure directories
        String cleanFileName = Paths.get(targetFileName).getFileName().toString();
        for (Path dir : List.of(
                Paths.get("config/InfinityNexus/structures/server"),
                Paths.get("config/InfinityNexus/structures/server/schematics"),
                Paths.get("config/InfinityNexus/structures/client"),
                CREATE_SCHEMATICS_DIR)) {
            Path candidate = dir.resolve(cleanFileName);
            if (Files.exists(candidate)) {
                return readPatternFromFile(candidate.toFile());
            }
        }

        // 3. Check Mod JAR / Datapack resources
        if (resourceManager != null) {
            List<ResourceLocation> candidates = new ArrayList<>();
            if (targetFileName.contains(":")) {
                candidates.add(ResourceLocation.parse(targetFileName));
            } else {
                for (String ns : List.of(SatisfactoryBuildTool.MOD_ID, "satisfactory")) {
                    candidates.add(ResourceLocation.fromNamespaceAndPath(ns, "structures/" + cleanFileName));
                    candidates.add(ResourceLocation.fromNamespaceAndPath(ns, "schematics/" + cleanFileName));
                    if (!targetFileName.startsWith("structures/") && !targetFileName.startsWith("schematics/")) {
                        candidates.add(ResourceLocation.fromNamespaceAndPath(ns, targetFileName));
                    }
                }
            }

            for (ResourceLocation loc : candidates) {
                Optional<Resource> res = resourceManager.getResource(loc);
                if (res.isPresent()) {
                    try (InputStream is = res.get().open()) {
                        return readPatternFromStream(is);
                    } catch (IOException e) {
                        SatisfactoryBuildTool.LOGGER.error("Failed reading datapack schematic {}: {}", loc, e.getMessage());
                    }
                }
            }
        }

        SatisfactoryBuildTool.LOGGER.error("Could not find Create schematic file: {}", targetFileName);
        return null;
    }

    @Nullable
    public static List<List<List<String>>> readPatternFromFile(File nbtFile) {
        try (InputStream is = new FileInputStream(nbtFile)) {
            return readPatternFromStream(is);
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed reading schematic NBT file {}: {}", nbtFile.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Parses a Create / Vanilla StructureTemplate .nbt stream into a 3D [Y][Z][X] pattern,
     * preserving BlockEntity NBT (Copycats, Basins, Belts, etc.) as appended "{...}" SNBT.
     */
    @Nullable
    public static List<List<List<String>>> readPatternFromStream(InputStream inputStream) throws IOException {
        CompoundTag root = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());

        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() < 3) return null;

        int width = sizeTag.getInt(0);  // X
        int height = sizeTag.getInt(1); // Y
        int length = sizeTag.getInt(2); // Z
        if (width <= 0 || height <= 0 || length <= 0) return null;

        ListTag paletteTag = root.contains("palette", Tag.TAG_LIST)
                ? root.getList("palette", Tag.TAG_COMPOUND)
                : (root.contains("palettes", Tag.TAG_LIST) ? root.getList("palettes", Tag.TAG_LIST).getList(0) : new ListTag());

        String[] paletteStrings = new String[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            paletteStrings[i] = nbtPaletteEntryToStateString(paletteTag.getCompound(i));
        }

        String[][][] grid = new String[height][length][width];
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                Arrays.fill(grid[y][z], " ");
            }
        }

        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockEntry = blocksTag.getCompound(i);
            ListTag posTag = blockEntry.getList("pos", Tag.TAG_INT);
            int stateIdx = blockEntry.getInt("state");

            if (posTag.size() >= 3 && stateIdx >= 0 && stateIdx < paletteStrings.length) {
                int x = posTag.getInt(0);
                int y = posTag.getInt(1);
                int z = posTag.getInt(2);
                if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length) {
                    String baseStateStr = paletteStrings[stateIdx];
                    if (!baseStateStr.equals(" ") && blockEntry.contains("nbt", Tag.TAG_COMPOUND)) {
                        CompoundTag beNbt = blockEntry.getCompound("nbt").copy();
                        beNbt.remove("x");
                        beNbt.remove("y");
                        beNbt.remove("z");
                        if (!beNbt.isEmpty()) {
                            baseStateStr = baseStateStr + beNbt.toString();
                        }
                    }
                    grid[y][z][x] = baseStateStr;
                }
            }
        }

        List<List<List<String>>> pattern = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            List<List<String>> layer = new ArrayList<>(length);
            for (int z = 0; z < length; z++) {
                List<String> row = new ArrayList<>(width);
                row.addAll(Arrays.asList(grid[y][z]).subList(0, width));
                layer.add(row);
            }
            pattern.add(layer);
        }

        return pattern;
    }

    /**
     * Writes a 3D [Y][Z][X] pattern (including any "{...}" BlockEntity NBT) to a Create .nbt schematic file.
     */
    public static boolean writePatternToNbtFile(List<List<List<String>>> pattern, Path outputNbtPath) {
        if (pattern == null || pattern.isEmpty() || pattern.get(0).isEmpty() || pattern.get(0).get(0).isEmpty()) {
            return false;
        }

        int height = pattern.size();
        int length = pattern.get(0).size();
        int width = pattern.get(0).get(0).size();

        Map<String, Integer> paletteIndexMap = new LinkedHashMap<>();
        ListTag paletteTag = new ListTag();
        ListTag blocksTag = new ListTag();

        for (int y = 0; y < height; y++) {
            List<List<String>> layer = pattern.get(y);
            for (int z = 0; z < length && z < layer.size(); z++) {
                List<String> row = layer.get(z);
                for (int x = 0; x < width && x < row.size(); x++) {
                    String fullStr = row.get(x);
                    if (fullStr == null || fullStr.isEmpty() || fullStr.equals(" ")) {
                        continue;
                    }

                    String cleanStateStr = stripNbt(fullStr);
                    CompoundTag beNbt = extractNbt(fullStr);

                    int stateIdx = paletteIndexMap.computeIfAbsent(cleanStateStr, key -> {
                        int idx = paletteTag.size();
                        paletteTag.add(stateStringToNbtPaletteEntry(key));
                        return idx;
                    });

                    CompoundTag blockTag = new CompoundTag();
                    ListTag posList = new ListTag();
                    posList.add(IntTag.valueOf(x));
                    posList.add(IntTag.valueOf(y));
                    posList.add(IntTag.valueOf(z));
                    blockTag.put("pos", posList);
                    blockTag.putInt("state", stateIdx);
                    if (beNbt != null && !beNbt.isEmpty()) {
                        blockTag.put("nbt", beNbt);
                    }
                    blocksTag.add(blockTag);
                }
            }
        }

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());

        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(width));
        sizeTag.add(IntTag.valueOf(height));
        sizeTag.add(IntTag.valueOf(length));
        root.put("size", sizeTag);
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", new ListTag());

        try {
            if (outputNbtPath.getParent() != null && !Files.exists(outputNbtPath.getParent())) {
                Files.createDirectories(outputNbtPath.getParent());
            }
            NbtIo.writeCompressed(root, outputNbtPath);
            return true;
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed writing schematic NBT file {}: {}", outputNbtPath, e.getMessage());
            return false;
        }
    }

    private static String nbtPaletteEntryToStateString(CompoundTag entry) {
        String name = entry.getString("Name");
        if (name.isEmpty() ||
                name.equals("minecraft:air") ||
                name.equals("minecraft:cave_air") ||
                name.equals("minecraft:void_air") ||
                name.equals("minecraft:structure_void")) {
            return " ";
        }

        if (!entry.contains("Properties", Tag.TAG_COMPOUND)) {
            return name;
        }

        CompoundTag props = entry.getCompound("Properties");
        if (props.isEmpty()) {
            return name;
        }

        List<String> propPairs = new ArrayList<>();
        for (String key : props.getAllKeys()) {
            if (Config.list_of_ignored_blockstates != null && Config.list_of_ignored_blockstates.contains(key)) {
                continue;
            }
            propPairs.add(key + "=" + props.getString(key));
        }

        if (propPairs.isEmpty()) {
            return name;
        }
        return name + "[" + String.join(",", propPairs) + "]";
    }

    private static CompoundTag stateStringToNbtPaletteEntry(String stateStr) {
        String clean = stripNbt(stateStr);
        CompoundTag entry = new CompoundTag();
        int bracketIdx = clean.indexOf('[');
        if (bracketIdx == -1) {
            entry.putString("Name", clean);
            return entry;
        }

        String blockName = clean.substring(0, bracketIdx);
        String propsPart = clean.substring(bracketIdx + 1, clean.length() - 1);
        entry.putString("Name", blockName);

        if (!propsPart.isEmpty()) {
            CompoundTag propsTag = new CompoundTag();
            for (String pair : propsPart.split(",")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    propsTag.putString(kv[0].trim(), kv[1].trim());
                }
            }
            entry.put("Properties", propsTag);
        }
        return entry;
    }
}