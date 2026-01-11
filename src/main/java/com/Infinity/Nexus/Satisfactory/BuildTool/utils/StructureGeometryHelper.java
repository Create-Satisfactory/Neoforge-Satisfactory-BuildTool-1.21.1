package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;

/**
 * Utilitário para cálculos geométricos e rotação de estruturas
 */
public class StructureGeometryHelper {
    
    /**
     * Calcula a posição rotacionada de um bloco dentro de uma estrutura
     */
    public static BlockPos getRotatedBlockPos(BlockPos startPos, int x, int y, int z, 
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
     * Obtém a largura rotacionada de uma estrutura
     */
    public static int getRotatedWidth(int width, int length, Direction direction) {
        return (direction == Direction.EAST || direction == Direction.WEST) ? length : width;
    }
    
    /**
     * Obtém o comprimento rotacionado de uma estrutura
     */
    public static int getRotatedLength(int width, int length, Direction direction) {
        return (direction == Direction.EAST || direction == Direction.WEST) ? width : length;
    }
    
    /**
     * Calcula o tamanho da estrutura em uma determinada direção após rotação
     */
    public static int getStructureSizeInDirection(int width, int length, int height, 
                                                Direction structRotation, Direction targetDir) {
        int rotatedW = getRotatedWidth(width, length, structRotation);
        int rotatedL = getRotatedLength(width, length, structRotation);
        
        return switch (targetDir.getAxis()) {
            case X -> rotatedW;
            case Z -> rotatedL;
            case Y -> height;
        };
    }
    
    /**
     * Converte o yaw do jogador para uma das 4 direções principais
     */
    public static Direction getDirectionFromYaw(float yaw) {
        // Normaliza o yaw para 0-360
        float normalizedYaw = (yaw + 360) % 360;
        
        // Determina a direção baseada no yaw
        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            return Direction.SOUTH;  // Player olhando para SOUTH (Z+)
        } else if (normalizedYaw >= 45 && normalizedYaw < 135) {
            return Direction.WEST;   // Player olhando para WEST (X-)
        } else if (normalizedYaw >= 135 && normalizedYaw < 225) {
            return Direction.NORTH;  // Player olhando para NORTH (Z-)
        } else {
            return Direction.EAST;   // Player olhando para EAST (X+)
        }
    }
    
    /**
     * Converte direção para yaw em graus
     */
    public static float getYawFromDirection(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
    }
    
    /**
     * Aplica offset de rotação a uma direção
     */
    public static Direction applyRotationOffset(Direction base, int offset) {
        Direction current = base;
        for (int i = 0; i < offset; i++) {
            current = current.getClockWise();
        }
        return current;
    }
    
    /**
     * Rotaciona uma direção horizontal baseado na rotação da estrutura
     */
    public static Direction rotateDirection(Direction original, Direction rotation) {
        if (original.getAxis() == Direction.Axis.Y) {
            return original; // Não rotaciona direções verticais
        }
        
        int originalHorizontal = horizontalDirectionToIndex(original);
        int rotationOffset = directionToRotationIndex(rotation);
        
        int newIndex = (originalHorizontal + rotationOffset) % 4;
        if (newIndex < 0) newIndex += 4;
        
        return indexToHorizontalDirection(newIndex);
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
    
    private static int directionToRotationIndex(Direction direction) {
        return switch (direction) {
            case EAST -> 1;  // 90 graus
            case SOUTH -> 2; // 180 graus
            case WEST -> 3;  // 270 graus
            default -> 0;    // 0 graus
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
    
    /**
     * Calcula a posição do preview baseada na mira do jogador
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