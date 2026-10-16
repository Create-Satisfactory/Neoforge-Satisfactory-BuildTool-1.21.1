package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.events.BuildToolProtectionEvents;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT)
public class StructurePreview {
    private static int rotationOffset = 0;
    private static boolean isFixed = false;
    private static BlockPos fixedPos = null;
    private static Direction zoopDirection = null;
    private static int zoopCount = 1;
    private static boolean isZoopEnabled = false;

    private static int getMaxZoop() { return Config.max_zoop_count; }

    private static StructureLoader.StructureData currentPreviewStructure = null;
    private static BlockPos previewPosition = null;
    private static Direction previewDirection = Direction.NORTH;

    private static long previewStartTime = 0;
    private static long getPreviewDuration() { return Config.preview_duration_ms; }

    // Live placement status for BuildToolOverlay
    private static boolean currentPlacementObstructed = false;
    private static boolean currentPlacementMissingItems = false;

    private static class ActiveBuildAnimation {
        final StructureLoader.StructureData structure;
        final BlockPos startPos;
        final Direction direction;
        final long startTimeMs;
        final long durationMs;

        ActiveBuildAnimation(StructureLoader.StructureData structure, BlockPos startPos, Direction direction, long durationMs) {
            this.structure = structure;
            this.startPos = startPos;
            this.direction = direction;
            this.startTimeMs = System.currentTimeMillis();
            this.durationMs = durationMs;
        }
    }

    private static final List<ActiveBuildAnimation> ACTIVE_ANIMATIONS = new ArrayList<>();

    public static void startBuildAnimation(StructureLoader.StructureData structure, BlockPos pos, Direction dir) {
        int vol = Math.max(1, structure.getWidth() * structure.getHeight() * structure.getLength());
        int targetTicks = Math.clamp(16 + (int) Math.sqrt(vol) * 3, 20, 48);
        ACTIVE_ANIMATIONS.add(new ActiveBuildAnimation(structure, pos.immutable(), dir, targetTicks * 50L));
    }

    public static boolean isCurrentPlacementObstructed() {
        return currentPlacementObstructed;
    }

    public static boolean isCurrentPlacementMissingItems() {
        return currentPlacementMissingItems;
    }

    public static StructureLoader.StructureData getCurrentPreviewStructure() {
        return currentPreviewStructure;
    }

    public static void showPreview(StructureLoader.StructureData structure, BlockPos position, Direction direction) {
        if (currentPreviewStructure != structure) {
            rotationOffset = 0;
            isFixed = false;
            fixedPos = null;
            zoopDirection = null;
            zoopCount = 1;
            PreviewRenderer.clearCaches();
        }
        currentPreviewStructure = structure;
        previewPosition = position;
        previewDirection = direction;
        previewStartTime = System.currentTimeMillis();
    }

    public static void hidePreview() {
        currentPreviewStructure = null;
        previewPosition = null;
        previewDirection = Direction.NORTH;
        currentPlacementObstructed = false;
        currentPlacementMissingItems = false;
    }

    public static Direction getPreviewDirection() { return previewDirection; }
    public static BlockPos getPreviewPosition() { return previewPosition; }
    public static Direction getZoopDirection() { return zoopDirection; }
    public static int getZoopCount() { return zoopCount; }

    public static void updatePreviewPosition(Player player) {
        if (currentPreviewStructure != null) {
            if (!isFixed) {
                previewPosition = StructureGeometryHelper.getPreviewPositionFromPlayer(player);
                Direction baseDirection = StructureGeometryHelper.getDirectionFromYaw(player.getYRot());
                previewDirection = StructureGeometryHelper.applyRotationOffset(baseDirection, rotationOffset);
            } else {
                if (isZoopEnabled) {
                    updateZoop(player);
                } else {
                    zoopCount = 1;
                    zoopDirection = null;
                }
            }
            previewStartTime = System.currentTimeMillis();
        }
    }

