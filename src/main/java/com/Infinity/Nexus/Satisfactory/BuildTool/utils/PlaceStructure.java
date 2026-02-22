package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureCompleteS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.*;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.PlayerProgression;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceStructure {
    private static int getBlocksPerTick() { return Config.blocks_per_tick; }
    private static final Queue<StructureBuildTask> BUILD_QUEUE = new ArrayDeque<>();

    public static void buildStructure(StructureLoader.StructureData structure, BlockPos position, float rotationY, ServerPlayer player) {
        if (structure == null) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.invalid_structure").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        int pendingBuilds = countPendingBuilds(player);
        if (pendingBuilds >= Config.max_queued_structures) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.build_in_progress")
                    .append(" (Limit: " + Config.max_queued_structures + ")")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }

        try {
            // Progression Validation
            if (!player.isCreative()) {
                PlayerProgression progression = ProgressionHandler.getProgression(player.getUUID());
                boolean canBuild = false;

                if (progression.isStructureUnlocked(structure.getName())) {
                    canBuild = true;
                } else if (progression.getTier() >= structure.getTier()) {
                    if (structure.getSubtier() == null || structure.getSubtier().isEmpty() ||
                            progression.hasCompletedSubtier(structure.getSubtier())) {
                        canBuild = true;
                    }
                }

                if (!canBuild) {
                    player.displayClientMessage(Component.translatable(
                            "message.satisfactory_build_tool.structure_locked",
                            structure.getName()
                    ).withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }
            }

            // Check for required items
            if (!player.isCreative() && (structure.getTier() > 0 || !structure.getRequiredItems().isEmpty())) {
                List<StructureLoader.RequiredItem> requiredItems = structure.getRequiredItems();
                boolean hasAllItems = true;

                for (StructureLoader.RequiredItem req : requiredItems) {
                    if (!playerHasItem(player, req.getItemId(), req.getCount())) {
                        hasAllItems = false;
                        String itemName = req.getItemId();
                        try {
                            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(req.getItemId()));
                            if (item != null) {
                                itemName = item.getDescription().getString();
                            }
                        } catch (Exception ignored) {}
                        player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.missing_item", req.getCount(), itemName).withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                }

                if (!hasAllItems) return;

                if (structure.isOnlyItems()) {
                    for (StructureLoader.RequiredItem req : requiredItems) {
                        consumeItem(player, req.getItemId(), req.getCount());
                    }
                }
            }

            ServerLevel serverLevel = player.serverLevel();

            if (!checkAreaClear(structure, position, rotationY, serverLevel)) {
                if (!Config.allow_obstruction_clearing) {
                    player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.area_obstructed").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                } else {
                     player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.clearing_obstructions").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                }
            }

            boolean isCreativeOrOnlyItems = player.isCreative() || structure.isOnlyItems();

            StructureBuildTask mainTask = new StructureBuildTask(structure, position, player, serverLevel, isCreativeOrOnlyItems, rotationY);
            int totalBlocks = mainTask.collectBlocks();

            if (totalBlocks == 0) {
                player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.no_blocks_to_place").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                return;
            }

            synchronized (BUILD_QUEUE) {
                BUILD_QUEUE.offer(mainTask);
            }

            player.displayClientMessage(Component.translatable(
                    "message.satisfactory_build_tool.building_structure",
                    structure.getName(), totalBlocks, getBlocksPerTick()
            ).withStyle(net.minecraft.ChatFormatting.GREEN), true);

        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Error building structure: {}", e.getMessage(), e);
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.build_error", e.getMessage()).withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    public static void tick() {
        synchronized (BUILD_QUEUE) {
            if (BUILD_QUEUE.isEmpty()) return;
            int blocksProcessed = 0;
            Queue<StructureBuildTask> completedTasks = new ArrayDeque<>();
            java.util.Set<java.util.UUID> processedPlayers = new java.util.HashSet<>();

            for (StructureBuildTask task : BUILD_QUEUE) {
                if (blocksProcessed >= getBlocksPerTick()) break;
                
                java.util.UUID playerUUID = task.player.getUUID();
                if (processedPlayers.contains(playerUUID)) continue; // Sequential building per player

                int processed = task.process(getBlocksPerTick() - blocksProcessed);
                blocksProcessed += processed;
                
                if (task.isCompleted()) {
                    completedTasks.offer(task);
                } else {
                    processedPlayers.add(playerUUID); // Don't process other tasks for this player in this tick
                }
                
                if (blocksProcessed >= getBlocksPerTick()) break;
            }
            BUILD_QUEUE.removeAll(completedTasks);
        }
    }

    private static int countPendingBuilds(ServerPlayer player) {
        int count = 0;
        synchronized (BUILD_QUEUE) {
            for (StructureBuildTask task : BUILD_QUEUE) {
                if (task.player.getUUID().equals(player.getUUID())) count++;
            }
        }
        return count;
    }

    private static class StructureBuildTask {
        private final StructureLoader.StructureData structure;
        private final BlockPos startPos;
        private final ServerPlayer player;
        private final ServerLevel level;
        private final boolean isCreative;
        private final float rotationY;
        private final Queue<BlockPlacement> blocksToPlace = new ArrayDeque<>();
        private int totalBlocks = 0;
        private int blocksPlaced = 0;
        private boolean completed = false;

        public StructureBuildTask(StructureLoader.StructureData structure, BlockPos startPos, ServerPlayer player, ServerLevel level, boolean isCreative, float rotationY) {
            this.structure = structure;
            this.startPos = startPos;
            this.player = player;
            this.level = level;
            this.isCreative = isCreative;
            this.rotationY = rotationY;
        }

        public int collectBlocks() {
            int width = structure.getWidth();
            int length = structure.getLength();
            int height = structure.getHeight();
            Direction direction = getDirectionFromYaw(rotationY);
            int selectedColorIndex = 0;
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof BuildTool) {
                selectedColorIndex = BuildTool.getColor(mainHandItem);
            }
            String templateBlockStr = Config.template_block;
            List<String> colorBlocks = Config.color_blocks;

            for (int y = 0; y < height; y++) {
                List<List<String>> layer = structure.getPattern().get(y);
                for (int z = 0; z < length; z++) {
                    List<String> row = layer.get(z);
                    for (int x = 0; x < width; x++) {
                        String blockString = row.get(x);
                        if (!blockString.equals(" ") && !blockString.isEmpty()) {
                             // SECURITY FIX: Only official server structures can be colored or materialized
                             boolean isOfficial = structure.isServerStructure();
                             
                             // Color replacement logic (only for official structures)
                             if (isOfficial && blockString.contains(templateBlockStr) && !colorBlocks.isEmpty() && selectedColorIndex >= 0 && selectedColorIndex < colorBlocks.size()) {
                                 String replacementId = colorBlocks.get(selectedColorIndex);
                                 if (blockString.contains("[")) {
                                     blockString = replacementId + blockString.substring(blockString.indexOf("["));
                                 } else {
                                     blockString = replacementId;
                                 }
                             }
                             
                             // Material replacement logic (only for official structures)
                             if (isOfficial) {
                                 String selectedMaterial = BuildTool.getMaterial(mainHandItem);
                                 if (selectedMaterial != null && !selectedMaterial.isEmpty()) {
                                     Config.MaterialEntry materialEntry = Config.material_blocks.stream()
                                         .filter(m -> m.name.equals(selectedMaterial))
                                         .findFirst().orElse(null);
                                     
                                     if (materialEntry != null && blockString.contains(Config.base_material)) {
                                         blockString = blockString.replace(Config.base_material, materialEntry.blockId);
                                         
                                         // If material is concrete AND we have a color selected, apply color too
                                         if (materialEntry.blockId.contains("_concrete") && selectedColorIndex >= 0 && selectedColorIndex < colorBlocks.size()) {
                                             String concreteColor = colorBlocks.get(selectedColorIndex);
                                             // Replace gray_concrete with the selected color concrete
                                             if (blockString.contains("gray_concrete")) {
                                                 blockString = blockString.replace("gray_concrete", concreteColor.replace("minecraft:", ""));
                                             }
                                         }
                                     }
                                 }
                             }
                            BlockPos blockPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                            BlockState blockState = structure.getBlockStateForString(blockString);
                            if (blockState != null) {
                                blocksToPlace.offer(new BlockPlacement(rotateBlockState(blockState, direction), blockPos));
                                totalBlocks++;
                            }
                        }
                    }
                }
            }
            return totalBlocks;
        }

        public int process(int maxBlocks) {
            int processed = 0;
            while (processed < maxBlocks && !blocksToPlace.isEmpty()) {
                BlockPlacement placement = blocksToPlace.poll();
                if (tryPlaceBlock(placement.blockState, placement.pos, player, level, isCreative)) blocksPlaced++;
                processed++;
            }
            if (blocksToPlace.isEmpty()) {
                completed = true;
                player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.build_complete", structure.getName(), blocksPlaced, totalBlocks, (isCreative ? " (Creative Mode)" : "")).withStyle(net.minecraft.ChatFormatting.GREEN), true);
                
                // Calculate bounds
                int width = structure.getWidth();
                int height = structure.getHeight();
                int length = structure.getLength();
                Direction direction = getDirectionFromYaw(rotationY);
                
                BlockPos p1 = getRotatedBlockPos(startPos, 0, 0, 0, width, length, direction);
                BlockPos p2 = getRotatedBlockPos(startPos, width - 1, height - 1, length - 1, width, length, direction);
                
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

                PlacedStructure placed = new PlacedStructure(UUID.randomUUID(), structure.getName(), minPos, maxPos, startPos, rotationY, level.dimension());
                ModMessages.sendToPlayer(new StructureCompleteS2CPacket(placed), player);
            }
            return processed;
        }

        public boolean isCompleted() { return completed; }
    }

    private static BlockPos getRotatedBlockPos(BlockPos startPos, int x, int y, int z, int width, int length, Direction direction) {
        int rotatedX = x, rotatedZ = z;
        switch (direction) {
            case EAST: rotatedX = length - 1 - z; rotatedZ = x; break;
            case SOUTH: rotatedX = width - 1 - x; rotatedZ = length - 1 - z; break;
            case WEST: rotatedX = z; rotatedZ = width - 1 - x; break;
            default: break;
        }
        return startPos.offset(rotatedX, y, rotatedZ);
    }

    private static BlockState rotateBlockState(BlockState state, Direction direction) {
        BlockState rotated = state;
        if (rotated.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) rotated = rotateAxis(rotated, direction);
        if (rotated.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) rotated = rotateFacing(rotated, direction);
        if (rotated.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) rotated = rotateHorizontalFacing(rotated, direction);
        if (rotated.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16)) rotated = rotateRotation(rotated, direction);
        return rotated;
    }

    private static BlockState rotateAxis(BlockState state, Direction dir) {
        Direction.Axis axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
        if (axis == Direction.Axis.Y) return state;
        if (dir == Direction.EAST || dir == Direction.WEST) return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return state;
    }

    private static BlockState rotateFacing(BlockState state, Direction dir) {
        Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
        if (facing.getAxis() == Direction.Axis.Y) return state;
        return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, rotateDirection(facing, dir));
    }

    private static BlockState rotateHorizontalFacing(BlockState state, Direction dir) {
        return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, rotateDirection(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING), dir));
    }

    private static BlockState rotateRotation(BlockState state, Direction dir) {
        int r = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16);
        int offset = (dir == Direction.EAST) ? 4 : (dir == Direction.SOUTH) ? 8 : (dir == Direction.WEST) ? 12 : 0;
        return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16, (r + offset) % 16);
    }

    private static Direction rotateDirection(Direction original, Direction rotation) {
        if (original.getAxis() == Direction.Axis.Y) return original;
        int rot = (rotation == Direction.EAST) ? 1 : (rotation == Direction.SOUTH) ? 2 : (rotation == Direction.WEST) ? 3 : 0;
        Direction res = original;
        for (int i = 0; i < rot; i++) res = res.getClockWise();
        return res;
    }

    private static Direction getDirectionFromYaw(float yaw) {
        float norm = (yaw + 360) % 360;
        if (norm >= 315 || norm < 45) return Direction.SOUTH;
        if (norm >= 45 && norm < 135) return Direction.WEST;
        if (norm >= 135 && norm < 225) return Direction.NORTH;
        return Direction.EAST;
    }

    private static int getStructureSizeInDirection(StructureLoader.StructureData structure, Direction structRotation, Direction zoopDir) {
        int w = structure.getWidth(), l = structure.getLength(), h = structure.getHeight();
        int rw = (structRotation == Direction.EAST || structRotation == Direction.WEST) ? l : w;
        int rl = (structRotation == Direction.EAST || structRotation == Direction.WEST) ? w : l;
        return switch (zoopDir.getAxis()) { case X -> rw; case Z -> rl; case Y -> h; };
    }

    private static boolean playerHasItem(ServerPlayer player, String itemId, int count) {
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(itemId));
            int found = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == item) found += stack.getCount();
            }
            return found >= count;
        } catch (Exception e) { return false; }
    }

    private static void consumeItem(ServerPlayer player, String itemId, int count) {
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(itemId));
            int remaining = count;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == item) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                    if (remaining <= 0) break;
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean tryPlaceBlock(BlockState targetState, BlockPos pos, ServerPlayer player, ServerLevel level, boolean isCreative) {
        try {
            Block targetBlock = targetState.getBlock();
            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir() && !currentState.canBeReplaced() && !currentState.is(targetBlock)) {
                player.gameMode.destroyBlock(pos);
            }
            if (!isCreative) {
                // Procurar o item correto no inventário baseado no bloco necessário
                if (!hasBlockInInventory(targetBlock, player)) return false;
            }
            return placeBlockAsPlayer(targetState, pos, player, level, isCreative);
        } catch (Exception e) { 
            SatisfactoryBuildTool.LOGGER.error("Error trying to place block at " + pos + ": " + e.getMessage());
            return false; 
        }
    }

    private static ItemStack findBlockInInventory(Block block, ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (Block.byItem(stack.getItem()) == block) return stack;
        }
        return ItemStack.EMPTY;
    }
    
    private static boolean hasBlockInInventory(Block block, ServerPlayer player) {
        int needed = 1; // We need at least 1 block
        int found = 0;
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (Block.byItem(stack.getItem()) == block) {
                found += stack.getCount();
                if (found >= needed) return true;
            }
        }
        return false;
    }

    private static boolean placeBlockAsPlayer(BlockState targetState, BlockPos pos, ServerPlayer player, ServerLevel level, boolean isCreative) {
        try {
            FakePlayer fakePlayer = new FakePlayer(level, new GameProfile(UUID.fromString("f66c412e-8c88-4d12-a0f4-1c55fd08f34f"), "[CREATIVITYORB]"));
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(targetState.getBlock().asItem()));
            UseOnContext ctx = new UseOnContext(fakePlayer, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).subtract(0, 1, 0), Direction.UP, pos, false));
            if (CommonHooks.onPlaceItemIntoWorld(ctx).consumesAction()) {
                if (!isCreative) {
                    ItemStack s = findBlockInInventory(targetState.getBlock(), player);
                    if (!s.isEmpty()) s.shrink(1);
                }
                level.setBlock(pos, targetState, 3);
                return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private static class BlockPlacement {
        public final BlockState blockState;
        public final BlockPos pos;
        public BlockPlacement(BlockState state, BlockPos pos) { this.blockState = state; this.pos = pos; }
    }
    private static boolean checkAreaClear(StructureLoader.StructureData structure, BlockPos startPos, float rotationY, ServerLevel level) {
        int width = structure.getWidth();
        int length = structure.getLength();
        int height = structure.getHeight();
        Direction direction = getDirectionFromYaw(rotationY);

        for (int y = 0; y < height; y++) {
            List<List<String>> layer = structure.getPattern().get(y);
            for (int z = 0; z < length; z++) {
                List<String> row = layer.get(z);
                for (int x = 0; x < width; x++) {
                    String blockString = row.get(x);
                    // Only check positions where we are actually placing a block
                    if (!blockString.equals(" ") && !blockString.isEmpty()) {
                        BlockPos blockPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                        BlockState state = level.getBlockState(blockPos);
                        
                        // If it's not air and not replaceable, it's an obstruction
                        if (!state.isAir() && !state.canBeReplaced()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}