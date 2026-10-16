package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;

import java.util.*;
import java.util.stream.Collectors;

public class StructureCategoryManager {

    private final Map<String, List<StructureLoader.StructureData>> categorizedStructures = new HashMap<>();
    private List<String> categories = new ArrayList<>();
    private final Map<String, List<String>> subCategories = new HashMap<>();

    public void parseStructures(Collection<StructureLoader.StructureData> structures) {
        categorizedStructures.clear();
        categories.clear();
        subCategories.clear();

        for (StructureLoader.StructureData structure : structures) {
            // Exclude player-saved custom blueprints from the official Builder tab
            if ("custom".equalsIgnoreCase(structure.getCategory()) && !structure.isServerStructure()) {
                continue;
            }

            String guiPath = structure.getGuiPath();
            String mainCategory = guiPath;
            String subCategory = null;

            String separator = guiPath.contains(">") ? ">" : (guiPath.contains("/") ? "/" : null);
            if (separator != null) {
                String[] parts = guiPath.split(separator);
                mainCategory = parts[0].trim();
                if (parts.length > 1) {
                    subCategory = parts[1].trim();
                }
            }

            if (subCategory != null && !subCategory.isEmpty()) {
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

    private void organizeCategories() {
        categories = new ArrayList<>(categorizedStructures.keySet().stream()
                .map(s -> s.contains(" > ") ? s.split(" > ")[0] : s)
                .distinct()
                .collect(Collectors.toList()));

        List<String> order = Config.category_order;
        categories.sort((a, b) -> {
            int indexA = order.indexOf(a);
            int indexB = order.indexOf(b);
            if (indexA != -1 && indexB != -1) return Integer.compare(indexA, indexB);
            if (indexA != -1) return -1;
            if (indexB != -1) return 1;
            return a.compareToIgnoreCase(b);
        });

        for (List<String> subs : subCategories.values()) {
            subs.sort(String::compareToIgnoreCase);
        }
    }

    public List<StructureLoader.StructureData> getFilteredStructures(String categoryOrPath, String searchTerm) {
        if (categoryOrPath == null) return Collections.emptyList();

        List<StructureLoader.StructureData> result = new ArrayList<>(
                categorizedStructures.getOrDefault(categoryOrPath, Collections.emptyList())
        );

        // Only aggregate subcategories if a top-level category (without " > ") was requested directly
        if (!categoryOrPath.contains(" > ")) {
            for (String sub : subCategories.getOrDefault(categoryOrPath, Collections.emptyList())) {
                String subPath = categoryOrPath + " > " + sub;
                result.addAll(categorizedStructures.getOrDefault(subPath, Collections.emptyList()));
            }
        }

        if (searchTerm != null && !searchTerm.isEmpty()) {
            String lowerSearch = searchTerm.toLowerCase();
            result = result.stream()
                    .filter(s -> s.getName().toLowerCase().contains(lowerSearch) ||
                            s.getDescription().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }
        return result;
    }

    public List<String> getCategories() {
        return new ArrayList<>(categories);
    }

    public List<String> getSubCategories(String category) {
        return new ArrayList<>(subCategories.getOrDefault(category, Collections.emptyList()));
    }
}