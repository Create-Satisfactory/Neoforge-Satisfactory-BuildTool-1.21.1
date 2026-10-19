package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

public class BlueprintEditorScreen extends Screen {

    private static final ResourceLocation DEFAULT_ICON =
            ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "textures/gui/icons/factory_default.png");

    private static final int COL_HEADER_BG     = 0xF225272B;
    private static final int COL_BODY_BG       = 0xEB181A1D;
    private static final int COL_SIDE_BG       = 0xE623262A;
    private static final int COL_FIELD_BG      = 0xFF2B2E34;
    private static final int COL_FIELD_FOCUSED = 0xFF363A42;
    private static final int COL_BTN_GRAY      = 0xFF3E4147;
    private static final int COL_BTN_GRAY_HOV  = 0xFF52565E;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int COL_ORANGE_HOV    = 0xFFFFAA5A;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFB0B5BC;
    private static final int TEXT_DARK         = 0xFF181A1D;

    private static final int HEADER_H = 22;
    private static final int LEFT_PANE_W = 116;

    private final StructureLoader.StructureData structure;
    private final Runnable onSaveCallback;
    private final String originalFileName;

    private int winX, winY, winW, winH;
    private int formX, formW;
    private int saveBtnX, cancelBtnX, btnY, btnW, btnH;

    private EditBox nameField;
    private EditBox authorField;
    private EditBox descriptionField;
    private EditBox guiPathField;
    private EditBox iconPathField;

    public BlueprintEditorScreen(StructureLoader.StructureData structure, Runnable onSaveCallback) {
        super(Component.translatable("gui.satisfactory_build_tool.editor.title"));
        this.structure = structure;
        this.onSaveCallback = onSaveCallback;
        // Use the actual tracked filename on disk if available, falling back to the sanitized name
        String tracked = structure.getFileName();
        if (tracked != null && !tracked.isEmpty()) {
            this.originalFileName = tracked.toLowerCase().replace(".json", "").replace(" ", "_");
        } else {
            this.originalFileName = structure.getName().toLowerCase().replace(" ", "_");
        }
    }

    @Override
    protected void init() {
        super.init();

        this.winW = Math.min(this.width - 32, 440);
        this.winH = 242;
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(12, (this.height - this.winH - 28) / 2);

        this.formX = this.winX + LEFT_PANE_W + 14;
        this.formW = this.winW - LEFT_PANE_W - 28;

        int startY = this.winY + HEADER_H + 20;
        int rowStep = 34;

        // 1. Blueprint Name
        nameField = createFicsitEditBox(formX + 6, startY, formW - 12, 64, structure.getName(), "Blueprint Name...");
        this.addRenderableWidget(nameField);

        // 2. Author
        authorField = createFicsitEditBox(formX + 6, startY + rowStep, formW - 12, 64, structure.getAuthor(), "Author...");
        this.addRenderableWidget(authorField);

        // 3. Description
        descriptionField = createFicsitEditBox(formX + 6, startY + rowStep * 2, formW - 12, 256, structure.getDescription(), "Description...");
        this.addRenderableWidget(descriptionField);

        // 4. Category / GUI Path
        String defaultPath = structure.getGuiPath() != null ? structure.getGuiPath() : "";
        guiPathField = createFicsitEditBox(formX + 6, startY + rowStep * 3, formW - 12, 128, defaultPath, "Category > Subcategory");
        this.addRenderableWidget(guiPathField);

        // 5. Icon Path (supports Item IDs like "create:mechanical_press" or ".png" texture paths)
        String defaultIcon = (structure.getIconPath() != null && !structure.getIconPath().isEmpty())
                ? structure.getIconPath()
                : "minecraft:paper";
        iconPathField = createFicsitEditBox(formX + 6, startY + rowStep * 4, formW - 12, 128, defaultIcon, "Item ID or texture.png");
        this.addRenderableWidget(iconPathField);

        // Bottom Save & Cancel Button Bounds
        this.btnW = 104;
        this.btnH = 20;
        this.btnY = this.winY + this.winH - 28;
        this.cancelBtnX = this.winX + this.winW - 14 - this.btnW;
        this.saveBtnX = this.cancelBtnX - 8 - this.btnW;
    }

    private EditBox createFicsitEditBox(int x, int y, int width, int maxLen, String initialValue, String hint) {
        EditBox box = new EditBox(this.font, x, y, width, 12, Component.literal(hint));
        box.setBordered(false);
        box.setMaxLength(maxLen);
        box.setTextColor(TEXT_WHITE);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(initialValue != null ? initialValue : "");
        return box;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x70090A0C);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // 1. Top Header Bar
        g.fill(winX, winY, winX + winW, winY + HEADER_H, COL_HEADER_BG);
        g.fill(winX, winY + HEADER_H - 2, winX + winW, winY + HEADER_H, COL_FICSIT_ORANGE);
        g.drawString(this.font, "✎", winX + 8, winY + 6, COL_FICSIT_ORANGE, false);
        g.drawString(this.font, this.title, winX + 22, winY + 6, TEXT_WHITE, false);

        // Top-Right 'X' Close Button
        int closeX = winX + winW - 24;
        boolean closeHover = mouseX >= closeX && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H;
        if (closeHover) {
            g.fill(closeX, winY, winX + winW, winY + HEADER_H - 2, 0xFF40444B);
        }
        g.fill(closeX - 1, winY + 3, closeX, winY + HEADER_H - 4, 0xFF3A3D42);
        g.drawCenteredString(this.font, "✕", closeX + 12, winY + 6, TEXT_WHITE);

        int bodyY = winY + HEADER_H;
        int bodyH = winH - HEADER_H;

        // 2. Left Preview Sidebar
        g.fill(winX, bodyY, winX + LEFT_PANE_W, bodyY + bodyH, COL_SIDE_BG);
        g.fill(winX + LEFT_PANE_W - 1, bodyY, winX + LEFT_PANE_W, bodyY + bodyH, 0xFF141518);

        int iconBoxSize = 64;
        int iconBoxX = winX + (LEFT_PANE_W - iconBoxSize) / 2;
        int iconBoxY = bodyY + 18;
        g.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxSize, iconBoxY + iconBoxSize, 0xFF141619);
        g.renderOutline(iconBoxX, iconBoxY, iconBoxSize, iconBoxSize, COL_FICSIT_ORANGE);

        // Live Icon Preview from iconPathField
        String liveIconPath = iconPathField != null ? iconPathField.getValue().trim() : structure.getIconPath();
        renderPreviewIcon(g, liveIconPath, iconBoxX + 8, iconBoxY + 8, 48);

        // Blueprint Dimensions & Block Count Info
        String dims = structure.getWidth() + "×" + structure.getLength() + "×" + structure.getHeight() + "m";
        g.drawCenteredString(this.font, "Footprint", winX + LEFT_PANE_W / 2, iconBoxY + iconBoxSize + 12, TEXT_GRAY);
        g.drawCenteredString(this.font, dims, winX + LEFT_PANE_W / 2, iconBoxY + iconBoxSize + 23, COL_FICSIT_ORANGE);

        // 3. Right Form Area & Input Field Backgrounds
        g.fill(winX + LEFT_PANE_W, bodyY, winX + winW, bodyY + bodyH, COL_BODY_BG);

        String[] labels = {
                Component.translatable("gui.satisfactory_build_tool.editor.name").getString(),
                Component.translatable("gui.satisfactory_build_tool.editor.author").getString(),
                Component.translatable("gui.satisfactory_build_tool.editor.description").getString(),
                Component.translatable("gui.satisfactory_build_tool.editor.gui_path").getString(),
                Component.translatable("gui.satisfactory_build_tool.editor.icon_path").getString()
        };
        EditBox[] boxes = {nameField, authorField, descriptionField, guiPathField, iconPathField};

        int startY = this.winY + HEADER_H + 20;
        int rowStep = 34;

        for (int i = 0; i < boxes.length; i++) {
            int boxY = startY + i * rowStep - 4;
            EditBox box = boxes[i];
            boolean focused = box != null && box.isFocused();

            // Label above input box
            g.pose().pushPose();
            g.pose().translate(formX, boxY - 9, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(this.font, labels[i].toUpperCase(), 0, 0, focused ? COL_FICSIT_ORANGE : TEXT_GRAY, false);
            g.pose().popPose();

            // Dark Input Well + Left Orange Accent Stripe when focused
            g.fill(formX, boxY, formX + formW, boxY + 18, focused ? COL_FIELD_FOCUSED : COL_FIELD_BG);
            g.renderOutline(formX, boxY, formW, 18, focused ? COL_FICSIT_ORANGE : 0xFF1E2024);
            if (focused) {
                g.fill(formX, boxY, formX + 3, boxY + 18, COL_FICSIT_ORANGE);
            }
        }

        // 4. Save & Cancel FICSIT Buttons
        boolean saveHover = mouseX >= saveBtnX && mouseX < saveBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(saveBtnX, btnY, saveBtnX + btnW, btnY + btnH, saveHover ? COL_ORANGE_HOV : COL_FICSIT_ORANGE);
        g.renderOutline(saveBtnX, btnY, btnW, btnH, 0xFF141518);
        g.drawCenteredString(this.font, Component.translatable("gui.satisfactory_build_tool.info.save"),
                saveBtnX + btnW / 2, btnY + 6, TEXT_DARK);

        boolean cancelHover = mouseX >= cancelBtnX && mouseX < cancelBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(cancelBtnX, btnY, cancelBtnX + btnW, btnY + btnH, cancelHover ? COL_BTN_GRAY_HOV : COL_BTN_GRAY);
        g.renderOutline(cancelBtnX, btnY, btnW, btnH, 0xFF141518);
        g.drawCenteredString(this.font, Component.translatable("gui.satisfactory_build_tool.close_button"),
                cancelBtnX + btnW / 2, btnY + 6, TEXT_WHITE);

        g.renderOutline(winX, winY, winW, winH, 0xFF141518);

        // Render EditBoxes on top of the custom backgrounds
        super.render(g, mouseX, mouseY, partialTick);
        renderBottomKeybindBar(g);
    }

    private void renderPreviewIcon(GuiGraphics g, String iconPath, int x, int y, int size) {
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
                {"Save Changes", "Enter"},
                {"Next Field", "Tab"},
                {"Cancel", "Escape"}
        };

        int spacing = 6;
        int totalW = 0;
        int[] widths = new int[callouts.length];
        for (int i = 0; i < callouts.length; i++) {
            int w1 = this.font.width(callouts[i][0]) + 14;
            int w2 = (int) (this.font.width(callouts[i][1]) * 0.75f) + 14;
            widths[i] = Math.max(w1, w2);
            totalW += widths[i] + (i < callouts.length - 1 ? spacing : 0);
        }

        int curX = (this.width - totalW) / 2;
        int barY = winY + winH + 6;

        for (int i = 0; i < callouts.length; i++) {
            int bw = widths[i];
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= winX + winW - 24 && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H) {
                this.onClose();
                return true;
            }
            if (mouseX >= saveBtnX && mouseX < saveBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                saveChanges();
                return true;
            }
            if (mouseX >= cancelBtnX && mouseX < cancelBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveChanges();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveChanges() {
        try {
            String newName = nameField.getValue().trim();
            if (newName.isEmpty()) return;

            String newNameFormatted = newName.toLowerCase().replace(" ", "_");
            StructureFileManager fileManager = new StructureFileManager();
            Path clientDir = fileManager.getClientStructuresDir();

            boolean nameChanged = !originalFileName.equals(newNameFormatted);
            if (nameChanged) {
                // Also delete any old .nbt file that was referenced in the previous schematics list
                for (String entry : structure.getSchematics()) {
                    String oldNbt = entry.contains(":") ? entry.substring(entry.indexOf(':') + 1).trim() : entry.trim();
                    if (!oldNbt.endsWith(".nbt")) oldNbt += ".nbt";
                    if (!oldNbt.equalsIgnoreCase(newNameFormatted + ".nbt")) {
                        Files.deleteIfExists(clientDir.resolve(oldNbt));
                    }
                }
                fileManager.renameStructureFile(originalFileName, newNameFormatted, clientDir);
            }

            StructureLoader.StructureData updatedStructure = createUpdatedStructure();
            if (updatedStructure != null) {
                boolean saved = fileManager.saveStructureToFile(updatedStructure, clientDir, newNameFormatted);
                if (saved) {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
                    }
                    StructureLoader.loadClientStructures();
                    com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool.loadStructures();
                    if (onSaveCallback != null) {
                        onSaveCallback.run();
                    }
                    this.onClose();
                }
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save structure changes", e);
        }
    }

    private StructureLoader.StructureData createUpdatedStructure() {
        try {
            StructureLoader.StructureData newInstance = StructureLoader.StructureData.class.getDeclaredConstructor().newInstance();
            Field[] fields = StructureLoader.StructureData.class.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                switch (field.getName()) {
                    case "name" -> field.set(newInstance, nameField.getValue().trim());
                    case "author" -> field.set(newInstance, authorField.getValue().trim());
                    case "description" -> field.set(newInstance, descriptionField.getValue().trim());
                    case "guiPath" -> field.set(newInstance, guiPathField.getValue().trim().isEmpty() ? null : guiPathField.getValue().trim());
                    case "iconPath" -> field.set(newInstance, iconPathField.getValue().trim().isEmpty() ? null : iconPathField.getValue().trim());
                    default -> field.set(newInstance, field.get(structure));
                }
            }
            return newInstance;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to create updated structure", e);
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}