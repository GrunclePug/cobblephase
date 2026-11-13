package com.grunclepug.cobblephase.news;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.grunclepug.cobblephase.pda.Pda;
import com.grunclepug.cobblephase.pda.PdaCatalog;
import com.grunclepug.cobblephase.pda.PdaSound;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells everyone online when someone catches a shiny or the server's first of a species. Players can turn these
 * off in the PDA menu.
 */
public final class CatchNews {
    private static final Logger LOG = LoggerFactory.getLogger("cobblephase");

    private CatchNews() {
    }

    public static void init() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, CatchNews::onCapture);
        ServerLifecycleEvents.SERVER_STARTED.register(CatchNews::seed);
        // Covers anyone the seed missed; their earlier catches stop counting as firsts once they join.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            learn(ServerFirsts.get(server), Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(handler.player)));
    }

    /** Once per world: record everything already in every known player's Pokédex. */
    private static void seed(MinecraftServer server) {
        ServerFirsts firsts = ServerFirsts.get(server);
        if (firsts.seeded()) {
            return;
        }
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        int players = 0;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".dat")) {
                        continue;
                    }
                    try {
                        UUID id = UUID.fromString(name.substring(0, name.length() - 4));
                        learn(firsts, Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(id));
                        players++;
                    } catch (IllegalArgumentException ignored) {
                        // Not a player file.
                    }
                }
            } catch (IOException e) {
                LOG.warn("Couldn't read player data to seed server firsts", e);
            }
        }
        firsts.markSeeded();
        LOG.info("Seeded server firsts from {} players' Pokédexes", players);
    }

    private static void learn(ServerFirsts firsts, AbstractPokedexManager pokedex) {
        for (ResourceLocation id : pokedex.getSpeciesRecords().keySet()) {
            if (pokedex.getKnowledgeForSpecies(id) == PokedexEntryProgress.OWNED) {
                firsts.know(id);
            }
        }
    }

    private static void onCapture(PokemonCapturedEvent event) {
        ServerPlayer catcher = event.getPlayer();
        Pokemon pokemon = event.getPokemon();
        boolean first = ServerFirsts.get(catcher.server)
            .claim(pokemon.getSpecies().getResourceIdentifier(), catcher.getGameProfile().getName());
        boolean shiny = pokemon.getShiny();
        if (!first && !shiny) {
            return;
        }

        MutableComponent message = Component.literal(shiny ? "★ " : "[Catch] ")
            .withStyle(shiny ? ChatFormatting.GOLD : ChatFormatting.GREEN)
            .append(catcher.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" caught ").withStyle(ChatFormatting.GRAY));
        if (first) {
            message.append(Component.literal("the server's first ").withStyle(ChatFormatting.GRAY))
                .append(details(pokemon, pokemon.getSpecies().getTranslatedName()));
            if (shiny) {
                message.append(Component.literal(", and it's shiny!").withStyle(ChatFormatting.GOLD));
            } else {
                message.append(Component.literal("!").withStyle(ChatFormatting.GRAY));
            }
        } else {
            message.append(Component.literal("a ").withStyle(ChatFormatting.GRAY))
                .append(details(pokemon, PdaCatalog.name(pokemon)))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY));
        }

        for (ServerPlayer player : catcher.server.getPlayerList().getPlayers()) {
            if (Pda.settings(player).announcements()) {
                player.sendSystemMessage(message);
                if (shiny) {
                    PdaSound.SPARKLE.play(player, null);
                }
            }
        }
    }

    /** The Pokémon's name, with level, nature and ability on hover. */
    private static Component details(Pokemon pokemon, MutableComponent name) {
        Component hover = Component.empty()
            .append(PdaCatalog.name(pokemon).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\nLevel " + pokemon.getLevel()).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("\nNature: ").withStyle(ChatFormatting.GRAY))
            .append(Component.translatable(pokemon.getNature().getDisplayName()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\nAbility: ").withStyle(ChatFormatting.GRAY))
            .append(Component.translatable(pokemon.getAbility().getDisplayName()).withStyle(ChatFormatting.WHITE));
        // Wrapped so a shared name component from Cobblemon is never restyled.
        return Component.empty().append(name).withStyle(style -> style.withColor(ChatFormatting.AQUA)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
}
