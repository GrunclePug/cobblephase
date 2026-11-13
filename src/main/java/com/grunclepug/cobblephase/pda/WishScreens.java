package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.wish.Wishboard;
import com.grunclepug.cobblephase.wish.Wishes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The shared wishboard and the form for posting a wish. */
final class WishScreens {
    private static final long HOUR_MILLIS = 60L * 60 * 1000;

    private WishScreens() {
    }

    static void board(PdaMenu menu) {
        ServerPlayer player = menu.player;
        PdaMenu.View view = menu.view;
        Wishboard board = Wishboard.get(player.server);
        List<Wishboard.Wish> wishes = board.wishes();
        long now = System.currentTimeMillis();
        int start = menu.pager(wishes.size());
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < wishes.size(); i++) {
            Wishboard.Wish wish = wishes.get(start + i);
            boolean mine = wish.poster().equals(player.getUUID());
            boolean confirming = wish.id().equals(view.confirmWish);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Wanted by ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(wish.posterName()).withStyle(mine ? ChatFormatting.GREEN : ChatFormatting.WHITE)));
            if (!wish.note().isEmpty()) {
                lore.add(text("\"" + wish.note() + "\"", ChatFormatting.WHITE));
            }
            lore.add(dim("Posted " + ago(now - wish.created()) + " ago, expires in " + ago(wish.expires() - now)));
            if (mine) {
                lore.add(confirming ? text("Click again to remove", ChatFormatting.RED) : hint("Click: remove (got it, or changed your mind)"));
            } else {
                lore.add(hint("Catch one and they'll be told."));
            }
            menu.set(i, icon(speciesIcon(wish.species()), Wishes.describe(wish), lore, confirming), !mine ? null : right -> {
                if (confirming) {
                    board.remove(wish.id(), player.getUUID());
                    view.confirmWish = null;
                } else {
                    view.confirmWish = wish.id();
                }
            });
        }
        if (wishes.isEmpty()) {
            menu.set(22, icon(Items.LECTERN, text("No wishes yet", ChatFormatting.GRAY),
                List.of(hint("Post what you're looking for below.")), false), null);
        }

        menu.back(PdaMenu.Screen.HOME);
        long mine = board.countBy(player.getUUID());
        menu.set(49, icon(Items.WRITABLE_BOOK, text("Post a wish", ChatFormatting.LIGHT_PURPLE), List.of(
            hint("Pick a Pokémon you're looking for."),
            dim("Yours: " + mine + "/" + Wishboard.MAX_PER_PLAYER + ". Wishes last 14 days.")), false), right -> {
                if (Wishboard.get(player.server).countBy(player.getUUID()) >= Wishboard.MAX_PER_PLAYER) {
                    player.sendSystemMessage(Wishes.prefix().append(Component.literal(
                        "You already have " + Wishboard.MAX_PER_PLAYER + " wishes. Remove one first.").withStyle(ChatFormatting.RED)));
                    return;
                }
                view.pick(PdaMenu.Picker.WISH);
            });
        if (view.confirmWish != null) {
            menu.beforeOtherClicks(-1, () -> view.confirmWish = null);
        }
    }

    static void draft(PdaMenu menu) {
        PdaMenu.View view = menu.view;
        ResourceLocation id = view.wishSpecies;
        Species species = PokemonSpecies.getByIdentifier(id);
        menu.set(13, icon(speciesIcon(id),
            species == null ? text(id.toString(), ChatFormatting.WHITE) : species.getTranslatedName().withStyle(ChatFormatting.AQUA),
            List.of(hint("Your new wish")), false), null);
        menu.set(29, icon(Items.NETHER_STAR, toggleTitle("Shiny only", view.wishShiny), List.of(), view.wishShiny),
            right -> view.wishShiny = !view.wishShiny);
        menu.set(31, icon(Items.OAK_SIGN, text("Note: " + (view.wishNote.isEmpty() ? "none" : "\"" + view.wishNote + "\""),
            ChatFormatting.AQUA), List.of(hint("Optional, e.g. \"female, for breeding\""), hint("Click to type it in chat")),
            !view.wishNote.isEmpty()), right -> ChatPrompt.ask(menu.player, "Type a short note for your wish.",
                (player, text) -> view.wishNote = text.length() > Wishboard.MAX_NOTE ? text.substring(0, Wishboard.MAX_NOTE) : text));
        menu.set(33, icon(Items.LIME_DYE, text("Post wish", ChatFormatting.GREEN),
            List.of(hint("Everyone with catch news on is told.")), false), right -> {
                if (Wishes.post(menu.player, id, view.wishShiny, view.wishNote)) {
                    view.wishSpecies = null;
                    view.go(PdaMenu.Screen.WISHBOARD);
                }
            });
        menu.set(45, icon(Items.ARROW, text("Cancel", ChatFormatting.WHITE), List.of(), false), right -> {
            view.wishSpecies = null;
            view.go(PdaMenu.Screen.WISHBOARD);
        });
    }

    private static ItemStack speciesIcon(ResourceLocation id) {
        Species species = PokemonSpecies.getByIdentifier(id);
        return species == null ? new ItemStack(Items.PAPER) : PokemonItem.Companion.from(species);
    }

    /** "3d", "5h" or "<1h". */
    private static String ago(long millis) {
        long hours = Math.max(0, millis) / HOUR_MILLIS;
        if (hours >= 24) {
            return (hours / 24) + "d";
        }
        return hours > 0 ? hours + "h" : "<1h";
    }
}
