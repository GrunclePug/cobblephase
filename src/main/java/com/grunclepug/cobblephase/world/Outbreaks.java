package com.grunclepug.cobblephase.world;

import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.news.NewsLog;
import com.grunclepug.cobblephase.pda.Pda;
import com.grunclepug.cobblephase.pda.PdaSound;
import com.grunclepug.cobblephase.tracker.SpawnIndex;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Mass outbreaks: now and then one species becomes far more likely to spawn for a while. It works through a spawn
 * influence on every player's spawner, which multiplies that species' entry weights within their rarity bucket.
 */
public final class Outbreaks {
    public static final float MULTIPLIER = 15f;
    private static final int DURATION = 20 * 60 * 20;
    private static final int MIN_GAP = 60 * 60 * 20;
    private static final int MAX_GAP = 120 * 60 * 20;
    /** Outbreak species come from these buckets, so they're noticeable without flooding the world with rares. */
    private static final Set<String> BUCKETS = Set.of("common", "uncommon", "rare");
    private static final Set<String> EXCLUDED_LABELS = Set.of("legendary", "mythical", "ultra_beast", "paradox");
    private static final RandomSource RANDOM = RandomSource.create();

    /** Guards {@link #species} and {@link #AFFECTED} together; spawning may ask from another thread. */
    private static final Object LOCK = new Object();
    private static Species species;
    /** Ticks counted only while someone is online, so an empty server doesn't run the clock down. */
    private static long clock;
    private static long endsAt;
    private static long nextAt = -1;
    /** Per spawn detail: does it spawn the outbreak species? Cleared whenever the outbreak changes. */
    private static final Map<SpawnDetail, Boolean> AFFECTED = new IdentityHashMap<>();

    private Outbreaks() {
    }

    public static void init() {
        PlayerSpawnerFactory.INSTANCE.getInfluenceBuilders().add(player -> Influence.INSTANCE);
    }

    /** The species in an outbreak right now, or null. */
    public static Species current() {
        synchronized (LOCK) {
            return species;
        }
    }

    /** Ticks until the current outbreak ends, or until the next one starts. */
    public static long ticksLeft(MinecraftServer server) {
        return Math.max(0, current() != null ? endsAt - clock : nextAt - clock);
    }

    public static void tick(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        long now = ++clock;
        if (nextAt < 0) {
            nextAt = now + MIN_GAP + RANDOM.nextInt(MAX_GAP - MIN_GAP);
        }
        Species active = current();
        if (active != null && now >= endsAt) {
            announce(server, Component.literal("The ").withStyle(ChatFormatting.GRAY)
                .append(active.getTranslatedName().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" outbreak is over.").withStyle(ChatFormatting.GRAY)), false);
            set(null);
            nextAt = now + MIN_GAP + RANDOM.nextInt(MAX_GAP - MIN_GAP);
        }
        if (current() == null && now >= nextAt) {
            Species chosen = choose();
            if (chosen == null) {
                nextAt = now + MIN_GAP;
                return;
            }
            set(chosen);
            endsAt = now + DURATION;
            NewsLog.add(server, "Outbreak: " + chosen.getName() + " swarmed for 20 minutes");
            announce(server, Component.literal("Outbreak! ").withStyle(ChatFormatting.GOLD)
                .append(chosen.getTranslatedName().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" are swarming for the next 20 minutes. Find them with the PDA tracker.")
                    .withStyle(ChatFormatting.GRAY)), true);
        }
    }

    private static void set(Species next) {
        synchronized (LOCK) {
            species = next;
            AFFECTED.clear();
        }
    }

    /** A random species with a natural spawn entry in an eligible bucket, skipping legendaries and the like. */
    private static Species choose() {
        List<Species> candidates = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<SpawnIndex.Route>> entry : SpawnIndex.index().entrySet()) {
            List<SpawnIndex.Route> routes = entry.getValue();
            if (routes.isEmpty()) {
                continue;
            }
            Species candidate = routes.getFirst().species();
            if (candidate.getLabels().stream().anyMatch(EXCLUDED_LABELS::contains)) {
                continue;
            }
            if (routes.stream().anyMatch(route -> BUCKETS.contains(route.detail().getBucket()))) {
                candidates.add(candidate);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private static void announce(MinecraftServer server, MutableComponent text, boolean sound) {
        Component line = Component.literal("[Outbreak] ").withStyle(ChatFormatting.GOLD).append(text);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Pda.settings(player).announcements()) {
                player.sendSystemMessage(line);
                if (sound) {
                    PdaSound.VILLAGE_BELL.play(player, null);
                }
            }
        }
    }

    /** Boosts the outbreak species' entries. Spawning may call this off the server thread, hence the lock. */
    private static final class Influence implements SpawningInfluence {
        static final Influence INSTANCE = new Influence();

        @Override
        public float affectWeight(SpawnDetail detail, SpawnablePosition position, float weight) {
            boolean affected;
            synchronized (LOCK) {
                Species target = species;
                if (target == null) {
                    return weight;
                }
                affected = AFFECTED.computeIfAbsent(detail, d -> spawns(d, target));
            }
            return affected ? weight * MULTIPLIER : weight;
        }

        private static boolean spawns(SpawnDetail detail, Species target) {
            String name = target.getName().toLowerCase(java.util.Locale.ROOT);
            String id = target.getResourceIdentifier().toString();
            if (detail instanceof PokemonSpawnDetail single) {
                return matches(single.getPokemon().getSpecies(), name, id);
            }
            if (detail instanceof PokemonHerdSpawnDetail herd) {
                for (PokemonHerdSpawnDetail.Herdable member : herd.getHerdablePokemon()) {
                    if (matches(member.getPokemon().getSpecies(), name, id)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean matches(String species, String name, String id) {
            return species != null && (species.equalsIgnoreCase(name) || species.equalsIgnoreCase(id)
                || species.equalsIgnoreCase(id.substring(id.indexOf(':') + 1)));
        }
    }
}
