package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructurePreview;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class BuildToolOverlay {
    public static final BuildToolOverlay INSTANCE = new BuildToolOverlay();

    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int COL_DARK_BOX      = 0xCC181A1D;
    private static final int COL_WARNING_PILL  = 0xCCE59344;
    private static final int COL_ERROR_PILL    = 0xCCD94040;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_LIGHT_GRAY   = 0xFFD0D4DC;
    private static final int TEXT_DARK         = 0xFF181A1D;

    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BuildTool)) {
            return;
        }

        renderCenterCrosshairHud(guiGraphics, mc.font, player, heldItem);
        renderBottomKeybindBar(guiGraphics, mc.font, player);
    }

    /**
     * Renders the Center-Screen Hologram Status right below the crosshair:
     * Hammer badge -> "Currently Building:" -> Structure Name -> Cost Cards -> Warning/Status Pill.
     */
    private void renderCenterCrosshairHud(GuiGraphics g, Font font, Player player, ItemStack stack) {
        BuildTool.BuildMode mode = BuildTool.getMode();
        if (mode == BuildTool.BuildMode.NONE) return;

        int centerX = g.guiWidth() / 2;
        int curY = (int) (g.guiHeight() * 0.55f);

        // 1. Small Hammer Badge Icon
        int badgeColor = switch (mode) {
            case CONSTRUCTION -> TEXT_WHITE;
            case DESTRUCTION -> 0xFFFF5555;
            case CLEAR -> 0xFF4FC3F7;
            default -> TEXT_WHITE;
        };
        g.fill(centerX - 6, curY, centerX + 6, curY + 12, 0x99181A1D);
        g.fill(centerX - 4, curY + 2, centerX + 3, curY + 5, badgeColor);
        g.fill(centerX - 1, curY + 5, centerX + 1, curY + 10, badgeColor);
        curY += 15;

        if (mode == BuildTool.BuildMode.CONSTRUCTION) {
            StructureLoader.StructureData structure = BuildTool.getSelectedStructure();
            if (structure == null) return;

            // 2. "Currently Building:" Label
            g.pose().pushPose();
            g.pose().translate(centerX, curY, 0);
            g.pose().scale(0.8f, 0.8f, 1.0f);
            String subLabel = "Currently Building:";
            g.drawString(font, subLabel, -font.width(subLabel) / 2, 0, TEXT_LIGHT_GRAY, true);
            g.pose().popPose();
            curY += 9;

            // 3. Large Structure Title ("The HUB")
            g.pose().pushPose();
            g.pose().translate(centerX, curY, 0);
            g.pose().scale(1.2f, 1.2f, 1.0f);
            String title = structure.getName();
            g.drawString(font, title, -font.width(title) / 2, 0, TEXT_WHITE, true);
            g.pose().popPose();
            curY += 15;

            // 4. Compact Cost Cards + Missing Item Check
            List<StructureLoader.RequiredItem> costs = structure.isOnlyItems()
                    ? structure.getRequiredItems()
                    : structure.getAggregatedCosts();

            boolean missingAnyItem = false;
            int zoopMultiplier = Math.max(1, StructurePreview.getZoopCount());

            if (!costs.isEmpty() && !player.isCreative()) {
                int cardSize = 24;
                int spacing = 4;
                int visibleCount = Math.min(costs.size(), 6);
                int totalW = visibleCount * cardSize + (visibleCount - 1) * spacing;
                int startX = centerX - totalW / 2;

                for (int i = 0; i < visibleCount; i++) {
                    StructureLoader.RequiredItem req = costs.get(i);
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
                    if (item == null || item == Items.AIR) continue;

                    int needed = req.getCount() * zoopMultiplier;
                    int have = countItem(player, item);
                    if (have < needed) missingAnyItem = true;

                    int cx = startX + i * (cardSize + spacing);
                    g.fill(cx, curY, cx + cardSize, curY + cardSize - 6, 0xCC383C42);
                    g.fill(cx, curY + cardSize - 6, cx + cardSize, curY + cardSize, 0xEE181A1D);
                    g.renderOutline(cx, curY, cardSize, cardSize, have >= needed ? 0xFF52565E : 0xFFD94040);

                    g.renderItem(new ItemStack(item), cx + 4, curY + 1);

                    String countTxt = have + "/" + needed;
                    g.pose().pushPose();
                    g.pose().translate(cx + cardSize / 2f, curY + cardSize - 5, 200);
                    g.pose().scale(0.55f, 0.55f, 1.0f);
                    g.drawString(font, countTxt, -font.width(countTxt) / 2, 0,
                            have >= needed ? TEXT_WHITE : 0xFFFF5555, false);
                    g.pose().popPose();
                }
                curY += cardSize + 5;
            }

            // 5. Status / Warning Pill Box ("Missing Resources!", "Hologram Locked", etc.)
            String statusText = null;
            int pillBg = COL_WARNING_PILL;

            if (StructurePreview.isCurrentPlacementObstructed()) {
                statusText = "Encroaching other's Clearance!";
                pillBg = COL_ERROR_PILL;
            } else if (missingAnyItem && !player.isCreative()) {
                statusText = "Cannot afford!";
                pillBg = COL_ERROR_PILL;
            } else if (StructurePreview.isFixed()) {
                int z = StructurePreview.getZoopCount();
                statusText = z > 1 ? ("Hologram Locked (Zoop: " + z + "x)") : "Hologram Locked — Nudge with Arrows";
            } else {
                String mat = BuildTool.getMaterial(stack);
                if (mat != null && !mat.isEmpty()) {
                    statusText = "Material: " + mat;
                }
            }

            if (statusText != null) {
                int pw = (int) (font.width(statusText) * 0.8f) + 12;
                g.fill(centerX - pw / 2, curY, centerX + pw / 2, curY + 12, pillBg);
                g.pose().pushPose();
                g.pose().translate(centerX, curY + 3, 0);
                g.pose().scale(0.8f, 0.8f, 1.0f);
                g.drawString(font, statusText, -font.width(statusText) / 2, 0, TEXT_WHITE, false);
                g.pose().popPose();
            }
        } else if (mode == BuildTool.BuildMode.DESTRUCTION) {
            g.drawCenteredString(font, "FICSIT DISMANTLE MODE", centerX, curY, 0xFFFF5555);
            curY += 12;
            String sub = "Aim at any placed structure and Right-Click to dismantle";
            int pw = (int) (font.width(sub) * 0.75f) + 12;
            g.fill(centerX - pw / 2, curY, centerX + pw / 2, curY + 12, COL_ERROR_PILL);
            g.pose().pushPose();
            g.pose().translate(centerX, curY + 3, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(font, sub, -font.width(sub) / 2, 0, TEXT_WHITE, false);
            g.pose().popPose();
        } else if (mode == BuildTool.BuildMode.CLEAR) {
            g.drawCenteredString(font, "AREA CLEAR / FLATTEN MODE", centerX, curY, 0xFF4FC3F7);
        }
    }

    /**
     * Renders Satisfactory's horizontal keybind callout bar along the bottom of the screen.
     */
    private void renderBottomKeybindBar(GuiGraphics g, Font font, Player player) {
        BuildTool.BuildMode mode = BuildTool.getMode();
        List<String[]> callouts = new ArrayList<>();
        var options = Minecraft.getInstance().options;

        if (mode == BuildTool.BuildMode.CONSTRUCTION) {
            callouts.add(new String[]{"Build", "Shift + " + options.keyUse.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Rotate Hologram", KeyBindings.ROTATE_KEY.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Lock Hologram", KeyBindings.FIX_KEY.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Zoop Mode", KeyBindings.ZOOP_KEY.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Quick Switch", KeyBindings.CYCLE_STRUCTURE_KEY.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Build Menu", options.keyUse.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Switch Mode", KeyBindings.DESTRUCTION_MODE_KEY.getTranslatedKeyMessage().getString()});
        } else if (mode == BuildTool.BuildMode.DESTRUCTION) {
            callouts.add(new String[]{"Dismantle Structure", options.keyUse.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Hide Bounding Box", "Ctrl + " + options.keyAttack.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Switch Mode", KeyBindings.DESTRUCTION_MODE_KEY.getTranslatedKeyMessage().getString()});
        } else {
            callouts.add(new String[]{"Select Area", "Shift + " + options.keyUse.getTranslatedKeyMessage().getString()});
            callouts.add(new String[]{"Switch Mode", KeyBindings.DESTRUCTION_MODE_KEY.getTranslatedKeyMessage().getString()});
        }

        int spacing = 5;
        int totalW = 0;
        int[] widths = new int[callouts.size()];

        for (int i = 0; i < callouts.size(); i++) {
            int w1 = (int) (font.width(callouts.get(i)[0]) * 0.75f) + 10;
            int w2 = (int) (font.width(callouts.get(i)[1]) * 0.65f) + 12;
            widths[i] = Math.max(w1, w2);
            totalW += widths[i] + (i < callouts.size() - 1 ? spacing : 0);
        }

        int curX = (g.guiWidth() - totalW) / 2;
        int barY = g.guiHeight() - 46; // Positioned cleanly above the vanilla hotbar

        for (int i = 0; i < callouts.size(); i++) {
            int bw = widths[i];
            String action = callouts.get(i)[0];
            String key = callouts.get(i)[1];

            // Dark translucent top callout box
            g.fill(curX, barY, curX + bw, barY + 19, COL_DARK_BOX);

            g.pose().pushPose();
            g.pose().translate(curX + bw / 2f, barY + 2.5f, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawString(font, action, -font.width(action) / 2, 0, TEXT_WHITE, false);
            g.pose().popPose();

            // FICSIT Orange bottom keybind pill
            int pillW = (int) (font.width(key) * 0.65f) + 8;
            int pillX = curX + (bw - pillW) / 2;
            g.fill(pillX, barY + 11, pillX + pillW, barY + 18, COL_FICSIT_ORANGE);

            g.pose().pushPose();
            g.pose().translate(curX + bw / 2f, barY + 12.5f, 0);
            g.pose().scale(0.65f, 0.65f, 1.0f);
            g.drawString(font, key, -font.width(key) / 2, 0, TEXT_DARK, false);
            g.pose().popPose();

            curX += bw + spacing;
        }
    }

    private int countItem(Player player, Item target) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == target) count += s.getCount();
        }
        return count;
    }
}