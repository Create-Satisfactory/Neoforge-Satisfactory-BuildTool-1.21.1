package com.Infinity.Nexus.Satisfactory.BuildTool.networking;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.AreaClearC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetColorC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.SetMaterialC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructurePlacementC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureCompleteS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureDestroyC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureRemoveS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureSyncS2CPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModMessages {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SatisfactoryBuildTool.MOD_ID)
                .versioned("1.0")
                .optional();

        registrar.playToServer(
                StructurePlacementC2SPacket.TYPE,
                StructurePlacementC2SPacket.STREAM_CODEC,
                StructurePlacementC2SPacket::handle
        );

        registrar.playToServer(
                StructureDestroyC2SPacket.TYPE,
                StructureDestroyC2SPacket.STREAM_CODEC,
                StructureDestroyC2SPacket::handle
        );

        registrar.playToServer(
                AreaClearC2SPacket.TYPE,
                AreaClearC2SPacket.STREAM_CODEC,
                AreaClearC2SPacket::handle
        );

        registrar.playToClient(
                StructureSyncS2CPacket.TYPE,
                StructureSyncS2CPacket.STREAM_CODEC,
                StructureSyncS2CPacket::handle
        );

        registrar.playToServer(
                SetColorC2SPacket.TYPE,
                SetColorC2SPacket.STREAM_CODEC,
                SetColorC2SPacket::handle
        );
        
        registrar.playToServer(
                SetMaterialC2SPacket.TYPE,
                SetMaterialC2SPacket.STREAM_CODEC,
                SetMaterialC2SPacket::handle
        );

        registrar.playToClient(
                StructureCompleteS2CPacket.TYPE,
                StructureCompleteS2CPacket.STREAM_CODEC,
                StructureCompleteS2CPacket::handle
        );

        registrar.playToClient(
                StructureRemoveS2CPacket.TYPE,
                StructureRemoveS2CPacket.STREAM_CODEC,
                StructureRemoveS2CPacket::handle
        );
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(CustomPacketPayload packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}