package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.item.PokemonItem;
import com.grunclepug.cobblephase.tracker.RouteText;
import com.grunclepug.cobblephase.tracker.SpawnIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** "What spawns here?": every natural spawn entry for the biome the player stands in, with filters and sorting. */
final class HereScreen {
    private HereScreen() {
    }

    /** One species (and form) that can spawn here: its most common entry, and whether any entry could spawn now. */
    private record Entry(SpawnIndex.Route route, boolean now) {
    }

    static void render(PdaMenu menu) {
        ServerPlayer player = menu.player;
        PdaMenu.View view = menu.view;
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        ResourceLocation biome = level.getBiome(pos).unwrapKey().map(key -> key.location()).orElse(null);
        AbstractPokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);

        Map<String, Entry> entries = new LinkedHashMap<>();
        if (biome != null) {
            for (List<SpawnIndex.Route> routes : SpawnIndex.index().values()) {
                for (SpawnIndex.Route route : routes) {
                    if (!route.detail().getValidBiomes().contains(biome)) {
                        continue;
                    }
                    boolean now = possibleNow(route, level, pos);
                    String key = route.species().getResourceIdentifier() + "/" + route.formText();
                    Entry old = entries.get(key);
                    if (old == null) {
                        entries.put(key, new Entry(route, now));
                    } else {
                        SpawnIndex.Route best = route.detail().getWeight() > old.route().detail().getWeight() ? route : old.route();
                        entries.put(key, new Entry(best, now || old.now()));
                    }
                }
            }
        }

