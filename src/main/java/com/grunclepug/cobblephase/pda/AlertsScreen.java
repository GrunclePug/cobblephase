package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.hint;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;
import static com.grunclepug.cobblephase.pda.MenuItems.toggleTitle;

import com.grunclepug.cobblephase.world.ConditionAlerts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Condition alerts: pick weather, times of day or moon phases to be pinged once when they next start. */
final class AlertsScreen {
    private AlertsScreen() {
    }

    static void render(PdaMenu menu) {
        PdaSettings settings = Pda.settings(menu.player);
        menu.set(4, icon(net.minecraft.world.item.Items.CLOCK, text("Condition alerts", ChatFormatting.YELLOW), List.of(
            hint("Many spawns need a time, weather or moon."),
            hint("Pick one to be pinged once when it starts."),
            dim("Uses the overworld's weather and time.")), false), null);
        List<ConditionAlerts.Alert> alerts = ConditionAlerts.ALERTS;
        for (int i = 0; i < alerts.size(); i++) {
            ConditionAlerts.Alert alert = alerts.get(i);
            boolean on = settings.alerts().contains(alert.id());
            boolean active = ConditionAlerts.activeNow(menu.player.server, alert);
            List<Component> lore = new ArrayList<>();
            lore.add(active ? text("Happening right now", ChatFormatting.GREEN) : dim("Not happening right now"));
            lore.add(hint(on ? "Click: cancel the alert" : "Click: ping me when it next starts"));
            menu.set(18 + i, icon(alert.icon(), toggleTitle(alert.displayName(), on), lore, on), right -> {
                List<String> next = new ArrayList<>(Pda.settings(menu.player).alerts());
                if (!next.remove(alert.id())) {
                    next.add(alert.id());
                }
                Pda.save(menu.player, Pda.settings(menu.player).withAlerts(next));
            });
        }
        menu.back(PdaMenu.Screen.HOME);
    }
}
