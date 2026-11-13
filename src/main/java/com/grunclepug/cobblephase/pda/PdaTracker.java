package com.grunclepug.cobblephase.pda;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player scanning for wild Pokémon that match the player's rules, while they carry a PDA. Scans run once a
 * second per player (staggered); nothing ticks per Pokémon and nothing runs for players without a PDA.
 */
public final class PdaTracker {
    private static final int SCAN_INTERVAL = 20;
    private static final int BAR_INTERVAL = 10;
    /** A Pokémon out of range this long pings again when it comes back. */
    private static final long SEEN_EXPIRY = 60 * 20;
    private static final int USE_DEBOUNCE = 3;
    /** Repeat interval in ticks: fastest within {@link #BEEP_NEAR} blocks, slowest at the edge of the range. */
    private static final int BEEP_FASTEST = 8;
    private static final int BEEP_SLOWEST = 100;
    private static final double BEEP_NEAR = 4.0;
    /** Pause before the first repeat after a ping or a new selection. */
    private static final int BEEP_AFTER_SELECT = 10;
    private static final int MAX_CHAT_LINES = 3;
    private static final int BAR_ENTRIES = 4;
    private static final int SHOW_HEIGHT_FROM = 4;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final Map<UUID, State> STATES = new HashMap<>();

    private PdaTracker() {
    }

    /** A matching Pokémon and the index of the first (highest-priority) rule it matched. */
    public record Result(PokemonEntity entity, int rule) {
    }

