package org.destroyermob.devtools.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.destroyermob.devtools.DevTools;
import org.destroyermob.devtools.item.LootrDevToolItem;

public final class DevToolsCommands {
    private DevToolsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(DevTools.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(lootrDevTool(false))
                .then(lootrDevTool(true)));
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
}
