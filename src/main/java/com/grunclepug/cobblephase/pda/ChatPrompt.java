package com.grunclepug.cobblephase.pda;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Text input for the menu: it closes, the player types one chat message, and that message is kept out of public
 * chat and handed to the waiting screen. Simpler and more reliable than abusing an anvil's rename box.
 */
final class ChatPrompt {
    private static final long TIMEOUT = 60 * 20;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(long expires, BiConsumer<ServerPlayer, String> answer) {
    }

    private ChatPrompt() {
    }

    /** Closes the menu and waits for one message; "cancel" (or a blank line) just reopens the menu. */
    static void ask(ServerPlayer player, String prompt, BiConsumer<ServerPlayer, String> answer) {
        PENDING.put(player.getUUID(), new Pending(player.server.getTickCount() + TIMEOUT, answer));
        player.closeContainer();
        player.sendSystemMessage(prefix().append(Component.literal(prompt + " Type 'cancel' to go back.")
            .withStyle(ChatFormatting.GRAY)));
    }

    /** Returns false (don't broadcast) for a message that answers a pending prompt. */
    static boolean onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
        Pending pending = PENDING.remove(sender.getUUID());
        if (pending == null) {
            return true;
        }
        String text = message.signedContent().trim();
        sender.server.execute(() -> {
            if (sender.hasDisconnected()) {
                return;
            }
            if (!text.isEmpty() && !text.equalsIgnoreCase("cancel")) {
                pending.answer().accept(sender, text);
            }
            PdaMenu.reopenIfIdle(sender);
        });
        return false;
    }

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        PENDING.entrySet().removeIf(entry -> {
            if (now < entry.getValue().expires()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(prefix().append(Component.literal("Timed out.").withStyle(ChatFormatting.GRAY)));
            }
            return true;
        });
    }

    static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    static MutableComponent prefix() {
        return Component.literal("[PDA] ").withStyle(ChatFormatting.AQUA);
    }
}
