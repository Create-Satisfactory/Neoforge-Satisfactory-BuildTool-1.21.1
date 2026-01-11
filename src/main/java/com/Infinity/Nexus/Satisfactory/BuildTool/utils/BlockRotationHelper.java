package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

/**
 * Utilitário para rotação de estados de blocos
 */
public class BlockRotationHelper {
    
    /**
     * Rotaciona completamente um BlockState baseado na direção
     */
    public static BlockState rotateBlockState(BlockState originalState, Direction direction) {
        BlockState rotatedState = originalState;
        
        // Rotaciona eixos de logs e blocos direcionais
        if (rotatedState.hasProperty(BlockStateProperties.AXIS)) {
            rotatedState = rotateAxis(rotatedState, direction);
        }
        
        if (rotatedState.hasProperty(BlockStateProperties.FACING)) {
            rotatedState = rotateFacing(rotatedState, direction);
        }
        
        if (rotatedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            rotatedState = rotateHorizontalFacing(rotatedState, direction);
        }
        
        if (rotatedState.hasProperty(BlockStateProperties.ROTATION_16)) {
            rotatedState = rotateRotation(rotatedState, direction);
        }
        
        return rotatedState;
    }
    
    /**
     * Rotaciona a propriedade AXIS
     */
    private static BlockState rotateAxis(BlockState state, Direction direction) {
        Direction.Axis currentAxis = state.getValue(BlockStateProperties.AXIS);
        
        // Se o eixo já for Y, não precisa rotacionar
        if (currentAxis == Direction.Axis.Y) {
            return state;
        }
        
        // Rotaciona o eixo horizontal baseado na direção
        switch (direction) {
            case EAST: // 90 graus
            case WEST: // 270 graus
                if (currentAxis == Direction.Axis.X) {
                    return state.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
                } else if (currentAxis == Direction.Axis.Z) {
                    return state.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                }
                break;
            case SOUTH: // 180 graus
            case NORTH: // 0 graus - mantém
            default:
                // Mantém o eixo original
                break;
        }
        
        return state;
    }
    
    /**
     * Rotaciona a propriedade FACING
     */
    private static BlockState rotateFacing(BlockState state, Direction direction) {
        Direction currentFacing = state.getValue(BlockStateProperties.FACING);
        
        // Se estiver virado para cima/baixo, não rotaciona
        if (currentFacing == Direction.UP || currentFacing == Direction.DOWN) {
            return state;
        }
        
        Direction rotatedFacing = StructureGeometryHelper.rotateDirection(currentFacing, direction);
        return state.setValue(BlockStateProperties.FACING, rotatedFacing);
    }
    
    /**
     * Rotaciona a propriedade HORIZONTAL_FACING
     */
    private static BlockState rotateHorizontalFacing(BlockState state, Direction direction) {
        Direction currentFacing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction rotatedFacing = StructureGeometryHelper.rotateDirection(currentFacing, direction);
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotatedFacing);
    }
    
    /**
     * Rotaciona a propriedade ROTATION_16 (para banners, etc)
     */
    private static BlockState rotateRotation(BlockState state, Direction direction) {
        int currentRotation = state.getValue(BlockStateProperties.ROTATION_16);
        int rotationIncrement = getRotationIncrement(direction);
        int newRotation = (currentRotation + rotationIncrement) % 16;
        if (newRotation < 0) newRotation += 16;
        
        return state.setValue(BlockStateProperties.ROTATION_16, newRotation);
    }
    
    /**
     * Obtém o incremento de rotação baseado na direção
     */
    private static int getRotationIncrement(Direction direction) {
        switch (direction) {
            case EAST: return 4;  // 90 graus
            case SOUTH: return 8; // 180 graus
            case WEST: return 12; // 270 graus
            case NORTH: default: return 0; // 0 graus
        }
    }
}