    private record Matcher(int index, PdaRule rule, Set<ResourceLocation> species, Set<String> labels,
                           Set<String> buckets) {
        static Matcher of(int index, PdaRule rule) {
            return new Matcher(index, rule, Set.copyOf(rule.species()), Set.copyOf(rule.labels()),
                Set.copyOf(rule.buckets()));
        }

        boolean matches(PokemonEntity entity, Pokemon pokemon, AbstractPokedexManager pokedex) {
            if (rule.shinyOnly() && !pokemon.getShiny()) {
                return false;
            }
            if (rule.uncaughtOnly()
                && pokedex.getKnowledgeForSpecies(pokemon.getSpecies().getResourceIdentifier()) == PokedexEntryProgress.OWNED) {
                return false;
            }
            if (!buckets.isEmpty()) {
                // Pokémon from fishing, commands or before this mod have no bucket; Set.copyOf rejects null lookups.
                String bucket = entity.getAttached(Pda.SPAWN_BUCKET);
                if (bucket == null || !buckets.contains(bucket)) {
                    return false;
                }
            }
            if (species.isEmpty() && labels.isEmpty()) {
                return true;
            }
            Species kind = pokemon.getSpecies();
            if (species.contains(kind.getResourceIdentifier())) {
                return true;
            }
            for (String label : labels) {
                if (kind.getLabels().contains(label) || pokemon.getForm().getLabels().contains(label)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class State {
        final Map<UUID, Long> seen = new HashMap<>();
        List<Result> results = List.of();
        UUID selected;
        UUID needleEntity;
        GlobalPos needle;
        long lastUse = -USE_DEBOUNCE;
        long nextBeep;
        PdaSettings compiledFor;
        List<Matcher> matchers = List.of();

        List<Matcher> matchers(PdaSettings settings) {
            if (settings != compiledFor) {
                List<Matcher> list = new ArrayList<>();
                for (int i = 0; i < settings.rules().size(); i++) {
                    if (settings.rules().get(i).active()) {
                        list.add(Matcher.of(i, settings.rules().get(i)));
                    }
                }
                matchers = list;
                compiledFor = settings;
            }
            return matchers;
        }
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long phase = now + player.getId();
            if (phase % SCAN_INTERVAL == 0) {
                scan(player, now);
            }
            if (phase % BAR_INTERVAL == 0 && PdaItem.held(player)) {
                actionBar(player);
            }
            beep(player, now);
        }
    }

    /** Repeats the selected result's sound, more often the closer it is, like a metal detector. */
    private static void beep(ServerPlayer player, long now) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.selected == null || now < state.nextBeep) {
            return;
        }
        PdaSettings settings = Pda.settings(player);
        Result selected = selected(state);
        if (selected == null || settings.muted() || settings.repeat() == PdaSettings.Repeat.OFF
            || (settings.repeat() == PdaSettings.Repeat.HELD && !PdaItem.held(player))) {
            return;
        }
        settings.soundFor(selected.rule()).play(player, selected.entity().position());
        state.nextBeep = now + beepInterval(Math.sqrt(selected.entity().distanceToSqr(player)), settings.radius());
    }

    private static int beepInterval(double distance, int radius) {
        double t = Mth.clamp((distance - BEEP_NEAR) / (radius - BEEP_NEAR), 0.0, 1.0);
        return (int) Math.round(Mth.lerp(t, BEEP_FASTEST, BEEP_SLOWEST));
    }

    /** True once per click: block use, item use and both hands can all fire for one right-click. */
    static boolean claimUse(ServerPlayer player) {
        State state = state(player);
        long now = player.server.getTickCount();
        if (now - state.lastUse < USE_DEBOUNCE) {
            return false;
        }
        state.lastUse = now;
        return true;
    }

    private static void scan(ServerPlayer player, long now) {
        State state = state(player);
        state.seen.values().removeIf(tick -> now - tick > SEEN_EXPIRY);
        PdaSettings settings = Pda.settings(player);
        List<Matcher> matchers = state.matchers(settings);
        if (player.isSpectator() || matchers.isEmpty() || !PdaItem.carried(player)) {
            state.results = List.of();
            state.selected = null;
            state.needle = null;
            state.needleEntity = null;
            PdaItem.pointAll(player, null);
            return;
        }

        double radiusSq = (double) settings.radius() * settings.radius();
        List<PokemonEntity> nearby = player.serverLevel().getEntitiesOfClass(PokemonEntity.class,
            player.getBoundingBox().inflate(settings.radius()),
            entity -> entity.isAlive() && !entity.isBattling() && entity.getPokemon().isWild()
                && entity.distanceToSqr(player) <= radiusSq);

        AbstractPokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
        List<Result> matches = new ArrayList<>();
        List<Result> fresh = new ArrayList<>();
        for (PokemonEntity entity : nearby) {
            Pokemon pokemon = entity.getPokemon();
            for (Matcher matcher : matchers) {
                if (matcher.matches(entity, pokemon, pokedex)) {
                    Result result = new Result(entity, matcher.index());
                    matches.add(result);
                    if (state.seen.put(entity.getUUID(), now) == null) {
                        fresh.add(result);
                    }
                    break;
                }
            }
        }

        Comparator<Result> byPriority = Comparator.<Result>comparingInt(Result::rule)
            .thenComparingDouble(r -> r.entity().distanceToSqr(player));
        if (!fresh.isEmpty() && !settings.muted()) {
            fresh.sort(byPriority);
            ping(player, settings, fresh);
            state.nextBeep = now + BEEP_AFTER_SELECT;
        }

        // The closest of each species, with shinies kept apart so a nearby normal one can't hide them.
        Map<String, Result> best = new LinkedHashMap<>();
        matches.sort(Comparator.comparingDouble(r -> r.entity().distanceToSqr(player)));
        for (Result result : matches) {
            Pokemon pokemon = result.entity().getPokemon();
            best.putIfAbsent(pokemon.getSpecies().getResourceIdentifier() + (pokemon.getShiny() ? "#shiny" : ""), result);
        }
        List<Result> results = new ArrayList<>(best.values());
        state.results = List.copyOf(results);
        if (indexOf(results, state.selected) < 0) {
            state.selected = results.isEmpty() ? null : results.getFirst().entity().getUUID();
        }
        updateNeedle(player, state);
        if (player.containerMenu instanceof PdaMenu menu) {
            menu.refreshResults();
        }
    }

    private static void ping(ServerPlayer player, PdaSettings settings, List<Result> fresh) {
        Result first = fresh.getFirst();
        settings.soundFor(first.rule()).play(player, first.entity().position());
        for (int i = 0; i < Math.min(MAX_CHAT_LINES, fresh.size()); i++) {
            player.sendSystemMessage(pingLine(player, fresh.get(i).entity()));
        }
        if (fresh.size() > MAX_CHAT_LINES) {
            player.sendSystemMessage(Component.literal("[PDA] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("+" + (fresh.size() - MAX_CHAT_LINES) + " more nearby")
                    .withStyle(ChatFormatting.GRAY)));
        }
    }

    private static Component pingLine(ServerPlayer player, PokemonEntity entity) {
        MutableComponent line = Component.literal("[PDA] ").withStyle(ChatFormatting.AQUA)
            .append(PdaCatalog.name(entity.getPokemon()).withStyle(ChatFormatting.WHITE));
        String bucket = entity.getAttached(Pda.SPAWN_BUCKET);
        if (bucket != null) {
            line.append(Component.literal(" · " + PdaCatalog.bucketName(bucket)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        BlockPos pos = entity.blockPosition();
        line.append(Component.literal(" · " + distance(player, entity) + "m " + compass(player, entity.position())
            + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")").withStyle(ChatFormatting.GRAY));
        return line;
    }

    /** Keeps the selected target's needle position, moving it only when the target has moved noticeably. */
    private static void updateNeedle(ServerPlayer player, State state) {
        Result selected = selected(state);
        if (selected == null) {
            state.needle = null;
            state.needleEntity = null;
        } else {
            PokemonEntity entity = selected.entity();
            BlockPos pos = entity.blockPosition();
            // Moving the needle replays the held-item bob on the client, so only move it when it matters.
            double threshold = Mth.clamp(Math.sqrt(entity.distanceToSqr(player)) / 10.0, 1.0, 4.0);
            if (state.needle == null || !entity.getUUID().equals(state.needleEntity)
                || !state.needle.dimension().equals(entity.level().dimension())
                || state.needle.pos().distSqr(pos) > threshold * threshold) {
                state.needle = GlobalPos.of(entity.level().dimension(), pos);
                state.needleEntity = entity.getUUID();
            }
        }
        PdaItem.pointAll(player, state.needle);
    }

    /** Selects the next result (sneak + right-click). */
    static void cycle(ServerPlayer player) {
        State state = state(player);
        List<Result> live = live(state);
        if (live.isEmpty()) {
            player.displayClientMessage(Component.literal("No Pokémon to point at.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int next = (indexOf(live, state.selected) + 1) % live.size();
        select(player, live.get(next).entity().getUUID());
    }

    static void select(ServerPlayer player, UUID entity) {
        State state = state(player);
        state.selected = entity;
        state.nextBeep = player.server.getTickCount() + BEEP_AFTER_SELECT;
        updateNeedle(player, state);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.5f, 1.4f);
        if (PdaItem.held(player)) {
            actionBar(player);
        }
    }

    /** Current results whose Pokémon are still around, nearest first as of the last scan. */
    static List<Result> results(ServerPlayer player) {
        return live(state(player));
    }

    static UUID selectedId(ServerPlayer player) {
        return state(player).selected;
    }

    private static List<Result> live(State state) {
        List<Result> live = new ArrayList<>();
        for (Result result : state.results) {
            if (result.entity().isAlive()) {
                live.add(result);
            }
        }
        return live;
    }

    private static Result selected(State state) {
        for (Result result : live(state)) {
            if (result.entity().getUUID().equals(state.selected)) {
                return result;
            }
        }
        return null;
    }

    private static int indexOf(List<Result> results, UUID id) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).entity().getUUID().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void actionBar(ServerPlayer player) {
        State state = state(player);
        PdaSettings settings = Pda.settings(player);
        MutableComponent bar = Component.empty();
        if (settings.muted()) {
            bar.append(Component.literal("[muted] ").withStyle(ChatFormatting.RED));
        }
        List<Result> live = live(state);
        if (state.matchers(settings).isEmpty()) {
            bar.append(Component.literal("No active ping rules. Right-click to set some up.").withStyle(ChatFormatting.GRAY));
        } else if (live.isEmpty()) {
            bar.append(Component.literal("Scanning... nothing within " + settings.radius() + "m").withStyle(ChatFormatting.GRAY));
        } else {
            int selected = Math.max(0, indexOf(live, state.selected));
            // Show the page of results that contains the selected one, so it is always visible.
            int start = selected / BAR_ENTRIES * BAR_ENTRIES;
            for (int i = start; i < Math.min(start + BAR_ENTRIES, live.size()); i++) {
                if (i > start) {
                    bar.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                }
                bar.append(entry(player, live.get(i).entity(), i == selected));
            }
            if (live.size() > BAR_ENTRIES) {
                bar.append(Component.literal("  (" + (selected + 1) + "/" + live.size() + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        player.displayClientMessage(bar, true);
    }

    private static Component entry(ServerPlayer player, PokemonEntity entity, boolean selected) {
        Vec3 pos = entity.position();
        MutableComponent text = Component.literal((selected ? "▶ " : "") + arrow(player, pos) + " ")
            .append(PdaCatalog.name(entity.getPokemon()))
            .append(" " + distance(player, entity) + "m" + height(player, pos));
        return text.withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
    }

    private static int distance(ServerPlayer player, PokemonEntity entity) {
        return (int) Math.round(Math.sqrt(entity.distanceToSqr(player)));
    }

    /** Direction relative to where the player is facing. */
    private static String arrow(ServerPlayer player, Vec3 target) {
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(bearing - player.getYRot());
        return ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];
    }

    /** Absolute compass direction, for chat lines that outlive the player's facing. */
    private static String compass(ServerPlayer player, Vec3 target) {
        double angle = Math.toDegrees(Math.atan2(target.x - player.getX(), player.getZ() - target.z));
        return COMPASS[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    private static String height(ServerPlayer player, Vec3 target) {
        int dy = (int) Math.round(target.y - player.getY());
        if (Math.abs(dy) < SHOW_HEIGHT_FROM) {
            return "";
        }
        return dy > 0 ? " ▲" + dy : " ▼" + -dy;
    }
}
