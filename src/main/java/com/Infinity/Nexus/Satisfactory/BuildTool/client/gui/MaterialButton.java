package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class MaterialButton extends Button {
    private final Config.MaterialEntry material;
    
    public MaterialButton(int x, int y, int width, int height, Config.MaterialEntry material, Runnable onPress) {
        super(x, y, width, height, Component.literal(material.name), 
              button -> onPress.run(), DEFAULT_NARRATION);
        this.material = material;
    }
    
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        int bgColor = this.isHovered() ? 0xFF606060 : 0xFF404040;
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
        
        // Border
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xFF808080);
        graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, 0xFF808080);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, 0xFF808080);
        graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF808080);
        
        // Render block icon
        try {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(material.blockId));
            if (block != null) {
                ItemStack stack = new ItemStack(block);
                int iconSize = 16;
                int iconX = this.getX() + (this.width - iconSize) / 2;
                int iconY = this.getY() + 2;
                graphics.renderItem(stack, iconX, iconY);
            }
        } catch (Exception ignored) {}
        
        // Material name
        graphics.drawCenteredString(Minecraft.getInstance().font, material.name, 
                this.getX() + this.width / 2, this.getY() + this.height - 10, 0xFFFFFF);
    }
}
