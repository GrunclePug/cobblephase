package com.grunclepug.cobblephase.pda;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

/**
 * The PDA's menu: a vanilla 6-row chest screen whose slots act as buttons, so clients need nothing installed.
 * Screens re-render in place; nothing in the top grid can be taken or replaced.
 */
public final class PdaMenu extends ChestMenu {
    public enum Screen { HOME, RULE, SPECIES, SOUND }

    private static final Component TITLE = Component.literal("Trainer PDA");
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int BOTTOM = 45;

    private final ServerPlayer player;
    private final SimpleContainer grid;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private Screen screen = Screen.HOME;
    private int rule = -1;
    private int page;
    private String filter;
    private boolean selectedOnly;
    private boolean confirmDelete;

    @FunctionalInterface
    private interface Button {
        void click(boolean right);
    }

    private PdaMenu(int containerId, Inventory inventory, ServerPlayer player, SimpleContainer grid) {
        super(MenuType.GENERIC_9x6, containerId, inventory, grid, 6);
        this.player = player;
        this.grid = grid;
    }

    public static void open(ServerPlayer player, Screen screen) {
        open(player, screen, -1, null);
    }

    static void openSpecies(ServerPlayer player, int rule, String filter) {
        open(player, Screen.SPECIES, rule, filter);
    }

