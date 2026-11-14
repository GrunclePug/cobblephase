package com.grunclepug.cobblephase.tracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cobblephase rate <token> good|bad}: the target of the tracker's chat buttons after a teleport. Chat buttons
 * can only run commands, so this exists; players don't need to type it.
 */
public final class TrackerCommand {
    private TrackerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobblephase")
            .then(Commands.literal("rate")
                .then(Commands.argument("token", StringArgumentType.word())
                    .then(Commands.literal("good").executes(context -> rate(context, true)))
                    .then(Commands.literal("bad").executes(context -> rate(context, false))))));
    }

    private static int rate(CommandContext<CommandSourceStack> context, boolean good) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        if (!Locator.rate(player, StringArgumentType.getString(context, "token"), good)) {
            Locator.message(player, Component.literal("That spot was already rated, or the rating expired.")
                .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        Locator.message(player, Component.literal(good ? "Thanks! This spot will be used first next time."
            : "Thanks! Searches will skip this area from now on.").withStyle(good ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return 1;
    }
}
