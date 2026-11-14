package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import com.cobblemon.mod.common.item.PokemonItem;
import com.grunclepug.cobblephase.habitat.HabitatReminders;
import com.grunclepug.cobblephase.wish.Wishboard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Rules, nearby matches and settings. */
final class HomeScreen {
    private HomeScreen() {
    }

    static void render(PdaMenu menu, PdaSettings settings) {
        ServerPlayer player = menu.player;
        List<PdaRule> rules = settings.rules();
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            PdaRule rule = rules.get(i);
            List<Component> lore = new ArrayList<>(RuleScreens.summary(rule));
            lore.add(Component.empty());
            lore.add(hint("Left-click: edit"));
            lore.add(hint("Right-click: turn " + (rule.enabled() ? "off" : "on")));
            menu.set(i, icon(RuleScreens.ruleIcon(rule), RuleScreens.ruleTitle(index, rule), lore, false), right -> {
                if (right) {
                    Pda.save(player, Pda.settings(player).updateRule(index, r -> r.withEnabled(!r.enabled())));
                } else {
                    menu.view.rule = index;
                    menu.view.go(PdaMenu.Screen.RULE);
                }
            });
        }
        if (rules.isEmpty()) {
            menu.set(13, icon(Items.WRITABLE_BOOK, text("No ping rules yet", ChatFormatting.YELLOW),
                List.of(hint("Click New rule below to make one.")), false), null);
        }

        for (int i = 27; i < 36; i++) {
            menu.set(i, icon(Items.GRAY_STAINED_GLASS_PANE, text("Nearby matches below", ChatFormatting.GRAY),
                List.of(), false), null);
        }
        menu.set(27, icon(Items.RECOVERY_COMPASS, text("Pokémon tracker", ChatFormatting.GOLD), List.of(
            hint("Where a Pokémon spawns, and teleport"), hint("to the nearest spot for it")), false),
            right -> menu.view.pick(PdaMenu.Picker.TRACKER));
        menu.set(28, icon(Items.GRASS_BLOCK, text("What spawns here?", ChatFormatting.GREEN), List.of(
            hint("Everything that can spawn in"), hint("the biome you're standing in")), false),
            right -> menu.view.go(PdaMenu.Screen.HERE));
        List<PdaTracker.Result> results = PdaTracker.results(player);
        for (int i = 0; i < Math.min(9, results.size()); i++) {
            PdaTracker.Result result = results.get(i);
            boolean selected = result.entity().getUUID().equals(PdaTracker.selectedId(player));
            int distance = (int) Math.round(Math.sqrt(result.entity().distanceToSqr(player)));
            List<Component> lore = List.of(
                text(distance + " blocks away", ChatFormatting.GRAY),
                dim("Rule " + (result.rule() + 1)),
                hint(selected ? "The needle points here" : "Click: point the needle here"));
            menu.set(36 + i, icon(PokemonItem.Companion.from(result.entity().getPokemon().getSpecies()),
                PdaCatalog.name(result.entity().getPokemon()).withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE),
                lore, selected), right -> PdaTracker.select(player, result.entity().getUUID()));
        }
        if (results.isEmpty()) {
            menu.set(40, icon(Items.LIGHT_GRAY_DYE, text("No matches nearby", ChatFormatting.GRAY),
                List.of(hint("Pings work while a PDA is in your inventory.")), false), null);
        }

        if (rules.size() < PdaSettings.MAX_RULES) {
            menu.set(45, icon(Items.WRITABLE_BOOK, text("New rule", ChatFormatting.GREEN), List.of(), false), right -> {
                PdaSettings current = Pda.settings(player);
                Pda.save(player, current.addRule(PdaRule.empty()));
                menu.view.rule = current.rules().size();
                menu.view.go(PdaMenu.Screen.RULE);
            });
        }
        int reminders = HabitatReminders.get(player).size();
        menu.set(29, icon(Items.MOSS_BLOCK, text("Habitat planner", ChatFormatting.DARK_GREEN), List.of(
            hint("Which habitats spawn a Pokémon, and when"),
            dim(reminders + " reminder" + (reminders == 1 ? "" : "s") + " set")), false),
            right -> menu.view.pick(PdaMenu.Picker.PLANNER));
        int wishes = Wishboard.get(player.server).wishes().size();
        menu.set(30, icon(Items.LECTERN, text("Wishboard", ChatFormatting.LIGHT_PURPLE), List.of(
            hint("Pokémon your friends are looking for"),
            dim(wishes + " wish" + (wishes == 1 ? "" : "es") + " posted")), false),
            right -> menu.view.go(PdaMenu.Screen.WISHBOARD));
        menu.set(47, icon(Items.SPYGLASS, text("Range: " + settings.radius() + " blocks", ChatFormatting.AQUA),
            List.of(hint("Click to change")), false), right -> Pda.save(player, Pda.settings(player).nextRadius()));
        menu.set(48, icon(Items.REPEATER, text("Repeat: " + settings.repeat().displayName, ChatFormatting.AQUA), List.of(
            hint("The selected result's sound repeats,"),
            hint("faster the closer you get."),
            dim("Always: while the PDA is in your inventory"),
            dim("While held: only with it in your hand"),
            dim("Off: one ping when found"),
            hint("Click to change")), false), right -> Pda.save(player, Pda.settings(player).nextRepeat()));
        menu.set(49, icon(settings.muted() ? Items.GRAY_DYE : Items.LIME_DYE,
            text(settings.muted() ? "Pings: muted" : "Pings: on", settings.muted() ? ChatFormatting.RED : ChatFormatting.GREEN),
            List.of(hint("Click to " + (settings.muted() ? "unmute" : "mute")),
                dim("Muted still tracks and points, without sounds or chat.")), false),
            right -> Pda.save(player, Pda.settings(player).withMuted(!Pda.settings(player).muted())));
        menu.set(50, icon(Items.FIREWORK_ROCKET,
            text(settings.announcements() ? "Catch news: on" : "Catch news: off",
                settings.announcements() ? ChatFormatting.GREEN : ChatFormatting.RED),
            List.of(hint("Chat news when anyone catches a shiny,"), hint("the server's first of a species,"),
                hint("or posts a wish."), hint("Click to turn " + (settings.announcements() ? "off" : "on"))), false),
            right -> Pda.save(player, Pda.settings(player).withAnnouncements(!Pda.settings(player).announcements())));
        menu.set(52, icon(Items.BOOK, text("How it works", ChatFormatting.GOLD), List.of(
            hint("Right-click the PDA: open this menu"),
            hint("Sneak + right-click: point at the next result"),
            hint("Hold it: results show above the hotbar"),
            hint("Pings work while it's anywhere in your inventory"),
            hint("Rules are checked top to bottom; the first match wins")), false), null);
        menu.set(53, icon(Items.IRON_DOOR, text("Close", ChatFormatting.WHITE), List.of(), false),
            right -> player.closeContainer());
    }
}
