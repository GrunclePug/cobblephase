package com.grunclepug.cobblephase.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Nearest-first searches that only sample world-generation noise, never load or generate chunks, so they can run on
 * a background thread (the same noise sampling worldgen worker threads use). Each call to {@link Search#next}
 * resumes where the previous one stopped. Results are predictions: the caller loads the spot and checks it.
 */
final class Searches {
    static final int MAX_DISTANCE = 10_000;
    private static final int BIOME_STEP = 32;

    private Searches() {
    }

    /** A predicted spot: block coordinates, and the biome or structure id it was found for. */
    record Candidate(int x, int y, int z, String target) {
    }

    interface Search {
        /** The next candidate in roughly increasing distance, or null when the area is exhausted or cancelled. */
        Candidate next(BooleanSupplier cancelled);

        /** Skips anything within {@code radius} blocks of a spot from now on. */
        void avoid(int x, int z, int radius);
    }

    private abstract static class Base implements Search {
        final int originX;
        final int originZ;
        private final List<int[]> avoid = new ArrayList<>();

        Base(int originX, int originZ, List<TeleportSpots.Spot> bad) {
            this.originX = originX;
            this.originZ = originZ;
            for (TeleportSpots.Spot spot : bad) {
                avoid.add(new int[] {spot.x(), spot.z(), TeleportSpots.BAD_RADIUS});
            }
        }

        @Override
        public synchronized void avoid(int x, int z, int radius) {
            avoid.add(new int[] {x, z, radius});
        }

        synchronized boolean avoided(int x, int z) {
            for (int[] spot : avoid) {
                long dx = x - spot[0];
                long dz = z - spot[1];
                if (dx * dx + dz * dz <= (long) spot[2] * spot[2]) {
                    return true;
                }
            }
            return false;
        }

        boolean inRange(int x, int z) {
            long dx = x - originX;
            long dz = z - originZ;
            return dx * dx + dz * dz <= (long) MAX_DISTANCE * MAX_DISTANCE;
        }
    }

    /**
     * Square rings around the origin, sampling the biome at a few heights per column like vanilla's locate (surface
     * heights only when the spawn needs open sky).
     */
    static final class BiomeSearch extends Base {
        private final Set<ResourceLocation> biomes;
        private final BiomeSource source;
        private final Climate.Sampler sampler;
        private final int[] heights;
        private int radius;
        private int index;

        BiomeSearch(ServerLevel level, int originX, int originZ, Set<ResourceLocation> biomes, boolean surfaceOnly,
                    List<TeleportSpots.Spot> bad) {
            super(originX, originZ, bad);
            this.biomes = biomes;
            this.source = level.getChunkSource().getGenerator().getBiomeSource();
            this.sampler = level.getChunkSource().randomState().sampler();
            int min = level.getMinBuildHeight();
            int max = level.dimensionType().hasCeiling() ? level.getMinBuildHeight() + level.getLogicalHeight() : level.getMaxBuildHeight();
            int from = surfaceOnly && !level.dimensionType().hasCeiling() ? Math.max(min, level.getSeaLevel()) : min + 16;
            List<Integer> ys = new ArrayList<>();
            for (int y = from; y < max; y += surfaceOnly ? 32 : 48) {
                ys.add(y);
            }
            this.heights = ys.stream().mapToInt(Integer::intValue).toArray();
        }

        @Override
        public Candidate next(BooleanSupplier cancelled) {
            while (radius <= MAX_DISTANCE) {
                int perimeter = radius == 0 ? 1 : 8 * (radius / BIOME_STEP);
                while (index < perimeter) {
                    if ((index & 255) == 0 && cancelled.getAsBoolean()) {
                        return null;
                    }
                    int[] offset = ringOffset(radius, index++);
                    int x = originX + offset[0];
                    int z = originZ + offset[1];
                    if (!inRange(x, z) || avoided(x, z)) {
                        continue;
                    }
                    for (int y : heights) {
                        Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z), sampler);
                        ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(null);
                        if (id != null && biomes.contains(id)) {
                            return new Candidate(x, y, z, "biome:" + id);
                        }
                    }
                }
                radius += BIOME_STEP;
                index = 0;
            }
            return null;
        }

        /** The i-th point on the square ring at {@code radius}, walking its perimeter in steps. */
        private static int[] ringOffset(int radius, int i) {
            if (radius == 0) {
                return new int[] {0, 0};
            }
            int steps = radius / BIOME_STEP;
            int side = 2 * steps;
            int along = (i % side) * BIOME_STEP;
            return switch (i / side) {
                case 0 -> new int[] {-radius + along, -radius};
                case 1 -> new int[] {radius, -radius + along};
                case 2 -> new int[] {radius - along, radius};
                default -> new int[] {-radius, radius - along};
            };
        }
    }

    /**
     * Candidate chunks of the structure sets that contain the structure, one ring of placement regions at a time.
     * A candidate is a chunk the set's placement picks whose surroundings include one of the structure's biomes;
     * whether the structure really generated there is for the caller to check (it depends on the set's weighted
     * pick and the structure's own template, which only world generation can answer exactly).
     */
    static final class StructureSearch extends Base {
        /**
         * Offsets from a candidate chunk's corner covering where a rotated start piece's biome check can land; the
         * filter only drops a chunk when none of these spots, at any of the heights, has one of its biomes.
         */
        private static final int[][] SAMPLES = grid(32, 16);

        private final Holder<Structure> structure;
        private final ResourceLocation structureId;
        private final ChunkGeneratorStructureState state;
        private final BiomeSource source;
        private final Climate.Sampler sampler;
        private final int[] heights;
        private final List<RandomSpreadStructurePlacement> placements = new ArrayList<>();
        private int ring;

        StructureSearch(ServerLevel level, int originX, int originZ, Holder<Structure> structure, ResourceLocation structureId,
                        List<TeleportSpots.Spot> bad) {
            super(originX, originZ, bad);
            this.structure = structure;
            this.structureId = structureId;
            this.state = level.getChunkSource().getGeneratorState();
            this.source = level.getChunkSource().getGenerator().getBiomeSource();
            this.sampler = level.getChunkSource().randomState().sampler();
            int sea = level.getSeaLevel();
            List<Integer> ys = new ArrayList<>();
            for (int y = sea - 32; y <= sea + 160; y += 16) {
                ys.add(y);
            }
            this.heights = ys.stream().mapToInt(Integer::intValue).toArray();
            for (Holder<StructureSet> set : state.possibleStructureSets()) {
                boolean contains = set.value().structures().stream().anyMatch(e -> e.structure().equals(structure));
                if (contains && set.value().placement() instanceof RandomSpreadStructurePlacement spread) {
                    placements.add(spread);
                }
            }
        }

        /** False when the structure doesn't generate here, or only with a placement type this can't search. */
        boolean searchable() {
            return !placements.isEmpty();
        }

        private static int[][] grid(int radius, int step) {
            List<int[]> points = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    points.add(new int[] {dx, dz});
                }
            }
            points.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
            return points.toArray(int[][]::new);
        }

        Holder<Structure> structure() {
            return structure;
        }

        /** Candidate chunks of the next ring (possibly none), or null once past the distance limit or cancelled. */
        List<Candidate> nextRing(BooleanSupplier cancelled) {
            boolean any = false;
            List<Candidate> found = new ArrayList<>();
            for (RandomSpreadStructurePlacement placement : placements) {
                int spacing = placement.spacing();
                if (ring > MAX_DISTANCE / (spacing * 16) + 1) {
                    continue;
                }
                any = true;
                int regionX = Math.floorDiv(originX >> 4, spacing);
                int regionZ = Math.floorDiv(originZ >> 4, spacing);
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || cancelled.getAsBoolean()) {
                            continue;
                        }
                        ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(),
                            (regionX + dx) * spacing, (regionZ + dz) * spacing);
                        int x = chunk.getMinBlockX();
                        int z = chunk.getMinBlockZ();
                        if (inRange(x, z) && !avoided(x, z) && placement.isStructureChunk(state, chunk.x, chunk.z)
                            && biomeNearby(x, z)) {
                            found.add(new Candidate(x, heights[1], z, "structure:" + structureId));
                        }
                    }
                }
            }
            ring++;
            return any && !cancelled.getAsBoolean() ? found : null;
        }

        private boolean biomeNearby(int x, int z) {
            for (int[] offset : SAMPLES) {
                for (int y : heights) {
                    Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x + offset[0]), QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z + offset[1]), sampler);
                    if (structure.value().biomes().contains(biome)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public Candidate next(BooleanSupplier cancelled) {
            throw new UnsupportedOperationException("Structure searches go ring by ring");
        }
    }
}