    private static void open(ServerPlayer player, Screen screen, int rule, String filter) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
            PdaMenu menu = new PdaMenu(id, inventory, player, new SimpleContainer(SIZE));
            menu.screen = screen;
            menu.rule = rule;
            menu.filter = filter;
            menu.render();
            return menu;
        }, TITLE));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player clicker) {
        if (slot >= 0 && slot < SIZE) {
            Button action = buttons.get(slot);
            if (action != null && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                action.click(button == 1);
                if (player.containerMenu == this) {
                    render();
                }
            }
            sendAllDataToRemote();
            return;
        }
        // These can pull items into or out of the grid; normal clicks in the player's own inventory are fine.
        if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT) {
            sendAllDataToRemote();
            return;
        }
        super.clicked(slot, button, type, clicker);
    }

    @Override
    public ItemStack quickMoveStack(Player clicker, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player clicker) {
        return true;
    }

    // Rendering

    private void render() {
        buttons.clear();
        for (int i = 0; i < SIZE; i++) {
            grid.setItem(i, ItemStack.EMPTY);
        }
        PdaSettings settings = Pda.settings(player);
        if (screen != Screen.HOME && (rule < 0 || rule >= settings.rules().size())) {
            screen = Screen.HOME;
        }
        switch (screen) {
            case HOME -> renderHome(settings);
            case RULE -> renderRule(settings.rules().get(rule));
            case SPECIES -> renderSpecies(settings.rules().get(rule));
            case SOUND -> renderSound(settings.rules().get(rule));
        }
        for (int i = BOTTOM; i < SIZE; i++) {
            if (grid.getItem(i).isEmpty()) {
                grid.setItem(i, icon(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "), List.of(), false));
            }
        }
    }

    private void renderHome(PdaSettings settings) {
        List<PdaRule> rules = settings.rules();
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            PdaRule r = rules.get(i);
            List<Component> lore = new ArrayList<>(summary(r));
            lore.add(Component.empty());
            lore.add(hint("Left-click: edit"));
            lore.add(hint("Right-click: turn " + (r.enabled() ? "off" : "on")));
            set(i, icon(ruleIcon(r), ruleTitle(index, r), lore, false), right -> {
                if (right) {
                    update(index, x -> x.withEnabled(!x.enabled()));
                } else {
                    edit(index);
                }
            });
        }
        if (rules.isEmpty()) {
            set(13, icon(Items.WRITABLE_BOOK, text("No ping rules yet", ChatFormatting.YELLOW),
                List.of(hint("Click New rule below to make one.")), false), null);
        }

        for (int i = 27; i < 36; i++) {
            set(i, icon(Items.GRAY_STAINED_GLASS_PANE, text("Nearby matches below", ChatFormatting.GRAY), List.of(), false), null);
        }
        List<PdaTracker.Result> results = PdaTracker.results(player);
        for (int i = 0; i < Math.min(9, results.size()); i++) {
            PdaTracker.Result result = results.get(i);
            boolean selected = result.entity().getUUID().equals(PdaTracker.selectedId(player));
            int distance = (int) Math.round(Math.sqrt(result.entity().distanceToSqr(player)));
            List<Component> lore = List.of(
                text(distance + " blocks away", ChatFormatting.GRAY),
                text("Rule " + (result.rule() + 1), ChatFormatting.DARK_GRAY),
                hint(selected ? "The needle points here" : "Click: point the needle here"));
            set(36 + i, icon(PokemonItem.Companion.from(result.entity().getPokemon().getSpecies()),
                PdaCatalog.name(result.entity().getPokemon()).withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE),
                lore, selected), right -> PdaTracker.select(player, result.entity().getUUID()));
        }
        if (results.isEmpty()) {
            set(40, icon(Items.LIGHT_GRAY_DYE, text("No matches nearby", ChatFormatting.GRAY),
                List.of(hint("Pings work while a PDA is in your inventory.")), false), null);
        }

        if (rules.size() < PdaSettings.MAX_RULES) {
            set(45, icon(Items.WRITABLE_BOOK, text("New rule", ChatFormatting.GREEN), List.of(), false), right -> {
                PdaSettings current = Pda.settings(player);
                Pda.save(player, current.addRule(PdaRule.empty()));
                edit(current.rules().size());
            });
        }
        set(47, icon(Items.SPYGLASS, text("Range: " + settings.radius() + " blocks", ChatFormatting.AQUA),
            List.of(hint("Click to change")), false), right -> Pda.save(player, Pda.settings(player).nextRadius()));
        set(49, icon(settings.muted() ? Items.GRAY_DYE : Items.LIME_DYE,
            text(settings.muted() ? "Pings: muted" : "Pings: on", settings.muted() ? ChatFormatting.RED : ChatFormatting.GREEN),
            List.of(hint("Click to " + (settings.muted() ? "unmute" : "mute")),
                text("Muted still tracks and points, without sounds or chat.", ChatFormatting.DARK_GRAY)), false),
            right -> Pda.save(player, Pda.settings(player).withMuted(!Pda.settings(player).muted())));
        set(51, icon(Items.BOOK, text("How it works", ChatFormatting.GOLD), List.of(
            hint("Right-click the PDA: open this menu"),
            hint("Sneak + right-click: point at the next result"),
            hint("Hold it: results show above the hotbar"),
            hint("Pings work while it's anywhere in your inventory"),
            hint("Rules are checked top to bottom; the first match wins")), false), null);
        set(53, icon(Items.IRON_DOOR, text("Close", ChatFormatting.WHITE), List.of(), false), right -> player.closeContainer());
    }

    private void renderRule(PdaRule r) {
        List<Component> header = new ArrayList<>(summary(r));
        header.add(Component.empty());
        header.add(hint("A Pokémon pings when it matches all of:"));
        header.add(hint("  a chosen Pokémon or group (if any),"));
        header.add(hint("  a chosen rarity (if any), and shiny (if on)."));
        set(4, icon(ruleIcon(r), ruleTitle(rule, r), header, false), null);

        Species first = firstSpecies(r);
        set(10, icon(first == null ? new ItemStack(Items.NAME_TAG) : PokemonItem.Companion.from(first),
            text("Pokémon: " + r.species().size() + " chosen", ChatFormatting.AQUA),
            List.of(hint("Click to choose")), false), right -> {
                screen = Screen.SPECIES;
                page = 0;
                filter = null;
                selectedOnly = false;
            });
        for (int i = 0; i < PdaCatalog.LABELS.size(); i++) {
            PdaCatalog.Label label = PdaCatalog.LABELS.get(i);
            boolean on = r.labels().contains(label.id());
            set(12 + i, icon(label.icon(), toggleTitle(label.displayName(), on),
                List.of(hint("Matches any " + label.displayName() + " Pokémon")), on),
                right -> update(rule, x -> x.toggleLabel(label.id())));
        }

        Set<String> buckets = new LinkedHashSet<>(PdaCatalog.buckets());
        buckets.addAll(r.buckets());
        int slot = 19;
        for (String bucket : buckets) {
            if (slot > 25) {
                break;
            }
            boolean on = r.buckets().contains(bucket);
            set(slot++, icon(PdaCatalog.bucketIcon(bucket), toggleTitle(PdaCatalog.bucketName(bucket), on),
                List.of(hint("Only Pokémon that spawned as " + PdaCatalog.bucketName(bucket)),
                    text("None chosen: any rarity", ChatFormatting.DARK_GRAY)), on),
                right -> update(rule, x -> x.toggleBucket(bucket)));
        }

        set(29, icon(Items.NETHER_STAR, toggleTitle("Shiny only", r.shinyOnly()), List.of(), r.shinyOnly()),
            right -> update(rule, x -> x.withShinyOnly(!x.shinyOnly())));
        PdaSound sound = PdaSound.byId(r.sound());
        set(31, icon(sound.icon, text("Sound: " + sound.displayName, ChatFormatting.AQUA),
            List.of(hint("Click to choose")), false), right -> screen = Screen.SOUND);
        set(33, icon(r.enabled() ? Items.LIME_DYE : Items.GRAY_DYE,
            text(r.enabled() ? "Rule: on" : "Rule: off", r.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED),
            List.of(hint("Click to turn " + (r.enabled() ? "off" : "on"))), false),
            right -> update(rule, x -> x.withEnabled(!x.enabled())));

        set(45, icon(Items.ARROW, text("Back", ChatFormatting.WHITE), List.of(), false), right -> screen = Screen.HOME);
        if (rule > 0) {
            set(48, icon(Items.SPECTRAL_ARROW, text("Move up", ChatFormatting.WHITE),
                List.of(hint("Earlier rules win when several match")), false), right -> move(-1));
        }
        if (rule < Pda.settings(player).rules().size() - 1) {
            set(50, icon(Items.SPECTRAL_ARROW, text("Move down", ChatFormatting.WHITE), List.of(), false), right -> move(1));
        }
        boolean confirming = confirmDelete;
        set(53, icon(Items.TNT, text(confirming ? "Click again to delete" : "Delete rule", ChatFormatting.RED),
            List.of(), confirming), right -> {
                if (confirming) {
                    Pda.save(player, Pda.settings(player).removeRule(rule));
                    screen = Screen.HOME;
                    rule = -1;
                } else {
                    confirmDelete = true;
                }
            });
        if (!confirming) {
            return;
        }
        // Any other click cancels a pending delete.
        buttons.replaceAll((s, action) -> s == 53 ? action : right -> {
            confirmDelete = false;
            action.click(right);
        });
    }

    private void renderSpecies(PdaRule r) {
        String key = filter == null ? "" : PdaCatalog.searchKey(filter);
        List<Species> list = new ArrayList<>();
        for (Species species : PdaCatalog.species()) {
            if (selectedOnly && !r.species().contains(species.getResourceIdentifier())) {
                continue;
            }
            if (!key.isEmpty() && !PdaCatalog.searchKey(species.getName()).contains(key)
                && !PdaCatalog.searchKey(species.getResourceIdentifier().getPath()).contains(key)) {
                continue;
            }
            list.add(species);
        }
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < list.size(); i++) {
            Species species = list.get(page * PAGE_SIZE + i);
            ResourceLocation id = species.getResourceIdentifier();
            boolean on = r.species().contains(id);
            set(i, icon(PokemonItem.Companion.from(species),
                species.getTranslatedName().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                List.of(text("#" + species.getNationalPokedexNumber(), ChatFormatting.DARK_GRAY),
                    on ? text("✔ Chosen: click to remove", ChatFormatting.GREEN) : hint("Click to choose")), on),
                right -> update(rule, x -> x.toggleSpecies(id)));
        }
        if (list.isEmpty()) {
            set(22, icon(Items.BARRIER, text("No Pokémon match", ChatFormatting.RED), List.of(), false), null);
        }

        set(45, icon(Items.ARROW, text("Back", ChatFormatting.WHITE), List.of(), false), right -> screen = Screen.RULE);
        if (page > 0) {
            set(46, icon(Items.SPECTRAL_ARROW, text("◀ Previous page", ChatFormatting.WHITE), List.of(), false), right -> page--);
        }
        set(47, icon(Items.PAPER, text("Page " + (page + 1) + "/" + pages, ChatFormatting.WHITE),
            List.of(hint(list.size() + " Pokémon")), false), null);
        if (page < pages - 1) {
            set(48, icon(Items.SPECTRAL_ARROW, text("Next page ▶", ChatFormatting.WHITE), List.of(), false), right -> page++);
        }
        set(50, icon(Items.SPYGLASS, text("Search", ChatFormatting.AQUA), List.of(
            hint("Type names in chat; exact names are added directly"),
            filter == null ? text("No search", ChatFormatting.DARK_GRAY) : text("Showing: \"" + filter + "\"", ChatFormatting.YELLOW)),
            false), right -> PdaSearch.start(player, rule));
        if (filter != null) {
            set(51, icon(Items.BRUSH, text("Clear search", ChatFormatting.WHITE), List.of(), false), right -> {
                filter = null;
                page = 0;
            });
        }
        set(52, icon(Items.ENDER_EYE, toggleTitle("Chosen only (" + r.species().size() + ")", selectedOnly),
            List.of(hint("Show only the Pokémon this rule has")), selectedOnly), right -> {
                selectedOnly = !selectedOnly;
                page = 0;
            });
    }

    private void renderSound(PdaRule r) {
        PdaSound[] sounds = PdaSound.values();
        for (int i = 0; i < sounds.length; i++) {
            PdaSound sound = sounds[i];
            boolean on = sound.id.equals(PdaSound.byId(r.sound()).id);
            set(18 + i, icon(sound.icon, toggleTitle(sound.displayName, on), List.of(hint("Click to hear and choose")), on),
                right -> {
                    sound.play(player, null);
                    update(rule, x -> x.withSound(sound.id));
                });
        }
        set(45, icon(Items.ARROW, text("Back", ChatFormatting.WHITE), List.of(), false), right -> screen = Screen.RULE);
    }

    // Actions

    private void edit(int index) {
        screen = Screen.RULE;
        rule = index;
        confirmDelete = false;
    }

    private void move(int offset) {
        Pda.save(player, Pda.settings(player).moveRule(rule, offset));
        rule += offset;
    }

    private void update(int index, UnaryOperator<PdaRule> change) {
        Pda.save(player, Pda.settings(player).updateRule(index, change));
    }

    // Item helpers

    private void set(int slot, ItemStack stack, Button action) {
        grid.setItem(slot, stack);
        if (action != null) {
            buttons.put(slot, action);
        }
    }

    private static ItemStack icon(ItemLike item, Component name, List<Component> lore, boolean glint) {
        return icon(new ItemStack(item), name, lore, glint);
    }

    private static ItemStack icon(ItemStack stack, Component name, List<Component> lore, boolean glint) {
        stack.set(DataComponents.CUSTOM_NAME, Component.empty().withStyle(s -> s.withItalic(false)).append(name));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream()
            .map(line -> (Component) Component.empty().withStyle(s -> s.withItalic(false)).append(line)).toList()));
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static ItemStack ruleIcon(PdaRule r) {
        Species first = firstSpecies(r);
        if (first != null) {
            return PokemonItem.Companion.from(first);
        }
        if (r.shinyOnly()) {
            return new ItemStack(Items.NETHER_STAR);
        }
        if (!r.labels().isEmpty()) {
            for (PdaCatalog.Label label : PdaCatalog.LABELS) {
                if (r.labels().contains(label.id())) {
                    return new ItemStack(label.icon());
                }
            }
        }
        if (!r.buckets().isEmpty()) {
            return new ItemStack(PdaCatalog.bucketIcon(r.buckets().getFirst()));
        }
        return new ItemStack(Items.PAPER);
    }

    private static Component ruleTitle(int index, PdaRule r) {
        ChatFormatting color = !r.complete() ? ChatFormatting.YELLOW : r.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED;
        String status = !r.complete() ? "incomplete" : r.enabled() ? "on" : "off";
        return text("Rule " + (index + 1) + " (" + status + ")", color);
    }

    /** One line per criterion, shared by the rule list and the rule editor. */
    private static List<Component> summary(PdaRule r) {
        List<Component> lines = new ArrayList<>();
        if (!r.species().isEmpty()) {
            MutableComponent names = text("Pokémon: ", ChatFormatting.GRAY);
            int shown = 0;
            for (ResourceLocation id : r.species()) {
                if (shown == 5) {
                    names.append(text(", +" + (r.species().size() - 5) + " more", ChatFormatting.GRAY));
                    break;
                }
                Species species = PokemonSpecies.getByIdentifier(id);
                names.append(text(shown++ == 0 ? "" : ", ", ChatFormatting.GRAY));
                names.append(species == null ? text(id.toString(), ChatFormatting.WHITE)
                    : species.getTranslatedName().withStyle(ChatFormatting.WHITE));
            }
            lines.add(names);
        }
        if (!r.labels().isEmpty()) {
            List<String> names = r.labels().stream().map(PdaCatalog::labelName).toList();
            lines.add(text((r.species().isEmpty() ? "Groups: " : "Or groups: ") + String.join(", ", names), ChatFormatting.GRAY));
        }
        lines.add(text("Rarity: " + (r.buckets().isEmpty() ? "any"
            : String.join(", ", r.buckets().stream().map(PdaCatalog::bucketName).toList())), ChatFormatting.GRAY));
        if (r.shinyOnly()) {
            lines.add(text("Shiny only", ChatFormatting.GOLD));
        }
        lines.add(text("Sound: " + PdaSound.byId(r.sound()).displayName, ChatFormatting.GRAY));
        if (!r.complete()) {
            lines.add(text("Choose a Pokémon, group, rarity or shiny to start pinging.", ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static Species firstSpecies(PdaRule r) {
        for (ResourceLocation id : r.species()) {
            Species species = PokemonSpecies.getByIdentifier(id);
            if (species != null) {
                return species;
            }
        }
        return null;
    }

    private static Component toggleTitle(String name, boolean on) {
        return text((on ? "✔ " : "") + name, on ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    private static MutableComponent text(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private static Component hint(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }
}
