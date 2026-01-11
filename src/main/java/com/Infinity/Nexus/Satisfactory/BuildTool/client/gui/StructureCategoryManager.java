package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilitário para gerenciamento de categorias e estruturas na interface
 */
public class StructureCategoryManager {
    
    private Map<String, List<StructureLoader.StructureData>> categorizedStructures = new HashMap<>();
    private List<String> categories = new ArrayList<>();
    private Map<String, List<String>> subCategories = new HashMap<>();
    
    /**
     * Parseia e organiza as estruturas em categorias
     */
    public void parseStructures(Collection<StructureLoader.StructureData> structures) {
        categorizedStructures.clear();
        categories.clear();
        subCategories.clear();
        
        for (StructureLoader.StructureData structure : structures) {
            // SECURITY FIX: Only include server-side official structures in these categories
            if (!structure.isServerStructure()) {
                continue;
            }
            
            String guiPath = structure.getGuiPath();
            String mainCategory = guiPath;
            String subCategory = null;
            
            // Support both "/" and ">" as separators
            String separator = guiPath.contains(">") ? ">" : (guiPath.contains("/") ? "/" : null);
            
            if (separator != null) {
                String[] parts = guiPath.split(separator);
                mainCategory = parts[0].trim();
                if (parts.length > 1) {
                    subCategory = parts[1].trim();
                }
            }
            
            if (subCategory != null) {
                String fullPath = mainCategory + " > " + subCategory;
                categorizedStructures.computeIfAbsent(fullPath, k -> new ArrayList<>()).add(structure);
                List<String> subs = subCategories.computeIfAbsent(mainCategory, k -> new ArrayList<>());
                if (!subs.contains(subCategory)) {
                    subs.add(subCategory);
                }
            } else {
                categorizedStructures.computeIfAbsent(mainCategory, k -> new ArrayList<>()).add(structure);
            }
        }
        
        organizeCategories();
    }
    
    /**
     * Organiza as categorias seguindo a ordem de configuração
     */
    private void organizeCategories() {
        // Sort categories alphabetically first
        categories = new ArrayList<>(categorizedStructures.keySet().stream()
                .map(s -> s.contains(" > ") ? s.split(" > ")[0] : s)
                .distinct()
                .collect(Collectors.toList()));
        
        // Sort categories by config order, then alphabetically for unknown ones
        List<String> order = Config.category_order;
        categories.sort((a, b) -> {
            int indexA = order.indexOf(a);
            int indexB = order.indexOf(b);
            if (indexA != -1 && indexB != -1) return Integer.compare(indexA, indexB);
            if (indexA != -1) return -1;
            if (indexB != -1) return 1;
            return a.compareToIgnoreCase(b);
        });
        
        // Sort subcategories
        for (List<String> subs : subCategories.values()) {
            subs.sort(String::compareToIgnoreCase);
        }
    }
    
    /**
     * Obtém estruturas filtradas por categoria e busca
     */
    public List<StructureLoader.StructureData> getFilteredStructures(String category, String searchTerm) {
        List<StructureLoader.StructureData> result = new ArrayList<>();
        
        if (category == null) return result;
        
        // Get structures for this category (and subcategories)
        List<StructureLoader.StructureData> categoryStructures = categorizedStructures.getOrDefault(category, new ArrayList<>());
        result.addAll(categoryStructures);
        
        // Also include subcategories
        List<String> subs = subCategories.getOrDefault(category, new ArrayList<>());
        for (String sub : subs) {
            String subPath = category + " > " + sub;
            result.addAll(categorizedStructures.getOrDefault(subPath, new ArrayList<>()));
        }
        
        // Apply search filter
        if (searchTerm != null && !searchTerm.isEmpty()) {
            String lowerSearch = searchTerm.toLowerCase();
            result = result.stream()
                    .filter(s -> s.getName().toLowerCase().contains(lowerSearch) ||
                               s.getDescription().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }
        
        return result;
    }
    
    /**
     * Obtém todas as categorias principais ordenadas
     */
    public List<String> getCategories() {
        return new ArrayList<>(categories);
    }
    
    /**
     * Obtém subcategorias de uma categoria específica
     */
    public List<String> getSubCategories(String category) {
        return new ArrayList<>(subCategories.getOrDefault(category, new ArrayList<>()));
    }
    
    /**
     * Verifica se uma categoria tem subcategorias
     */
    public boolean hasSubCategories(String category) {
        return subCategories.containsKey(category) && !subCategories.get(category).isEmpty();
    }
    
    /**
     * Limpa todos os dados
     */
    public void clear() {
        categorizedStructures.clear();
        categories.clear();
        subCategories.clear();
    }
}