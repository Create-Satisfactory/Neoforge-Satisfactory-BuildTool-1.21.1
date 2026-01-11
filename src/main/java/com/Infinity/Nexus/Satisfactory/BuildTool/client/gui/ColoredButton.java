package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ColorUtils;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ColoredButton extends Button {
    private final int backgroundColor;
    private final int textColor;
    private final boolean hasBackground;
    
    public ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int textColor, boolean hasBackground) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.hasBackground = hasBackground;
    }
    
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.visible) {
            // Draw background if enabled
            if (hasBackground) {
                int bg = this.isHovered ? ColorUtils.lighten(backgroundColor, 0.2f) : backgroundColor;
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);
                
                // Border
                int borderColor = this.isHovered ? ModConstants.Colors.WHITE : ModConstants.Colors.LIGHT_GRAY;
                guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);
            }
            
            // Draw text
            int textX = this.getX() + this.width / 2;
            int textY = this.getY() + (this.height - 8) / 2;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), textX, textY, textColor);
        }
    }
    
    public static Builder coloredBuilder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }
    
    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private int backgroundColor = ModConstants.Colors.GRAY;
        private int textColor = 0xFFFFFF;
        private boolean hasBackground = true;
        
        public Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }
        
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder backgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }
        
        public Builder textColor(int color) {
            this.textColor = color;
            return this;
        }
        
        public Builder noBackground() {
            this.hasBackground = false;
            return this;
        }
        
        public ColoredButton build() {
            return new ColoredButton(x, y, width, height, message, onPress, backgroundColor, textColor, hasBackground);
        }
    }
}