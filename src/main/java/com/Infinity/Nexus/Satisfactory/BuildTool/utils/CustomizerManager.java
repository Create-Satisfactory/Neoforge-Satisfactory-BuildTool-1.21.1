package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CustomizerManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Paths.get("config/InfinityNexus/customizer.json");

    public static final String DEFAULT_PRIMARY_TEMPLATE = "minecraft:yellow_concrete";
    public static final String DEFAULT_SECONDARY_TEMPLATE = "minecraft:cyan_terracotta";

    private static final String TOKEN_PRIMARY = "__FICSIT_PRIMARY_BLOCK__";
    private static final String TOKEN_SECONDARY = "__FICSIT_SECONDARY_BLOCK__";
    private static final String TOKEN_MATERIAL = "__FICSIT_MATERIAL_BLOCK__";

    private static CustomizerConfigData DATA = new CustomizerConfigData();
    private static final Map<String, SwatchDef> SWATCH_BY_ID = new LinkedHashMap<>();
    private static final List<SwatchDef> SWATCH_LIST = new ArrayList<>();
    private static final Map<String, MaterialDef> MATERIAL_BY_ID = new LinkedHashMap<>();

    public record SwatchDef(
            String id,
            String name,
            String subCategory,
            boolean isFinish,
            String primaryHex,
            String secondaryHex,
            String primaryBlock,
            String secondaryBlock,
            String description,
            boolean unlockedByDefault
    ) {
        public int getPrimaryColorInt() { return parseHex(primaryHex, 0xFFFA9549); }
        public int getSecondaryColorInt() { return parseHex(secondaryHex, 0xFF5F668C); }
    }

    public record MaterialDef(
            String id,
            String name,
            String subCategory,
            String targetCategory,
            String iconBlock,
            String fallbackBlock,
            String description,
            boolean unlockedByDefault
    ) {}

    public static class CustomizerConfigData {
        public String primaryTemplateBlock = DEFAULT_PRIMARY_TEMPLATE;
        public String secondaryTemplateBlock = DEFAULT_SECONDARY_TEMPLATE;
        public List<SwatchDef> swatches = new ArrayList<>();
        public List<MaterialDef> materials = new ArrayList<>();
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                Files.createDirectories(CONFIG_FILE.getParent());
                DATA = createDefaultData();
                try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                    GSON.toJson(DATA, writer);
                }
            } else {
                try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
                    CustomizerConfigData loaded = GSON.fromJson(reader, CustomizerConfigData.class);
                    if (loaded != null && loaded.swatches != null && !loaded.swatches.isEmpty()) {
                        DATA = loaded;
                    } else {
                        DATA = createDefaultData();
                    }
                }
            }
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to load customizer.json, using defaults", e);
            DATA = createDefaultData();
        }

        SWATCH_BY_ID.clear();
        SWATCH_LIST.clear();
        MATERIAL_BY_ID.clear();

        if (DATA.swatches != null) {
            for (SwatchDef s : DATA.swatches) {
                SWATCH_BY_ID.put(s.id().toLowerCase(), s);
                SWATCH_LIST.add(s);
            }
        }
        if (DATA.materials != null) {
            for (MaterialDef m : DATA.materials) {
                MATERIAL_BY_ID.put(m.id().toLowerCase(), m);
                MATERIAL_BY_ID.put(m.name().toLowerCase(), m);
            }
        }
    }

    public static List<SwatchDef> getAllSwatches() {
        if (SWATCH_LIST.isEmpty()) load();
        return Collections.unmodifiableList(SWATCH_LIST);
    }

    public static Collection<MaterialDef> getAllMaterials() {
        if (MATERIAL_BY_ID.isEmpty()) load();
        return DATA.materials != null ? Collections.unmodifiableList(DATA.materials) : Collections.emptyList();
    }

    public static SwatchDef getSwatch(int index) {
        if (SWATCH_LIST.isEmpty()) load();
        if (index >= 0 && index < SWATCH_LIST.size()) {
            return SWATCH_LIST.get(index);
        }
        return SWATCH_LIST.isEmpty() ? null : SWATCH_LIST.get(0);
    }

    @Nullable
    public static MaterialDef getMaterial(@Nullable String materialKey) {
        if (materialKey == null || materialKey.isEmpty()) return null;
        if (MATERIAL_BY_ID.isEmpty()) load();
        return MATERIAL_BY_ID.get(materialKey.trim().toLowerCase());
    }

    public static String getPrimaryTemplateBlock() {
        if (DATA.primaryTemplateBlock != null && !DATA.primaryTemplateBlock.isEmpty()) {
            return DATA.primaryTemplateBlock;
        }
        return (Config.template_block != null && !Config.template_block.isEmpty())
                ? Config.template_block
                : DEFAULT_PRIMARY_TEMPLATE;
    }

    public static String getSecondaryTemplateBlock() {
        return (DATA.secondaryTemplateBlock != null && !DATA.secondaryTemplateBlock.isEmpty())
                ? DATA.secondaryTemplateBlock
                : DEFAULT_SECONDARY_TEMPLATE;
    }

    /**
     * Two-pass replacement of Primary Swatch, Secondary Swatch, and Base Material blocks.
     * Works on all structures (server & client blueprints) and inside Copycat {nbt} strings!
     */
    public static String applyCustomizerReplacements(
            String rawBlockString,
            boolean isOfficial,
            boolean usingMaterialSchematic,
            int swatchIndex,
            @Nullable String selectedMaterial) {

        if (rawBlockString == null || rawBlockString.isEmpty() || rawBlockString.equals(" ")) {
            return rawBlockString;
        }

        String result = rawBlockString;
        SwatchDef swatch = getSwatch(swatchIndex);

        String priTemplate = getPrimaryTemplateBlock();
        String secTemplate = getSecondaryTemplateBlock();
        String baseMatTemplate = (Config.base_material != null && !Config.base_material.isEmpty())
                ? Config.base_material
                : "minecraft:stone";

        // Pass 1: Tokenize placeholders so Primary, Secondary, and Material never overwrite each other
        boolean hasPrimary = false;
        boolean hasSecondary = false;
        boolean hasMaterial = false;

        if (swatch != null) {
            if (priTemplate != null && !priTemplate.isEmpty() && result.contains(priTemplate)) {
                result = result.replace(priTemplate, TOKEN_PRIMARY);
                hasPrimary = true;
            }
            // Also check Config.template_block if it differs from priTemplate
            if (Config.template_block != null && !Config.template_block.isEmpty()
                    && !Config.template_block.equals(priTemplate) && result.contains(Config.template_block)) {
                result = result.replace(Config.template_block, TOKEN_PRIMARY);
                hasPrimary = true;
            }
            if (secTemplate != null && !secTemplate.isEmpty() && result.contains(secTemplate)) {
                result = result.replace(secTemplate, TOKEN_SECONDARY);
                hasSecondary = true;
            }
        }

        MaterialDef matDef = (!usingMaterialSchematic && selectedMaterial != null && !selectedMaterial.isEmpty())
                ? getMaterial(selectedMaterial)
                : null;

        if (matDef != null && matDef.fallbackBlock() != null && !matDef.fallbackBlock().isEmpty()
                && result.contains(baseMatTemplate)) {
            result = result.replace(baseMatTemplate, TOKEN_MATERIAL);
            hasMaterial = true;
        }

        // Pass 2: Replace tokens with target block IDs
        if (hasPrimary && swatch != null && swatch.primaryBlock() != null) {
            result = result.replace(TOKEN_PRIMARY, swatch.primaryBlock());
        }
        if (hasSecondary && swatch != null && swatch.secondaryBlock() != null) {
            result = result.replace(TOKEN_SECONDARY, swatch.secondaryBlock());
        }
        if (hasMaterial && matDef != null && matDef.fallbackBlock() != null) {
            result = result.replace(TOKEN_MATERIAL, matDef.fallbackBlock());
        }

        return result;
    }

    private static int parseHex(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : (hex.startsWith("0x") ? hex.substring(2) : hex);
            if (clean.length() == 6) {
                return 0xFF000000 | Integer.parseInt(clean, 16);
            }
            return (int) Long.parseLong(clean, 16);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static CustomizerConfigData createDefaultData() {
        CustomizerConfigData data = new CustomizerConfigData();

        // 1. Default Swatches
        data.swatches.add(new SwatchDef("ficsit_factory", "FICSIT Factory Swatch", "1. Default", false,
                "#FA9549", "#5F668C", "minecraft:orange_concrete", "minecraft:gray_concrete",
                "Applied to all Factory Buildings by default.\n\nWarning: Changing this Swatch will apply the change to all buildings currently using it.", true));
        data.swatches.add(new SwatchDef("project_assembly", "Project Assembly Swatch", "1. Default", false,
                "#FA9549", "#474E68", "minecraft:orange_concrete", "minecraft:cyan_terracotta",
                "Applied to Special Progression Buildings by default.", true));
        data.swatches.add(new SwatchDef("ficsit_foundation", "FICSIT Foundation Swatch", "1. Default", false,
                "#6B707B", "#FA9549", "minecraft:gray_concrete", "minecraft:orange_concrete",
                "Applied to all Foundations and Ramps by default.", true));
        data.swatches.add(new SwatchDef("concrete_structure", "Concrete Structure Swatch", "1. Default", false,
                "#EAEAEA", "#FA9549", "minecraft:white_concrete", "minecraft:orange_concrete",
                "Applied to all Concrete Structures by default.", true));
        data.swatches.add(new SwatchDef("pipeline", "Pipeline Swatch", "1. Default", false,
                "#FA9549", "#5F668C", "minecraft:orange_concrete", "minecraft:gray_concrete",
                "Applied to all Pipelines and Pipe Attachments by default.\n\nWarning: Changing this Swatch will apply the change to all buildings currently using it.", true));

        // 2. Custom Swatch
        data.swatches.add(new SwatchDef("custom_swatch", "Custom Swatch", "2. Custom", false,
                "#18191C", "#18191C", "minecraft:black_concrete", "minecraft:black_concrete",
                "Custom high-contrast carbon swatch.", true));

        // 3. Finishes
        data.swatches.add(new SwatchDef("finish_chrome", "Chrome Finish", "3. Finishes", true,
                "#8C929C", "#D8DEE9", "minecraft:iron_block", "minecraft:light_gray_concrete",
                "Applies a reflective polished Chrome finish to structures.", true));
        data.swatches.add(new SwatchDef("finish_copper", "Copper Finish", "3. Finishes", true,
                "#9C523B", "#E8997D", "minecraft:copper_block", "minecraft:brown_concrete",
                "Applies a warm metallic Copper finish to structures.", true));
        data.swatches.add(new SwatchDef("finish_unpainted", "Unpainted", "3. Finishes", true,
                "#545860", "#A2A8B4", "minecraft:gray_concrete", "minecraft:light_gray_concrete",
                "Strips paint layers to expose raw industrial steel.", true));
        data.swatches.add(new SwatchDef("finish_carbon", "Carbon Steel Finish", "3. Finishes", true,
                "#282C34", "#6C768C", "minecraft:netherite_block", "minecraft:black_concrete",
                "Applies a dark brushed Carbon Steel finish.", true));
        data.swatches.add(new SwatchDef("finish_caterium", "Caterium Finish", "3. Finishes", true,
                "#A68529", "#FFE580", "minecraft:gold_block", "minecraft:yellow_concrete",
                "Applies a gleaming Caterium gold finish.", true));

        // 4. Numbered Swatches (Swatch 1 - Swatch 12)
        Object[][] numbered = {
                {"swatch_1", "Swatch 1", "#0078B8", "#F2B705", "minecraft:blue_concrete", "minecraft:yellow_concrete"},
                {"swatch_2", "Swatch 2", "#1D1E22", "#C8102E", "minecraft:black_concrete", "minecraft:red_concrete"},
                {"swatch_3", "Swatch 3", "#6B7366", "#434840", "minecraft:gray_concrete", "minecraft:green_concrete"},
                {"swatch_4", "Swatch 4", "#00B4D8", "#D938C5", "minecraft:cyan_concrete", "minecraft:magenta_concrete"},
                {"swatch_5", "Swatch 5", "#636B59", "#594F43", "minecraft:green_concrete", "minecraft:brown_concrete"},
                {"swatch_6", "Swatch 6", "#F5E6D3", "#E8833A", "minecraft:white_concrete", "minecraft:orange_concrete"},
                {"swatch_7", "Swatch 7", "#7FCC19", "#2E4D12", "minecraft:lime_concrete", "minecraft:green_concrete"},
                {"swatch_8", "Swatch 8", "#7F3FB2", "#B24CD8", "minecraft:purple_concrete", "minecraft:magenta_concrete"},
                {"swatch_9", "Swatch 9", "#FEED63", "#4C4C4C", "minecraft:yellow_concrete", "minecraft:gray_concrete"},
                {"swatch_10", "Swatch 10", "#F27FA5", "#F0F0F0", "minecraft:pink_concrete", "minecraft:white_concrete"},
                {"swatch_11", "Swatch 11", "#99BAD6", "#334CB2", "minecraft:light_blue_concrete", "minecraft:blue_concrete"},
                {"swatch_12", "Swatch 12", "#664C33", "#9D9D97", "minecraft:brown_concrete", "minecraft:light_gray_concrete"}
        };
        for (Object[] row : numbered) {
            data.swatches.add(new SwatchDef(
                    (String) row[0], (String) row[1], "4. Swatches", false,
                    (String) row[2], (String) row[3], (String) row[4], (String) row[5],
                    "Standard FICSIT dual-tone color swatch for factory color-coding and Copycat panels.", true
            ));
        }

        // Foundation Materials
        data.materials.add(new MaterialDef("FICSIT", "FICSIT Foundation", "1. Foundation Materials", "Foundations",
                "minecraft:stone", "minecraft:stone",
                "Applied to Foundations and Ramps to convert them to the FICSIT Foundation style.", true));
        data.materials.add(new MaterialDef("Concrete", "Concrete Foundation", "1. Foundation Materials", "Foundations",
                "minecraft:gray_concrete", "minecraft:gray_concrete",
                "Applied to Foundations and Ramps to convert them to the Concrete Foundation style.", true));
        data.materials.add(new MaterialDef("Grip Metal", "Grip Metal Foundation", "1. Foundation Materials", "Foundations",
                "minecraft:iron_block", "minecraft:iron_block",
                "Applied to Foundations and Ramps to convert them to the Grip Metal Foundation style.", true));
        data.materials.add(new MaterialDef("Coated Concrete", "Coated Concrete Foundation", "1. Foundation Materials", "Foundations",
                "minecraft:polished_andesite", "minecraft:polished_andesite",
                "Applied to Foundations and Ramps to convert them to the Coated Concrete Foundation style.", true));
        data.materials.add(new MaterialDef("Asphalt", "Asphalt Foundation", "1. Foundation Materials", "Foundations",
                "minecraft:black_concrete", "minecraft:black_concrete",
                "Applied to Foundations and Ramps to convert them to the Asphalt Foundation style.", true));

        // Wall Materials
        data.materials.add(new MaterialDef("FICSIT Wall", "FICSIT Wall", "2. Wall Materials", "Walls",
                "minecraft:orange_concrete", "minecraft:orange_concrete",
                "Applied to Walls to convert them to the standard FICSIT Wall style.", true));
        data.materials.add(new MaterialDef("Concrete Wall", "Concrete Wall", "2. Wall Materials", "Walls",
                "minecraft:light_gray_concrete", "minecraft:light_gray_concrete",
                "Applied to Walls to convert them to the Concrete Wall style.", true));
        data.materials.add(new MaterialDef("Steel Wall", "Steel Wall", "2. Wall Materials", "Walls",
                "minecraft:deepslate_tiles", "minecraft:deepslate_tiles",
                "Applied to Walls to convert them to the corrugated Steel Wall style.", true));

        // Roof Materials
        data.materials.add(new MaterialDef("FICSIT Roof", "FICSIT Roof", "3. Roof Materials", "Roofs",
                "minecraft:orange_terracotta", "minecraft:orange_terracotta",
                "Applied to Roofs to convert them to the FICSIT Roof style.", true));
        data.materials.add(new MaterialDef("Tar Roof", "Tar Roof", "3. Roof Materials", "Roofs",
                "minecraft:black_terracotta", "minecraft:black_terracotta",
                "Applied to Roofs to convert them to the flat Tar Roof style.", true));
        data.materials.add(new MaterialDef("Metal Roof", "Metal Roof", "3. Roof Materials", "Roofs",
                "minecraft:iron_block", "minecraft:iron_block",
                "Applied to Roofs to convert them to the corrugated Metal Roof style.", true));
        data.materials.add(new MaterialDef("Glass Roof", "Glass Roof", "3. Roof Materials", "Roofs",
                "minecraft:tinted_glass", "minecraft:tinted_glass",
                "Applied to Roofs to convert them to the industrial Glass Roof style.", true));

        return data;
    }
}