package com.Infinity.Nexus.Satisfactory.BuildTool.events;


import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.commands.Structures;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.command.ConfigCommand;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID)
public class CommandRegister {
    @SubscribeEvent
    public static void onCommandRegister(final RegisterCommandsEvent event) {
        new Structures(event.getDispatcher());
        ConfigCommand.register(event.getDispatcher());
    }
}