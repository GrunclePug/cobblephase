package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The PDA's menu: a vanilla 6-row chest screen whose slots act as buttons, so clients need nothing installed.
 * Screens re-render in place; nothing in the top grid can be taken or replaced. Where the player is in the menu
 * lives in a {@link View} kept per player, so it survives closing the menu for a chat prompt.
 */
public final class PdaMenu extends ChestMenu {
    public enum Screen {
        HOME, RULE, FILTERS, FORMS, NATURES, SOUND, SPECIES, PLANNER, REMINDERS, WISHBOARD, WISH_NEW, TRACKER, ROUTE_BIOMES,
        HERE, ALERTS, LEDGER, PARTY, EVOLVE, EVS, NEWS
    }

    /** What the species picker is choosing for. */
    enum Picker { RULE, PLANNER, WISH, TRACKER }

    /** How "What spawns here?" sorts. */
    enum HereSort {
        RAREST("rarest first"), WEIGHT("most common first"), NAME("name"), DEX("Pokédex number");

        final String displayName;

        HereSort(String displayName) {
            this.displayName = displayName;
        }
    }

    static final int SIZE = 54;
    static final int PAGE_SIZE = 45;
    static final int BOTTOM = 45;
    private static final Component TITLE = Component.literal("Trainer PDA");
    private static final Map<UUID, View> VIEWS = new HashMap<>();

    /** Where a player is in the menu, and anything they're part-way through. */
    static final class View {
        Screen screen = Screen.HOME;
        int rule = -1;
        int page;
        String filter;
        boolean selectedOnly;
        boolean confirmDelete;
        Picker picker = Picker.RULE;
        ResourceLocation plannerSpecies;
        ResourceLocation wishSpecies;
        boolean wishShiny;
        String wishNote = "";
        UUID confirmWish;
        ResourceLocation trackerSpecies;
        Screen trackerBack = Screen.SPECIES;
        int route;
        boolean hereNow;
        boolean hereUncaught;
        String hereBucket;
        String hereType;
        HereSort hereSort = HereSort.RAREST;
        boolean ledgerMissing = true;
        boolean partyEvolution = true;
        UUID partyPokemon;
        String evStat;

        void go(Screen next) {
            screen = next;
            page = 0;
            confirmDelete = false;
            confirmWish = null;
        }

        void pick(Picker mode) {
            picker = mode;
            filter = null;
            selectedOnly = false;
            go(Screen.SPECIES);
        }
    }

    @FunctionalInterface
    interface Button {
        void click(boolean right);
    }

    final ServerPlayer player;
    final View view;
    private final SimpleContainer grid;
    private final Map<Integer, Button> buttons = new HashMap<>();

    private PdaMenu(int containerId, Inventory inventory, ServerPlayer player, View view, SimpleContainer grid) {
        super(MenuType.GENERIC_9x6, containerId, inventory, grid, 6);
        this.player = player;
        this.view = view;
        this.grid = grid;
    }

    /** Opens the menu fresh at {@code screen}. */
    public static void open(ServerPlayer player, Screen screen) {
        View view = new View();
        view.screen = screen;
        VIEWS.put(player.getUUID(), view);
        show(player, view);
    }

    /** Back to where the player was, unless they've since opened something else or died. */
    static void reopenIfIdle(ServerPlayer player) {
        View view = VIEWS.get(player.getUUID());
        if (view != null && player.isAlive() && player.containerMenu == player.inventoryMenu) {
            show(player, view);
        }
    }

    static void forget(ServerPlayer player) {
        VIEWS.remove(player.getUUID());
    }

    private static void show(ServerPlayer player, View view) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
            PdaMenu menu = new PdaMenu(id, inventory, player, view, new SimpleContainer(SIZE));
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

    /** Called after each scan so the home screen's nearby list stays current while open. */
    void refreshResults() {
        if (view.screen == Screen.HOME) {
            render();
        }
    }

    /** Re-renders the player's open PDA menu if it shows one of {@code screens}, so no button acts on stale data. */
    public static void refreshIfShowing(ServerPlayer player, Screen... screens) {
        if (player.containerMenu instanceof PdaMenu menu) {
            for (Screen screen : screens) {
                if (menu.view.screen == screen) {
                    menu.render();
                    return;
                }
            }
        }
    }

