package com.grunclepug.cobblephase.wish;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The server's shared wishboard: Pokémon players are looking for. Wishes expire after two weeks. */
public final class Wishboard extends SavedData {
    public static final int MAX_PER_PLAYER = 9;
    public static final int MAX_NOTE = 60;
    public static final long EXPIRY_MILLIS = 14L * 24 * 60 * 60 * 1000;
    private static final String NAME = "cobblephase_wishboard";
    private static final Factory<Wishboard> FACTORY = new Factory<>(Wishboard::new, Wishboard::load, null);

    public record Wish(UUID id, UUID poster, String posterName, ResourceLocation species, boolean shiny, String note,
                       long created) {
        static final Codec<Wish> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Wish::id),
            UUIDUtil.CODEC.fieldOf("poster").forGetter(Wish::poster),
            Codec.STRING.fieldOf("poster_name").forGetter(Wish::posterName),
            ResourceLocation.CODEC.fieldOf("species").forGetter(Wish::species),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(Wish::shiny),
            Codec.STRING.optionalFieldOf("note", "").forGetter(Wish::note),
            Codec.LONG.fieldOf("created").forGetter(Wish::created)
        ).apply(i, Wish::new));

        public long expires() {
            return created + EXPIRY_MILLIS;
        }
    }

    private final List<Wish> wishes = new ArrayList<>();

    public static Wishboard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static Wishboard load(CompoundTag tag, HolderLookup.Provider registries) {
        Wishboard board = new Wishboard();
        Wish.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("wishes")).result().ifPresent(board.wishes::addAll);
        return board;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Tag list = Wish.CODEC.listOf().encodeStart(NbtOps.INSTANCE, wishes).getOrThrow();
        tag.put("wishes", list);
        return tag;
    }

    /** Current wishes, oldest first, after dropping expired ones. */
    public List<Wish> wishes() {
        long now = System.currentTimeMillis();
        if (wishes.removeIf(wish -> wish.expires() <= now)) {
            setDirty();
        }
        List<Wish> list = new ArrayList<>(wishes);
        list.sort(Comparator.comparingLong(Wish::created));
        return list;
    }

    public long countBy(UUID poster) {
        return wishes().stream().filter(wish -> wish.poster().equals(poster)).count();
    }

    public void add(Wish wish) {
        wishes.add(wish);
        setDirty();
    }

    /** Removes a wish if {@code by} posted it. */
    public boolean remove(UUID id, UUID by) {
        boolean removed = wishes.removeIf(wish -> wish.id().equals(id) && wish.poster().equals(by));
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
