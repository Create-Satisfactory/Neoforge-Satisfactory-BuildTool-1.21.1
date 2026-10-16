package com.Infinity.Nexus.Satisfactory.BuildTool.events;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.client.ClientStructureTracker;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ServerStructureTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = SatisfactoryBuildTool.MOD_ID)
public class BuildToolProtectionEvents {

    public static boolean isBlockProtected(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return ClientStructureTracker.findStructureContaining(pos, level) != null;
        } else if (level instanceof ServerLevel serverLevel) {
            return ServerStructureTracker.get(serverLevel).isProtectedBlock(pos);
        }
        return false;
    }

    /**
     * 1. Halts mining progress in Survival (acts like Bedrock).
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getPosition().isEmpty()) return;
        BlockPos pos = event.getPosition().get();
        Player player = event.getEntity();

        if (isBlockProtected(player.level(), pos)) {
            event.setNewSpeed(0.0F);
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("⚠ FICSIT Property: Use the Build Tool (Destruction Mode) to dismantle!")
                            .withStyle(ChatFormatting.GOLD),
                    true
            );
        }
    }

    /**
     * 2. Cancels direct block breaking (including Creative Mode left-clicks and block breakers).
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && isBlockProtected(level, event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(
                    Component.literal("⚠ FICSIT Property: Use the Build Tool (Destruction Mode) to dismantle!")
                            .withStyle(ChatFormatting.GOLD),
                    true
            );
        }
    }

    /**
     * 3. Protects placed structures from Creeper / TNT explosions.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> isBlockProtected(level, pos));
    }

    /**
     * 4. Prevents Create Wrenches from dismantling individual components inside a Build Tool structure.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (!isBlockProtected(level, pos)) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (itemId.getPath().contains("wrench")) {
            event.setCanceled(true);
            if (!level.isClientSide) {
                event.getEntity().displayClientMessage(
                        Component.literal("⚠ Cannot wrench FICSIT structures! Use the Build Tool (Destruction Mode).")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
        }
    }
}