package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitário para validação e processamento de estruturas
 */
public class StructureValidator {
    
    private static final Map<String, BlockState> BLOCK_STATE_CACHE = new HashMap<>();
    
    /**
     * Valida se uma estrutura é válida
     */
    public static boolean isValidStructure(StructureLoader.StructureData structure) {
        if (structure == null) return false;
        
        // Verifica campos obrigatórios
        if (structure.getName() == null || structure.getName().isEmpty()) {
            SatisfactoryBuildTool.LOGGER.warn("Structure validation failed: missing name");
            return false;
        }
        
        if (structure.getPattern() == null || structure.getPattern().isEmpty()) {
            SatisfactoryBuildTool.LOGGER.warn("Structure validation failed: empty pattern");
            return false;
        }
        
        // Valida dimensões
        if (structure.getWidth() <= 0 || structure.getHeight() <= 0 || structure.getLength() <= 0) {
            SatisfactoryBuildTool.LOGGER.warn("Structure validation failed: invalid dimensions");
            return false;
        }
        
        // Valida padrão
        if (!isValidPattern(structure)) {
            SatisfactoryBuildTool.LOGGER.warn("Structure validation failed: invalid pattern format");
            return false;
        }
        
        return true;
    }
    
    /**
     * Valida o formato do padrão da estrutura
     */
    private static boolean isValidPattern(StructureLoader.StructureData structure) {
        List<List<List<String>>> pattern = structure.getPattern();
        
        if (pattern.size() != structure.getHeight()) {
            SatisfactoryBuildTool.LOGGER.warn("Pattern height mismatch: expected {}, got {}", 
                                            structure.getHeight(), pattern.size());
            return false;
        }
        
        for (int y = 0; y < pattern.size(); y++) {
            List<List<String>> layer = pattern.get(y);
            
            if (layer.size() != structure.getLength()) {
                SatisfactoryBuildTool.LOGGER.warn("Pattern length mismatch at layer {}: expected {}, got {}", 
                                                y, structure.getLength(), layer.size());
                return false;
            }
            
            for (int z = 0; z < layer.size(); z++) {
                List<String> row = layer.get(z);
                
                if (row.size() != structure.getWidth()) {
                    SatisfactoryBuildTool.LOGGER.warn("Pattern width mismatch at layer {}, row {}: expected {}, got {}", 
                                                    y, z, structure.getWidth(), row.size());
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Pré-processa e valida os estados dos blocos
     */
    public static void preprocessBlockStates(StructureLoader.StructureData structure) {
        BLOCK_STATE_CACHE.clear();
        
        // Processa blocos individuais no padrão
        List<List<List<String>>> pattern = structure.getPattern();
        for (List<List<String>> layer : pattern) {
            for (List<String> row : layer) {
                for (String blockChar : row) {
                    if (!blockChar.equals(" ") && !BLOCK_STATE_CACHE.containsKey(blockChar)) {
                        BlockState state = parseBlockState(blockChar);
                        if (state != null) {
                            BLOCK_STATE_CACHE.put(blockChar, state);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Converte string para BlockState
     */
    public static BlockState parseBlockState(String blockString) {
        if (blockString == null || blockString.isEmpty()) {
            return null;
        }
        
        try {
            // Remove espaços e converte para minúsculo
            blockString = blockString.trim().toLowerCase();
            
            // Separa nome do bloco das propriedades
            String[] parts = blockString.split("\\[");
            String blockName = parts[0];
            
            // Converte para ResourceLocation
            ResourceLocation blockId;
            if (blockName.contains(":")) {
                blockId = ResourceLocation.parse(blockName);
            } else {
                blockId = ResourceLocation.fromNamespaceAndPath("minecraft", blockName);
            }
            
            // Obtém o bloco
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block == null) {
                SatisfactoryBuildTool.LOGGER.warn("Unknown block: {}", blockName);
                return null;
            }
            
            BlockState state = block.defaultBlockState();
            
            // Aplica propriedades se existirem
            if (parts.length > 1) {
                String propertiesStr = parts[1];
                if (propertiesStr.endsWith("]")) {
                    propertiesStr = propertiesStr.substring(0, propertiesStr.length() - 1);
                }
                
                String[] properties = propertiesStr.split(",");
                for (String prop : properties) {
                    String[] keyValue = prop.split("=");
                    if (keyValue.length == 2) {
                        String propName = keyValue[0].trim();
                        String propValue = keyValue[1].trim();
                        state = applyProperty(state, propName, propValue);
                    }
                }
            }
            
            return state;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.warn("Failed to parse block state '{}': {}", blockString, e.getMessage());
            return null;
        }
    }
    
    /**
     * Aplica uma propriedade a um BlockState
     */
    @SuppressWarnings("unchecked")
    private static BlockState applyProperty(BlockState state, String propName, String propValue) {
        try {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(propName)) {
                    return state.setValue((Property) property, 
                                        parsePropertyValue(property, propValue));
                }
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.warn("Failed to apply property {}={}: {}", propName, propValue, e.getMessage());
        }
        return state;
    }
    
    /**
     * Converte valor de string para o tipo correto da propriedade
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Comparable parsePropertyValue(Property<?> property, String value) {
        Class<?> valueType = property.getValueClass();
        
        if (valueType == Boolean.class) {
            return Boolean.valueOf(value);
        } else if (valueType == Integer.class) {
            return Integer.valueOf(value);
        } else if (Enum.class.isAssignableFrom(valueType)) {
            // Para enums, tenta encontrar o valor correspondente
            for (Object enumValue : property.getPossibleValues()) {
                if (enumValue.toString().equalsIgnoreCase(value)) {
                    return (Comparable) enumValue;
                }
            }
        }
        
        // Fallback para string
        return value;
    }
    
    /**
     * Obtém BlockState do cache para uma string
     */
    public static BlockState getBlockStateForString(StructureLoader.StructureData structure, String blockString) {
        if (BLOCK_STATE_CACHE.containsKey(blockString)) {
            return BLOCK_STATE_CACHE.get(blockString);
        }
        
        // Tenta parsear se não estiver no cache
        BlockState state = parseBlockState(blockString);
        if (state != null) {
            BLOCK_STATE_CACHE.put(blockString, state);
        }
        
        return state;
    }
    
    /**
     * Calcula estatísticas da estrutura
     */
    public static StructureStats calculateStats(StructureLoader.StructureData structure) {
        int totalBlocks = 0;
        int airBlocks = 0;
        Map<String, Integer> blockCounts = new HashMap<>();
        
        List<List<List<String>>> pattern = structure.getPattern();
        for (List<List<String>> layer : pattern) {
            for (List<String> row : layer) {
                for (String blockChar : row) {
                    totalBlocks++;
                    
                    if (blockChar.equals(" ")) {
                        airBlocks++;
                    } else {
                        blockCounts.merge(blockChar, 1, Integer::sum);
                    }
                }
            }
        }
        
        return new StructureStats(totalBlocks, airBlocks, blockCounts);
    }
    
    /**
     * Classe para armazenar estatísticas da estrutura
     */
    public static class StructureStats {
        private final int totalBlocks;
        private final int airBlocks;
        private final Map<String, Integer> blockCounts;
        
        public StructureStats(int totalBlocks, int airBlocks, Map<String, Integer> blockCounts) {
            this.totalBlocks = totalBlocks;
            this.airBlocks = airBlocks;
            this.blockCounts = blockCounts;
        }
        
        public int getTotalBlocks() { return totalBlocks; }
        public int getAirBlocks() { return airBlocks; }
        public int getSolidBlocks() { return totalBlocks - airBlocks; }
        public Map<String, Integer> getBlockCounts() { return new HashMap<>(blockCounts); }
        
        public double getAirPercentage() {
            return totalBlocks > 0 ? (double) airBlocks / totalBlocks * 100 : 0;
        }
    }
}