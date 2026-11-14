package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import com.cobblemon.mod.common.api.habitats.HabitatPool;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.habitat.HabitatPhases;
import com.grunclepug.cobblephase.habitat.HabitatReminders;
import com.grunclepug.cobblephase.tracker.Locator;
import com.grunclepug.cobblephase.tracker.RouteText;
import com.grunclepug.cobblephase.tracker.SpawnIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The Pokémon tracker: every way a species spawns, and finding (and teleporting to) the nearest spot for one. */
final class TrackerScreens {
    private static final int FIRST_SLOT = 9;
    private static final int PER_PAGE = 36;

    private TrackerScreens() {
    }

    /** One card on the tracker: a natural spawn entry, or a habitat that can spawn the species. */
    private record Card(SpawnIndex.Route route, HabitatPool pool) {
    }

    static void tracker(PdaMenu menu) {
        ServerPlayer player = menu.player;
        ResourceLocation id = menu.view.trackerSpecies;
        Species species = PokemonSpecies.getByIdentifier(id);
        if (species == null) {
            menu.view.go(PdaMenu.Screen.HOME);
            return;
        }
        List<Card> cards = cards(id);
        menu.set(4, icon(PokemonItem.Companion.from(species), species.getTranslatedName().withStyle(ChatFormatting.WHITE),
            List.of(dim("#" + species.getNationalPokedexNumber()),
                hint(cards.isEmpty() ? "It doesn't spawn naturally or in habitats." : cards.size() + " ways it spawns, below."),
                dim("Finding a spot finds suitable terrain;"), dim("it doesn't guarantee a spawn.")), false), null);
        menu.set(0, icon(Items.BELL, text("Ping me for it", ChatFormatting.AQUA),
            List.of(hint("Adds a PDA rule for this Pokémon")), false), right -> addRule(menu, id));
        if (!HabitatPhases.poolsFor(id).isEmpty()) {
            menu.set(8, icon(Items.MOSS_BLOCK, text("Habitat planner", ChatFormatting.DARK_GREEN),
                List.of(hint("Phases and reminders for its habitats")), false), right -> {
                    menu.view.plannerSpecies = id;
                    menu.view.picker = PdaMenu.Picker.PLANNER;
                    menu.view.go(PdaMenu.Screen.PLANNER);
                });
        }

        int start = menu.pager(cards.size(), PER_PAGE, null);
        for (int i = 0; i < PER_PAGE && start + i < cards.size(); i++) {
            int index = start + i;
            Card card = cards.get(index);
            if (card.route() != null) {
                routeCard(menu, card.route(), index, FIRST_SLOT + i);
            } else {
                habitatCard(menu, card.pool(), id, FIRST_SLOT + i);
            }
        }
        menu.back(menu.view.trackerBack);
    }

