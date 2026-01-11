package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ClientStructureSaver;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record StructureSyncS2CPacket(
        StructureData structure
) implements CustomPacketPayload {

    public static final Type<StructureSyncS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SatisfactoryBuildTool.MOD_ID, "structure_sync"));

    // StreamCodec personalizado
    public static final StreamCodec<FriendlyByteBuf, StructureSyncS2CPacket> STREAM_CODEC =
            StreamCodec.of(
                    StructureSyncS2CPacket::encode,
                    StructureSyncS2CPacket::decode
            );

    private static void encode(FriendlyByteBuf buf, StructureSyncS2CPacket packet) {
        StructureData.encode(buf, packet.structure);
    }

    private static StructureSyncS2CPacket decode(FriendlyByteBuf buf) {
        StructureData structure = StructureData.decode(buf);
        return new StructureSyncS2CPacket(structure);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructureSyncS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientStructureSaver.saveStructureLocally(packet.structure());

            if (context.player() != null) {
                context.player().sendSystemMessage(
                        Component.translatable(
                                "message.satisfactory_build_tool.structure_received_saved",
                                packet.structure().name()
                        ).withStyle(ChatFormatting.GREEN)
                );
            }
        });
    }

    // Record para dados da estrutura (igual ao do packet C2S)
    public record StructureData(
            String name,
            String author,
            String description,
            String category,
            String guiPath, // NEW
            String iconPath, // NEW
            int tier,
            String subtier, // NEW
            int energyConsumption, // NEW
            boolean onlyItems,
            List<StructureLoader.RequiredItem> requiredItems,
            List<List<List<String>>> pattern,
            boolean isOfficial
    ) {

        public static void encode(FriendlyByteBuf buf, StructureData data) {
            buf.writeUtf(data.name);
            buf.writeUtf(data.author != null ? data.author : "");
            buf.writeUtf(data.description != null ? data.description : "");
            buf.writeUtf(data.category != null ? data.category : "");
            buf.writeUtf(data.guiPath != null ? data.guiPath : "");
            buf.writeUtf(data.iconPath != null ? data.iconPath : ""); // NEW
            buf.writeInt(data.tier);
            buf.writeUtf(data.subtier != null ? data.subtier : ""); // NEW (Handle null)
            buf.writeInt(data.energyConsumption); // NEW
            buf.writeBoolean(data.onlyItems);

            // Encode requiredItems
            buf.writeInt(data.requiredItems.size());
            for (StructureLoader.RequiredItem item : data.requiredItems) {
                buf.writeUtf(item.getItemId());
                buf.writeInt(item.getCount());
            }

            // Encode pattern 3D
            buf.writeInt(data.pattern.size()); // Altura (Y)
            for (List<List<String>> layer : data.pattern) {
                buf.writeInt(layer.size()); // Comprimento (Z)
                for (List<String> row : layer) {
                    buf.writeInt(row.size()); // Largura (X)
                    for (String blockString : row) {
                        buf.writeUtf(blockString != null ? blockString : "");
                    }
                }
            }
            buf.writeBoolean(data.isOfficial);
        }

        public static StructureData decode(FriendlyByteBuf buf) {
            String name = buf.readUtf();
            String author = buf.readUtf();
            String description = buf.readUtf();
            String category = buf.readUtf();
            String guiPath = buf.readUtf();
            if (guiPath.isEmpty()) guiPath = null;
            String iconPath = buf.readUtf(); // NEW
            if (iconPath.isEmpty()) iconPath = null;
            int tier = buf.readInt();
            String subtier = buf.readUtf(); // NEW
            if (subtier.isEmpty()) subtier = null; // Convert empty back to null
            int energyConsumption = buf.readInt(); // NEW
            boolean onlyItems = buf.readBoolean();

            // Decode requiredItems
            int requiredItemsCount = buf.readInt();
            List<StructureLoader.RequiredItem> requiredItems = new ArrayList<>(requiredItemsCount);
            for (int i = 0; i < requiredItemsCount; i++) {
                String itemId = buf.readUtf();
                int count = buf.readInt();
                requiredItems.add(new StructureLoader.RequiredItem(itemId, count));
            }

            int height = buf.readInt();
            List<List<List<String>>> pattern = new ArrayList<>(height);

            for (int y = 0; y < height; y++) {
                int length = buf.readInt();
                List<List<String>> layer = new ArrayList<>(length);

                for (int z = 0; z < length; z++) {
                    int width = buf.readInt();
                    List<String> row = new ArrayList<>(width);

                    for (int x = 0; x < width; x++) {
                        row.add(buf.readUtf());
                    }
                    layer.add(row);
                }
                pattern.add(layer);
            }

            boolean isOfficial = buf.readBoolean();

            return new StructureData(name, author, description, category, guiPath, iconPath, tier, subtier, energyConsumption, onlyItems, requiredItems, pattern, isOfficial);
        }
    }
}