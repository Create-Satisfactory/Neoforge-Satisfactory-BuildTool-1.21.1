package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PlacedStructure(UUID id, String structureName, BlockPos minPos, BlockPos maxPos, BlockPos startPos, float rotation, ResourceKey<Level> level) {
}
