package com.grunclepug.cobblephase.party;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.variants.BlockClickEvolution;
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution;
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution;
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution;
import com.grunclepug.cobblephase.pda.PdaSound;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The evolution helper's pin: one party Pokémon per player. Every few seconds its evolutions are checked, and the
 * player is told once when every requirement of one is met (time, place, friendship and so on).
 */
public final class EvolutionWatch {
    public static final AttachmentType<UUID> PINNED = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath("cobblephase", "evolution_pin"),
        builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath());
    private static final int INTERVAL = 100;

    private EvolutionWatch() {
    }

    public static Iterable<Pokemon> party(ServerPlayer player) {
        return Cobblemon.INSTANCE.getStorage().getParty(player);
    }

    public static Pokemon find(ServerPlayer player, UUID id) {
        for (Pokemon pokemon : party(player)) {
            if (pokemon != null && pokemon.getUuid().equals(id)) {
                return pokemon;
            }
        }
        return null;
    }

    public static boolean ready(Pokemon pokemon, Evolution evolution) {
        for (Requirement requirement : evolution.getRequirements()) {
            if (!requirement.check(pokemon)) {
                return false;
            }
        }
        return true;
    }

    /** How the evolution happens once its requirements are met. */
    public static String trigger(Evolution evolution) {
        if (evolution instanceof LevelUpEvolution) {
            return "levels up";
        }
        if (evolution instanceof ItemInteractionEvolution) {
            return "is given the right item";
        }
        if (evolution instanceof TradeEvolution) {
            return "is traded";
        }
        if (evolution instanceof BlockClickEvolution) {
            return "is used on the right block";
        }
        return "evolves";
    }

    /** "LevelRequirement" → "Level", "TimeRangeRequirement" → "Time range". */
    public static String requirementName(Requirement requirement) {
        String name = requirement.getClass().getSimpleName().replace("Requirement", "");
        String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? "condition" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID pinned = player.getAttached(PINNED);
            if (pinned == null) {
                continue;
            }
            Pokemon pokemon = find(player, pinned);
            if (pokemon == null) {
                continue; // In the PC for now; keep the pin.
            }
            for (Evolution evolution : pokemon.getEvolutions()) {
                if (ready(pokemon, evolution)) {
                    player.sendSystemMessage(Component.literal("[Evolution] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(pokemon.getSpecies().getTranslatedName().withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" meets every requirement to evolve. It will once it "
                            + trigger(evolution) + ".").withStyle(ChatFormatting.GRAY)));
                    PdaSound.LEVEL_UP.play(player, null);
                    player.removeAttached(PINNED);
                    break;
                }
            }
        }
    }
}
