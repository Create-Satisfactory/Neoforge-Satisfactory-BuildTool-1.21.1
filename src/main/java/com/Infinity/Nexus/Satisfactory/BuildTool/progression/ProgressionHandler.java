package com.Infinity.Nexus.Satisfactory.BuildTool.progression;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ftb.mods.ftbquests.api.QuestFile;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProgressionHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, PlayerProgression> CACHE = new HashMap<>();
    private static final String FOLDER_PATH = FMLPaths.CONFIGDIR.get().resolve("InfinityNexus/progression").toString();

    /**
     * Resolves the FTB Team UUID for a player so all teammates share the same progression instance.
     * Falls back to the player's own UUID if FTB Teams/Quests is not active.
     */
    public static UUID getEffectiveTeamId(Player player) {
        TeamData teamData = getFTBTeamData(player);
        if (teamData != null && teamData.getTeamId() != null) {
            return teamData.getTeamId();
        }
        return player.getUUID();
    }

    public static PlayerProgression getProgression(Player player) {
        return getProgression(getEffectiveTeamId(player));
    }

    public static PlayerProgression getProgression(UUID teamOrPlayerId) {
        // If a player UUID was passed on the server, try to resolve their FTB Team UUID first
        if (ServerQuestFile.INSTANCE != null) {
            TeamData td = ServerQuestFile.INSTANCE.getNullableTeamData(teamOrPlayerId);
            if (td != null && td.getTeamId() != null) {
                teamOrPlayerId = td.getTeamId();
            }
        }
        if (CACHE.containsKey(teamOrPlayerId)) {
            return CACHE.get(teamOrPlayerId);
        }
        return load(teamOrPlayerId);
    }

    /**
     * Authoritative check for whether a player's team has unlocked a structure.
     * Checks both the shared TeamProgression JSON and live FTB Quests Milestone completions.
     */
    public static boolean isStructureUnlocked(Player player, StructureLoader.StructureData structure) {
        if (structure == null) return false;
        if (player.isCreative()) return true;
        if ("custom".equalsIgnoreCase(structure.getCategory()) || structure.isLocalStructure()) return true;

        // 1. Check shared TeamProgression file (manual /structure unlocks & defaults)
        PlayerProgression progression = getProgression(player);
        if (progression.isStructureUnlocked(structure.getName())) {
            return true;
        }

        // 2. Check FTB Quests TeamData for Milestone / Tag / Tier completion
        if (isUnlockedViaFTBQuests(player, structure)) {
            return true;
        }

        // 3. Fallback to manual tier/subtier values in TeamProgression
        if (structure.getTier() > 0 && progression.getTier() >= structure.getTier()) {
            String sub = structure.getSubtier();
            return sub == null || sub.isEmpty() || progression.hasCompletedSubtier(sub);
        }

        return false;
    }

    /**
     * Checks if a Customizer material is unlocked for the player's team (via config/command or FTB Quest tag).
     */
    public static boolean isMaterialUnlocked(Player player, String materialName) {
        if (player.isCreative()) return true;

        PlayerProgression progression = getProgression(player);
        if (progression.isMaterialUnlocked(materialName)) {
            return true;
        }

        TeamData teamData = getFTBTeamData(player);
        QuestFile questFile = getQuestFile(player);
        if (teamData == null || questFile == null) return false;

        String targetTag = "unlock_material:" + materialName.toLowerCase();
        AtomicBoolean unlocked = new AtomicBoolean(false);

        questFile.forAllQuests(quest -> {
            if (!unlocked.get() && teamData.isCompleted(quest)) {
                for (String tag : quest.getTags()) {
                    if (tag.equalsIgnoreCase(targetTag)) {
                        unlocked.set(true);
                        break;
                    }
                }
            }
        });

        return unlocked.get();
    }

    private static boolean isUnlockedViaFTBQuests(Player player, StructureLoader.StructureData structure) {
        TeamData teamData = getFTBTeamData(player);
        QuestFile questFile = getQuestFile(player);
        if (teamData == null || questFile == null) return false;

        String structName = structure.getName().trim();
        String structSlug = structName.toLowerCase().replace(" ", "_");
        String unlockTag1 = "unlock:" + structName.toLowerCase();
        String unlockTag2 = "unlock:" + structSlug;
        String subtier = structure.getSubtier() != null ? structure.getSubtier().trim() : "";

        AtomicBoolean unlocked = new AtomicBoolean(false);

        // A. Check all quests in FTB Quests for matching tags, titles, or hex IDs
        questFile.forAllQuests(quest -> {
            if (unlocked.get() || !teamData.isCompleted(quest)) return;

            // 1. Check explicit tags (e.g., "unlock:constructor" or a tag matching "subtier")
            for (String tag : quest.getTags()) {
                if (tag.equalsIgnoreCase(unlockTag1) ||
                        tag.equalsIgnoreCase(unlockTag2) ||
                        (!subtier.isEmpty() && tag.equalsIgnoreCase(subtier))) {
                    unlocked.set(true);
                    return;
                }
            }

            // 2. Check if the structure's "subtier" field matches the completed Milestone's Title or Hex ID
            if (!subtier.isEmpty()) {
                String questTitle = quest.getTitle().getString().trim();
                String questHexId = Quest.getCodeString(quest.id);
                if (questTitle.equalsIgnoreCase(subtier) || questHexId.equalsIgnoreCase(subtier)) {
                    unlocked.set(true);
                }
            }
        });

        if (unlocked.get()) return true;

        // B. If the structure has no specific subtier, check if its Tier chapter is unlocked
        if (subtier.isEmpty() && structure.getTier() > 0) {
            String tierSearch = "tier " + structure.getTier();
            AtomicBoolean tierUnlocked = new AtomicBoolean(false);

            questFile.forAllChapters(chapter -> {
                if (tierUnlocked.get()) return;
                String chapTitle = chapter.getTitle().getString().toLowerCase();
                if (chapTitle.contains(tierSearch)) {
                    for (Quest q : chapter.getQuests()) {
                        if (teamData.canStartTasks(q) || teamData.isCompleted(q)) {
                            tierUnlocked.set(true);
                            break;
                        }
                    }
                }
            });
            return tierUnlocked.get();
        }

        return false;
    }

    /**
     * Computes a hash of the team's completed quests so ModEvents can detect new HUB Milestone completions.
     */
    public static int getTeamQuestCompletionHash(ServerPlayer player) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return 0;
        TeamData data = file.getOrCreateTeamData(player);

        final int[] hash = { data.getTeamId().hashCode() };
        file.forAllQuests(quest -> {
            if (data.isCompleted(quest)) {
                hash[0] = 31 * hash[0] + Long.hashCode(quest.id);
            }
        });
        return hash[0];
    }

    @Nullable
    private static TeamData getFTBTeamData(Player player) {
        if (player.level().isClientSide()) {
            return ClientQuestFile.exists() ? ClientQuestFile.INSTANCE.selfTeamData : null;
        } else if (player instanceof ServerPlayer serverPlayer && ServerQuestFile.INSTANCE != null) {
            return ServerQuestFile.INSTANCE.getOrCreateTeamData(serverPlayer);
        }
        return null;
    }

    @Nullable
    private static QuestFile getQuestFile(Player player) {
        if (player.level().isClientSide()) {
            return ClientQuestFile.exists() ? ClientQuestFile.INSTANCE : null;
        } else {
            return ServerQuestFile.INSTANCE;
        }
    }

    public static PlayerProgression load(UUID teamId) {
        File folder = new File(FOLDER_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, teamId.toString() + ".json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                PlayerProgression progression = GSON.fromJson(reader, PlayerProgression.class);
                if (progression != null) {
                    progression.ensureDefaults();
                    CACHE.put(teamId, progression);
                    return progression;
                }
            } catch (IOException e) {
                SatisfactoryBuildTool.LOGGER.error("Failed to load progression for team {}", teamId, e);
            }
        }

        PlayerProgression newProgression = new PlayerProgression(teamId);
        CACHE.put(teamId, newProgression);
        save(newProgression);
        return newProgression;
    }

    public static void save(PlayerProgression progression) {
        File folder = new File(FOLDER_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, progression.getPlayer().toString() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(progression, writer);
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save progression for team {}", progression.getPlayer(), e);
        }
    }

    public static void clearCache(UUID id) {
        CACHE.remove(id);
    }
}