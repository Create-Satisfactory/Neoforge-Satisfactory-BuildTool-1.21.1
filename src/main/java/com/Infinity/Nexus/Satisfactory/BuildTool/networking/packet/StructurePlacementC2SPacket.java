package com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.PlayerProgression;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PlaceStructure;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record StructurePlacementC2SPacket(
        StructureData structure,
        BlockPos position,
        float rotationY,
        Direction zoopDirection,
        int zoopCount
) implements CustomPacketPayload {

    public static final Type<StructurePlacementC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SatisfactoryBuildTool.MOD_ID, "structure_placement"));

    // StreamCodec personalizado
    public static final StreamCodec<FriendlyByteBuf, StructurePlacementC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    StructurePlacementC2SPacket::encode,
                    StructurePlacementC2SPacket::decode
            );

    private static void encode(FriendlyByteBuf buf, StructurePlacementC2SPacket packet) {
        // Encode StructureData
        StructureData.encode(buf, packet.structure);
        // Encode position and rotation
        buf.writeBlockPos(packet.position);
        buf.writeFloat(packet.rotationY);
        buf.writeBoolean(packet.zoopDirection != null);
        if (packet.zoopDirection != null) {
            buf.writeEnum(packet.zoopDirection);
        }
        buf.writeInt(packet.zoopCount);
    }

    private static StructurePlacementC2SPacket decode(FriendlyByteBuf buf) {
        StructureData structure = StructureData.decode(buf);
        BlockPos position = buf.readBlockPos();
        float rotationY = buf.readFloat();
        Direction zoopDirection = null;
        if (buf.readBoolean()) {
            zoopDirection = buf.readEnum(Direction.class);
        }
        int zoopCount = buf.readInt();
        return new StructurePlacementC2SPacket(structure, position, rotationY, zoopDirection, zoopCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructurePlacementC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            // Converte para StructureLoader.StructureData
            StructureLoader.StructureData structureData = packet.structure().toStructureData();

            // SECURITY FIX: If structure exists on the server, use official server data to prevent spoofing
            StructureLoader.StructureData officialData = StructureLoader.getServerStructure(structureData.getName());
            boolean isOfficial = false;
            if (officialData != null) {
                structureData = officialData;
                isOfficial = true;
            } else {
                // If it's not a server structure, mark it as NOT from server to avoid bypassing checks
                structureData.setFromServer(false);
            }

            // SERVER-SIDE UNLOCK CHECK: Verify if the player has unlocked this structure
            if (isOfficial) {
                PlayerProgression progression = ProgressionHandler.getProgression(player.getUUID());
                boolean hasUnlocked = progression.isStructureUnlocked(structureData.getName());

                // Also check if they meet the tier requirements (auto-unlock for Tier > 0)
                if (!hasUnlocked && structureData.getTier() > 0) {
                    if (progression.getTier() >= structureData.getTier()) {
                        if (structureData.getSubtier() == null || structureData.getSubtier().isEmpty() ||
                                progression.hasCompletedSubtier(structureData.getSubtier())) {
                            hasUnlocked = true;
                        }
                    }
                }

                if (!hasUnlocked) {
                    SatisfactoryBuildTool.LOGGER.warn("Player {} tried to place locked structure: {}", player.getName().getString(), structureData.getName());
                    player.sendSystemMessage(Component.translatable(
                            "message.satisfactory_build_tool.error_structure_locked"
                    ).withStyle(ChatFormatting.RED));
                    return;
                }
            }

            if (structureData != null && structureData.isValid()) {
                PlaceStructure.buildStructure(structureData, packet.position(), packet.rotationY(), player);
            } else {
                SatisfactoryBuildTool.LOGGER.warn("Invalid structure received from client: {}", packet.structure().name());
                player.sendSystemMessage(Component.translatable(
                        "message.satisfactory_build_tool.error_invalid_structure_received"
                ).withStyle(ChatFormatting.RED));
            }
        });
    }

    // Classe interna para representar os dados da estrutura no packet
    public record StructureData(
            String name,
            String author,
            String description,
            String category,
            String guiPath, // NEW
            String iconPath, // NEW
            int tier,
            String subtier,
            int energyConsumption, // NEW
            boolean onlyItems,
            List<StructureLoader.RequiredItem> requiredItems,
            List<List<List<String>>> pattern
    ) {

        public static void encode(FriendlyByteBuf buf, StructureData data) {
            buf.writeUtf(data.name);
            buf.writeUtf(data.author);
            buf.writeUtf(data.description);
            buf.writeUtf(data.category);
            buf.writeUtf(data.guiPath != null ? data.guiPath : ""); // NEW
            buf.writeUtf(data.iconPath != null ? data.iconPath : ""); // NEW
            buf.writeInt(data.tier);
            buf.writeUtf(data.subtier != null ? data.subtier : "");
            buf.writeInt(data.energyConsumption); // NEW
            buf.writeBoolean(data.onlyItems);
            
            buf.writeInt(data.requiredItems.size());
            for (StructureLoader.RequiredItem item : data.requiredItems) {
                buf.writeUtf(item.getItemId());
                buf.writeInt(item.getCount());
            }

            buf.writeInt(data.pattern.size());
            for (List<List<String>> layer : data.pattern) {
                buf.writeInt(layer.size());
                for (List<String> row : layer) {
                    buf.writeInt(row.size());
                    for (String block : row) {
                        buf.writeUtf(block);
                    }
                }
            }
        }

        public static StructureData decode(FriendlyByteBuf buf) {
            String name = buf.readUtf();
            String author = buf.readUtf();
            String description = buf.readUtf();
            String category = buf.readUtf();
            String guiPath = buf.readUtf(); // NEW
            if (guiPath.isEmpty()) guiPath = null;
            String iconPath = buf.readUtf(); // NEW
            if (iconPath.isEmpty()) iconPath = null;
            int tier = buf.readInt();
            String subtier = buf.readUtf();
            if (subtier.isEmpty()) subtier = null;
            int energyConsumption = buf.readInt(); // NEW
            boolean onlyItems = buf.readBoolean();
            
            int requiredItemsCount = buf.readInt();
            List<StructureLoader.RequiredItem> requiredItems = new ArrayList<>();
            for (int i = 0; i < requiredItemsCount; i++) {
                requiredItems.add(new StructureLoader.RequiredItem(buf.readUtf(), buf.readInt()));
            }

            int height = buf.readInt();
            List<List<List<String>>> pattern = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                int length = buf.readInt();
                List<List<String>> layer = new ArrayList<>();
                for (int z = 0; z < length; z++) {
                    int width = buf.readInt();
                    List<String> row = new ArrayList<>();
                    for (int x = 0; x < width; x++) {
                        row.add(buf.readUtf());
                    }
                    layer.add(row);
                }
                pattern.add(layer);
            }

            return new StructureData(name, author, description, category, guiPath, iconPath, tier, subtier, energyConsumption, onlyItems, requiredItems, pattern);
        }

        public StructureLoader.StructureData toStructureData() {
            StructureLoader.StructureData structureData = new StructureLoader.StructureData(
                    this.name != null ? this.name : "Unknown",
                    this.author,
                    this.description,
                    this.category,
                    this.guiPath,
                    this.iconPath,
                    this.tier,
                    this.subtier,
                    this.energyConsumption,
                    this.onlyItems,
                    this.requiredItems,
                    this.pattern
            );

            // Pré-processa os estados dos blocos
            structureData.preprocessBlockStates();

            return structureData;
        }
    }
}