package com.grunclepug.cobblephase.tracker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tracker destinations players rated after teleporting, shared by everyone. A good spot is used before searching
 * again; a bad one, and anything near it, is skipped by later searches.
 */
public final class TeleportSpots extends SavedData {
    /** Searches skip candidates this close to a bad spot (a tiny biome patch, a broken habitat). */
    public static final int BAD_RADIUS = 192;
    private static final String NAME = "cobblephase_spots";
    private static final Factory<TeleportSpots> FACTORY = new Factory<>(TeleportSpots::new, TeleportSpots::load, null);

    /** {@code target} is "biome:<id>" or "structure:<id>"; spots are per dimension. */
    public record Spot(ResourceLocation dimension, String target, int x, int y, int z, boolean good) {
        static final Codec<Spot> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(Spot::dimension),
            Codec.STRING.fieldOf("target").forGetter(Spot::target),
            Codec.INT.fieldOf("x").forGetter(Spot::x),
            Codec.INT.fieldOf("y").forGetter(Spot::y),
            Codec.INT.fieldOf("z").forGetter(Spot::z),
            Codec.BOOL.fieldOf("good").forGetter(Spot::good)
        ).apply(i, Spot::new));

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        double distanceSq(int px, int pz) {
            double dx = x - px;
            double dz = z - pz;
            return dx * dx + dz * dz;
        }
    }

    private final List<Spot> spots = new ArrayList<>();

    public static TeleportSpots get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static TeleportSpots load(CompoundTag tag, HolderLookup.Provider registries) {
        TeleportSpots spots = new TeleportSpots();
        Spot.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("spots")).result().ifPresent(spots.spots::addAll);
        return spots;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("spots", Spot.CODEC.listOf().encodeStart(NbtOps.INSTANCE, spots).getOrThrow());
        return tag;
    }

    /** Records a rating, replacing any earlier rating of the same spot. */
    public void rate(Spot spot) {
        spots.removeIf(s -> s.dimension().equals(spot.dimension()) && s.target().equals(spot.target())
            && s.x() == spot.x() && s.z() == spot.z());
        spots.add(spot);
        setDirty();
    }

    /** Bad spots in a dimension, for searches to avoid (whatever their target). */
    public List<Spot> bad(ResourceLocation dimension) {
        return spots.stream().filter(s -> !s.good() && s.dimension().equals(dimension)).toList();
    }

    /** The nearest good spot for any of {@code targets} within {@code maxDistance}, or null. */
    public Spot nearestGood(ResourceLocation dimension, Set<String> targets, int px, int pz, int maxDistance) {
        Spot best = null;
        double bestSq = (double) maxDistance * maxDistance;
        for (Spot spot : spots) {
            if (spot.good() && spot.dimension().equals(dimension) && targets.contains(spot.target())) {
                double sq = spot.distanceSq(px, pz);
                if (sq <= bestSq) {
                    best = spot;
                    bestSq = sq;
                }
            }
        }
        return best;
    }

    static boolean nearAny(List<Spot> spots, int x, int z, int radius) {
        double radiusSq = (double) radius * radius;
        for (Spot spot : spots) {
            if (spot.distanceSq(x, z) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
