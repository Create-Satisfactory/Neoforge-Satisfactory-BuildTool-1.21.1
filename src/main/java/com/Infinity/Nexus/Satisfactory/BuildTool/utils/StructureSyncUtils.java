package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureSyncS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.PlayerProgression;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class StructureSyncUtils {

    /**
     * Sends a structure to a specific player
     */
    public static void sendStructureToPlayer(ServerPlayer player, StructureLoader.StructureData structureData) {
        StructureSyncS2CPacket.StructureData packetData =
                new StructureSyncS2CPacket.StructureData(
                        structureData.getName(),
                        structureData.getAuthor(),
                        structureData.getDescription(),
                        structureData.getCategory(),
                        structureData.getGuiPath(),
                        structureData.getIconPath(),
                        structureData.getTier(),
                        structureData.getSubtier(),
                        structureData.getEnergyConsumption(),
                        structureData.isOnlyItems(),
                        structureData.getRequiredItems(),
                        structureData.getPattern(),
                        true // Official from server structures
                );

        PacketDistributor.sendToPlayer(player, new StructureSyncS2CPacket(packetData));
    }

    /**
     * Overload for StructureSaver.StructureData (legacy/saving usage)
     */
    public static void sendStructureToPlayer(ServerPlayer player, StructureSaver.StructureData structureData) {
        StructureSyncS2CPacket.StructureData packetData =
                new StructureSyncS2CPacket.StructureData(
                        structureData.name,
                        structureData.author,
                        structureData.description,
                        structureData.category,
                        structureData.guiPath != null ? structureData.guiPath : structureData.category,
                        structureData.iconPath,
                        structureData.tier,
                        structureData.subtier,
                        structureData.energyConsumption,
                        structureData.onlyItems,
                        structureData.requiredItems,
                        structureData.pattern,
                        false // New saved blueprint, not official server structure
                );

        PacketDistributor.sendToPlayer(player, new StructureSyncS2CPacket(packetData));
    }

    /**
     * Syncs all unlocked structures to the player based on their progression
     */
    public static void syncStructuresToPlayer(ServerPlayer player) {
        PlayerProgression progression = ProgressionHandler.getProgression(player.getUUID());
        
        for (StructureLoader.StructureData structure : StructureLoader.getServerStructures()) {
            boolean shouldSync = false;

            // Sync if explicitly unlocked
            if (progression.isStructureUnlocked(structure.getName())) {
                shouldSync = true;
            }
            // Sync if "custom" (Blueprints)
            else if ("custom".equalsIgnoreCase(structure.getCategory())) {
                shouldSync = true;
            }
            // Sync if meets tier/subtier requirements (Tier 0 structures must be explicitly unlocked)
            else if (structure.getTier() > 0 && progression.getTier() >= structure.getTier()) {
                if (structure.getSubtier() == null || structure.getSubtier().isEmpty() ||
                        progression.hasCompletedSubtier(structure.getSubtier())) {
                    shouldSync = true;
                }
            }

            if (shouldSync) {
                sendStructureToPlayer(player, structure);
            }
        }
    }
}