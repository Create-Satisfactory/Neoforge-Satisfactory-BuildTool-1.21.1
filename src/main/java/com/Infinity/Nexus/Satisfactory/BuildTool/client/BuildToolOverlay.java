package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructurePreview;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BuildToolOverlay {

    public static final BuildToolOverlay INSTANCE = new BuildToolOverlay();

    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.options.hideGui) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BuildTool)) {
            return;
        }

        renderOverlay(guiGraphics, mc, player, heldItem);
    }

    private void renderOverlay(GuiGraphics guiGraphics, Minecraft mc, Player player, ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        // Title
        lines.add(Component.translatable("item.satisfactory_build_tool.build_tool")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Mode
        BuildTool.BuildMode mode = BuildTool.getMode();
        ChatFormatting modeColor = switch (mode) {
            case CONSTRUCTION -> ChatFormatting.GREEN;
            case DESTRUCTION -> ChatFormatting.RED;
            case CLEAR -> ChatFormatting.BLUE;
            case NONE -> ChatFormatting.GRAY;
        };
        
        String modeKey = switch (mode) {
            case CONSTRUCTION -> "overlay.satisfactory_build_tool.mode.construction";
            case DESTRUCTION -> "overlay.satisfactory_build_tool.mode.destruction";
            case CLEAR -> "overlay.satisfactory_build_tool.mode.clear";
            case NONE -> "overlay.satisfactory_build_tool.mode.none";
        };
        
        lines.add(Component.translatable("overlay.satisfactory_build_tool.mode", 
                Component.translatable(modeKey).withStyle(modeColor)).withStyle(ChatFormatting.WHITE));

        // Structure Info (Construction Mode)
        if (mode == BuildTool.BuildMode.CONSTRUCTION) {
            StructureLoader.StructureData structure = BuildTool.getSelectedStructure();
            if (structure != null) {
                 lines.add(Component.literal("Structure:").withStyle(ChatFormatting.WHITE));
                 
                 String name = structure.getName();
                 if (name.length() > 12) {
                     name = name.substring(0, 12);
                 }
                 lines.add(Component.literal(name).withStyle(ChatFormatting.YELLOW));

                lines.add(Component.translatable("overlay.satisfactory_build_tool.size", 
                        structure.getWidth(), structure.getHeight(), structure.getLength())
                        .withStyle(ChatFormatting.GRAY));
                
                // Color Info
                int colorIndex = BuildTool.getColor(stack);
                if (colorIndex >= 0 && colorIndex < Config.color_blocks.size()) {
                    String colorName = Config.color_blocks.get(colorIndex);
                    // Format: minecraft:white_concrete -> White
                    String displayColorName = colorName;
                    if (displayColorName.contains(":")) {
                        displayColorName = displayColorName.split(":")[1];
                    }
                    displayColorName = displayColorName.replace("_concrete", "").replace("_", " ");
                    if (displayColorName.length() > 0) {
                        displayColorName = displayColorName.substring(0, 1).toUpperCase() + displayColorName.substring(1);
                    }
                    
                    ChatFormatting colorFormat = switch (colorIndex) {
                        case 0 -> ChatFormatting.WHITE;       // White
                        case 1 -> ChatFormatting.GOLD;        // Orange
                        case 2 -> ChatFormatting.LIGHT_PURPLE;// Magenta
                        case 3 -> ChatFormatting.AQUA;        // Light Blue
                        case 4 -> ChatFormatting.YELLOW;      // Yellow
                        case 5 -> ChatFormatting.GREEN;       // Lime
                        case 6 -> ChatFormatting.LIGHT_PURPLE;// Pink
                        case 7 -> ChatFormatting.DARK_GRAY;   // Gray
                        case 8 -> ChatFormatting.GRAY;        // Light Gray
                        case 9 -> ChatFormatting.DARK_AQUA;   // Cyan
                        case 10 -> ChatFormatting.DARK_PURPLE;// Purple
                        case 11 -> ChatFormatting.BLUE;       // Blue
                        case 12 -> ChatFormatting.GOLD;       // Brown
                        case 13 -> ChatFormatting.DARK_GREEN; // Green
                        case 14 -> ChatFormatting.RED;        // Red
                        case 15 -> ChatFormatting.DARK_GRAY;  // Black (Readable)
                        default -> ChatFormatting.WHITE;
                    };

                    lines.add(Component.translatable("overlay.satisfactory_build_tool.color", 
                            Component.literal(displayColorName).withStyle(colorFormat))
                            .withStyle(ChatFormatting.WHITE));
                }

                // Material Info
                String selectedMaterial = BuildTool.getMaterial(stack);
                if (selectedMaterial != null && !selectedMaterial.isEmpty()) {
                    lines.add(Component.translatable("overlay.satisfactory_build_tool.material", 
                            Component.literal(selectedMaterial).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.WHITE));
                }

                if (StructurePreview.isFixed()) {
                    lines.add(Component.translatable("overlay.satisfactory_build_tool.fixed")
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }

        // Section: Controls
        lines.add(Component.empty());
        lines.add(Component.translatable("overlay.satisfactory_build_tool.hotkeys").withStyle(ChatFormatting.GOLD));

        // Dynamic Hotkeys
        addHotkey(lines, KeyBindings.DESTRUCTION_MODE_KEY.getTranslatedKeyMessage(), "overlay.satisfactory_build_tool.key.mode");
        
        if (mode == BuildTool.BuildMode.CONSTRUCTION) {
            addHotkey(lines, KeyBindings.CYCLE_STRUCTURE_KEY.getTranslatedKeyMessage(), "overlay.satisfactory_build_tool.key.cycle");
            addHotkey(lines, KeyBindings.ROTATE_KEY.getTranslatedKeyMessage(), "overlay.satisfactory_build_tool.key.rotate");
            addHotkey(lines, KeyBindings.FIX_KEY.getTranslatedKeyMessage(), "overlay.satisfactory_build_tool.key.fix");
            addHotkey(lines, KeyBindings.ZOOP_KEY.getTranslatedKeyMessage(), "overlay.satisfactory_build_tool.key.zoop_fix");
            
            lines.add(Component.literal("Shift+RClick: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("overlay.satisfactory_build_tool.mouse.place").withStyle(ChatFormatting.WHITE)));
            lines.add(Component.literal("RClick: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("overlay.satisfactory_build_tool.mouse.menu").withStyle(ChatFormatting.WHITE)));
             
        } else if (mode == BuildTool.BuildMode.CLEAR) {
            boolean bothPointsSelected = BuildTool.getPoint1(player) != null && BuildTool.getPoint2(player) != null;
            
            if (bothPointsSelected) {
                 lines.add(Component.translatable("overlay.satisfactory_build_tool.mouse.clear_inside").withStyle(ChatFormatting.YELLOW));
            } else {
                 lines.add(Component.literal("Shift+RClick: ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.translatable("overlay.satisfactory_build_tool.mouse.action").withStyle(ChatFormatting.WHITE)));
            }
        } else if (mode == BuildTool.BuildMode.DESTRUCTION) {
             lines.add(Component.literal("RClick: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("overlay.satisfactory_build_tool.mouse.action").withStyle(ChatFormatting.WHITE)));
        }
        
        // Render Box
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        
        int maxWidth = 0;
        for (Component line : lines) {
            int width = mc.font.width(line);
            if (width > maxWidth) maxWidth = width;
        }
        
        int padding = 5;
        int boxWidth = maxWidth + (padding * 2);
        int boxHeight = (lines.size() * mc.font.lineHeight) + (padding * 2);
        
        int x = screenWidth - boxWidth - 5;
        int y = screenHeight - boxHeight - 5;
        
        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0x90000000); // Semi-transparent black
        
        // Draw Lines
        int currentY = y + padding;
        for (Component line : lines) {
            guiGraphics.drawString(mc.font, line, x + padding, currentY, 0xFFFFFFFF, true);
            currentY += mc.font.lineHeight;
        }
    }
    
    private void addHotkey(List<Component> lines, Component key, String descKey) {
        lines.add(key.copy().withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(descKey).withStyle(ChatFormatting.WHITE)));
    }
}
