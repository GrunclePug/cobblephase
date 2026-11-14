package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.habitat.HabitatPhases;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Pages of Pokémon icons. For a rule it toggles many species; for the habitat planner and the wishboard it picks
 * one and moves on.
 */
final class SpeciesPicker {
    private SpeciesPicker() {
    }

    static void render(PdaMenu menu) {
        PdaMenu.View view = menu.view;
        boolean forRule = view.picker == PdaMenu.Picker.RULE;
        List<ResourceLocation> chosen = forRule ? menu.rule().species() : List.of();
        List<Species> list = filtered(view, chosen);

        int start = menu.pager(list.size());
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < list.size(); i++) {
            Species species = list.get(start + i);
            ResourceLocation id = species.getResourceIdentifier();
            boolean on = chosen.contains(id);
            Component action = !forRule ? hint(switch (view.picker) {
                case PLANNER -> "Click to plan";
                case WISH -> "Click to wish for it";
                default -> "Click to track";
            })
                : on ? text("✔ Chosen: click to remove", ChatFormatting.GREEN) : hint("Click to choose");
            menu.set(i, icon(PokemonItem.Companion.from(species),
                species.getTranslatedName().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                List.of(dim("#" + species.getNationalPokedexNumber()), action), on),
                right -> choose(menu, id));
        }
        if (list.isEmpty()) {
            menu.set(22, icon(Items.BARRIER, text("No Pokémon match", ChatFormatting.RED), List.of(), false), null);
        }

