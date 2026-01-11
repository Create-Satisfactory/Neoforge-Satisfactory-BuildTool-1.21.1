package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.Camera;

import java.util.ArrayList;
import java.util.List;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.KeyBindings;

@EventBusSubscriber(value = Dist.CLIENT)
public class StructurePreview {
    private static int rotationOffset = 0; // 0, 1, 2, 3 (0, 90, 180, 270)
    private static boolean isFixed = false;
    private static BlockPos fixedPos = null;
    private static Direction zoopDirection = null;
    private static int zoopCount = 1;
    private static int getMaxZoop() { return Config.max_zoop_count; }

    private static StructureLoader.StructureData currentPreviewStructure = null;
    private static BlockPos previewPosition = null;
    private static Direction previewDirection = Direction.NORTH;
    
    private static long previewStartTime = 0;
    private static long getPreviewDuration() { return Config.preview_duration_ms; }

    public static StructureLoader.StructureData getCurrentPreviewStructure() {
        return currentPreviewStructure;
    }

    /**
     * Ativa a visualização da estrutura na posição especificada
     */
    public static void showPreview(StructureLoader.StructureData structure, BlockPos position, Direction direction) {
        if (currentPreviewStructure != structure) {
            rotationOffset = 0;
            isFixed = false;
            fixedPos = null;
            zoopDirection = null;
            zoopCount = 1;
        }
        currentPreviewStructure = structure;
        previewPosition = position;
        previewDirection = direction;
        previewStartTime = System.currentTimeMillis();
    }

    /**
     * Desativa a visualização da estrutura
     */
    public static void hidePreview() {
        currentPreviewStructure = null;
        previewPosition = null;
        previewDirection = Direction.NORTH;
    }

    /**
     * Obtém a direção atual do preview
     */
    public static Direction getPreviewDirection() {
        return previewDirection;
    }

    public static BlockPos getPreviewPosition() {
        return previewPosition;
    }

    public static Direction getZoopDirection() {
        return zoopDirection;
    }

    public static int getZoopCount() {
        return zoopCount;
    }

    /**
     * Atualiza a posição e direção da visualização baseada no olhar do jogador
     */
    public static void updatePreviewPosition(Player player) {
        if (currentPreviewStructure != null) {
            if (!isFixed) {
                // Usa o helper para calcular posição e direção
                previewPosition = StructureGeometryHelper.getPreviewPositionFromPlayer(player);
                
                // Determina a direção baseada no yaw do jogador
                Direction baseDirection = StructureGeometryHelper.getDirectionFromYaw(player.getYRot());
                previewDirection = StructureGeometryHelper.applyRotationOffset(baseDirection, rotationOffset);
            } else {
                // Fixed position, update zoop if toggle is on
                if (isZoopEnabled) {
                    updateZoop(player);
                } else {
                    zoopCount = 1;
                    zoopDirection = null;
                }
            }
            // Refresh timer while player is actively using the build tool
            previewStartTime = System.currentTimeMillis();
        }
    }

    private static Direction applyRotationOffset(Direction base, int offset) {
        return StructureGeometryHelper.applyRotationOffset(base, offset);
    }

    private static Direction getDirectionFromYaw(float yaw) {
        return StructureGeometryHelper.getDirectionFromYaw(yaw);
    }

    public static void rotatePreview() {
        rotationOffset = (rotationOffset + 1) % 4;
        if (Minecraft.getInstance().player != null) {
            updatePreviewPosition(Minecraft.getInstance().player);
        }
    }

    private static boolean isZoopEnabled = false;

    public static void toggleZoop() {
        if (!isFixed) return; // Only toggle if fixed mode is active
        isZoopEnabled = !isZoopEnabled;
        if (!isZoopEnabled) {
            zoopCount = 1;
            zoopDirection = null;
        }
    }

    public static boolean isZoopEnabled() {
        return isZoopEnabled;
    }

    public static void toggleFixed() {
        isFixed = !isFixed;
        if (isFixed) {
            fixedPos = previewPosition;
        } else {
            fixedPos = null;
            zoopCount = 1;
            zoopDirection = null;
            isZoopEnabled = false; // Disable zoop when unfixing
        }
    }

    public static boolean isFixed() {
        return isFixed;
    }

    public static void movePreview(Direction direction) {
        if (isFixed && fixedPos != null) {
            fixedPos = fixedPos.relative(direction);
            previewPosition = fixedPos;
            previewStartTime = System.currentTimeMillis();
        }
    }

