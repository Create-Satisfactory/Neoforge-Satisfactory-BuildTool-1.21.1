package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PlacedStructure;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureDestroyer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record StructureDestroyC2SPacket(PlacedStructure structure) implements CustomPacketPayload {
    public static final Type<StructureDestroyC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "structure_destroy_c2s"));

    public static final StreamCodec<FriendlyByteBuf, StructureDestroyC2SPacket> STREAM_CODEC = StreamCodec.ofMember(StructureDestroyC2SPacket::encode, StructureDestroyC2SPacket::decode);

    public static void encode(StructureDestroyC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.structure.id());
        buf.writeUtf(packet.structure.structureName());
        buf.writeBlockPos(packet.structure.minPos());
        buf.writeBlockPos(packet.structure.maxPos());
        buf.writeBlockPos(packet.structure.startPos());
        buf.writeFloat(packet.structure.rotation());
        buf.writeResourceLocation(packet.structure.level().location());
    }

    public static StructureDestroyC2SPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf();
        BlockPos minPos = buf.readBlockPos();
        BlockPos maxPos = buf.readBlockPos();
        BlockPos startPos = buf.readBlockPos();
        float rotation = buf.readFloat();
        ResourceLocation dimLoc = buf.readResourceLocation();
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        
        return new StructureDestroyC2SPacket(new PlacedStructure(id, name, minPos, maxPos, startPos, rotation, dimKey));
    }

    public static void handle(StructureDestroyC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                StructureDestroyer.destroyStructure(serverPlayer, packet.structure);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
