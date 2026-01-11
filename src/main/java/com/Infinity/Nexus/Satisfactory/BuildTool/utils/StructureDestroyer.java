package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureRemoveS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.AreaClearC2SPacket.ClearType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

public class StructureDestroyer {

    private static int getBlocksPerTick() { return Config.blocks_per_tick; }
    private static final Queue<ClearTask> CLEAR_QUEUE = new ArrayDeque<>();

    public static void destroyStructure(ServerPlayer player, PlacedStructure placed) {
        if (!BuildTool.isDestructionMode()) {
        }

        StructureLoader.StructureData structureData = StructureLoader.getServerStructure(placed.structureName());
        if (structureData == null) {
            structureData = StructureLoader.getClientStructure(placed.structureName());
        }

        if (structureData == null) {
            player.displayClientMessage(Component.translatable("error.satisfactory_build_tool.structure_data_not_found").withStyle(ChatFormatting.RED), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!validateIntegrity(level, placed, structureData)) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.structure_integrity_failed").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        ModMessages.sendToPlayer(new StructureRemoveS2CPacket(placed.id()), player);
        refundAndDestroy(player, level, placed, structureData);
    }

    private static boolean validateIntegrity(ServerLevel level, PlacedStructure placed, StructureLoader.StructureData structure) {
        return true; 
    }

    private static void refundAndDestroy(ServerPlayer player, ServerLevel level, PlacedStructure placed, StructureLoader.StructureData structure) {
        Direction direction = StructureGeometryHelper.getDirectionFromYaw(placed.rotation());
        int width = structure.getWidth();
        int length = structure.getLength();
        int height = structure.getHeight();
        BlockPos startPos = placed.startPos();

        List<ItemStack> drops = new ArrayList<>();

        List<StructureLoader.RequiredItem> requiredItems = structure.getRequiredItems();
        if (requiredItems != null && !requiredItems.isEmpty()) {
            for (StructureLoader.RequiredItem req : requiredItems) {
                String itemId = req.getItemId();
                int count = req.getCount();
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                if (item != null && item != Items.AIR) {
                    drops.add(new ItemStack(item, count));
                }
            }
        } 
        
        boolean dropBlocks = structure.isOnlyItems();

        for (int y = 0; y < height; y++) {
            if (y >= structure.getPattern().size()) continue;
            var layer = structure.getPattern().get(y);
            for (int z = 0; z < length; z++) {
                if (z >= layer.size()) continue;
                var row = layer.get(z);
                for (int x = 0; x < width; x++) {
                    if (x >= row.size()) continue;
                    
                    String blockStr = row.get(x);
                    if (blockStr.equals(" ") || blockStr.isEmpty()) continue;

                    BlockPos targetPos = StructureGeometryHelper.getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                    BlockState worldState = level.getBlockState(targetPos);
                    
                    if (!worldState.isAir()) {
                         if (!dropBlocks) {
                             player.gameMode.destroyBlock(targetPos);
                         }else {
                             level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                         }
                    }
                }
            }
        }
        
        BlockPos spawnPos = player.blockPosition().above();
        for (ItemStack stack : drops) {
            ItemEntity entity = new ItemEntity(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, stack);
            level.addFreshEntity(entity);
        }

        if (!dropBlocks) {
            Vec3 vStart = new Vec3(placed.minPos().getX(), placed.minPos().getY(), placed.minPos().getZ());
            Vec3 vEnd = new Vec3(placed.maxPos().getX(), placed.maxPos().getY(), placed.maxPos().getZ());
            AABB aabb = new AABB(vStart, vEnd).expandTowards(1, 1, 1);
            List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, aabb);
            for (ItemEntity entity : entities) {
                entity.teleportTo(player.getX(), player.getY(), player.getZ());
                entity.setNoPickUpDelay();
            }
        }
        
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
            
            // Allow multiple players to clear simultaneously mostly, but limit total block ops per tick
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
            // Collect blocks Top-Down (Layer by Layer)
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
            int maxScanned = maxBlocks * 20; // Allow scanning 20x more blocks than placing/breaking to skip air fast

            while (processed < maxBlocks && scanned < maxScanned && !blocksToProcess.isEmpty()) {
                BlockPos nextPos = blocksToProcess.peek();
                
                // Detect Layer Change
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
                collectLayerDrops(); // Collect the last layer
                finish();
            }
            return processed;
        }
        
        private void collectLayerDrops() {
            // Teleport drops for the current layer scope (or entire column to be safe against gravity)
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
            // FLATTEN: Special handling for the base level
            if (clearType == ClearType.FLATTEN && targetPos.getY() == minPos.getY()) {
                if (substitute) {
                    tryPlaceSubstitute(player, level, targetPos, substituteItem);
                }
                return true;
            }

            BlockState worldState = level.getBlockState(targetPos);
            
            // Skip Air
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
        
        BlockState targetState = level.getBlockState(pos);
        if (!targetState.canBeReplaced()) {
            level.destroyBlock(pos, true, player);
        }

        // Check inventory for the item
        if (player.getAbilities().instabuild || consumeItem(player, template)) {
            BlockState newState = blockItem.getBlock().defaultBlockState();
            level.setBlock(pos, newState, 3);
        }
    }

    private static boolean consumeItem(ServerPlayer player, ItemStack template) {
        // 1. Check inventory (excluding hands)
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
