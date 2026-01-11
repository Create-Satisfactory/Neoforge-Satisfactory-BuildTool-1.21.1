package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetColorC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureFileManager;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.gui.StructureCategoryManager;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.gui.GuiRenderer;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ColorUtils;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;

public class BuildToolScreen extends Screen {
    
    private static final ResourceLocation DEFAULT_ICON = ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "textures/gui/icons/factory_default.png");
    
    private final StructureCategoryManager categoryManager = new StructureCategoryManager();
    
    private int itemsPerRow = 3; // Dynamicly calculated
    
    public enum Tab {
        BUILDER,
        CUSTOMIZER,
        BLUEPRINTS
    }
    
    private Tab currentTab = Tab.BUILDER;
    private String selectedCategory = null;
    private String customizerCategory = "Colors"; // Track selected category in Customizer (Colors or Materials)
    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private StructureLoader.StructureData selectedStructureInTab = null;
    private Button selectButton;
    private Button editButton;
    private Button deleteButton;
    private StructureLoader.RequiredItem hoveredCostItem = null;
    private final StructureFileManager fileManager = new StructureFileManager();
    
    public BuildToolScreen() {
        super(Component.translatable("gui.satisfactory_build_tool.build_tool_screen.title"));
        parseStructures();
    }

    public BuildToolScreen(Tab initialTab) {
        this();
        this.currentTab = initialTab;
    }
    
    private void parseStructures() {
        // Get all client structures
        Collection<StructureLoader.StructureData> structures = StructureLoader.getClientStructures();
        categoryManager.parseStructures(structures);
        
        // Select first category by default
        if (currentTab == Tab.BLUEPRINTS) {
            // Para blueprints, selecionar "All" por padrão
            if (selectedCategory == null) {
                selectedCategory = "All";
            }
        } else {
            // Para builder, usar a primeira categoria normal
            List<String> categories = categoryManager.getCategories();
            if (!categories.isEmpty() && selectedCategory == null) {
                selectedCategory = categories.get(0);
            }
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate items per row based on available width
        int gridAreaWidth = (this.width - GuiRenderer.getInfoPanelWidth() - GuiRenderer.getPadding()) - (GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() * 2);
        this.itemsPerRow = Math.max(1, (gridAreaWidth + GuiRenderer.getGridSpacing()) / (GuiRenderer.getGridItemSize() + GuiRenderer.getGridSpacing()));
        
        // Tab buttons (Builder, Customizer, Blueprints)
        int usableWidth = this.width - GuiRenderer.getTabHeight() - GuiRenderer.getPadding() * 3;
        int buttonWidth = usableWidth / 3;
        
        int tabY = GuiRenderer.getPadding();
        
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.satisfactory_build_tool.tab.builder"),
                b -> setTab(Tab.BUILDER))
                .bounds(GuiRenderer.getPadding(), tabY, buttonWidth - 2, GuiRenderer.getTabHeight())
                .build());
        
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.satisfactory_build_tool.tab.customizer"),
                b -> setTab(Tab.CUSTOMIZER))
                .bounds(GuiRenderer.getPadding() + buttonWidth, tabY, buttonWidth - 2, GuiRenderer.getTabHeight())
                .build());
        
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.satisfactory_build_tool.tab.blueprints"),
                b -> setTab(Tab.BLUEPRINTS))
                .bounds(GuiRenderer.getPadding() + buttonWidth * 2, tabY, buttonWidth - 2, GuiRenderer.getTabHeight())
                .build());

        // Close button (X) - Square and in the top right
        this.addRenderableWidget(Button.builder(
                Component.literal("X"),
                b -> this.onClose())
                .bounds(this.width - GuiRenderer.getTabHeight() - GuiRenderer.getPadding(), tabY, GuiRenderer.getTabHeight(), GuiRenderer.getTabHeight())
                .build());
        
        // Search box (adjust position to leave space for info panel)
        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS) {
            initSearchBox();
        }
        
        // Category buttons (for Builder, Blueprints, and Customizer tabs)
        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS) {
            initCategoryButtons();
        } else if (currentTab == Tab.CUSTOMIZER) {
            // Customizer has its own categories (Colors and Materials)
            initCustomizerCategoryButtons();
        }
        
        // Color & Material palette (for Customizer tab)
        if (currentTab == Tab.CUSTOMIZER) {
            if (customizerCategory.equals("Colors")) {
                initColorPalette();
            } else if (customizerCategory.equals("Materials")) {
                initMaterialPalette();
            }
        }

        // Select button (initially hidden)
        selectButton = Button.builder(
                Component.translatable("gui.satisfactory_build_tool.info.select"),
                b -> {
                    if (selectedStructureInTab != null) {
                        selectStructure(selectedStructureInTab);
                    }
                })
                .bounds(this.width - GuiRenderer.getInfoPanelWidth() + 10, this.height - 30, GuiRenderer.getInfoPanelWidth() - 20, 20)
                .build();
        selectButton.visible = false;
        this.addRenderableWidget(selectButton);
        
        // Edit button (for blueprints tab) - estilo normal com background
        editButton = Button.builder(
                Component.translatable("gui.satisfactory_build_tool.edit"),
                b -> openEditor())
                .bounds(this.width - GuiRenderer.getInfoPanelWidth() + 10, this.height - 60, (GuiRenderer.getInfoPanelWidth() - 25) / 2, 20)
                .build();
        editButton.visible = false;
        this.addRenderableWidget(editButton);
        
        // Delete button (for blueprints tab) - estilo normal com background
        deleteButton = Button.builder(
                Component.translatable("gui.satisfactory_build_tool.delete"),
                b -> deleteStructure())
                .bounds(this.width - GuiRenderer.getInfoPanelWidth() + 15 + (GuiRenderer.getInfoPanelWidth() - 25) / 2, this.height - 60, (GuiRenderer.getInfoPanelWidth() - 25) / 2, 20)
                .build();
        deleteButton.visible = false;
        this.addRenderableWidget(deleteButton);
    }
    
    private List<String> getCategories() {
        return categoryManager.getCategories();
    }
    
    private List<String> getCustomCategories() {
        // Obter todas as categorias únicas das estruturas custom
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All"); // Categoria para mostrar todas
        
        List<StructureLoader.StructureData> clientStructures = StructureLoader.getClientStructures();
        
        for (StructureLoader.StructureData structure : clientStructures) {
            if (structure.isLocalStructure() && structure.getGuiPath() != null) {
                String guiPath = structure.getGuiPath();
                // Extrair categoria principal (antes do " > ")
                if (guiPath.contains(" > ")) {
                    String mainCategory = guiPath.split(" > ")[0];
                    categories.add(mainCategory);
                } else {
                    categories.add(guiPath);
                }
            }
        }
        
        return new ArrayList<>(categories);
    }
    
    private void initSearchBox() {
        int sidebarArea = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding();
        int searchY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding(); // Same level as headers
        int searchX = sidebarArea + GuiRenderer.getPadding();
        int searchWidth = this.width - searchX - GuiRenderer.getInfoPanelWidth() - GuiRenderer.getPadding();
        
        searchBox = new EditBox(this.font, searchX, searchY, searchWidth, 20,
                Component.translatable("gui.satisfactory_build_tool.search"));
        searchBox.setHint(Component.translatable("gui.satisfactory_build_tool.search.hint"));
        this.addRenderableWidget(searchBox);
    }
    
    private void initCategoryButtons() {
        int headerHeight = 20;
        int buttonY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + headerHeight;
        int buttonX = GuiRenderer.getPadding() - 5;
        int buttonWidth = GuiRenderer.getSidebarWidth();
        int buttonHeight = 20;
        
        List<String> categories;
        if (currentTab == Tab.BLUEPRINTS) {
            // Para blueprints, obter categorias das estruturas custom
            categories = getCustomCategories();
        } else {
            // Para builder, usar categorias normais
            categories = getCategories();
        }
        
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            int y = buttonY + i * (buttonHeight + 5);
            
            this.addRenderableWidget(Button.builder(
                    Component.literal(category),
                    b -> selectCategory(category))
                    .bounds(buttonX, y, buttonWidth, buttonHeight)
                    .build());
        }
    }
    
    private void initCustomizerCategoryButtons() {
        int headerHeight = 20;
        int buttonY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + headerHeight;
        int buttonX = GuiRenderer.getPadding() - 5;
        int buttonWidth = GuiRenderer.getSidebarWidth();
        int buttonHeight = 20;
        
        // Create two buttons: Colors and Materials
        this.addRenderableWidget(Button.builder(
                Component.literal("Colors"),
                b -> selectCustomizerCategory("Colors"))
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Materials"),
                b -> selectCustomizerCategory("Materials"))
                .bounds(buttonX, buttonY + buttonHeight + 5, buttonWidth, buttonHeight)
                .build());
    }
    
    private void selectCustomizerCategory(String category) {
        this.customizerCategory = category;
        this.clearWidgets();
        this.init();
    }
    
    private void initColorPalette() {
        int paletteY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2;
        // Center in available space (full width minus sidebar for Customizer)
        int availableWidth = this.width - GuiRenderer.getSidebarWidth() - GuiRenderer.getPadding() * 2;
        int paletteX = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() + (availableWidth - (4 * 40)) / 2;
        
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            int row = i / 4;
            int col = i % 4;
            int x = paletteX + col * 40;
            int y = paletteY + row * 40;
            
            DyeColor color = colors[i];
            int colorValue = getColorValue(color);
            this.addRenderableWidget(new ColorButton(x, y, 35, 35, color, colorValue, () -> selectColor(color)));
        }
    }
    
    private void initMaterialPalette() {
        // Render materials centered in available space
        int materialsY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2;
        int availableWidth = this.width - GuiRenderer.getSidebarWidth() - GuiRenderer.getPadding() * 2;
        int paletteX = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() + (availableWidth - (4 * 40)) / 2;
        
        // Get player progression to filter unlocked materials
        com.Infinity.Nexus.Satisfactory.BuildTool.progression.PlayerProgression progression = 
            com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler.getProgression(
                net.minecraft.client.Minecraft.getInstance().player.getUUID());
        
        java.util.List<com.Infinity.Nexus.Satisfactory.BuildTool.config.Config.MaterialEntry> unlockedMaterials = 
            com.Infinity.Nexus.Satisfactory.BuildTool.config.Config.material_blocks.stream()
                .filter(m -> progression.isMaterialUnlocked(m.name))
                .collect(java.util.stream.Collectors.toList());
        
        int row = 0, col = 0;
        for (com.Infinity.Nexus.Satisfactory.BuildTool.config.Config.MaterialEntry material : unlockedMaterials) {
            int x = paletteX + col * 40;
            int y = materialsY + row * 40;
            
            this.addRenderableWidget(new MaterialButton(x, y, 35, 35, material, () -> selectMaterial(material)));
            
            col++;
            if (col >= 4) {
                col = 0;
                row++;
            }
        }
    }
    
    private void selectMaterial(com.Infinity.Nexus.Satisfactory.BuildTool.config.Config.MaterialEntry material) {
        // Send packet to server to update BuildTool material
        com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages.sendToServer(
            new com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetMaterialC2SPacket(material.name)
        );
        this.onClose();
    }
    
    private void setTab(Tab tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
        this.clearWidgets();
        this.init();
    }
    
    private void selectCategory(String category) {
        this.selectedCategory = category;
        this.scrollOffset = 0;
        // No need to clear widgets here, we just change the data shown in the grid
    }
    
    private void selectSubCategory(String sub) {
        // Legacy/Unused for now as we show all subcategories in the grid
    }
    
    private void selectColor(DyeColor color) {
        // Send packet to server to update BuildTool color
        ModMessages.sendToServer(
            new SetColorC2SPacket(color.getId())
        );
        this.onClose();
    }
    
    private int getColorValue(DyeColor color) {
        return switch (color) {
            case WHITE -> 0xFFF0F0F0; // Alpha FF + RGB
            case ORANGE -> 0xFFFF681F;
            case MAGENTA -> 0xFFB24CD8;
            case LIGHT_BLUE -> 0xFF99BAD6;
            case YELLOW -> 0xFFFEED63;
            case LIME -> 0xFF7FCC19;
            case PINK -> 0xFFF27FA5;
            case GRAY -> 0xFF4C4C4C;
            case LIGHT_GRAY -> 0xFF9D9D97;
            case CYAN -> 0xFF4C7F99;
            case PURPLE -> 0xFF7F3FB2;
            case BLUE -> 0xFF334CB2;
            case BROWN -> 0xFF664C33;
            case GREEN -> 0xFF667F33;
            case RED -> 0xFF993333;
            case BLACK -> 0xFF191919;
        };
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // Sidebar background (for Builder, Blueprints, and Customizer)
        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS || currentTab == Tab.CUSTOMIZER) {
            graphics.fill(0, GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + 10, GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding(), this.height, ModConstants.Colors.TRANSPARENT_BLACK);
        }
        
        // Info panel background (NOT for Customizer)
        if (currentTab != Tab.CUSTOMIZER) {
            graphics.fill(this.width - GuiRenderer.getInfoPanelWidth(), GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + 10, this.width, this.height, ModConstants.Colors.TRANSPARENT_BLACK);
        }

        if (currentTab == Tab.BUILDER) {
            renderBuilderTab(graphics, mouseX, mouseY);
        } else if (currentTab == Tab.CUSTOMIZER) {
            renderCustomizerTab(graphics);
        } else if (currentTab == Tab.BLUEPRINTS) {
            renderBlueprintsTab(graphics, mouseX, mouseY);
        }
        
        // Render info panel only for Builder and Blueprints
        if (currentTab != Tab.CUSTOMIZER) {
            renderInfoPanel(graphics, mouseX, mouseY);
        }
        
        // Update button visibility
        if (selectButton != null) {
            selectButton.visible = selectedStructureInTab != null && (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS);
        }
        if (editButton != null) {
            editButton.visible = selectedStructureInTab != null && currentTab == Tab.BLUEPRINTS;
        }
        if (deleteButton != null) {
            deleteButton.visible = selectedStructureInTab != null && currentTab == Tab.BLUEPRINTS;
        }
    }
    
    private void renderBuilderTab(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw Sidebar Header
        graphics.drawString(this.font, Component.translatable("gui.satisfactory_build_tool.builder.categories"), 
                GuiRenderer.getPadding(), GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + 5, 0xFFA500, true);

        if (selectedCategory != null) {
            int currentY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2 + 20 - scrollOffset;
            int gridXStart = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() * 2;
            String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";
            
            // Get subcategories for the selected main category
            List<String> subs = categoryManager.getSubCategories(selectedCategory);
            
            // If there are no subcategories, just render the main one
            if (subs.isEmpty()) {
                List<StructureLoader.StructureData> structures = categoryManager.getFilteredStructures(selectedCategory, searchText);
                currentY = renderStructureGrid(graphics, structures, gridXStart, currentY, mouseX, mouseY);
            } else {
                for (String sub : subs) {
                    String fullPath = selectedCategory + " > " + sub;
                    List<StructureLoader.StructureData> structures = categoryManager.getFilteredStructures(fullPath, searchText);
                    
                    if (!structures.isEmpty()) {
                        // Draw Subcategory Header
                        drawSectionHeader(graphics, gridXStart, currentY, this.width - gridXStart - GuiRenderer.getInfoPanelWidth() - GuiRenderer.getPadding(), Component.literal(sub));
                        currentY += 15;
                        currentY = renderStructureGrid(graphics, structures, gridXStart, currentY, mouseX, mouseY);
                        currentY += 10; // Spacing between subcategories
                    }
                }
            }
            // Update max scroll
            maxScroll = Math.max(0, (currentY + scrollOffset) - (this.height - GuiRenderer.getPadding()));
        }
    }
    
    private void renderCustomizerTab(GuiGraphics graphics) {
        // Draw Sidebar Header
        graphics.drawString(this.font, Component.literal("Customize"), 
                GuiRenderer.getPadding(), GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + 5, 0xFFA500, true);
        
        // Title centered in available space
        int availableWidth = this.width - GuiRenderer.getSidebarWidth() - GuiRenderer.getPadding() * 2;
        int centerX = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() + availableWidth / 2;
        graphics.drawCenteredString(this.font,
                Component.literal(customizerCategory),
                centerX, GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding(), 0xFFA500);
    }
    
    private void renderBlueprintsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw Sidebar Header for categories
        graphics.drawString(this.font, Component.translatable("gui.satisfactory_build_tool.builder.categories"),
                GuiRenderer.getPadding(), GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() + 5, 0xFFA500, true);


        if (selectedCategory != null) {
            int currentY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2 + 20 - scrollOffset;
            int gridXStart = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() * 2;
            String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

            // Get custom structures (local blueprints) filtered by category and search
            List<StructureLoader.StructureData> customStructures = StructureLoader.getClientStructures().stream()
                    .filter(StructureLoader.StructureData::isLocalStructure)
                    .filter(s -> s.getGuiPath() != null)
                    .filter(s -> selectedCategory.equals("All") ||
                            s.getGuiPath().equalsIgnoreCase(selectedCategory) ||
                            s.getGuiPath().startsWith(selectedCategory + " > "))
                    .filter(s -> searchText.isEmpty() || s.getName().toLowerCase().contains(searchText))
                    .collect(Collectors.toList());

            if (customStructures.isEmpty()) {
                selectedCategory = "All";
                graphics.drawCenteredString(this.font, Component.literal("No custom structures in this category"),
                        (this.width - GuiRenderer.getInfoPanelWidth() + GuiRenderer.getSidebarWidth()) / 2, this.height / 2, 0x808080);
                return;
            }

            // Render structures in grid
            currentY = renderStructureGrid(graphics, customStructures, gridXStart, currentY, mouseX, mouseY);
            maxScroll = Math.max(0, (currentY + scrollOffset) - (this.height - GuiRenderer.getPadding()));
        }
    }
    
    private int renderStructureGrid(GuiGraphics graphics, List<StructureLoader.StructureData> structures, int gridXStart, int startY, int mouseX, int mouseY) {
        int gridX = gridXStart;
        int gridY = startY;
        int itemsInRow = 0;
        int topLimit = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2 + 20;
        
        for (StructureLoader.StructureData structure : structures) {
            if (itemsInRow >= itemsPerRow) {
                itemsInRow = 0;
                gridX = gridXStart;
                gridY += GuiRenderer.getGridItemSize() + GuiRenderer.getGridSpacing();
            }
            
            // Skip rendering if outside view bounds
            if (gridY + GuiRenderer.getGridItemSize() > topLimit && gridY < this.height - GuiRenderer.getPadding()) {
                boolean hovered = mouseX >= gridX && mouseX < gridX + GuiRenderer.getGridItemSize() &&
                                  mouseY >= gridY && mouseY < gridY + GuiRenderer.getGridItemSize();
                
                int bgColor = (selectedStructureInTab == structure) ? ModConstants.Colors.HIGHLIGHT_ORANGE : (hovered ? ModConstants.Colors.WHITE : ModConstants.Colors.TRANSPARENT_BLACK);
                graphics.fill(gridX, gridY, gridX + GuiRenderer.getGridItemSize(), gridY + GuiRenderer.getGridItemSize(), bgColor);
                
                // Draw Icon
                ResourceLocation iconToDraw = DEFAULT_ICON;
                if (structure.getIconPath() != null && !structure.getIconPath().isEmpty()) {
                    try {
                        iconToDraw = ResourceLocation.parse(structure.getIconPath());
                    } catch (Exception ignored) {}
                }
                graphics.blit(iconToDraw, gridX + 5, gridY + 5, 0, 0, GuiRenderer.getGridItemSize() - 10, GuiRenderer.getGridItemSize() - 10, GuiRenderer.getGridItemSize() - 10, GuiRenderer.getGridItemSize() - 10);
                
                // Draw name
                String name = structure.getName();
                if (this.font.width(name) > GuiRenderer.getGridItemSize() - 10) {
                    name = this.font.plainSubstrByWidth(name, GuiRenderer.getGridItemSize() - 15) + "...";
                }
                graphics.drawString(this.font, name, gridX + 5, gridY + 5, 0xFFFFFF);
                
                // Draw size
                String sizeInfo = structure.getWidth() + "x" + structure.getHeight() + "x" + structure.getLength();
                graphics.drawString(this.font, sizeInfo, gridX + 5, gridY + GuiRenderer.getGridItemSize() - 15, 0xAAAAAA);
            }
            
            gridX += GuiRenderer.getGridItemSize() + GuiRenderer.getGridSpacing();
            itemsInRow++;
        }
        
        return gridY + GuiRenderer.getGridItemSize();
    }
    
    private void drawSectionHeader(GuiGraphics graphics, int x, int y, int width, Component text) {
        graphics.fill(x, y, x + width, y + 12, ColorUtils.rgbaToInt(1f, 1f, 1f, 0.25f));
        graphics.drawCenteredString(this.font, text, x + width / 2, y + 2, 0xFFA500);
    }

    private void renderInfoPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedStructureInTab == null) return;
        
        int x = this.width - GuiRenderer.getInfoPanelWidth() + 5;
        int y = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding();
        int width = GuiRenderer.getInfoPanelWidth() - 10;
        
        // Name
        graphics.drawCenteredString(this.font, selectedStructureInTab.getName(), x + width / 2, y + 5, 0xFFFFFF);
        y += 20;
        
        // Structure (Large Icon)
        drawSectionHeader(graphics, x, y, width, Component.translatable("gui.satisfactory_build_tool.info.structure"));
        y += 15;
        
        int iconSize = 64;
        int iconX = x + (width - iconSize) / 2;
        ResourceLocation iconToDraw = DEFAULT_ICON;
        if (selectedStructureInTab.getIconPath() != null && !selectedStructureInTab.getIconPath().isEmpty()) {
            try {
                iconToDraw = ResourceLocation.parse(selectedStructureInTab.getIconPath());
            } catch (Exception ignored) {}
        }
        graphics.blit(iconToDraw, iconX, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
        y += iconSize + 5;
        
        // Description
        drawSectionHeader(graphics, x, y, width, Component.translatable("gui.satisfactory_build_tool.info.description"));
        y += 15;
        String desc = selectedStructureInTab.getDescription();
        List<FormattedCharSequence> lines = this.font.split(Component.literal(desc), width - 5);
        for (int i = 0; i < Math.min(lines.size(), 3); i++) {
            graphics.drawString(this.font, lines.get(i), x + 2, y, 0xFFFFFF, false);
            y += 10;
        }
        y += 5;
        
        // Stats
        if(selectedStructureInTab.getEnergyConsumption() >= 1) {
            drawSectionHeader(graphics, x, y, width, Component.translatable("gui.satisfactory_build_tool.info.stats"));
            y += 15;
            graphics.drawString(this.font, Component.translatable("gui.satisfactory_build_tool.info.energy"), x + 2, y, 0xAAAAAA);
            String energyText = selectedStructureInTab.getEnergyConsumption() + " MW";
            graphics.drawString(this.font, energyText, x + width - this.font.width(energyText) - 2, y, 0xFFFFFF);
            y += 15;
        }

        // Cost (3x9 grid)
        drawSectionHeader(graphics, x, y, width, Component.translatable("gui.satisfactory_build_tool.info.cost"));
        y += 15;
        
        List<StructureLoader.RequiredItem> costs;
        if (selectedStructureInTab.isOnlyItems()) {
            costs = new ArrayList<>(selectedStructureInTab.getRequiredItems());
            costs.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        } else {
            costs = selectedStructureInTab.getAggregatedCosts();
        }
        
        // Reset hover state
        hoveredCostItem = null;
        
        for (int i = 0; i < Math.min(costs.size(), 27); i++) {
            int row = i / 9;
            int col = i % 9;
            int itemX = x + col * 20;
            int itemY = y + row * 20;
            
            StructureLoader.RequiredItem req = costs.get(i);
            try {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    graphics.renderItem(stack, itemX, itemY);
                    graphics.renderItemDecorations(this.font, stack, itemX, itemY, String.valueOf(req.getCount()));
                    
                    // Check hover
                    if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                        hoveredCostItem = req;
                    }
                }
            } catch (Exception ignored) {}
        }
        
        // Render tooltip for hovered item
        if (hoveredCostItem != null) {
            try {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(hoveredCostItem.getItemId()));
                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    graphics.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            } catch (Exception ignored) {}
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button); // Only left click
        
        // Handle grid clicks
        if (currentTab == Tab.BUILDER) {
            if (selectedCategory != null) {
                int currentY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2 + 20 - scrollOffset;
                int gridXStart = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() * 2;
                String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";
                List<String> subs = categoryManager.getSubCategories(selectedCategory);
                
                if (subs.isEmpty()) {
                    List<StructureLoader.StructureData> structures = categoryManager.getFilteredStructures(selectedCategory, searchText);
                    List<StructureLoader.StructureData> filtered = structures.stream()
                        .filter(s -> searchText.isEmpty() || s.getName().toLowerCase().contains(searchText))
                        .collect(Collectors.toList());
                    if (checkGridClick(mouseX, mouseY, filtered, gridXStart, currentY)) return true;
                } else {
                    for (String sub : subs) {
                        String fullPath = selectedCategory + " > " + sub;
                        List<StructureLoader.StructureData> structures = categoryManager.getFilteredStructures(fullPath, searchText);
                        List<StructureLoader.StructureData> filtered = structures.stream()
                            .filter(s -> searchText.isEmpty() || s.getName().toLowerCase().contains(searchText))
                            .collect(Collectors.toList());
                        if (!filtered.isEmpty()) {
                            currentY += 15; // Header offset
                            if (checkGridClick(mouseX, mouseY, filtered, gridXStart, currentY)) return true;
                            currentY = calculateGridEnd(filtered, currentY) + 10;
                        }
                    }
                }
            }
        } else if (currentTab == Tab.BLUEPRINTS) {
            String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";
            List<StructureLoader.StructureData> customStructures = StructureLoader.getClientStructures().stream()
                    .filter(s -> s.getCategory().equalsIgnoreCase("custom"))
                    .filter(s -> searchText.isEmpty() || s.getName().toLowerCase().contains(searchText))
                    .collect(Collectors.toList());
            
            int gridXStart = GuiRenderer.getSidebarWidth() + GuiRenderer.getPadding() * 2;
            int currentY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2 + 20 - scrollOffset;
            if (checkGridClick(mouseX, mouseY, customStructures, gridXStart, currentY)) return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int calculateGridEnd(List<StructureLoader.StructureData> structures, int startY) {
        int rows = (int) Math.ceil((double) structures.size() / itemsPerRow);
        return startY + rows * (GuiRenderer.getGridItemSize() + GuiRenderer.getGridSpacing()) - GuiRenderer.getGridSpacing();
    }

    private boolean checkGridClick(double mouseX, double mouseY, List<StructureLoader.StructureData> structures, int gridXStart, int startY) {
        int contentY = GuiRenderer.getPadding() + GuiRenderer.getTabHeight() + GuiRenderer.getPadding() * 2 + 20;
        int gridX = gridXStart;
        int gridY = startY;
        int itemsInRow = 0;
        
        for (StructureLoader.StructureData structure : structures) {
            if (itemsInRow >= itemsPerRow) {
                itemsInRow = 0;
                gridX = gridXStart;
                gridY += GuiRenderer.getGridItemSize() + GuiRenderer.getGridSpacing();
            }
            
            if (mouseX >= gridX && mouseX < gridX + GuiRenderer.getGridItemSize() &&
                mouseY >= gridY && mouseY < gridY + GuiRenderer.getGridItemSize() &&
                gridY >= contentY && gridY < this.height - GuiRenderer.getPadding()) {
                
                selectedStructureInTab = structure;
                return true;
            }
            
            gridX += GuiRenderer.getGridItemSize() + GuiRenderer.getGridSpacing();
            itemsInRow++;
        }
        return false;
    }


    private void selectStructure(StructureLoader.StructureData structure) {
        if (structure == null) return;
        
        // CRITICAL: Force reload of BuildTool's structure cache to ensure sync
        BuildTool.loadStructures();
        
        // Find index in global list
        List<StructureLoader.StructureData> all = StructureLoader.getClientStructures();
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getName().equals(structure.getName())) {
                index = i;
                break;
            }
        }
        
        if (index != -1) {
            BuildTool.setSelectedStructureIndex(index);
            Minecraft.getInstance().player.sendSystemMessage(
                Component.translatable("message.satisfactory_build_tool.structure_selected", structure.getName())
            );
            this.onClose();
        }
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Handle scrolling
        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    private void openEditor() {
        if (selectedStructureInTab != null && currentTab == Tab.BLUEPRINTS) {
            // Recarregar as estruturas antes de abrir o editor para garantir dados atualizados
            StructureLoader.loadClientStructures();
            
            // Encontrar a estrutura atualizada pelo nome
            List<StructureLoader.StructureData> customStructures = StructureLoader.getClientStructures().stream()
                    .filter(s -> s.getCategory().equalsIgnoreCase("custom"))
                    .collect(Collectors.toList());
            
            StructureLoader.StructureData structureToEdit = customStructures.stream()
                    .filter(s -> s.getName().equals(selectedStructureInTab.getName()))
                    .findFirst()
                    .orElse(selectedStructureInTab);
            
            Minecraft.getInstance().setScreen(new BlueprintEditorScreen(structureToEdit, () -> {
                StructureLoader.loadClientStructures();
                this.clearWidgets();
                this.init();
            }));
        }
    }
    
    private void deleteStructure() {
        if (selectedStructureInTab != null && currentTab == Tab.BLUEPRINTS) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(
                this::confirmDelete,
                Component.translatable("gui.satisfactory_build_tool.delete_confirm_title"),
                Component.translatable("gui.satisfactory_build_tool.delete_confirm_message", selectedStructureInTab.getName())
            ));
        }
    }

    private void confirmDelete(boolean confirmed) {
        if (confirmed && selectedStructureInTab != null) {
            String structureName = selectedStructureInTab.getName();
            Path clientDir = fileManager.getClientStructuresDir();
            
            boolean deleted = fileManager.deleteStructureFile(selectedStructureInTab, clientDir);
            
            if (deleted) {
                // Mensagem de sucesso
                Minecraft.getInstance().player.sendSystemMessage(
                    Component.translatable("message.satisfactory_build_tool.structure_deleted", structureName)
                );
            } else {
                // Mensagem de erro
                Minecraft.getInstance().player.sendSystemMessage(
                    Component.translatable("message.satisfactory_build_tool.delete_failed", structureName)
                        .withStyle(ChatFormatting.RED)
                );
            }
        }
        
        // Force reload structures in BuildTool to sync indices
        BuildTool.loadStructures();
        
        // Reabre a tela, voltando para a aba de blueprints
        Minecraft.getInstance().setScreen(new BuildToolScreen(Tab.BLUEPRINTS));
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
