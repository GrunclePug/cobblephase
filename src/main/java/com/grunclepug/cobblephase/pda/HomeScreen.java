package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.compat.Rct;
import com.grunclepug.cobblephase.habitat.HabitatPhases;
import com.grunclepug.cobblephase.habitat.HabitatReminders;
import com.grunclepug.cobblephase.world.Outbreaks;
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
        menu.set(31, icon(Items.KNOWLEDGE_BOOK, text("Server Pokédex", ChatFormatting.AQUA), List.of(
            hint("What nobody has caught yet,"), hint("and who caught each one first")), false),
            right -> menu.view.go(PdaMenu.Screen.LEDGER));
        menu.set(32, icon(Items.DRAGON_BREATH, text("Evolution helper", ChatFormatting.LIGHT_PURPLE), List.of(
            hint("What your party needs to evolve;"), hint("pin one to be told when it's ready")), false), right -> {
                menu.view.partyEvolution = true;
                menu.view.go(PdaMenu.Screen.PARTY);
            });
        menu.set(33, icon(Items.IRON_CHESTPLATE, text("EV helper", ChatFormatting.LIGHT_PURPLE), List.of(
            hint("Your party's EVs, and which"), hint("Pokémon to battle for each stat")), false), right -> {
                menu.view.partyEvolution = false;
                menu.view.go(PdaMenu.Screen.PARTY);
            });
        menu.set(34, icon(Items.MAP, text("News", ChatFormatting.DARK_AQUA), List.of(
            hint("Recent firsts, shinies, wishes"), hint("and outbreaks")), false),
            right -> menu.view.go(PdaMenu.Screen.NEWS));
        int alerts = settings.alerts().size();
        menu.set(35, icon(Items.CLOCK, text("Condition alerts", ChatFormatting.YELLOW), List.of(
            hint("Get pinged when it next thunders,"), hint("turns night, a full moon rises..."),
            dim(alerts + " alert" + (alerts == 1 ? "" : "s") + " set")), alerts > 0),
            right -> menu.view.go(PdaMenu.Screen.ALERTS));
        Species outbreak = Outbreaks.current();
        String left = HabitatPhases.duration(Outbreaks.ticksLeft(player.server));
        if (outbreak != null) {
            menu.set(46, icon(PokemonItem.Companion.from(outbreak), Component.literal("Outbreak: ").withStyle(ChatFormatting.GOLD)
                .append(outbreak.getTranslatedName().withStyle(ChatFormatting.AQUA)), List.of(
                hint("Spawning " + (int) Outbreaks.MULTIPLIER + "× as often for " + left + " more."),
                hint("Click: open it in the tracker")), true), right -> {
                    menu.view.trackerSpecies = outbreak.getResourceIdentifier();
                    menu.view.trackerBack = PdaMenu.Screen.HOME;
                    menu.view.go(PdaMenu.Screen.TRACKER);
                });
        } else {
            menu.set(46, icon(Items.GRAY_DYE, text("No outbreak right now", ChatFormatting.GRAY),
                List.of(dim("The next one starts in about " + left + ".")), false), null);
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
        if (Rct.available()) {
            List<String> next = Rct.nextTrainers(player);
            long until = Rct.ticksUntilVisit(player);
            List<Component> lore = new ArrayList<>();
            lore.add(hint(next.isEmpty() ? "No next trainer in your RCT series" : "Next: " + String.join(" or ", next)));
            lore.add(!settings.trainerVisits() ? dim("Visits are off (Settings)")
                : until < 0 ? dim("Visits start a while after you log in")
                : hint("Visits you in about " + HabitatPhases.duration(until)));
            lore.add(dim("Every 20–40 minutes your next trainer"));
            lore.add(dim("turns up within 10 blocks to battle."));
            boolean op = player.hasPermissions(2);
            if (op) {
                lore.add(hint("Operator: click to call one now"));
            }
            menu.set(47, icon(Items.TARGET, text("Trainer visits", ChatFormatting.RED), lore, false), !op ? null : right -> {
                player.closeContainer();
                Rct.visitNow(player);
            });
        }
        menu.set(49, icon(Items.COMPARATOR, text("Settings", ChatFormatting.AQUA), List.of(
            hint("Range, repeat, mute, catch news,"), hint("shiny guard, trainer visits")), false),
            right -> menu.view.go(PdaMenu.Screen.SETTINGS));
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
