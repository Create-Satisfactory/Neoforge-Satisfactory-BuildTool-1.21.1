package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructurePreview;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PlacedStructure;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureDestroyC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.BuildToolClient;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof BuildTool && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.options.keyUse.matches(event.getKey(), event.getScanCode())) {
                handleUseBinding(mc, mc.player);
            } else if (mc.options.keyAttack.matches(event.getKey(), event.getScanCode())) {
                handleAttackBinding(mc, mc.player);
            }
        }
        
        if (KeyBindings.ROTATE_KEY.consumeClick()) {
            StructurePreview.rotatePreview();
        }
        
        if (KeyBindings.FIX_KEY.consumeClick()) {
            StructurePreview.toggleFixed();
        }

        if (KeyBindings.DESTRUCTION_MODE_KEY.consumeClick()) {
            BuildTool.cycleMode(mc.player);
        }

        if (KeyBindings.CYCLE_STRUCTURE_KEY.consumeClick()) {
            BuildTool.cycleStructures(mc.player);
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;

        Player player = mc.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof BuildTool)) return;

        // Check Minecraft's configured "Use Item / Place Block" binding (Default: Right-Click)
        if (mc.options.keyUse.matchesMouse(event.getButton())) {
            handleUseBinding(mc, player);
        }
        // Check Minecraft's configured "Attack / Destroy" binding (Default: Left-Click)
        else if (mc.options.keyAttack.matchesMouse(event.getButton())) {
            handleAttackBinding(mc, player);
        }
    }

    private static void handleUseBinding(Minecraft mc, Player player) {
        if (BuildTool.getMode() == BuildTool.BuildMode.DESTRUCTION) {
            // Handle destruction mode - check if player clicked on a structure (100 block reach)
            HitResult hitResult = player.pick(100.0D, 0.0F, false);
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                BlockPos pos = blockHit.getBlockPos();

                // Find structure at the clicked position (or containing it from a distance)
                PlacedStructure structure = ClientStructureTracker.findStructureAt(pos, player.level());
                if (structure == null) {
                    structure = ClientStructureTracker.findStructureContaining(pos, player.level());
                }

                if (structure != null) {
                    ModMessages.sendToServer(new StructureDestroyC2SPacket(structure));
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }
        }
    }

    private static void handleAttackBinding(Minecraft mc, Player player) {
        if (BuildTool.getMode() == BuildTool.BuildMode.DESTRUCTION) {
            // Handle destruction mode with Ctrl + Attack to only remove the red bounding box
            if (isCtrlKeyDown()) {
                HitResult hitResult = player.pick(100.0D, 0.0F, false);
                if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hitResult;
                    BlockPos pos = blockHit.getBlockPos();

                    PlacedStructure structure = ClientStructureTracker.findStructureAt(pos, player.level());
                    if (structure == null) {
                        structure = ClientStructureTracker.findStructureContaining(pos, player.level());
                    }

                    if (structure != null) {
                        ClientStructureTracker.removeStructure(structure.id());
                        player.swing(InteractionHand.MAIN_HAND);
                        player.displayClientMessage(
                                Component.translatable("message.satisfactory_build_tool.red_box_removed")
                                        .withStyle(ChatFormatting.YELLOW),
                                true
                        );
                    }
                }
            }
        } else if (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) {
            BlockPos point1 = BuildTool.getPoint1(player);
            BlockPos point2 = BuildTool.getPoint2(player);

            // 1. If attacking in the air, reset the current partial/full selection
            if (point1 != null || point2 != null) {
                if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                    BuildTool.resetParcialSelection(player);
                    player.displayClientMessage(
                            Component.translatable("message.infinity_nexus.creativity.selection_reset")
                                    .withStyle(ChatFormatting.YELLOW),
                            true
                    );
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
                    return;
                }
            }

            // 2. If attacking a block inside the selected area, open the Clear Mode screen
            if (point1 != null && point2 != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos clickedPos = ((BlockHitResult) mc.hitResult).getBlockPos();

                int minX = Math.min(point1.getX(), point2.getX());
                int minY = Math.min(point1.getY(), point2.getY());
                int minZ = Math.min(point1.getZ(), point2.getZ());
                int maxX = Math.max(point1.getX(), point2.getX());
                int maxY = Math.max(point1.getY(), point2.getY());
                int maxZ = Math.max(point1.getZ(), point2.getZ());

                if (clickedPos.getX() >= minX && clickedPos.getX() <= maxX &&
                        clickedPos.getY() >= minY && clickedPos.getY() <= maxY &&
                        clickedPos.getZ() >= minZ && clickedPos.getZ() <= maxZ) {
                    BuildToolClient.openClearModeScreen();
                }
            }
        }
    }
    
    private static boolean isCtrlKeyDown() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1 ||
               org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == 1;
    }
}
