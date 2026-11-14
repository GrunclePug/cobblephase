package com.grunclepug.cobblephase.compat;

import com.grunclepug.cobblephase.compat.rct.TrainerVisits;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only door to Radical Cobblemon Trainers. Its classes are referenced only from {@link TrainerVisits}, which is
 * loaded only when the mod is installed, so cobblephase still runs without it.
 */
public final class Rct {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("rctmod");

    private Rct() {
    }

    public static boolean available() {
        return LOADED;
    }

    public static void tick(MinecraftServer server) {
        if (LOADED) {
            TrainerVisits.tick(server);
        }
    }

    public static void forget(ServerPlayer player) {
        if (LOADED) {
            TrainerVisits.forget(player);
        }
    }

    /** Display names of the player's next trainers in their current series; empty when none or RCT is absent. */
    public static List<String> nextTrainers(ServerPlayer player) {
        return LOADED ? TrainerVisits.nextNames(player) : List.of();
    }

    public static void visitNow(ServerPlayer player) {
        if (LOADED) {
            TrainerVisits.visitNow(player);
        }
    }

    /** Ticks until the player's next trainer visit, or -1 when none is scheduled. */
    public static long ticksUntilVisit(ServerPlayer player) {
        return LOADED ? TrainerVisits.ticksUntil(player) : -1;
    }
}
