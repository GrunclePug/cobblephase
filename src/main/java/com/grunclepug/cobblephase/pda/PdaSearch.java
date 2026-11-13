package com.grunclepug.cobblephase.pda;

import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pokémon search for the species picker: the menu closes, the player types names in chat, and that one message is
 * kept out of public chat. Simpler and more reliable than abusing an anvil's rename box.
 */
final class PdaSearch {
    private static final long TIMEOUT = 60 * 20;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(int rule, long expires) {
    }

    private PdaSearch() {
    }

    static void start(ServerPlayer player, int rule) {
        PENDING.put(player.getUUID(), new Pending(rule, player.server.getTickCount() + TIMEOUT));
        player.closeContainer();
        player.sendSystemMessage(prefix().append(Component.literal(
            "Type a Pokémon name in chat, or several separated by commas. Type 'cancel' to go back.")
            .withStyle(ChatFormatting.GRAY)));
    }

    /** Returns false (don't broadcast) for a message that answers a pending search. */
    static boolean onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
        Pending pending = PENDING.remove(sender.getUUID());
        if (pending == null) {
            return true;
        }
        String text = message.signedContent();
        sender.server.execute(() -> apply(sender, pending, text));
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
                player.sendSystemMessage(prefix().append(Component.literal("Search timed out.").withStyle(ChatFormatting.GRAY)));
            }
            return true;
        });
    }

    static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private static void apply(ServerPlayer player, Pending pending, String text) {
        if (player.hasDisconnected()) {
            return;
        }
        PdaSettings settings = Pda.settings(player);
        if (pending.rule() >= settings.rules().size()) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("cancel")) {
            reopen(player, pending.rule(), null);
            return;
        }

        List<ResourceLocation> species = new ArrayList<>(settings.rules().get(pending.rule()).species());
        MutableComponent addedNames = Component.empty();
        int added = 0;
        List<String> unmatched = new ArrayList<>();
        for (String term : trimmed.split(",")) {
            String key = PdaCatalog.searchKey(term);
            if (key.isEmpty()) {
                continue;
            }
            Species match = exact(key);
            if (match == null) {
                unmatched.add(term.trim());
                continue;
            }
            if (!species.contains(match.getResourceIdentifier())) {
                species.add(match.getResourceIdentifier());
            }
            addedNames.append(added++ == 0 ? Component.empty() : Component.literal(", "));
            addedNames.append(match.getTranslatedName());
        }
        Pda.save(player, settings.updateRule(pending.rule(), rule -> rule.withSpecies(species)));

        if (added > 0) {
            player.sendSystemMessage(prefix().append(Component.literal("Added: ").withStyle(ChatFormatting.GRAY))
                .append(addedNames.withStyle(ChatFormatting.WHITE)));
        }
        String filter = null;
        if (added == 0 && unmatched.size() == 1) {
            // Not an exact name: show the picker filtered by it instead.
            filter = unmatched.getFirst();
        } else if (!unmatched.isEmpty()) {
            player.sendSystemMessage(prefix().append(Component.literal("No Pokémon named: " + String.join(", ", unmatched))
                .withStyle(ChatFormatting.RED)));
        }
        reopen(player, pending.rule(), filter);
    }

    private static Species exact(String key) {
        for (Species species : PdaCatalog.species()) {
            if (PdaCatalog.searchKey(species.getName()).equals(key)
                || PdaCatalog.searchKey(species.getResourceIdentifier().getPath()).equals(key)) {
                return species;
            }
        }
        return null;
    }

    /** Back to the picker, unless the player has since opened something else or died. */
    private static void reopen(ServerPlayer player, int rule, String filter) {
        if (player.isAlive() && player.containerMenu == player.inventoryMenu) {
            PdaMenu.openSpecies(player, rule, filter);
        }
    }

    static MutableComponent prefix() {
        return Component.literal("[PDA] ").withStyle(ChatFormatting.AQUA);
    }
}
