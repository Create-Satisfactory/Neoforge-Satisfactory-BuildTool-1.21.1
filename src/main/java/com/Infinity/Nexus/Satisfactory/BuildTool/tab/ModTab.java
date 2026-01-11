package com.Infinity.Nexus.Satisfactory.BuildTool.tab;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SatisfactoryBuildTool.MOD_ID);
    public static final Supplier<CreativeModeTab> SATISFACTORY_BUILD_TOOL_TAB = CREATIVE_MODE_TABS.register("satisfactory_build_tool",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.satisfactory_build_tool"))
                    .icon(() -> new ItemStack(ModItems.BUILD_TOOL.get()))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.BUILD_TOOL);
                    })
                    .build());
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
