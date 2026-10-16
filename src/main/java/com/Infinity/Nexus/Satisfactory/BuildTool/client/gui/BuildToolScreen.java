package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetColorC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetMaterialC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureFileManager;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.*;

public class BuildToolScreen extends Screen {

    private static final ResourceLocation DEFAULT_ICON =
            ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "textures/gui/icons/factory_default.png");

    // Authentic Satisfactory Builder Menu Palette
    private static final int COL_HEADER_BG       = 0xF225272B;
    private static final int COL_SIDEBAR_BG      = 0xE62B2E33;
    private static final int COL_CENTER_BG       = 0xD8181A1D;
    private static final int COL_INSPECTOR_BG    = 0xE623262A;
    private static final int COL_SEARCH_BG       = 0xFF52565E;
    private static final int COL_SUBHEADER_BG    = 0xFF3E4147;
    private static final int COL_FICSIT_ORANGE   = 0xFFE59344;
    private static final int COL_CARD_HOVER      = 0x44FFFFFF;
    private static final int COL_CARD_SELECTED   = 0x55E59344;
    private static final int TEXT_WHITE          = 0xFFFFFFFF;
    private static final int TEXT_GRAY           = 0xFFB0B5BC;
    private static final int TEXT_DARK           = 0xFF181A1D;

    private static final int HEADER_H            = 22;
    private static final int SIDEBAR_W           = 58;
    private static final int CAT_BTN_H           = 42;
    private static final int ITEM_CELL_W         = 66;
    private static final int ITEM_CELL_H         = 64;
    private static final int ITEM_SPACING_X      = 10;
    private static final int ITEM_SPACING_Y      = 8;

    public enum Tab { BUILDER, CUSTOMIZER, BLUEPRINTS }

    private final StructureCategoryManager categoryManager = new StructureCategoryManager();
    private final StructureFileManager fileManager = new StructureFileManager();

    private Tab currentTab = Tab.BUILDER;
    private String selectedCategory = null;
    private String customizerCategory = "Colors";

    // Window bounds (centered modal like Satisfactory)
    private int winX, winY, winW, winH;
    private int centerColX, centerColW, inspectorX, inspectorW;
    private int itemsPerRow = 4;

    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private StructureLoader.StructureData selectedStructureInTab = null;
    private StructureLoader.StructureData hoveredStructure = null;
    private StructureLoader.RequiredItem hoveredCostItem = null;

    public BuildToolScreen() {
        super(Component.translatable("gui.satisfactory_build_tool.build_tool_screen.title"));
        parseStructures();
    }

    public BuildToolScreen(Tab initialTab) {
        this();
        this.currentTab = initialTab;
    }

    private void parseStructures() {
        Collection<StructureLoader.StructureData> structures = StructureLoader.getClientStructures();
        categoryManager.parseStructures(structures);

        if (currentTab == Tab.BLUEPRINTS) {
            if (selectedCategory == null) selectedCategory = "All";
        } else {
            List<String> categories = categoryManager.getCategories();
            if (!categories.isEmpty() && (selectedCategory == null || !categories.contains(selectedCategory))) {
                selectedCategory = categories.get(0);
            }
        }

        if (selectedStructureInTab == null) {
            selectedStructureInTab = BuildTool.getSelectedStructure();
        }
    }

    @Override
    protected void init() {
        super.init();
        parseStructures();

        // Calculate centered modal window dimensions (leaves space at bottom for the keybind bar)
        this.winW = Math.min(this.width - 40, 620);
        this.winH = Math.min(this.height - 54, 340);
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(12, (this.height - this.winH - 32) / 2);

        this.inspectorW = Math.max(150, (int) (this.winW * 0.28f));
        this.centerColX = this.winX + SIDEBAR_W;
        this.centerColW = (currentTab == Tab.CUSTOMIZER)
                ? (this.winW - SIDEBAR_W)
                : (this.winW - SIDEBAR_W - this.inspectorW);
        this.inspectorX = this.centerColX + this.centerColW;

        this.itemsPerRow = Math.max(1, (this.centerColW - 20 + ITEM_SPACING_X) / (ITEM_CELL_W + ITEM_SPACING_X));

        // Search Box inside the top of the Center Column
        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS) {
            searchBox = new EditBox(this.font, this.centerColX + 8, this.winY + HEADER_H + 6,
                    this.centerColW - 28, 14, Component.literal("Search for Building..."));
            searchBox.setBordered(false);
            searchBox.setTextColor(TEXT_WHITE);
            searchBox.setHint(Component.literal("Search for Building...").withStyle(ChatFormatting.GRAY));
            this.addRenderableWidget(searchBox);
        }

        // Customizer Color / Material Palettes
        if (currentTab == Tab.CUSTOMIZER) {
            if (customizerCategory.equals("Colors")) {
                initColorPalette();
            } else {
                initMaterialPalette();
            }
        }
    }

    private List<String> getActiveCategories() {
        if (currentTab == Tab.CUSTOMIZER) {
            return List.of("Colors", "Materials");
        }
        if (currentTab == Tab.BLUEPRINTS) {
            Set<String> categories = new LinkedHashSet<>();
            categories.add("All");
            for (StructureLoader.StructureData s : StructureLoader.getClientStructures()) {
                if (s.isLocalStructure() && s.getGuiPath() != null) {
                    String path = s.getGuiPath();
                    categories.add(path.contains(" > ") ? path.split(" > ")[0].trim() : path);
                }
            }
            return new ArrayList<>(categories);
        }
        return categoryManager.getCategories();
    }

    private void initColorPalette() {
        int paletteY = this.winY + HEADER_H + 36;
        int paletteX = this.centerColX + (this.centerColW - (4 * 44)) / 2;

        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            int row = i / 4;
            int col = i % 4;
            DyeColor color = colors[i];
            this.addRenderableWidget(new ColorButton(
                    paletteX + col * 44, paletteY + row * 44, 38, 38,
                    color, getColorValue(color), () -> selectColor(color)
            ));
        }
    }

    private void initMaterialPalette() {
        int materialsY = this.winY + HEADER_H + 36;
        int paletteX = this.centerColX + (this.centerColW - (4 * 44)) / 2;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        List<Config.MaterialEntry> unlocked = Config.material_blocks.stream()
                .filter(m -> ProgressionHandler.isMaterialUnlocked(player, m.name))
                .toList();

        int row = 0, col = 0;
        for (Config.MaterialEntry material : unlocked) {
            this.addRenderableWidget(new MaterialButton(
                    paletteX + col * 44, materialsY + row * 44, 38, 38,
                    material, () -> selectMaterial(material)
            ));
            if (++col >= 4) {
                col = 0;
                row++;
            }
        }
    }

    private void setTab(Tab tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
        this.selectedCategory = null;
        this.clearWidgets();
        this.init();
    }

    private void selectColor(DyeColor color) {
        ModMessages.sendToServer(new SetColorC2SPacket(color.getId()));
        this.onClose();
    }

    private void selectMaterial(Config.MaterialEntry material) {
        ModMessages.sendToServer(new SetMaterialC2SPacket(material.name));
        this.onClose();
    }

    private int getColorValue(DyeColor color) {
        return switch (color) {
            case WHITE -> 0xFFF0F0F0;
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

    // ==========================================
    // SATISFACTORY UI RENDERING
    // ==========================================

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Subtle vignette so the world stays visible around the Builder window just like Satisfactory
        g.fill(0, 0, this.width, this.height, 0x38000000);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        this.hoveredStructure = null;

        // 1. Top Header Bar (Hammer Icon + Mode Tabs + Right 'X' Close Button)
        g.fill(winX, winY, winX + winW, winY + HEADER_H, COL_HEADER_BG);
        drawHammerIcon(g, winX + 7, winY + 5, TEXT_WHITE);

        renderHeaderTab(g, "Builder", Tab.BUILDER, winX + 22, winY, 64, mouseX, mouseY);
        renderHeaderTab(g, "Customizer", Tab.CUSTOMIZER, winX + 90, winY, 76, mouseX, mouseY);
        renderHeaderTab(g, "Blueprints", Tab.BLUEPRINTS, winX + 170, winY, 74, mouseX, mouseY);

        // Close 'X' button on top-right of window
        int closeX = winX + winW - 24;
        boolean closeHover = mouseX >= closeX && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H;
        if (closeHover) {
            g.fill(closeX, winY, winX + winW, winY + HEADER_H, 0xFF40444B);
        }
        g.fill(closeX - 1, winY + 3, closeX, winY + HEADER_H - 3, 0xFF3A3D42);
        g.drawCenteredString(this.font, "✕", closeX + 12, winY + 7, TEXT_WHITE);

        int bodyY = winY + HEADER_H;
        int bodyH = winH - HEADER_H;

        // 2. Left Category Sidebar (Tall Icon + Label Tiles)
        g.fill(winX, bodyY, winX + SIDEBAR_W, bodyY + bodyH, COL_SIDEBAR_BG);
        renderCategorySidebar(g, winX, bodyY, mouseX, mouseY);

        // 3. Center Column (Search Bar + Numbered Subcategory Bars + Building Icons)
        g.fill(centerColX, bodyY, centerColX + centerColW, bodyY + bodyH, COL_CENTER_BG);

        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS) {
            // Search Bar Background & Magnifying Glass Icon
            int searchBarY = bodyY + 2;
            g.fill(centerColX + 2, searchBarY, centerColX + centerColW - 2, searchBarY + 18, COL_SEARCH_BG);
            drawMagnifyingGlassIcon(g, centerColX + centerColW - 16, searchBarY + 4, 0xFFD0D4DC);

            int gridTop = bodyY + 22;
            int gridBottom = bodyY + bodyH - 2;
            g.enableScissor(centerColX, gridTop, centerColX + centerColW, gridBottom);
            if (currentTab == Tab.BUILDER) {
                renderBuilderGrid(g, gridTop, mouseX, mouseY);
            } else {
                renderBlueprintsGrid(g, gridTop, mouseX, mouseY);
            }
            g.disableScissor();
        } else {
            g.drawCenteredString(this.font, customizerCategory, centerColX + centerColW / 2, bodyY + 12, COL_FICSIT_ORANGE);
        }

        // 4. Right Inspector Pane
        if (currentTab != Tab.CUSTOMIZER) {
            g.fill(inspectorX, bodyY, winX + winW, bodyY + bodyH, COL_INSPECTOR_BG);
            g.fill(inspectorX, bodyY, inspectorX + 1, bodyY + bodyH, 0xFF141518);
            renderInspectorPane(g, inspectorX, bodyY, inspectorW, bodyH, mouseX, mouseY);
        }

        // Subtle outer border around the Builder window
        g.renderOutline(winX, winY, winW, winH, 0xFF141518);

        // 5. Render SearchBox & Customizer Widgets
        super.render(g, mouseX, mouseY, partialTick);

        // 6. Render Bottom Satisfactory Keybind Legend Bar below the window
        renderBottomKeybindBar(g);

        // 7. Render item tooltip if hovering a cost slot
        if (hoveredCostItem != null) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(hoveredCostItem.getItemId()));
            if (item != null && item != Items.AIR) {
                List<Component> tip = List.of(
                        item.getDescription(),
                        Component.literal("Required: " + hoveredCostItem.getCount() + "x").withStyle(ChatFormatting.GOLD)
                );
                g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
            }
        }
    }

    private void renderHeaderTab(GuiGraphics g, String label, Tab tab, int x, int y, int w, int mouseX, int mouseY) {
        boolean active = (this.currentTab == tab);
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEADER_H;
        int txtColor = active ? TEXT_WHITE : (hovered ? 0xFFD8DEE9 : 0xFF888E96);
        g.drawString(this.font, label, x, y + 7, txtColor, false);
        if (active) {
            g.fill(x, y + HEADER_H - 2, x + this.font.width(label), y + HEADER_H, COL_FICSIT_ORANGE);
        }
    }

    private void renderCategorySidebar(GuiGraphics g, int startX, int startY, int mouseX, int mouseY) {
        List<String> categories = getActiveCategories();
        int curY = startY;

        for (String cat : categories) {
            if (curY + CAT_BTN_H > winY + winH) break;

            boolean isSelected = (currentTab == Tab.CUSTOMIZER)
                    ? cat.equalsIgnoreCase(customizerCategory)
                    : cat.equalsIgnoreCase(selectedCategory);
            boolean isHovered = mouseX >= startX && mouseX < startX + SIDEBAR_W &&
                    mouseY >= curY && mouseY < curY + CAT_BTN_H;

            int bg = isSelected ? COL_FICSIT_ORANGE : (isHovered ? 0xFF383C42 : 0x00000000);
            if (bg != 0) {
                g.fill(startX, curY, startX + SIDEBAR_W, curY + CAT_BTN_H, bg);
            }
            // Subtle divider line between sidebar categories
            g.fill(startX, curY + CAT_BTN_H - 1, startX + SIDEBAR_W, curY + CAT_BTN_H, 0xFF222428);

            // Procedural Category Icon centered in upper half of tile
            drawCategoryIcon(g, cat, startX + SIDEBAR_W / 2, curY + 13, TEXT_WHITE);

            // Category Label centered at bottom of tile
            g.pose().pushPose();
            g.pose().translate(startX + SIDEBAR_W / 2f, curY + CAT_BTN_H - 10, 0);
            g.pose().scale(0.68f, 0.68f, 1.0f);
            String label = cat.length() > 13 ? cat.substring(0, 12) + "." : cat;
            g.drawString(this.font, label, -this.font.width(label) / 2, 0,
                    isSelected ? TEXT_WHITE : TEXT_GRAY, false);
            g.pose().popPose();

            curY += CAT_BTN_H;
        }
    }

    private void renderBuilderGrid(GuiGraphics g, int gridTop, int mouseX, int mouseY) {
        if (selectedCategory == null) return;

        int currentY = gridTop + 4 - scrollOffset;
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        List<String> subs = categoryManager.getSubCategories(selectedCategory);

        if (subs.isEmpty()) {
            List<StructureLoader.StructureData> list = categoryManager.getFilteredStructures(selectedCategory, searchText);
            drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 4, "1. " + selectedCategory);
            currentY += 18;
            currentY = renderBuildingCells(g, list, centerColX + 10, currentY, gridTop, mouseX, mouseY);
        } else {
            int subIndex = 1;
            for (String sub : subs) {
                String fullPath = selectedCategory + " > " + sub;
                List<StructureLoader.StructureData> list = categoryManager.getFilteredStructures(fullPath, searchText);
                if (!list.isEmpty()) {
                    String numberedTitle = sub.matches("^\\d+\\..*") ? sub : (subIndex + ". " + sub);
                    drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 4, numberedTitle);
                    currentY += 18;
                    currentY = renderBuildingCells(g, list, centerColX + 10, currentY, gridTop, mouseX, mouseY);
                    currentY += 8;
                    subIndex++;
                }
            }
        }

        maxScroll = Math.max(0, (currentY + scrollOffset) - (winY + winH - 10));
    }

    private void renderBlueprintsGrid(GuiGraphics g, int gridTop, int mouseX, int mouseY) {
        if (selectedCategory == null) return;

        int currentY = gridTop + 4 - scrollOffset;
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        List<StructureLoader.StructureData> customStructures = StructureLoader.getClientStructures().stream()
                .filter(StructureLoader.StructureData::isLocalStructure)
                .filter(s -> s.getGuiPath() != null)
                .filter(s -> selectedCategory.equals("All") ||
                        s.getGuiPath().equalsIgnoreCase(selectedCategory) ||
                        s.getGuiPath().startsWith(selectedCategory + " > "))
                .filter(s -> searchText.isEmpty() || s.getName().toLowerCase().contains(searchText))
                .toList();

        drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 4, "1. Saved Blueprints");
        currentY += 18;

        if (customStructures.isEmpty()) {
            g.drawCenteredString(this.font, "No saved Blueprints found.",
                    centerColX + centerColW / 2, gridTop + 50, TEXT_GRAY);
            return;
        }

        currentY = renderBuildingCells(g, customStructures, centerColX + 10, currentY, gridTop, mouseX, mouseY);
        maxScroll = Math.max(0, (currentY + scrollOffset) - (winY + winH - 10));
    }

    private void drawNumberedSubcategoryBar(GuiGraphics g, int x, int y, int width, String text) {
        g.fill(x, y, x + width, y + 13, COL_SUBHEADER_BG);
        g.drawString(this.font, text, x + 6, y + 3, TEXT_WHITE, false);
    }

    private int renderBuildingCells(GuiGraphics g, List<StructureLoader.StructureData> structures,
                                    int startX, int startY, int clipTop, int mouseX, int mouseY) {
        int cellX = startX;
        int cellY = startY;
        int col = 0;

        for (StructureLoader.StructureData structure : structures) {
            if (col >= this.itemsPerRow) {
                col = 0;
                cellX = startX;
                cellY += ITEM_CELL_H + ITEM_SPACING_Y;
            }

            boolean hovered = mouseX >= cellX && mouseX < cellX + ITEM_CELL_W &&
                    mouseY >= cellY && mouseY < cellY + ITEM_CELL_H &&
                    mouseY >= clipTop && mouseY < winY + winH;
            boolean selected = (selectedStructureInTab != null && selectedStructureInTab.equals(structure));

            if (hovered) {
                this.hoveredStructure = structure;
                g.fill(cellX, cellY, cellX + ITEM_CELL_W, cellY + ITEM_CELL_H, COL_CARD_HOVER);
            } else if (selected) {
                g.fill(cellX, cellY, cellX + ITEM_CELL_W, cellY + ITEM_CELL_H, COL_CARD_SELECTED);
                g.renderOutline(cellX, cellY, ITEM_CELL_W, ITEM_CELL_H, COL_FICSIT_ORANGE);
            }

            // Building Icon (36x36 centered at top of cell)
            renderStructureIcon(g, structure.getIconPath(), cellX + (ITEM_CELL_W - 32) / 2, cellY + 4, 32);

            // Wrapped Building Name below icon (matches Satisfactory's 1-2 line centered labels)
            List<FormattedCharSequence> nameLines = this.font.split(Component.literal(structure.getName()), ITEM_CELL_W + 14);
            g.pose().pushPose();
            g.pose().translate(cellX + ITEM_CELL_W / 2f, cellY + 41, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            for (int lineIdx = 0; lineIdx < Math.min(2, nameLines.size()); lineIdx++) {
                FormattedCharSequence seq = nameLines.get(lineIdx);
                g.drawString(this.font, seq, -this.font.width(seq) / 2, lineIdx * 10,
                        (hovered || selected) ? COL_FICSIT_ORANGE : TEXT_WHITE, false);
            }
            g.pose().popPose();

            cellX += ITEM_CELL_W + ITEM_SPACING_X;
            col++;
        }

        return cellY + ITEM_CELL_H;
    }

    private void renderInspectorPane(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        hoveredCostItem = null;
        StructureLoader.StructureData target = (hoveredStructure != null) ? hoveredStructure : selectedStructureInTab;
        if (target == null) return;

        int padX = x + 8;
        int innerW = w - 16;
        int curY = y + 8;

        // Building Title & Specs
        g.drawString(this.font, target.getName(), padX, curY, COL_FICSIT_ORANGE, false);
        curY += 12;

        String dims = target.getWidth() + "×" + target.getLength() + "×" + target.getHeight() + "m";
        String power = target.getEnergyConsumption() > 0 ? ("  •  " + target.getEnergyConsumption() + " MW") : "";
        g.pose().pushPose();
        g.pose().translate(padX, curY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(this.font, "Footprint: " + dims + power, 0, 0, TEXT_GRAY, false);
        g.pose().popPose();
        curY += 12;

        g.fill(padX, curY, padX + innerW, curY + 1, 0xFF3E4147);
        curY += 6;

        // Description
        String desc = target.getDescription();
        if (desc == null || desc.isEmpty()) desc = "Standard FICSIT modular structure.";
        List<FormattedCharSequence> lines = this.font.split(Component.literal(desc), (int) (innerW / 0.75f));
        g.pose().pushPose();
        g.pose().translate(padX, curY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            g.drawString(this.font, lines.get(i), 0, i * 10, TEXT_WHITE, false);
        }
        g.pose().popPose();
        curY += Math.min(4, lines.size()) * 8 + 10;

        // Cost Section (Square Satisfactory Cost Cards with dark bottom quantity strip)
        drawNumberedSubcategoryBar(g, padX, curY, innerW, "Cost");
        curY += 17;

        List<StructureLoader.RequiredItem> costs = target.isOnlyItems()
                ? target.getRequiredItems()
                : target.getAggregatedCosts();

        Player player = Minecraft.getInstance().player;
        int cardSize = 30;
        int cols = Math.max(1, innerW / (cardSize + 4));

        for (int i = 0; i < Math.min(costs.size(), 12); i++) {
            int c = i % cols;
            int r = i / cols;
            int slotX = padX + c * (cardSize + 4);
            int slotY = curY + r * (cardSize + 4);

            StructureLoader.RequiredItem req = costs.get(i);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
            if (item == null || item == Items.AIR) continue;

            int have = countItemInInventory(player, item);
            boolean enough = (player != null && player.isCreative()) || have >= req.getCount();

            // Satisfactory-style cost card (light gray top + dark charcoal bottom count bar)
            g.fill(slotX, slotY, slotX + cardSize, slotY + cardSize - 8, 0xFF8A8E94);
            g.fill(slotX, slotY + cardSize - 8, slotX + cardSize, slotY + cardSize, 0xFF282A2E);
            g.renderOutline(slotX, slotY, cardSize, cardSize, enough ? 0xFF3E4147 : 0xFFD94040);

            g.renderItem(new ItemStack(item), slotX + (cardSize - 16) / 2, slotY + 2);

            String amtStr = String.valueOf(req.getCount());
            g.pose().pushPose();
            g.pose().translate(slotX + cardSize - 2, slotY + cardSize - 6, 200);
            g.pose().scale(0.65f, 0.65f, 1.0f);
            g.drawString(this.font, amtStr, -this.font.width(amtStr), 0, enough ? TEXT_WHITE : 0xFFFF5555, false);
            g.pose().popPose();

            if (mouseX >= slotX && mouseX < slotX + cardSize && mouseY >= slotY && mouseY < slotY + cardSize) {
                hoveredCostItem = req;
            }
        }

        // Blueprints Tab Edit / Delete Buttons at the bottom of the Inspector
        if (currentTab == Tab.BLUEPRINTS && selectedStructureInTab != null) {
            int btnY = y + h - 24;
            int halfW = (innerW - 4) / 2;

            boolean editHov = mouseX >= padX && mouseX < padX + halfW && mouseY >= btnY && mouseY < btnY + 18;
            g.fill(padX, btnY, padX + halfW, btnY + 18, editHov ? COL_FICSIT_ORANGE : COL_SUBHEADER_BG);
            g.drawCenteredString(this.font, "Edit", padX + halfW / 2, btnY + 5, TEXT_WHITE);

            int delX = padX + halfW + 4;
            boolean delHov = mouseX >= delX && mouseX < delX + halfW && mouseY >= btnY && mouseY < btnY + 18;
            g.fill(delX, btnY, delX + halfW, btnY + 18, delHov ? 0xFFD94040 : COL_SUBHEADER_BG);
            g.drawCenteredString(this.font, "Delete", delX + halfW / 2, btnY + 5, TEXT_WHITE);
        }
    }

    /**
     * Renders Satisfactory's signature bottom keybind legend bar right under the Builder window.
     */
    private void renderBottomKeybindBar(GuiGraphics g) {
        var options = Minecraft.getInstance().options;
        String[][] callouts = {
                {"Select", options.keyUse.getTranslatedKeyMessage().getString()},
                {"Customizer", "Tab"},
                {"Quick Switch", "Z"},
                {"Search", "Space Bar"},
                {"Close", "Escape"}
        };

        int spacing = 6;
        int totalW = 0;
        int[] boxWidths = new int[callouts.length];

        for (int i = 0; i < callouts.length; i++) {
            int w1 = this.font.width(callouts[i][0]) + 14;
            int w2 = (int) (this.font.width(callouts[i][1]) * 0.75f) + 14;
            boxWidths[i] = Math.max(w1, w2);
            totalW += boxWidths[i] + (i < callouts.length - 1 ? spacing : 0);
        }

        int curX = (this.width - totalW) / 2;
        int barY = this.winY + this.winH + 6;

        for (int i = 0; i < callouts.length; i++) {
            int bw = boxWidths[i];
            // Dark translucent top card
            g.fill(curX, barY, curX + bw, barY + 21, 0xCC181A1D);
            g.drawCenteredString(this.font, callouts[i][0], curX + bw / 2, barY + 3, TEXT_WHITE);

            // Orange keybind pill at bottom
            int pillW = (int) (this.font.width(callouts[i][1]) * 0.75f) + 8;
            int pillX = curX + (bw - pillW) / 2;
            g.fill(pillX, barY + 12, pillX + pillW, barY + 20, COL_FICSIT_ORANGE);

            g.pose().pushPose();
            g.pose().translate(curX + bw / 2f, barY + 13.5f, 0);
            g.pose().scale(0.7f, 0.7f, 1.0f);
            g.drawString(this.font, callouts[i][1], -this.font.width(callouts[i][1]) / 2, 0, TEXT_DARK, false);
            g.pose().popPose();

            curX += bw + spacing;
        }
    }

    // ==========================================
    // PROCEDURAL SATISFACTORY VECTOR ICONS
    // ==========================================

    private void drawHammerIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 1, x + 9, y + 4, color); // Hammer head
        g.fill(x + 5, y + 4, x + 7, y + 11, color); // Handle
    }

    private void drawMagnifyingGlassIcon(GuiGraphics g, int x, int y, int color) {
        g.renderOutline(x, y, 7, 7, color);
        g.fill(x + 6, y + 6, x + 9, y + 9, color);
    }

    private void drawCategoryIcon(GuiGraphics g, String category, int cx, int cy, int color) {
        String lower = category.toLowerCase();
        if (lower.contains("special") || lower.contains("progression")) {
            // Checkmark icon
            g.fill(cx - 5, cy, cx - 2, cy + 3, color);
            g.fill(cx - 2, cy + 2, cx + 1, cy - 1, color);
            g.fill(cx + 1, cy - 1, cx + 5, cy - 5, color);
        } else if (lower.contains("prod") || lower.contains("manufact")) {
            // Factory sawtooth roof + chimney
            g.fill(cx - 6, cy - 6, cx - 4, cy + 4, color);
            g.fill(cx - 6, cy - 1, cx + 6, cy + 4, color);
            g.fill(cx - 3, cy - 3, cx, cy - 1, color);
            g.fill(cx + 2, cy - 3, cx + 5, cy - 1, color);
        } else if (lower.contains("power") || lower.contains("energy")) {
            // Lightning bolt
            g.fill(cx - 1, cy - 6, cx + 3, cy - 1, color);
            g.fill(cx - 3, cy - 2, cx + 3, cy + 1, color);
            g.fill(cx - 2, cy, cx + 1, cy + 5, color);
        } else if (lower.contains("logist") || lower.contains("convey") || lower.contains("transp")) {
            // Conveyor box + arrow
            g.renderOutline(cx - 6, cy - 5, 12, 10, color);
            g.fill(cx - 3, cy - 1, cx + 2, cy + 1, color);
            g.fill(cx + 1, cy - 3, cx + 4, cy + 3, color);
        } else if (lower.contains("organ") || lower.contains("stor")) {
            // Organization hierarchy tree
            g.fill(cx - 2, cy - 6, cx + 2, cy - 3, color);
            g.fill(cx - 5, cy - 1, cx + 5, cy, color);
            g.fill(cx - 6, cy + 1, cx - 3, cy + 4, color);
            g.fill(cx - 1, cy + 1, cx + 2, cy + 4, color);
            g.fill(cx + 3, cy + 1, cx + 6, cy + 4, color);
        } else if (lower.contains("found")) {
            // Isometric Foundation slab
            g.fill(cx - 4, cy - 4, cx + 4, cy - 2, color);
            g.fill(cx - 6, cy - 2, cx + 6, cy + 2, color);
            g.fill(cx - 4, cy + 2, cx + 4, cy + 4, color);
        } else {
            // Isometric Wall / Default Cube
            g.fill(cx - 5, cy - 5, cx, cy + 4, color);
            g.fill(cx + 1, cy - 5, cx + 5, cy + 4, color);
        }
    }

    private void renderStructureIcon(GuiGraphics g, String iconPath, int x, int y, int size) {
        if (iconPath != null && !iconPath.isEmpty() && !iconPath.endsWith(".png")) {
            try {
                ResourceLocation itemId = ResourceLocation.parse(iconPath);
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        g.pose().pushPose();
                        g.pose().translate(x, y, 50);
                        float scale = size / 16.0f;
                        g.pose().scale(scale, scale, 1.0f);
                        g.renderItem(new ItemStack(item), 0, 0);
                        g.pose().popPose();
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        ResourceLocation tex = DEFAULT_ICON;
        if (iconPath != null && !iconPath.isEmpty()) {
            try {
                tex = ResourceLocation.parse(iconPath);
            } catch (Exception ignored) {}
        }
        g.blit(tex, x, y, 0, 0, size, size, size, size);
    }

    private int countItemInInventory(Player player, Item target) {
        if (player == null) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == target) total += s.getCount();
        }
        return total;
    }

    // ==========================================
    // INPUT & INTERACTION
    // ==========================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Space Bar focuses the search box when not already typing
        if (keyCode == GLFW.GLFW_KEY_SPACE && searchBox != null && !searchBox.isFocused()) {
            this.setFocused(searchBox);
            searchBox.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            Tab next = switch (currentTab) {
                case BUILDER -> Tab.CUSTOMIZER;
                case CUSTOMIZER -> Tab.BLUEPRINTS;
                case BLUEPRINTS -> Tab.BUILDER;
            };
            setTab(next);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 1. Check Header Bar Tabs & Close 'X'
            if (mouseY >= winY && mouseY < winY + HEADER_H) {
                if (mouseX >= winX + 22 && mouseX < winX + 86) { setTab(Tab.BUILDER); return true; }
                if (mouseX >= winX + 90 && mouseX < winX + 166) { setTab(Tab.CUSTOMIZER); return true; }
                if (mouseX >= winX + 170 && mouseX < winX + 244) { setTab(Tab.BLUEPRINTS); return true; }
                if (mouseX >= winX + winW - 24 && mouseX < winX + winW) { this.onClose(); return true; }
            }

            // 2. Check Left Sidebar Categories
            int bodyY = winY + HEADER_H;
            if (mouseX >= winX && mouseX < winX + SIDEBAR_W && mouseY >= bodyY && mouseY < winY + winH) {
                int idx = (int) ((mouseY - bodyY) / CAT_BTN_H);
                List<String> cats = getActiveCategories();
                if (idx >= 0 && idx < cats.size()) {
                    if (currentTab == Tab.CUSTOMIZER) {
                        this.customizerCategory = cats.get(idx);
                        this.clearWidgets();
                        this.init();
                    } else {
                        this.selectedCategory = cats.get(idx);
                        this.scrollOffset = 0;
                    }
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                    }
                    return true;
                }
            }

            // 3. Check Blueprints Edit / Delete Buttons in Inspector
            if (currentTab == Tab.BLUEPRINTS && selectedStructureInTab != null) {
                int padX = inspectorX + 8;
                int innerW = inspectorW - 16;
                int halfW = (innerW - 4) / 2;
                int btnY = winY + winH - 24;
                if (mouseY >= btnY && mouseY < btnY + 18) {
                    if (mouseX >= padX && mouseX < padX + halfW) { openEditor(); return true; }
                    if (mouseX >= padX + halfW + 4 && mouseX < padX + innerW) { deleteStructure(); return true; }
                }
            }

            // 4. Check Building Item Click in Center Grid
            if (hoveredStructure != null) {
                if (currentTab == Tab.BUILDER) {
                    // Authentic Satisfactory behavior: LMB immediately selects and closes!
                    selectStructure(hoveredStructure);
                } else {
                    if (hoveredStructure.equals(selectedStructureInTab)) {
                        selectStructure(hoveredStructure);
                    } else {
                        selectedStructureInTab = hoveredStructure;
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectStructure(StructureLoader.StructureData structure) {
        if (structure == null) return;
        BuildTool.loadStructures();

        List<StructureLoader.StructureData> all = StructureLoader.getClientStructures();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getName().equals(structure.getName())) {
                BuildTool.setSelectedStructureIndex(i);
                this.onClose();
                return;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == Tab.BUILDER || currentTab == Tab.BLUEPRINTS) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void openEditor() {
        if (selectedStructureInTab != null && currentTab == Tab.BLUEPRINTS) {
            StructureLoader.loadClientStructures();
            Minecraft.getInstance().setScreen(new BlueprintEditorScreen(selectedStructureInTab, () -> {
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
            Path clientDir = fileManager.getClientStructuresDir();
            fileManager.deleteStructureFile(selectedStructureInTab, clientDir);
            selectedStructureInTab = null;
        }
        BuildTool.loadStructures();
        Minecraft.getInstance().setScreen(new BuildToolScreen(Tab.BLUEPRINTS));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}