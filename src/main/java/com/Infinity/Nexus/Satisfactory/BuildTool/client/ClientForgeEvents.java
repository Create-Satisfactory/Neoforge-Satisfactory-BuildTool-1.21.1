package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructurePreview;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) return;
        
        if (KeyBindings.ROTATE_KEY.consumeClick()) {
            StructurePreview.rotatePreview();
        }
        
        if (KeyBindings.FIX_KEY.consumeClick()) {
            StructurePreview.toggleFixed();
        }

        if (KeyBindings.DESTRUCTION_MODE_KEY.consumeClick()) {
            BuildTool.cycleMode(Minecraft.getInstance().player);
        }

        if (KeyBindings.CYCLE_STRUCTURE_KEY.consumeClick()) {
            BuildTool.cycleStructures(Minecraft.getInstance().player);
        }

        if (KeyBindings.ZOOP_KEY.consumeClick()) {
            StructurePreview.toggleZoop();
        }

        if (StructurePreview.isFixed()) {
            if (KeyBindings.MOVE_UP_KEY.consumeClick()) StructurePreview.movePreview(Direction.UP);
            if (KeyBindings.MOVE_DOWN_KEY.consumeClick()) StructurePreview.movePreview(Direction.DOWN);
            if (KeyBindings.MOVE_NORTH_KEY.consumeClick()) StructurePreview.movePreview(Direction.NORTH);
            if (KeyBindings.MOVE_SOUTH_KEY.consumeClick()) StructurePreview.movePreview(Direction.SOUTH);
            if (KeyBindings.MOVE_WEST_KEY.consumeClick()) StructurePreview.movePreview(Direction.WEST);
            if (KeyBindings.MOVE_EAST_KEY.consumeClick()) StructurePreview.movePreview(Direction.EAST);
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen != null) return;

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.getMainHandItem().getItem() instanceof BuildTool) {
                if(BuildTool.getMode() == BuildTool.BuildMode.CLEAR) {
                    if (BuildTool.getPoint1(player) != null || BuildTool.getPoint2(player) != null) {
                        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                            BuildTool.resetParcialSelection(player);
                            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.selection_reset").withStyle(ChatFormatting.YELLOW), true);
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
                        }
                    }
                }
            }
        }
    }
}
