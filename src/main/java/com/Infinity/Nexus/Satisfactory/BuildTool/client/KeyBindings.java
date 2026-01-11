package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_TUTORIAL = "key.category." + SatisfactoryBuildTool.MOD_ID;

    public static final KeyMapping ROTATE_KEY = new KeyMapping(
            "key.satisfactory_build_tool.rotate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping FIX_KEY = new KeyMapping(
            "key.satisfactory_build_tool.fix",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping ZOOP_KEY = new KeyMapping(
            "key.satisfactory_build_tool.zoop",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping DESTRUCTION_MODE_KEY = new KeyMapping(
            "key.satisfactory_build_tool.destruction_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping CYCLE_STRUCTURE_KEY = new KeyMapping(
            "key.satisfactory_build_tool.cycle_structure",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping MOVE_UP_KEY = new KeyMapping(
            "key.satisfactory_build_tool.move_up",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping MOVE_DOWN_KEY = new KeyMapping(
            "key.satisfactory_build_tool.move_down",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping MOVE_NORTH_KEY = new KeyMapping(
            "key.satisfactory_build_tool.move_north",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping MOVE_SOUTH_KEY = new KeyMapping(
            "key.satisfactory_build_tool.move_south",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping MOVE_WEST_KEY = new KeyMapping(
            "key.satisfactory_build_tool.move_west",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            KEY_CATEGORY_TUTORIAL
    );

    public static final KeyMapping MOVE_EAST_KEY = new KeyMapping(
            "key.satisfactory_build_tool.move_east",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            KEY_CATEGORY_TUTORIAL
    );
}
