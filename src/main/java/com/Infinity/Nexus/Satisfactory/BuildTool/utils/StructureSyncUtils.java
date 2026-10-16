package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureSyncS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class StructureSyncUtils {

    public static void sendStructureToPlayer(ServerPlayer player, StructureLoader.StructureData structureData) {
        StructureSyncS2CPacket.StructureData packetData =
                new StructureSyncS2CPacket.StructureData(
                        structureData.getName(),
                        structureData.getAuthor(),
                        structureData.getDescription(),
                        structureData.getCategory(),
                        structureData.getGuiPath(),
                        structureData.getIconPath(),
                        structureData.getSchematics(),
                        structureData.getTier(),
                        structureData.getSubtier(),
                        structureData.getEnergyConsumption(),
                        structureData.isOnlyItems(),
                        structureData.getRequiredItems(),
                        structureData.getPattern(),
                        structureData.getMaterialPatterns(),
                        true
                );

        PacketDistributor.sendToPlayer(player, new StructureSyncS2CPacket(packetData));
    }

    public static void sendStructureToPlayer(ServerPlayer player, StructureSaver.StructureData structureData) {
        StructureSyncS2CPacket.StructureData packetData =
                new StructureSyncS2CPacket.StructureData(
                        structureData.name,
                        structureData.author,
                        structureData.description,
                        structureData.category,
                        structureData.guiPath != null ? structureData.guiPath : structureData.category,
                        structureData.iconPath,
                        structureData.getSchematics(),
                        structureData.tier,
                        structureData.subtier,
                        structureData.energyConsumption,
                        structureData.onlyItems,
                        structureData.requiredItems,
                        structureData.pattern,
                        structureData.getMaterialPatterns(),
                        false
                );

        PacketDistributor.sendToPlayer(player, new StructureSyncS2CPacket(packetData));
    }

    /**
     * Syncs all structures unlocked by the player's FTB Team.
     */
    public static void syncStructuresToPlayer(ServerPlayer player) {
        for (StructureLoader.StructureData structure : StructureLoader.getServerStructures()) {
            if (ProgressionHandler.isStructureUnlocked(player, structure)) {
                sendStructureToPlayer(player, structure);
            }
        }
    }

    /**
     * Syncs unlocked structures to every online member of the player's FTB Team.
     */
    public static void syncStructuresToTeam(ServerPlayer teamMember) {
        if (teamMember.getServer() == null) return;
        UUID targetTeamId = ProgressionHandler.getEffectiveTeamId(teamMember);

        for (ServerPlayer onlinePlayer : teamMember.getServer().getPlayerList().getPlayers()) {
            if (ProgressionHandler.getEffectiveTeamId(onlinePlayer).equals(targetTeamId)) {
                syncStructuresToPlayer(onlinePlayer);
            }
        }
    }
}