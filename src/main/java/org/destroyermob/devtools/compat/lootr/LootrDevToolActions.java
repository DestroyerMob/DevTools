package org.destroyermob.devtools.compat.lootr;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.data.ILootrSavedData;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.neoforge.init.ModBlocks;
import org.destroyermob.devtools.item.LootrDevToolItem;

public final class LootrDevToolActions {
    private LootrDevToolActions() {
    }

    public static InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        if (!LootrDevToolItem.canUse(serverPlayer)) {
            send(serverPlayer, "message.devtools.lootr_dev_tool_no_permission");
            return InteractionResult.FAIL;
        }

        ResourceKey<LootTable> table = tableKey(LootrDevToolItem.lootTable(context.getItemInHand()));
        BlockPos clickedPos = context.getClickedPos();
        if (resolve(level, clickedPos) != null) {
            resetLootrBlock(serverLevel, clickedPos, table, serverPlayer);
            playRerollEffects(serverLevel, clickedPos, serverPlayer);
            send(serverPlayer, "message.devtools.lootr_dev_tool_set", table.location());
            return InteractionResult.SUCCESS;
        }

        BlockPos placePos = placementPos(context);
        if (placePos == null) {
            send(serverPlayer, "message.devtools.lootr_dev_tool_no_room");
            return InteractionResult.FAIL;
        }

        BlockState chestState = orientForPlayer(ModBlocks.CHEST.get().defaultBlockState(), serverPlayer);
        serverLevel.setBlock(placePos, chestState, 11);
        configureLootrBlock(serverLevel, placePos, table, serverPlayer);
        playPlaceEffects(serverLevel, placePos, serverPlayer);
        send(serverPlayer, "message.devtools.lootr_dev_tool_placed", table.location());
        return InteractionResult.SUCCESS;
    }

    public static void handleLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ILootrBlockEntity lootr = resolve(level, pos);
        if (lootr == null) {
            return;
        }

        event.setCanceled(true);
        Player player = event.getEntity();
        if (level.isClientSide()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!LootrDevToolItem.canUse(serverPlayer)) {
            send(serverPlayer, "message.devtools.lootr_dev_tool_no_permission");
            return;
        }

        ResourceKey<LootTable> table = lootr.getInfoLootTable();
        if (table == null) {
            table = tableKey(LootrDevToolItem.lootTable(player.getMainHandItem()));
        }
        resetLootrBlock(serverLevel, pos, table, serverPlayer);
        playRerollEffects(serverLevel, pos, serverPlayer);
        send(serverPlayer, "message.devtools.lootr_dev_tool_rerolled", table.location());
    }

    private static BlockPos placementPos(UseOnContext context) {
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        BlockPos pos = placeContext.getClickedPos();
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).canBeReplaced(placeContext)) {
            return null;
        }
        return pos;
    }

    private static BlockState orientForPlayer(BlockState state, ServerPlayer player) {
        Direction facing = player.getDirection().getOpposite();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return state;
    }

    private static void resetLootrBlock(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table, ServerPlayer player) {
        ILootrBlockEntity oldLootr = resolve(level, pos);
        if (oldLootr != null) {
            clearPlayerInventory(oldLootr, player);
        }

        BlockState state = level.getBlockState(pos);
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(pos, state, 11);
        configureLootrBlock(level, pos, table, player);
    }

    private static void configureLootrBlock(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table, ServerPlayer player) {
        ILootrBlockEntity lootr = resolve(level, pos);
        if (lootr == null) {
            return;
        }
        lootr.setLootTableInternal(table, level.getRandom().nextLong());
        ILootrSavedData data = LootrAPI.getData(lootr);
        if (data != null) {
            data.update(lootr);
            data.clearInventories(player.getUUID());
            data.clearOpeners();
            data.markChanged();
        }
        lootr.performUpdate();
    }

    private static void clearPlayerInventory(ILootrBlockEntity lootr, ServerPlayer player) {
        ILootrSavedData data = LootrAPI.getData(lootr);
        if (data != null && data.clearInventories(player.getUUID())) {
            data.markChanged();
        }
    }

    private static ILootrBlockEntity resolve(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : LootrAPI.resolveBlockEntity(blockEntity);
    }

    private static ResourceKey<LootTable> tableKey(ResourceLocation id) {
        return ResourceKey.create(Registries.LOOT_TABLE, id);
    }

    private static void playPlaceEffects(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.85F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
    }

    private static void playRerollEffects(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.45F, 1.35F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }

    private static void send(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
