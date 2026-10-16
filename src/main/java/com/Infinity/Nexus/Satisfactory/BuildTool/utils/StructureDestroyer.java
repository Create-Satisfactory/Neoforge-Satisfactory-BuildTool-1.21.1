package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.AreaClearC2SPacket.ClearType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class StructureDestroyer {
    private static int getBlocksPerTick() { return Config.blocks_per_tick; }
    private static final Queue<ClearTask> CLEAR_QUEUE = new ArrayDeque<>();

    public static void destroyStructure(ServerPlayer player, PlacedStructure clientPlaced) {
        ServerLevel level = player.serverLevel();
        ServerStructureTracker tracker = ServerStructureTracker.get(level);

        // Find authoritative structure on the server (by UUID or by position)
        PlacedStructure placed = tracker.getStructureById(clientPlaced.id());
        if (placed == null) {
            placed = tracker.getStructureAt(clientPlaced.startPos());
        }
        if (placed == null) {
            placed = clientPlaced;
        }

        StructureLoader.StructureData structureData = StructureLoader.getServerStructure(placed.structureName());
        if (structureData == null) {
            structureData = StructureLoader.getClientStructure(placed.structureName());
        }

        // Retrieve exact tracked block coordinates before unregistering
        List<BlockPos> trackedBlocks = new ArrayList<>(tracker.getBlocksForStructure(placed.id()));
        tracker.removeStructure(level, placed.id());

        refundAndDestroy(player, level, placed, structureData, trackedBlocks);
    }

    private static void refundAndDestroy(ServerPlayer player, ServerLevel level, PlacedStructure placed,
                                         StructureLoader.StructureData structure, List<BlockPos> trackedBlocks) {
        List<ItemStack> refunds = new ArrayList<>();

        // 1. Calculate FICSIT item refund
        if (!player.isCreative() && structure != null) {
            List<StructureLoader.RequiredItem> costs = structure.isOnlyItems()
                    ? structure.getRequiredItems()
                    : structure.getAggregatedCosts();

            for (StructureLoader.RequiredItem req : costs) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
                if (item != null && item != Items.AIR) {
                    int remaining = req.getCount();
                    while (remaining > 0) {
                        int stackSize = Math.min(remaining, item.getDefaultMaxStackSize());
                        refunds.add(new ItemStack(item, stackSize));
                        remaining -= stackSize;
                    }
                }
            }
        }

        // 2. If trackedBlocks is empty (legacy structure), compute positions from pattern
        if (trackedBlocks.isEmpty() && structure != null) {
            Direction direction = StructureGeometryHelper.getDirectionFromYaw(placed.rotation());
            int width = structure.getWidth();
            int length = structure.getLength();
            int height = structure.getHeight();
            BlockPos startPos = placed.startPos();

            for (int y = 0; y < height && y < structure.getPattern().size(); y++) {
                var layer = structure.getPattern().get(y);
                for (int z = 0; z < length && z < layer.size(); z++) {
                    var row = layer.get(z);
                    for (int x = 0; x < width && x < row.size(); x++) {
                        String blockStr = row.get(x);
                        if (!blockStr.equals(" ") && !blockStr.isEmpty()) {
                            trackedBlocks.add(StructureGeometryHelper.getRotatedBlockPos(startPos, x, y, z, width, length, direction));
                        }
                    }
                }
            }
        }

        // 3. Remove all blocks of the structure cleanly without Copycats dropping their inner camo blocks!
        for (BlockPos targetPos : trackedBlocks) {
            BlockState worldState = level.getBlockState(targetPos);
            if (!worldState.isAir()) {
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be != null) {
                    // If it's a standard storage Container (e.g. Chest/Barrel), transfer contents directly to player
                    if (be instanceof net.minecraft.world.Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            ItemStack stack = container.getItem(i);
                            if (!stack.isEmpty()) {
                                ItemStack copy = stack.copy();
                                if (!player.getInventory().add(copy)) {
                                    ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), copy);
                                    entity.setNoPickUpDelay();
                                    level.addFreshEntity(entity);
                                }
                            }
                        }
                        container.clearContent();
                    }
                    // CRITICAL: Remove the BlockEntity BEFORE setting Air so CopycatBlock.onRemove() sees null and never drops its inner block!
                    level.removeBlockEntity(targetPos);
                }

                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS |
                                net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE |
                                net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS);
            }
        }

        // Notify neighbors after all blocks are removed
        for (BlockPos targetPos : trackedBlocks) {
            level.updateNeighborsAt(targetPos, Blocks.AIR);
        }

        // 4. Insert refunded FICSIT items directly into player's inventory (or drop at feet if full)
        for (ItemStack stack : refunds) {
            if (!player.getInventory().add(stack)) {
                ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
                entity.setNoPickUpDelay();
                level.addFreshEntity(entity);
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.structure_destroyed").withStyle(ChatFormatting.GREEN), true);
    }

    public static void clearArea(ServerPlayer player, BlockPos minPos, BlockPos maxPos, ClearType clearType) {
        ItemStack offhandStack = player.getOffhandItem();
        boolean substitute = clearType == ClearType.FLATTEN &&
                !offhandStack.isEmpty() && offhandStack.getItem() instanceof BlockItem;
        ClearTask task = new ClearTask(player, player.serverLevel(), minPos, maxPos, clearType, substitute, offhandStack.copy());
        task.collectBlocks();

        synchronized (CLEAR_QUEUE) {
            CLEAR_QUEUE.offer(task);
        }
        player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.clearing_area", task.totalBlocks).withStyle(ChatFormatting.YELLOW), true);
    }

    public static void tick() {
        synchronized (CLEAR_QUEUE) {
            if (CLEAR_QUEUE.isEmpty()) return;
            int blocksProcessed = 0;
            Queue<ClearTask> completedTasks = new ArrayDeque<>();

            for (ClearTask task : CLEAR_QUEUE) {
                if (blocksProcessed >= getBlocksPerTick()) break;
                int processed = task.process(getBlocksPerTick() - blocksProcessed);
                blocksProcessed += processed;
                if (task.isCompleted()) {
                    completedTasks.offer(task);
                }
            }
            CLEAR_QUEUE.removeAll(completedTasks);
        }
    }

    private static class ClearTask {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos minPos;
        private final BlockPos maxPos;
        private final ClearType clearType;
        private final boolean substitute;
        private final ItemStack substituteItem;
        private final Queue<BlockPos> blocksToProcess = new ArrayDeque<>();
        private int totalBlocks = 0;
        private int blocksCleared = 0;
        private boolean completed = false;
        private int currentLayerY;

        public ClearTask(ServerPlayer player, ServerLevel level, BlockPos minPos, BlockPos maxPos, ClearType clearType, boolean substitute, ItemStack substituteItem) {
            this.player = player;
            this.level = level;
            this.minPos = minPos;
            this.maxPos = maxPos;
            this.clearType = clearType;
            this.substitute = substitute;
            this.substituteItem = substituteItem;
            this.currentLayerY = maxPos.getY();
        }

        public void collectBlocks() {
            for (int y = maxPos.getY(); y >= minPos.getY(); y--) {
                for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
                    for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                        blocksToProcess.offer(new BlockPos(x, y, z));
                        totalBlocks++;
                    }
                }
            }
        }

        public int process(int maxBlocks) {
            int processed = 0;
            int scanned = 0;
            int maxScanned = maxBlocks * 20;
            while (processed < maxBlocks && scanned < maxScanned && !blocksToProcess.isEmpty()) {
                BlockPos nextPos = blocksToProcess.peek();

                if (nextPos.getY() != currentLayerY) {
                    collectLayerDrops();
                    currentLayerY = nextPos.getY();
                }
                BlockPos targetPos = blocksToProcess.poll();
                scanned++;

                if (processBlock(targetPos)) {
                    blocksCleared++;
                    processed++;
                }
            }

            if (blocksToProcess.isEmpty()) {
                collectLayerDrops();
                finish();
            }
            return processed;
        }

        private void collectLayerDrops() {
            Vec3 vStart = new Vec3(minPos.getX(), minPos.getY(), minPos.getZ());
            Vec3 vEnd = new Vec3(maxPos.getX(), maxPos.getY(), maxPos.getZ());
            AABB aabb = new AABB(vStart, vEnd).inflate(2.0);
            List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, aabb);
            for (ItemEntity entity : entities) {
                entity.teleportTo(player.getX(), player.getY(), player.getZ());
                entity.setNoPickUpDelay();
            }
        }

        private boolean processBlock(BlockPos targetPos) {
            // Never clear blocks protected by a placed Build Tool structure!
            if (ServerStructureTracker.get(level).isProtectedBlock(targetPos)) {
                return false;
            }
            if (clearType == ClearType.FLATTEN && targetPos.getY() == minPos.getY()) {
                if (substitute) {
                    tryPlaceSubstitute(player, level, targetPos, substituteItem);
                }
                return true;
            }
            BlockState worldState = level.getBlockState(targetPos);
            if (worldState.isAir()) {
                return false;
            }
            if (worldState.getDestroySpeed(level, targetPos) >= 0) {
                level.destroyBlock(targetPos, true, player);
                return true;
            }
            return false;
        }

        private void finish() {
            completed = true;
            String msg = clearType == ClearType.FLATTEN ?
                    "message.satisfactory_build_tool.area_flattened" : "message.satisfactory_build_tool.area_cleared";
            player.displayClientMessage(Component.translatable(msg).withStyle(ChatFormatting.BLUE), true);
        }

        public boolean isCompleted() { return completed; }
    }

    private static void tryPlaceSubstitute(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack template) {
        if (!(template.getItem() instanceof BlockItem blockItem)) return;
        if (ServerStructureTracker.get(level).isProtectedBlock(pos)) return;

        BlockState targetState = level.getBlockState(pos);
        if (!targetState.canBeReplaced()) {
            level.destroyBlock(pos, true, player);
        }
        if (player.getAbilities().instabuild || consumeItem(player, template)) {
            BlockState newState = blockItem.getBlock().defaultBlockState();
            level.setBlock(pos, newState, 3);
        }
    }

    private static boolean consumeItem(ServerPlayer player, ItemStack template) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}