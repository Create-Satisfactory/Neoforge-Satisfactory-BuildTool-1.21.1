package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreviewRenderer {

    private static final int FULL_BRIGHT = 0xF000F0;

    // Cache of virtual client-side BlockEntities & ModelData keyed by rotated state + raw SNBT + direction
    private static final Map<String, BlockEntity> VIRTUAL_BE_CACHE = new HashMap<>();
    private static final Map<String, ModelData> MODEL_DATA_CACHE = new HashMap<>();

    public static void clearCaches() {
        VIRTUAL_BE_CACHE.clear();
        MODEL_DATA_CACHE.clear();
    }

    /**
     * Renders a preview block with full Create / Copycat ModelData, BlockEntityRenderers, and RGBA hologram tinting.
     */
    public static void renderPreviewBlock(
            BlockState rotatedState,
            @Nullable String rawBlockString,
            Direction structureDirection,
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float r, float g, float b, float alpha,
            @Nullable Direction[] visibleFaces) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        ModelData modelData = ModelData.EMPTY;
        BlockEntity virtualBe = null;

        // 1. If the block is an EntityBlock (Copycats, Create Basins/Shafts/Mixers, etc.), resolve its virtual BlockEntity & ModelData
        if (rotatedState.getBlock() instanceof EntityBlock entityBlock) {
            String cacheKey = rotatedState.toString() + "|" + structureDirection.getName() + "|" + (rawBlockString != null ? rawBlockString : "");
            virtualBe = VIRTUAL_BE_CACHE.get(cacheKey);
            modelData = MODEL_DATA_CACHE.getOrDefault(cacheKey, ModelData.EMPTY);

            if (virtualBe == null && !MODEL_DATA_CACHE.containsKey(cacheKey)) {
                try {
                    virtualBe = entityBlock.newBlockEntity(BlockPos.ZERO, rotatedState);
                    if (virtualBe != null) {
                        virtualBe.setLevel(mc.level);
                        CompoundTag nbt = SchematicNbtHelper.extractNbt(rawBlockString);
                        if (nbt != null && !nbt.isEmpty()) {
                            CompoundTag copy = nbt.copy();
                            copy.putInt("x", 0);
                            copy.putInt("y", 0);
                            copy.putInt("z", 0);
                            virtualBe.loadWithComponents(copy, mc.level.registryAccess());

                            Rotation rot = PlaceStructure.getRotationFromDirection(structureDirection);
                            Object transform = PlaceStructure.createStructureTransform(rot);
                            PlaceStructure.applyCreateTransformToBlockEntity(transform, virtualBe);
                        }
                        modelData = virtualBe.getModelData();
                        VIRTUAL_BE_CACHE.put(cacheKey, virtualBe);
                    }
                } catch (Exception ignored) {}
                MODEL_DATA_CACHE.put(cacheKey, modelData);
            }
        }

        // 2. Render BakedBlockModel Quads (passing null RenderType so solid/cutout Create & Copycat quads are included)
        if (rotatedState.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

            try {
                BakedModel model = blockRenderer.getBlockModel(rotatedState);
                modelData = model.getModelData(mc.level, pos, rotatedState, modelData);
                VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
                RandomSource rand = RandomSource.create(42L);

                if (visibleFaces == null || visibleFaces.length == 0) {
                    for (Direction dir : Direction.values()) {
                        renderQuads(poseStack, consumer, model.getQuads(rotatedState, dir, rand, modelData, null), r, g, b, alpha);
                    }
                    renderQuads(poseStack, consumer, model.getQuads(rotatedState, null, rand, modelData, null), r, g, b, alpha);
                } else {
                    for (Direction dir : visibleFaces) {
                        renderQuads(poseStack, consumer, model.getQuads(rotatedState, dir, rand, modelData, null), r, g, b, alpha);
                    }
                    renderQuads(poseStack, consumer, model.getQuads(rotatedState, null, rand, modelData, null), r, g, b, alpha);
                }
            } catch (Exception e) {
                renderWireframeBox(poseStack, bufferSource, pos, r, g, b, alpha);
            } finally {
                poseStack.popPose();
            }
        }

        // 3. Render BlockEntityRenderer (so Create Shafts, Cogwheels, Mixer heads, and Depots show up in the hologram)
        if (virtualBe != null) {
            try {
                BlockEntityRenderer<BlockEntity> ber = mc.getBlockEntityRenderDispatcher().getRenderer(virtualBe);
                if (ber != null) {
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    ber.render(virtualBe, 0.0f, poseStack, bufferSource, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            } catch (Exception ignored) {}
        }
    }

    private static void renderQuads(PoseStack poseStack, VertexConsumer consumer, List<BakedQuad> quads,
                                    float r, float g, float b, float alpha) {
        PoseStack.Pose last = poseStack.last();
        for (BakedQuad quad : quads) {
            consumer.putBulkData(last, quad, r, g, b, alpha, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * Legacy overload for compatibility.
     */
    public static void renderPreviewBlock(BlockState state, BlockPos pos, PoseStack poseStack,
                                          MultiBufferSource bufferSource, float alpha, Direction[] visibleFaces) {
        renderPreviewBlock(state, null, Direction.NORTH, pos, poseStack, bufferSource, 0.45f, 0.85f, 1.0f, alpha, visibleFaces);
    }

    public static void renderPreviewBlock(BlockState state, BlockPos pos, PoseStack poseStack,
                                          MultiBufferSource bufferSource, float alpha) {
        renderPreviewBlock(state, pos, poseStack, bufferSource, alpha, null);
    }

    private static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                           BlockPos pos, float r, float g, float b, float alpha) {
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                r, g, b, alpha
        );
    }

    public static void renderInvalidPosition(PoseStack poseStack, MultiBufferSource bufferSource,
                                             BlockPos pos, float alpha) {
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                1.0f, 0.2f, 0.2f, alpha
        );
    }
}