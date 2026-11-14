package com.grunclepug.cobblephase.compat.rct;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData;
import com.gitlab.srcmc.rctmod.api.data.save.TrainerPlayerData;
import com.gitlab.srcmc.rctmod.api.service.SeriesManager;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import com.grunclepug.cobblephase.pda.Pda;
import com.grunclepug.cobblephase.pda.PdaSound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Trainer visits: every so often, the next trainer in a player's Radical Cobblemon Trainers series turns up within
 * 10 blocks to battle them. The trainer is spawned by RCT itself ({@code TrainerSpawner.attemptSpawnFor}), so RCT's
 * rules still apply: valid ground, unique trainers (one Gardenia at a time), and its per-player trainer limit.
 */
public final class TrainerVisits {
    /** Online time between visits. */
    private static final int MIN_GAP = 20 * 60 * 20;
    private static final int MAX_GAP = 40 * 60 * 20;
    /** When a visit can't happen right now (battle, no room), try again this soon. */
    private static final int RETRY = 60 * 20;
    private static final int MAX_RETRIES = 10;
    private static final int MIN_DISTANCE = 5;
    private static final int MAX_DISTANCE = 10;
    private static final int POSITION_TRIES = 16;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Per player: ticks of online time until their next visit, and failed tries in a row. */
    private static final Map<UUID, long[]> SCHEDULE = new HashMap<>();

    private TrainerVisits() {
    }

    public static void forget(ServerPlayer player) {
        SCHEDULE.remove(player.getUUID());
    }

    public static long ticksUntil(ServerPlayer player) {
        long[] entry = SCHEDULE.get(player.getUUID());
        return entry == null ? -1 : Math.max(0, entry[0]);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long[] entry = SCHEDULE.computeIfAbsent(player.getUUID(), id -> new long[] {gap(), 0});
            if (!Pda.settings(player).trainerVisits()) {
                continue;
            }
            entry[0] -= 20;
            if (entry[0] > 0) {
                continue;
            }
            if (visit(player)) {
                entry[0] = gap();
                entry[1] = 0;
            } else if (++entry[1] >= MAX_RETRIES) {
                entry[0] = gap();
                entry[1] = 0;
            } else {
                entry[0] = RETRY;
            }
        }
    }

    /** An operator's "call now": a visit right away, with the reason when it can't happen. */
    public static void visitNow(ServerPlayer player) {
        if (nextIds(player).isEmpty()) {
            player.sendSystemMessage(Component.literal("[Trainers] No next trainer in your current series.")
                .withStyle(ChatFormatting.GRAY));
        } else if (visit(player)) {
            SCHEDULE.put(player.getUUID(), new long[] {gap(), 0});
        } else {
            player.sendSystemMessage(Component.literal("[Trainers] Couldn't call one now: in a battle, no room nearby,"
                + " or RCT's trainer limit or uniqueness rule said no.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Spawns one of the player's next trainers nearby; false when it can't right now. */
    private static boolean visit(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || BattleRegistry.getBattleByParticipatingPlayer(player) != null
            || RCTMod.getInstance().isInBattle(player)) {
            return false;
        }
        List<String> next = nextIds(player);
        if (next.isEmpty()) {
            return true; // Nothing to fight (series done or none chosen); wait for the next slot.
        }
        String trainer = next.get(RANDOM.nextInt(next.size()));
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < POSITION_TRIES; i++) {
            BlockPos pos = position(level, player);
            if (pos == null) {
                continue;
            }
            // Pinned home near here, spawned for this player (keeps RCT's limits), no chance roll.
            TrainerMob mob = RCTMod.getInstance().getTrainerSpawner()
                .attemptSpawnFor(player, trainer, pos, true, false, true, 1.0, 1.0);
            if (mob != null) {
                player.sendSystemMessage(Component.literal("[Trainers] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(name(trainer)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" has come to challenge you!").withStyle(ChatFormatting.WHITE)));
                PdaSound.VILLAGE_BELL.play(player, mob.position());
                return true;
            }
        }
        return false;
    }

    /** A standing spot 5–10 blocks away, near the player's height: surface if close to it, else a floor nearby. */
    private static BlockPos position(ServerLevel level, ServerPlayer player) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double distance = MIN_DISTANCE + RANDOM.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(surface - player.getBlockY()) <= 4) {
            return new BlockPos(x, surface, z);
        }
        for (int y = player.getBlockY() + 4; y >= player.getBlockY() - 4; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir() && level.getBlockState(pos.below()).getFluidState().isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    /** The player's next required trainers in their current series, as RCT's trainer card shows them. */
    private static List<String> nextIds(ServerPlayer player) {
        RCTMod rct = RCTMod.getInstance();
        TrainerPlayerData data = rct.getTrainerManager().getData(player);
        if (data == null || data.isInEmptySeries() || data.isSeriesCompleted()) {
            return List.of();
        }
        SeriesManager.SeriesGraph graph = rct.getSeriesManager().getGraph(data.getCurrentSeries());
        List<String> ids = new ArrayList<>();
        graph.getNext(data.getDefeatedTrainerIds()).stream().forEach(node -> ids.add(node.id()));
        return ids;
    }

    public static List<String> nextNames(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        for (String id : nextIds(player)) {
            names.add(name(id));
        }
        return names;
    }

    private static String name(String trainerId) {
        TrainerMobData data = RCTMod.getInstance().getTrainerManager().getData(trainerId);
        String identity = data == null || data.getTrainerTeam() == null ? null : data.getTrainerTeam().getIdentity();
        return identity == null || identity.isBlank() ? trainerId : identity;
    }

    private static long gap() {
        return MIN_GAP + RANDOM.nextInt(MAX_GAP - MIN_GAP);
    }
}
