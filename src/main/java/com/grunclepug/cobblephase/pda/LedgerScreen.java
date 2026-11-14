package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.news.ServerFirsts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** The server Pokédex: how much of it the group has caught, what nobody has yet, and who caught each first. */
final class LedgerScreen {
    private LedgerScreen() {
    }

    static void render(PdaMenu menu) {
        PdaMenu.View view = menu.view;
        Map<String, String> caught = ServerFirsts.get(menu.player.server).entries();
        List<Species> all = PdaCatalog.species();
        List<Species> shown = new ArrayList<>();
        int count = 0;
        for (Species species : all) {
            boolean has = caught.containsKey(species.getResourceIdentifier().toString());
            count += has ? 1 : 0;
            if (has != view.ledgerMissing) {
                shown.add(species);
            }
        }
        String title = count + "/" + all.size() + " caught";
        int start = menu.pager(shown.size(), PdaMenu.PAGE_SIZE, title);
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < shown.size(); i++) {
            Species species = shown.get(start + i);
            String first = caught.get(species.getResourceIdentifier().toString());
            List<Component> lore = new ArrayList<>();
            lore.add(dim("#" + species.getNationalPokedexNumber()));
            if (view.ledgerMissing) {
                lore.add(text("Nobody has caught one yet", ChatFormatting.YELLOW));
            } else {
                lore.add(first == null || first.isEmpty() ? hint("Caught before tracking began")
                    : Component.literal("First caught by ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(first).withStyle(ChatFormatting.WHITE)));
            }
            lore.add(hint("Click: open in the tracker"));
            menu.set(i, icon(PokemonItem.Companion.from(species), species.getTranslatedName().withStyle(ChatFormatting.WHITE),
                lore, false), right -> {
                    view.trackerSpecies = species.getResourceIdentifier();
                    view.trackerBack = PdaMenu.Screen.LEDGER;
                    view.go(PdaMenu.Screen.TRACKER);
                });
        }
        menu.back(PdaMenu.Screen.HOME);
        menu.set(50, icon(Items.SPYGLASS, toggleTitle("Nobody has it yet", view.ledgerMissing), List.of(
            hint("Show species nobody on the server has caught")), view.ledgerMissing), right -> {
                view.ledgerMissing = true;
                view.page = 0;
            });
        menu.set(51, icon(Items.GOLDEN_HELMET, toggleTitle("First catches", !view.ledgerMissing), List.of(
            hint("Show caught species and who got each first")), !view.ledgerMissing), right -> {
                view.ledgerMissing = false;
                view.page = 0;
            });
    }
}
