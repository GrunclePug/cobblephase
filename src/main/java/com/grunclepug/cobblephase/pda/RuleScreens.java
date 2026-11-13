package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The rule editor and its sub-pages: filters, forms, natures and sound. */
final class RuleScreens {
    private RuleScreens() {
    }

    static void rule(PdaMenu menu, PdaRule rule) {
        PdaMenu.View view = menu.view;
        List<Component> header = new ArrayList<>(summary(rule));
        header.add(Component.empty());
        header.add(hint("A Pokémon pings when it matches all of:"));
        header.add(hint("  a chosen Pokémon or group (if any),"));
        header.add(hint("  a chosen rarity (if any), shiny,"));
        header.add(hint("  not caught yet and the filters (if set)."));
        menu.set(4, icon(ruleIcon(rule), ruleTitle(view.rule, rule), header, false), null);

        Species first = firstSpecies(rule);
        menu.set(10, icon(first == null ? new ItemStack(Items.NAME_TAG) : PokemonItem.Companion.from(first),
            text("Pokémon: " + rule.species().size() + " chosen", ChatFormatting.AQUA),
            List.of(hint("Click to choose")), false), right -> view.pick(PdaMenu.Picker.RULE));
        for (int i = 0; i < PdaCatalog.LABELS.size(); i++) {
            PdaCatalog.Label label = PdaCatalog.LABELS.get(i);
            boolean on = rule.labels().contains(label.id());
            menu.set(12 + i, icon(label.icon(), toggleTitle(label.displayName(), on),
                List.of(hint("Matches any " + label.displayName() + " Pokémon")), on),
                right -> menu.updateRule(r -> r.toggleLabel(label.id())));
        }
        int filters = rule.filters().count();
        menu.set(16, icon(Items.HOPPER, text("More filters" + (filters > 0 ? " (" + filters + " set)" : ""), ChatFormatting.AQUA),
            List.of(hint("Type, form, nature, level,"), hint("perfect IVs, hidden ability"), hint("Click to open")),
            filters > 0), right -> view.go(PdaMenu.Screen.FILTERS));

        Set<String> buckets = new LinkedHashSet<>(PdaCatalog.buckets());
        buckets.addAll(rule.buckets());
        int slot = 19;
        for (String bucket : buckets) {
            if (slot > 25) {
                break;
            }
            boolean on = rule.buckets().contains(bucket);
            menu.set(slot++, icon(PdaCatalog.bucketIcon(bucket), toggleTitle(PdaCatalog.bucketName(bucket), on),
                List.of(hint("Only Pokémon that spawned as " + PdaCatalog.bucketName(bucket)),
                    dim("None chosen: any rarity")), on),
                right -> menu.updateRule(r -> r.toggleBucket(bucket)));
        }

        menu.set(28, icon(Items.NETHER_STAR, toggleTitle("Shiny only", rule.shinyOnly()), List.of(), rule.shinyOnly()),
            right -> menu.updateRule(r -> r.withShinyOnly(!r.shinyOnly())));
        menu.set(30, icon(CobblemonItems.POKEDEX_RED, toggleTitle("Not caught yet", rule.uncaughtOnly()),
            List.of(hint("Only species missing from your Pokédex"),
                dim("On its own: pings for every species you still need")), rule.uncaughtOnly()),
            right -> menu.updateRule(r -> r.withUncaughtOnly(!r.uncaughtOnly())));
        PdaSound sound = PdaSound.byId(rule.sound());
        menu.set(32, icon(sound.icon, text("Sound: " + sound.displayName, ChatFormatting.AQUA),
            List.of(hint("Click to choose")), false), right -> view.go(PdaMenu.Screen.SOUND));
        menu.set(34, icon(rule.enabled() ? Items.LIME_DYE : Items.GRAY_DYE,
            text(rule.enabled() ? "Rule: on" : "Rule: off", rule.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED),
            List.of(hint("Click to turn " + (rule.enabled() ? "off" : "on"))), false),
            right -> menu.updateRule(r -> r.withEnabled(!r.enabled())));

        menu.back(PdaMenu.Screen.HOME);
        int count = Pda.settings(menu.player).rules().size();
        if (view.rule > 0) {
            menu.set(48, icon(Items.SPECTRAL_ARROW, text("Move up", ChatFormatting.WHITE),
                List.of(hint("Earlier rules win when several match")), false), right -> move(menu, -1));
        }
        if (view.rule < count - 1) {
            menu.set(50, icon(Items.SPECTRAL_ARROW, text("Move down", ChatFormatting.WHITE), List.of(), false),
                right -> move(menu, 1));
        }
        boolean confirming = view.confirmDelete;
        menu.set(53, icon(Items.TNT, text(confirming ? "Click again to delete" : "Delete rule", ChatFormatting.RED),
            List.of(), confirming), right -> {
                if (confirming) {
                    Pda.save(menu.player, Pda.settings(menu.player).removeRule(view.rule));
                    view.rule = -1;
                    view.go(PdaMenu.Screen.HOME);
                } else {
                    view.confirmDelete = true;
                }
            });
        if (confirming) {
            menu.beforeOtherClicks(53, () -> view.confirmDelete = false);
        }
    }

