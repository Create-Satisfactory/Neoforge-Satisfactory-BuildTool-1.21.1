package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Objects;

public class StructureLoader {
    // Create Gson with custom adapters to handle Optional and other problematic types
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
            .create();

    private static final Map<String, StructureData> SERVER_STRUCTURES = new HashMap<>(); // Master list (Server)
    private static final Map<String, StructureData> SYNCED_STRUCTURES = new HashMap<>(); // Official synced (Client)
    private static final Map<String, StructureData> LOCAL_STRUCTURES = new HashMap<>();  // Blueprints (Client)
    
    // Paths
    private static final Path SERVER_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/server");
    private static final Path CLIENT_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/client");

    /**
     * Custom TypeAdapterFactory to handle Optional types
     */
    private static class OptionalTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            if (typeToken.getRawType() != Optional.class) {
                return null;
            }

            return (TypeAdapter<T>) new OptionalTypeAdapter<>(gson, typeToken);
        }
    }

    /**
     * Custom TypeAdapter for Optional
     */
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

    /**
     * Carrega todas as estruturas da pasta de estruturas DO SERVIDOR
     * Deve ser chamado apenas no Server Side (ou CommonSetup se for Dedicated, mas agora é ServerStarting)
     */
    public static void loadStructures() {
        SERVER_STRUCTURES.clear();

        try {
            if (!Files.exists(SERVER_STRUCTURES_DIR)) {
                Files.createDirectories(SERVER_STRUCTURES_DIR);
                SatisfactoryBuildTool.LOGGER.info("Diretório de estruturas do servidor criado: {}", SERVER_STRUCTURES_DIR.toString());
                return;
            }

            File structuresDir = SERVER_STRUCTURES_DIR.toFile();
            File[] structureFiles = structuresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (structureFiles == null || structureFiles.length == 0) {
                SatisfactoryBuildTool.LOGGER.warn("Nenhuma estrutura encontrada em: {}. Gerando template...", SERVER_STRUCTURES_DIR.toString());
                createDefaultTemplate();
                structureFiles = structuresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                if (structureFiles == null || structureFiles.length == 0) return;
            }

            int loadedCount = 0;
            for (File file : structureFiles) {
                try {
                    StructureData structure = loadStructureFromFile(file);
                    if (structure != null) {
                        structure.setFromServer(true); // Mark as official
                        SERVER_STRUCTURES.put(structure.getName().toLowerCase(), structure);
                        loadedCount++;
                        SatisfactoryBuildTool.LOGGER.info("Server Structure loaded: {} ({}x{}x{})",
                                structure.getName(), structure.getWidth(), structure.getHeight(), structure.getLength());
                    }
                } catch (Exception e) {
                    SatisfactoryBuildTool.LOGGER.error("Error loading server structure from file: {}", file.getName(), e);
                }
            }

            SatisfactoryBuildTool.LOGGER.info("Loaded {} server structures", loadedCount);

        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Error accessing server structures directory", e);
        }
    }

    /**
     * Gera um arquivo de template com todas as variáveis possíveis para referência do usuário.
     */
    private static void createDefaultTemplate() {
        try {
            List<RequiredItem> items = new ArrayList<>();
            items.add(new RequiredItem("minecraft:stone", 4));
            items.add(new RequiredItem("minecraft:iron_ingot", 2));

            List<List<List<String>>> pattern = new ArrayList<>();
            List<List<String>> layer = new ArrayList<>();
            List<String> row = new ArrayList<>();
            row.add("minecraft:stone");
            row.add("minecraft:stone");
            row.add("minecraft:stone");
            layer.add(new ArrayList<>(row));
            layer.add(new ArrayList<>(row));
            layer.add(new ArrayList<>(row));
            pattern.add(layer);

            StructureData template = new StructureData(
                "Template Foundation",
                "Satiscraftory",
                "A reference structure showing all available fields and how to use them.",
                "Foundations",
                "Foundations > Stone",
                "satisfactory_build_tool:textures/gui/icons/factory_default.png",
                0,
                "",
                0,
                false,
                items,
                pattern
            );

            File templateFile = new File(SERVER_STRUCTURES_DIR.toFile(), "template_foundation.json");
            try (FileWriter writer = new FileWriter(templateFile)) {
                GSON.toJson(template, writer);
                SatisfactoryBuildTool.LOGGER.info("Default template created: {}", templateFile.getAbsolutePath());
            }
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to create default template", e);
        }
    }

    /**
     * Carrega todas as estruturas da pasta de estruturas DO CLIENTE
     * Chamado no lado do cliente para carregar cache local e blueprints
     */
    public static void loadClientStructures() {
        LOCAL_STRUCTURES.clear();

        try {
            if (!Files.exists(CLIENT_STRUCTURES_DIR)) {
                Files.createDirectories(CLIENT_STRUCTURES_DIR);
                return;
            }

            File structuresDir = CLIENT_STRUCTURES_DIR.toFile();
            File[] structureFiles = structuresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (structureFiles == null || structureFiles.length == 0) {
                return;
            }

            int loadedCount = 0;
            
            for (File file : structureFiles) {
                try {
                    StructureData structure = loadStructureFromFile(file);
                    if (structure != null) {
                        structure.setFromLocal(true);
                        LOCAL_STRUCTURES.put(structure.getName().toLowerCase(), structure);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    SatisfactoryBuildTool.LOGGER.error("Error loading client structure from file: {}", file.getName(), e);
                }
            }

            SatisfactoryBuildTool.LOGGER.info("Loaded {} client structures from local cache", loadedCount);

        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Error accessing client structures directory", e);
        }
    }

    /**
     * Carrega uma estrutura individual de um arquivo JSON
     */
    private static StructureData loadStructureFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            StructureData structure = GSON.fromJson(reader, StructureData.class);

            if (structure != null && structure.isValid()) {
                // Set the source filename
                structure.setFileName(file.getName());
                // Pré-processa os estados dos blocos para melhor performance
                structure.preprocessBlockStates();
                return structure;
            } else {
                SatisfactoryBuildTool.LOGGER.error("Estrutura inválida no arquivo: {}", file.getName());
                return null;
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Erro ao ler arquivo de estrutura: {}", file.getName(), e);
            return null;
        }
    }

    // ... loadStructureFromFile ...

    /**
     * Adiciona uma estrutura ao mapa do CLIENTE
     * Chamado quando o cliente recebe um pacote de estrutura
     */
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
            SatisfactoryBuildTool.LOGGER.info("Client Structure added/updated: {} (Official: {})", structure.getName(), isOfficial);
        }
    }
    
    /**
     * Obtém uma estrutura (Preferência para Server se existir, senão Client?)
     * Na verdade, o uso depende do contexto.
     * BuildTool (Client) deve usar getClientStructure.
     * Sync (Server) deve usar getServerStructure.
     */
     
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
    
    /**
     * Helper antigo para compatibilidade, mas idealmente deve ser substituído por chamadas explícitas.
     * Mantendo para não quebrar código existente imediatamente, mas redirecionando.
     * SE for chamado no Server Thread, retorna Server. Se Client, retorna Client?
     * É difícil saber o thread context aqui de forma segura sem `DistExecutor`.
     * Vamos assumir que quem chama sabe o que quer, ou mudamos os nomes.
     * Vou mudar o nome do método original para forçar refatoração correta.
     */

    public static List<StructureData> getClientStructures() {
        Map<String, StructureData> merged = new HashMap<>(LOCAL_STRUCTURES);
        merged.putAll(SYNCED_STRUCTURES); // Official structures overwrite local ones with same name
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

    /**
     * Classe que representa os dados de uma estrutura
     */
    public static class StructureData {
        private String name;
        private String author;
        private String description;
        private String category;
        private String guiPath; // NEW
        private String iconPath; // NEW
        private int tier = 0;
        private String subtier; // NEW
        private int energyConsumption = 0; // NEW
        private boolean onlyItems = false;
        private List<RequiredItem> requiredItems = new ArrayList<>();
        private List<List<List<String>>> pattern; // Novo formato: Y → Z → X com strings completas

        private transient Map<String, BlockState> blockStateCache = new HashMap<>();
        private transient String fileName; // NEW: Track the source filename for deletion/editing
        private transient boolean fromServer = false; // NEW: Flag to identify official structures
        private transient boolean fromLocal = false;  // NEW: Flag to identify user blueprints

        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileName() { return fileName; }
        
        public void setFromServer(boolean fromServer) { this.fromServer = fromServer; }
        public boolean isServerStructure() { return fromServer; }
        
        public void setFromLocal(boolean fromLocal) { this.fromLocal = fromLocal; }
        public boolean isLocalStructure() { return fromLocal; }

        // Default constructor for Gson
        public StructureData() {
        }
        
        public StructureData(String name, String author, String description, String category, String guiPath, String iconPath, int tier, String subtier, int energyConsumption, boolean onlyItems, List<RequiredItem> requiredItems, List<List<List<String>>> pattern) {
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
            this.requiredItems = requiredItems;
            this.pattern = pattern;
        }

        // Getters
        public String getName() { return name != null ? name : "Unknown"; }
        public String getAuthor() { return author != null ? author : "Unknown"; }
        public String getDescription() { return description != null ? description : ""; }
        public String getCategory() { return category != null ? category : "unknown"; }
        public String getGuiPath() { return guiPath != null ? guiPath : getCategory(); } // Default to category
        public String getIconPath() { return iconPath; } // NEW
        public int getTier() { return tier; }
        public String getSubtier() { return subtier; } // NEW
        public int getEnergyConsumption() { return energyConsumption; } // NEW
        public boolean isOnlyItems() { return onlyItems; }
        public List<RequiredItem> getRequiredItems() { return requiredItems != null ? requiredItems : new ArrayList<>(); }
        public List<List<List<String>>> getPattern() { return pattern != null ? pattern : new ArrayList<>(); }

        public List<RequiredItem> getAggregatedCosts() {
            Map<String, Integer> costs = new HashMap<>();
            
            // Add required items
            if (requiredItems != null) {
                for (RequiredItem item : requiredItems) {
                    costs.put(item.getItemId(), costs.getOrDefault(item.getItemId(), 0) + item.getCount());
                }
            }
            
            // Add blocks from pattern if not onlyItems
            if (!onlyItems && pattern != null) {
                for (List<List<String>> layer : pattern) {
                    for (List<String> row : layer) {
                        for (String stateString : row) {
                            if (!stateString.equals(" ") && !stateString.isEmpty()) {
                                // Extract item ID from state string (e.g. "minecraft:stone" or "minecraft:furnace[facing=north]")
                                String itemId = stateString;
                                if (stateString.contains("[")) {
                                    itemId = stateString.substring(0, stateString.indexOf("["));
                                }
                                costs.put(itemId, costs.getOrDefault(itemId, 0) + 1);
                            }
                        }
                    }
                }
            }
            
            List<RequiredItem> result = new ArrayList<>();
            costs.forEach((id, count) -> result.add(new RequiredItem(id, count)));
            // Sort by count descending
            result.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
            return result;
        }

        /**
         * Pré-processa os estados dos blocos para cache
         */
        public void preprocessBlockStates() {
            if (pattern == null) {
                pattern = new ArrayList<>();
            }

            if (blockStateCache == null) {
                blockStateCache = new HashMap<>();
            }

            // Processa todas as strings únicas no padrão
            Set<String> uniqueStateStrings = new HashSet<>();
            for (List<List<String>> layer : pattern) {
                for (List<String> row : layer) {
                    for (String stateString : row) {
                        if (!stateString.equals(" ") && !stateString.isEmpty()) {
                            uniqueStateStrings.add(stateString);
                        }
                    }
                }
            }

            // Pré-processa cada string única
            for (String stateString : uniqueStateStrings) {
                BlockState state = parseBlockState(stateString);
                if (state != null) {
                    blockStateCache.put(stateString, state);
                }
            }
        }

        /**
         * Valida se a estrutura tem todos os dados necessários
         */
        public boolean isValid() {
            return name != null && !name.isEmpty() &&
                    pattern != null && !pattern.isEmpty();
        }

        /**
         * Obtém a altura da estrutura (número de camadas)
         */
        public int getHeight() {
            return pattern != null ? pattern.size() : 0;
        }

        /**
         * Obtém a largura da estrutura
         */
        public int getWidth() {
            if (pattern == null || pattern.isEmpty() || pattern.get(0).isEmpty()) {
                return 0;
            }
            return pattern.get(0).get(0).size();
        }

        /**
         * Obtém o comprimento da estrutura
         */
        public int getLength() {
            if (pattern == null || pattern.isEmpty()) {
                return 0;
            }
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

        /**
         * Obtém o estado do bloco para uma string específica
         */
        public BlockState getBlockStateForString(String stateString) {
            if (stateString.equals(" ") || stateString.isEmpty()) {
                return null; // Ar
            }

            if (blockStateCache != null && blockStateCache.containsKey(stateString)) {
                return blockStateCache.get(stateString);
            }

            // Se não está no cache, analisa e adiciona
            BlockState state = parseBlockState(stateString);
            if (state != null && blockStateCache != null) {
                blockStateCache.put(stateString, state);
            }

            return state;
        }


        /**
         * Analisa a string do estado do bloco
         */
        private BlockState parseBlockState(String stateString) {
            try {
                if (stateString == null || stateString.isEmpty() || stateString.equals(" ")) {
                    return null;
                }

                // Verifica se tem propriedades
                int bracketIndex = stateString.indexOf('[');

                if (bracketIndex == -1) {
                    // Apenas ID do bloco, sem propriedades
                    Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(stateString));
                    return block != null ? block.defaultBlockState() : null;
                }

                // Extrai ID do bloco e propriedades
                String blockId = stateString.substring(0, bracketIndex);
                String propertiesString = stateString.substring(bracketIndex + 1, stateString.length() - 1);

                Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
                if (block == null) {
                    SatisfactoryBuildTool.LOGGER.error("Bloco não encontrado: {}", blockId);
                    return null;
                }

                BlockState state = block.defaultBlockState();

                // Aplica as propriedades
                if (!propertiesString.isEmpty()) {
                    String[] properties = propertiesString.split(",");
                    for (String prop : properties) {
                        String[] keyValue = prop.split("=");
                        if (keyValue.length == 2) {
                            String propName = keyValue[0];
                            String propValue = keyValue[1];

                            state = applyProperty(state, propName, propValue);
                        }
                    }
                }

                return state;

            } catch (Exception e) {
                SatisfactoryBuildTool.LOGGER.error("Erro ao analisar estado do bloco: {}", stateString, e);
                return null;
            }
        }

        /**
         * Aplica uma propriedade ao estado do bloco
         */
        private BlockState applyProperty(BlockState state, String propertyName, String value) {
            for (Property<?> prop : state.getProperties()) {
                if (prop.getName().equals(propertyName)) {
                    return setPropertyValue(state, prop, value);
                }
            }
            return state;
        }

        /**
         * Define o valor de uma propriedade
         */
        private <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String value) {
            Optional<T> parsedValue = property.getValue(value);
            if (parsedValue.isPresent()) {
                return state.setValue(property, parsedValue.get());
            }
            return state;
        }

        @Override
        public String toString() {
            return String.format("Structure{name='%s', category='%s', tier=%d, subtier='%s', size=%dx%dx%d}",
                    getName(), getCategory(), getTier(), getSubtier(), getWidth(), getLength(), getHeight());
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