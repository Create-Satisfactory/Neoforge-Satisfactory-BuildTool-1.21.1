package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record StructureRemoveS2CPacket(UUID structureId) implements CustomPacketPayload {
    public static final Type<StructureRemoveS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "structure_remove_s2c"));

    public static final StreamCodec<FriendlyByteBuf, StructureRemoveS2CPacket> STREAM_CODEC = StreamCodec.ofMember(StructureRemoveS2CPacket::encode, StructureRemoveS2CPacket::decode);

    public static void encode(StructureRemoveS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.structureId);
    }

    public static StructureRemoveS2CPacket decode(FriendlyByteBuf buf) {
        return new StructureRemoveS2CPacket(buf.readUUID());
    }

    public static void handle(StructureRemoveS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientStructureTracker.removeStructure(packet.structureId);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
