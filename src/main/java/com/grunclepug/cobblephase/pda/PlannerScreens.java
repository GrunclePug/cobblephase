package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import com.cobblemon.mod.common.api.habitats.HabitatPool;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.grunclepug.cobblephase.habitat.HabitatPhases;
import com.grunclepug.cobblephase.habitat.HabitatReminders;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The habitat planner: where and when a species can spawn from habitats, plus phase reminders. */
final class PlannerScreens {
    private static final int FIRST_POOL_SLOT = 9;

    private PlannerScreens() {
    }

    static void planner(PdaMenu menu) {
        ResourceLocation speciesId = menu.view.plannerSpecies;
        Species species = PokemonSpecies.getByIdentifier(speciesId);
        long gameTime = HabitatPhases.gameTime(menu.player.server);
        menu.set(4, icon(species == null ? new ItemStack(Items.PAPER) : PokemonItem.Companion.from(species),
            species == null ? text(speciesId.toString(), ChatFormatting.WHITE) : species.getTranslatedName().withStyle(ChatFormatting.WHITE),
            List.of(hint("Habitats that can spawn it, and when."),
                dim("Assumes a habitat's default phase order."),
                dim("A phase allows a spawn; it doesn't guarantee one.")), false), null);

        List<HabitatPool> pools = HabitatPhases.poolsFor(speciesId);
        for (int i = 0; i < Math.min(PdaMenu.BOTTOM - FIRST_POOL_SLOT, pools.size()); i++) {
            HabitatPool pool = pools.get(i);
            int count = pool.getPhaseCount();
            long day = HabitatPhases.day(gameTime);
            long until = HabitatPhases.ticksUntil(pool, speciesId, gameTime);
            HabitatReminders.Reminder reminder = new HabitatReminders.Reminder(pool.getId(), speciesId);
            boolean reminding = HabitatReminders.has(menu.player, reminder);

            List<Component> lore = new ArrayList<>();
            lore.add(hint("Phases: " + join(HabitatPhases.phasesFor(pool, speciesId)) + " (of " + Math.max(1, count) + ")"));
            lore.add(hint("Now: phase " + HabitatPhases.phaseOf(day, count) + ", next in "
                + HabitatPhases.duration(HabitatPhases.ticksToNextPhase(gameTime))));
            if (until == 0) {
                lore.add(text("Can spawn now!", ChatFormatting.GREEN));
            } else if (until > 0) {
                long nextDay = day + 1 + (until - HabitatPhases.ticksToNextPhase(gameTime)) / HabitatPhases.TICKS_PER_PHASE;
                lore.add(text("Next: phase " + HabitatPhases.phaseOf(nextDay, count) + " in " + HabitatPhases.duration(until),
                    ChatFormatting.YELLOW));
            }
            lore.add(Component.empty());
            lore.add(reminding ? text("🔔 Reminder set: click to cancel", ChatFormatting.AQUA)
                : hint("Click: remind me when it can spawn here"));
            menu.set(FIRST_POOL_SLOT + i, icon(until == 0 ? Items.FLOWERING_AZALEA : Items.MOSS_BLOCK,
                Component.translatable(pool.getName()).withStyle(ChatFormatting.GOLD), lore, reminding || until == 0),
                right -> toggle(menu, reminder));
        }
        if (pools.isEmpty()) {
            menu.set(22, icon(Items.BARRIER, text("No habitat spawns this Pokémon", ChatFormatting.RED), List.of(), false), null);
        }
        menu.back(PdaMenu.Screen.SPECIES);
        remindersButton(menu, 52);
    }

    static void reminders(PdaMenu menu) {
        long gameTime = HabitatPhases.gameTime(menu.player.server);
        List<HabitatReminders.Reminder> reminders = HabitatReminders.get(menu.player);
        for (int i = 0; i < Math.min(PdaMenu.PAGE_SIZE, reminders.size()); i++) {
            HabitatReminders.Reminder reminder = reminders.get(i);
            Species species = PokemonSpecies.getByIdentifier(reminder.species());
            HabitatPool pool = HabitatPhases.pool(reminder.pool());
            List<Component> lore = new ArrayList<>();
            lore.add(pool == null ? dim(reminder.pool().toString())
                : Component.literal("In ").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable(pool.getName()).withStyle(ChatFormatting.GOLD)));
            if (pool != null) {
                long until = HabitatPhases.ticksUntil(pool, reminder.species(), gameTime);
                lore.add(until == 0 ? text("Can spawn now; reminds at its next window", ChatFormatting.GREEN)
                    : hint("Next window in " + HabitatPhases.duration(Math.max(0, until))));
            }
            lore.add(hint("Click: remove"));
            menu.set(i, icon(species == null ? new ItemStack(Items.PAPER) : PokemonItem.Companion.from(species),
                species == null ? text(reminder.species().toString(), ChatFormatting.WHITE)
                    : species.getTranslatedName().withStyle(ChatFormatting.WHITE), lore, false),
                right -> HabitatReminders.toggle(menu.player, reminder));
        }
        if (reminders.isEmpty()) {
            menu.set(22, icon(Items.BELL, text("No reminders", ChatFormatting.GRAY),
                List.of(hint("Pick a Pokémon, then click a habitat.")), false), null);
        }
        menu.back(PdaMenu.Screen.SPECIES);
    }

    static void remindersButton(PdaMenu menu, int slot) {
        int count = HabitatReminders.get(menu.player).size();
        menu.set(slot, icon(Items.BELL, text("Your reminders (" + count + ")", ChatFormatting.AQUA),
            List.of(hint("Each one pings you once, when its"), hint("habitat's phase allows the spawn.")), false),
            right -> menu.view.go(PdaMenu.Screen.REMINDERS));
    }

    private static void toggle(PdaMenu menu, HabitatReminders.Reminder reminder) {
        if (!HabitatReminders.toggle(menu.player, reminder)) {
            menu.player.sendSystemMessage(ChatPrompt.prefix().append(Component.literal(
                "You have " + HabitatReminders.MAX_PER_PLAYER + " reminders already. Remove one first.").withStyle(ChatFormatting.RED)));
        }
    }

    private static String join(List<Integer> phases) {
        List<String> parts = new ArrayList<>();
        for (int phase : phases) {
            parts.add(Integer.toString(phase));
        }
        return String.join(", ", parts);
    }
}
