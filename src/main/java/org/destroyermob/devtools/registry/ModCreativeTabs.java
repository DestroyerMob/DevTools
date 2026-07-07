package org.destroyermob.devtools.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.devtools.DevTools;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DevTools.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEV_TOOLS =
            CREATIVE_TABS.register("dev_tools", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.devtools"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.LOOTR_DEV_TOOL.get().createDefaultStack())
                    .displayItems((parameters, output) -> output.accept(ModItems.LOOTR_DEV_TOOL.get().createDefaultStack()))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
