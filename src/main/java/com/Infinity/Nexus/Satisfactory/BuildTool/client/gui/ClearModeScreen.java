package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.AreaClearC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ClearModeScreen extends Screen {

    private static final int COL_HEADER_BG     = 0xF225272B;
    private static final int COL_BODY_BG       = 0xEB181A1D;
    private static final int COL_WELL_BG       = 0xFF121316;
    private static final int COL_CARD_BG       = 0xFF2B2E34;
    private static final int COL_CARD_HOV      = 0xFF3A3E47;
    private static final int COL_BTN_GRAY      = 0xFF3E4147;
    private static final int COL_BTN_GRAY_HOV  = 0xFF52565E;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int COL_FICSIT_CYAN   = 0xFF4FC3F7;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFB0B5BC;
    private static final int TEXT_DARK         = 0xFF181A1D;

    private static final int HEADER_H = 22;

    private int winX, winY, winW, winH;
    private int cardX, cardW, clearCardY, flattenCardY, cardH;
    private int cancelBtnX, cancelBtnY, cancelBtnW, cancelBtnH;

    public ClearModeScreen() {
        super(Component.translatable("gui.satisfactory_build_tool.clear_mode.title"));
    }

    @Override
    protected void init() {
        super.init();
        if (!Config.enable_clear_mode) {
            this.onClose();
            return;
        }

        this.winW = 310;
        this.winH = 192;
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(14, (this.height - this.winH - 28) / 2);

        this.cardX = this.winX + 14;
        this.cardW = this.winW - 28;
        this.cardH = 34;

        this.clearCardY = this.winY + HEADER_H + 46;
        this.flattenCardY = this.clearCardY + this.cardH + 8;

        this.cancelBtnW = 120;
        this.cancelBtnH = 20;
        this.cancelBtnX = this.winX + (this.winW - this.cancelBtnW) / 2;
        this.cancelBtnY = this.winY + this.winH - 28;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x68090A0C);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        Player player = Minecraft.getInstance().player;

        // 1. Top Header Bar
        g.fill(winX, winY, winX + winW, winY + HEADER_H, COL_HEADER_BG);
        g.fill(winX, winY + HEADER_H - 2, winX + winW, winY + HEADER_H, COL_FICSIT_CYAN);
        g.drawString(this.font, "❖", winX + 8, winY + 6, COL_FICSIT_CYAN, false);
        g.drawString(this.font, this.title, winX + 20, winY + 6, TEXT_WHITE, false);

        int closeX = winX + winW - 24;
        boolean closeHover = mouseX >= closeX && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H;
        if (closeHover) {
            g.fill(closeX, winY, winX + winW, winY + HEADER_H - 2, 0xFF40444B);
        }
        g.fill(closeX - 1, winY + 3, closeX, winY + HEADER_H - 4, 0xFF3A3D42);
        g.drawCenteredString(this.font, "✕", closeX + 12, winY + 6, TEXT_WHITE);

        // 2. Main Body & Selected Volume Summary Bar
        int bodyY = winY + HEADER_H;
        int bodyH = winH - HEADER_H;
        g.fill(winX, bodyY, winX + winW, bodyY + bodyH, COL_BODY_BG);

        g.fill(cardX, bodyY + 8, cardX + cardW, bodyY + 38, COL_WELL_BG);
        g.renderOutline(cardX, bodyY + 8, cardW, 30, 0xFF282B30);

        String dimsText = "No Area Selected";
        String volText = "";
        if (player != null) {
            BlockPos p1 = BuildTool.getPoint1(player);
            BlockPos p2 = BuildTool.getPoint2(player);
            if (p1 != null && p2 != null) {
                int w = Math.abs(p2.getX() - p1.getX()) + 1;
                int h = Math.abs(p2.getY() - p1.getY()) + 1;
                int l = Math.abs(p2.getZ() - p1.getZ()) + 1;
                dimsText = "Selected Zone: " + w + "×" + l + "×" + h + "m";
                volText = (w * h * l) + " blocks";
            }
        }
        g.drawString(this.font, dimsText, cardX + 8, bodyY + 14, TEXT_WHITE, false);
        g.drawString(this.font, volText, cardX + cardW - this.font.width(volText) - 8, bodyY + 14, COL_FICSIT_CYAN, false);
        g.pose().pushPose();
        g.pose().translate(cardX + 8, bodyY + 26, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(this.font, "Protected FICSIT structures inside the zone will not be harmed.", 0, 0, TEXT_GRAY, false);
        g.pose().popPose();

        // 3. Option 1: CLEAR ALL Card
        boolean clearHov = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= clearCardY && mouseY < clearCardY + cardH;
        g.fill(cardX, clearCardY, cardX + cardW, clearCardY + cardH, clearHov ? COL_CARD_HOV : COL_CARD_BG);
        g.fill(cardX, clearCardY, cardX + 3, clearCardY + cardH, clearHov ? COL_FICSIT_ORANGE : COL_FICSIT_CYAN);
        g.renderOutline(cardX, clearCardY, cardW, cardH, clearHov ? COL_FICSIT_ORANGE : 0xFF1E2024);

        g.drawString(this.font, Component.translatable("gui.satisfactory_build_tool.clear_mode.clear"),
                cardX + 10, clearCardY + 6, clearHov ? COL_FICSIT_ORANGE : TEXT_WHITE, false);
        g.pose().pushPose();
        g.pose().translate(cardX + 10, clearCardY + 20, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(this.font, "Excavates all terrain and foliage within the selected bounding box.", 0, 0, TEXT_GRAY, false);
        g.pose().popPose();

        // 4. Option 2: FLATTEN Card (with Off-Hand Substitute Block indicator)
        boolean flatHov = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= flattenCardY && mouseY < flattenCardY + cardH;
        g.fill(cardX, flattenCardY, cardX + cardW, flattenCardY + cardH, flatHov ? COL_CARD_HOV : COL_CARD_BG);
        g.fill(cardX, flattenCardY, cardX + 3, flattenCardY + cardH, flatHov ? COL_FICSIT_ORANGE : COL_FICSIT_CYAN);
        g.renderOutline(cardX, flattenCardY, cardW, cardH, flatHov ? COL_FICSIT_ORANGE : 0xFF1E2024);

        g.drawString(this.font, Component.translatable("gui.satisfactory_build_tool.clear_mode.flatten"),
                cardX + 10, flattenCardY + 6, flatHov ? COL_FICSIT_ORANGE : TEXT_WHITE, false);

        ItemStack offhand = player != null ? player.getOffhandItem() : ItemStack.EMPTY;
        boolean hasOffhandBlock = !offhand.isEmpty() && offhand.getItem() instanceof BlockItem;
        String flatDesc = hasOffhandBlock
                ? "Levels terrain and paves base layer with: " + offhand.getHoverName().getString()
                : "Levels terrain above the lowest Y-layer (hold block in Off-Hand to pave).";

        g.pose().pushPose();
        g.pose().translate(cardX + 10, flattenCardY + 20, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(this.font, flatDesc, 0, 0, hasOffhandBlock ? COL_FICSIT_ORANGE : TEXT_GRAY, false);
        g.pose().popPose();

        if (hasOffhandBlock) {
            g.renderItem(offhand, cardX + cardW - 22, flattenCardY + 9);
        }

        // 5. Cancel Button
        boolean cancelHov = mouseX >= cancelBtnX && mouseX < cancelBtnX + cancelBtnW && mouseY >= cancelBtnY && mouseY < cancelBtnY + cancelBtnH;
        g.fill(cancelBtnX, cancelBtnY, cancelBtnX + cancelBtnW, cancelBtnY + cancelBtnH, cancelHov ? COL_BTN_GRAY_HOV : COL_BTN_GRAY);
        g.renderOutline(cancelBtnX, cancelBtnY, cancelBtnW, cancelBtnH, 0xFF141518);
        g.drawCenteredString(this.font, Component.translatable("gui.satisfactory_build_tool.clear_mode.cancel"),
                cancelBtnX + cancelBtnW / 2, cancelBtnY + 6, TEXT_WHITE);

        g.renderOutline(winX, winY, winW, winH, 0xFF141518);

        super.render(g, mouseX, mouseY, partialTick);
        renderBottomKeybindBar(g);
    }

    private void renderBottomKeybindBar(GuiGraphics g) {
        String[][] callouts = {
                {"Execute Operation", "LMB"},
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
            if (mouseX >= cardX && mouseX < cardX + cardW && mouseY >= clearCardY && mouseY < clearCardY + cardH) {
                handleClear(AreaClearC2SPacket.ClearType.ALL);
                return true;
            }
            if (mouseX >= cardX && mouseX < cardX + cardW && mouseY >= flattenCardY && mouseY < flattenCardY + cardH) {
                handleClear(AreaClearC2SPacket.ClearType.FLATTEN);
                return true;
            }
            if (mouseX >= cancelBtnX && mouseX < cancelBtnX + cancelBtnW && mouseY >= cancelBtnY && mouseY < cancelBtnY + cancelBtnH) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleClear(AreaClearC2SPacket.ClearType type) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        BlockPos p1 = BuildTool.getPoint1(player);
        BlockPos p2 = BuildTool.getPoint2(player);

        if (p1 != null && p2 != null) {
            BlockPos minPos = new BlockPos(
                    Math.min(p1.getX(), p2.getX()),
                    Math.min(p1.getY(), p2.getY()),
                    Math.min(p1.getZ(), p2.getZ())
            );
            BlockPos maxPos = new BlockPos(
                    Math.max(p1.getX(), p2.getX()),
                    Math.max(p1.getY(), p2.getY()),
                    Math.max(p1.getZ(), p2.getZ())
            );

            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.1f);
            ModMessages.sendToServer(new AreaClearC2SPacket(minPos, maxPos, type));
            BuildTool.resetSelection(player);
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}