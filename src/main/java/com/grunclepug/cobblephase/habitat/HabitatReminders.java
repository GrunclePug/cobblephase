package com.grunclepug.cobblephase.habitat;

import com.cobblemon.mod.common.api.habitats.HabitatPool;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.pda.PdaSound;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One-shot reminders: tell a player when a habitat's phase rolls over to one where a species can spawn. Checked
 * once per phase change, for online players only; a reminder stays until it fires while its player is online.
 */
public final class HabitatReminders {
    public static final int MAX_PER_PLAYER = 27;

    public record Reminder(ResourceLocation pool, ResourceLocation species) {
        static final Codec<Reminder> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("pool").forGetter(Reminder::pool),
            ResourceLocation.CODEC.fieldOf("species").forGetter(Reminder::species)
        ).apply(i, Reminder::new));
    }

    public static final AttachmentType<List<Reminder>> REMINDERS = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath("cobblephase", "habitat_reminders"),
        builder -> builder.persistent(Reminder.CODEC.listOf()).copyOnDeath());

    private static long lastDay = -1;

    private HabitatReminders() {
    }

    public static List<Reminder> get(ServerPlayer player) {
        return player.getAttachedOrElse(REMINDERS, List.of());
    }

    public static boolean has(ServerPlayer player, Reminder reminder) {
        return get(player).contains(reminder);
    }

    /** Adds or removes a reminder; false when adding would pass the limit. */
    public static boolean toggle(ServerPlayer player, Reminder reminder) {
        List<Reminder> list = new ArrayList<>(get(player));
        if (!list.remove(reminder)) {
            if (list.size() >= MAX_PER_PLAYER) {
                return false;
            }
            list.add(reminder);
        }
        player.setAttached(REMINDERS, List.copyOf(list));
        return true;
    }

    public static void tick(MinecraftServer server) {
        long gameTime = HabitatPhases.gameTime(server);
        long day = HabitatPhases.day(gameTime);
        if (day == lastDay) {
            return;
        }
        boolean first = lastDay < 0;
        lastDay = day;
        if (first) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<Reminder> reminders = get(player);
            if (reminders.isEmpty()) {
                continue;
            }
            List<Reminder> remaining = new ArrayList<>();
            for (Reminder reminder : reminders) {
                HabitatPool pool = HabitatPhases.pool(reminder.pool());
                if (pool == null) {
                    continue; // The habitat no longer exists; drop the reminder.
                }
                int phase = HabitatPhases.phaseOf(day, pool.getPhaseCount());
                if (HabitatPhases.spawnsIn(pool, reminder.species(), phase)) {
                    notify(player, pool, reminder.species(), phase);
                } else {
                    remaining.add(reminder);
                }
            }
            if (remaining.size() != reminders.size()) {
                player.setAttached(REMINDERS, List.copyOf(remaining));
            }
        }
    }

    private static void notify(ServerPlayer player, HabitatPool pool, ResourceLocation speciesId, int phase) {
        Species species = PokemonSpecies.getByIdentifier(speciesId);
        player.sendSystemMessage(Component.literal("[Habitat] ").withStyle(ChatFormatting.DARK_GREEN)
            .append(species == null ? Component.literal(speciesId.toString()) : species.getTranslatedName().withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" can spawn in ").withStyle(ChatFormatting.GRAY))
            .append(Component.translatable(pool.getName()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" now (phase " + phase + "/" + pool.getPhaseCount() + ", for "
                + HabitatPhases.duration(HabitatPhases.TICKS_PER_PHASE) + ").").withStyle(ChatFormatting.GRAY)));
        PdaSound.CHIME.play(player, null);
    }
}
