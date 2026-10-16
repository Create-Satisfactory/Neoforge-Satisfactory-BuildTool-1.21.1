package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class StructureLoader {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
            .create();

    private static final Map<String, StructureData> SERVER_STRUCTURES = new HashMap<>();
    private static final Map<String, StructureData> SYNCED_STRUCTURES = new HashMap<>();
    private static final Map<String, StructureData> LOCAL_STRUCTURES = new HashMap<>();

    private static final Path SERVER_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/server");
    private static final Path CLIENT_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/client");

    private static class OptionalTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            if (typeToken.getRawType() != Optional.class) return null;
            return (TypeAdapter<T>) new OptionalTypeAdapter<>(gson, typeToken);
        }
    }

    private static class OptionalTypeAdapter<T> extends TypeAdapter<Optional<T>> {
        private final Gson gson;
        private final TypeToken<T> typeToken;

        public OptionalTypeAdapter(Gson gson, TypeToken<T> typeToken) {
            this.gson = gson;
            this.typeToken = typeToken;
        }

        @Override
        public void write(JsonWriter out, Optional<T> value) throws IOException {
            if (value != null && value.isPresent()) {
                gson.toJson(value.get(), typeToken.getType(), out);
            } else {
                out.nullValue();
            }
        }

        @Override
        public Optional<T> read(JsonReader in) throws IOException {
            T value = gson.fromJson(in, typeToken.getType());
            return Optional.ofNullable(value);
        }
    }

    public static void loadStructures() {
        ResourceManager rm = ServerLifecycleHooks.getCurrentServer() != null
                ? ServerLifecycleHooks.getCurrentServer().getResourceManager()
                : null;
        loadStructures(rm);
    }

    public static void loadStructures(ResourceManager resourceManager) {
        SERVER_STRUCTURES.clear();
        int loadedCount = 0;

        // 1. Load built-in Mod & Datapack structures (data/<namespace>/structures/*.json)
        if (resourceManager != null) {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                    "structures",
                    loc -> loc.getPath().endsWith(".json") &&
                            (loc.getNamespace().equals(SatisfactoryBuildTool.MOD_ID) || loc.getNamespace().equals("satisfactory"))
            );

            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                    StructureData structure = GSON.fromJson(reader, StructureData.class);
                    if (structure != null) {
                        structure.loadSchematics(null, entry.getKey(), resourceManager);
                        if (structure.isValid()) {
                            structure.setFileName(entry.getKey().getPath());
                            structure.setFromServer(true);
                            structure.preprocessBlockStates();
                            SERVER_STRUCTURES.put(structure.getName().toLowerCase(), structure);
                            loadedCount++;
                        }
                    }
                } catch (Exception e) {
                    SatisfactoryBuildTool.LOGGER.error("Error loading datapack structure {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }

        // 2. Load config/InfinityNexus/structures/server/*.json
        try {
            if (!Files.exists(SERVER_STRUCTURES_DIR)) {
                Files.createDirectories(SERVER_STRUCTURES_DIR);
            }

            File structuresDir = SERVER_STRUCTURES_DIR.toFile();
            File[] structureFiles = structuresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if ((structureFiles == null || structureFiles.length == 0) && SERVER_STRUCTURES.isEmpty()) {
                createDefaultTemplate();
                structureFiles = structuresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            }

            if (structureFiles != null) {
                for (File file : structureFiles) {
                    StructureData structure = loadStructureFromFile(file, resourceManager);
                    if (structure != null) {
                        structure.setFromServer(true);
                        SERVER_STRUCTURES.put(structure.getName().toLowerCase(), structure);
                        loadedCount++;
                        SatisfactoryBuildTool.LOGGER.info("Server Structure loaded: {} ({}x{}x{}) with {} material schematics",
                                structure.getName(), structure.getWidth(), structure.getHeight(), structure.getLength(),
                                structure.getMaterialPatterns().size());
                    }
                }
            }
            SatisfactoryBuildTool.LOGGER.info("Loaded {} total server structures", loadedCount);
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Error accessing server structures directory", e);
        }
    }

    /**
     * Generates a default template demonstrating the "schematics": ["Material:file.nbt"] array format.
     */
    private static void createDefaultTemplate() {
        try {
            List<RequiredItem> items = List.of(
                    new RequiredItem("minecraft:stone", 4),
                    new RequiredItem("minecraft:iron_ingot", 2)
            );

            List<List<List<String>>> stonePattern = List.of(
                    List.of(
                            List.of("minecraft:stone", "minecraft:stone", "minecraft:stone"),
                            List.of("minecraft:stone", "minecraft:stone", "minecraft:stone"),
                            List.of("minecraft:stone", "minecraft:stone", "minecraft:stone")
                    )
            );

            List<List<List<String>>> concretePattern = List.of(
                    List.of(
                            List.of("minecraft:gray_concrete", "minecraft:gray_concrete", "minecraft:gray_concrete"),
                            List.of("minecraft:gray_concrete", "minecraft:gray_concrete", "minecraft:gray_concrete"),
                            List.of("minecraft:gray_concrete", "minecraft:gray_concrete", "minecraft:gray_concrete")
                    )
            );

            // 1. Write the Create .nbt schematic files
            SchematicNbtHelper.writePatternToNbtFile(stonePattern, SERVER_STRUCTURES_DIR.resolve("template_foundation_stone.nbt"));
            SchematicNbtHelper.writePatternToNbtFile(concretePattern, SERVER_STRUCTURES_DIR.resolve("template_foundation_concrete.nbt"));

            // 2. Write the metadata JSON with the "material:nbt" array
            List<String> schematicsList = List.of(
                    "Stone:template_foundation_stone.nbt",
                    "Concrete:template_foundation_concrete.nbt"
            );

            StructureData template = new StructureData(
                    "Template Foundation",
                    "FICSIT",
                    "A reference structure showing the material:nbt array format.",
                    "Foundations",
                    "Foundations > Foundations",
                    "minecraft:stone",
                    schematicsList,
                    0,
                    "",
                    0,
                    true,
                    items,
                    stonePattern
            );

            File templateFile = new File(SERVER_STRUCTURES_DIR.toFile(), "template_foundation.json");
            try (FileWriter writer = new FileWriter(templateFile)) {
                GSON.toJson(template, writer);
            }
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to create default template", e);
        }
    }

    public static void loadClientStructures() {
        LOCAL_STRUCTURES.clear();
        try {
            if (!Files.exists(CLIENT_STRUCTURES_DIR)) {
                Files.createDirectories(CLIENT_STRUCTURES_DIR);
                return;
            }
            File[] structureFiles = CLIENT_STRUCTURES_DIR.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (structureFiles == null) return;

            for (File file : structureFiles) {
                StructureData structure = loadStructureFromFile(file, null);
                if (structure != null) {
                    structure.setFromLocal(true);
                    LOCAL_STRUCTURES.put(structure.getName().toLowerCase(), structure);
                }
            }
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Error accessing client structures directory", e);
        }
    }

    public static StructureData loadStructureFromFile(File file, ResourceManager resourceManager) {
        try (FileReader reader = new FileReader(file)) {
            StructureData structure = GSON.fromJson(reader, StructureData.class);
            if (structure != null) {
                structure.loadSchematics(file, null, resourceManager);
                if (structure.isValid()) {
                    structure.setFileName(file.getName());
                    structure.preprocessBlockStates();
                    return structure;
                } else {
                    SatisfactoryBuildTool.LOGGER.error("Invalid structure or missing .nbt schematic for file: {}", file.getName());
                }
            }
            return null;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Error reading structure file: {}", file.getName(), e);
            return null;
        }
    }

    public static void addClientStructure(StructureData structure, boolean isOfficial) {
        if (structure != null && structure.isValid()) {
            structure.setFromServer(isOfficial);
            structure.setFromLocal(!isOfficial);
            structure.preprocessBlockStates();
            if (isOfficial) {
                SYNCED_STRUCTURES.put(structure.getName().toLowerCase(), structure);
            } else {
                LOCAL_STRUCTURES.put(structure.getName().toLowerCase(), structure);
            }
        }
    }

    public static StructureData getClientStructure(String name) {
        String key = name.toLowerCase();
        if (SYNCED_STRUCTURES.containsKey(key)) {
            return SYNCED_STRUCTURES.get(key);
        }
        return LOCAL_STRUCTURES.get(key);
    }

    public static StructureData getServerStructure(String name) {
        return SERVER_STRUCTURES.get(name.toLowerCase());
    }

    public static void clearSyncedStructures() {
        SYNCED_STRUCTURES.clear();
    }

    public static List<StructureData> getClientStructures() {
        Map<String, StructureData> merged = new HashMap<>(LOCAL_STRUCTURES);

        net.minecraft.world.entity.player.Player clientPlayer = null;
        try {
            clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
        } catch (Exception ignored) {}

        for (Map.Entry<String, StructureData> entry : SYNCED_STRUCTURES.entrySet()) {
            // Live client-side check so resetting FTB Quests hides re-locked buildings immediately
            if (clientPlayer == null || ProgressionHandler.isStructureUnlocked(clientPlayer, entry.getValue())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }

        List<StructureData> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparing(s -> s.getName().toLowerCase()));
        return list;
    }

    public static List<StructureData> getServerStructures() {
        List<StructureData> list = new ArrayList<>(SERVER_STRUCTURES.values());
        list.sort(Comparator.comparing(s -> s.getName().toLowerCase()));
        return list;
    }

    public static Path getClientStructuresDir() {
        return CLIENT_STRUCTURES_DIR;
    }

    public static class StructureData {
        private String name;
        private String author;
        private String description;
        private String category;
        private String guiPath;
        private String iconPath;

        // Array of "Material:filename.nbt" entries (also accepts "materials" or "material_schematics" in JSON)
        @SerializedName(value = "schematics", alternate = {"materials", "material_schematics"})
        private List<String> schematics = new ArrayList<>();

        // Optional single-schematic fallback field
        private String schematic;

        private int tier = 0;
        private String subtier;
        private int energyConsumption = 0;
        private boolean onlyItems = false;
        private List<RequiredItem> requiredItems = new ArrayList<>();

        // Transient runtime caches (never written into the JSON)
        private transient List<List<List<String>>> pattern;
        private transient Map<String, List<List<List<String>>>> materialPatterns = new LinkedHashMap<>();
        private transient Map<String, BlockState> blockStateCache = new HashMap<>();
        private transient String fileName;
        private transient boolean fromServer = false;
        private transient boolean fromLocal = false;

        public StructureData() {}

        public StructureData(String name, String author, String description, String category, String guiPath,
                             String iconPath, int tier, String subtier, int energyConsumption, boolean onlyItems,
                             List<RequiredItem> requiredItems, List<List<List<String>>> pattern) {
            this(name, author, description, category, guiPath, iconPath,
                    new ArrayList<>(List.of("Default:" + (name != null ? name.toLowerCase().replace(" ", "_") + ".nbt" : "structure.nbt"))),
                    tier, subtier, energyConsumption, onlyItems, requiredItems, pattern);
        }

        public StructureData(String name, String author, String description, String category, String guiPath,
                             String iconPath, List<String> schematics, int tier, String subtier, int energyConsumption,
                             boolean onlyItems, List<RequiredItem> requiredItems, List<List<List<String>>> pattern) {
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
            this.requiredItems = requiredItems;
            this.pattern = pattern;
        }

        /**
         * Parses all "Material:file.nbt" entries in the schematics array and loads each .nbt pattern.
         */
        public void loadSchematics(@Nullable File sourceJsonFile, @Nullable ResourceLocation sourceResourceLoc,
                                   @Nullable ResourceManager resourceManager) {
            if (this.materialPatterns == null) {
                this.materialPatterns = new LinkedHashMap<>();
            } else {
                this.materialPatterns.clear();
            }

            if (this.schematics == null) {
                this.schematics = new ArrayList<>();
            }

            // 1. Load every "Material:file.nbt" entry from the array
            for (String entry : this.schematics) {
                if (entry == null || entry.trim().isEmpty()) continue;

                String matKey = "default";
                String nbtFile = entry.trim();

                int colonIdx = nbtFile.indexOf(':');
                if (colonIdx != -1) {
                    matKey = nbtFile.substring(0, colonIdx).trim().toLowerCase();
                    nbtFile = nbtFile.substring(colonIdx + 1).trim();
                }

                List<List<List<String>>> loaded = SchematicNbtHelper.loadSchematicPattern(
                        nbtFile, sourceJsonFile, sourceResourceLoc, resourceManager
                );
                if (loaded != null && !loaded.isEmpty()) {
                    this.materialPatterns.put(matKey, loaded);
                    if (this.pattern == null || this.pattern.isEmpty()) {
                        this.pattern = loaded; // First valid schematic becomes the default pattern
                    }
                }
            }

            // 2. Fallback to single "schematic" field or "<json_name>.nbt" if the array was empty
            if (this.pattern == null || this.pattern.isEmpty()) {
                List<List<List<String>>> fallback = SchematicNbtHelper.loadSchematicPattern(
                        this.schematic, sourceJsonFile, sourceResourceLoc, resourceManager
                );
                if (fallback != null && !fallback.isEmpty()) {
                    this.pattern = fallback;
                    this.materialPatterns.put("default", fallback);
                    String defaultNbt = (this.schematic != null && !this.schematic.isEmpty())
                            ? this.schematic
                            : getName().toLowerCase().replace(" ", "_") + ".nbt";
                    if (this.schematics.isEmpty()) {
                        this.schematics.add("Default:" + defaultNbt);
                    }
                }
            }
        }

        /**
         * Returns the 3D block pattern for the requested material name (e.g. "Concrete"),
         * or falls back to the default pattern if that material isn't in the array.
         */
        public List<List<List<String>>> getPatternForMaterial(@Nullable String materialName) {
            if (materialName != null && !materialName.isEmpty() && materialPatterns != null && !materialPatterns.isEmpty()) {
                List<List<List<String>>> specific = materialPatterns.get(materialName.trim().toLowerCase());
                if (specific != null && !specific.isEmpty()) {
                    return specific;
                }
            }
            return getPattern();
        }

        /**
         * Returns true if this structure has a dedicated .nbt schematic for the given material name.
         */
        public boolean hasMaterialSchematic(@Nullable String materialName) {
            if (materialName == null || materialName.isEmpty() || materialPatterns == null) return false;
            return materialPatterns.containsKey(materialName.trim().toLowerCase());
        }

        public List<String> getSchematics() {
            if (schematics == null || schematics.isEmpty()) {
                String nbt = (schematic != null && !schematic.isEmpty())
                        ? schematic
                        : getName().toLowerCase().replace(" ", "_") + ".nbt";
                return new ArrayList<>(List.of("Default:" + nbt));
            }
            return schematics;
        }

        public void setSchematics(List<String> schematics) {
            this.schematics = schematics != null ? new ArrayList<>(schematics) : new ArrayList<>();
        }

        public Map<String, List<List<List<String>>>> getMaterialPatterns() {
            if (materialPatterns == null) materialPatterns = new LinkedHashMap<>();
            if (materialPatterns.isEmpty() && pattern != null && !pattern.isEmpty()) {
                materialPatterns.put("default", pattern);
            }
            return materialPatterns;
        }

        public void setMaterialPatterns(Map<String, List<List<List<String>>>> materialPatterns) {
            this.materialPatterns = materialPatterns != null ? new LinkedHashMap<>(materialPatterns) : new LinkedHashMap<>();
            if ((this.pattern == null || this.pattern.isEmpty()) && !this.materialPatterns.isEmpty()) {
                this.pattern = this.materialPatterns.values().iterator().next();
            }
        }

        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileName() { return fileName; }

        public void setFromServer(boolean fromServer) { this.fromServer = fromServer; }
        public boolean isServerStructure() { return fromServer; }

        public void setFromLocal(boolean fromLocal) { this.fromLocal = fromLocal; }
        public boolean isLocalStructure() { return fromLocal; }

        public String getName() { return name != null ? name : "Unknown"; }
        public String getAuthor() { return author != null ? author : "Unknown"; }
        public String getDescription() { return description != null ? description : ""; }
        public String getCategory() { return category != null ? category : "unknown"; }
        public String getGuiPath() { return guiPath != null ? guiPath : getCategory(); }
        public String getIconPath() { return iconPath; }
        public int getTier() { return tier; }
        public String getSubtier() { return subtier; }
        public int getEnergyConsumption() { return energyConsumption; }
        public boolean isOnlyItems() { return onlyItems; }
        public List<RequiredItem> getRequiredItems() { return requiredItems != null ? requiredItems : new ArrayList<>(); }

        public List<List<List<String>>> getPattern() { return pattern != null ? pattern : new ArrayList<>(); }
        public void setPattern(List<List<List<String>>> pattern) { this.pattern = pattern; }

        public List<RequiredItem> getAggregatedCosts() {
            Map<String, Integer> costs = new HashMap<>();
            if (requiredItems != null) {
                for (RequiredItem item : requiredItems) {
                    costs.put(item.getItemId(), costs.getOrDefault(item.getItemId(), 0) + item.getCount());
                }
            }
            if (!onlyItems && pattern != null) {
                for (List<List<String>> layer : pattern) {
                    for (List<String> row : layer) {
                        for (String rawString : row) {
                            String stateString = SchematicNbtHelper.stripNbt(rawString);
                            if (!stateString.equals(" ") && !stateString.isEmpty()) {
                                String itemId = stateString.contains("[")
                                        ? stateString.substring(0, stateString.indexOf("["))
                                        : stateString;
                                costs.put(itemId, costs.getOrDefault(itemId, 0) + 1);
                            }
                        }
                    }
                }
            }
            List<RequiredItem> result = new ArrayList<>();
            costs.forEach((id, count) -> result.add(new RequiredItem(id, count)));
            result.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
            return result;
        }

        /**
         * Pre-processes blockstates across ALL material schematics so switching materials has zero lag.
         */
        public void preprocessBlockStates() {
            if (pattern == null) pattern = new ArrayList<>();
            if (blockStateCache == null) blockStateCache = new HashMap<>();

            Set<String> uniqueStateStrings = new HashSet<>();
            collectUniqueStates(pattern, uniqueStateStrings);

            if (materialPatterns != null) {
                for (List<List<List<String>>> matPattern : materialPatterns.values()) {
                    collectUniqueStates(matPattern, uniqueStateStrings);
                }
            }

            for (String stateString : uniqueStateStrings) {
                BlockState state = parseBlockState(stateString);
                if (state != null) {
                    blockStateCache.put(stateString, state);
                }
            }
        }

        private void collectUniqueStates(List<List<List<String>>> pat, Set<String> out) {
            if (pat == null) return;
            for (List<List<String>> layer : pat) {
                for (List<String> row : layer) {
                    for (String stateString : row) {
                        String clean = SchematicNbtHelper.stripNbt(stateString);
                        if (!clean.equals(" ") && !clean.isEmpty()) {
                            out.add(clean);
                        }
                    }
                }
            }
        }

        public boolean isValid() {
            return name != null && !name.isEmpty() && pattern != null && !pattern.isEmpty();
        }

        public int getHeight() {
            return pattern != null ? pattern.size() : 0;
        }

        public int getWidth() {
            if (pattern == null || pattern.isEmpty() || pattern.get(0).isEmpty()) return 0;
            return pattern.get(0).get(0).size();
        }

        public int getLength() {
            if (pattern == null || pattern.isEmpty()) return 0;
            return pattern.get(0).size();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StructureData that = (StructureData) o;
            return Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName());
        }

        public BlockState getBlockStateForString(String stateString) {
            String clean = SchematicNbtHelper.stripNbt(stateString);
            if (clean.equals(" ") || clean.isEmpty()) return null;
            if (blockStateCache != null && blockStateCache.containsKey(clean)) {
                return blockStateCache.get(clean);
            }
            BlockState state = parseBlockState(clean);
            if (state != null && blockStateCache != null) {
                blockStateCache.put(clean, state);
            }
            return state;
        }

        private BlockState parseBlockState(String stateString) {
            try {
                if (stateString == null || stateString.isEmpty() || stateString.equals(" ")) return null;
                int bracketIndex = stateString.indexOf('[');
                if (bracketIndex == -1) {
                    Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(stateString));
                    return block != null ? block.defaultBlockState() : null;
                }
                String blockId = stateString.substring(0, bracketIndex);
                String propertiesString = stateString.substring(bracketIndex + 1, stateString.length() - 1);
                Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
                if (block == null) return null;

                BlockState state = block.defaultBlockState();
                if (!propertiesString.isEmpty()) {
                    for (String prop : propertiesString.split(",")) {
                        String[] kv = prop.split("=");
                        if (kv.length == 2) {
                            state = applyProperty(state, kv[0].trim(), kv[1].trim());
                        }
                    }
                }
                return state;
            } catch (Exception e) {
                return null;
            }
        }

        private BlockState applyProperty(BlockState state, String propertyName, String value) {
            for (Property<?> prop : state.getProperties()) {
                if (prop.getName().equals(propertyName)) {
                    return setPropertyValue(state, prop, value);
                }
            }
            return state;
        }

        private <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String value) {
            Optional<T> parsedValue = property.getValue(value);
            return parsedValue.map(t -> state.setValue(property, t)).orElse(state);
        }
    }

    public static class RequiredItem {
        private String itemId;
        private int count;

        public RequiredItem(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }

        public String getItemId() { return itemId; }
        public int getCount() { return count; }
    }
}