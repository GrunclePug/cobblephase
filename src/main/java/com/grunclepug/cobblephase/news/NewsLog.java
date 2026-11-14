package com.grunclepug.cobblephase.news;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A short shared log of server news (firsts, shinies, wishes, outbreaks), plus private notes for one player (someone
 * caught your wish). On login, players get a summary of what happened since they left; the PDA lists the rest.
 */
public final class NewsLog extends SavedData {
    private static final int MAX_ENTRIES = 100;
    private static final int LOGIN_LINES = 6;
    private static final String NAME = "cobblephase_news";
    private static final Factory<NewsLog> FACTORY = new Factory<>(NewsLog::new, NewsLog::load, null);

    /** When the player last left, in epoch milliseconds. */
    public static final AttachmentType<Long> LAST_SEEN = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath("cobblephase", "last_seen"), builder -> builder.persistent(Codec.LONG).copyOnDeath());

    /** {@code forPlayer} empty means everyone sees it. */
    public record Entry(long time, String text, Optional<UUID> forPlayer) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("time").forGetter(Entry::time),
            Codec.STRING.fieldOf("text").forGetter(Entry::text),
            UUIDUtil.CODEC.optionalFieldOf("for").forGetter(Entry::forPlayer)
        ).apply(i, Entry::new));

        public boolean visibleTo(UUID player) {
            return forPlayer.isEmpty() || forPlayer.get().equals(player);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static NewsLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static NewsLog load(CompoundTag tag, HolderLookup.Provider registries) {
        NewsLog log = new NewsLog();
        Entry.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("entries")).result().ifPresent(log.entries::addAll);
        return log;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("entries", Entry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, entries).getOrThrow());
        return tag;
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> summarize(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            handler.player.setAttached(LAST_SEEN, System.currentTimeMillis()));
    }

    public static void add(MinecraftServer server, String text) {
        get(server).append(new Entry(System.currentTimeMillis(), text, Optional.empty()));
    }

    public static void addFor(MinecraftServer server, UUID player, String text) {
        get(server).append(new Entry(System.currentTimeMillis(), text, Optional.of(player)));
    }

    private void append(Entry entry) {
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
        setDirty();
    }

    /** Entries the player can see, newest first. */
    public List<Entry> visibleTo(UUID player) {
        List<Entry> list = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).visibleTo(player)) {
                list.add(entries.get(i));
            }
        }
        return list;
    }

    private static void summarize(ServerPlayer player) {
        Long lastSeen = player.getAttached(LAST_SEEN);
        if (lastSeen == null) {
            return;
        }
        List<Entry> missed = get(player.server).visibleTo(player.getUUID()).stream()
            .filter(entry -> entry.time() > lastSeen).toList();
        if (missed.isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.literal("[News] ").withStyle(ChatFormatting.DARK_AQUA)
            .append(Component.literal("While you were away (" + missed.size() + "):").withStyle(ChatFormatting.WHITE)));
        for (int i = 0; i < Math.min(LOGIN_LINES, missed.size()); i++) {
            player.sendSystemMessage(Component.literal("  • " + missed.get(i).text()).withStyle(ChatFormatting.GRAY));
        }
        if (missed.size() > LOGIN_LINES) {
            player.sendSystemMessage(Component.literal("  +" + (missed.size() - LOGIN_LINES)
                + " more in the PDA under News.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
