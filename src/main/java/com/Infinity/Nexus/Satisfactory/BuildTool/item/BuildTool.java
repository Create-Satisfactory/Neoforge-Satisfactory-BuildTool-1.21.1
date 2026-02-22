package com.Infinity.Nexus.Satisfactory.BuildTool.item;

import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureDestroyC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructurePlacementC2SPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.*;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.PlacedStructure;

import net.minecraft.ChatFormatting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class BuildTool extends PickaxeItem {
    private static int selectedStructureIndex = 0;
    private static List<StructureLoader.StructureData> availableStructures;
    // Maps to store selection data per player and per side (Client/Server)
    private static final Map<UUID, SelectionData> clientSelections = new java.util.HashMap<>();
    private static final Map<UUID, SelectionData> serverSelections = new java.util.HashMap<>();

    private static class SelectionData {
        public BlockPos point1;
        public BlockPos point2;
        public boolean selectingArea;
    }

    private static SelectionData getSelectionData(Player player) {
        if (player.level().isClientSide()) {
            return clientSelections.computeIfAbsent(player.getUUID(), k -> new SelectionData());
        } else {
            return serverSelections.computeIfAbsent(player.getUUID(), k -> new SelectionData());
        }
    }
    // Valores agora obtidos da configuração
    private static int getMaxSize() { return Config.max_structure_size; }
    private static long getMaxVolume() { return Config.max_structure_volume; }

    public enum BuildMode {
        DESTRUCTION,
        CONSTRUCTION,
        CLEAR,
        NONE
    }

    private static BuildMode currentMode = BuildMode.CONSTRUCTION;

    // Sistema de cooldown para evitar cliques duplos na destruição
    private static long lastDestructionTime = 0;
    private static final long DESTRUCTION_COOLDOWN_MS = 500; // 500ms de cooldown

    public BuildTool(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(PickaxeItem.createAttributes(Tiers.NETHERITE, 1.0F, -2.8F)));
    }

    public static BuildMode getMode() {
        return currentMode;
    }

    public static boolean isDestructionMode() {
        return currentMode == BuildMode.DESTRUCTION;
    }

    // Getters for selection state (client-side rendering)
    public static BlockPos getPoint1(Player player) {
        return getSelectionData(player).point1;
    }

    public static BlockPos getPoint2(Player player) {
        return getSelectionData(player).point2;
    }

    public static boolean isSelectingArea(Player player) {
        return getSelectionData(player).selectingArea;
    }

    public static void resetSelection(Player player) {
        SelectionData data = getSelectionData(player);
        data.point1 = null;
        data.point2 = null;
        data.selectingArea = false;
    }

    public static void resetParcialSelection(Player player) {
        SelectionData data = getSelectionData(player);
        if(data.point2 != null) {
            data.point2 = null;
            data.selectingArea = true;
            return;
        }

        if(data.point1 != null) {
            resetSelection(player);
        }
    }

    public static void cycleMode(Player player) {
        resetSelection(player);
        currentMode = switch (currentMode) {
            case CONSTRUCTION -> BuildMode.DESTRUCTION;
            case DESTRUCTION -> Config.enable_clear_mode ? BuildMode.CLEAR : BuildMode.NONE;
            case CLEAR -> BuildMode.NONE;
            case NONE -> BuildMode.CONSTRUCTION;
        };

        String messageKey = switch (currentMode) {
            case CONSTRUCTION -> "message.satisfactory_build_tool.mode_construction";
            case DESTRUCTION -> "message.satisfactory_build_tool.mode_destruction";
            case CLEAR -> "message.satisfactory_build_tool.mode_clear";
            case NONE -> "message.satisfactory_build_tool.mode_none";
        };

        ChatFormatting color = switch (currentMode) {
            case CONSTRUCTION -> ChatFormatting.GREEN;
            case DESTRUCTION -> ChatFormatting.RED;
            case CLEAR -> ChatFormatting.BLUE;
            case NONE -> ChatFormatting.GRAY;
        };

        player.displayClientMessage(Component.translatable(messageKey).withStyle(color), true);

        if (currentMode == BuildMode.DESTRUCTION) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.destruction_mode_instruction").withStyle(ChatFormatting.YELLOW), false);
        } else if (currentMode == BuildMode.CLEAR) {
            player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.clear_mode_instruction").withStyle(ChatFormatting.YELLOW), false);
        }
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
    }

    public static void setSelectedStructureIndex(int index) {
        setSelectedStructureIndexWithoutClientCall(index);
        try {
            // Tenta chamar o cliente apenas se estivermos no ambiente correto
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraftClass.getMethod("getInstance").invoke(null);
            Object player = minecraftClass.getMethod("player").invoke(instance);
            if (player != null) {
                Class<?> structurePreviewClass = Class.forName("com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructurePreview");
                structurePreviewClass.getMethod("updateFromCreativeOrb", player.getClass()).invoke(null, player);
            }
        } catch (Exception e) {
            // Ignora erros relacionados à classe do cliente não estar disponível
        }
    }

    public static void setSelectedStructureIndexWithoutClientCall(int index) {
        selectedStructureIndex = index;
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if(!Config.enable_creativity_orb){
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        if (currentMode == BuildMode.NONE || (currentMode == BuildMode.CLEAR && !Config.enable_clear_mode)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        ItemStack itemstack = player.getItemInHand(hand);

        // Modo Destruição - agora manuseado no cliente
        if (currentMode == BuildMode.DESTRUCTION) {
            if (level.isClientSide) {
                // No client, apenas consome o evento para evitar abrir a GUI
                return InteractionResultHolder.success(itemstack);
            } else {
                // No servidor, apenas passa o evento pois o tratamento é feito no cliente
                return InteractionResultHolder.pass(itemstack);
            }
        }

        // Modo Construção - apenas no cliente
        if (level.isClientSide() && hand != InteractionHand.OFF_HAND) {
            if (availableStructures == null || availableStructures.isEmpty()) {
                loadStructures();
            }

            if (availableStructures.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.no_structures").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(itemstack);
            }

            if (player.isShiftKeyDown()) {
                if (currentMode == BuildMode.CLEAR) {
                    // Check if player is clicking inside an existing selection
                    SelectionData data = getSelectionData(player);
                    if (data.point1 != null && data.point2 != null) {
                        HitResult hitResult = player.pick(100.0D, 0.0F, false);
                        if (hitResult.getType() == HitResult.Type.BLOCK) {
                            BlockPos clickedPos = ((BlockHitResult) hitResult).getBlockPos();
                            if (isPosInSelection(player, clickedPos)) {
                                openClearModeScreen();
                                return InteractionResultHolder.success(itemstack);
                            }
                        }
                    }
                    return handleAreaSelection(player, itemstack);
                }
                return placeStructure(player, itemstack);
            } else {
                // Open main GUI
                openBuildToolScreen();
                return InteractionResultHolder.success(itemstack);
            }
        } else if(!level.isClientSide() && hand == InteractionHand.OFF_HAND && player.isShiftKeyDown()){
            return handleAreaSelection(player, itemstack);
        } else if(level.isClientSide() && hand == InteractionHand.OFF_HAND && player.isShiftKeyDown()){
            // Allow area selection on client too for visualization
            return handleAreaSelection(player, itemstack);
        }

        return InteractionResultHolder.pass(itemstack);
    }

    private boolean isPosInSelection(Player player, BlockPos pos) {
        SelectionData data = getSelectionData(player);
        if (data.point1 == null || data.point2 == null) return false;
        int minX = Math.min(data.point1.getX(), data.point2.getX());
        int minY = Math.min(data.point1.getY(), data.point2.getY());
        int minZ = Math.min(data.point1.getZ(), data.point2.getZ());
        int maxX = Math.max(data.point1.getX(), data.point2.getX());
        int maxY = Math.max(data.point1.getY(), data.point2.getY());
        int maxZ = Math.max(data.point1.getZ(), data.point2.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getY() >= minY && pos.getY() <= maxY &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private void openClearModeScreen() {
        // Usar a classe cliente para abrir a tela
        try {
            Class.forName("com.Infinity.Nexus.Satisfactory.BuildTool.client.BuildToolClient");
            // Chama o método estático para abrir a tela de limpeza
            Class<?> clientClass = Class.forName("com.Infinity.Nexus.Satisfactory.BuildTool.client.BuildToolClient");
            clientClass.getMethod("openClearModeScreen").invoke(null);
        } catch (Exception e) {
            // Ignora erros relacionados à classe do cliente não estar disponível
        }
    }

    private InteractionResultHolder<ItemStack> handleAreaSelection(Player player, ItemStack itemstack) {
        HitResult hitResult = player.pick(100.0D, 0.0F, false);

        BlockPos clickedPos;

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            // Player is looking at a block - use that position
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            clickedPos = blockHit.getBlockPos();
        } else {
            // Player is looking into the air - calculate position at max distance
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookVec = player.getLookAngle();
            Vec3 targetPos = eyePos.add(lookVec.scale(100.0D));
            clickedPos = BlockPos.containing(targetPos);
        }

        SelectionData data = getSelectionData(player);

        if (!data.selectingArea) {
            data.point1 = clickedPos;
            data.selectingArea = true;
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.point1_selected",
                    data.point1.getX(), data.point1.getY(), data.point1.getZ()).withStyle(ChatFormatting.GREEN), true);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
        } else {
            data.point2 = clickedPos;
            data.selectingArea = false;

            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.point2_selected",
                    data.point2.getX(), data.point2.getY(), data.point2.getZ()).withStyle(ChatFormatting.GREEN), true);

            if (!checkSizeLimit(player, data.point1, data.point2)) {
                data.point2 = null; // Keep point1 but reset point2 on error
                data.selectingArea = true; // Still "selecting" area (waiting for a valid point2)
                return InteractionResultHolder.fail(itemstack);
            }

            if (currentMode == BuildMode.CLEAR) {
                player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.clear_confirm_prompt").withStyle(ChatFormatting.YELLOW), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
            } else {
                player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.save_prompt").withStyle(ChatFormatting.YELLOW), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
                saveStructure(player);
            }
        }

        return InteractionResultHolder.success(itemstack);
    }

    private boolean checkSizeLimit(Player player, BlockPos pos1, BlockPos pos2) {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;
        int volume = width * height * length;



        if (width > getMaxSize() || height > getMaxSize() || length > getMaxSize()) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.size_limit_exceeded",
                    getMaxSize(), getMaxSize(), getMaxSize()).withStyle(ChatFormatting.RED), true);
            return false;
        }

        if (volume > getMaxVolume()) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.volume_limit_exceeded",
                    getMaxVolume()).withStyle(ChatFormatting.RED), true);
            return false;
        }

        return true;
    }

    private boolean canBuildStructure(StructureLoader.StructureData structure, Player player) {
        int width = structure.getWidth();
        int height = structure.getHeight();
        int length = structure.getLength();
        int volume = width * height * length;

        if (width > getMaxSize() || height > getMaxSize() || length > getMaxSize()) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.structure_too_large",
                    structure.getName(), getMaxSize(), getMaxSize(), getMaxSize()).withStyle(ChatFormatting.RED), true);
            return false;
        }

        if (volume > getMaxVolume()) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.structure_volume_too_large",
                    structure.getName(), getMaxVolume()).withStyle(ChatFormatting.RED), true);
            return false;
        }

        return true;
    }

    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide() && player.getMainHandItem().getItem() instanceof BuildTool creativity) {
            if (currentMode == BuildMode.CONSTRUCTION && getSelectedStructure() != null) {
                StructureLoader.StructureData structure = getSelectedStructure();

                if (structure.getWidth() > getMaxSize() || structure.getHeight() > getMaxSize() || structure.getLength() > getMaxSize()) {
                    player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.preview_too_large")
                            .withStyle(ChatFormatting.RED), true);
                } else if (StructurePreview.getCurrentPreviewStructure() != structure) {
                    BlockPos previewPos = StructurePreview.getPreviewPositionFromPlayer(player);
                    StructurePreview.showPreview(structure, previewPos, player.getDirection().getOpposite());
                }
            }
        }
    }

    private void saveStructure(Player player) {
        SelectionData data = getSelectionData(player);
        if (data.point1 == null || data.point2 == null) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.no_area_selected").withStyle(ChatFormatting.RED), true);
            return;
        }



        BlockPos minPos = new BlockPos(
                Math.min(data.point1.getX(), data.point2.getX()),
                Math.min(data.point1.getY(), data.point2.getY()),
                Math.min(data.point1.getZ(), data.point2.getZ())
        );

        BlockPos maxPos = new BlockPos(
                Math.max(data.point1.getX(), data.point2.getX()),
                Math.max(data.point1.getY(), data.point2.getY()),
                Math.max(data.point1.getZ(), data.point2.getZ())
        );



        boolean success = StructureSaver.saveStructureFromWorld(
                player.level(),
                minPos,
                maxPos,
                "custom_structure_" + System.currentTimeMillis(),
                player.getName().getString(),
                ModConstants.Defaults.DEFAULT_DESCRIPTION,
                "custom",
                player
        );

        if (success) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.structure_saved").withStyle(ChatFormatting.GREEN), true);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

            StructureLoader.loadClientStructures();
            loadStructures();
        } else {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.save_failed").withStyle(ChatFormatting.RED), true);
        }

        data.point1 = null;
        data.point2 = null;
    }

    private void openBuildToolScreen() {
        // Usar a classe cliente para abrir a tela
        try {
            Class.forName("com.Infinity.Nexus.Satisfactory.BuildTool.client.BuildToolClient");
            // Chama o método estático para abrir a tela principal
            Class<?> clientClass = Class.forName("com.Infinity.Nexus.Satisfactory.BuildTool.client.BuildToolClient");
            clientClass.getMethod("openBuildToolScreen").invoke(null);
        } catch (Exception e) {
            // Ignora erros relacionados à classe do cliente não estar disponível
        }
    }

    private InteractionResultHolder<ItemStack> placeStructure(Player player, ItemStack itemstack) {
        StructureLoader.StructureData selectedStructure = getSelectedStructure();
        if (selectedStructure == null) {
            player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.no_structure_selected").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(itemstack);
        }

        if (!canBuildStructure(selectedStructure, player)) {
            return InteractionResultHolder.fail(itemstack);
        }

        BlockPos placementPos = StructurePreview.getPreviewPosition();
        if (placementPos == null) {
            placementPos = StructurePreview.getPreviewPositionFromPlayer(player);
        }
        float rotationY = StructurePreview.getYawFromDirection(StructurePreview.getPreviewDirection());

        StructurePlacementC2SPacket.StructureData packetStructure =
                new StructurePlacementC2SPacket.StructureData(
                        selectedStructure.getName(),
                        selectedStructure.getAuthor() != null ? selectedStructure.getAuthor() : "none",
                        selectedStructure.getDescription() != null ? selectedStructure.getDescription() : "none",
                        selectedStructure.getCategory() != null ? selectedStructure.getCategory() : "none",
                        selectedStructure.getGuiPath(),
                        selectedStructure.getIconPath(),
                        selectedStructure.getTier(),
                        selectedStructure.getSubtier(),
                        selectedStructure.getEnergyConsumption(),
                        selectedStructure.isOnlyItems(),
                        selectedStructure.getRequiredItems(),
                        selectedStructure.getPattern()
                );

        int zoopCount = StructurePreview.getZoopCount();
        net.minecraft.core.Direction zoopDir = StructurePreview.getZoopDirection();

        for (int i = 0; i < zoopCount; i++) {
            BlockPos currentPos = placementPos;
            if (zoopDir != null && i > 0) {
                int sizeInDir = StructurePreview.getStructureSizeInDirection(selectedStructure, StructurePreview.getPreviewDirection(), zoopDir);
                currentPos = placementPos.relative(zoopDir, i * sizeInDir);
            }
            ModMessages.sendToServer(new StructurePlacementC2SPacket(
                    packetStructure,
                    currentPos,
                    rotationY,
                    null, // Pass null to prevent server-side zoop loop
                    1     // Single placement
            ));
        }

        player.displayClientMessage(Component.translatable("message.infinity_nexus.creativity.building_structure", selectedStructure.getName()).withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResultHolder.success(itemstack);
    }

    public static void loadStructures() {
        availableStructures = StructureLoader.getClientStructures();
    }

    public static void cycleStructures(Player player) {
        if (currentMode != BuildMode.CONSTRUCTION) return;

        if (availableStructures == null || availableStructures.isEmpty()) {
            loadStructures();
        }

        if (availableStructures == null || availableStructures.isEmpty()) return;

        StructureLoader.StructureData current = availableStructures.get(selectedStructureIndex);
        String guiPath = current.getGuiPath();
        if (guiPath == null) return;

        // Extrai Categoria e Tipo (ex: "Foundations > Stone")
        String categoryType = guiPath;
        if (guiPath.contains(" > ")) {
            String[] parts = guiPath.split(" > ");
            if (parts.length >= 2) {
                categoryType = parts[0] + " > " + parts[1];
            }
        }

        // Encontra todos os índices das estruturas relacionadas
        List<Integer> relatedIndices = new ArrayList<>();
        for (int i = 0; i < availableStructures.size(); i++) {
            StructureLoader.StructureData s = availableStructures.get(i);
            String sPath = s.getGuiPath();
            if (sPath != null && sPath.startsWith(categoryType)) {
                relatedIndices.add(i);
            }
        }

        if (relatedIndices.size() <= 1) return;

        // Encontra o índice atual dentro da lista de relacionados e avança
        int currentInRelated = relatedIndices.indexOf(selectedStructureIndex);
        int nextInRelated = (currentInRelated + 1) % relatedIndices.size();
        selectedStructureIndex = relatedIndices.get(nextInRelated);

        // Atualiza o preview e informa o jogador
        StructurePreview.updateFromCreativeOrb(player);
        StructureLoader.StructureData next = availableStructures.get(selectedStructureIndex);
        player.displayClientMessage(Component.translatable("message.satisfactory_build_tool.structure_cycled", next.getName())
                .withStyle(ChatFormatting.GREEN), true);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.5f);
    }


    public static StructureLoader.StructureData getSelectedStructure() {
        if (availableStructures == null || availableStructures.isEmpty()) {
            return null;
        }
        return availableStructures.get(selectedStructureIndex);
    }
    /**
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        if(Config.enable_creativity_orb) {
            tooltip.add(Component.translatable("tooltip.infinity_nexus_mod.orb.title"));
            tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_cycle").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_save_area").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_limit", getMaxSize(), getMaxVolume()).withStyle(ChatFormatting.GOLD));

            // Color Info
            int colorIndex = getColor(stack);
            String colorName = (Config.color_blocks != null && Config.color_blocks.size() > colorIndex) ? Config.color_blocks.get(colorIndex) : "Unknown";
            String displayColorName = colorName.contains(":") ? colorName.split(":")[1].replace("_concrete", "").replace("_", " ") : colorName;
            if (displayColorName.length() > 0) {
                displayColorName = displayColorName.substring(0, 1).toUpperCase() + displayColorName.substring(1);
            }
            tooltip.add(Component.translatable("tooltip.satisfactory_build_tool.selected_color", displayColorName).withStyle(ChatFormatting.LIGHT_PURPLE));

            // Material Info
            String selectedMaterial = getMaterial(stack);
            if (selectedMaterial != null && !selectedMaterial.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.satisfactory_build_tool.selected_material", selectedMaterial).withStyle(ChatFormatting.AQUA));
            }


            if (getSelectedStructure() != null) {
                StructureLoader.StructureData structure = getSelectedStructure();
                tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_structure", structure.getName()).withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_size", structure.getWidth(), structure.getLength(), structure.getHeight()).withStyle(ChatFormatting.GREEN));

                // Tier and Cost Info
                if (structure.getTier() > 0) {
                    tooltip.add(Component.translatable("tooltip.satisfactory_build_tool.tier", structure.getTier()).withStyle(ChatFormatting.GOLD));
                }
                if (structure.isOnlyItems()) {
                    tooltip.add(Component.translatable("tooltip.satisfactory_build_tool.only_items_required", structure.isOnlyItems()).withStyle(ChatFormatting.BLUE));
                }

                if (structure.getWidth() > getMaxSize() || structure.getHeight() > getMaxSize() || structure.getLength() > getMaxSize()) {
                    tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_too_large").withStyle(ChatFormatting.RED));
                }
            } else {
                tooltip.add(Component.translatable("tooltip.infinity_nexus.creativity_no_structure").withStyle(ChatFormatting.RED));
            }

            tooltip.add(Component.translatable("tooltip.infinity_nexus.pressShift"));
            if (Screen.hasShiftDown()) {
                tooltip.add(Component.translatable("tooltip.satisfactory_build_tool.fake_player_info").withStyle(ChatFormatting.WHITE));
            }
        }

        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }
    **/

    public static int getColor(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getInt("Color");
    }

    public static void setColor(ItemStack stack, int color) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("Color", color));
    }

    public static String getMaterial(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("Material");
    }

    public static void setMaterial(ItemStack stack, String materialName) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("Material", materialName));
    }
}