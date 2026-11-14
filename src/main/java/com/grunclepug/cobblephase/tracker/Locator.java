package com.grunclepug.cobblephase.tracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Finds a destination and teleports the player there, without stalling the server:
 * <ul>
 *   <li>Biomes: a background noise search predicts spots; each is loaded with a chunk ticket (generated off-thread
 *   like any chunk) and checked before landing.</li>
 *   <li>Habitat structures: a background search lists candidate chunks ring by ring; the server then asks world
 *   generation for just those chunks' structure starts (asynchronously, a few at a time), which is exactly what
 *   vanilla {@code /locate} checks, and goes to the nearest real one.</li>
 * </ul>
 * The player is moved with vanilla {@code /tp}, which Essential Commands records for {@code /back}.
 */
public final class Locator {
    /** How many spots to load and reject (no safe ground, wrong biome) before giving up. */
    private static final int MAX_CHECKS = 8;
    private static final int MAX_IN_FLIGHT = 16;
    private static final int LOAD_TIMEOUT = 30 * 20;
    private static final int LANDING_RADIUS = 8;
    private static final int RATING_TTL = 30 * 60 * 20;
    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("cobblephase_tracker", Comparator.comparingLong(ChunkPos::toLong), LOAD_TIMEOUT);
    private static final ExecutorService SEARCHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cobblephase-locate");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Map<String, Rating> RATINGS = new HashMap<>();

    private Locator() {
    }

    /** A teleport waiting to be rated; filed as good or bad when the player clicks a button. */
    private record Rating(UUID player, TeleportSpots.Spot spot, long expires) {
    }

    private record Check(Searches.Candidate candidate, CompletableFuture<ChunkResult<ChunkAccess>> future) {
    }

    private enum Stage { SEARCHING, SCANNING, CHECKING, LOADING }

    private static final class Job {
        final UUID player;
        final ServerLevel level;
        final String label;
        final Searches.BiomeSearch biomeSearch;
        final Searches.StructureSearch structureSearch;
        final Set<ResourceLocation> biomes;
        final boolean surfaceOnly;
        volatile boolean cancelled;
        Stage stage;
        Future<Searches.Candidate> candidateFuture;
        Future<List<Searches.Candidate>> ringFuture;
        final Deque<Searches.Candidate> toCheck = new ArrayDeque<>();
        final List<Check> inFlight = new ArrayList<>();
        final List<BlockPos> confirmed = new ArrayList<>();
        String target;
        ChunkPos ticket;
        BlockPos destination;
        boolean destinationChecked;
        boolean fromGoodSpot;
        long loadStart;
        int checks;

        Job(ServerPlayer player, String label, Searches.BiomeSearch biomeSearch, Searches.StructureSearch structureSearch,
            Set<ResourceLocation> biomes, boolean surfaceOnly) {
            this.player = player.getUUID();
            this.level = player.serverLevel();
            this.label = label;
            this.biomeSearch = biomeSearch;
            this.structureSearch = structureSearch;
            this.biomes = biomes;
            this.surfaceOnly = surfaceOnly;
        }

        int originX() {
            return biomeSearch != null ? biomeSearch.originX : structureSearch.originX;
        }

        int originZ() {
            return biomeSearch != null ? biomeSearch.originZ : structureSearch.originZ;
        }
    }

    /** Finds the nearest of {@code biomes} in the player's dimension and teleports there. */
    public static void findBiome(ServerPlayer player, String label, Set<ResourceLocation> biomes, boolean surfaceOnly) {
        ServerLevel level = player.serverLevel();
        Set<ResourceLocation> here = generatable(level, biomes);
        if (here.isEmpty()) {
            message(player, Component.literal("None of its biomes generate in this dimension.").withStyle(ChatFormatting.RED));
            return;
        }
        Searches.BiomeSearch search = new Searches.BiomeSearch(level, player.getBlockX(), player.getBlockZ(), here,
            surfaceOnly, TeleportSpots.get(player.server).bad(level.dimension().location()));
        Set<String> targets = here.stream().map(id -> "biome:" + id).collect(Collectors.toSet());
        start(player, new Job(player, label, search, null, here, surfaceOnly), targets);
    }

