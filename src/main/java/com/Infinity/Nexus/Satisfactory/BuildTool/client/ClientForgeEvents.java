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

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.getMainHandItem().getItem() instanceof BuildTool) {
                if (BuildTool.getMode() == BuildTool.BuildMode.DESTRUCTION) {
                    // Handle destruction mode - check if player clicked on a structure
                    // Use the same reach distance as other tools (100 blocks)
                    HitResult hitResult = player.pick(100.0D, 0.0F, false);
                    if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult blockHit = (BlockHitResult) hitResult;
                        BlockPos pos = blockHit.getBlockPos();

                        // Find structure at the clicked position
                        PlacedStructure structure = ClientStructureTracker.findStructureAt(pos, player.level());
                        if (structure != null) {
                            // Right click destroys the structure (without any modifier key)
                            // Send destroy packet to server to actually destroy the structure
                            ModMessages.sendToServer(new StructureDestroyC2SPacket(structure));
                            player.swing(InteractionHand.MAIN_HAND);
                        } else {
                            // If no structure found at the exact hit position, try to find a structure that contains this block position
                            // This allows clicking from a distance
                            structure = ClientStructureTracker.findStructureContaining(pos, player.level());
                            if (structure != null) {
                                // Right click destroys the structure (without any modifier key)
                                // Send destroy packet to server to actually destroy the structure
                                ModMessages.sendToServer(new StructureDestroyC2SPacket(structure));
                                player.swing(InteractionHand.MAIN_HAND);
                            }
                        }
                    }
                }
            }
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.getMainHandItem().getItem() instanceof BuildTool) {
                if (BuildTool.getMode() == BuildTool.BuildMode.DESTRUCTION) {
                    // Handle destruction mode with Ctrl + Left click to only remove red box
                    if (isCtrlKeyDown()) {
                        // Use the same reach distance as other tools (100 blocks)
                        HitResult hitResult = player.pick(100.0D, 0.0F, false);
                        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                            BlockHitResult blockHit = (BlockHitResult) hitResult;
                            BlockPos pos = blockHit.getBlockPos();

                            // Find structure at the clicked position
                            PlacedStructure structure = ClientStructureTracker.findStructureAt(pos, player.level());
                            if (structure != null) {
                                // Only remove the red box (visual representation) without destroying the structure
                                ClientStructureTracker.removeStructure(structure.id());
                                player.swing(InteractionHand.MAIN_HAND);
                                
                                // Show message to inform player
                                player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.red_box_removed").withStyle(ChatFormatting.YELLOW), true);
                            } else {
                                // If no structure found at the exact hit position, try to find a structure that contains this block position
                                // This allows clicking from a distance
                                structure = ClientStructureTracker.findStructureContaining(pos, player.level());
                                if (structure != null) {
                                    // Only remove the red box (visual representation) without destroying the structure
                                    ClientStructureTracker.removeStructure(structure.id());
                                    player.swing(InteractionHand.MAIN_HAND);
                                    
                                    // Show message to inform player
                                    player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.red_box_removed").withStyle(ChatFormatting.YELLOW), true);
                                }
                            }
                        }
                    }
                } else if (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) {
                    if (BuildTool.getPoint1(player) != null || BuildTool.getPoint2(player) != null) {
                        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                            BuildTool.resetParcialSelection(player);
                            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.selection_reset").withStyle(ChatFormatting.YELLOW), true);
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
                        }
                    }
                } else if (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) {
                    // Check if player clicked inside the selected area to open clear mode screen
                    BlockPos point1 = BuildTool.getPoint1(player);
                    BlockPos point2 = BuildTool.getPoint2(player);
                    if (point1 != null && point2 != null) {
                        BlockPos clickedPos = ((BlockHitResult) mc.hitResult).getBlockPos();
                        
                        // Check if clicked position is within the selected area
                        int minX = Math.min(point1.getX(), point2.getX());
                        int minY = Math.min(point1.getY(), point2.getY());
                        int minZ = Math.min(point1.getZ(), point2.getZ());
                        int maxX = Math.max(point1.getX(), point2.getX());
                        int maxY = Math.max(point1.getY(), point2.getY());
                        int maxZ = Math.max(point1.getZ(), point2.getZ());
                        
                        if (clickedPos.getX() >= minX && clickedPos.getX() <= maxX &&
                            clickedPos.getY() >= minY && clickedPos.getY() <= maxY &&
                            clickedPos.getZ() >= minZ && clickedPos.getZ() <= maxZ) {
                            // Open clear mode screen
                            BuildToolClient.openClearModeScreen();
                        }
                    }
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