        List<Entry> list = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (view.hereNow && !entry.now()) {
                continue;
            }
            if (view.hereBucket != null && !view.hereBucket.equals(entry.route().detail().getBucket())) {
                continue;
            }
            if (view.hereType != null && !hasType(entry.route(), view.hereType)) {
                continue;
            }
            if (view.hereUncaught && pokedex.getKnowledgeForSpecies(entry.route().species().getResourceIdentifier())
                == PokedexEntryProgress.OWNED) {
                continue;
            }
            list.add(entry);
        }
        list.sort(comparator(view.hereSort));

        String biomeName = biome == null ? "Unknown biome" : RouteText.idName(biome);
        int start = menu.pager(list.size(), PdaMenu.PAGE_SIZE, biomeName);
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < list.size(); i++) {
            Entry entry = list.get(start + i);
            SpawnIndex.Route route = entry.route();
            String form = route.formText();
            List<Component> lore = new ArrayList<>();
            lore.add(entry.now() ? text("✔ Can spawn right now", ChatFormatting.GREEN)
                : dim("Not right now (time, weather or sky)"));
            lore.addAll(RouteText.describe(route));
            lore.add(Component.empty());
            lore.add(hint("Click: open in the tracker"));
            Component name = Component.literal(form.isEmpty() ? "" : form + " ").withStyle(ChatFormatting.WHITE)
                .append(route.species().getTranslatedName().withStyle(ChatFormatting.WHITE));
            menu.set(i, icon(PokemonItem.Companion.from(route.props()), name, lore, entry.now()), right -> {
                view.trackerSpecies = route.species().getResourceIdentifier();
                view.trackerBack = PdaMenu.Screen.HERE;
                view.go(PdaMenu.Screen.TRACKER);
            });
        }
        if (list.isEmpty()) {
            menu.set(22, icon(Items.BARRIER, text("Nothing matches here", ChatFormatting.GRAY),
                List.of(hint("Try loosening the filters below.")), false), null);
        }

        menu.back(PdaMenu.Screen.HOME);
        menu.set(49, icon(Items.CLOCK, toggleTitle("Right now only", view.hereNow),
            List.of(hint("Only what this time, weather"), hint("and sky allow at the moment")), view.hereNow),
            right -> {
                view.hereNow = !view.hereNow;
                view.page = 0;
            });
        List<String> buckets = PdaCatalog.buckets();
        menu.set(50, icon(view.hereBucket == null ? Items.SNOWBALL : PdaCatalog.bucketIcon(view.hereBucket),
            text("Rarity: " + (view.hereBucket == null ? "any" : PdaCatalog.bucketName(view.hereBucket)), ChatFormatting.AQUA),
            List.of(hint("Left-click: next"), hint("Right-click: any")), view.hereBucket != null), right -> {
                view.hereBucket = right ? null : cycle(buckets, view.hereBucket);
                view.page = 0;
            });
        List<String> types = new ArrayList<>();
        for (ElementalType type : ElementalTypes.all()) {
            types.add(type.getName().toLowerCase(Locale.ROOT));
        }
        menu.set(51, icon(view.hereType == null ? Items.PAPER : RuleScreens.typeIcon(view.hereType),
            text("Type: " + (view.hereType == null ? "any" : RouteText.idName(ResourceLocation.withDefaultNamespace(view.hereType))),
                ChatFormatting.AQUA),
            List.of(hint("Left-click: next"), hint("Right-click: any")), view.hereType != null), right -> {
                view.hereType = right ? null : cycle(types, view.hereType);
                view.page = 0;
            });
        menu.set(52, icon(CobblemonItems.POKEDEX_RED, toggleTitle("Not caught yet", view.hereUncaught),
            List.of(hint("Only species missing from your Pokédex")), view.hereUncaught), right -> {
                view.hereUncaught = !view.hereUncaught;
                view.page = 0;
            });
        menu.set(53, icon(Items.COMPARATOR, text("Sort: " + view.hereSort.displayName, ChatFormatting.AQUA),
            List.of(hint("Click to change")), false), right -> {
                PdaMenu.HereSort[] sorts = PdaMenu.HereSort.values();
                view.hereSort = sorts[(view.hereSort.ordinal() + 1) % sorts.length];
                view.page = 0;
            });
    }

    /** Whether some condition of the entry allows a spawn at {@code pos} now; its biome already matched. */
    private static boolean possibleNow(SpawnIndex.Route route, ServerLevel level, BlockPos pos) {
        List<SpawningCondition<?>> conditions = route.detail().getConditions();
        if (conditions.isEmpty()) {
            return true;
        }
        int time = (int) (level.getDayTime() % 24000L);
        for (SpawningCondition<?> c : conditions) {
            if ((c.getDimensions().isEmpty() || c.getDimensions().contains(level.dimension().location()))
                && (c.getTimeRange() == null || c.getTimeRange().contains(time))
                && (c.isRaining() == null || c.isRaining() == level.isRaining())
                && (c.isThundering() == null || c.isThundering() == level.isThundering())
                && (c.getCanSeeSky() == null || c.getCanSeeSky() == level.canSeeSky(pos.above()))
                && (c.getMoonPhase() == null || c.getMoonPhase().contains(level.getMoonPhase()))
                && (c.getMinY() == null || pos.getY() >= c.getMinY())
                && (c.getMaxY() == null || pos.getY() <= c.getMaxY())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasType(SpawnIndex.Route route, String type) {
        for (ElementalType t : route.species().getStandardForm().getTypes()) {
            if (t.getName().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private static Comparator<Entry> comparator(PdaMenu.HereSort sort) {
        List<String> buckets = PdaCatalog.buckets();
        Comparator<Entry> byName = Comparator.comparing(e -> e.route().species().getName());
        return switch (sort) {
            case RAREST -> Comparator.<Entry>comparingInt(e -> -buckets.indexOf(e.route().detail().getBucket()))
                .thenComparingDouble(e -> e.route().detail().getWeight()).thenComparing(byName);
            case WEIGHT -> Comparator.<Entry>comparingDouble(e -> -e.route().detail().getWeight()).thenComparing(byName);
            case NAME -> byName;
            case DEX -> Comparator.<Entry>comparingInt(e -> e.route().species().getNationalPokedexNumber()).thenComparing(byName);
        };
    }

    /** The value after {@code current} in {@code values}, wrapping to "any" (null) after the last. */
    private static String cycle(List<String> values, String current) {
        int index = current == null ? -1 : values.indexOf(current);
        return index + 1 < values.size() ? values.get(index + 1) : null;
    }
}