    public static void rotatePreview() {
        rotationOffset = (rotationOffset + 1) % 4;
        if (Minecraft.getInstance().player != null) {
            if (isFixed) {
                Direction baseDirection = StructureGeometryHelper.getDirectionFromYaw(Minecraft.getInstance().player.getYRot());
                previewDirection = StructureGeometryHelper.applyRotationOffset(baseDirection, rotationOffset);
            } else {
                updatePreviewPosition(Minecraft.getInstance().player);
            }
        }
    }

    public static void toggleZoop() {
        if (!isFixed) return;
        isZoopEnabled = !isZoopEnabled;
        if (!isZoopEnabled) {
            zoopCount = 1;
            zoopDirection = null;
        }
    }

    public static boolean isZoopEnabled() { return isZoopEnabled; }

    public static void toggleFixed() {
        isFixed = !isFixed;
        if (isFixed) {
            fixedPos = previewPosition;
        } else {
            fixedPos = null;
            zoopCount = 1;
            zoopDirection = null;
            isZoopEnabled = false;
        }
    }

    public static boolean isFixed() { return isFixed; }

    public static void movePreview(Direction direction) {
        if (isFixed && fixedPos != null) {
            fixedPos = fixedPos.relative(direction);
            previewPosition = fixedPos;
            previewStartTime = System.currentTimeMillis();
        }
    }

