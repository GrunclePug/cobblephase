package com.grunclepug.cobblephase.tracker;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kotlin.ranges.IntRange;
import net.minecraft.resources.ResourceLocation;

/**
 * Every way each species can spawn naturally, read from Cobblemon's loaded world spawn pool (so datapacks and addons
 * like AllTheMons are included), herd members included. Rebuilt after a datapack reload.
 */
public final class SpawnIndex {
    /**
     * One spawn entry for one species: the entry's own conditions, rarity and weight. {@code props} keeps the form
     * and aspects it spawns with.
     */
    public record Route(Species species, PokemonProperties props, SpawnDetail detail, IntRange levels, boolean herd) {
        /** "Galarian" style form text from the entry's properties, or "" for the base form. */
        public String formText() {
            List<String> parts = new ArrayList<>();
            if (props.getForm() != null && !props.getForm().isBlank()) {
                parts.add(props.getForm());
            }
            for (String aspect : props.getAspects()) {
                if (!parts.contains(aspect)) {
                    parts.add(aspect);
                }
            }
            if (parts.isEmpty()) {
                return "";
            }
            String text = String.join(" ", parts);
            return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
        }

        public String levelText() {
            if (levels == null) {
                return "?";
            }
            return levels.getFirst() == levels.getLast() ? Integer.toString(levels.getFirst())
                : levels.getFirst() + "–" + levels.getLast();
        }
    }

    private static volatile Map<ResourceLocation, List<Route>> bySpecies;

    private SpawnIndex() {
    }

    public static void invalidate() {
        bySpecies = null;
    }

    public static List<Route> routes(ResourceLocation species) {
        return index().getOrDefault(species, List.of());
    }

    public static Map<ResourceLocation, List<Route>> index() {
        Map<ResourceLocation, List<Route>> map = bySpecies;
        if (map == null) {
            map = new HashMap<>();
            SpawnPool pool = CobblemonSpawnPools.WORLD_SPAWN_POOL;
            if (pool != null) {
                for (SpawnDetail detail : pool.getDetails()) {
                    if (detail instanceof PokemonSpawnDetail single) {
                        add(map, single.getPokemon(), detail, single.getLevelRange(), false);
                    } else if (detail instanceof PokemonHerdSpawnDetail herd) {
                        for (PokemonHerdSpawnDetail.Herdable member : herd.getHerdablePokemon()) {
                            add(map, member.getPokemon(), detail, member.getLevelRange(), true);
                        }
                    }
                }
            }
            map.replaceAll((id, list) -> Collections.unmodifiableList(list));
            bySpecies = map;
        }
        return map;
    }

    private static void add(Map<ResourceLocation, List<Route>> map, PokemonProperties props, SpawnDetail detail,
                            IntRange levels, boolean herd) {
        Species species = resolve(props.getSpecies());
        if (species == null) {
            return;
        }
        map.computeIfAbsent(species.getResourceIdentifier(), id -> new ArrayList<>())
            .add(new Route(species, props, detail, levels, herd));
    }

    /** "zapdos" or "cobblemon:zapdos" → the species, or null. */
    private static Species resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        ResourceLocation id = name.contains(":") ? ResourceLocation.tryParse(name)
            : ResourceLocation.fromNamespaceAndPath("cobblemon", name.toLowerCase(Locale.ROOT));
        Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        return species != null ? species : PokemonSpecies.getByName(name.toLowerCase(Locale.ROOT));
    }
}
