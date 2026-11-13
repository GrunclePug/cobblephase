package com.grunclepug.cobblephase;

import com.cobblemon.mod.common.api.habitats.HabitatPool;
import com.cobblemon.mod.common.api.habitats.HabitatPools;
import com.cobblemon.mod.common.api.habitats.HabitatSpawn;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers {@code /phase <habitat>}, which reports a Cobblemon habitat's current spawn phase.
 *
 * <p>Mirrors {@code HabitatBlockEntity.calculatePhase} for the default SIMPLE phase order: the phase advances
 * once every 24000 ticks of total game time (not time of day), cycling through the pool's phase count.
 */
public final class Cobblephase implements ModInitializer {
    private static final long TICKS_PER_PHASE = 24000L;
    private static final String HABITATS_PREFIX = "habitats/";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phase")
            .then(Commands.argument("habitat", ResourceLocationArgument.id())
                .suggests(SUGGEST_HABITATS)
                .executes(Cobblephase::run)));
    }

    /** Short names ("flowerbed_clearing"), or full ids ("cobblemon:habitats/flowerbed_clearing") once a ':' is typed. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_HABITATS = (context, builder) -> {
        boolean full = builder.getRemaining().contains(":");
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : HabitatPools.INSTANCE.getHabitatPoolsById().keySet()) {
            names.add(full ? id.getNamespace() + ":" + HABITATS_PREFIX + shortName(id) : shortName(id));
        }
        names.sort(null);
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation query = ResourceLocationArgument.getId(context, "habitat");
        HabitatPool pool = find(query);
        if (pool == null) {
            source.sendFailure(Component.literal("Unknown habitat: " + shortName(query)));
            return 0;
        }

        int count = pool.getPhaseCount();
        long gameTime = source.getLevel().getGameTime();
        long day = gameTime / TICKS_PER_PHASE;
        int phase = phaseOf(day, count);
        int next = phaseOf(day + 1, count);
        long secondsLeft = (TICKS_PER_PHASE - gameTime % TICKS_PER_PHASE) / 20;

        MutableComponent header = Component.empty()
            .append(Component.translatable(pool.getName()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(": phase ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(phase + "/" + count).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(", next " + next + " in ~" + duration(secondsLeft)).withStyle(ChatFormatting.GRAY));

        MutableComponent species = Component.literal("Spawning now: ").withStyle(ChatFormatting.GRAY);
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (HabitatSpawn spawn : pool.getSpawns()) {
            if (spawn.getPhases() != null && !spawn.getPhases().contains(phase)) {
                continue;
            }
            if (!seen.add(spawn.getSpecies().getResourceIdentifier())) {
                continue;
            }
            if (seen.size() > 1) {
                species.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            species.append(spawn.getSpecies().getTranslatedName().withStyle(ChatFormatting.WHITE));
        }

        source.sendSuccess(() -> header, false);
        source.sendSuccess(() -> species, false);
        return phase;
    }

    /** Matches "flowerbed_clearing", "habitats/flowerbed_clearing" or "cobblemon:habitats/flowerbed_clearing". */
    private static HabitatPool find(ResourceLocation query) {
        boolean anyNamespace = query.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
        String name = shortName(query);
        for (Map.Entry<ResourceLocation, HabitatPool> entry : HabitatPools.INSTANCE.getHabitatPoolsById().entrySet()) {
            ResourceLocation id = entry.getKey();
            if (shortName(id).equals(name) && (anyNamespace || id.getNamespace().equals(query.getNamespace()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String shortName(ResourceLocation id) {
        String path = id.getPath();
        return path.startsWith(HABITATS_PREFIX) ? path.substring(HABITATS_PREFIX.length()) : path;
    }

    private static int phaseOf(long day, int count) {
        return count <= 1 ? 1 : (int) (day % count) + 1;
    }

    private static String duration(long seconds) {
        return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }
}