    private static void move(PdaMenu menu, int offset) {
        Pda.save(menu.player, Pda.settings(menu.player).moveRule(menu.view.rule, offset));
        menu.view.rule += offset;
    }

    static void filters(PdaMenu menu, PdaRule rule) {
        PdaFilters filters = rule.filters();
        List<ElementalType> types = ElementalTypes.all();
        for (int i = 0; i < Math.min(19, types.size()); i++) {
            ElementalType type = types.get(i);
            String key = type.getName().toLowerCase(Locale.ROOT);
            boolean on = filters.types().contains(key);
            menu.set(i, icon(typeIcon(key), toggleTitle(type.getDisplayName(), on),
                List.of(hint("Matches Pokémon with this type")), on),
                right -> menu.updateFilters(f -> f.toggleType(key)));
        }

        menu.set(20, icon(Items.NAME_TAG, text("Forms: " + chosen(filters.forms().size()), ChatFormatting.AQUA),
            List.of(hint("Alolan, Galarian, Mega and more"), hint("Click to choose")), !filters.forms().isEmpty()),
            right -> menu.view.go(PdaMenu.Screen.FORMS));
        menu.set(22, icon(Items.PAPER, text("Natures: " + chosen(filters.natures().size()), ChatFormatting.AQUA),
            List.of(hint("Click to choose")), !filters.natures().isEmpty()),
            right -> menu.view.go(PdaMenu.Screen.NATURES));
        menu.set(24, icon(Items.BLAZE_POWDER, toggleTitle("Hidden ability", filters.hiddenAbility()),
            List.of(hint("Only Pokémon with their hidden ability")), filters.hiddenAbility()),
            right -> menu.updateFilters(f -> f.withHiddenAbility(!f.hiddenAbility())));

        List<Component> step = List.of(hint("Left-click: +" + PdaFilters.LEVEL_STEP),
            hint("Right-click: -" + PdaFilters.LEVEL_STEP));
        menu.set(29, icon(Items.EXPERIENCE_BOTTLE,
            text("Min level: " + (filters.minLevel() > 0 ? filters.minLevel() : "any"), ChatFormatting.AQUA), step,
            filters.minLevel() > 0), right -> menu.updateFilters(f -> f.stepMinLevel(right ? -PdaFilters.LEVEL_STEP : PdaFilters.LEVEL_STEP)));
        menu.set(31, icon(Items.EXPERIENCE_BOTTLE,
            text("Max level: " + (filters.maxLevel() > 0 ? filters.maxLevel() : "any"), ChatFormatting.AQUA), step,
            filters.maxLevel() > 0), right -> menu.updateFilters(f -> f.stepMaxLevel(right ? -PdaFilters.LEVEL_STEP : PdaFilters.LEVEL_STEP)));
        menu.set(33, icon(Items.DIAMOND,
            text("Perfect IVs: " + (filters.minPerfectIvs() > 0 ? "at least " + filters.minPerfectIvs() : "any"), ChatFormatting.AQUA),
            List.of(hint("How many stats must have a 31 IV"), hint("Left-click: +1"), hint("Right-click: -1")),
            filters.minPerfectIvs() > 0), right -> menu.updateFilters(f -> f.stepPerfectIvs(right ? -1 : 1)));

        menu.back(PdaMenu.Screen.RULE);
        if (!filters.isEmpty()) {
            menu.set(53, icon(Items.BARRIER, text("Clear filters", ChatFormatting.RED), List.of(), false),
                right -> menu.updateRule(r -> r.withFilters(PdaFilters.NONE)));
        }
    }

