package com.Infinity.Nexus.Satisfactory.BuildTool.item;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SatisfactoryBuildTool.MOD_ID);

    public static final DeferredItem<Item> BUILD_TOOL = ITEMS.register("build_tool", () -> new BuildTool(new Item.Properties().component(DataComponents.UNBREAKABLE, new Unbreakable(true))));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
