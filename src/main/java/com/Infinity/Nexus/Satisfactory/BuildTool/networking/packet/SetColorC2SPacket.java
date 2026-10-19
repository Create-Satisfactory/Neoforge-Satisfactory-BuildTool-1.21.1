package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetColorC2SPacket(int color) implements CustomPacketPayload {

    public static final Type<SetColorC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SatisfactoryBuildTool.MOD_ID, "set_color"));

    public static final StreamCodec<FriendlyByteBuf, SetColorC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    SetColorC2SPacket::encode,
                    SetColorC2SPacket::decode
            );

    private static void encode(FriendlyByteBuf buf, SetColorC2SPacket packet) {
        buf.writeInt(packet.color);
    }

    private static SetColorC2SPacket decode(FriendlyByteBuf buf) {
        return new SetColorC2SPacket(buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetColorC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof BuildTool) {
                BuildTool.setColor(mainHandItem, packet.color());
                player.inventoryMenu.broadcastChanges();
            }
        });
    }
}
