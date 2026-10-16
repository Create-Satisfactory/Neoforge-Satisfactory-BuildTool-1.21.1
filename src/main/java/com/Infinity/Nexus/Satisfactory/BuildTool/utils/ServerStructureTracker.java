package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureCompleteS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureRemoveS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ServerStructureTracker extends SavedData {

    private static final String DATA_NAME = "satisfactory_build_tool_structures";

    // Maps Structure UUID -> PlacedStructure metadata
    private final Map<UUID, PlacedStructure> structuresById = new HashMap<>();
    // Maps Structure UUID -> Exact list of placed BlockPos belonging to that structure
    private final Map<UUID, List<BlockPos>> structureBlocks = new HashMap<>();
    // Fast O(1) lookup: Maps any protected BlockPos -> Parent Structure UUID
    private final Map<BlockPos, UUID> posToStructureId = new HashMap<>();

    public static ServerStructureTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(ServerStructureTracker::new, ServerStructureTracker::load),
                DATA_NAME
        );
    }

    /**
     * Registers a placed structure and locks all of its placed block coordinates.
     */
    public void registerStructure(ServerLevel level, PlacedStructure placed, Collection<BlockPos> placedPositions) {
        List<BlockPos> immutablePositions = placedPositions.stream().map(BlockPos::immutable).toList();
        structuresById.put(placed.id(), placed);
        structureBlocks.put(placed.id(), immutablePositions);

        for (BlockPos pos : immutablePositions) {
            posToStructureId.put(pos, placed.id());
        }
        setDirty();

        // Sync to all players in this level so their Destruction Mode & client protection stay in sync
        for (ServerPlayer player : level.players()) {
            ModMessages.sendToPlayer(new StructureCompleteS2CPacket(placed), player);
        }
    }

    /**
     * Checks if a block coordinate is protected by a Build Tool structure.
     */
    public boolean isProtectedBlock(BlockPos pos) {
        return posToStructureId.containsKey(pos);
    }

    /**
     * Finds the PlacedStructure owning the given BlockPos (or containing it within bounds as fallback).
     */
    @Nullable
    public PlacedStructure getStructureAt(BlockPos pos) {
        UUID id = posToStructureId.get(pos);
        if (id != null && structuresById.containsKey(id)) {
            return structuresById.get(id);
        }
        for (PlacedStructure s : structuresById.values()) {
            if (pos.getX() >= s.minPos().getX() && pos.getX() <= s.maxPos().getX() &&
                    pos.getY() >= s.minPos().getY() && pos.getY() <= s.maxPos().getY() &&
                    pos.getZ() >= s.minPos().getZ() && pos.getZ() <= s.maxPos().getZ()) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public PlacedStructure getStructureById(UUID id) {
        return structuresById.get(id);
    }

    public List<BlockPos> getBlocksForStructure(UUID id) {
        return structureBlocks.getOrDefault(id, Collections.emptyList());
    }

    /**
     * Unregisters a structure when dismantled by the Build Tool and notifies all players.
     */
    @Nullable
    public PlacedStructure removeStructure(ServerLevel level, UUID id) {
        PlacedStructure removed = structuresById.remove(id);
        List<BlockPos> blocks = structureBlocks.remove(id);
        if (blocks != null) {
            for (BlockPos pos : blocks) {
                posToStructureId.remove(pos);
            }
        }
        if (removed != null) {
            setDirty();
            for (ServerPlayer player : level.players()) {
                ModMessages.sendToPlayer(new StructureRemoveS2CPacket(id), player);
            }
        }
        return removed;
    }

    /**
     * Syncs all placed structures in the player's current dimension upon login.
     */
    public static void syncAllToPlayer(ServerPlayer player) {
        ServerStructureTracker tracker = get(player.serverLevel());
        for (PlacedStructure placed : tracker.structuresById.values()) {
            ModMessages.sendToPlayer(new StructureCompleteS2CPacket(placed), player);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (PlacedStructure s : structuresById.values()) {
            CompoundTag st = new CompoundTag();
            st.putUUID("Id", s.id());
            st.putString("Name", s.structureName());
            st.put("MinPos", NbtUtils.writeBlockPos(s.minPos()));
            st.put("MaxPos", NbtUtils.writeBlockPos(s.maxPos()));
            st.put("StartPos", NbtUtils.writeBlockPos(s.startPos()));
            st.putFloat("Rotation", s.rotation());
            st.putString("Dimension", s.level().location().toString());

            ListTag blocksTag = new ListTag();
            for (BlockPos bp : structureBlocks.getOrDefault(s.id(), Collections.emptyList())) {
                blocksTag.add(NbtUtils.writeBlockPos(bp));
            }
            st.put("Blocks", blocksTag);
            list.add(st);
        }
        tag.put("PlacedStructures", list);
        return tag;
    }

    public static ServerStructureTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerStructureTracker tracker = new ServerStructureTracker();
        ListTag list = tag.getList("PlacedStructures", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag st = list.getCompound(i);
            UUID id = st.getUUID("Id");
            String name = st.getString("Name");
            BlockPos minPos = NbtUtils.readBlockPos(st, "MinPos").orElse(BlockPos.ZERO);
            BlockPos maxPos = NbtUtils.readBlockPos(st, "MaxPos").orElse(BlockPos.ZERO);
            BlockPos startPos = NbtUtils.readBlockPos(st, "StartPos").orElse(BlockPos.ZERO);
            float rotation = st.getFloat("Rotation");
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(st.getString("Dimension")));

            PlacedStructure placed = new PlacedStructure(id, name, minPos, maxPos, startPos, rotation, dim);
            tracker.structuresById.put(id, placed);

            List<BlockPos> blocks = new ArrayList<>();
            ListTag blocksTag = st.getList("Blocks", Tag.TAG_INT_ARRAY);
            for (Tag bTag : blocksTag) {
                if (bTag instanceof net.minecraft.nbt.IntArrayTag arr && arr.getAsIntArray().length == 3) {
                    int[] a = arr.getAsIntArray();
                    BlockPos bp = new BlockPos(a[0], a[1], a[2]);
                    blocks.add(bp);
                    tracker.posToStructureId.put(bp, id);
                }
            }
            tracker.structureBlocks.put(id, blocks);
        }
        return tracker;
    }
}