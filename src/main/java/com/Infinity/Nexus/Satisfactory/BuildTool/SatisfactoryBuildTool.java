package com.Infinity.Nexus.Satisfactory.BuildTool;

import com.Infinity.Nexus.Satisfactory.BuildTool.component.ModDataComponents;
import com.Infinity.Nexus.Satisfactory.BuildTool.config.Config;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.ModItems;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.ModMessages;
import com.Infinity.Nexus.Satisfactory.BuildTool.tab.ModTab;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SatisfactoryBuildTool.MOD_ID)
public class SatisfactoryBuildTool {
    public static final String MOD_ID = "satisfactory_build_tool";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SatisfactoryBuildTool(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);

        ModItems.register(modEventBus);

        ModTab.register(modEventBus);

        ModDataComponents.register(modEventBus);

        modEventBus.register(ModMessages.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }


    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("   §4_____§5_   __§9__________§3_   ______§b_______  __");
        LOGGER.info("  §4/_  _§5/ | / §9/ ____/  _§3/ | / /  _§b/_  __| \\/ /");
        LOGGER.info("   §4/ /§5/  |/ §9/ /_   / /§3/  |/ // /  §b/ /   \\  / ");
        LOGGER.info(" §4_/ /§5/ /|  §9/ __/ _/ /§3/ /|  // /  §b/ /    / /  ");
        LOGGER.info("§4/___§5/_/ |_§9/_/   /___§3/_/ |_/___/ §b/_/    /_/   ");
        LOGGER.info("§b    Infinity Nexus Satisfactory BuildTool");

    }
}
