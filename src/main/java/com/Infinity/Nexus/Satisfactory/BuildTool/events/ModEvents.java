package com.Infinity.Nexus.Satisfactory.BuildTool.events;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID)
public class ModEvents {

    private static final Map<UUID, Integer> LAST_TEAM_QUEST_HASH = new HashMap<>();
    private static int questCheckTicker = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PlaceStructure.tick();
        StructureDestroyer.tick();

        // Every 20 ticks (1 second), check if any online player's FTB Team completed a Milestone
        if (++questCheckTicker >= 20) {
            questCheckTicker = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                int currentHash = ProgressionHandler.getTeamQuestCompletionHash(player);
                Integer previousHash = LAST_TEAM_QUEST_HASH.put(player.getUUID(), currentHash);
                if (previousHash != null && previousHash != currentHash) {
                    StructureSyncUtils.syncStructuresToPlayer(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        SatisfactoryBuildTool.LOGGER.info(TranslationHelper.translateOrEmpty(ModConstants.TranslationKeys.LOG_LOADING_STRUCTURES).getString());
        StructureLoader.loadStructures(event.getServer().getResourceManager());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            LAST_TEAM_QUEST_HASH.put(serverPlayer.getUUID(), ProgressionHandler.getTeamQuestCompletionHash(serverPlayer));
            StructureSyncUtils.syncStructuresToPlayer(serverPlayer);
            ServerStructureTracker.syncAllToPlayer(serverPlayer);
        }
    }
}