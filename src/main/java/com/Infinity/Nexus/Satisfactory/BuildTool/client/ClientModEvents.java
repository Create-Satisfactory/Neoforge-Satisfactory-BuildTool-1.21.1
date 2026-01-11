package com.Infinity.Nexus.Satisfactory.BuildTool.client;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        StructureLoader.loadClientStructures();
        ClientStructureTracker.load();
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.ROTATE_KEY);
        event.register(KeyBindings.FIX_KEY);
        event.register(KeyBindings.ZOOP_KEY);
        event.register(KeyBindings.DESTRUCTION_MODE_KEY);
        event.register(KeyBindings.CYCLE_STRUCTURE_KEY);
        event.register(KeyBindings.MOVE_UP_KEY);
        event.register(KeyBindings.MOVE_DOWN_KEY);
        event.register(KeyBindings.MOVE_NORTH_KEY);
        event.register(KeyBindings.MOVE_SOUTH_KEY);
        event.register(KeyBindings.MOVE_WEST_KEY);
        event.register(KeyBindings.MOVE_EAST_KEY);
    }

    @SubscribeEvent
    public static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SatisfactoryBuildTool.MOD_ID, "build_tool_overlay"), BuildToolOverlay.INSTANCE::render);
    }
}
