package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PatternDecalRenderer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DECAL_CACHE_FILE = Paths.get("config/InfinityNexus/structures/client/pattern_decals.json");
    private static final int FULL_BRIGHT = 0xF000F0;

    // 8-second grace period so decals are never pruned while PlaceStructure is still animating blocks
    private static final long BUILD_ANIMATION_GRACE_MS = 8000L;

    public record PlacedDecal(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            String directionName,
            String patternKey,
            int secondaryColor,
            long createdAtMs
    ) {
        public String posKey() {
            return minX + "," + minY + "," + minZ + ":" + maxX + "," + maxY + "," + maxZ;
        }
    }

    private static final Map<String, PlacedDecal> PLACED_DECALS = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    public static void load() {
        loaded = true;
        PLACED_DECALS.clear();
        try {
            if (Files.exists(DECAL_CACHE_FILE)) {
                try (FileReader reader = new FileReader(DECAL_CACHE_FILE.toFile())) {
                    Type listType = new TypeToken<List<PlacedDecal>>() {}.getType();
                    List<PlacedDecal> list = GSON.fromJson(reader, listType);
                    if (list != null) {
                        for (PlacedDecal d : list) {
                            PLACED_DECALS.put(d.posKey(), d);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to load pattern decals", e);
        }
    }

    public static void save() {
        try {
            if (!Files.exists(DECAL_CACHE_FILE.getParent())) {
                Files.createDirectories(DECAL_CACHE_FILE.getParent());
            }
            try (FileWriter writer = new FileWriter(DECAL_CACHE_FILE.toFile())) {
                GSON.toJson(new ArrayList<>(PLACED_DECALS.values()), writer);
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save pattern decals", e);
        }
    }

    /**
     * Called from BuildTool on client click. Computes exact rotated minPos/maxPos from the active pattern.
     */
    public static void onClientStructurePlaced(
            StructureLoader.StructureData structure,
            BlockPos startPos,
            Direction direction,
            @Nullable String patternKey,
            int colorIndex) {

        if (structure == null || startPos == null) return;

        List<List<List<String>>> pat = structure.getPattern();
        int h = (pat != null && !pat.isEmpty()) ? pat.size() : structure.getHeight();
        int l = (pat != null && !pat.isEmpty() && !pat.get(0).isEmpty()) ? pat.get(0).size() : structure.getLength();
        int w = (pat != null && !pat.isEmpty() && !pat.get(0).isEmpty() && !pat.get(0).get(0).isEmpty())
                ? pat.get(0).get(0).size()
                : structure.getWidth();

        if (w <= 0 || l <= 0 || h <= 0) return;

        BlockPos p1 = StructureGeometryHelper.getRotatedBlockPos(startPos, 0, 0, 0, w, l, direction);
        BlockPos p2 = StructureGeometryHelper.getRotatedBlockPos(startPos, w - 1, h - 1, l - 1, w, l, direction);

        BlockPos minPos = new BlockPos(
                Math.min(p1.getX(), p2.getX()),
                Math.min(p1.getY(), p2.getY()),
                Math.min(p1.getZ(), p2.getZ())
        );
        BlockPos maxPos = new BlockPos(
                Math.max(p1.getX(), p2.getX()),
                Math.max(p1.getY(), p2.getY()),
                Math.max(p1.getZ(), p2.getZ())
        );

        registerStructureDecal(minPos, maxPos, direction, patternKey, colorIndex);
    }

    /**
     * Registers or updates a decal using the exact minPos and maxPos of a placed structure.
     */
    public static void registerStructureDecal(
            BlockPos minPos,
            BlockPos maxPos,
            Direction direction,
            @Nullable String patternKey,
            int colorIndex) {

        if (!loaded) load();
        if (minPos == null || maxPos == null) return;

        String key = minPos.getX() + "," + minPos.getY() + "," + minPos.getZ() + ":"
                + maxPos.getX() + "," + maxPos.getY() + "," + maxPos.getZ();

        if (patternKey == null || patternKey.isEmpty() || patternKey.equalsIgnoreCase("remove")) {
            if (PLACED_DECALS.remove(key) != null) {
                save();
            }
            return;
        }

        int secondaryColor = 0xFFFA9549;
        CustomizerManager.SwatchDef swatch = CustomizerManager.getSwatch(colorIndex);
        if (swatch != null) {
            secondaryColor = swatch.getSecondaryColorInt();
        }

        PLACED_DECALS.put(key, new PlacedDecal(
                minPos.getX(), minPos.getY(), minPos.getZ(),
                maxPos.getX(), maxPos.getY(), maxPos.getZ(),
                direction.getName(), patternKey, secondaryColor,
                System.currentTimeMillis()
        ));
        save();
    }

    /**
     * Renders decals on all placed structures in the world.
     */
    public static void renderWorldDecals(PoseStack poseStack, Camera camera) {
        if (!loaded) load();
        if (PLACED_DECALS.isEmpty()) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Prune dismantled structures ONLY after the 8-second build animation grace window has passed!
        List<PlacedStructure> trackedStructures = ClientStructureTracker.getFormattedStructures(player.level());
        if (!trackedStructures.isEmpty()) {
            Set<String> validKeys = new HashSet<>();
            for (PlacedStructure ps : trackedStructures) {
                String k = ps.minPos().getX() + "," + ps.minPos().getY() + "," + ps.minPos().getZ() + ":"
                        + ps.maxPos().getX() + "," + ps.maxPos().getY() + "," + ps.maxPos().getZ();
                validKeys.add(k);
            }
            boolean removedAny = PLACED_DECALS.entrySet().removeIf(entry ->
                    (now - entry.getValue().createdAtMs() > BUILD_ANIMATION_GRACE_MS) && !validKeys.contains(entry.getKey())
            );
            if (removedAny) {
                save();
            }
        }

        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x(), -camPos.y(), -camPos.z());

        BlockPos playerPos = player.blockPosition();
        for (PlacedDecal decal : PLACED_DECALS.values()) {
            BlockPos center = new BlockPos(
                    (decal.minX() + decal.maxX()) / 2,
                    decal.maxY(),
                    (decal.minZ() + decal.maxZ()) / 2
            );
            if (center.distSqr(playerPos) > 80 * 80) continue;

            // After the build animation finishes, skip rendering if the structure was broken
            if (now - decal.createdAtMs() > BUILD_ANIMATION_GRACE_MS && player.level().getBlockState(center).isAir()) {
                continue;
            }

            Direction dir = Direction.byName(decal.directionName());
            if (dir == null) dir = Direction.NORTH;

            renderSurfaceDecal(
                    poseStack, bufferSource,
                    decal.minX(), decal.maxY() + 1.01f, decal.minZ(),
                    decal.maxX() - decal.minX() + 1,
                    decal.maxZ() - decal.minZ() + 1,
                    dir, decal.patternKey(), decal.secondaryColor(), 0.90f
            );
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    /**
     * Renders the active pattern decal on the Hologram Preview before placement.
     */
    public static void renderHologramDecal(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockPos startPos,
            int rotatedWidth,
            int height,
            int rotatedLength,
            Direction direction,
            @Nullable String patternKey,
            int colorIndex,
            boolean isInvalid) {

        if (patternKey == null || patternKey.isEmpty() || patternKey.equalsIgnoreCase("remove")) {
            return;
        }
        if (rotatedWidth <= 0 || rotatedLength <= 0 || height <= 0) {
            return;
        }

        int secondaryColor = 0xFFFA9549;
        if (isInvalid) {
            secondaryColor = 0xFFFF4433;
        } else {
            CustomizerManager.SwatchDef swatch = CustomizerManager.getSwatch(colorIndex);
            if (swatch != null) {
                secondaryColor = swatch.getSecondaryColorInt();
            }
        }

        renderSurfaceDecal(
                poseStack, bufferSource,
                startPos.getX(), startPos.getY() + height + 0.006f, startPos.getZ(),
                rotatedWidth, rotatedLength,
                direction, patternKey, secondaryColor, 0.82f
        );
    }

    /**
     * Maps a 16x16 virtual FICSIT stencil canvas (-8..+8) onto the top face of any W x L structure.
     */
    private static void renderSurfaceDecal(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float minX, float topY, float minZ,
            float widthX, float lengthZ,
            Direction dir,
            String patternKey,
            int rgbColor,
            float alpha) {

        float centerX = minX + widthX / 2.0f;
        float centerZ = minZ + lengthZ / 2.0f;

        poseStack.pushPose();
        poseStack.translate(centerX, topY, centerZ);

        float yawDeg = switch (dir) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f; // NORTH
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.scale(widthX / 16.0f, lengthZ / 16.0f, 1.0f);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.debugQuads());
        float r = ((rgbColor >> 16) & 0xFF) / 255.0f;
        float g = ((rgbColor >> 8) & 0xFF) / 255.0f;
        float b = (rgbColor & 0xFF) / 255.0f;

        drawPatternGeometry2D(poseStack, bufferSource, vc, patternKey, r, g, b, alpha, rgbColor);

        poseStack.popPose();
    }

    private static void drawPatternGeometry2D(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            VertexConsumer vc,
            String patKey,
            float r, float g, float b, float a,
            int rgbColor) {

        if (patKey.startsWith("num_")) {
            String digit = patKey.substring(4);
            drawTextDecal(poseStack, bufferSource, digit, 1.35f, rgbColor);
            return;
        }

        boolean prohibited = patKey.startsWith("no_");
        String baseKey = prohibited ? patKey.substring(3) : patKey;

        switch (baseKey) {
            // 1. ARROWS
            case "arrow_up" -> {
                fill2D(poseStack, vc, -1.2f, -4.0f, 1.2f, 6.0f, r, g, b, a);
                fill2D(poseStack, vc, -3.5f, -2.5f, 3.5f, -0.5f, r, g, b, a);
                fill2D(poseStack, vc, -2.0f, -5.0f, 2.0f, -2.5f, r, g, b, a);
                fill2D(poseStack, vc, -0.8f, -6.5f, 0.8f, -5.0f, r, g, b, a);
            }
            case "arrow_left" -> {
                fill2D(poseStack, vc, 1.0f, -2.0f, 3.4f, 6.0f, r, g, b, a);
                fill2D(poseStack, vc, -3.5f, -3.2f, 3.4f, -0.8f, r, g, b, a);
                fill2D(poseStack, vc, -5.5f, -4.8f, -2.5f, 0.8f, r, g, b, a);
            }
            case "arrow_right" -> {
                fill2D(poseStack, vc, -3.4f, -2.0f, -1.0f, 6.0f, r, g, b, a);
                fill2D(poseStack, vc, -3.4f, -3.2f, 3.5f, -0.8f, r, g, b, a);
                fill2D(poseStack, vc, 2.5f, -4.8f, 5.5f, 0.8f, r, g, b, a);
            }
            case "arrow_uturn" -> {
                fill2D(poseStack, vc, 1.5f, -4.5f, 3.8f, 6.0f, r, g, b, a);
                fill2D(poseStack, vc, -3.5f, -5.5f, 3.8f, -3.2f, r, g, b, a);
                fill2D(poseStack, vc, -3.8f, -5.5f, -1.5f, 2.0f, r, g, b, a);
                fill2D(poseStack, vc, -5.2f, 1.5f, -0.1f, 4.2f, r, g, b, a);
            }

            // 2. ICONS
            case "icon_factory" -> {
                fill2D(poseStack, vc, -5.5f, -5.0f, -3.0f, 5.0f, r, g, b, a);
                fill2D(poseStack, vc, -5.5f, 0.0f, 5.5f, 5.0f, r, g, b, a);
                fill2D(poseStack, vc, -2.0f, -2.5f, 1.0f, 0.0f, r, g, b, a);
                fill2D(poseStack, vc, 2.0f, -2.5f, 5.0f, 0.0f, r, g, b, a);
            }
            case "icon_power" -> {
                fill2D(poseStack, vc, -1.0f, -6.0f, 3.0f, -0.5f, r, g, b, a);
                fill2D(poseStack, vc, -3.0f, -1.5f, 3.0f, 1.5f, r, g, b, a);
                fill2D(poseStack, vc, -2.5f, 0.5f, 1.0f, 6.0f, r, g, b, a);
            }
            case "icon_storage" -> {
                outline2D(poseStack, vc, -5.5f, -4.0f, 5.5f, 4.5f, 1.0f, r, g, b, a);
                fill2D(poseStack, vc, -6.0f, -5.0f, 6.0f, -2.5f, r, g, b, a);
                fill2D(poseStack, vc, -1.8f, -1.0f, 1.8f, 1.0f, r, g, b, a);
            }
            case "icon_nuclear" -> {
                fill2D(poseStack, vc, -1.2f, -1.2f, 1.2f, 1.2f, r, g, b, a);
                fill2D(poseStack, vc, -5.0f, -5.0f, -1.8f, -1.8f, r, g, b, a);
                fill2D(poseStack, vc, 1.8f, -5.0f, 5.0f, -1.8f, r, g, b, a);
                fill2D(poseStack, vc, -1.8f, 2.2f, 1.8f, 5.5f, r, g, b, a);
            }
            case "icon_liquid" -> {
                fill2D(poseStack, vc, -1.0f, -5.5f, 1.0f, -2.5f, r, g, b, a);
                fill2D(poseStack, vc, -2.5f, -2.5f, 2.5f, 0.5f, r, g, b, a);
                fill2D(poseStack, vc, -3.8f, 0.5f, 3.8f, 5.0f, r, g, b, a);
            }
            case "icon_cross" -> {
                for (int i = -5; i <= 5; i++) {
                    fill2D(poseStack, vc, i - 1.0f, i - 1.0f, i + 1.0f, i + 1.0f, r, g, b, a);
                    fill2D(poseStack, vc, i - 1.0f, -i - 1.0f, i + 1.0f, -i + 1.0f, r, g, b, a);
                }
            }
            case "icon_pioneer" -> {
                fill2D(poseStack, vc, -2.5f, -5.5f, 2.5f, -0.5f, r, g, b, a);
                fill2D(poseStack, vc, -4.8f, 1.0f, 4.8f, 5.8f, r, g, b, a);
            }
            case "icon_tractor", "icon_truck", "icon_explorer", "icon_cart" -> {
                fill2D(poseStack, vc, -5.0f, -3.5f, 2.5f, 2.5f, r, g, b, a);
                fill2D(poseStack, vc, 2.5f, -1.0f, 5.5f, 2.5f, r, g, b, a);
                fill2D(poseStack, vc, -4.2f, 2.5f, -1.5f, 5.2f, r, g, b, a);
                fill2D(poseStack, vc, 1.5f, 2.5f, 4.2f, 5.2f, r, g, b, a);
            }
            case "icon_parking" -> drawTextDecal(poseStack, bufferSource, "P", 1.35f, rgbColor);

            // 3. LINES
            case "line_center" -> fill2D(poseStack, vc, -0.9f, -7.8f, 0.9f, 7.8f, r, g, b, a);
            case "line_corner" -> {
                fill2D(poseStack, vc, -0.9f, -7.8f, 0.9f, 0.9f, r, g, b, a);
                fill2D(poseStack, vc, -0.9f, -0.9f, 7.8f, 0.9f, r, g, b, a);
            }
            case "line_split" -> {
                fill2D(poseStack, vc, -7.8f, -0.9f, 7.8f, 0.9f, r, g, b, a);
                fill2D(poseStack, vc, -0.9f, -7.8f, 0.9f, 0.9f, r, g, b, a);
            }
            case "line_cross" -> {
                fill2D(poseStack, vc, -7.8f, -0.9f, 7.8f, 0.9f, r, g, b, a);
                fill2D(poseStack, vc, -0.9f, -7.8f, 0.9f, 7.8f, r, g, b, a);
            }
            case "line_side", "zone_line" -> fill2D(poseStack, vc, -7.5f, -7.8f, -5.7f, 7.8f, r, g, b, a);
            case "line_double" -> {
                fill2D(poseStack, vc, -7.5f, -7.8f, -5.7f, 7.8f, r, g, b, a);
                fill2D(poseStack, vc, 5.7f, -7.8f, 7.5f, 7.8f, r, g, b, a);
            }
            case "line_dotted" -> {
                for (float y = -7.2f; y < 7.2f; y += 3.8f) {
                    fill2D(poseStack, vc, -0.9f, y, 0.9f, y + 2.2f, r, g, b, a);
                }
            }

            // 5. PATHS
            case "path_straight", "path_pioneer", "path_cart" -> {
                fill2D(poseStack, vc, -3.5f, -7.8f, 3.5f, 7.8f, r, g, b, a * 0.45f);
                fill2D(poseStack, vc, -4.6f, -7.8f, -3.5f, 7.8f, r, g, b, a);
                fill2D(poseStack, vc, 3.5f, -7.8f, 4.6f, 7.8f, r, g, b, a);
            }
            case "path_corner" -> {
                fill2D(poseStack, vc, -3.5f, -7.8f, 3.5f, 3.5f, r, g, b, a * 0.45f);
                fill2D(poseStack, vc, -3.5f, -3.5f, 7.8f, 3.5f, r, g, b, a * 0.45f);
                fill2D(poseStack, vc, -4.6f, -7.8f, -3.5f, 4.6f, r, g, b, a);
                fill2D(poseStack, vc, -4.6f, 3.5f, 7.8f, 4.6f, r, g, b, a);
            }
            case "path_split" -> {
                fill2D(poseStack, vc, -7.8f, -3.5f, 7.8f, 3.5f, r, g, b, a * 0.45f);
                fill2D(poseStack, vc, -3.5f, -7.8f, 3.5f, 3.5f, r, g, b, a * 0.45f);
                fill2D(poseStack, vc, -7.8f, 3.5f, 7.8f, 4.6f, r, g, b, a);
            }
            case "path_cross" -> {
                fill2D(poseStack, vc, -7.8f, -3.5f, 7.8f, 3.5f, r, g, b, a * 0.45f);
                fill2D(poseStack, vc, -3.5f, -7.8f, 3.5f, 7.8f, r, g, b, a * 0.45f);
            }
            case "path_crosswalk_sm", "path_crosswalk_lg" -> {
                float hw = baseKey.endsWith("lg") ? 5.8f : 3.2f;
                for (float y = -6.5f; y <= 6.0f; y += 3.4f) {
                    fill2D(poseStack, vc, -hw, y, hw, y + 2.0f, r, g, b, a);
                }
            }

            // 6. ZONES
            case "zone_full" -> {
                outline2D(poseStack, vc, -7.5f, -7.5f, 7.5f, 7.5f, 0.9f, r, g, b, a);
                for (float x = -6.0f; x <= 6.0f; x += 3.0f) {
                    fill2D(poseStack, vc, x - 0.7f, -6.8f, x + 0.7f, 6.8f, r, g, b, a * 0.75f);
                }
            }
            case "zone_half" -> {
                outline2D(poseStack, vc, -7.5f, -7.5f, 0.0f, 7.5f, 0.9f, r, g, b, a);
                for (float x = -6.0f; x <= -1.5f; x += 2.5f) {
                    fill2D(poseStack, vc, x - 0.6f, -6.8f, x + 0.6f, 6.8f, r, g, b, a * 0.75f);
                }
            }
            case "zone_quarter" -> {
                outline2D(poseStack, vc, -7.5f, -7.5f, 0.0f, 0.0f, 0.9f, r, g, b, a);
                for (float x = -6.0f; x <= -1.5f; x += 2.5f) {
                    fill2D(poseStack, vc, x - 0.6f, -6.8f, x + 0.6f, -0.7f, r, g, b, a * 0.75f);
                }
            }
        }

        if (prohibited) {
            outline2D(poseStack, vc, -6.8f, -6.8f, 6.8f, 6.8f, 1.1f, r, g, b, a);
            for (int i = -5; i <= 5; i++) {
                fill2D(poseStack, vc, i - 0.9f, i - 0.9f, i + 0.9f, i + 0.9f, r, g, b, a);
            }
        }
    }

    private static void drawTextDecal(PoseStack poseStack, MultiBufferSource bufferSource, String text, float scale, int color) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0f);
        float x = -font.width(text) / 2.0f;
        float y = -font.lineHeight / 2.0f;
        font.drawInBatch(text, x, y, color, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void outline2D(PoseStack poseStack, VertexConsumer vc,
                                  float x1, float y1, float x2, float y2, float thickness,
                                  float r, float g, float b, float a) {
        fill2D(poseStack, vc, x1, y1, x2, y1 + thickness, r, g, b, a);
        fill2D(poseStack, vc, x1, y2 - thickness, x2, y2, r, g, b, a);
        fill2D(poseStack, vc, x1, y1, x1 + thickness, y2, r, g, b, a);
        fill2D(poseStack, vc, x2 - thickness, y1, x2, y2, r, g, b, a);
    }

    private static void fill2D(PoseStack poseStack, VertexConsumer vc,
                               float x1, float y1, float x2, float y2,
                               float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        vc.addVertex(mat, x1, y2, 0.0f).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, 0.0f).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, 0.0f).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, 0.0f).setColor(r, g, b, a);

        vc.addVertex(mat, x1, y1, 0.0f).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, 0.0f).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, 0.0f).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, 0.0f).setColor(r, g, b, a);
    }
}