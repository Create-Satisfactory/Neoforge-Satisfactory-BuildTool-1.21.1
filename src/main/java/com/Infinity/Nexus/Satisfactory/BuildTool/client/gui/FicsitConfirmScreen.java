package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FicsitConfirmScreen extends Screen {

    private static final int COL_HEADER_BG     = 0xF225272B;
    private static final int COL_BODY_BG       = 0xEB181A1D;
    private static final int COL_WELL_BG       = 0xFF121316;
    private static final int COL_BTN_GRAY      = 0xFF3E4147;
    private static final int COL_BTN_GRAY_HOV  = 0xFF52565E;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int COL_ORANGE_HOV    = 0xFFFFAA5A;
    private static final int COL_DANGER_RED    = 0xFFD94040;
    private static final int COL_DANGER_HOV    = 0xFFF05454;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFB0B5BC;
    private static final int TEXT_DARK         = 0xFF181A1D;

    private static final int HEADER_H = 22;

    @Nullable
    private final Screen parentScreen;
    private final BooleanConsumer callback;
    private final Component message;
    private final Component confirmText;
    private final Component cancelText;
    private final boolean isDestructive;

    private int winX, winY, winW, winH;
    private int confirmBtnX, cancelBtnX, btnY, btnW, btnH;

    /**
     * Standard constructor matching Vanilla ConfirmScreen signature (defaults to destructive style for delete prompts).
     */
    public FicsitConfirmScreen(BooleanConsumer callback, Component title, Component message) {
        this(Minecraft.getInstance().screen, callback, title, message,
                Component.translatable("gui.satisfactory_build_tool.delete"),
                Component.translatable("gui.satisfactory_build_tool.clear_mode.cancel"),
                true);
    }

    /**
     * Full constructor for custom confirm/cancel labels and standard (Orange) vs destructive (Red) styling.
     */
    public FicsitConfirmScreen(@Nullable Screen parentScreen, BooleanConsumer callback,
                               Component title, Component message,
                               Component confirmText, Component cancelText,
                               boolean isDestructive) {
        super(title);
        this.parentScreen = parentScreen;
        this.callback = callback;
        this.message = message;
        this.confirmText = confirmText;
        this.cancelText = cancelText;
        this.isDestructive = isDestructive;
    }

    @Override
    protected void init() {
        super.init();
        this.winW = 320;
        this.winH = 138;
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(16, (this.height - this.winH - 28) / 2);

        this.btnW = 136;
        this.btnH = 20;
        this.btnY = this.winY + this.winH - 28;
        this.confirmBtnX = this.winX + 16;
        this.cancelBtnX = this.winX + this.winW - 16 - this.btnW;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x78090A0C);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int accentColor = isDestructive ? COL_DANGER_RED : COL_FICSIT_ORANGE;

        // 1. Top Header Bar
        g.fill(winX, winY, winX + winW, winY + HEADER_H, COL_HEADER_BG);
        g.fill(winX, winY + HEADER_H - 2, winX + winW, winY + HEADER_H, accentColor);

        // Warning Icon + Title
        g.drawString(this.font, "⚠", winX + 8, winY + 6, accentColor, false);
        g.drawString(this.font, this.title, winX + 22, winY + 6, TEXT_WHITE, false);

        // Top-Right 'X' Close Button
        int closeX = winX + winW - 24;
        boolean closeHover = mouseX >= closeX && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H;
        if (closeHover) {
            g.fill(closeX, winY, winX + winW, winY + HEADER_H - 2, 0xFF40444B);
        }
        g.fill(closeX - 1, winY + 3, closeX, winY + HEADER_H - 4, 0xFF3A3D42);
        g.drawCenteredString(this.font, "✕", closeX + 12, winY + 6, TEXT_WHITE);

        // 2. Main Modal Body & Message Well
        int bodyY = winY + HEADER_H;
        int bodyH = winH - HEADER_H;
        g.fill(winX, bodyY, winX + winW, bodyY + bodyH, COL_BODY_BG);

        int wellX = winX + 12;
        int wellY = bodyY + 10;
        int wellW = winW - 24;
        int wellH = bodyH - 46;
        g.fill(wellX, wellY, wellX + wellW, wellY + wellH, COL_WELL_BG);
        g.renderOutline(wellX, wellY, wellW, wellH, 0xFF282B30);

        List<FormattedCharSequence> lines = this.font.split(this.message, wellW - 16);
        int textStartY = wellY + (wellH - (lines.size() * 10)) / 2;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            g.drawString(this.font, line, winX + (winW - this.font.width(line)) / 2, textStartY + i * 10, TEXT_GRAY, false);
        }

        // 3. Confirm & Cancel FICSIT Buttons
        boolean confirmHover = mouseX >= confirmBtnX && mouseX < confirmBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        int confirmBg = isDestructive
                ? (confirmHover ? COL_DANGER_HOV : COL_DANGER_RED)
                : (confirmHover ? COL_ORANGE_HOV : COL_FICSIT_ORANGE);
        g.fill(confirmBtnX, btnY, confirmBtnX + btnW, btnY + btnH, confirmBg);
        g.renderOutline(confirmBtnX, btnY, btnW, btnH, 0xFF141518);
        g.drawCenteredString(this.font, confirmText, confirmBtnX + btnW / 2, btnY + 6,
                isDestructive ? TEXT_WHITE : TEXT_DARK);

        boolean cancelHover = mouseX >= cancelBtnX && mouseX < cancelBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(cancelBtnX, btnY, cancelBtnX + btnW, btnY + btnH, cancelHover ? COL_BTN_GRAY_HOV : COL_BTN_GRAY);
        g.renderOutline(cancelBtnX, btnY, btnW, btnH, 0xFF141518);
        g.drawCenteredString(this.font, cancelText, cancelBtnX + btnW / 2, btnY + 6, TEXT_WHITE);

        // Outer Window Border
        g.renderOutline(winX, winY, winW, winH, 0xFF141518);

        super.render(g, mouseX, mouseY, partialTick);
        renderBottomKeybindBar(g);
    }

    private void renderBottomKeybindBar(GuiGraphics g) {
        String[][] callouts = {
                {confirmText.getString(), "Enter / LMB"},
                {cancelText.getString(), "Escape"}
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

    private void triggerAction(boolean confirmed) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, confirmed ? 1.1f : 0.9f);
        }
        this.callback.accept(confirmed);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Top-right 'X' button
            if (mouseX >= winX + winW - 24 && mouseX < winX + winW && mouseY >= winY && mouseY < winY + HEADER_H) {
                triggerAction(false);
                return true;
            }
            // Confirm button
            if (mouseX >= confirmBtnX && mouseX < confirmBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                triggerAction(true);
                return true;
            }
            // Cancel button
            if (mouseX >= cancelBtnX && mouseX < cancelBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                triggerAction(false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            triggerAction(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            triggerAction(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.parentScreen != null && Minecraft.getInstance().screen == this) {
            Minecraft.getInstance().setScreen(this.parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}