package com.grunclepug.cobblephase.habitat;

import com.cobblemon.mod.common.api.habitats.HabitatPool;
import com.cobblemon.mod.common.api.habitats.HabitatSpawn;
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Reads a real habitat block: its pool, phase order (including random orders, which /phase can't predict), current
 * phase and what it can spawn now. Use the PDA on the block, or ask for the nearest one in loaded chunks.
 */
public final class HabitatObserver {
    private static final int SCAN_CHUNKS = 4;

    private HabitatObserver() {
    }

    /** Reports on the nearest habitat block in loaded chunks around the player; false if none. */
    public static boolean nearest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HabitatBlockEntity best = null;
        double bestSq = Double.MAX_VALUE;
        int cx = player.getBlockX() >> 4;
        int cz = player.getBlockZ() >> 4;
        for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
            for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof HabitatBlockEntity habitat && habitat.getPool() != null) {
                        double sq = entity.getBlockPos().distToCenterSqr(player.position());
                        if (sq < bestSq) {
                            best = habitat;
                            bestSq = sq;
                        }
                    }
                }
            }
        }
        if (best == null) {
            player.sendSystemMessage(prefix().append(Component.literal("No habitat block within "
                + SCAN_CHUNKS * 16 + " blocks.").withStyle(ChatFormatting.GRAY)));
            return false;
        }
        describe(player, best);
        return true;
    }

    public static void describe(ServerPlayer player, HabitatBlockEntity habitat) {
        HabitatPool pool = habitat.getPool();
        if (pool == null) {
            player.sendSystemMessage(prefix().append(Component.literal("This habitat isn't set up yet.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        BlockPos pos = habitat.getBlockPos();
        int distance = (int) Math.round(Math.sqrt(pos.distToCenterSqr(player.position())));
        String order = habitat.getPhaseOrder() == null ? "?" : habitat.getPhaseOrder().name().toLowerCase(Locale.ROOT);
        long gameTime = HabitatPhases.gameTime(player.server);
        player.sendSystemMessage(prefix()
            .append(Component.translatable(pool.getName()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + distance + "m): phase "
                + habitat.getCurrentPhase() + "/" + habitat.getNumberOfPhases() + ", " + order + " order, changes in "
                + HabitatPhases.duration(HabitatPhases.ticksToNextPhase(gameTime)) + ".").withStyle(ChatFormatting.GRAY)));
        Set<String> names = new LinkedHashSet<>();
        MutableComponent species = Component.literal("  Can spawn now: ").withStyle(ChatFormatting.GRAY);
        int phase = habitat.getCurrentPhase();
        for (HabitatSpawn spawn : pool.getSpawns()) {
            if ((spawn.getPhases() == null || spawn.getPhases().contains(phase))
                && names.add(spawn.getSpecies().getResourceIdentifier().toString())) {
                species.append(Component.literal(names.size() > 1 ? ", " : "").withStyle(ChatFormatting.GRAY))
                    .append(spawn.getSpecies().getTranslatedName().withStyle(ChatFormatting.WHITE));
            }
        }
        if (names.isEmpty()) {
            species.append(Component.literal("nothing in this phase").withStyle(ChatFormatting.WHITE));
        }
        player.sendSystemMessage(species);
    }

    private static MutableComponent prefix() {
        return Component.literal("[Habitat] ").withStyle(ChatFormatting.DARK_GREEN);
    }
}
