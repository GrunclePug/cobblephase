package com.grunclepug.cobblephase.world;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.grunclepug.cobblephase.pda.Pda;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stops a player's own attacks (melee or projectiles) from hurting wild shinies, unless they turn it off in the PDA.
 * Battles don't deal damage this way, so they're unaffected.
 */
public final class ShinyGuard {
    private ShinyGuard() {
    }

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof PokemonEntity pokemon) || !pokemon.getPokemon().isWild() || !pokemon.getPokemon().getShiny()) {
                return true;
            }
            if (!(source.getEntity() instanceof ServerPlayer player) || !Pda.settings(player).shinyGuard()) {
                return true;
            }
            player.displayClientMessage(Component.literal("Shiny guard stopped that hit. Turn it off in the PDA menu.")
                .withStyle(ChatFormatting.GOLD), true);
            return false;
        });
    }
}
