package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.AreaClearC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class ClearModeScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 10;

    public ClearModeScreen() {
        super(Component.translatable("gui.satisfactory_build_tool.clear_mode.title"));
    }

    @Override
    protected void init() {
        if (!Config.enable_clear_mode) {
            this.onClose();
            return;
        }
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = (this.height - (BUTTON_HEIGHT * 3 + SPACING * 2)) / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.satisfactory_build_tool.clear_mode.clear"),
                b -> handleClear(AreaClearC2SPacket.ClearType.ALL))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        y += BUTTON_HEIGHT + SPACING;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.satisfactory_build_tool.clear_mode.flatten"),
                b -> handleClear(AreaClearC2SPacket.ClearType.FLATTEN))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        y += BUTTON_HEIGHT + SPACING;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.satisfactory_build_tool.clear_mode.cancel"),
                b -> this.onClose())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
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

            ModMessages.sendToServer(new AreaClearC2SPacket(minPos, maxPos, type));
            BuildTool.resetSelection(player); // Reset selection after action
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, (this.height - (BUTTON_HEIGHT * 3 + SPACING * 2)) / 2 - 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
