package com.grunclepug.cobblephase.wish;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.news.NewsLog;
import com.grunclepug.cobblephase.pda.Pda;
import com.grunclepug.cobblephase.pda.PdaCatalog;
import com.grunclepug.cobblephase.pda.PdaSound;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Wishboard actions and the "someone caught what you wished for" notices. */
public final class Wishes {
    /** A catcher hears about the same wish at most this often. */
    private static final long NOTICE_COOLDOWN_MILLIS = 60L * 60 * 1000;
    private static final Map<String, Long> NOTICED = new HashMap<>();

    private Wishes() {
    }

    public static void init() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, Wishes::onCapture);
    }

    /** Posts a wish and tells everyone with catch news on. */
    public static boolean post(ServerPlayer player, ResourceLocation species, boolean shiny, String note) {
        Wishboard board = Wishboard.get(player.server);
        if (board.countBy(player.getUUID()) >= Wishboard.MAX_PER_PLAYER) {
            player.sendSystemMessage(prefix().append(Component.literal(
                "You already have " + Wishboard.MAX_PER_PLAYER + " wishes. Remove one first.").withStyle(ChatFormatting.RED)));
            return false;
        }
        Wishboard.Wish wish = new Wishboard.Wish(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName(),
            species, shiny, note, System.currentTimeMillis());
        board.add(wish);
        NewsLog.add(player.server, player.getGameProfile().getName() + " is looking for " + (shiny ? "a shiny " : "")
            + speciesName(species) + (note.isEmpty() ? "" : " (\"" + note + "\")"));
        Component line = prefix()
            .append(player.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" is looking for ").withStyle(ChatFormatting.GRAY))
            .append(describe(wish))
            .append(note.isEmpty() ? Component.empty() : Component.literal(": \"" + note + "\"").withStyle(ChatFormatting.GRAY));
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player || Pda.settings(other).announcements()) {
                other.sendSystemMessage(line);
            }
        }
        return true;
    }

    private static void onCapture(PokemonCapturedEvent event) {
        ServerPlayer catcher = event.getPlayer();
        Pokemon pokemon = event.getPokemon();
        ResourceLocation species = pokemon.getSpecies().getResourceIdentifier();
        long now = System.currentTimeMillis();
        NOTICED.values().removeIf(time -> now - time > NOTICE_COOLDOWN_MILLIS);
        for (Wishboard.Wish wish : Wishboard.get(catcher.server).wishes()) {
            if (wish.poster().equals(catcher.getUUID()) || !wish.species().equals(species)
                || (wish.shiny() && !pokemon.getShiny())) {
                continue;
            }
            if (NOTICED.putIfAbsent(wish.id() + "/" + catcher.getUUID(), now) != null) {
                continue;
            }
            NewsLog.addFor(catcher.server, wish.poster(), catcher.getGameProfile().getName() + " caught a "
                + speciesName(species) + " for your wish");
            catcher.sendSystemMessage(prefix()
                .append(Component.literal(wish.posterName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" wished for ").withStyle(ChatFormatting.GRAY))
                .append(describe(wish))
                .append(Component.literal(", and you just caught one! Maybe trade it?").withStyle(ChatFormatting.GRAY)));
            PdaSound.CHIME.play(catcher, null);
            ServerPlayer poster = catcher.server.getPlayerList().getPlayer(wish.poster());
            if (poster != null) {
                poster.sendSystemMessage(prefix()
                    .append(catcher.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" just caught ").withStyle(ChatFormatting.GRAY))
                    .append(PdaCatalog.name(pokemon).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" for your wish. Ask them for a trade!").withStyle(ChatFormatting.GRAY)));
                PdaSound.CHIME.play(poster, null);
            }
        }
    }

    private static String speciesName(ResourceLocation id) {
        Species species = PokemonSpecies.getByIdentifier(id);
        return species == null ? id.getPath() : species.getName();
    }

    /** "Shiny Ralts" or "Ralts". */
    public static MutableComponent describe(Wishboard.Wish wish) {
        Species species = PokemonSpecies.getByIdentifier(wish.species());
        MutableComponent name = Component.empty();
        if (wish.shiny()) {
            name.append(Component.literal("Shiny ").withStyle(ChatFormatting.GOLD));
        }
        name.append(species == null ? Component.literal(wish.species().toString()) : species.getTranslatedName());
        return name.withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent prefix() {
        return Component.literal("[Wish] ").withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