    private static void updateZoop(Player player) {
        if (fixedPos == null) return;

        // Determine zoop direction based on player look angle relative to fixedPos
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        
        // Find the direction most aligned with the look vector
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
            
            // Calculate distance in that direction
            HitResult hit = player.pick(100.0D, 0.0F, false);
            BlockPos targetPos;
            if (hit.getType() == HitResult.Type.BLOCK) {
                targetPos = ((BlockHitResult)hit).getBlockPos();
            } else {
                targetPos = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(20)));
            }

            // Project vector from fixedPos to targetPos onto zoopDirection
            BlockPos diff = targetPos.subtract(fixedPos);
            int dist = 0;
            switch (zoopDirection.getAxis()) {
                case X -> dist = diff.getX() * zoopDirection.getAxisDirection().getStep();
                case Y -> dist = diff.getY() * zoopDirection.getAxisDirection().getStep();
                case Z -> dist = diff.getZ() * zoopDirection.getAxisDirection().getStep();
            }

            // Divide distance by structure size in that direction
            int sizeInZoopDir = getStructureSizeInDirection(currentPreviewStructure, previewDirection, zoopDirection);
            zoopCount = Math.min(getMaxZoop(), Math.max(1, dist / sizeInZoopDir + 1));
        }
    }

    public static int getStructureSizeInDirection(StructureLoader.StructureData structure, Direction structRotation, Direction zoopDir) {
        int w = structure.getWidth();
        int l = structure.getLength();
        int h = structure.getHeight();
        
        // Rotate dimensions
        int rotatedW = getRotatedWidth(w, l, structRotation);
        int rotatedL = getRotatedLength(w, l, structRotation);
        
        return switch (zoopDir.getAxis()) {
            case X -> rotatedW;
            case Z -> rotatedL;
            case Y -> h;
        };
    }


    /**
     * Renderiza a visualização da estrutura no mundo
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // Render Selection Box (when selecting area) - works with off-hand
        if ((BuildTool.isSelectingArea(player) || BuildTool.getPoint1(player) != null) &&
            (player.getMainHandItem().getItem() instanceof BuildTool || 
             player.getOffhandItem().getItem() instanceof BuildTool)) {
            
            float r = 0.0f;
            float g = 1.0f;
            float b = 0.0f;
            
            if (BuildTool.getMode() == BuildTool.BuildMode.CLEAR) {
                r = 0.0f;
                g = 0.5f;
                b = 1.0f; // Blue/Cyan
            }
            
            renderSelectionBox(event.getPoseStack(), event.getCamera(), player, r, g, b);
            
            // If tool is only in off-hand, return here (selection mode only)
            if (!(player.getMainHandItem().getItem() instanceof BuildTool)) {
                return;
            }
        }
        
        // Ensure BuildTool is in Main Hand for other modes
        if (!(player.getMainHandItem().getItem() instanceof BuildTool)) {
            return;
        }

        // Render Destruction Mode Boxes
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

        // Verifica se o tempo de preview expirou
        if (System.currentTimeMillis() - previewStartTime > getPreviewDuration()) {
            hidePreview();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());

        if (zoopCount > 1 && zoopDirection != null) {
            for (int i = 0; i < zoopCount; i++) {
                int sizeInDir = getStructureSizeInDirection(currentPreviewStructure, previewDirection, zoopDirection);
                BlockPos offsetPos = previewPosition.relative(zoopDirection, i * sizeInDir);
                renderStructurePreview(poseStack, bufferSource, offsetPos, currentPreviewStructure, previewDirection);
            }
        } else {
            renderStructurePreview(poseStack, bufferSource, previewPosition, currentPreviewStructure, previewDirection);
        }

        poseStack.popPose();
    }

    /**
     * Renderiza a estrutura na posição especificada com rotação
     */
    private static void renderStructurePreview(PoseStack poseStack, MultiBufferSource bufferSource,
                                               BlockPos startPos, StructureLoader.StructureData structure,
                                               Direction direction) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int width = structure.getWidth();
        int length = structure.getLength();
        int height = structure.getHeight();

        // DEBUG: Renderiza um marcador na posição inicial
        renderDebugMarker(poseStack, bufferSource, startPos);

        Vec3 playerPos = player.position();
        Vec3 playerLook = player.getLookAngle();

        // Resolve Template Block and Selected Color Block
        Block templateBlock = BuiltInRegistries.BLOCK.get(
                ResourceLocation.parse(Config.template_block));
        
        // Check if this is an official structure (color/material allowed)
        boolean isOfficial = structure.isServerStructure();
        
        Block selectedColorBlock = null;
        Block selectedMaterialBlock = null;
        ItemStack heldItem = player.getMainHandItem();
        
        if (isOfficial && heldItem.getItem() instanceof BuildTool) {
            // Resolve color block
            int colorIndex = BuildTool.getColor(heldItem);
            if (colorIndex >= 0 && colorIndex < Config.color_blocks.size()) {
                String colorBlockName = Config.color_blocks.get(colorIndex);
                selectedColorBlock = BuiltInRegistries.BLOCK.get(
                        ResourceLocation.parse(colorBlockName));
            }
            
            // Resolve material block
            String selectedMaterial = BuildTool.getMaterial(heldItem);
            if (selectedMaterial != null && !selectedMaterial.isEmpty()) {
                Config.MaterialEntry materialEntry = Config.material_blocks.stream()
                    .filter(m -> m.name.equals(selectedMaterial))
                    .findFirst().orElse(null);
                if (materialEntry != null) {
                    selectedMaterialBlock = BuiltInRegistries.BLOCK.get(
                            ResourceLocation.parse(materialEntry.blockId));
                }
            }
        }

        // Renderiza cada camada da estrutura
        for (int y = 0; y < height; y++) {
            if (y >= structure.getPattern().size()) continue;

            List<List<String>> layer = structure.getPattern().get(y);

            for (int z = 0; z < length; z++) {
                if (z >= layer.size()) continue;

                List<String> row = layer.get(z);

                for (int x = 0; x < width; x++) {
                    if (x >= row.size()) continue;

                    String blockString = row.get(x);
                    if (blockString.equals(" ") || blockString.isEmpty()) continue;

                    // Aplica rotação às coordenadas
                    BlockPos rotatedPos = getRotatedBlockPos(startPos, x, y, z, width, length, direction);

                    // 1. Distance Culling (32 blocks radius for full preview)
                    double distSq = rotatedPos.distToCenterSqr(playerPos);
                    if (distSq > 4096*2) continue; // 64 blocks

                    // 2. Direction/Frustum Culling (Simplified: check if in front of player)
                    Vec3 toBlock = Vec3.atCenterOf(rotatedPos).subtract(player.getEyePosition());
                    Vec3 toBlockNorm = toBlock.normalize();
                    double dot = playerLook.dot(toBlockNorm);
                    if (dot < -0.2) continue; // Roughly skip blocks behind player

                    // 3. Occlusion & Dynamic Face Culling
                    Direction[] visibleFaces = getVisibleFaces(structure, x, y, z, direction, toBlockNorm);
                    if (visibleFaces.length == 0) continue; // Occlusion culling: block totally surrounded or faces pointing away

                    BlockState blockState = structure.getBlockStateForString(blockString);
                    if (blockState != null) {
                        // 4. Replacement Logic (only for official structures)
                        if (isOfficial) {
                            // Material Replacement
                            if (selectedMaterialBlock != null && blockState.getBlock().equals(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(Config.base_material)))) {
                                blockState = selectedMaterialBlock.defaultBlockState();
                                
                                // Special handling for concrete: apply color if both are selected
                                if (selectedColorBlock != null && blockState.getBlock().getDescriptionId().contains("_concrete") && 
                                    blockState.getBlock().getDescriptionId().contains("gray")) {
                                    blockState = selectedColorBlock.defaultBlockState();
                                }
                            }
                            // Standard Color Replacement (Template Color)
                            else if (selectedColorBlock != null && blockState.getBlock().equals(templateBlock)) {
                                blockState = selectedColorBlock.defaultBlockState();
                            }
                        }
                        
                        BlockState rotatedState = BlockRotationHelper.rotateBlockState(blockState, direction);
                        PreviewRenderer.renderPreviewBlock(rotatedState, rotatedPos, poseStack, bufferSource, 0.7f, visibleFaces);
                    }
                }
            }
        }

        // Renderiza a bounding box da estrutura completa (com rotação)
        renderStructureBoundingBox(poseStack, bufferSource, startPos, width, height, length, direction);
    }

    /**
     * Determina quais faces de um bloco estão expostas e voltadas para o jogador
     */
    private static Direction[] getVisibleFaces(StructureLoader.StructureData structure, int x, int y, int z, 
                                             Direction structureRotation, Vec3 toBlockNorm) {
        List<Direction> visible = new ArrayList<>();
        int rotIndex = directionToRotationIndex(structureRotation);
        
        for (Direction worldDir : Direction.values()) {
            // 1. Dynamic Face Culling: Only render faces pointing towards the player
            // Dot product of face normal and vector from eye to block
            // If face points towards player, dot product is negative (normal opposite to toBlock)
            Vec3 normal = Vec3.atLowerCornerOf(worldDir.getNormal());
            if (normal.dot(toBlockNorm) > 0.3) {
                // Face is pointing away from player (> 72 degrees)
                continue;
            }

            // 2. Occlusion Culling: Check if neighbor in pattern is air
            Direction patternDir = worldDir;
            if (worldDir.getAxis().isHorizontal()) {
                int worldIdx = horizontalDirectionToIndex(worldDir);
                int patternIdx = (worldIdx - rotIndex + 4) % 4;
                patternDir = indexToHorizontalDirection(patternIdx);
            }
            
            int nx = x + patternDir.getStepX();
            int ny = y + patternDir.getStepY();
            int nz = z + patternDir.getStepZ();
            
            if (isAirOrTransparent(structure, nx, ny, nz)) {
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
        return switch (index) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /**
     * Verifica se uma posição na estrutura é ar ou um bloco transparente
     */
    private static boolean isAirOrTransparent(StructureLoader.StructureData structure, int x, int y, int z) {
        if (y < 0 || y >= structure.getHeight()) return true;
        
        List<List<List<String>>> pattern = structure.getPattern();
        List<List<String>> layer = pattern.get(y);
        
        if (z < 0 || z >= layer.size()) return true;
        List<String> row = layer.get(z);
        
        if (x < 0 || x >= row.size()) return true;
        String blockString = row.get(x);
        
        if (blockString.equals(" ") || blockString.isEmpty()) return true;
        
        // Opcional: Check if the block is actually solid
        BlockState state = structure.getBlockStateForString(blockString);
        return state == null || !state.isSolid();
    }


    public static float getYawFromDirection(Direction direction) {
        return StructureGeometryHelper.getYawFromDirection(direction);
    }

    /**
     * Converte direção para índice de rotação
     */
    private static int directionToRotationIndex(Direction direction) {
        return switch (direction) {
            case EAST -> 1;  // 90 graus
            case SOUTH -> 2; // 180 graus
            case WEST -> 3;  // 270 graus
            default -> 0;    // 0 graus
        };
    }
    /**
     * Calcula a posição rotacionada de um bloco
     */
    private static BlockPos getRotatedBlockPos(BlockPos startPos, int x, int y, int z,
                                               int width, int length, Direction direction) {
        int rotatedX = x;
        int rotatedZ = z;

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
            case NORTH:
            default:
                rotatedX = x;
                rotatedZ = z;
                break;
        }

        return startPos.offset(rotatedX, y, rotatedZ);
    }

    /**
     * Obtém a largura rotacionada
     */
    private static int getRotatedWidth(int width, int length, Direction direction) {
        return (direction == Direction.EAST || direction == Direction.WEST) ? length : width;
    }

    /**
     * Obtém o comprimento rotacionado
     */
    private static int getRotatedLength(int width, int length, Direction direction) {
        return (direction == Direction.EAST || direction == Direction.WEST) ? width : length;
    }

    /**
     * Renderiza um marcador de debug na posição
     */
    private static void renderDebugMarker(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos) {
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        float r = 1.0f; // Vermelho
        float g = 0.0f;
        float b = 0.0f;
        float a = 1.0f;

        // Renderiza uma cruz vermelha na posição
        LevelRenderer.renderLineBox(poseStack, lineConsumer,
                pos.getX() + 0.4, pos.getY() + 0.4, pos.getZ() + 0.4,
                pos.getX() + 0.6, pos.getY() + 0.6, pos.getZ() + 0.6,
                r, g, b, a
        );
    }

    /**
     * Renderiza o bloco de forma translúcida
     */
    private static void renderTranslucentBlock(PoseStack poseStack, MultiBufferSource bufferSource,
                                               BlockRenderDispatcher blockRenderer, BlockPos pos,
                                               BlockState blockState) {
        try {
            // Renderiza o bloco com transparência
            VertexConsumer translucentConsumer = bufferSource.getBuffer(RenderType.translucent());

            BakedModel model = blockRenderer.getBlockModel(blockState);

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

            blockRenderer.getModelRenderer().renderModel(
                    poseStack.last(),
                    translucentConsumer,
                    blockState,
                    model,
                    1.0f, 1.0f, 1.0f,
                    getPackedLight(pos),
                    0,
                    ModelData.EMPTY,
                    RenderType.translucent()
            );

            poseStack.popPose();
        } catch (Exception e) {
            System.err.println("Erro ao renderizar bloco preview: " + e.getMessage());
        }
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
            // Check distance (100 blocks)
            if (structure.minPos().distSqr(playerPos) > 10000 && structure.maxPos().distSqr(playerPos) > 10000) {
                continue;
            }

            // Render Red Box
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineConsumer,
                    structure.minPos().getX(), structure.minPos().getY(), structure.minPos().getZ(),
                    structure.maxPos().getX() + 1, structure.maxPos().getY() + 1, structure.maxPos().getZ() + 1,
                    1.0f, 0.0f, 0.0f, 0.8f // Red with transparency
            );
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    /**
     * Renders a dynamic selection box from point1 to the player's current look position
     */
    private static void renderSelectionBox(PoseStack poseStack, Camera camera, Player player, float r, float g, float b) {
        BlockPos point1 = BuildTool.getPoint1(player);
        BlockPos point2 = BuildTool.getPoint2(player);
        
        if (point1 == null) {
            return;
        }

        if (BuildTool.isSelectingArea(player)) {
            // Get the block the player is currently looking at
            HitResult hitResult = player.pick(100.0D, 0.0F, false);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                point2 = ((BlockHitResult) hitResult).getBlockPos();
            } else {
                // Calculate position at 100 blocks distance
                Vec3 targetPos = player.getEyePosition().add(player.getLookAngle().scale(100.0D));
                point2 = BlockPos.containing(targetPos);
            }
        } else {
            point2 = BuildTool.getPoint2(player);
        }

        if (point2 == null) {
            return;
        }

        // Calculate min and max positions
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

        // Render Selection Box
        LevelRenderer.renderLineBox(
                poseStack,
                lineConsumer,
                minX, minY, minZ,
                maxX + 1, maxY + 1, maxZ + 1,
                r, g, b, 0.9f 
        );

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    /**
     * Renderiza a bounding box da estrutura completa com rotação
     */
    private static void renderStructureBoundingBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                                   BlockPos startPos, int width, int height, int length,
                                                   Direction direction) {
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        float r = 1.0f; // Branco
        float g = 1.0f;
        float b = 1.0f;
        float a = 0.5f;

        int rotatedWidth = getRotatedWidth(width, length, direction);
        int rotatedLength = getRotatedLength(width, length, direction);

        // Renderiza a bounding box usando renderLineBox
        LevelRenderer.renderLineBox(
                poseStack,
                lineConsumer,
                startPos.getX(), startPos.getY(), startPos.getZ(),
                startPos.getX() + rotatedWidth, startPos.getY() + height, startPos.getZ() + rotatedLength,
                r, g, b, a
        );
    }

    /**
     * Obtém a iluminação packed para a posição
     */
    private static int getPackedLight(BlockPos pos) {
        // Retorna iluminação máxima para melhor visibilidade
        return 0xF000F0;
    }

    /**
     * Atualiza automaticamente a visualização quando uma nova estrutura é selecionada
     */
    public static void updateFromCreativeOrb(Player player) {
        StructureLoader.StructureData selectedStructure = BuildTool.getSelectedStructure();
        if (selectedStructure != null) {
            updatePreviewPosition(player);
            // Força a atualização do preview
            BlockPos previewPos = getPreviewPositionFromPlayer(player);
            Direction direction = getDirectionFromYaw(player.getYRot());
            showPreview(selectedStructure, previewPos, direction);
        }
    }

    /**
     * Obtém a posição do preview baseada na mira do jogador
     */
    public static BlockPos getPreviewPositionFromPlayer(Player player) {
        // Obtém a posição onde o jogador está mirando
        HitResult hitResult = player.pick(100.0D, 0.0F, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        } else {
            // Se não estiver mirando em um bloco, calcula posição à frente
            Vec3 lookVec = player.getLookAngle();
            Vec3 eyePos = player.getEyePosition();
            Vec3 targetPos = eyePos.add(lookVec.scale(10)); // 10 blocos à frente dos olhos
            return new BlockPos(
                    (int)Math.floor(targetPos.x),
                    (int)Math.floor(targetPos.y),
                    (int)Math.floor(targetPos.z)
            );
        }
    }
}