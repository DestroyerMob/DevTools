package org.destroyermob.devtools.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.destroyermob.devtools.compat.lootr.LootrDevToolActions;
import org.destroyermob.devtools.registry.ModDataComponents;
import org.destroyermob.devtools.registry.ModItems;

public class LootrDevToolItem extends Item {
    public static final ResourceLocation DEFAULT_LOOT_TABLE = ResourceLocation.withDefaultNamespace("chests/simple_dungeon");

    public LootrDevToolItem(Properties properties) {
        super(properties);
    }

    public ItemStack createDefaultStack() {
        return create(DEFAULT_LOOT_TABLE);
    }

    public static ItemStack create(ResourceLocation lootTable) {
        ItemStack stack = new ItemStack(ModItems.LOOTR_DEV_TOOL.get());
        stack.set(ModDataComponents.LOOTR_DEV_TOOL_LOOT_TABLE.get(), lootTable);
        return stack;
    }

    public static ResourceLocation lootTable(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOOTR_DEV_TOOL_LOOT_TABLE.get(), DEFAULT_LOOT_TABLE);
    }

    public static boolean is(ItemStack stack) {
        return stack.is(ModItems.LOOTR_DEV_TOOL.get());
    }

    public static boolean canUse(Player player) {
        return player.getAbilities().instabuild
                || player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!ModList.get().isLoaded("lootr")) {
            Player player = context.getPlayer();
            if (player != null && !context.getLevel().isClientSide()) {
                player.displayClientMessage(Component.translatable("message.devtools.lootr_dev_tool_missing_lootr"), true);
            }
            return InteractionResult.FAIL;
        }
        return LootrDevToolActions.useOn(context);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.devtools.lootr_dev_tool.table", lootTable(stack)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.devtools.lootr_dev_tool.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.devtools.lootr_dev_tool.reroll").withStyle(ChatFormatting.GRAY));
    }

    public static void handleLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!is(event.getEntity().getMainHandItem()) || !ModList.get().isLoaded("lootr")) {
            return;
        }
        LootrDevToolActions.handleLeftClick(event);
    }
}