    /** Finds the nearest generated instance of a structure in the player's dimension and teleports there. */
    public static void findStructure(ServerPlayer player, String label, ResourceLocation structureId) {
        ServerLevel level = player.serverLevel();
        Holder<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .getHolder(ResourceKey.create(Registries.STRUCTURE, structureId)).<Holder<Structure>>map(h -> h).orElse(null);
        if (structure == null) {
            message(player, Component.literal("That habitat isn't a structure on this server.").withStyle(ChatFormatting.RED));
            return;
        }
        Searches.StructureSearch search = new Searches.StructureSearch(level, player.getBlockX(), player.getBlockZ(),
            structure, structureId, TeleportSpots.get(player.server).bad(level.dimension().location()));
        if (!search.searchable()) {
            message(player, Component.literal("That habitat doesn't generate in this dimension.").withStyle(ChatFormatting.RED));
            return;
        }
        start(player, new Job(player, label, null, search, null, false), Set.of("structure:" + structureId));
    }

    private static Set<ResourceLocation> generatable(ServerLevel level, Set<ResourceLocation> biomes) {
        return level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
            .map(holder -> holder.unwrapKey().map(ResourceKey::location).orElse(null))
            .filter(id -> id != null && biomes.contains(id))
            .collect(Collectors.toSet());
    }

    private static void start(ServerPlayer player, Job job, Set<String> targets) {
        cancel(player.getUUID());
        JOBS.put(player.getUUID(), job);
        TeleportSpots.Spot good = TeleportSpots.get(player.server).nearestGood(job.level.dimension().location(), targets,
            player.getBlockX(), player.getBlockZ(), Searches.MAX_DISTANCE);
        if (good != null) {
            job.fromGoodSpot = true;
            job.target = good.target();
            goTo(job, good.pos(), true, player.server);
            message(player, Component.literal("Using a spot rated good for " + job.label + "...").withStyle(ChatFormatting.GRAY));
            return;
        }
        message(player, Component.literal("Searching for " + job.label + " within "
            + String.format(Locale.ROOT, "%,d", Searches.MAX_DISTANCE) + " blocks...").withStyle(ChatFormatting.GRAY));
        searchNext(job);
    }

    /** Asks the background search for more: the next biome spot, or the next ring of structure candidates. */
    private static void searchNext(Job job) {
        if (job.biomeSearch != null) {
            job.stage = Stage.SEARCHING;
            job.candidateFuture = SEARCHER.submit(() -> job.biomeSearch.next(() -> job.cancelled));
        } else if (!job.confirmed.isEmpty()) {
            nearestConfirmed(job);
        } else {
            job.stage = Stage.SCANNING;
            job.ringFuture = SEARCHER.submit(() -> job.structureSearch.nextRing(() -> job.cancelled));
        }
    }

    public static void cancel(UUID player) {
        Job job = JOBS.remove(player);
        if (job != null) {
            finish(job);
        }
    }

    /** Stops a job's background work and releases its chunk ticket. */
    private static void finish(Job job) {
        job.cancelled = true;
        releaseTicket(job);
    }

