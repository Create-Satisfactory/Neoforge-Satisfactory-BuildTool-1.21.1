package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetMaterialC2SPacket(String materialName) implements CustomPacketPayload {

    public static final Type<SetMaterialC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SatisfactoryBuildTool.MOD_ID, "set_material"));

    public static final StreamCodec<FriendlyByteBuf, SetMaterialC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    SetMaterialC2SPacket::encode,
                    SetMaterialC2SPacket::decode
            );

    private static void encode(FriendlyByteBuf buf, SetMaterialC2SPacket packet) {
        buf.writeUtf(packet.materialName);
    }

    private static SetMaterialC2SPacket decode(FriendlyByteBuf buf) {
        return new SetMaterialC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetMaterialC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof BuildTool) {
                if (packet.materialName().startsWith("PATTERN:")) {
                    String pattern = packet.materialName().substring("PATTERN:".length());
                    BuildTool.setPattern(mainHandItem, pattern);
                } else {
                    BuildTool.setMaterial(mainHandItem, packet.materialName());
                }
                player.inventoryMenu.broadcastChanges();
            }
        });
    }
}
