package com.grunclepug.cobblephase.world;

import com.cobblemon.mod.common.api.spawning.MoonPhaseRange;
import com.cobblemon.mod.common.api.spawning.TimeRange;
import com.grunclepug.cobblephase.pda.Pda;
import com.grunclepug.cobblephase.pda.PdaSettings;
import com.grunclepug.cobblephase.pda.PdaSound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One-shot alerts for the conditions spawns depend on: weather, time of day and moon phase, in the overworld. An
 * alert fires when its condition starts (false → true) while the player is online, then clears.
 */
public final class ConditionAlerts {
    /** A condition a player can ask to hear about. Times and moon phases use Cobblemon's own named ranges. */
    public record Alert(String id, String displayName, Item icon, Predicate<ServerLevel> active) {
    }

    public static final List<Alert> ALERTS = List.of(
        new Alert("thunder", "Thunderstorm", Items.LIGHTNING_ROD, ServerLevel::isThundering),
        new Alert("rain", "Rain", Items.WATER_BUCKET, ServerLevel::isRaining),
        new Alert("clear", "Clear weather", Items.SUNFLOWER, level -> !level.isRaining()),
        new Alert("dawn", "Dawn", Items.ORANGE_DYE, time("dawn")),
        new Alert("day", "Day", Items.GLOWSTONE_DUST, time("day")),
        new Alert("dusk", "Dusk", Items.RED_DYE, time("dusk")),
        new Alert("night", "Night", Items.BLACK_DYE, time("night")),
        new Alert("full_moon", "Full moon", Items.SNOWBALL, moon("full")),
        new Alert("new_moon", "New moon", Items.COAL, moon("new")));

    private static final Map<String, Boolean> WAS_ACTIVE = new HashMap<>();

    private ConditionAlerts() {
    }

    public static Alert byId(String id) {
        for (Alert alert : ALERTS) {
            if (alert.id().equals(id)) {
                return alert;
            }
        }
        return null;
    }

    public static boolean activeNow(MinecraftServer server, Alert alert) {
        return alert.active().test(server.overworld());
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        List<Alert> started = new ArrayList<>();
        for (Alert alert : ALERTS) {
            boolean active = alert.active().test(overworld);
            Boolean was = WAS_ACTIVE.put(alert.id(), active);
            if (was != null && !was && active) {
                started.add(alert);
            }
        }
        if (started.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PdaSettings settings = Pda.settings(player);
            List<String> remaining = new ArrayList<>(settings.alerts());
            boolean fired = false;
            for (Alert alert : started) {
                if (remaining.remove(alert.id())) {
                    fired = true;
                    player.sendSystemMessage(Component.literal("[Alert] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(alert.displayName() + " has started.").withStyle(ChatFormatting.WHITE)));
                }
            }
            if (fired) {
                PdaSound.CHIME.play(player, null);
                Pda.save(player, settings.withAlerts(remaining));
            }
        }
    }

    private static Predicate<ServerLevel> time(String name) {
        return level -> {
            TimeRange range = TimeRange.Companion.getTimeRanges().get(name);
            return range != null && range.contains((int) (level.getDayTime() % 24000L));
        };
    }

    /** The first of Cobblemon's moon phase ranges whose name contains {@code name} ("full", "new"). */
    private static Predicate<ServerLevel> moon(String name) {
        return level -> {
            for (Map.Entry<String, MoonPhaseRange> entry : MoonPhaseRange.Companion.getMoonPhaseRanges().entrySet()) {
                if (entry.getKey().contains(name)) {
                    return entry.getValue().contains(level.getMoonPhase());
                }
            }
            return false;
        };
    }
}
