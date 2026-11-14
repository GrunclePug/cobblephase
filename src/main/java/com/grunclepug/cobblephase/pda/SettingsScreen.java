package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import com.grunclepug.cobblephase.compat.Rct;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The PDA's settings: range, repeat, mute, catch news, shiny guard and trainer visits. */
final class SettingsScreen {
    private SettingsScreen() {
    }

    static void render(PdaMenu menu) {
        ServerPlayer player = menu.player;
        PdaSettings settings = Pda.settings(player);
        menu.set(19, icon(Items.SPYGLASS, text("Range: " + settings.radius() + " blocks", ChatFormatting.AQUA),
            List.of(hint("Click to change")), false), right -> Pda.save(player, Pda.settings(player).nextRadius()));
        menu.set(20, icon(Items.REPEATER, text("Repeat: " + settings.repeat().displayName, ChatFormatting.AQUA), List.of(
            hint("The selected result's sound repeats,"),
            hint("faster the closer you get."),
            dim("Always: while the PDA is in your inventory"),
            dim("While held: only with it in your hand"),
            dim("Off: one ping when found"),
            hint("Click to change")), false), right -> Pda.save(player, Pda.settings(player).nextRepeat()));
        menu.set(21, icon(settings.muted() ? Items.GRAY_DYE : Items.LIME_DYE,
            text(settings.muted() ? "Pings: muted" : "Pings: on", settings.muted() ? ChatFormatting.RED : ChatFormatting.GREEN),
            List.of(hint("Click to " + (settings.muted() ? "unmute" : "mute")),
                dim("Muted still tracks and points, without sounds or chat.")), false),
            right -> Pda.save(player, Pda.settings(player).withMuted(!Pda.settings(player).muted())));
        menu.set(23, icon(Items.FIREWORK_ROCKET,
            text(settings.announcements() ? "Catch news: on" : "Catch news: off",
                settings.announcements() ? ChatFormatting.GREEN : ChatFormatting.RED),
            List.of(hint("Chat news when anyone catches a shiny,"), hint("the server's first of a species,"),
                hint("or posts a wish."), hint("Click to turn " + (settings.announcements() ? "off" : "on"))), false),
            right -> Pda.save(player, Pda.settings(player).withAnnouncements(!Pda.settings(player).announcements())));
        menu.set(24, icon(settings.shinyGuard() ? Items.SHIELD : Items.IRON_SWORD,
            text(settings.shinyGuard() ? "Shiny guard: on" : "Shiny guard: off",
                settings.shinyGuard() ? ChatFormatting.GREEN : ChatFormatting.RED),
            List.of(hint("Stops your own hits and arrows"), hint("from hurting wild shinies."),
                hint("Click to turn " + (settings.shinyGuard() ? "off" : "on"))), false),
            right -> Pda.save(player, Pda.settings(player).withShinyGuard(!Pda.settings(player).shinyGuard())));
        if (Rct.available()) {
            menu.set(25, icon(settings.trainerVisits() ? Items.TARGET : Items.GRAY_DYE,
                text(settings.trainerVisits() ? "Trainer visits: on" : "Trainer visits: off",
                    settings.trainerVisits() ? ChatFormatting.GREEN : ChatFormatting.RED),
                List.of(hint("Every 20–40 minutes your next Radical"), hint("Cobblemon Trainers trainer turns up"),
                    hint("within 10 blocks to battle you."),
                    hint("Click to turn " + (settings.trainerVisits() ? "off" : "on"))), false),
                right -> Pda.save(player, Pda.settings(player).withTrainerVisits(!Pda.settings(player).trainerVisits())));
        }
        menu.back(PdaMenu.Screen.HOME);
    }
}
