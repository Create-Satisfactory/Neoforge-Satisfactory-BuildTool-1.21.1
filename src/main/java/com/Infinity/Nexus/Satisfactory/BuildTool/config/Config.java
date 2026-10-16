package com.Infinity.Nexus.Satisfactory.BuildTool.config;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID)
public class Config {
    // Instancia a configuração
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    //STRUCTURES
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SYNCHRONIZED_STRUCTURES = BUILDER
            .comment("List of structures that will be synchronized between client and server")
            .defineList("synchronized_structures", List.of("template_foundation"), o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue GENERATE_TEMPLATE = BUILDER
            .comment("true = generate a template structure if the server structures folder is empty, false = do not generate")
            .define("generate_template", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> IGNORED_PROPERTIES = BUILDER
            .comment("List of properties that will be ignored when pasting a structure")
            .defineList("ignored_properties", List.of(
                    "age", "stage", "moisture", "growth", "germinating", "level", "layers",
                    "honey_level", "eggs", "pickles", "bites", "berries", "charges",
                    "flower_amount", "power", "triggered", "enabled", "lit", "signal_fire",
                    "has_book", "has_record", "damage", "rotation", "note", "attachment",
                    "hanging", "drag", "shrieking", "bloom", "candles", "conditional",
                    "delay", "locked", "mode", "scroll", "shape", "short", "unstable"
            ), o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue ENABLE_CREATIVITY_ORB = BUILDER.comment("true = enable, false = disable")
            .define("enable_buildtool", true);

    public static final ModConfigSpec.IntValue MAX_QUEUED_STRUCTURES = BUILDER.comment("Maximum number of queued structures per player")
            .defineInRange("max_queued_structures", 100, 1, 1000);

    public static final ModConfigSpec.BooleanValue ENABLE_CLEAR_MODE = BUILDER
            .comment("true = enable CLEAR mode, false = disable")
            .define("enable_clear_mode", false);

    public static final ModConfigSpec.BooleanValue ALLOW_OBSTRUCTION_CLEARING = BUILDER
            .comment("true = automatically clear obstructions when placing structure, false = prevent placement")
            .define("allow_obstruction_clearing", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CATEGORY_ORDER = BUILDER
            .comment("Order of categories that will be displayed in the GUI")
            .defineList("category_order", List.of("Logistics", "Production", "Power", "Organization", "Transportation"), o -> o instanceof String);

    // COLORS
    public static final ModConfigSpec.ConfigValue<String> TEMPLATE_BLOCK = BUILDER
            .comment("Block that will be replaced by the selected color in BuildTool")
            .define("template_block", "minecraft:yellow_concrete");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLOR_BLOCKS = BUILDER
            .comment("List of 16 blocks corresponding to the 16 colors (0-15). Order: White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, Black")
            .defineList("color_blocks", List.of(
                    "minecraft:white_concrete",
                    "minecraft:orange_concrete",
                    "minecraft:magenta_concrete",
                    "minecraft:light_blue_concrete",
                    "minecraft:yellow_concrete",
                    "minecraft:lime_concrete",
                    "minecraft:pink_concrete",
                    "minecraft:gray_concrete",
                    "minecraft:light_gray_concrete",
                    "minecraft:cyan_concrete",
                    "minecraft:purple_concrete",
                    "minecraft:blue_concrete",
                    "minecraft:brown_concrete",
                    "minecraft:green_concrete",
                    "minecraft:red_concrete",
                    "minecraft:black_concrete"
            ), o -> o instanceof String);

    // MATERIALS
    public static final ModConfigSpec.ConfigValue<String> BASE_MATERIAL = BUILDER
            .comment("Base block that will be replaced by the selected material in Build Parts")
            .define("base_material", "minecraft:stone");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MATERIAL_BLOCKS = BUILDER
            .comment("List of materials in 'name|block_id' format. Example: 'Stone|minecraft:stone'")
            .defineList("material_blocks", List.of(
                    "Stone|minecraft:stone",
                    "Iron|minecraft:iron_block",
                    "Gold|minecraft:gold_block",
                    "Concrete|minecraft:gray_concrete"
            ), o -> o instanceof String);

    // BUILD LIMITS
    public static final ModConfigSpec.IntValue MAX_STRUCTURE_SIZE = BUILDER
            .comment("Maximum allowed size for structures (width, height, length)")
            .defineInRange("max_structure_size", 100, 10, 1000);
    
    public static final ModConfigSpec.IntValue MAX_STRUCTURE_VOLUME = BUILDER
            .comment("Maximum allowed volume for structures (in blocks)")
            .defineInRange("max_structure_volume", 1000000, 100, 10000000);
    
    public static final ModConfigSpec.IntValue MAX_ZOOP_COUNT = BUILDER
            .comment("Maximum number of structures that can be placed in a single Zoop")
            .defineInRange("max_zoop_count", 20, 1, 100);
    
    public static final ModConfigSpec.LongValue PREVIEW_DURATION_MS = BUILDER
            .comment("Duration in milliseconds that structure preview will remain visible")
            .defineInRange("preview_duration_ms", 60000L, 1000L, 600000L);
    
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("Number of blocks placed per tick during construction")
            .defineInRange("blocks_per_tick", 10, 1, 100);
    
    // PERFORMANCE
    public static final ModConfigSpec.BooleanValue ENABLE_COMPRESSION = BUILDER
            .comment("true = enable data compression, false = disable")
            .define("enable_compression", true);
    
    public static final ModConfigSpec.BooleanValue VERBOSE_LOGGING = BUILDER
            .comment("true = detailed logging, false = minimal logging")
            .define("verbose_logging", false);
    
    // Finaliza o build
    public static final ModConfigSpec SPEC = BUILDER.build();

    public static List<String> list_of_structures_to_sync = new ArrayList<>();
    public static List<String> list_of_ignored_blockstates = new ArrayList<>();

    public static boolean enable_creativity_orb = true;
    public static String template_block;
    public static List<String> color_blocks = new ArrayList<>();
    public static String base_material;
    public static List<MaterialEntry> material_blocks = new ArrayList<>();
    
    // Material entry class
    public static class MaterialEntry {
        public final String name;
        public final String blockId;
        
        public MaterialEntry(String name, String blockId) {
            this.name = name;
            this.blockId = blockId;
        }
    }
    public static List<String> category_order = new ArrayList<>();
    public static int max_queued_structures = 100;
    public static boolean generate_template = true;
    
    // Novos valores
    public static int max_structure_size = 100;
    public static int max_structure_volume = 1000000;
    public static int max_zoop_count = 20;
    public static long preview_duration_ms = 60000;
    public static int blocks_per_tick = 10;
    public static boolean enable_compression = true;
    public static boolean verbose_logging = false;
    public static boolean enable_clear_mode = true;
    public static boolean allow_obstruction_clearing = false;



    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        //Carrega as variaveis de Configuração para a memoria para uso posterior
        //STRUCTURES
        list_of_structures_to_sync = new ArrayList<>(SYNCHRONIZED_STRUCTURES.get());
        list_of_ignored_blockstates = new ArrayList<>(IGNORED_PROPERTIES.get());
        //ORBS
        enable_creativity_orb = ENABLE_CREATIVITY_ORB.get();
        //COLORS
        template_block = TEMPLATE_BLOCK.get();
        color_blocks = new ArrayList<>(COLOR_BLOCKS.get());
        base_material = BASE_MATERIAL.get();
        material_blocks = MATERIAL_BLOCKS.get().stream()
                .map(s -> {
                    String[] parts = s.split("\\|");
                    return new MaterialEntry(parts[0].trim(), parts.length > 1 ? parts[1].trim() : parts[0]);
                })
                .collect(java.util.stream.Collectors.toList());
        category_order = new ArrayList<>(CATEGORY_ORDER.get());
        max_queued_structures = MAX_QUEUED_STRUCTURES.get();
        generate_template = GENERATE_TEMPLATE.get();
        
        // BUILD LIMITS
        max_structure_size = MAX_STRUCTURE_SIZE.get();
        max_structure_volume = MAX_STRUCTURE_VOLUME.get();
        max_zoop_count = MAX_ZOOP_COUNT.get();
        preview_duration_ms = PREVIEW_DURATION_MS.get();
        blocks_per_tick = BLOCKS_PER_TICK.get();
        
        // PERFORMANCE
        enable_compression = ENABLE_COMPRESSION.get();
        verbose_logging = VERBOSE_LOGGING.get();
        
        enable_clear_mode = ENABLE_CLEAR_MODE.get();
        allow_obstruction_clearing = ALLOW_OBSTRUCTION_CLEARING.get();
    }
}
