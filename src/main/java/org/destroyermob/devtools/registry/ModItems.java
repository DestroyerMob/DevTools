package org.destroyermob.devtools.registry;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.destroyermob.devtools.DevTools;
import org.destroyermob.devtools.item.LootrDevToolItem;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DevTools.MOD_ID);

    public static final DeferredItem<LootrDevToolItem> LOOTR_DEV_TOOL = ITEMS.register(
            "lootr_dev_tool",
            () -> new LootrDevToolItem(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
