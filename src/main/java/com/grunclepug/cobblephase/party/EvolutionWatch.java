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
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition;
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition;
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition;
import com.cobblemon.mod.common.pokemon.requirements.BiomeRequirement;
import com.cobblemon.mod.common.pokemon.requirements.FriendshipRequirement;
import com.cobblemon.mod.common.pokemon.requirements.HeldItemRequirement;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;
import com.cobblemon.mod.common.pokemon.requirements.MoonPhaseRequirement;
import com.cobblemon.mod.common.pokemon.requirements.TimeRangeRequirement;
import com.grunclepug.cobblephase.tracker.RouteText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
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

    /** How the evolution happens once its requirements are met, naming the item or block where there is one. */
    public static String trigger(Evolution evolution) {
        if (evolution instanceof LevelUpEvolution) {
            return "levels up";
        }
        if (evolution instanceof ItemInteractionEvolution item) {
            return "is given " + items(item.getRequiredContext());
        }
        if (evolution instanceof TradeEvolution) {
            return "is traded";
        }
        if (evolution instanceof BlockClickEvolution block) {
            return "is used on " + registryLike(block.getRequiredContext());
        }
        return "evolves";
    }

    /** A requirement with its values: "Level 16+", "Friendship 160+ (now 120)", "Holding Moon Stone". */
    public static String describe(Requirement requirement, Pokemon pokemon) {
        if (requirement instanceof LevelRequirement level) {
            return level.getMaxLevel() >= 100 || level.getMaxLevel() == Integer.MAX_VALUE
                ? "Level " + level.getMinLevel() + "+" : "Level " + level.getMinLevel() + "–" + level.getMaxLevel();
        }
        if (requirement instanceof FriendshipRequirement friendship) {
            return "Friendship " + friendship.getAmount() + "+ (now " + pokemon.getFriendship() + ")";
        }
        if (requirement instanceof HeldItemRequirement held) {
            return "Holding " + items(held.getItemCondition());
        }
        if (requirement instanceof TimeRangeRequirement time) {
            String name = time.getRange().displayName() == null ? "" : time.getRange().displayName().getString();
            return "Time: " + (name.isBlank() ? "certain times" : name.toLowerCase(Locale.ROOT));
        }
        if (requirement instanceof BiomeRequirement biome) {
            String text = biome.getBiomeCondition() == null ? "" : "In " + registryLike(biome.getBiomeCondition());
            String not = biome.getBiomeAnticondition() == null ? "" : "not in " + registryLike(biome.getBiomeAnticondition());
            return text.isEmpty() ? (not.isEmpty() ? "Biome" : Character.toUpperCase(not.charAt(0)) + not.substring(1))
                : text + (not.isEmpty() ? "" : ", " + not);
        }
        if (requirement instanceof MoonPhaseRequirement moon) {
            return "Moon: " + moon.getMoonPhase().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        String tooltip = requirement.getPokedexTooltip();
        return tooltip != null && !tooltip.isBlank() ? Component.translatable(tooltip).getString() : requirementName(requirement);
    }

    /** "Moon Stone", "Moon Stone or Sun Stone", or a tag's name. */
    private static String items(ItemPredicate predicate) {
        if (predicate == null || predicate.items().isEmpty()) {
            return "the right item";
        }
        HolderSet<Item> set = predicate.items().get();
        if (set.unwrapKey().isPresent()) {
            return RouteText.idName(set.unwrapKey().get().location()) + " (any)";
        }
        List<String> names = new ArrayList<>();
        for (Holder<Item> item : set) {
            item.unwrapKey().ifPresent(key -> names.add(RouteText.idName(key.location())));
        }
        return names.isEmpty() ? "the right item" : String.join(" or ", names);
    }

    private static String registryLike(RegistryLikeCondition<?> condition) {
        if (condition instanceof RegistryLikeTagCondition<?> tag) {
            return RouteText.idName(tag.getTag().location()) + " (any)";
        }
        if (condition instanceof RegistryLikeIdentifierCondition<?> id) {
            return RouteText.idName(id.getIdentifier());
        }
        return "the right one";
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
