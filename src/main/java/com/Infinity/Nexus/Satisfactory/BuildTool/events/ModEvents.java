package com.Infinity.Nexus.Satisfactory.BuildTool.events;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.*;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;


@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID)
public class ModEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PlaceStructure.tick();
        StructureDestroyer.tick();
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        SatisfactoryBuildTool.LOGGER.info(TranslationHelper.translateOrEmpty(ModConstants.TranslationKeys.LOG_LOADING_STRUCTURES).getString());
        StructureLoader.loadStructures();
    }


    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SatisfactoryBuildTool.LOGGER.info(
                TranslationHelper.translateWithFallback(
                    ModConstants.TranslationKeys.LOG_SYNCING_STRUCTURES, 
                    "Syncing structures for player %s based on unlocked structures", 
                    serverPlayer.getName().getString()
                ).getString()
            );
            StructureSyncUtils.syncStructuresToPlayer(serverPlayer);
        }
    }
}