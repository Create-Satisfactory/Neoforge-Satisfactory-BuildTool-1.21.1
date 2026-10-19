package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetColorC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetMaterialC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.CustomizerManager;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PreviewRenderer;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureFileManager;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
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

    // Authentic Satisfactory UI Palette
    private static final int COL_HEADER_BG       = 0xF232353B;
    private static final int COL_TAB_INACTIVE    = 0xFF52565E;
    private static final int COL_TAB_HOVER       = 0xFF646973;
    private static final int COL_TAB_ACTIVE      = 0xFF32353B;
    private static final int COL_SIDEBAR_BG      = 0xE626282C;
    private static final int COL_CENTER_BG       = 0xDF16181B;
    private static final int COL_INSPECTOR_BG    = 0xEB23262A;
    private static final int COL_SEARCH_BG       = 0xFF5E626A;
    private static final int COL_SUBHEADER_BG    = 0xFF3E4147;
    private static final int COL_FICSIT_ORANGE   = 0xFFE59344;
    private static final int COL_CIRCLE_RING     = 0xFF7D828A;
    private static final int COL_CIRCLE_DARK     = 0xFF454950;
    private static final int TEXT_WHITE          = 0xFFFFFFFF;
    private static final int TEXT_GRAY           = 0xFFB0B5BC;
    private static final int TEXT_DARK           = 0xFF181A1D;

    private static final int HEADER_H            = 22;
    private static final int SIDEBAR_W           = 62;
    private static final int CAT_BTN_H           = 44;
    private static final int ITEM_CELL_W         = 66;
    private static final int ITEM_CELL_H         = 68;
    private static final int ITEM_SPACING_X      = 8;
    private static final int ITEM_SPACING_Y      = 8;

    public enum Tab { BUILDER, CUSTOMIZER, BLUEPRINTS }

    // Customizer Data Models
    private enum CustomizerType { SWATCH, FINISH, MATERIAL, PATTERN }

    private record CustomizerEntry(
            String id,
            String name,
            String subCategory,
            CustomizerType type,
            int primaryColor,
            int secondaryColor,
            int colorIndex,
            String materialOrPatternKey,
            String iconBlockId,
            String description
    ) {}

    private static final List<CustomizerEntry> PATTERN_ENTRIES = buildPatternEntries();

    private final StructureCategoryManager categoryManager = new StructureCategoryManager();
    private final StructureFileManager fileManager = new StructureFileManager();

    private Tab currentTab = Tab.BUILDER;
    private String selectedCategory = null;
    private String customizerCategory = "Colors";

    private int winX, winY, winW, winH;
    private int centerColX, centerColW, inspectorX, inspectorW;
    private int itemsPerRow = 5;

    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private StructureLoader.StructureData selectedStructureInTab = null;
    private StructureLoader.StructureData hoveredStructure = null;

    private CustomizerEntry selectedCustomizerEntry = null;
    private CustomizerEntry hoveredCustomizerEntry = null;

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
        } else if (currentTab == Tab.BUILDER) {
            List<String> categories = categoryManager.getCategories();
            if (!categories.isEmpty() && (selectedCategory == null || !categories.contains(selectedCategory))) {
                selectedCategory = categories.get(0);
            }
        }

        if (selectedStructureInTab == null) {
            selectedStructureInTab = BuildTool.getSelectedStructure();
        }
        List<CustomizerEntry> colorEntries = getColorEntries();
        if (selectedCustomizerEntry == null && !colorEntries.isEmpty()) {
            selectedCustomizerEntry = colorEntries.get(0);
        }
    }

    @Override
    protected void init() {
        super.init();
        parseStructures();

        this.winW = Math.min(this.width - 36, 650);
        this.winH = Math.min(this.height - 52, 350);
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(10, (this.height - this.winH - 30) / 2);

        this.inspectorW = Math.max(162, (int) (this.winW * 0.28f));
        this.centerColX = this.winX + SIDEBAR_W;
        this.centerColW = this.winW - SIDEBAR_W - this.inspectorW;
        this.inspectorX = this.centerColX + this.centerColW;

        this.itemsPerRow = Math.max(1, (this.centerColW - 18 + ITEM_SPACING_X) / (ITEM_CELL_W + ITEM_SPACING_X));

        // Search Box at the top of the Center Column (all 3 tabs have a search bar!)
        String hintText = (currentTab == Tab.CUSTOMIZER) ? "Search in Customizer..." : "Search for Building...";
        searchBox = new EditBox(this.font, this.centerColX + 8, this.winY + HEADER_H + 6,
                this.centerColW - 28, 14, Component.literal(hintText));
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT_WHITE);
        searchBox.setHint(Component.literal(hintText).withStyle(ChatFormatting.GRAY));
        this.addRenderableWidget(searchBox);
    }

    private List<String> getActiveCategories() {
        if (currentTab == Tab.CUSTOMIZER) {
            return List.of("Colors", "Materials", "Patterns");
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

    private void setTab(Tab tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
        List<CustomizerEntry> colorEntries = getColorEntries();
        if (tab == Tab.CUSTOMIZER && selectedCustomizerEntry == null && !colorEntries.isEmpty()) {
            selectedCustomizerEntry = colorEntries.get(0);
        }
        this.clearWidgets();
        this.init();
    }

    // ==========================================
    // MAIN RENDER PIPELINE
    // ==========================================

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x38000000);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        this.hoveredStructure = null;
        this.hoveredCustomizerEntry = null;
        this.hoveredCostItem = null;

        // 1. Top Header Bar with Boxed Tabs (Builder | Customizer | Blueprints)
        g.fill(winX, winY, winX + winW, winY + HEADER_H, COL_HEADER_BG);
        renderBoxedHeaderTab(g, "Builder", Tab.BUILDER, winX + 2, winY + 2, 92, HEADER_H - 2, mouseX, mouseY);
        renderBoxedHeaderTab(g, "Customizer", Tab.CUSTOMIZER, winX + 96, winY + 2, 98, HEADER_H - 2, mouseX, mouseY);
        renderBoxedHeaderTab(g, "Blueprints", Tab.BLUEPRINTS, winX + 196, winY + 2, 98, HEADER_H - 2, mouseX, mouseY);

        // Close 'X' button on top-right
        int closeX = winX + winW - 26;
        boolean closeHover = mouseX >= closeX && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H;
        if (closeHover) {
            g.fill(closeX, winY, winX + winW, winY + HEADER_H, 0xFF4A4E57);
        }
        g.fill(closeX - 1, winY + 2, closeX, winY + HEADER_H - 2, 0xFF222428);
        g.drawCenteredString(this.font, "✕", closeX + 13, winY + 7, TEXT_WHITE);

        int bodyY = winY + HEADER_H;
        int bodyH = winH - HEADER_H;

        // 2. Left Category Sidebar
        g.fill(winX, bodyY, winX + SIDEBAR_W, bodyY + bodyH, COL_SIDEBAR_BG);
        renderCategorySidebar(g, winX, bodyY, mouseX, mouseY);

        // 3. Center Column (Search Bar + Scrollable Subcategory Grid)
        g.fill(centerColX, bodyY, centerColX + centerColW, bodyY + bodyH, COL_CENTER_BG);

        int searchBarY = bodyY + 2;
        g.fill(centerColX + 2, searchBarY, centerColX + centerColW - 2, searchBarY + 18, COL_SEARCH_BG);
        drawMagnifyingGlassIcon(g, centerColX + centerColW - 16, searchBarY + 4, 0xFFE0E4EC);

        int gridTop = bodyY + 22;
        int gridBottom = bodyY + bodyH - 2;
        g.enableScissor(centerColX, gridTop, centerColX + centerColW, gridBottom);
        if (currentTab == Tab.BUILDER) {
            renderBuilderGrid(g, gridTop, mouseX, mouseY);
        } else if (currentTab == Tab.CUSTOMIZER) {
            renderCustomizerGrid(g, gridTop, mouseX, mouseY);
        } else {
            renderBlueprintsGrid(g, gridTop, mouseX, mouseY);
        }
        g.disableScissor();

        // Thin vertical scrollbar track on the right edge of the center column
        renderScrollbar(g, centerColX + centerColW - 4, gridTop, gridBottom - gridTop);

        // 4. Right Inspector Pane (Rendered in all 3 tabs!)
        g.fill(inspectorX, bodyY, winX + winW, bodyY + bodyH, COL_INSPECTOR_BG);
        g.fill(inspectorX, bodyY, inspectorX + 1, bodyY + bodyH, 0xFF141518);
        if (currentTab == Tab.CUSTOMIZER) {
            renderCustomizerInspector(g, inspectorX, bodyY, inspectorW, bodyH);
        } else {
            renderStructureInspector(g, inspectorX, bodyY, inspectorW, bodyH, mouseX, mouseY);
        }

        // Window Outline
        g.renderOutline(winX, winY, winW, winH, 0xFF141518);

        // 5. Render SearchBox widget
        super.render(g, mouseX, mouseY, partialTick);

        // 6. Render Bottom Satisfactory Keybind Legend Bar
        renderBottomKeybindBar(g);

        // 7. Cost Tooltip
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

    private void renderBoxedHeaderTab(GuiGraphics g, String label, Tab tab, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean active = (this.currentTab == tab);
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = active ? COL_TAB_ACTIVE : (hovered ? COL_TAB_HOVER : COL_TAB_INACTIVE);
        g.fill(x, y, x + w, y + h, bg);

        // Tab Icon (Hammer / Paintbrush / Blueprint)
        if (tab == Tab.BUILDER) {
            drawHammerIcon(g, x + 6, y + 4, TEXT_WHITE);
        } else if (tab == Tab.CUSTOMIZER) {
            drawPaintbrushIcon(g, x + 6, y + 4, TEXT_WHITE);
        } else {
            drawBlueprintIcon(g, x + 6, y + 4, TEXT_WHITE);
        }

        g.drawString(this.font, label, x + 20, y + 6, TEXT_WHITE, false);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int trackH) {
        g.fill(x, y, x + 3, y + trackH, 0x66000000);
        if (maxScroll > 0) {
            int thumbH = Math.max(20, (int) ((float) trackH / (trackH + maxScroll) * trackH));
            int thumbY = y + (int) (((float) scrollOffset / maxScroll) * (trackH - thumbH));
            g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFF9EA4B0);
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
            g.fill(startX, curY + CAT_BTN_H - 1, startX + SIDEBAR_W, curY + CAT_BTN_H, 0xFF1E2024);

            // Category Icon
            drawCategoryIcon(g, cat, startX + SIDEBAR_W / 2, curY + 14, TEXT_WHITE);

            // Category Name
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

    // ==========================================
    // CUSTOMIZER TAB GRID & INSPECTOR
    // ==========================================

    private static List<CustomizerEntry> getColorEntries() {
        List<CustomizerEntry> list = new ArrayList<>();
        List<CustomizerManager.SwatchDef> swatches = CustomizerManager.getAllSwatches();
        for (int i = 0; i < swatches.size(); i++) {
            CustomizerManager.SwatchDef s = swatches.get(i);
            list.add(new CustomizerEntry(
                    s.id(), s.name(), s.subCategory(),
                    s.isFinish() ? CustomizerType.FINISH : CustomizerType.SWATCH,
                    s.getPrimaryColorInt(), s.getSecondaryColorInt(),
                    i, s.name(), s.primaryBlock(), s.description()
            ));
        }
        return list;
    }

    private List<CustomizerEntry> getMaterialEntriesForPlayer() {
        Player player = Minecraft.getInstance().player;
        List<CustomizerEntry> list = new ArrayList<>();
        for (CustomizerManager.MaterialDef mat : CustomizerManager.getAllMaterials()) {
            if (player != null && !mat.unlockedByDefault() && !ProgressionHandler.isMaterialUnlocked(player, mat.id())) {
                continue;
            }
            list.add(new CustomizerEntry(
                    mat.id(), mat.name(), mat.subCategory(), CustomizerType.MATERIAL,
                    0xFF888888, 0xFF444444, -1, mat.id(), mat.iconBlock(), mat.description()
            ));
        }
        return list;
    }

    private void renderCustomizerGrid(GuiGraphics g, int gridTop, int mouseX, int mouseY) {
        int currentY = gridTop + 4 - scrollOffset;
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        List<CustomizerEntry> sourceList = switch (customizerCategory) {
            case "Materials" -> getMaterialEntriesForPlayer();
            case "Patterns" -> PATTERN_ENTRIES;
            default -> getColorEntries();
        };

        // Group by subCategory in insertion order
        Map<String, List<CustomizerEntry>> grouped = new LinkedHashMap<>();
        for (CustomizerEntry entry : sourceList) {
            if (!searchText.isEmpty() && !entry.name().toLowerCase().contains(searchText)) continue;
            grouped.computeIfAbsent(entry.subCategory(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<String, List<CustomizerEntry>> group : grouped.entrySet()) {
            drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 8, group.getKey());
            currentY += 17;
            currentY = renderCustomizerCells(g, group.getValue(), centerColX + 8, currentY, gridTop, mouseX, mouseY);
            currentY += 8;
        }

        maxScroll = Math.max(0, (currentY + scrollOffset) - (winY + winH - 10));
    }

    private int renderCustomizerCells(GuiGraphics g, List<CustomizerEntry> entries,
                                      int startX, int startY, int clipTop, int mouseX, int mouseY) {
        int cellX = startX;
        int cellY = startY;
        int col = 0;

        Player player = Minecraft.getInstance().player;
        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        int activeColorIdx = (held.getItem() instanceof BuildTool) ? BuildTool.getColor(held) : -1;
        String activeMat = (held.getItem() instanceof BuildTool) ? BuildTool.getMaterial(held) : "";
        String activePat = (held.getItem() instanceof BuildTool) ? BuildTool.getPattern(held) : "";

        for (CustomizerEntry entry : entries) {
            if (col >= this.itemsPerRow) {
                col = 0;
                cellX = startX;
                cellY += ITEM_CELL_H + ITEM_SPACING_Y;
            }

            boolean hovered = mouseX >= cellX && mouseX < cellX + ITEM_CELL_W &&
                    mouseY >= cellY && mouseY < cellY + ITEM_CELL_H &&
                    mouseY >= clipTop && mouseY < winY + winH;

            boolean isEquipped = switch (entry.type()) {
                case SWATCH, FINISH -> entry.colorIndex() == activeColorIdx;
                case MATERIAL -> entry.materialOrPatternKey().equalsIgnoreCase(activeMat);
                case PATTERN -> entry.materialOrPatternKey().equalsIgnoreCase(activePat);
            };

            if (hovered) {
                this.hoveredCustomizerEntry = entry;
                g.fill(cellX, cellY, cellX + ITEM_CELL_W, cellY + ITEM_CELL_H, COL_FICSIT_ORANGE);
            } else if (isEquipped) {
                g.renderOutline(cellX, cellY, ITEM_CELL_W, ITEM_CELL_H, COL_FICSIT_ORANGE);
            }

            int circleCx = cellX + ITEM_CELL_W / 2;
            int circleCy = cellY + 21;
            int radius = 17;

            renderCustomizerCircleBadge(g, entry, circleCx, circleCy, radius);

            List<FormattedCharSequence> lines = this.font.split(Component.literal(entry.name()), ITEM_CELL_W + 16);
            g.pose().pushPose();
            g.pose().translate(cellX + ITEM_CELL_W / 2f, cellY + 44, 0);
            g.pose().scale(0.72f, 0.72f, 1.0f);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                FormattedCharSequence seq = lines.get(i);
                g.drawString(this.font, seq, -this.font.width(seq) / 2, i * 10, TEXT_WHITE, false);
            }
            g.pose().popPose();

            cellX += ITEM_CELL_W + ITEM_SPACING_X;
            col++;
        }

        return cellY + ITEM_CELL_H;
    }

    /**
     * Renders the signature Satisfactory circular Customizer badge:
     * - SWATCH: Grey outer ring + 45-degree diagonal split circle (top-left Primary, bottom-right Secondary)
     * - FINISH: Grey outer ring + metallic shaded sphere
     * - MATERIAL: Dark grey circle + 3D Block Item rendered inside
     * - PATTERN: Grey circle + white vector pattern symbol
     */
    private void renderCustomizerCircleBadge(GuiGraphics g, CustomizerEntry entry, int cx, int cy, int r) {
        if (entry.type() == CustomizerType.SWATCH) {
            drawFilledCircle(g, cx, cy, r, COL_CIRCLE_RING);
            drawDiagonalSplitCircle(g, cx, cy, r - 3, entry.primaryColor(), entry.secondaryColor());
        } else if (entry.type() == CustomizerType.FINISH) {
            drawFilledCircle(g, cx, cy, r, COL_CIRCLE_RING);
            drawMetallicFinishCircle(g, cx, cy, r - 3, entry.primaryColor(), entry.secondaryColor());
        } else if (entry.type() == CustomizerType.MATERIAL) {
            drawFilledCircle(g, cx, cy, r, COL_CIRCLE_DARK);
            renderStructureIcon(g, entry.iconBlockId(), cx - 11, cy - 11, 22);
        } else if (entry.type() == CustomizerType.PATTERN) {
            drawFilledCircle(g, cx, cy, r, COL_CIRCLE_RING);
            drawPatternSymbol(g, entry.materialOrPatternKey(), cx, cy, r - 3);
        }
    }

    private void renderCustomizerInspector(GuiGraphics g, int x, int y, int w, int h) {
        CustomizerEntry target = (hoveredCustomizerEntry != null) ? hoveredCustomizerEntry : selectedCustomizerEntry;
        if (target == null) return;

        int padX = x + 10;
        int innerW = w - 20;
        int curY = y + 8;

        // 1. Centered Title + Horizontal Divider Line
        g.drawCenteredString(this.font, target.name(), x + w / 2, curY, TEXT_WHITE);
        curY += 13;
        g.fill(padX, curY, padX + innerW, curY + 1, 0xFF8A8E94);
        curY += 10;

        // 2. Large Center Preview Area (76px tall)
        int previewCx = x + w / 2;
        int previewCy = curY + 36;
        if (target.type() == CustomizerType.MATERIAL) {
            renderStructureIcon(g, target.iconBlockId(), previewCx - 28, previewCy - 28, 56);
        } else if (target.type() == CustomizerType.PATTERN) {
            drawPatternFoundationPreview(g, target, previewCx, previewCy);
        } else {
            renderStructureIcon(g, target.iconBlockId(), previewCx - 28, previewCy - 28, 56);
            renderCustomizerCircleBadge(g, target, previewCx + 20, previewCy + 14, 14);
        }
        curY += 78;

        // 3. "Info" Section Header + Horizontal Line
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, curY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        String infoLbl = "Info";
        g.drawString(this.font, infoLbl, -this.font.width(infoLbl) / 2, 0, TEXT_GRAY, false);
        g.pose().popPose();
        curY += 9;
        g.fill(padX, curY, padX + innerW, curY + 1, 0xFF8A8E94);
        curY += 6;

        // Description Paragraph
        List<FormattedCharSequence> lines = this.font.split(Component.literal(target.description()), (int) (innerW / 0.78f));
        g.pose().pushPose();
        g.pose().translate(padX, curY, 0);
        g.pose().scale(0.78f, 0.78f, 1.0f);
        for (int i = 0; i < Math.min(7, lines.size()); i++) {
            g.drawString(this.font, lines.get(i), 0, i * 10, TEXT_WHITE, false);
        }
        g.pose().popPose();

        // 4. Bottom "Stats" Section Header + Horizontal Line
        int statsY = y + h - 42;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, statsY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        String statsLbl = "Stats";
        g.drawString(this.font, statsLbl, -this.font.width(statsLbl) / 2, 0, TEXT_GRAY, false);
        g.pose().popPose();
        g.fill(padX, statsY + 9, padX + innerW, statsY + 10, 0xFF8A8E94);

        if (target.iconBlockId() != null && !target.iconBlockId().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(padX, statsY + 15, 0);
            g.pose().scale(0.72f, 0.72f, 1.0f);
            g.drawString(this.font, "Block: " + target.iconBlockId(), 0, 0, TEXT_GRAY, false);
            g.pose().popPose();
        }
    }

    // ==========================================
    // BUILDER & BLUEPRINTS GRIDS & INSPECTOR
    // ==========================================

    private void renderBuilderGrid(GuiGraphics g, int gridTop, int mouseX, int mouseY) {
        if (selectedCategory == null) return;

        int currentY = gridTop + 4 - scrollOffset;
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        List<String> subs = categoryManager.getSubCategories(selectedCategory);

        if (subs.isEmpty()) {
            List<StructureLoader.StructureData> list = categoryManager.getFilteredStructures(selectedCategory, searchText);
            drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 8, "1. " + selectedCategory);
            currentY += 17;
            currentY = renderBuildingCells(g, list, centerColX + 8, currentY, gridTop, mouseX, mouseY);
        } else {
            int subIndex = 1;
            for (String sub : subs) {
                String fullPath = selectedCategory + " > " + sub;
                List<StructureLoader.StructureData> list = categoryManager.getFilteredStructures(fullPath, searchText);
                if (!list.isEmpty()) {
                    String numberedTitle = sub.matches("^\\d+\\..*") ? sub : (subIndex + ". " + sub);
                    drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 8, numberedTitle);
                    currentY += 17;
                    currentY = renderBuildingCells(g, list, centerColX + 8, currentY, gridTop, mouseX, mouseY);
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

        drawNumberedSubcategoryBar(g, centerColX + 2, currentY, centerColW - 8, "1. Saved Blueprints");
        currentY += 17;

        if (customStructures.isEmpty()) {
            g.drawCenteredString(this.font, "No saved Blueprints found.",
                    centerColX + centerColW / 2, gridTop + 50, TEXT_GRAY);
            return;
        }

        currentY = renderBuildingCells(g, customStructures, centerColX + 8, currentY, gridTop, mouseX, mouseY);
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
                g.fill(cellX, cellY, cellX + ITEM_CELL_W, cellY + ITEM_CELL_H, COL_FICSIT_ORANGE);
            } else if (selected) {
                g.renderOutline(cellX, cellY, ITEM_CELL_W, ITEM_CELL_H, COL_FICSIT_ORANGE);
            }

            renderStructureIcon(g, structure.getIconPath(), cellX + (ITEM_CELL_W - 32) / 2, cellY + 4, 32);

            List<FormattedCharSequence> nameLines = this.font.split(Component.literal(structure.getName()), ITEM_CELL_W + 14);
            g.pose().pushPose();
            g.pose().translate(cellX + ITEM_CELL_W / 2f, cellY + 42, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            for (int lineIdx = 0; lineIdx < Math.min(2, nameLines.size()); lineIdx++) {
                FormattedCharSequence seq = nameLines.get(lineIdx);
                g.drawString(this.font, seq, -this.font.width(seq) / 2, lineIdx * 10, TEXT_WHITE, false);
            }
            g.pose().popPose();

            cellX += ITEM_CELL_W + ITEM_SPACING_X;
            col++;
        }

        return cellY + ITEM_CELL_H;
    }

    private void renderStructureInspector(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        StructureLoader.StructureData target = (hoveredStructure != null) ? hoveredStructure : selectedStructureInTab;
        if (target == null) return;

        int padX = x + 10;
        int innerW = w - 20;
        int curY = y + 8;

        // 1. Centered Title + Horizontal Line
        g.drawCenteredString(this.font, target.getName(), x + w / 2, curY, TEXT_WHITE);
        curY += 13;
        g.fill(padX, curY, padX + innerW, curY + 1, 0xFF8A8E94);
        curY += 8;

        // 2. Large Center Icon Preview (52x52)
        renderStructureIcon(g, target.getIconPath(), x + (w - 52) / 2, curY, 52);
        curY += 58;

        // 3. "Info" Section Header + Horizontal Line
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, curY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        String infoLbl = "Info";
        g.drawString(this.font, infoLbl, -this.font.width(infoLbl) / 2, 0, TEXT_GRAY, false);
        g.pose().popPose();
        curY += 9;
        g.fill(padX, curY, padX + innerW, curY + 1, 0xFF8A8E94);
        curY += 6;

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

        // 4. "Stats" Section Header + Horizontal Line
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, curY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        String statsLbl = "Stats";
        g.drawString(this.font, statsLbl, -this.font.width(statsLbl) / 2, 0, TEXT_GRAY, false);
        g.pose().popPose();
        curY += 9;
        g.fill(padX, curY, padX + innerW, curY + 1, 0xFF8A8E94);
        curY += 6;

        String dims = "Footprint: " + target.getWidth() + "×" + target.getLength() + "×" + target.getHeight() + "m";
        String power = target.getEnergyConsumption() > 0 ? ("  •  " + target.getEnergyConsumption() + " MW") : "";
        g.pose().pushPose();
        g.pose().translate(padX, curY, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(this.font, dims + power, 0, 0, COL_FICSIT_ORANGE, false);
        g.pose().popPose();
        curY += 12;

        // Cost Cards (automatically reflects per-material cost overrides if active)
        Player player = Minecraft.getInstance().player;
        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        String activeMat = (held.getItem() instanceof BuildTool) ? BuildTool.getMaterial(held) : "";
        List<StructureLoader.RequiredItem> costs = target.getRequiredItemsForMaterial(activeMat);

        int cardSize = 28;
        int cols = Math.max(1, innerW / (cardSize + 4));

        for (int i = 0; i < Math.min(costs.size(), 8); i++) {
            int c = i % cols;
            int r = i / cols;
            int slotX = padX + c * (cardSize + 4);
            int slotY = curY + r * (cardSize + 4);

            StructureLoader.RequiredItem req = costs.get(i);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
            if (item == null || item == Items.AIR) continue;

            int have = countItemInInventory(player, item);
            boolean enough = (player != null && player.isCreative()) || have >= req.getCount();

            g.fill(slotX, slotY, slotX + cardSize, slotY + cardSize - 8, 0xFF8A8E94);
            g.fill(slotX, slotY + cardSize - 8, slotX + cardSize, slotY + cardSize, 0xFF282A2E);
            g.renderOutline(slotX, slotY, cardSize, cardSize, enough ? 0xFF3E4147 : 0xFFD94040);

            g.renderItem(new ItemStack(item), slotX + (cardSize - 16) / 2, slotY + 1);

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

        // Blueprints Tab Edit / Delete Buttons
        if (currentTab == Tab.BLUEPRINTS && selectedStructureInTab != null) {
            int btnY = y + h - 22;
            int halfW = (innerW - 4) / 2;

            boolean editHov = mouseX >= padX && mouseX < padX + halfW && mouseY >= btnY && mouseY < btnY + 16;
            g.fill(padX, btnY, padX + halfW, btnY + 16, editHov ? COL_FICSIT_ORANGE : COL_SUBHEADER_BG);
            g.drawCenteredString(this.font, "Edit", padX + halfW / 2, btnY + 4, TEXT_WHITE);

            int delX = padX + halfW + 4;
            boolean delHov = mouseX >= delX && mouseX < delX + halfW && mouseY >= btnY && mouseY < btnY + 16;
            g.fill(delX, btnY, delX + halfW, btnY + 16, delHov ? 0xFFD94040 : COL_SUBHEADER_BG);
            g.drawCenteredString(this.font, "Delete", delX + halfW / 2, btnY + 4, TEXT_WHITE);
        }
    }

    // ==========================================
    // PROCEDURAL CIRCLE & VECTOR ICON HELPERS
    // ==========================================

    private void drawFilledCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /**
     * Draws Satisfactory's 45-degree diagonal split circle swatch (top-left Primary, bottom-right Secondary).
     */
    private void drawDiagonalSplitCircle(GuiGraphics g, int cx, int cy, int r, int primaryCol, int secondaryCol) {
        for (int dy = -r; dy <= r; dy++) {
            int maxDx = (int) Math.sqrt(r * r - dy * dy);
            int splitDx = Math.clamp(-dy, -maxDx, maxDx);
            if (splitDx > -maxDx) {
                g.fill(cx - maxDx, cy + dy, cx + splitDx, cy + dy + 1, primaryCol);
            }
            if (maxDx > splitDx) {
                g.fill(cx + splitDx, cy + dy, cx + maxDx + 1, cy + dy + 1, secondaryCol);
            }
        }
    }

    /**
     * Draws a glossy metallic sphere for Customizer -> Colors -> 3. Finishes.
     */
    private void drawMetallicFinishCircle(GuiGraphics g, int cx, int cy, int r, int baseCol, int highlightCol) {
        drawFilledCircle(g, cx, cy, r, baseCol);
        drawFilledCircle(g, cx - r / 4, cy - r / 4, Math.max(2, r / 2), highlightCol);
        drawFilledCircle(g, cx - r / 3, cy - r / 3, Math.max(1, r / 4), 0xFFFFFFFF);
    }

    /**
     * Draws the procedural white vector icon inside each Customizer -> Patterns circle badge.
     */
    private void drawPatternSymbol(GuiGraphics g, String patKey, int cx, int cy, int r) {
        int c = TEXT_WHITE;
        if (patKey.equals("remove")) {
            g.fill(cx - 2, cy - 8, cx + 2, cy - 4, c);
            g.fill(cx - 8, cy - 2, cx - 4, cy + 2, c);
            g.fill(cx + 4, cy - 2, cx + 8, cy + 2, c);
            g.fill(cx - 2, cy + 4, cx + 2, cy + 8, c);
            drawFilledCircle(g, cx + 5, cy + 5, 5, COL_FICSIT_ORANGE);
            g.drawString(this.font, "×", cx + 3, cy + 1, TEXT_DARK, false);
            return;
        }
        if (patKey.startsWith("num_")) {
            String digit = patKey.substring(4);
            g.pose().pushPose();
            g.pose().translate(cx, cy - 6, 0);
            g.pose().scale(1.5f, 1.5f, 1.0f);
            g.drawString(this.font, digit, -this.font.width(digit) / 2, 0, c, false);
            g.pose().popPose();
            return;
        }

        boolean prohibited = patKey.startsWith("no_");
        String baseKey = prohibited ? patKey.substring(3) : patKey;

        switch (baseKey) {
            case "arrow_up" -> {
                g.fill(cx - 1, cy - 6, cx + 2, cy + 8, c);
                g.fill(cx - 4, cy - 4, cx + 5, cy - 2, c);
                g.fill(cx - 2, cy - 8, cx + 3, cy - 4, c);
            }
            case "arrow_left" -> {
                g.fill(cx + 2, cy - 2, cx + 5, cy + 7, c);
                g.fill(cx - 5, cy - 4, cx + 5, cy - 1, c);
                g.fill(cx - 7, cy - 6, cx - 3, cy + 1, c);
            }
            case "arrow_right" -> {
                g.fill(cx - 4, cy - 2, cx - 1, cy + 7, c);
                g.fill(cx - 4, cy - 4, cx + 6, cy - 1, c);
                g.fill(cx + 4, cy - 6, cx + 8, cy + 1, c);
            }
            case "arrow_uturn" -> {
                g.fill(cx + 2, cy - 5, cx + 5, cy + 7, c);
                g.fill(cx - 4, cy - 6, cx + 5, cy - 3, c);
                g.fill(cx - 4, cy - 5, cx - 1, cy + 3, c);
                g.fill(cx - 6, cy + 2, cx + 1, cy + 5, c);
            }
            case "icon_factory" -> {
                g.fill(cx - 7, cy - 6, cx - 4, cy + 6, c);
                g.fill(cx - 7, cy, cx + 7, cy + 6, c);
                g.fill(cx - 3, cy - 3, cx + 1, cy, c);
                g.fill(cx + 2, cy - 3, cx + 6, cy, c);
            }
            case "icon_power" -> {
                g.fill(cx - 1, cy - 8, cx + 4, cy - 1, c);
                g.fill(cx - 4, cy - 2, cx + 4, cy + 2, c);
                g.fill(cx - 3, cy + 1, cx + 1, cy + 8, c);
            }
            case "icon_storage" -> {
                g.renderOutline(cx - 7, cy - 5, 14, 10, c);
                g.fill(cx - 8, cy - 6, cx + 8, cy - 3, c);
                g.fill(cx - 2, cy - 1, cx + 2, cy + 1, c);
            }
            case "icon_nuclear" -> {
                drawFilledCircle(g, cx, cy, 2, c);
                g.fill(cx - 6, cy - 6, cx - 2, cy - 2, c);
                g.fill(cx + 2, cy - 6, cx + 6, cy - 2, c);
                g.fill(cx - 2, cy + 3, cx + 2, cy + 7, c);
            }
            case "icon_liquid" -> {
                g.fill(cx - 1, cy - 7, cx + 2, cy - 3, c);
                g.fill(cx - 3, cy - 3, cx + 4, cy + 1, c);
                drawFilledCircle(g, cx, cy + 2, 5, c);
            }
            case "icon_cross" -> {
                for (int i = -6; i <= 6; i++) {
                    g.fill(cx + i - 1, cy + i - 1, cx + i + 2, cy + i + 2, c);
                    g.fill(cx + i - 1, cy - i - 1, cx + i + 2, cy - i + 2, c);
                }
            }
            case "icon_pioneer" -> {
                drawFilledCircle(g, cx, cy - 3, 4, c);
                g.fill(cx - 6, cy + 3, cx + 6, cy + 8, c);
            }
            case "icon_tractor", "icon_truck", "icon_explorer", "icon_cart" -> {
                g.fill(cx - 6, cy - 4, cx + 4, cy + 3, c);
                g.fill(cx + 2, cy - 1, cx + 7, cy + 3, c);
                drawFilledCircle(g, cx - 4, cy + 5, 3, c);
                drawFilledCircle(g, cx + 4, cy + 5, 3, c);
            }
            case "icon_parking" -> {
                g.pose().pushPose();
                g.pose().translate(cx, cy - 6, 0);
                g.pose().scale(1.5f, 1.5f, 1.0f);
                g.drawString(this.font, "P", -this.font.width("P") / 2, 0, c, false);
                g.pose().popPose();
            }
            case "line_center" -> g.fill(cx - 1, cy - r + 2, cx + 1, cy + r - 2, c);
            case "line_corner" -> {
                g.fill(cx - 1, cy - r + 2, cx + 1, cy + 1, c);
                g.fill(cx - 1, cy - 1, cx + r - 2, cy + 1, c);
            }
            case "line_split" -> {
                g.fill(cx - r + 2, cy + 2, cx + r - 2, cy + 4, c);
                g.fill(cx - 1, cy - r + 3, cx + 1, cy + 3, c);
            }
            case "line_cross" -> {
                g.fill(cx - r + 2, cy - 1, cx + r - 2, cy + 1, c);
                g.fill(cx - 1, cy - r + 2, cx + 1, cy + r - 2, c);
            }
            case "line_side" -> g.fill(cx - r + 4, cy - r + 4, cx - r + 6, cy + r - 4, c);
            case "line_double" -> {
                g.fill(cx - r + 4, cy - r + 4, cx - r + 6, cy + r - 4, c);
                g.fill(cx + r - 6, cy - r + 4, cx + r - 4, cy + r - 4, c);
            }
            case "line_dotted" -> {
                for (int dy = -r + 3; dy < r - 3; dy += 5) {
                    g.fill(cx - 1, cy + dy, cx + 1, cy + dy + 3, c);
                }
            }
            case "path_straight" -> {
                g.fill(cx - 4, cy - r + 2, cx + 4, cy + r - 2, 0xFFD0D4DC);
                g.fill(cx - 6, cy - r + 2, cx - 5, cy + r - 2, c);
                g.fill(cx + 5, cy - r + 2, cx + 6, cy + r - 2, c);
            }
            case "path_corner" -> {
                g.fill(cx - 4, cy - r + 2, cx + 4, cy + 4, 0xFFD0D4DC);
                g.fill(cx - 4, cy - 4, cx + r - 2, cy + 4, 0xFFD0D4DC);
            }
            case "path_split" -> {
                g.fill(cx - r + 2, cy - 3, cx + r - 2, cy + 5, 0xFFD0D4DC);
                g.fill(cx - 4, cy - r + 2, cx + 4, cy + 4, 0xFFD0D4DC);
            }
            case "path_cross" -> {
                g.fill(cx - r + 2, cy - 4, cx + r - 2, cy + 4, 0xFFD0D4DC);
                g.fill(cx - 4, cy - r + 2, cx + 4, cy + r - 2, 0xFFD0D4DC);
            }
            case "path_crosswalk_sm", "path_crosswalk_lg" -> {
                int w = baseKey.endsWith("lg") ? 8 : 4;
                for (int dy = -8; dy <= 8; dy += 5) {
                    g.fill(cx - w, cy + dy, cx + w, cy + dy + 3, c);
                }
            }
            case "zone_full" -> {
                for (int d = -10; d <= 10; d += 4) {
                    g.fill(cx + d - 4, cy - 8, cx + d - 2, cy + 8, c);
                }
                g.renderOutline(cx - 9, cy - 9, 18, 18, c);
            }
            case "zone_half" -> {
                g.fill(cx - 9, cy - 9, cx, cy + 9, c);
                g.renderOutline(cx - 9, cy - 9, 18, 18, c);
            }
            case "zone_quarter" -> {
                g.fill(cx - 9, cy - 9, cx, cy, c);
                g.renderOutline(cx - 9, cy - 9, 18, 18, c);
            }
            default -> g.fill(cx - r + 4, cy - 1, cx + r - 4, cy + 1, c);
        }

        if (prohibited) {
            g.renderOutline(cx - 9, cy - 9, 18, 18, c);
            for (int i = -7; i <= 7; i++) {
                g.fill(cx + i, cy + i, cx + i + 2, cy + i + 2, c);
            }
        }
    }

    /**
     * Renders the isometric foundation preview with an orange pattern overlay in the Customizer Inspector.
     */
    private void drawPatternFoundationPreview(GuiGraphics g, CustomizerEntry target, int cx, int cy) {
        g.fill(cx - 34, cy - 12, cx + 34, cy + 14, 0xFF60656E);
        g.fill(cx - 34, cy + 14, cx + 34, cy + 20, 0xFF282B30);
        g.renderOutline(cx - 34, cy - 12, 68, 32, 0xFF181A1D);

        g.fill(cx - 28, cy - 8, cx + 28, cy + 10, 0xFF454950);
        g.renderOutline(cx - 28, cy - 8, 56, 18, COL_FICSIT_ORANGE);
        drawPatternSymbol(g, target.materialOrPatternKey(), cx, cy + 1, 9);
    }

    private void drawHammerIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 1, x + 9, y + 4, color);
        g.fill(x + 5, y + 4, x + 7, y + 11, color);
    }

    private void drawPaintbrushIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 6, y + 1, x + 10, y + 5, color);
        g.fill(x + 4, y + 5, x + 7, y + 8, color);
        g.fill(x + 2, y + 8, x + 4, y + 11, color);
    }

    private void drawBlueprintIcon(GuiGraphics g, int x, int y, int color) {
        g.renderOutline(x + 1, y + 1, 10, 10, color);
        g.fill(x + 4, y + 1, x + 5, y + 11, color);
    }

    private void drawMagnifyingGlassIcon(GuiGraphics g, int x, int y, int color) {
        g.renderOutline(x, y, 7, 7, color);
        g.fill(x + 6, y + 6, x + 9, y + 9, color);
    }

    private void drawCategoryIcon(GuiGraphics g, String category, int cx, int cy, int color) {
        String lower = category.toLowerCase();
        if (lower.equals("colors")) {
            drawFilledCircle(g, cx, cy, 7, color);
            g.fill(cx - 4, cy - 3, cx - 2, cy - 1, COL_SIDEBAR_BG);
            g.fill(cx, cy - 4, cx + 2, cy - 2, COL_SIDEBAR_BG);
            g.fill(cx + 2, cy, cx + 4, cy + 2, COL_SIDEBAR_BG);
        } else if (lower.equals("materials")) {
            g.fill(cx - 7, cy - 6, cx + 2, cy - 3, color);
            g.fill(cx + 3, cy - 6, cx + 7, cy - 3, color);
            g.fill(cx - 7, cy - 2, cx - 2, cy + 1, color);
            g.fill(cx - 1, cy - 2, cx + 7, cy + 1, color);
            g.fill(cx - 7, cy + 2, cx + 3, cy + 5, color);
            g.fill(cx + 4, cy + 2, cx + 7, cy + 5, color);
        } else if (lower.equals("patterns")) {
            g.fill(cx - 2, cy - 7, cx + 2, cy - 3, color);
            g.fill(cx - 7, cy - 2, cx - 3, cy + 2, color);
            g.fill(cx + 3, cy - 2, cx + 7, cy + 2, color);
            g.fill(cx - 2, cy + 3, cx + 2, cy + 7, color);
        } else if (lower.contains("special") || lower.contains("progression")) {
            g.fill(cx - 5, cy, cx - 2, cy + 3, color);
            g.fill(cx - 2, cy + 2, cx + 1, cy - 1, color);
            g.fill(cx + 1, cy - 1, cx + 5, cy - 5, color);
        } else if (lower.contains("prod") || lower.contains("manufact")) {
            g.fill(cx - 6, cy - 6, cx - 4, cy + 4, color);
            g.fill(cx - 6, cy - 1, cx + 6, cy + 4, color);
            g.fill(cx - 3, cy - 3, cx, cy - 1, color);
            g.fill(cx + 2, cy - 3, cx + 5, cy - 1, color);
        } else if (lower.contains("power") || lower.contains("energy")) {
            g.fill(cx - 1, cy - 6, cx + 3, cy - 1, color);
            g.fill(cx - 3, cy - 2, cx + 3, cy + 1, color);
            g.fill(cx - 2, cy, cx + 1, cy + 5, color);
        } else if (lower.contains("logist") || lower.contains("convey") || lower.contains("transp")) {
            g.renderOutline(cx - 6, cy - 5, 12, 10, color);
            g.fill(cx - 3, cy - 1, cx + 2, cy + 1, color);
            g.fill(cx + 1, cy - 3, cx + 4, cy + 3, color);
        } else if (lower.contains("organ") || lower.contains("stor")) {
            g.fill(cx - 2, cy - 6, cx + 2, cy - 3, color);
            g.fill(cx - 5, cy - 1, cx + 5, cy, color);
            g.fill(cx - 6, cy + 1, cx - 3, cy + 4, color);
            g.fill(cx - 1, cy + 1, cx + 2, cy + 4, color);
            g.fill(cx + 3, cy + 1, cx + 6, cy + 4, color);
        } else if (lower.contains("found")) {
            g.fill(cx - 4, cy - 4, cx + 4, cy - 2, color);
            g.fill(cx - 6, cy - 2, cx + 6, cy + 2, color);
            g.fill(cx - 4, cy + 2, cx + 4, cy + 4, color);
        } else {
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

    private void renderBottomKeybindBar(GuiGraphics g) {
        String[][] callouts = {
                {"Select", "LMB"},
                {"Bind to Hotbar", "Hover + 0-9"},
                {"Cycle Tab", "Tab"},
                {"Search", "Space Bar"},
                {"Close", "X / Escape"}
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
            g.fill(curX, barY, curX + bw, barY + 21, 0xCC181A1D);
            g.drawCenteredString(this.font, callouts[i][0], curX + bw / 2, barY + 3, TEXT_WHITE);

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
    // INPUT & CLICK HANDLING
    // ==========================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
            // 1. Top Boxed Header Tabs & Close Button
            if (mouseY >= winY && mouseY < winY + HEADER_H) {
                if (mouseX >= winX + 2 && mouseX < winX + 94) { setTab(Tab.BUILDER); return true; }
                if (mouseX >= winX + 96 && mouseX < winX + 194) { setTab(Tab.CUSTOMIZER); return true; }
                if (mouseX >= winX + 196 && mouseX < winX + 294) { setTab(Tab.BLUEPRINTS); return true; }
                if (mouseX >= winX + winW - 26 && mouseX < winX + winW) { this.onClose(); return true; }
            }

            // 2. Left Sidebar Categories
            int bodyY = winY + HEADER_H;
            if (mouseX >= winX && mouseX < winX + SIDEBAR_W && mouseY >= bodyY && mouseY < winY + winH) {
                int idx = (int) ((mouseY - bodyY) / CAT_BTN_H);
                List<String> cats = getActiveCategories();
                if (idx >= 0 && idx < cats.size()) {
                    if (currentTab == Tab.CUSTOMIZER) {
                        this.customizerCategory = cats.get(idx);
                        this.scrollOffset = 0;
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

            // 3. Blueprints Edit / Delete Buttons
            if (currentTab == Tab.BLUEPRINTS && selectedStructureInTab != null) {
                int padX = inspectorX + 10;
                int innerW = inspectorW - 20;
                int halfW = (innerW - 4) / 2;
                int btnY = winY + winH - 22;
                if (mouseY >= btnY && mouseY < btnY + 16) {
                    if (mouseX >= padX && mouseX < padX + halfW) { openEditor(); return true; }
                    if (mouseX >= padX + halfW + 4 && mouseX < padX + innerW) { deleteStructure(); return true; }
                }
            }

            // 4. Customizer Item Selection
            if (currentTab == Tab.CUSTOMIZER && hoveredCustomizerEntry != null) {
                selectedCustomizerEntry = hoveredCustomizerEntry;
                applyCustomizerSelection(hoveredCustomizerEntry);
                return true;
            }

            // 5. Builder / Blueprints Structure Selection
            if (hoveredStructure != null) {
                if (currentTab == Tab.BUILDER) {
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

    private void applyCustomizerSelection(CustomizerEntry entry) {
        Player player = Minecraft.getInstance().player;
        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;

        if (player != null) {
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.25f);
        }

        switch (entry.type()) {
            case SWATCH, FINISH -> {
                if (entry.colorIndex() >= 0) {
                    // 1. Update client ItemStack immediately so StructurePreview and Creative Mode keep the new color
                    if (held.getItem() instanceof BuildTool) {
                        BuildTool.setColor(held, entry.colorIndex());
                    }
                    // 2. Sync to Server
                    ModMessages.sendToServer(new SetColorC2SPacket(entry.colorIndex()));
                }
            }
            case MATERIAL -> {
                if (held.getItem() instanceof BuildTool) {
                    BuildTool.setMaterial(held, entry.materialOrPatternKey());
                }
                ModMessages.sendToServer(new SetMaterialC2SPacket(entry.materialOrPatternKey()));
            }
            case PATTERN -> {
                if (held.getItem() instanceof BuildTool) {
                    BuildTool.setPattern(held, entry.materialOrPatternKey());
                }
                ModMessages.sendToServer(new SetMaterialC2SPacket("PATTERN:" + entry.materialOrPatternKey()));
            }
        }

        PreviewRenderer.clearCaches();
        this.onClose();
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
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 24)));
        return true;
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
            Minecraft.getInstance().setScreen(new FicsitConfirmScreen(
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

    // ==========================================
    // STATIC SATISFACTORY PATTERN CATALOG
    // ==========================================

    private static List<CustomizerEntry> buildPatternEntries() {
        List<CustomizerEntry> list = new ArrayList<>();
        String patDesc = "Applied to Foundations to improve factory aesthetics and organization.\nThe Secondary Color in a Color Swatch is used for Patterns.";

        // 1. Arrows
        String[][] arrows = {
                {"remove", "Pattern Removal"},
                {"arrow_up", "Straight Arrow"},
                {"arrow_left", "Left Arrow"},
                {"arrow_right", "Right Arrow"},
                {"arrow_uturn", "U-Turn Arrow"},
                {"no_arrow_up", "NO! Straight Arrow"},
                {"no_arrow_left", "NO! Left Arrow"},
                {"no_arrow_right", "NO! Right Arrow"}
        };
        for (String[] a : arrows) {
            list.add(new CustomizerEntry("pat_arr_" + a[0], a[1], "1. Arrows", CustomizerType.PATTERN, 0, 0, -1, a[0], "minecraft:stone", patDesc));
        }

        // 2. Icons
        String[][] icons = {
                {"remove", "Pattern Removal"},
                {"icon_factory", "Factory Icon"},
                {"icon_power", "Power Icon"},
                {"icon_storage", "Storage Icon"},
                {"icon_nuclear", "Nuclear Warning Icon"},
                {"icon_liquid", "Liquid Icon"},
                {"icon_cross", "Cross Icon"},
                {"icon_pioneer", "Pioneer Icon"},
                {"no_icon_pioneer", "NO! Pioneer Icon"},
                {"icon_tractor", "Tractor Icon"},
                {"icon_truck", "Truck Icon"},
                {"icon_explorer", "Explorer Icon"},
                {"icon_cart", "Factory Cart™ Icon"},
                {"no_icon_cart", "NO! Factory Cart™ Icon"},
                {"icon_parking", "Parking Icon"},
                {"no_icon_parking", "NO! Parking Icon"}
        };
        for (String[] ic : icons) {
            list.add(new CustomizerEntry("pat_ic_" + ic[0], ic[1], "2. Icons", CustomizerType.PATTERN, 0, 0, -1, ic[0], "minecraft:stone", patDesc));
        }

        // 3. Lines
        String[][] lines = {
                {"remove", "Pattern Removal"},
                {"line_center", "Center Line"},
                {"line_corner", "Center Corner Line"},
                {"line_split", "Split Line"},
                {"line_cross", "Cross Line"},
                {"line_side", "Side Line"},
                {"line_double", "Double Line"},
                {"line_dotted", "Center Dotted Line"}
        };
        for (String[] ln : lines) {
            list.add(new CustomizerEntry("pat_ln_" + ln[0], ln[1], "3. Lines", CustomizerType.PATTERN, 0, 0, -1, ln[0], "minecraft:stone", patDesc));
        }

        // 4. Numbers
        list.add(new CustomizerEntry("pat_num_rem", "Pattern Removal", "4. Numbers", CustomizerType.PATTERN, 0, 0, -1, "remove", "minecraft:stone", patDesc));
        for (int n = 0; n <= 9; n++) {
            list.add(new CustomizerEntry("pat_num_" + n, "Number " + n, "4. Numbers", CustomizerType.PATTERN, 0, 0, -1, "num_" + n, "minecraft:stone", patDesc));
        }

        // 5. Paths
        String[][] paths = {
                {"remove", "Pattern Removal"},
                {"path_straight", "Straight Path"},
                {"path_corner", "Corner Path"},
                {"path_split", "Split Path"},
                {"path_cross", "Cross Path"},
                {"path_crosswalk_sm", "Small Crosswalk"},
                {"path_crosswalk_lg", "Large Crosswalk"}
        };
        for (String[] p : paths) {
            list.add(new CustomizerEntry("pat_path_" + p[0], p[1], "5. Paths", CustomizerType.PATTERN, 0, 0, -1, p[0], "minecraft:stone", patDesc));
        }

        // 6. Zones
        String[][] zones = {
                {"remove", "Pattern Removal"},
                {"zone_full", "Full Factory Zone"},
                {"zone_half", "Half Factory Zone"},
                {"zone_quarter", "Quarter Factory Zone"},
                {"line_side", "Factory Zone Line"}
        };
        for (String[] z : zones) {
            list.add(new CustomizerEntry("pat_zone_" + z[0], z[1], "6. Zones", CustomizerType.PATTERN, 0, 0, -1, z[0], "minecraft:stone", patDesc));
        }

        return list;
    }
}