    private static void releaseTicket(Job job) {
        if (job.ticket != null) {
            job.level.getChunkSource().removeRegionTicket(TICKET, job.ticket, 1, job.ticket);
            job.ticket = null;
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        RATINGS.values().removeIf(rating -> rating.expires() < now);
        for (Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator(); it.hasNext(); ) {
            Job job = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(job.player);
            if (player == null || player.serverLevel() != job.level || job.cancelled) {
                finish(job);
                it.remove();
                continue;
            }
            if (job.stage != Stage.LOADING && now % 40 == 0) {
                player.displayClientMessage(Component.literal("Tracker: searching for " + job.label + "...")
                    .withStyle(ChatFormatting.GRAY), true);
            }
            if (step(job, player, now)) {
                finish(job);
                it.remove();
            }
        }
    }

    /** Advances a job; true when it's finished. */
    private static boolean step(Job job, ServerPlayer player, long now) {
        switch (job.stage) {
            case SEARCHING -> {
                if (!job.candidateFuture.isDone()) {
                    return false;
                }
                Searches.Candidate candidate = result(job.candidateFuture);
                if (candidate == null) {
                    return notFound(job, player);
                }
                job.target = candidate.target();
                goTo(job, new BlockPos(candidate.x(), candidate.y(), candidate.z()), false, player.server);
                return false;
            }
            case SCANNING -> {
                if (!job.ringFuture.isDone()) {
                    return false;
                }
                List<Searches.Candidate> ring = result(job.ringFuture);
                if (ring == null) {
                    return notFound(job, player);
                }
                job.toCheck.addAll(ring);
                job.stage = Stage.CHECKING;
                return false;
            }
            case CHECKING -> {
                checkStructures(job);
                if (!job.inFlight.isEmpty() || !job.toCheck.isEmpty()) {
                    return false;
                }
                // The whole ring is checked: go to its nearest real structure, or scan the next ring.
                searchNext(job);
                return false;
            }
            case LOADING -> {
                if (!loaded(job.level, job.destination)) {
                    return now - job.loadStart > LOAD_TIMEOUT && retry(job, player, "it took too long to load");
                }
                return arrive(job, player);
            }
        }
        return true;
    }

    /** Starts structure-start checks up to the in-flight limit, and collects finished ones. */
    private static void checkStructures(Job job) {
        while (job.inFlight.size() < MAX_IN_FLIGHT && !job.toCheck.isEmpty()) {
            Searches.Candidate candidate = job.toCheck.poll();
            // Requested from a worker: on the server thread getChunkFuture would block until the chunk is ready.
            ServerChunkCache chunks = job.level.getChunkSource();
            job.inFlight.add(new Check(candidate, CompletableFuture.supplyAsync(() ->
                    chunks.getChunkFuture(candidate.x() >> 4, candidate.z() >> 4, ChunkStatus.STRUCTURE_STARTS, true), SEARCHER)
                .thenCompose(future -> future)));
        }
        for (Iterator<Check> it = job.inFlight.iterator(); it.hasNext(); ) {
            Check check = it.next();
            if (!check.future().isDone()) {
                continue;
            }
            it.remove();
            ChunkResult<ChunkAccess> loaded = result(check.future());
            ChunkAccess chunk = loaded == null ? null : loaded.orElse(null);
            StructureStart start = chunk == null ? null : chunk.getStartForStructure(job.structureSearch.structure().value());
            if (start != null && start.isValid()) {
                job.confirmed.add(start.getBoundingBox().getCenter());
                job.target = check.candidate().target();
            }
        }
    }

    private static void nearestConfirmed(Job job) {
        job.confirmed.sort(Comparator.comparingDouble(pos -> {
            double dx = pos.getX() - job.originX();
            double dz = pos.getZ() - job.originZ();
            return dx * dx + dz * dz;
        }));
        BlockPos nearest = job.confirmed.removeFirst();
        goTo(job, nearest, true, job.level.getServer());
    }

    /** Loads the area around {@code pos}; {@code checked} when it's already known to be the right place. */
    private static void goTo(Job job, BlockPos pos, boolean checked, MinecraftServer server) {
        releaseTicket(job);
        job.destination = pos;
        job.destinationChecked = checked;
        job.stage = Stage.LOADING;
        job.loadStart = server.getTickCount();
        job.ticket = new ChunkPos(pos);
        job.level.getChunkSource().addRegionTicket(TICKET, job.ticket, 1, job.ticket);
    }

    /** The 3×3 chunks around {@code pos} are fully loaded (the landing search stays inside them). */
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(center.x + dx, center.z + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Finds safe ground at the loaded destination and teleports; true when finished. */
    private static boolean arrive(Job job, ServerPlayer player) {
        ServerLevel level = job.level;
        Predicate<BlockPos> fits = job.biomes == null || job.destinationChecked ? pos -> true
            : pos -> level.getBiome(pos).unwrapKey().map(key -> job.biomes.contains(key.location())).orElse(false);
        BlockPos landing = landing(level, job.destination, fits, job.surfaceOnly);
        if (landing == null) {
            return retry(job, player, job.destinationChecked ? "no safe ground" : "no safe ground in the right biome");
        }
        teleport(job, player, landing);
        return true;
    }

    private static boolean retry(Job job, ServerPlayer player, String why) {
        releaseTicket(job);
        job.checks++;
        if (job.biomeSearch != null) {
            job.biomeSearch.avoid(job.destination.getX(), job.destination.getZ(), 96);
        } else {
            job.structureSearch.avoid(job.destination.getX(), job.destination.getZ(), 64);
        }
        if (job.fromGoodSpot) {
            job.fromGoodSpot = false;
            searchNext(job);
            return false;
        }
        if (job.checks >= MAX_CHECKS) {
            message(player, Component.literal("Gave up after " + MAX_CHECKS + " spots (" + why + "). Try again from"
                + " somewhere else.").withStyle(ChatFormatting.RED));
            return true;
        }
        searchNext(job);
        return false;
    }

    private static boolean notFound(Job job, ServerPlayer player) {
        message(player, Component.literal("No " + job.label + " found within "
            + String.format(Locale.ROOT, "%,d", Searches.MAX_DISTANCE) + " blocks.").withStyle(ChatFormatting.RED));
        return true;
    }

    private static <T> T result(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A safe standing spot near {@code at} that passes {@code fits}: the surface first, then (unless the spawn needs
     * open sky) nearby floors around {@code at}'s height. Columns spiral outward from the spot.
     */
    private static BlockPos landing(ServerLevel level, BlockPos at, Predicate<BlockPos> fits, boolean surfaceOnly) {
        boolean ceiling = level.dimensionType().hasCeiling();
        for (int r = 0; r <= LANDING_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = at.getX() + dx;
                    int z = at.getZ() + dz;
                    if (!level.getWorldBorder().isWithinBounds(x, z)) {
                        continue;
                    }
                    if (!ceiling) {
                        BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                        if (safe(level, surface) && fits.test(surface)) {
                            return surface;
                        }
                    }
                    if (!surfaceOnly || ceiling) {
                        for (int y = at.getY() + 12; y >= at.getY() - 12; y--) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (safe(level, pos) && fits.test(pos)) {
                                return pos;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Solid, harmless ground under {@code feet}, and room for a player with no fluid. */
    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP) || !ground.getFluidState().isEmpty() || harmful(ground)) {
            return false;
        }
        for (BlockPos pos : new BlockPos[] {feet, feet.above()}) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty() || harmful(state)) {
                return false;
            }
        }
        return true;
    }

    private static boolean harmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
            || state.is(Blocks.POINTED_DRIPSTONE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.LAVA);
    }

    private static void teleport(Job job, ServerPlayer player, BlockPos landing) {
        MinecraftServer server = player.server;
        int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(landing)));
        // Vanilla /tp, so Essential Commands records the old position for /back.
        String command = String.format(Locale.ROOT, "execute in %s run tp %s %.1f %d %.1f",
            job.level.dimension().location(), player.getStringUUID(), landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        // Same-dimension /tp keeps fall distance; don't let a fall in progress hurt at the destination.
        player.resetFallDistance();

        String where = job.structureSearch != null ? job.label
            : job.level.getBiome(landing).unwrapKey().map(key -> RouteText.idName(key.location())).orElse("there");
        String target = job.target != null ? job.target
            : job.level.getBiome(landing).unwrapKey().map(key -> "biome:" + key.location()).orElse("biome:unknown");
        TeleportSpots.Spot spot = new TeleportSpots.Spot(job.level.dimension().location(), target,
            landing.getX(), landing.getY(), landing.getZ(), true);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        RATINGS.put(token, new Rating(player.getUUID(), spot, server.getTickCount() + RATING_TTL));

        message(player, Component.literal("Teleported to " + where + ", " + String.format(Locale.ROOT, "%,d", distance)
            + " blocks away" + (job.fromGoodSpot ? " (a spot rated good)" : "") + ". Use /back to return.")
            .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  Good spot? ").withStyle(ChatFormatting.GRAY)
            .append(button("[Good]", ChatFormatting.GREEN, "/cobblephase rate " + token + " good",
                "Use this spot first next time"))
            .append(Component.literal(" "))
            .append(button("[Bad]", ChatFormatting.RED, "/cobblephase rate " + token + " bad",
                "Skip this area in future searches")));
    }

    private static Component button(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withBold(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** Files a rating from a chat button; false when the token is unknown, expired or not this player's. */
    static boolean rate(ServerPlayer player, String token, boolean good) {
        Rating rating = RATINGS.get(token);
        if (rating == null || !rating.player().equals(player.getUUID())
            || rating.expires() < player.server.getTickCount()) {
            return false;
        }
        RATINGS.remove(token);
        TeleportSpots.Spot spot = rating.spot();
        TeleportSpots.get(player.server).rate(new TeleportSpots.Spot(spot.dimension(), spot.target(),
            spot.x(), spot.y(), spot.z(), good));
        return true;
    }

    public static void shutdown() {
        JOBS.values().forEach(job -> job.cancelled = true);
        JOBS.clear();
    }

    static void message(ServerPlayer player, Component text) {
        player.sendSystemMessage(Component.literal("[Tracker] ").withStyle(ChatFormatting.GOLD).append(text));
    }
}
