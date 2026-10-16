package com.Infinity.Nexus.Satisfactory.BuildTool.commands;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.PlayerProgression;
import com.Infinity.Nexus.Satisfactory.BuildTool.progression.ProgressionHandler;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureSyncUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Structures {
    public Structures(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structure")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("progression")
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("structureId", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    StructureLoader.getServerStructures().forEach(s -> builder.suggest("\"" + s.getName() + "\""));
                                                    return builder.buildFuture();
                                                })
                                                .executes(this::unlockStructure))))
                        .then(Commands.literal("setTier")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                                .executes(this::setTier))))
                        .then(Commands.literal("completeSubtier")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("subtier", StringArgumentType.string())
                                                .executes(this::completeSubtier))))
                        .then(Commands.literal("sync")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::syncProgression)))
                        .then(Commands.literal("material")
                                .then(Commands.literal("unlock")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("materialName", StringArgumentType.string())
                                                        .suggests((context, builder) -> {
                                                            Config.material_blocks.forEach(m -> builder.suggest(m.name));
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(this::unlockMaterial))))
                                .then(Commands.literal("list")
                                        .executes(this::listMaterials)))
                        .then(Commands.literal("reload")
                                .executes(this::reloadStructures))
                )
        );
    }

    private int reloadStructures(CommandContext<CommandSourceStack> context) {
        StructureLoader.loadStructures(context.getSource().getServer().getResourceManager());
        for (ServerPlayer onlinePlayer : context.getSource().getServer().getPlayerList().getPlayers()) {
            StructureSyncUtils.syncStructuresToPlayer(onlinePlayer);
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.satisfactory_build_tool.reload_success"), true);
        return 1;
    }

    private int unlockStructure(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            String structureId = StringArgumentType.getString(context, "structureId");

            StructureLoader.StructureData structure = StructureLoader.getServerStructure(structureId);
            if (structure == null) {
                context.getSource().sendFailure(Component.translatable("command.satisfactory_build_tool.structure_not_found", structureId));
                return 0;
            }

            PlayerProgression progression = ProgressionHandler.getProgression(player);
            // Store the canonical structure name so case-sensitive checks always match
            progression.unlockStructure(structure.getName());
            ProgressionHandler.save(progression);

            // Sync newly unlocked structure to all online members of the player's FTB Team
            StructureSyncUtils.syncStructuresToTeam(player);

            context.getSource().sendSuccess(() -> Component.translatable("command.satisfactory_build_tool.unlock_success", structure.getName(), player.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int setTier(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            int tier = IntegerArgumentType.getInteger(context, "tier");

            PlayerProgression progression = ProgressionHandler.getProgression(player);
            progression.setTier(tier);
            ProgressionHandler.save(progression);

            StructureSyncUtils.syncStructuresToTeam(player);

            context.getSource().sendSuccess(() -> Component.translatable("command.satisfactory_build_tool.set_tier_success", tier, player.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int completeSubtier(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            String subtier = StringArgumentType.getString(context, "subtier");

            PlayerProgression progression = ProgressionHandler.getProgression(player);
            progression.addCompletedSubtier(subtier);
            ProgressionHandler.save(progression);

            StructureSyncUtils.syncStructuresToTeam(player);

            context.getSource().sendSuccess(() -> Component.translatable("command.satisfactory_build_tool.complete_subtier_success", subtier, player.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int syncProgression(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            StructureSyncUtils.syncStructuresToTeam(player);
            context.getSource().sendSuccess(() -> Component.translatable("command.satisfactory_build_tool.sync_success", player.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int unlockMaterial(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            String materialName = StringArgumentType.getString(context, "materialName");

            boolean found = Config.material_blocks.stream()
                    .anyMatch(m -> m.name.equals(materialName));

            if (!found) {
                context.getSource().sendFailure(Component.literal("Material '" + materialName + "' not found in config"));
                return 0;
            }

            PlayerProgression progression = ProgressionHandler.getProgression(player);
            progression.unlockMaterial(materialName);
            ProgressionHandler.save(progression);

            context.getSource().sendSuccess(() ->
                            Component.translatable("command.satisfactory_build_tool.material_unlocked", materialName, player.getName().getString()),
                    true);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int listMaterials(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Available materials:"), false);
        Config.material_blocks.forEach(m ->
                context.getSource().sendSuccess(() ->
                                Component.literal(" - " + m.name + " (" + m.blockId + ")"),
                        false)
        );
        return 1;
    }
}