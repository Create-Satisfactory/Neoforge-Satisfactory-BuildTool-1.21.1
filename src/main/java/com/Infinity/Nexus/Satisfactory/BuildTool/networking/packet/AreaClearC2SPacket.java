package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureDestroyer;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AreaClearC2SPacket(BlockPos minPos, BlockPos maxPos, ClearType clearType) implements CustomPacketPayload {
    public enum ClearType {
        ALL,
        FLATTEN
    }

    public static final Type<AreaClearC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "area_clear_c2s"));

    public static final StreamCodec<FriendlyByteBuf, AreaClearC2SPacket> STREAM_CODEC = StreamCodec.ofMember(AreaClearC2SPacket::encode, AreaClearC2SPacket::decode);

    public static void encode(AreaClearC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.minPos);
        buf.writeBlockPos(packet.maxPos);
        buf.writeEnum(packet.clearType);
    }

    public static AreaClearC2SPacket decode(FriendlyByteBuf buf) {
        BlockPos minPos = buf.readBlockPos();
        BlockPos maxPos = buf.readBlockPos();
        ClearType clearType = buf.readEnum(ClearType.class);
        return new AreaClearC2SPacket(minPos, maxPos, clearType);
    }

    public static void handle(AreaClearC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!Config.enable_clear_mode) return;
            if (context.player() instanceof ServerPlayer serverPlayer) {
                StructureDestroyer.clearArea(serverPlayer, packet.minPos, packet.maxPos, packet.clearType);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
