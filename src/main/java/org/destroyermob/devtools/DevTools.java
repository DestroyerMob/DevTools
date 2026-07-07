package org.destroyermob.devtools;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.destroyermob.devtools.command.DevToolsCommands;
import org.destroyermob.devtools.item.LootrDevToolItem;
import org.destroyermob.devtools.registry.ModCreativeTabs;
import org.destroyermob.devtools.registry.ModDataComponents;
import org.destroyermob.devtools.registry.ModItems;
import org.slf4j.Logger;

@Mod(DevTools.MOD_ID)
public class DevTools {
    public static final String MOD_ID = "devtools";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DevTools(IEventBus modEventBus) {
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::addCreativeTabContents);
        NeoForge.EVENT_BUS.addListener(LootrDevToolItem::handleLeftClick);
        NeoForge.EVENT_BUS.addListener(DevToolsCommands::register);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.LOOTR_DEV_TOOL.get().createDefaultStack());
        }
    }
}
