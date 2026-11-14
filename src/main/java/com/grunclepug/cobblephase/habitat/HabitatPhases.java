package com.grunclepug.cobblephase.habitat;

import com.cobblemon.mod.common.api.habitats.HabitatPool;
import com.cobblemon.mod.common.api.habitats.HabitatPools;
import com.cobblemon.mod.common.api.habitats.HabitatSpawn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Habitat spawn phases, mirroring {@code HabitatBlockEntity.calculatePhase} for the default SIMPLE order: the phase
 * advances once every 24000 ticks of total game time (not time of day), cycling through the pool's phase count.
 * Check it again when Cobblemon updates.
 */
public final class HabitatPhases {
    public static final long TICKS_PER_PHASE = 24000L;
    private static final String HABITATS_PREFIX = "habitats/";

    private HabitatPhases() {
    }

    /** Total game time, shared by every dimension. */
    public static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static long day(long gameTime) {
        return gameTime / TICKS_PER_PHASE;
    }

    /** The 1-based phase on a given phase day. */
    public static int phaseOf(long day, int count) {
        return count <= 1 ? 1 : (int) (day % count) + 1;
    }

    public static long ticksToNextPhase(long gameTime) {
        return TICKS_PER_PHASE - gameTime % TICKS_PER_PHASE;
    }

    /** Whether {@code species} can spawn in {@code pool} during {@code phase}. */
    public static boolean spawnsIn(HabitatPool pool, ResourceLocation species, int phase) {
        for (HabitatSpawn spawn : pool.getSpawns()) {
            if (spawn.getSpecies().getResourceIdentifier().equals(species)
                && (spawn.getPhases() == null || spawn.getPhases().contains(phase))) {
                return true;
            }
        }
        return false;
    }

    /** The phases in which {@code species} can spawn in {@code pool}, ascending. */
    public static List<Integer> phasesFor(HabitatPool pool, ResourceLocation species) {
        Set<Integer> phases = new LinkedHashSet<>();
        for (int phase = 1; phase <= Math.max(1, pool.getPhaseCount()); phase++) {
            if (spawnsIn(pool, species, phase)) {
                phases.add(phase);
            }
        }
        return new ArrayList<>(phases);
    }

    /**
     * Ticks until {@code species} can next spawn in {@code pool}: 0 if it can now, -1 if never.
     */
    public static long ticksUntil(HabitatPool pool, ResourceLocation species, long gameTime) {
        int count = pool.getPhaseCount();
        long day = day(gameTime);
        for (int k = 0; k < Math.max(1, count); k++) {
            if (spawnsIn(pool, species, phaseOf(day + k, count))) {
                return k == 0 ? 0 : ticksToNextPhase(gameTime) + (k - 1) * TICKS_PER_PHASE;
            }
        }
        return -1;
    }

    /** Pools that can spawn {@code species} in some phase, by name. */
    public static List<HabitatPool> poolsFor(ResourceLocation species) {
        List<HabitatPool> pools = new ArrayList<>();
        for (HabitatPool pool : pools().values()) {
            for (HabitatSpawn spawn : pool.getSpawns()) {
                if (spawn.getSpecies().getResourceIdentifier().equals(species)) {
                    pools.add(pool);
                    break;
                }
            }
        }
        pools.sort(Comparator.comparing(pool -> shortName(pool.getId())));
        return pools;
    }

    /** Every species any habitat can spawn. */
    public static Set<ResourceLocation> species() {
        Set<ResourceLocation> species = new LinkedHashSet<>();
        for (HabitatPool pool : pools().values()) {
            for (HabitatSpawn spawn : pool.getSpawns()) {
                species.add(spawn.getSpecies().getResourceIdentifier());
            }
        }
        return species;
    }

    public static Map<ResourceLocation, HabitatPool> pools() {
        return HabitatPools.INSTANCE.getHabitatPoolsById();
    }

    public static HabitatPool pool(ResourceLocation id) {
        return pools().get(id);
    }

    /** Pool names whose structure is spelled differently in Cobblemon's data. */
    private static final Map<String, String> STRUCTURE_ALIASES = Map.of(
        "reclaimed_desert_monument", "reclaimed_deserted_monument");

    /**
     * The world structure a habitat pool generates as, or null when it has none (some pools only appear inside
     * other structures, or are placed by hand). Matches "habitats/<name>" ignoring underscores.
     */
    public static ResourceLocation structureFor(ResourceLocation pool, RegistryAccess registries) {
        String name = shortName(pool);
        name = STRUCTURE_ALIASES.getOrDefault(name, name);
        String key = name.replace("_", "");
        for (ResourceLocation id : registries.registryOrThrow(Registries.STRUCTURE).keySet()) {
            if (id.getNamespace().equals(pool.getNamespace()) && id.getPath().startsWith(HABITATS_PREFIX)
                && id.getPath().substring(HABITATS_PREFIX.length()).replace("_", "").equals(key)) {
                return id;
            }
        }
        return null;
    }

    /** "cobblemon:habitats/flowerbed_clearing" → "flowerbed_clearing". */
    public static String shortName(ResourceLocation id) {
        String path = id.getPath();
        return path.startsWith(HABITATS_PREFIX) ? path.substring(HABITATS_PREFIX.length()) : path;
    }

    public static String duration(long ticks) {
        long seconds = ticks / 20;
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + (seconds % 3600 / 60) + "m";
        }
        return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }
}