    private static void routeCard(PdaMenu menu, SpawnIndex.Route route, int index, int slot) {
        ServerPlayer player = menu.player;
        Set<ResourceLocation> here = generatable(player, route.detail().getValidBiomes());
        List<Component> lore = new ArrayList<>(RouteText.describe(route));
        lore.add(Component.empty());
        if (here.isEmpty()) {
            lore.add(text("Its biomes don't generate in this dimension.", ChatFormatting.RED));
        } else {
            lore.add(hint("Left-click: find the nearest spot and teleport"));
            lore.add(hint("Right-click: pick a specific biome (" + here.size() + ")"));
        }
        String form = route.formText();
        Component name = Component.literal(form.isEmpty() ? "" : form + " ").withStyle(ChatFormatting.WHITE)
            .append(route.species().getTranslatedName().withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" · " + PdaCatalog.bucketName(route.detail().getBucket())).withStyle(ChatFormatting.GRAY));
        menu.set(slot, icon(PokemonItem.Companion.from(route.props()), name, lore, false), here.isEmpty() ? null : right -> {
            if (right) {
                menu.view.route = index;
                menu.view.go(PdaMenu.Screen.ROUTE_BIOMES);
            } else {
                player.closeContainer();
                Locator.findBiome(player, spotLabel(route), here, needsSky(route));
            }
        });
    }

    private static void habitatCard(PdaMenu menu, HabitatPool pool, ResourceLocation species, int slot) {
        ServerPlayer player = menu.player;
        long gameTime = HabitatPhases.gameTime(player.server);
        long until = HabitatPhases.ticksUntil(pool, species, gameTime);
        ResourceLocation structure = HabitatPhases.structureFor(pool.getId(), player.server.registryAccess());
        HabitatReminders.Reminder reminder = new HabitatReminders.Reminder(pool.getId(), species);
        boolean reminding = HabitatReminders.has(player, reminder);
        List<Component> lore = new ArrayList<>();
        lore.add(hint("Habitat · phases " + HabitatPhases.phasesFor(pool, species) + " of " + Math.max(1, pool.getPhaseCount())));
        lore.add(until == 0 ? text("Can spawn now", ChatFormatting.GREEN)
            : hint("Next window in " + HabitatPhases.duration(Math.max(0, until))));
        lore.add(Component.empty());
        lore.add(structure == null ? dim("Not a findable structure") : hint("Left-click: find the nearest one and teleport"));
        lore.add(hint(reminding ? "Right-click: cancel reminder" : "Right-click: remind me when it can spawn"));
        Component name = Component.translatable(pool.getName()).withStyle(ChatFormatting.GOLD)
            .append(Component.literal(" (habitat)").withStyle(ChatFormatting.GRAY));
        menu.set(slot, icon(until == 0 ? Items.FLOWERING_AZALEA : Items.MOSS_BLOCK, name, lore, reminding), right -> {
            if (right) {
                HabitatReminders.toggle(player, reminder);
            } else if (structure != null) {
                player.closeContainer();
                Locator.findStructure(player, Component.translatable(pool.getName()).getString(), structure);
            }
        });
    }

    /** The concrete biomes of one route that generate in the player's dimension; click one to go to it. */
    static void routeBiomes(PdaMenu menu) {
        ServerPlayer player = menu.player;
        List<Card> cards = cards(menu.view.trackerSpecies);
        if (menu.view.route < 0 || menu.view.route >= cards.size() || cards.get(menu.view.route).route() == null) {
            menu.view.go(PdaMenu.Screen.TRACKER);
            return;
        }
        SpawnIndex.Route route = cards.get(menu.view.route).route();
        List<ResourceLocation> biomes = new ArrayList<>(new TreeSet<>(generatable(player, route.detail().getValidBiomes())));
        int start = menu.pager(biomes.size());
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < biomes.size(); i++) {
            ResourceLocation biome = biomes.get(start + i);
            menu.set(i, icon(Items.GRASS_BLOCK,
                Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath()).withStyle(ChatFormatting.WHITE),
                List.of(dim(biome.toString()), hint("Click: find the nearest and teleport")), false), right -> {
                    player.closeContainer();
                    Locator.findBiome(player, RouteText.idName(biome), Set.of(biome), needsSky(route));
                });
        }
        menu.back(PdaMenu.Screen.TRACKER);
    }

    private static List<Card> cards(ResourceLocation species) {
        List<Card> cards = new ArrayList<>();
        for (SpawnIndex.Route route : SpawnIndex.routes(species)) {
            cards.add(new Card(route, null));
        }
        for (HabitatPool pool : HabitatPhases.poolsFor(species)) {
            cards.add(new Card(null, pool));
        }
        return cards;
    }

    private static Set<ResourceLocation> generatable(ServerPlayer player, Set<ResourceLocation> biomes) {
        Set<ResourceLocation> here = new java.util.HashSet<>();
        player.serverLevel().getChunkSource().getGenerator().getBiomeSource().possibleBiomes().forEach(holder ->
            holder.unwrapKey().map(ResourceKey::location).filter(biomes::contains).ifPresent(here::add));
        return here;
    }

    /** True when every way this entry can spawn needs open sky, so only surface spots are worth finding. */
    private static boolean needsSky(SpawnIndex.Route route) {
        java.util.Collection<SpawningCondition<?>> conditions = SpawnIndex.orEmpty(route.detail().getConditions());
        if (conditions.isEmpty()) {
            return false;
        }
        for (SpawningCondition<?> condition : conditions) {
            if (!Boolean.TRUE.equals(condition.getCanSeeSky())) {
                return false;
            }
        }
        return true;
    }

    private static String spotLabel(SpawnIndex.Route route) {
        String form = route.formText();
        return "a spot for " + (form.isEmpty() ? "" : form + " ") + route.species().getName();
    }

    private static void addRule(PdaMenu menu, ResourceLocation species) {
        PdaSettings settings = Pda.settings(menu.player);
        if (settings.rules().size() >= PdaSettings.MAX_RULES) {
            menu.player.sendSystemMessage(ChatPrompt.prefix().append(Component.literal("You have the maximum number of rules.")
                .withStyle(ChatFormatting.RED)));
            return;
        }
        Pda.save(menu.player, settings.addRule(PdaRule.empty().withSpecies(List.of(species))));
        menu.view.rule = settings.rules().size();
        menu.view.go(PdaMenu.Screen.RULE);
    }
}