    static void forms(PdaMenu menu, PdaRule rule) {
        List<PdaCatalog.Form> forms = PdaCatalog.forms();
        int start = menu.pager(forms.size());
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < forms.size(); i++) {
            PdaCatalog.Form form = forms.get(start + i);
            boolean on = rule.filters().forms().contains(form.key());
            List<Component> lore = new ArrayList<>();
            lore.add(dim("e.g. " + String.join(", ", form.examples())));
            lore.add(on ? text("✔ Chosen: click to remove", ChatFormatting.GREEN) : hint("Click to choose"));
            menu.set(i, icon(Items.NAME_TAG, toggleTitle(form.displayName(), on), lore, on),
                right -> menu.updateFilters(f -> f.toggleForm(form.key())));
        }
        menu.back(PdaMenu.Screen.FILTERS);
    }

    static void natures(PdaMenu menu, PdaRule rule) {
        List<Nature> natures = new ArrayList<>(Natures.all());
        natures.sort(Comparator.comparing(nature -> nature.getName().toString()));
        for (int i = 0; i < Math.min(PdaMenu.PAGE_SIZE, natures.size()); i++) {
            Nature nature = natures.get(i);
            boolean on = rule.filters().natures().contains(nature.getName());
            menu.set(i, icon(Items.PAPER, toggleTitle(Component.translatable(nature.getDisplayName()), on), List.of(), on),
                right -> menu.updateFilters(f -> f.toggleNature(nature.getName())));
        }
        menu.back(PdaMenu.Screen.FILTERS);
    }

    static void sound(PdaMenu menu, PdaRule rule) {
        PdaSound[] sounds = PdaSound.values();
        for (int i = 0; i < sounds.length; i++) {
            PdaSound sound = sounds[i];
            boolean on = sound == PdaSound.byId(rule.sound());
            menu.set(18 + i, icon(sound.icon, toggleTitle(sound.displayName, on), List.of(hint("Click to hear and choose")), on),
                right -> {
                    sound.play(menu.player, null);
                    menu.updateRule(r -> r.withSound(sound.id));
                });
        }
        menu.back(PdaMenu.Screen.RULE);
    }

    // Shared with the home screen

    static ItemStack ruleIcon(PdaRule rule) {
        Species first = firstSpecies(rule);
        if (first != null) {
            return PokemonItem.Companion.from(first);
        }
        if (rule.shinyOnly()) {
            return new ItemStack(Items.NETHER_STAR);
        }
        if (rule.uncaughtOnly()) {
            return new ItemStack(CobblemonItems.POKEDEX_RED);
        }
        for (PdaCatalog.Label label : PdaCatalog.LABELS) {
            if (rule.labels().contains(label.id())) {
                return new ItemStack(label.icon());
            }
        }
        if (!rule.buckets().isEmpty()) {
            return new ItemStack(PdaCatalog.bucketIcon(rule.buckets().getFirst()));
        }
        if (!rule.filters().types().isEmpty()) {
            return new ItemStack(typeIcon(rule.filters().types().getFirst()));
        }
        return new ItemStack(Items.PAPER);
    }

    static Component ruleTitle(int index, PdaRule rule) {
        ChatFormatting color = !rule.complete() ? ChatFormatting.YELLOW : rule.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED;
        String status = !rule.complete() ? "incomplete" : rule.enabled() ? "on" : "off";
        return text("Rule " + (index + 1) + " (" + status + ")", color);
    }

    /** One line per criterion, shared by the rule list and the rule editor. */
    static List<Component> summary(PdaRule rule) {
        List<Component> lines = new ArrayList<>();
        if (!rule.species().isEmpty()) {
            MutableComponent names = text("Pokémon: ", ChatFormatting.GRAY);
            int shown = 0;
            for (ResourceLocation id : rule.species()) {
                if (shown == 5) {
                    names.append(text(", +" + (rule.species().size() - 5) + " more", ChatFormatting.GRAY));
                    break;
                }
                Species species = PokemonSpecies.getByIdentifier(id);
                names.append(text(shown++ == 0 ? "" : ", ", ChatFormatting.GRAY));
                names.append(species == null ? text(id.toString(), ChatFormatting.WHITE)
                    : species.getTranslatedName().withStyle(ChatFormatting.WHITE));
            }
            lines.add(names);
        }
        if (!rule.labels().isEmpty()) {
            List<String> names = rule.labels().stream().map(PdaCatalog::labelName).toList();
            lines.add(text((rule.species().isEmpty() ? "Groups: " : "Or groups: ") + String.join(", ", names), ChatFormatting.GRAY));
        }
        lines.add(text("Rarity: " + (rule.buckets().isEmpty() ? "any"
            : String.join(", ", rule.buckets().stream().map(PdaCatalog::bucketName).toList())), ChatFormatting.GRAY));
        if (rule.shinyOnly()) {
            lines.add(text("Shiny only", ChatFormatting.GOLD));
        }
        if (rule.uncaughtOnly()) {
            lines.add(text("Not caught yet", ChatFormatting.LIGHT_PURPLE));
        }
        lines.addAll(filterSummary(rule.filters()));
        lines.add(text("Sound: " + PdaSound.byId(rule.sound()).displayName, ChatFormatting.GRAY));
        if (!rule.complete()) {
            lines.add(text("Choose a Pokémon, group, rarity, shiny, not caught yet", ChatFormatting.YELLOW));
            lines.add(text("or a filter to start pinging.", ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static List<Component> filterSummary(PdaFilters filters) {
        List<Component> lines = new ArrayList<>();
        if (!filters.types().isEmpty()) {
            MutableComponent line = text("Types: ", ChatFormatting.GRAY);
            for (int i = 0; i < filters.types().size(); i++) {
                ElementalType type = ElementalTypes.get(filters.types().get(i));
                line.append(text(i == 0 ? "" : ", ", ChatFormatting.GRAY));
                line.append(type == null ? text(filters.types().get(i), ChatFormatting.WHITE)
                    : type.getDisplayName().copy().withStyle(ChatFormatting.WHITE));
            }
            lines.add(line);
        }
        if (!filters.forms().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String key : filters.forms()) {
                names.add(PdaCatalog.formName(key));
            }
            lines.add(text("Forms: " + String.join(", ", names), ChatFormatting.GRAY));
        }
        if (!filters.natures().isEmpty()) {
            MutableComponent line = text("Natures: ", ChatFormatting.GRAY);
            for (int i = 0; i < filters.natures().size(); i++) {
                Nature nature = Natures.getNature(filters.natures().get(i));
                line.append(text(i == 0 ? "" : ", ", ChatFormatting.GRAY));
                line.append(nature == null ? text(filters.natures().get(i).getPath(), ChatFormatting.WHITE)
                    : Component.translatable(nature.getDisplayName()).withStyle(ChatFormatting.WHITE));
            }
            lines.add(line);
        }
        if (filters.minLevel() > 0 || filters.maxLevel() > 0) {
            String range = filters.minLevel() > 0 && filters.maxLevel() > 0 ? filters.minLevel() + "–" + filters.maxLevel()
                : filters.minLevel() > 0 ? filters.minLevel() + "+" : "up to " + filters.maxLevel();
            lines.add(text("Level: " + range, ChatFormatting.GRAY));
        }
        if (filters.minPerfectIvs() > 0) {
            lines.add(text("Perfect IVs: at least " + filters.minPerfectIvs(), ChatFormatting.GRAY));
        }
        if (filters.hiddenAbility()) {
            lines.add(text("Hidden ability", ChatFormatting.GRAY));
        }
        return lines;
    }

    private static Species firstSpecies(PdaRule rule) {
        for (ResourceLocation id : rule.species()) {
            Species species = PokemonSpecies.getByIdentifier(id);
            if (species != null) {
                return species;
            }
        }
        return null;
    }

    /** Cobblemon's type gem for a type, or paper if there isn't one. */
    static Item typeIcon(String type) {
        Item gem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", type + "_gem"));
        return gem == Items.AIR ? Items.PAPER : gem;
    }

    private static String chosen(int count) {
        return count == 0 ? "any" : count + " chosen";
    }
}
