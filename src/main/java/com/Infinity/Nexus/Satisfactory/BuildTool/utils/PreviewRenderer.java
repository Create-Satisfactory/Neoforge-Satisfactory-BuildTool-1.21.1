package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Utilitário para renderização de previews de blocos
 */
public class PreviewRenderer {
    
    /**
     * Renderiza um bloco individual do preview com suporte a culling de faces
     */
    public static void renderPreviewBlock(BlockState state, BlockPos pos, PoseStack poseStack, 
                                        MultiBufferSource bufferSource, float alpha, Direction[] visibleFaces) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        
        if (state.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            
            try {
                BakedModel model = blockRenderer.getBlockModel(state);
                VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
                
                if (visibleFaces == null || visibleFaces.length == 0) {
                    // Renderiza o modelo inteiro se não houver culling de faces
                    blockRenderer.getModelRenderer().renderModel(
                            poseStack.last(),
                            consumer,
                            state,
                            model,
                            1.0f, 1.0f, 1.0f,
                            LevelRenderer.getLightColor(mc.level, pos),
                            OverlayTexture.NO_OVERLAY,
                            ModelData.EMPTY,
                            RenderType.translucent()
                    );
                } else {
                    // Renderiza apenas as faces visíveis
                    for (Direction direction : visibleFaces) {
                        renderFace(poseStack, consumer, state, model, direction, pos, mc);
                    }
                    // Renderiza quads sem direção (internos/especiais)
                    renderFace(poseStack, consumer, state, model, null, pos, mc);
                }
            } catch (Exception e) {
                renderWireframeBox(poseStack, bufferSource, pos, alpha);
            } finally {
                poseStack.popPose();
            }
        } else {
            renderWireframeBox(poseStack, bufferSource, pos, alpha);
        }
    }

    private static void renderFace(PoseStack poseStack, VertexConsumer consumer, BlockState state, 
                                 BakedModel model, Direction direction, BlockPos pos, Minecraft mc) {
        for (BakedQuad quad : model.getQuads(state, direction, mc.level.random, ModelData.EMPTY, RenderType.translucent())) {
            consumer.putBulkData(
                poseStack.last(),
                quad,
                1.0f, 1.0f, 1.0f, 1.0f,
                LevelRenderer.getLightColor(mc.level, pos),
                OverlayTexture.NO_OVERLAY
            );
        }
    }

    /**
     * Renderiza um bloco individual do preview (Legado para compatibilidade)
     */
    public static void renderPreviewBlock(BlockState state, BlockPos pos, PoseStack poseStack, 
                                        MultiBufferSource bufferSource, float alpha) {
        renderPreviewBlock(state, pos, poseStack, bufferSource, alpha, null);
    }
    
    /**
     * Renderiza uma caixa wireframe para representar o bloco
     */
    private static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource, 
                                         BlockPos pos, float alpha) {
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                0.0f, 1.0f, 0.0f, alpha // Verde com transparência
        );
    }
    
    /**
     * Renderiza uma caixa de bounding box para a estrutura inteira
     */
    public static void renderStructureBoundingBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                                BlockPos startPos, int width, int height, int length,
                                                Direction rotation, float alpha) {
        // Calcula as dimensões rotacionadas
        int rotatedWidth = StructureGeometryHelper.getRotatedWidth(width, length, rotation);
        int rotatedLength = StructureGeometryHelper.getRotatedLength(width, length, rotation);
        
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                startPos.getX(), startPos.getY(), startPos.getZ(),
                startPos.getX() + rotatedWidth, startPos.getY() + height, startPos.getZ() + rotatedLength,
                0.0f, 1.0f, 1.0f, alpha // Ciano com transparência
        );
    }
    
    /**
     * Renderiza marcações para posições inválidas (vermelho)
     */
    public static void renderInvalidPosition(PoseStack poseStack, MultiBufferSource bufferSource,
                                           BlockPos pos, float alpha) {
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                1.0f, 0.0f, 0.0f, alpha // Vermelho com transparência
        );
    }
}