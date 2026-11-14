package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.party.EvolutionWatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The evolution and EV helpers: pick a party Pokémon, then see its evolutions or its EVs. */
final class PartyScreens {
    private static final List<Stats> STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE, Stats.SPEED);
    private static final int MAX_EV = 252;
    private static final int MAX_EV_TOTAL = 510;

    private PartyScreens() {
    }

    /** The party, to pick a Pokémon for the evolution or EV helper. */
    static void party(PdaMenu menu) {
        boolean evolution = menu.view.partyEvolution;
        menu.set(4, icon(evolution ? Items.DRAGON_BREATH : Items.IRON_CHESTPLATE,
            text(evolution ? "Evolution helper" : "EV helper", ChatFormatting.LIGHT_PURPLE),
            List.of(hint(evolution ? "What each party Pokémon needs to evolve" : "EVs so far, and what to battle for more"),
                hint("Pick a Pokémon below")), false), null);
        int slot = 19;
        for (Pokemon pokemon : EvolutionWatch.party(menu.player)) {
            if (pokemon == null) {
                continue;
            }
            UUID id = pokemon.getUuid();
            menu.set(slot++, icon(PokemonItem.Companion.from(pokemon), PdaCatalog.name(pokemon).withStyle(ChatFormatting.WHITE),
                List.of(dim("Level " + pokemon.getLevel()), hint("Click to open")), false), right -> {
                    menu.view.partyPokemon = id;
                    menu.view.go(evolution ? PdaMenu.Screen.EVOLVE : PdaMenu.Screen.EVS);
                });
        }
        menu.back(PdaMenu.Screen.HOME);
    }

    static void evolve(PdaMenu menu) {
        ServerPlayer player = menu.player;
        Pokemon pokemon = EvolutionWatch.find(player, menu.view.partyPokemon);
        if (pokemon == null) {
            menu.view.go(PdaMenu.Screen.PARTY);
            return;
        }
        boolean pinned = pokemon.getUuid().equals(player.getAttached(EvolutionWatch.PINNED));
        menu.set(4, icon(PokemonItem.Companion.from(pokemon), PdaCatalog.name(pokemon).withStyle(ChatFormatting.WHITE),
            List.of(dim("Level " + pokemon.getLevel())), false), null);
        int slot = 19;
        boolean any = false;
        for (Evolution evolution : pokemon.getEvolutions()) {
            any = true;
            Species result = evolution.getResult().getSpecies() == null ? null
                : PokemonSpecies.getByName(evolution.getResult().getSpecies().toLowerCase(java.util.Locale.ROOT));
            boolean ready = EvolutionWatch.ready(pokemon, evolution);
            List<Component> lore = new ArrayList<>();
            lore.add(hint("Evolves when it " + EvolutionWatch.trigger(evolution) + ", if:"));
            for (Requirement requirement : evolution.getRequirements()) {
                boolean met = requirement.check(pokemon);
                String tooltip = requirement.getPokedexTooltip();
                Component name = tooltip == null || tooltip.isBlank() ? Component.literal(EvolutionWatch.requirementName(requirement))
                    : Component.translatable(tooltip);
                lore.add(Component.literal(met ? "  ✔ " : "  ✘ ").withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(name.copy().withStyle(ChatFormatting.WHITE)));
            }
            if (evolution.getRequirements().isEmpty()) {
                lore.add(dim("  (nothing else)"));
            }
            lore.add(ready ? text("Ready now", ChatFormatting.GREEN) : dim("Not ready yet"));
            menu.set(slot++, icon(result == null ? new ItemStack(Items.PAPER) : PokemonItem.Companion.from(result),
                result == null ? text(evolution.getId(), ChatFormatting.WHITE) : result.getTranslatedName().withStyle(ChatFormatting.AQUA),
                lore, ready), null);
            if (slot > 25) {
                break;
            }
        }
        if (!any) {
            menu.set(22, icon(Items.BARRIER, text("It doesn't evolve", ChatFormatting.GRAY), List.of(), false), null);
        } else {
            menu.set(31, icon(Items.BELL, toggleTitle(pinned ? "Pinned" : "Pin it", pinned), List.of(
                hint("Tell me once when every requirement"), hint("of one evolution is met."),
                dim("One pinned Pokémon at a time.")), pinned), right -> {
                    if (pinned) {
                        player.removeAttached(EvolutionWatch.PINNED);
                    } else {
                        player.setAttached(EvolutionWatch.PINNED, pokemon.getUuid());
                    }
                });
        }
        menu.back(PdaMenu.Screen.PARTY);
    }

    static void evs(PdaMenu menu) {
        Pokemon pokemon = EvolutionWatch.find(menu.player, menu.view.partyPokemon);
        if (pokemon == null) {
            menu.view.go(PdaMenu.Screen.PARTY);
            return;
        }
        int total = 0;
        for (Stats stat : STATS) {
            total += pokemon.getEvs().get(stat);
        }
        menu.set(4, icon(PokemonItem.Companion.from(pokemon), PdaCatalog.name(pokemon).withStyle(ChatFormatting.WHITE),
            List.of(hint("EV total: " + total + "/" + MAX_EV_TOTAL)), false), null);
        for (int i = 0; i < STATS.size(); i++) {
            Stats stat = STATS.get(i);
            int value = pokemon.getEvs().get(stat);
            boolean on = stat.getShowdownId().equals(menu.view.evStat);
            menu.set(10 + i + (i >= 3 ? 1 : 0), icon(statIcon(stat), toggleTitle(stat.getDisplayName().copy()
                .append(Component.literal(": " + value + "/" + MAX_EV)), on), List.of(bar(value), hint("Click: what to battle for it")), on),
                right -> {
                    menu.view.evStat = stat.getShowdownId();
                    menu.view.page = 0;
                });
        }
        Stats chosen = null;
        for (Stats stat : STATS) {
            if (stat.getShowdownId().equals(menu.view.evStat)) {
                chosen = stat;
            }
        }
        if (chosen != null) {
            Stats target = chosen;
            List<Species> yielders = new ArrayList<>();
            for (Species species : PdaCatalog.species()) {
                if (evYield(species, target) > 0) {
                    yielders.add(species);
                }
            }
            yielders.sort(Comparator.<Species>comparingInt(s -> -evYield(s, target)).thenComparingInt(Species::getNationalPokedexNumber));
            for (int i = 0; i < Math.min(18, yielders.size()); i++) {
                Species species = yielders.get(i);
                menu.set(27 + i, icon(PokemonItem.Companion.from(species), species.getTranslatedName().withStyle(ChatFormatting.WHITE),
                    List.of(text("Gives " + evYield(species, target) + " " + target.getDisplayName().getString() + " EV", ChatFormatting.GREEN),
                        hint("Click: open in the tracker")), false), right -> {
                        menu.view.trackerSpecies = species.getResourceIdentifier();
                        menu.view.trackerBack = PdaMenu.Screen.EVS;
                        menu.view.go(PdaMenu.Screen.TRACKER);
                    });
            }
        }
        menu.back(PdaMenu.Screen.PARTY);
    }

    private static int evYield(Species species, Stat stat) {
        Integer value = species.getStandardForm().getEvYield().get(stat);
        return value == null ? 0 : value;
    }

    private static Component bar(int value) {
        int filled = Math.round(value / (float) MAX_EV * 10);
        return Component.literal("█".repeat(filled)).withStyle(ChatFormatting.GREEN)
            .append(Component.literal("█".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static net.minecraft.world.item.Item statIcon(Stats stat) {
        return switch (stat) {
            case HP -> Items.GOLDEN_APPLE;
            case ATTACK -> Items.IRON_SWORD;
            case DEFENCE -> Items.SHIELD;
            case SPECIAL_ATTACK -> Items.BLAZE_POWDER;
            case SPECIAL_DEFENCE -> Items.AMETHYST_SHARD;
            case SPEED -> Items.FEATHER;
            default -> Items.PAPER;
        };
    }
}
