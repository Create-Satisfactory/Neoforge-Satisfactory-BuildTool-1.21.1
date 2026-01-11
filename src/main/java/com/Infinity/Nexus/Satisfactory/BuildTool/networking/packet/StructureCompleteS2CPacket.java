package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PlacedStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record StructureCompleteS2CPacket(PlacedStructure structure) implements CustomPacketPayload {
    public static final Type<StructureCompleteS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "structure_complete_s2c"));

    public static final StreamCodec<FriendlyByteBuf, StructureCompleteS2CPacket> STREAM_CODEC = StreamCodec.ofMember(StructureCompleteS2CPacket::encode, StructureCompleteS2CPacket::decode);

    public static void encode(StructureCompleteS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.structure.id());
        buf.writeUtf(packet.structure.structureName());
        buf.writeBlockPos(packet.structure.minPos());
        buf.writeBlockPos(packet.structure.maxPos());
        buf.writeBlockPos(packet.structure.startPos());
        buf.writeFloat(packet.structure.rotation());
        buf.writeResourceLocation(packet.structure.level().location());
    }

    public static StructureCompleteS2CPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf();
        BlockPos minPos = buf.readBlockPos();
        BlockPos maxPos = buf.readBlockPos();
        BlockPos startPos = buf.readBlockPos();
        float rotation = buf.readFloat();
        ResourceLocation dimLoc = buf.readResourceLocation();
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        
        return new StructureCompleteS2CPacket(new PlacedStructure(id, name, minPos, maxPos, startPos, rotation, dimKey));
    }

    public static void handle(StructureCompleteS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
             ClientStructureTracker.addStructure(packet.structure);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