    private void render() {
        buttons.clear();
        for (int i = 0; i < SIZE; i++) {
            grid.setItem(i, ItemStack.EMPTY);
        }
        PdaSettings settings = Pda.settings(player);
        boolean needsRule = switch (view.screen) {
            case RULE, FILTERS, FORMS, NATURES, SOUND -> true;
            case SPECIES -> view.picker == Picker.RULE;
            default -> false;
        };
        if ((needsRule && (view.rule < 0 || view.rule >= settings.rules().size()))
            || (view.screen == Screen.PLANNER && view.plannerSpecies == null)
            || (view.screen == Screen.WISH_NEW && view.wishSpecies == null)
            || ((view.screen == Screen.TRACKER || view.screen == Screen.ROUTE_BIOMES) && view.trackerSpecies == null)) {
            view.go(Screen.HOME);
        }
        switch (view.screen) {
            case HOME -> HomeScreen.render(this, settings);
            case RULE -> RuleScreens.rule(this, rule());
            case FILTERS -> RuleScreens.filters(this, rule());
            case FORMS -> RuleScreens.forms(this, rule());
            case NATURES -> RuleScreens.natures(this, rule());
            case SOUND -> RuleScreens.sound(this, rule());
            case SPECIES -> SpeciesPicker.render(this);
            case PLANNER -> PlannerScreens.planner(this);
            case REMINDERS -> PlannerScreens.reminders(this);
            case WISHBOARD -> WishScreens.board(this);
            case WISH_NEW -> WishScreens.draft(this);
            case TRACKER -> TrackerScreens.tracker(this);
            case ROUTE_BIOMES -> TrackerScreens.routeBiomes(this);
            case HERE -> HereScreen.render(this);
            case ALERTS -> AlertsScreen.render(this);
            case LEDGER -> LedgerScreen.render(this);
            case PARTY -> PartyScreens.party(this);
            case EVOLVE -> PartyScreens.evolve(this);
            case EVS -> PartyScreens.evs(this);
            case NEWS -> NewsScreen.render(this);
        }
        for (int i = BOTTOM; i < SIZE; i++) {
            if (grid.getItem(i).isEmpty()) {
                grid.setItem(i, icon(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "), List.of(), false));
            }
        }
    }

    // Shared by the screens

    void set(int slot, ItemStack stack, Button action) {
        grid.setItem(slot, stack);
        if (action != null) {
            buttons.put(slot, action);
        }
    }

    /** Wraps every button but {@code keep} so clicking it runs {@code before} first. */
    void beforeOtherClicks(int keep, Runnable before) {
        buttons.replaceAll((slot, action) -> slot == keep ? action : right -> {
            before.run();
            action.click(right);
        });
    }

    void back(Screen to) {
        set(45, icon(Items.ARROW, text("Back", ChatFormatting.WHITE), List.of(), false), right -> view.go(to));
    }

    PdaRule rule() {
        return Pda.settings(player).rules().get(view.rule);
    }

    void updateRule(UnaryOperator<PdaRule> change) {
        Pda.save(player, Pda.settings(player).updateRule(view.rule, change));
    }

    void updateFilters(UnaryOperator<PdaFilters> change) {
        updateRule(rule -> rule.withFilters(change.apply(rule.filters())));
    }

    /** Previous / page / next buttons for a paged grid; returns the first index on the current page. */
    int pager(int total) {
        return pager(total, PAGE_SIZE, null);
    }

    /** Like {@link #pager(int)} with a custom page size, and an optional title on the page button. */
    int pager(int total, int pageSize, String title) {
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        view.page = Math.clamp(view.page, 0, pages - 1);
        if (view.page > 0) {
            set(46, icon(Items.SPECTRAL_ARROW, text("◀ Previous page", ChatFormatting.WHITE), List.of(), false),
                right -> view.page--);
        }
        set(47, icon(Items.PAPER, text((title == null ? "" : title + " · ") + "Page " + (view.page + 1) + "/" + pages,
            ChatFormatting.WHITE), List.of(hint(total + " total")), false), null);
        if (view.page < pages - 1) {
            set(48, icon(Items.SPECTRAL_ARROW, text("Next page ▶", ChatFormatting.WHITE), List.of(), false),
                right -> view.page++);
        }
        return view.page * pageSize;
    }
}
