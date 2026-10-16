package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.client.gui.BuildToolScreen;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.gui.ClearModeScreen;
import net.minecraft.client.Minecraft;

public class BuildToolClient {

    public static void openBuildToolScreen() {
        Minecraft.getInstance().setScreen(new BuildToolScreen());
    }

    public static void openClearModeScreen() {
        Minecraft.getInstance().setScreen(new ClearModeScreen());
    }
}