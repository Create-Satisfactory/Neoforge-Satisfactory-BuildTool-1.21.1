package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class PlaceStructure {

    // FICSIT Orange particle color (#FA9549)
    private static final DustParticleOptions FICSIT_ORANGE_PARTICLE =
            new DustParticleOptions(new Vector3f(0.98f, 0.58f, 0.28f), 1.15f);
    private static final DustParticleOptions FICSIT_CYAN_PARTICLE =
            new DustParticleOptions(new Vector3f(0.25f, 0.85f, 1.0f), 0.9f);

    private static final Queue<StructureBuildTask> BUILD_QUEUE = new ArrayDeque<>();

    // Cached reflection handles for Create's StructureTransform (supports Create & Copycats+ BlockEntities)
    private static boolean transformReflectionInitialized = false;
    private static Constructor<?> structureTransformCtor = null;
    private static Method applyBlockStateMethod = null;
    private static Method applyBlockEntityMethod = null;

    private static void initCreateTransformReflection() {
        if (transformReflectionInitialized) return;
        transformReflectionInitialized = true;
        try {
            Class<?> stClass = Class.forName("com.simibubi.create.content.contraptions.StructureTransform");
            structureTransformCtor = stClass.getConstructor(BlockPos.class, Direction.Axis.class, Rotation.class, Mirror.class);
            applyBlockStateMethod = stClass.getMethod("apply", BlockState.class);
            applyBlockEntityMethod = stClass.getMethod("apply", BlockEntity.class);
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.info("Create StructureTransform not found, using standard rotation fallback.");
        }
    }

    @Nullable
    public static Object createStructureTransform(Rotation rotation) {
        initCreateTransformReflection();
        if (structureTransformCtor == null) return null;
        try {
            return structureTransformCtor.newInstance(BlockPos.ZERO, Direction.Axis.Y, rotation, Mirror.NONE);
        } catch (Exception e) {
            return null;
        }
    }

    public static BlockState applyCreateTransform(@Nullable Object transformObj, BlockState state, Rotation rotation, Direction direction) {
        if (transformObj != null && applyBlockStateMethod != null) {
            try {
                return (BlockState) applyBlockStateMethod.invoke(transformObj, state);
            } catch (Exception ignored) {}
        }
        try {
            return state.rotate(rotation);
        } catch (Exception ignored) {
            return BlockRotationHelper.rotateBlockState(state, direction);
        }
    }

    public static void applyCreateTransformToBlockEntity(@Nullable Object transformObj, BlockEntity be) {
        if (transformObj != null && applyBlockEntityMethod != null && be != null) {
            try {
                applyBlockEntityMethod.invoke(transformObj, be);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Replaces Primary & Secondary Swatch placeholder blocks AND fallback Material blocks
     * in both outer BlockStates and inner Copycat {nbt} tags.
     */
    public static String applyCustomizerToRawString(
            String rawBlockString,
            boolean isOfficial,
            boolean usingMaterialSchematic,
            int selectedColorIndex,
            @Nullable String selectedMaterial) {
        return CustomizerManager.applyCustomizerReplacements(
                rawBlockString, isOfficial, usingMaterialSchematic, selectedColorIndex, selectedMaterial
        );
    }

    public static void buildStructure(StructureLoader.StructureData structure, BlockPos position, float rotationY, ServerPlayer player) {
        if (structure == null) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.invalid_structure").withStyle(ChatFormatting.RED), true);
            return;
        }

        int pendingBuilds = countPendingBuilds(player);
        if (pendingBuilds >= Config.max_queued_structures) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.build_in_progress")
                    .append(" (Limit: " + Config.max_queued_structures + ")")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        try {
            // 1. Team & FTB Quests Progression Validation
            if (!ProgressionHandler.isStructureUnlocked(player, structure)) {
                player.displayClientMessage(Component.translatable(
                        "message.satisfactory_build_tool.structure_locked",
                        structure.getName()
                ).withStyle(ChatFormatting.RED), true);
                return;
            }

            ItemStack heldItem = player.getMainHandItem();
            String selectedMaterial = (heldItem.getItem() instanceof BuildTool) ? BuildTool.getMaterial(heldItem) : "";
            List<StructureLoader.RequiredItem> activeRequiredItems = structure.getRequiredItemsForMaterial(selectedMaterial);

            // 2. Check and consume required items (supports per-material cost overrides)
            if (!player.isCreative() && (structure.getTier() > 0 || !activeRequiredItems.isEmpty())) {
                boolean hasAllItems = true;
                for (StructureLoader.RequiredItem req : activeRequiredItems) {
                    if (!playerHasItem(player, req.getItemId(), req.getCount())) {
                        hasAllItems = false;
                        String itemName = req.getItemId();
                        try {
                            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
                            if (item != null) itemName = item.getDescription().getString();
                        } catch (Exception ignored) {}
                        player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.missing_item", req.getCount(), itemName).withStyle(ChatFormatting.RED), true);
                    }
                }
                if (!hasAllItems) return;

                if (structure.isOnlyItems()) {
                    for (StructureLoader.RequiredItem req : activeRequiredItems) {
                        consumeItem(player, req.getItemId(), req.getCount());
                    }
                }
            }

            ServerLevel serverLevel = player.serverLevel();
            if (!checkAreaClear(structure, position, rotationY, serverLevel, player)) {
                if (!Config.allow_obstruction_clearing) {
                    player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.area_obstructed").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }

            boolean isCreativeOrOnlyItems = player.isCreative() || structure.isOnlyItems();
            StructureBuildTask mainTask = new StructureBuildTask(structure, position, player, serverLevel, isCreativeOrOnlyItems, rotationY);
            int totalBlocks = mainTask.collectBlocks();

            if (totalBlocks == 0) {
                player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.no_blocks_to_place").withStyle(ChatFormatting.YELLOW), true);
                return;
            }

            // Play initial FICSIT hologram lock-in sound
            serverLevel.playSound(null, position, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.6f);

            synchronized (BUILD_QUEUE) {
                BUILD_QUEUE.offer(mainTask);
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Error building structure: {}", e.getMessage(), e);
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.build_error", e.getMessage()).withStyle(ChatFormatting.RED), true);
        }
    }

    public static void tick() {
        synchronized (BUILD_QUEUE) {
            if (BUILD_QUEUE.isEmpty()) return;
            Queue<StructureBuildTask> completedTasks = new ArrayDeque<>();
            Set<UUID> processedPlayers = new HashSet<>();

            for (StructureBuildTask task : BUILD_QUEUE) {
                UUID playerUUID = task.player.getUUID();
                if (processedPlayers.contains(playerUUID)) continue;

                task.tickBuildStep();

                if (task.isCompleted()) {
                    completedTasks.offer(task);
                } else {
                    processedPlayers.add(playerUUID);
                }
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
        private final Direction direction;
        private final Rotation rotation;
        @Nullable
        private final Object createTransform;

        private final Queue<BlockPlacement> blocksToPlace = new ArrayDeque<>();
        private final List<BlockPos> placedPositions = new ArrayList<>();
        private int width, length, height;
        private int totalBlocks = 0;
        private int blocksPlaced = 0;
        private int blocksPerTick = 1;
        private int ticksElapsed = 0;
        private boolean completed = false;

        public StructureBuildTask(StructureLoader.StructureData structure, BlockPos startPos, ServerPlayer player,
                                  ServerLevel level, boolean isCreative, float rotationY) {
            this.structure = structure;
            this.startPos = startPos;
            this.player = player;
            this.level = level;
            this.isCreative = isCreative;
            this.rotationY = rotationY;
            this.direction = getDirectionFromYaw(rotationY);
            this.rotation = getRotationFromDirection(this.direction);
            this.createTransform = createStructureTransform(this.rotation);
        }

        public int collectBlocks() {
            int selectedColorIndex = 0;
            String selectedMaterial = "";
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof BuildTool) {
                selectedColorIndex = BuildTool.getColor(mainHandItem);
                selectedMaterial = BuildTool.getMaterial(mainHandItem);
            }

            List<List<List<String>>> activePattern = structure.getPatternForMaterial(selectedMaterial);
            boolean usingMaterialSchematic = structure.hasMaterialSchematic(selectedMaterial);

            if (activePattern == null || activePattern.isEmpty() || activePattern.get(0).isEmpty()) return 0;
            this.height = activePattern.size();
            this.length = activePattern.get(0).size();
            this.width = activePattern.get(0).get(0).size();

            double centerX = (width - 1) / 2.0;
            double centerZ = (length - 1) / 2.0;
            boolean isOfficial = structure.isServerStructure();

            // Collect layer-by-layer (Y ascending), sorting each layer from center outward for a smooth Satisfactory wave
            for (int y = 0; y < height; y++) {
                List<List<String>> layer = activePattern.get(y);
                List<BlockPlacement> layerPlacements = new ArrayList<>();

                for (int z = 0; z < length && z < layer.size(); z++) {
                    List<String> row = layer.get(z);
                    for (int x = 0; x < width && x < row.size(); x++) {
                        String rawBlockString = row.get(x);
                        if (rawBlockString == null || rawBlockString.equals(" ") || rawBlockString.isEmpty()) continue;

                        // Apply Dual-Tone Swatch & Material replacements on both outer state AND inner Copycat {nbt}
                        rawBlockString = applyCustomizerToRawString(
                                rawBlockString, isOfficial, usingMaterialSchematic, selectedColorIndex, selectedMaterial
                        );

                        String cleanStateStr = SchematicNbtHelper.stripNbt(rawBlockString);
                        CompoundTag beNbt = SchematicNbtHelper.extractNbt(rawBlockString);

                        BlockPos blockPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                        BlockState baseState = structure.getBlockStateForString(cleanStateStr);
                        if (baseState != null) {
                            BlockState rotatedState = applyCreateTransform(createTransform, baseState, rotation, direction);
                            double distFromCenter = Math.hypot(x - centerX, z - centerZ);
                            layerPlacements.add(new BlockPlacement(rotatedState, blockPos, beNbt, distFromCenter));
                        }
                    }
                }

                // Sort each horizontal layer radially from center outward
                layerPlacements.sort(Comparator.comparingDouble(p -> p.radialDist));
                for (BlockPlacement bp : layerPlacements) {
                    blocksToPlace.offer(bp);
                    totalBlocks++;
                }
            }

            // Scale build speed so structures assemble smoothly in ~20 to 48 ticks (1.0 to 2.4 seconds)
            int targetAnimationTicks = Math.clamp(16 + (int) Math.sqrt(totalBlocks) * 3, 20, 48);
            this.blocksPerTick = Math.max(1, (int) Math.ceil((double) totalBlocks / targetAnimationTicks));
            return totalBlocks;
        }

        public void tickBuildStep() {
            ticksElapsed++;
            int processedThisTick = 0;
            BlockPos lastPlacedPos = null;

            while (processedThisTick < blocksPerTick && !blocksToPlace.isEmpty()) {
                BlockPlacement placement = blocksToPlace.poll();
                if (placeSchematicBlock(placement)) {
                    blocksPlaced++;
                    placedPositions.add(placement.pos.immutable());
                    lastPlacedPos = placement.pos;

                    // Spawn FICSIT Orange materialization sparks on the newly formed block
                    if (processedThisTick % Math.max(1, blocksPerTick / 4) == 0) {
                        level.sendParticles(FICSIT_ORANGE_PARTICLE,
                                placement.pos.getX() + 0.5, placement.pos.getY() + 0.5, placement.pos.getZ() + 0.5,
                                3, 0.25, 0.25, 0.25, 0.02);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                placement.pos.getX() + 0.5, placement.pos.getY() + 0.5, placement.pos.getZ() + 0.5,
                                1, 0.2, 0.2, 0.2, 0.01);
                    }
                }
                processedThisTick++;
            }

            // Spawn a FICSIT construction beam from the player's Build Tool to the active assembly point
            if (lastPlacedPos != null) {
                spawnConstructionBeam(player, level, Vec3.atCenterOf(lastPlacedPos));

                if (ticksElapsed % 2 == 0) {
                    float progress = (float) blocksPlaced / Math.max(1, totalBlocks);
                    float pitch = 0.75f + (progress * 0.65f);
                    level.playSound(null, lastPlacedPos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.55f, pitch);
                }
            }

            if (blocksToPlace.isEmpty()) {
                finishBuild();
            }
        }

        private boolean placeSchematicBlock(BlockPlacement placement) {
            BlockPos pos = placement.pos;
            BlockState targetState = placement.blockState;

            try {
                if (ServerStructureTracker.get(level).isProtectedBlock(pos)) {
                    return false;
                }

                Block targetBlock = targetState.getBlock();
                BlockState currentState = level.getBlockState(pos);
                if (!currentState.isAir() && !currentState.canBeReplaced() && !currentState.is(targetBlock)) {
                    level.destroyBlock(pos, true, player);
                }

                if (!isCreative) {
                    if (!hasBlockInInventory(targetBlock, player)) return false;
                    ItemStack s = findBlockInInventory(targetBlock, player);
                    if (!s.isEmpty()) s.shrink(1);
                }

                // 1. Place the rotated BlockState directly (like Create's Schematic placement)
                level.setBlock(pos, targetState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

                // 2. Restore & Rotate BlockEntity NBT (Copycats, Copycats+, Basins, Belts, Supervisors)
                if (placement.blockEntityNbt != null && !placement.blockEntityNbt.isEmpty()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        CompoundTag fullNbt = placement.blockEntityNbt.copy();
                        fullNbt.putInt("x", pos.getX());
                        fullNbt.putInt("y", pos.getY());
                        fullNbt.putInt("z", pos.getZ());

                        // Load NBT components (fills Copycat Material & ItemStack, Basin filters, etc.)
                        be.loadWithComponents(fullNbt, level.registryAccess());
                        // Apply Create's StructureTransform to rotate internal BlockEntity data
                        applyCreateTransformToBlockEntity(createTransform, be);
                        be.setChanged();
                        level.sendBlockUpdated(pos, targetState, targetState, Block.UPDATE_ALL);
                    }
                }
                return true;
            } catch (Exception e) {
                SatisfactoryBuildTool.LOGGER.error("Error placing schematic block at {}: {}", pos, e.getMessage());
                return false;
            }
        }

        private void finishBuild() {
            completed = true;

            // Final pass: notify neighbors and sync BlockEntities so Create shafts/belts & Copycat textures connect
            for (BlockPos pos : placedPositions) {
                BlockState state = level.getBlockState(pos);
                level.updateNeighborsAt(pos, state.getBlock());
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                }
            }

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

            // Final lock-in audio & particle burst
            BlockPos centerPos = minPos.offset(
                    (maxPos.getX() - minPos.getX()) / 2,
                    (maxPos.getY() - minPos.getY()) / 2,
                    (maxPos.getZ() - minPos.getZ()) / 2
            );
            level.playSound(null, centerPos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 0.8f, 1.35f);
            level.sendParticles(ParticleTypes.END_ROD,
                    centerPos.getX() + 0.5, maxPos.getY() + 0.5, centerPos.getZ() + 0.5,
                    12,
                    (maxPos.getX() - minPos.getX()) * 0.3,
                    0.3,
                    (maxPos.getZ() - minPos.getZ()) * 0.3,
                    0.03
            );

            PlacedStructure placed = new PlacedStructure(UUID.randomUUID(), structure.getName(), minPos, maxPos, startPos, rotationY, level.dimension());
            ServerStructureTracker.get(level).registerStructure(level, placed, placedPositions);

            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof BuildTool) {
                PatternDecalRenderer.registerStructureDecal(
                        minPos, maxPos, direction, BuildTool.getPattern(held), BuildTool.getColor(held)
                );
            }
        }

        public boolean isCompleted() { return completed; }
    }

    private static void spawnConstructionBeam(ServerPlayer player, ServerLevel level, Vec3 target) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize().scale(0.35);
        Vec3 start = player.getEyePosition().add(look.scale(0.6)).add(right).subtract(0, 0.2, 0);

        Vec3 diff = target.subtract(start);
        double dist = diff.length();
        if (dist > 48.0 || dist < 0.5) return;

        int steps = Math.min(12, Math.max(3, (int) (dist * 1.5)));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 pt = start.add(diff.scale(t));
            level.sendParticles(i % 2 == 0 ? FICSIT_ORANGE_PARTICLE : FICSIT_CYAN_PARTICLE,
                    pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public static Rotation getRotationFromDirection(Direction dir) {
        return switch (dir) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static BlockPos getRotatedBlockPos(BlockPos startPos, int x, int y, int z, int width, int length, Direction direction) {
        int rotatedX = x, rotatedZ = z;
        switch (direction) {
            case EAST:
                rotatedX = length - 1 - z;
                rotatedZ = x;
                break;
            case SOUTH:
                rotatedX = width - 1 - x;
                rotatedZ = length - 1 - z;
                break;
            case WEST:
                rotatedX = z;
                rotatedZ = width - 1 - x;
                break;
            default:
                break;
        }
        return startPos.offset(rotatedX, y, rotatedZ);
    }

    private static Direction getDirectionFromYaw(float yaw) {
        float norm = (yaw + 360) % 360;
        if (norm >= 315 || norm < 45) return Direction.SOUTH;
        if (norm >= 45 && norm < 135) return Direction.WEST;
        if (norm >= 135 && norm < 225) return Direction.NORTH;
        return Direction.EAST;
    }

    private static boolean playerHasItem(ServerPlayer player, String itemId, int count) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
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
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
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

    private static ItemStack findBlockInInventory(Block block, ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (Block.byItem(stack.getItem()) == block) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasBlockInInventory(Block block, ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (Block.byItem(stack.getItem()) == block && !stack.isEmpty()) return true;
        }
        return false;
    }

    private static boolean checkAreaClear(StructureLoader.StructureData structure, BlockPos startPos, float rotationY,
                                          ServerLevel level, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        String selectedMat = held.getItem() instanceof BuildTool ? BuildTool.getMaterial(held) : "";
        List<List<List<String>>> activePattern = structure.getPatternForMaterial(selectedMat);
        if (activePattern == null || activePattern.isEmpty() || activePattern.get(0).isEmpty()) return false;

        int height = activePattern.size();
        int length = activePattern.get(0).size();
        int width = activePattern.get(0).get(0).size();
        Direction direction = getDirectionFromYaw(rotationY);

        for (int y = 0; y < height; y++) {
            List<List<String>> layer = activePattern.get(y);
            for (int z = 0; z < length && z < layer.size(); z++) {
                List<String> row = layer.get(z);
                for (int x = 0; x < width && x < row.size(); x++) {
                    String blockString = row.get(x);
                    if (blockString != null && !blockString.equals(" ") && !blockString.isEmpty()) {
                        BlockPos blockPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                        if (ServerStructureTracker.get(level).isProtectedBlock(blockPos)) {
                            return false;
                        }
                        BlockState state = level.getBlockState(blockPos);
                        if (!state.isAir() && !state.canBeReplaced()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static class BlockPlacement {
        public final BlockState blockState;
        public final BlockPos pos;
        @Nullable
        public final CompoundTag blockEntityNbt;
        public final double radialDist;

        public BlockPlacement(BlockState state, BlockPos pos, @Nullable CompoundTag blockEntityNbt, double radialDist) {
            this.blockState = state;
            this.pos = pos;
            this.blockEntityNbt = blockEntityNbt;
            this.radialDist = radialDist;
        }
    }
}