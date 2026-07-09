package org.destroyermob.devtools.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.destroyermob.devtools.DevTools;
import org.destroyermob.devtools.item.LootrDevToolItem;

public final class DevToolsCommands {
    private static final Set<UUID> DAMAGE_DEBUG_PLAYERS = ConcurrentHashMap.newKeySet();

    private DevToolsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(DevTools.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(lootrDevTool(false))
                .then(lootrDevTool(true))
                .then(damageDebug()));
    }

    public static void reportDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getNewDamage() <= 0.0F) {
            return;
        }

        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player) || !DAMAGE_DEBUG_PLAYERS.contains(player.getUUID())) {
            return;
        }

        player.sendSystemMessage(Component.literal("[DamageDebug] ")
                .append(Component.literal(formatDamage(event.getNewDamage())))
                .append(Component.literal(" final (raw "))
                .append(Component.literal(formatDamage(event.getOriginalDamage())))
                .append(Component.literal(") -> "))
                .append(target.getDisplayName()));
        player.sendSystemMessage(Component.literal("[DamageDebug] ")
                .append(Component.literal("attr=" + formatDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE))))
                .append(Component.literal(" main=" + itemSummary(player, player.getMainHandItem())))
                .append(Component.literal(" off=" + itemSummary(player, player.getOffhandItem())))
                .append(Component.literal(" sourceWeapon=" + itemSummary(player, event.getSource().getWeaponItem())))
                .append(Component.literal(" mode=" + player.gameMode.getGameModeForPlayer().getName())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lootrDevTool(boolean targetBranch) {
        var root = Commands.literal(targetBranch ? "lootr_dev_tool_to" : "lootr_dev_tool");
        var table = Commands.argument("loot_table", ResourceLocationArgument.id())
                .suggests(DevToolsCommands::suggestLootTables);
        table.executes(context -> giveLootrDevTool(context, targetBranch, ResourceLocationArgument.getId(context, "loot_table")));
        if (targetBranch) {
            return root.then(Commands.argument("targets", EntityArgument.players()).then(table));
        }
        return root.then(table);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> damageDebug() {
        return Commands.literal("damage_debug")
                .executes(DevToolsCommands::toggleDamageDebug)
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setDamageDebug(context, BoolArgumentType.getBool(context, "enabled"))));
    }

    private static CompletableFuture<Suggestions> suggestLootTables(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> tables = context.getSource().getServer().reloadableRegistries().get()
                .lookup(Registries.LOOT_TABLE)
                .map(registry -> registry.listElementIds()
                        .map(key -> key.location().toString())
                        .toList())
                .orElse(List.of());
        return SharedSuggestionProvider.suggest(tables, builder);
    }

    private static int giveLootrDevTool(CommandContext<CommandSourceStack> context, boolean targetBranch, ResourceLocation table) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return giveStack(context, targetBranch, LootrDevToolItem.create(table));
    }

    private static int toggleDamageDebug(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return setDamageDebug(context, !DAMAGE_DEBUG_PLAYERS.contains(player.getUUID()));
    }

    private static int setDamageDebug(CommandContext<CommandSourceStack> context, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (enabled) {
            DAMAGE_DEBUG_PLAYERS.add(player.getUUID());
        } else {
            DAMAGE_DEBUG_PLAYERS.remove(player.getUUID());
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.devtools.damage_debug." + (enabled ? "enabled" : "disabled")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int giveStack(CommandContext<CommandSourceStack> context, boolean targetBranch, ItemStack stack) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = targetBranch ? EntityArgument.getPlayers(context, "targets") : List.of(context.getSource().getPlayerOrException());
        for (ServerPlayer player : targets) {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            player.containerMenu.broadcastChanges();
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.devtools.give.success", stack.getHoverName(), targets.size()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static String formatDamage(float damage) {
        return String.format(Locale.ROOT, "%.3f", damage).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String itemSummary(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + "[stackAtk=" + formatDamage(stackAttackDamage(player, stack)) + "]";
    }

    private static float stackAttackDamage(ServerPlayer player, ItemStack stack) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        double baseDamage = attackDamage == null ? Attributes.ATTACK_DAMAGE.value().getDefaultValue() : attackDamage.getBaseValue();
        double[] addValue = {0.0D};
        double[] addMultipliedBase = {0.0D};
        double[] multipliedTotal = {1.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
            if (!Attributes.ATTACK_DAMAGE.equals(attribute)) {
                return;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> addValue[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> addMultipliedBase[0] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal[0] *= 1.0D + modifier.amount();
            }
        });

        double damageWithAdds = baseDamage + addValue[0];
        double damageWithBaseMultipliers = damageWithAdds + damageWithAdds * addMultipliedBase[0];
        return (float) Math.max(0.0D, damageWithBaseMultipliers * multipliedTotal[0]);
    }
}
