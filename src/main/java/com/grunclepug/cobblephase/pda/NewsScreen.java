package com.grunclepug.cobblephase.pda;

import static com.grunclepug.cobblephase.pda.MenuItems.dim;
import static com.grunclepug.cobblephase.pda.MenuItems.icon;
import static com.grunclepug.cobblephase.pda.MenuItems.text;

import com.grunclepug.cobblephase.news.NewsLog;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Items;

/** Recent server news: firsts, shinies, wishes, outbreaks, and notes meant for this player. */
final class NewsScreen {
    private NewsScreen() {
    }

    static void render(PdaMenu menu) {
        List<NewsLog.Entry> entries = NewsLog.get(menu.player.server).visibleTo(menu.player.getUUID());
        long now = System.currentTimeMillis();
        int start = menu.pager(entries.size());
        for (int i = 0; i < PdaMenu.PAGE_SIZE && start + i < entries.size(); i++) {
            NewsLog.Entry entry = entries.get(start + i);
            menu.set(i, icon(entry.forPlayer().isPresent() ? Items.WRITTEN_BOOK : Items.PAPER,
                text(entry.text(), ChatFormatting.WHITE), List.of(dim(ago(now - entry.time()) + " ago")), false), null);
        }
        if (entries.isEmpty()) {
            menu.set(22, icon(Items.PAPER, text("No news yet", ChatFormatting.GRAY), List.of(), false), null);
        }
        menu.back(PdaMenu.Screen.HOME);
    }

    private static String ago(long millis) {
        long minutes = Math.max(0, millis) / 60000;
        if (minutes >= 60 * 24) {
            return (minutes / 60 / 24) + "d";
        }
        return minutes >= 60 ? (minutes / 60) + "h" : minutes + "m";
    }
}
