package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ColorUtils;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

public class ColorButton extends AbstractWidget {
    private final DyeColor color;
    private final int colorValue;
    private final Runnable onPress;
    
    public ColorButton(int x, int y, int width, int height, DyeColor color, int colorValue, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.color = color;
        this.colorValue = colorValue;
        this.onPress = onPress;
    }
    
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.visible) {
            // Draw colored background
            int bgColor = this.isHovered ? ColorUtils.lighten(colorValue, 0.2f) : colorValue;
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            
            // Draw white border
            int borderColor = this.isHovered ? ModConstants.Colors.WHITE : ModConstants.Colors.LIGHT_GRAY;
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);
        }
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onPress.run();
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Narration not needed for color buttons
    }
}