    private static void updateZoop(Player player) {
        if (fixedPos == null) return;
        Vec3 lookVec = player.getLookAngle();

        double maxAlignment = -2.0;
        Direction bestDir = null;
        for (Direction dir : Direction.values()) {
            Vec3 dirVec = Vec3.atLowerCornerOf(dir.getNormal());
            double alignment = lookVec.dot(dirVec);
            if (alignment > maxAlignment) {
                maxAlignment = alignment;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            zoopDirection = bestDir;
            HitResult hit = player.pick(100.0D, 0.0F, false);
            BlockPos targetPos = (hit.getType() == HitResult.Type.BLOCK)
                    ? ((BlockHitResult) hit).getBlockPos()
                    : BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(20)));

            BlockPos diff = targetPos.subtract(fixedPos);
            int dist = switch (zoopDirection.getAxis()) {
                case X -> diff.getX() * zoopDirection.getAxisDirection().getStep();
                case Y -> diff.getY() * zoopDirection.getAxisDirection().getStep();
                case Z -> diff.getZ() * zoopDirection.getAxisDirection().getStep();
            };

            int sizeInZoopDir = getStructureSizeInDirection(currentPreviewStructure, previewDirection, zoopDirection);
            zoopCount = Math.min(getMaxZoop(), Math.max(1, dist / Math.max(1, sizeInZoopDir) + 1));
        }
    }

    public static int getStructureSizeInDirection(StructureLoader.StructureData structure, Direction structRotation, Direction zoopDir) {
        int rotatedW = getRotatedWidth(structure.getWidth(), structure.getLength(), structRotation);
        int rotatedL = getRotatedLength(structure.getWidth(), structure.getLength(), structRotation);
        return switch (zoopDir.getAxis()) {
            case X -> rotatedW;
            case Z -> rotatedL;
            case Y -> structure.getHeight();
        };
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        renderActiveBuildAnimations(event.getPoseStack(), event.getCamera());

        if ((BuildTool.isSelectingArea(player) || BuildTool.getPoint1(player) != null) &&
                (player.getMainHandItem().getItem() instanceof BuildTool ||
                        player.getOffhandItem().getItem() instanceof BuildTool)) {

            float r = (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) ? 0.0f : 0.0f;
            float g = (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) ? 0.5f : 1.0f;
            float b = (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) ? 1.0f : 0.0f;
            renderSelectionBox(event.getPoseStack(), event.getCamera(), player, r, g, b);

            if (!(player.getMainHandItem().getItem() instanceof BuildTool)) {
                return;
            }
        }

        if (!(player.getMainHandItem().getItem() instanceof BuildTool)) {
            return;
        }

        if (BuildTool.isDestructionMode()) {
            renderDestructionBoxes(event.getPoseStack(), event.getCamera(), player);
            return;
        }

        if (BuildTool.getMode() == BuildTool.BuildMode.NONE || BuildTool.getMode() == BuildTool.BuildMode.CLEAR) {
            return;
        }

        if (currentPreviewStructure == null || previewPosition == null) {
            return;
        }

        if (System.currentTimeMillis() - previewStartTime > getPreviewDuration()) {
            hidePreview();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        currentPlacementObstructed = false;
        currentPlacementMissingItems = !canPlayerAfford(player, currentPreviewStructure, Math.max(1, zoopCount));

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());

        if (zoopCount > 1 && zoopDirection != null) {
            int sizeInDir = getStructureSizeInDirection(currentPreviewStructure, previewDirection, zoopDirection);
            for (int i = 0; i < zoopCount; i++) {
                BlockPos offsetPos = previewPosition.relative(zoopDirection, i * sizeInDir);
                renderStructurePreview(poseStack, bufferSource, offsetPos, currentPreviewStructure, previewDirection);
            }
        } else {
            renderStructurePreview(poseStack, bufferSource, previewPosition, currentPreviewStructure, previewDirection);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.translucent());
        bufferSource.endBatch(RenderType.lines());
    }

    private static boolean canPlayerAfford(Player player, StructureLoader.StructureData structure, int multiplier) {
        if (player.isCreative()) return true;
        List<StructureLoader.RequiredItem> costs = structure.isOnlyItems()
                ? structure.getRequiredItems()
                : structure.getAggregatedCosts();

        for (StructureLoader.RequiredItem req : costs) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.getItemId()));
            if (item == null || item == Items.AIR) continue;
            int needed = req.getCount() * multiplier;
            int found = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() == item) found += s.getCount();
            }
            if (found < needed) return false;
        }
        return true;
    }

    private static void renderStructurePreview(PoseStack poseStack, MultiBufferSource bufferSource,
                                               BlockPos startPos, StructureLoader.StructureData structure,
                                               Direction direction) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack heldItem = player.getMainHandItem();
        String selectedMaterial = (heldItem.getItem() instanceof BuildTool) ? BuildTool.getMaterial(heldItem) : "";

        List<List<List<String>>> activePattern = structure.getPatternForMaterial(selectedMaterial);
        boolean usingMaterialSchematic = structure.hasMaterialSchematic(selectedMaterial);

        if (activePattern == null || activePattern.isEmpty() || activePattern.get(0).isEmpty()) return;
        int height = activePattern.size();
        int length = activePattern.get(0).size();
        int width = activePattern.get(0).get(0).size();

        // 1. Pre-check if any block in this placement is obstructed by terrain or a protected structure
        List<BlockPos> obstructedPositions = new ArrayList<>();
        if (!Config.allow_obstruction_clearing) {
            for (int y = 0; y < height; y++) {
                List<List<String>> layer = activePattern.get(y);
                for (int z = 0; z < length && z < layer.size(); z++) {
                    List<String> row = layer.get(z);
                    for (int x = 0; x < width && x < row.size(); x++) {
                        String raw = row.get(x);
                        if (raw != null && !raw.equals(" ") && !raw.isEmpty()) {
                            BlockPos worldPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                            BlockState existing = player.level().getBlockState(worldPos);
                            if (BuildToolProtectionEvents.isBlockProtected(player.level(), worldPos) ||
                                    (!existing.isAir() && !existing.canBeReplaced())) {
                                obstructedPositions.add(worldPos);
                            }
                        }
                    }
                }
            }
        }

        if (!obstructedPositions.isEmpty()) {
            currentPlacementObstructed = true;
        }

        boolean isInvalid = currentPlacementObstructed || currentPlacementMissingItems;

        // 2. Choose Hologram Tint Color (Red-Orange when invalid, Cyan-Blue when valid)
        float tintR = isInvalid ? 1.00f : (isFixed ? 0.35f : 0.45f);
        float tintG = isInvalid ? 0.26f : (isFixed ? 0.90f : 0.82f);
        float tintB = isInvalid ? 0.20f : 1.00f;
        float tintA = isInvalid ? 0.68f : 0.65f;

        Vec3 playerPos = player.position();
        Vec3 playerLook = player.getLookAngle();

        Block templateBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(Config.template_block));
        boolean isOfficial = structure.isServerStructure();

        Block selectedColorBlock = null;
        Block selectedMaterialBlock = null;

        if (isOfficial && heldItem.getItem() instanceof BuildTool) {
            int colorIndex = BuildTool.getColor(heldItem);
            if (colorIndex >= 0 && colorIndex < Config.color_blocks.size()) {
                selectedColorBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(Config.color_blocks.get(colorIndex)));
            }

            if (!usingMaterialSchematic && selectedMaterial != null && !selectedMaterial.isEmpty()) {
                Config.MaterialEntry materialEntry = Config.material_blocks.stream()
                        .filter(m -> m.name.equals(selectedMaterial))
                        .findFirst().orElse(null);
                if (materialEntry != null) {
                    selectedMaterialBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(materialEntry.blockId));
                }
            }
        }

        Rotation rotation = PlaceStructure.getRotationFromDirection(direction);
        Object createTransform = PlaceStructure.createStructureTransform(rotation);

        for (int y = 0; y < height; y++) {
            List<List<String>> layer = activePattern.get(y);
            for (int z = 0; z < length && z < layer.size(); z++) {
                List<String> row = layer.get(z);
                for (int x = 0; x < width && x < row.size(); x++) {
                    String rawBlockString = row.get(x);
                    if (rawBlockString == null || rawBlockString.equals(" ") || rawBlockString.isEmpty()) continue;

                    BlockPos rotatedPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);
                    if (rotatedPos.distToCenterSqr(playerPos) > 4096 * 2) continue;

                    Vec3 toBlockNorm = Vec3.atCenterOf(rotatedPos).subtract(player.getEyePosition()).normalize();
                    if (playerLook.dot(toBlockNorm) < -0.2) continue;

                    Direction[] visibleFaces = getVisibleFaces(structure, activePattern, x, y, z, direction, toBlockNorm);
                    if (visibleFaces.length == 0) continue;

                    BlockState blockState = structure.getBlockStateForString(rawBlockString);
                    if (blockState != null) {
                        if (isOfficial) {
                            if (selectedMaterialBlock != null && blockState.getBlock().equals(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(Config.base_material)))) {
                                blockState = selectedMaterialBlock.defaultBlockState();
                                if (selectedColorBlock != null && blockState.getBlock().getDescriptionId().contains("_concrete") &&
                                        blockState.getBlock().getDescriptionId().contains("gray")) {
                                    blockState = selectedColorBlock.defaultBlockState();
                                }
                            } else if (selectedColorBlock != null && blockState.getBlock().equals(templateBlock)) {
                                blockState = selectedColorBlock.defaultBlockState();
                            }
                        }

                        // Rotate BlockState using Create's StructureTransform so slopes, shafts, and copycats orient properly
                        BlockState rotatedState = PlaceStructure.applyCreateTransform(createTransform, blockState, rotation, direction);
                        PreviewRenderer.renderPreviewBlock(
                                rotatedState, rawBlockString, direction, rotatedPos,
                                poseStack, bufferSource,
                                tintR, tintG, tintB, tintA, visibleFaces
                        );
                    }
                }
            }
        }

        // Highlight obstructed blocks in red wireframe
        for (BlockPos badPos : obstructedPositions) {
            PreviewRenderer.renderInvalidPosition(poseStack, bufferSource, badPos, 0.85f);
        }

        renderStructureBoundingBox(poseStack, bufferSource, startPos, width, height, length, direction, isInvalid);
    }

    private static void renderActiveBuildAnimations(PoseStack poseStack, Camera camera) {
        if (ACTIVE_ANIMATIONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        ACTIVE_ANIMATIONS.removeIf(anim -> (now - anim.startTimeMs) > anim.durationMs);
        if (ACTIVE_ANIMATIONS.isEmpty()) return;

        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x(), -cam.y(), -cam.z());

        for (ActiveBuildAnimation anim : ACTIVE_ANIMATIONS) {
            float progress = Math.clamp((float) (now - anim.startTimeMs) / anim.durationMs, 0.0f, 1.0f);
            int rotW = getRotatedWidth(anim.structure.getWidth(), anim.structure.getLength(), anim.direction);
            int rotL = getRotatedLength(anim.structure.getWidth(), anim.structure.getLength(), anim.direction);
            int h = anim.structure.getHeight();

            double minX = anim.startPos.getX();
            double minY = anim.startPos.getY();
            double minZ = anim.startPos.getZ();
            double maxX = minX + rotW;
            double maxY = minY + h;
            double maxZ = minZ + rotL;

            LevelRenderer.renderLineBox(poseStack, lines, minX, minY, minZ, maxX, maxY, maxZ,
                    0.98f, 0.58f, 0.27f, 0.85f);

            double scanY = minY + (h * progress);
            LevelRenderer.renderLineBox(poseStack, lines,
                    minX - 0.05, scanY - 0.04, minZ - 0.05,
                    maxX + 0.05, scanY + 0.04, maxZ + 0.05,
                    1.0f, 0.85f, 0.35f, 1.0f);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static Direction[] getVisibleFaces(StructureLoader.StructureData structure, List<List<List<String>>> activePattern,
                                               int x, int y, int z, Direction structureRotation, Vec3 toBlockNorm) {
        List<Direction> visible = new ArrayList<>();
        int rotIndex = directionToRotationIndex(structureRotation);

        for (Direction worldDir : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(worldDir.getNormal());
            if (normal.dot(toBlockNorm) > 0.35) continue;

            Direction patternDir = worldDir;
            if (worldDir.getAxis().isHorizontal()) {
                int worldIdx = horizontalDirectionToIndex(worldDir);
                int patternIdx = (worldIdx - rotIndex + 4) % 4;
                patternDir = indexToHorizontalDirection(patternIdx);
            }

            int nx = x + patternDir.getStepX();
            int ny = y + patternDir.getStepY();
            int nz = z + patternDir.getStepZ();

            if (isAirOrTransparent(structure, activePattern, nx, ny, nz)) {
                visible.add(worldDir);
            }
        }
        return visible.toArray(new Direction[0]);
    }

    private static int horizontalDirectionToIndex(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private static Direction indexToHorizontalDirection(int index) {
        return switch (index % 4) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static boolean isAirOrTransparent(StructureLoader.StructureData structure, List<List<List<String>>> pattern, int x, int y, int z) {
        if (pattern == null || y < 0 || y >= pattern.size()) return true;
        List<List<String>> layer = pattern.get(y);
        if (z < 0 || z >= layer.size()) return true;
        List<String> row = layer.get(z);
        if (x < 0 || x >= row.size()) return true;

        String blockString = row.get(x);
        if (blockString == null || blockString.equals(" ") || blockString.isEmpty()) return true;

        BlockState state = structure.getBlockStateForString(blockString);
        return state == null || !state.canOcclude();
    }

    public static float getYawFromDirection(Direction direction) {
        return StructureGeometryHelper.getYawFromDirection(direction);
    }

    private static int directionToRotationIndex(Direction direction) {
        return switch (direction) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private static BlockPos getRotatedBlockPos(BlockPos startPos, int x, int y, int z,
                                               int width, int length, Direction direction) {
        return StructureGeometryHelper.getRotatedBlockPos(startPos, x, y, z, width, length, direction);
    }

    private static int getRotatedWidth(int width, int length, Direction direction) {
        return StructureGeometryHelper.getRotatedWidth(width, length, direction);
    }

    private static int getRotatedLength(int width, int length, Direction direction) {
        return StructureGeometryHelper.getRotatedLength(width, length, direction);
    }

    private static void renderDestructionBoxes(PoseStack poseStack, Camera camera, Player player) {
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        BlockPos playerPos = player.blockPosition();

        List<PlacedStructure> structures = ClientStructureTracker.getFormattedStructures(player.level());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());

        for (PlacedStructure structure : structures) {
            if (structure.minPos().distSqr(playerPos) > 10000 && structure.maxPos().distSqr(playerPos) > 10000) {
                continue;
            }
            LevelRenderer.renderLineBox(
                    poseStack, lineConsumer,
                    structure.minPos().getX(), structure.minPos().getY(), structure.minPos().getZ(),
                    structure.maxPos().getX() + 1, structure.maxPos().getY() + 1, structure.maxPos().getZ() + 1,
                    1.0f, 0.15f, 0.15f, 0.85f
            );
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderSelectionBox(PoseStack poseStack, Camera camera, Player player, float r, float g, float b) {
        BlockPos point1 = BuildTool.getPoint1(player);
        if (point1 == null) return;

        BlockPos point2;
        if (BuildTool.isSelectingArea(player)) {
            HitResult hitResult = player.pick(100.0D, 0.0F, false);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                point2 = ((BlockHitResult) hitResult).getBlockPos();
            } else {
                Vec3 targetPos = player.getEyePosition().add(player.getLookAngle().scale(100.0D));
                point2 = BlockPos.containing(targetPos);
            }
        } else {
            point2 = BuildTool.getPoint2(player);
        }
        if (point2 == null) return;

        int minX = Math.min(point1.getX(), point2.getX());
        int minY = Math.min(point1.getY(), point2.getY());
        int minZ = Math.min(point1.getZ(), point2.getZ());
        int maxX = Math.max(point1.getX(), point2.getX());
        int maxY = Math.max(point1.getY(), point2.getY());
        int maxZ = Math.max(point1.getZ(), point2.getZ());

        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
        LevelRenderer.renderLineBox(poseStack, lineConsumer, minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1, r, g, b, 0.9f);
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderStructureBoundingBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                                   BlockPos startPos, int width, int height, int length,
                                                   Direction direction, boolean isInvalid) {
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        int rotatedWidth = getRotatedWidth(width, length, direction);
        int rotatedLength = getRotatedLength(width, length, direction);

        float r = isInvalid ? 1.0f : (isFixed ? 0.98f : 0.85f);
        float g = isInvalid ? 0.25f : (isFixed ? 0.58f : 0.92f);
        float b = isInvalid ? 0.20f : (isFixed ? 0.27f : 1.0f);

        LevelRenderer.renderLineBox(
                poseStack, lineConsumer,
                startPos.getX(), startPos.getY(), startPos.getZ(),
                startPos.getX() + rotatedWidth, startPos.getY() + height, startPos.getZ() + rotatedLength,
                r, g, b, 0.65f
        );
    }

    public static void updateFromCreativeOrb(Player player) {
        StructureLoader.StructureData selectedStructure = BuildTool.getSelectedStructure();
        if (selectedStructure != null) {
            updatePreviewPosition(player);
            BlockPos previewPos = getPreviewPositionFromPlayer(player);
            Direction direction = StructureGeometryHelper.getDirectionFromYaw(player.getYRot());
            showPreview(selectedStructure, previewPos, direction);
        }
    }

    public static BlockPos getPreviewPositionFromPlayer(Player player) {
        return StructureGeometryHelper.getPreviewPositionFromPlayer(player);
    }
}