        PdaMenu.Screen back = switch (view.picker) {
            case RULE -> PdaMenu.Screen.RULE;
            case PLANNER, TRACKER -> PdaMenu.Screen.HOME;
            case WISH -> PdaMenu.Screen.WISHBOARD;
        };
        menu.back(back);
        menu.set(50, icon(Items.SPYGLASS, text("Search", ChatFormatting.AQUA), List.of(
            hint(forRule ? "Type names in chat; exact names are added" : "Type a name in chat"),
            view.filter == null ? dim("No search") : text("Showing: \"" + view.filter + "\"", ChatFormatting.YELLOW)),
            false), right -> ChatPrompt.ask(menu.player,
                forRule ? "Type a Pokémon name in chat, or several separated by commas." : "Type a Pokémon name in chat.",
                (player, text) -> search(player, view, text)));
        if (view.filter != null) {
            menu.set(51, icon(Items.BRUSH, text("Clear search", ChatFormatting.WHITE), List.of(), false), right -> {
                view.filter = null;
                view.page = 0;
            });
        }
        if (forRule) {
            menu.set(52, icon(Items.ENDER_EYE, toggleTitle("Chosen only (" + chosen.size() + ")", view.selectedOnly),
                List.of(hint("Show only the Pokémon this rule has")), view.selectedOnly), right -> {
                    view.selectedOnly = !view.selectedOnly;
                    view.page = 0;
                });
        }
        if (view.picker == PdaMenu.Picker.PLANNER) {
            PlannerScreens.remindersButton(menu, 52);
            PlannerScreens.observerButton(menu);
        }
    }

    /** The species this picker offers, after the search and "chosen only" filters. */
    private static List<Species> filtered(PdaMenu.View view, List<ResourceLocation> chosen) {
        Set<ResourceLocation> habitat = view.picker == PdaMenu.Picker.PLANNER ? HabitatPhases.species() : null;
        String key = view.filter == null ? "" : PdaCatalog.searchKey(view.filter);
        List<Species> list = new ArrayList<>();
        for (Species species : PdaCatalog.species()) {
            ResourceLocation id = species.getResourceIdentifier();
            if ((habitat != null && !habitat.contains(id)) || (view.selectedOnly && !chosen.contains(id))) {
                continue;
            }
            if (!key.isEmpty() && !PdaCatalog.searchKey(species.getName()).contains(key)
                && !PdaCatalog.searchKey(id.getPath()).contains(key)) {
                continue;
            }
            list.add(species);
        }
        return list;
    }

    private static void choose(PdaMenu menu, ResourceLocation id) {
        PdaMenu.View view = menu.view;
        switch (view.picker) {
            case RULE -> menu.updateRule(rule -> rule.toggleSpecies(id));
            case PLANNER -> {
                view.plannerSpecies = id;
                view.go(PdaMenu.Screen.PLANNER);
            }
            case WISH -> {
                view.wishSpecies = id;
                view.wishShiny = false;
                view.wishNote = "";
                view.go(PdaMenu.Screen.WISH_NEW);
            }
            case TRACKER -> track(view, id);
        }
    }

    private static void track(PdaMenu.View view, ResourceLocation id) {
        view.trackerSpecies = id;
        view.trackerBack = PdaMenu.Screen.SPECIES;
        view.go(PdaMenu.Screen.TRACKER);
    }

    /**
     * Applies a chat search: exact names are chosen directly (for a rule, several at once), and anything else
     * becomes a filter on the picker.
     */
    private static void search(ServerPlayer player, PdaMenu.View view, String text) {
        boolean forRule = view.picker == PdaMenu.Picker.RULE;
        if (forRule && view.rule >= Pda.settings(player).rules().size()) {
            return;
        }
        Set<ResourceLocation> habitat = view.picker == PdaMenu.Picker.PLANNER ? HabitatPhases.species() : null;
        String[] terms = forRule ? text.split(",") : new String[] {text};
        List<ResourceLocation> found = new ArrayList<>();
        MutableComponent names = Component.empty();
        List<String> unmatched = new ArrayList<>();
        for (String term : terms) {
            String key = PdaCatalog.searchKey(term);
            if (key.isEmpty()) {
                continue;
            }
            Species match = exact(key, habitat);
            if (match == null) {
                unmatched.add(term.trim());
                continue;
            }
            names.append(found.isEmpty() ? Component.empty() : Component.literal(", "));
            names.append(match.getTranslatedName());
            found.add(match.getResourceIdentifier());
        }

        view.filter = null;
        view.page = 0;
        if (found.isEmpty() && unmatched.size() == 1) {
            view.filter = unmatched.getFirst();
            return;
        }
        if (!unmatched.isEmpty()) {
            player.sendSystemMessage(ChatPrompt.prefix().append(Component.literal(
                "No " + (habitat != null ? "habitat " : "") + "Pokémon named: " + String.join(", ", unmatched))
                .withStyle(ChatFormatting.RED)));
        }
        if (found.isEmpty()) {
            return;
        }
        if (forRule) {
            Pda.save(player, Pda.settings(player).updateRule(view.rule, rule -> {
                List<ResourceLocation> species = new ArrayList<>(rule.species());
                for (ResourceLocation id : found) {
                    if (!species.contains(id)) {
                        species.add(id);
                    }
                }
                return rule.withSpecies(species);
            }));
            player.sendSystemMessage(ChatPrompt.prefix().append(Component.literal("Added: ").withStyle(ChatFormatting.GRAY))
                .append(names.withStyle(ChatFormatting.WHITE)));
        } else if (view.picker == PdaMenu.Picker.PLANNER) {
            view.plannerSpecies = found.getFirst();
            view.go(PdaMenu.Screen.PLANNER);
        } else if (view.picker == PdaMenu.Picker.TRACKER) {
            track(view, found.getFirst());
        } else {
            view.wishSpecies = found.getFirst();
            view.wishShiny = false;
            view.wishNote = "";
            view.go(PdaMenu.Screen.WISH_NEW);
        }
    }

    private static Species exact(String key, Set<ResourceLocation> within) {
        for (Species species : PdaCatalog.species()) {
            if (within != null && !within.contains(species.getResourceIdentifier())) {
                continue;
            }
            if (PdaCatalog.searchKey(species.getName()).equals(key)
                || PdaCatalog.searchKey(species.getResourceIdentifier().getPath()).equals(key)) {
                return species;
            }
        }
        return null;
    }
}
