package com.Infinity.Nexus.Satisfactory.BuildTool.events;

import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructurePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if(!Config.enable_creativity_orb){
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            if (player.getMainHandItem().getItem() instanceof BuildTool ||
                    player.getOffhandItem().getItem() instanceof BuildTool) {
                StructurePreview.updatePreviewPosition(player);
                BuildTool.onPlayerTick(Minecraft.getInstance().player);
            }
        }
    }
}