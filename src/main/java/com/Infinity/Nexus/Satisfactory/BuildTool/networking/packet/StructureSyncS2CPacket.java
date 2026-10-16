package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ClientStructureSaver;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record StructureSyncS2CPacket(StructureData structure) implements CustomPacketPayload {

    public static final Type<StructureSyncS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SatisfactoryBuildTool.MOD_ID, "structure_sync"));

    public static final StreamCodec<FriendlyByteBuf, StructureSyncS2CPacket> STREAM_CODEC =
            StreamCodec.of(StructureSyncS2CPacket::encode, StructureSyncS2CPacket::decode);

    private static void encode(FriendlyByteBuf buf, StructureSyncS2CPacket packet) {
        StructureData.encode(buf, packet.structure);
    }

    private static StructureSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new StructureSyncS2CPacket(StructureData.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructureSyncS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStructureSaver.saveStructureLocally(packet.structure()));
    }

    public record StructureData(
            String name,
            String author,
            String description,
            String category,
            String guiPath,
            String iconPath,
            List<String> schematics,
            int tier,
            String subtier,
            int energyConsumption,
            boolean onlyItems,
            List<StructureLoader.RequiredItem> requiredItems,
            List<List<List<String>>> pattern,
            Map<String, List<List<List<String>>>> materialPatterns,
            boolean isOfficial
    ) {
        public static void encode(FriendlyByteBuf buf, StructureData data) {
            buf.writeUtf(data.name);
            buf.writeUtf(data.author != null ? data.author : "");
            buf.writeUtf(data.description != null ? data.description : "");
            buf.writeUtf(data.category != null ? data.category : "");
            buf.writeUtf(data.guiPath != null ? data.guiPath : "");
            buf.writeUtf(data.iconPath != null ? data.iconPath : "");

            // Encode "material:nbt" list
            List<String> schems = data.schematics != null ? data.schematics : Collections.emptyList();
            buf.writeInt(schems.size());
            for (String s : schems) {
                buf.writeUtf(s != null ? s : "");
            }

            buf.writeInt(data.tier);
            buf.writeUtf(data.subtier != null ? data.subtier : "");
            buf.writeInt(data.energyConsumption);
            buf.writeBoolean(data.onlyItems);

            buf.writeInt(data.requiredItems.size());
            for (StructureLoader.RequiredItem item : data.requiredItems) {
                buf.writeUtf(item.getItemId());
                buf.writeInt(item.getCount());
            }

            writePattern3D(buf, data.pattern);

            // Encode materialPatterns map
            Map<String, List<List<List<String>>>> matMap = data.materialPatterns != null ? data.materialPatterns : Collections.emptyMap();
            buf.writeInt(matMap.size());
            for (Map.Entry<String, List<List<List<String>>>> entry : matMap.entrySet()) {
                buf.writeUtf(entry.getKey());
                writePattern3D(buf, entry.getValue());
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
            String iconPath = buf.readUtf();
            if (iconPath.isEmpty()) iconPath = null;

            int schemCount = buf.readInt();
            List<String> schematics = new ArrayList<>(schemCount);
            for (int i = 0; i < schemCount; i++) {
                schematics.add(buf.readUtf());
            }

            int tier = buf.readInt();
            String subtier = buf.readUtf();
            if (subtier.isEmpty()) subtier = null;
            int energyConsumption = buf.readInt();
            boolean onlyItems = buf.readBoolean();

            int requiredItemsCount = buf.readInt();
            List<StructureLoader.RequiredItem> requiredItems = new ArrayList<>(requiredItemsCount);
            for (int i = 0; i < requiredItemsCount; i++) {
                requiredItems.add(new StructureLoader.RequiredItem(buf.readUtf(), buf.readInt()));
            }

            List<List<List<String>>> pattern = readPattern3D(buf);

            int matMapSize = buf.readInt();
            Map<String, List<List<List<String>>>> materialPatterns = new LinkedHashMap<>(matMapSize);
            for (int i = 0; i < matMapSize; i++) {
                String matKey = buf.readUtf();
                List<List<List<String>>> matPat = readPattern3D(buf);
                materialPatterns.put(matKey, matPat);
            }

            boolean isOfficial = buf.readBoolean();
            return new StructureData(name, author, description, category, guiPath, iconPath, schematics,
                    tier, subtier, energyConsumption, onlyItems, requiredItems, pattern, materialPatterns, isOfficial);
        }

        private static void writePattern3D(FriendlyByteBuf buf, List<List<List<String>>> pat) {
            if (pat == null) pat = Collections.emptyList();
            buf.writeInt(pat.size());
            for (List<List<String>> layer : pat) {
                buf.writeInt(layer.size());
                for (List<String> row : layer) {
                    buf.writeInt(row.size());
                    for (String blockString : row) {
                        buf.writeUtf(blockString != null ? blockString : "");
                    }
                }
            }
        }

        private static List<List<List<String>>> readPattern3D(FriendlyByteBuf buf) {
            int height = buf.readInt();
            List<List<List<String>>> pat = new ArrayList<>(height);
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
                pat.add(layer);
            }
            return pat;
        }
    }
}