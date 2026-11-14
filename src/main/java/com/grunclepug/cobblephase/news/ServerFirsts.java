package com.grunclepug.cobblephase.news;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which species anyone on the server has caught, and who caught each first. Species caught before this was
 * installed are recorded with no catcher (from players' Pokédexes), so they never count as a first.
 */
public final class ServerFirsts extends SavedData {
    private static final String NAME = "cobblephase_firsts";
    private static final Factory<ServerFirsts> FACTORY = new Factory<>(ServerFirsts::new, ServerFirsts::load, null);

    /** Species id → first catcher's name, or "" when it was caught before tracking began. */
    private final Map<String, String> species = new HashMap<>();
    private boolean seeded;

    public static ServerFirsts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static ServerFirsts load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerFirsts firsts = new ServerFirsts();
        CompoundTag list = tag.getCompound("species");
        for (String key : list.getAllKeys()) {
            firsts.species.put(key, list.getString(key));
        }
        firsts.seeded = tag.getBoolean("seeded");
        return firsts;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag list = new CompoundTag();
        species.forEach(list::putString);
        tag.put("species", list);
        tag.putBoolean("seeded", seeded);
        return tag;
    }

    /** Species id → first catcher's name ("" when caught before tracking began), for the ledger. */
    public Map<String, String> entries() {
        return Map.copyOf(species);
    }

    boolean seeded() {
        return seeded;
    }

    void markSeeded() {
        seeded = true;
        setDirty();
    }

    /** Records a species as already caught, without a first catcher. */
    void know(ResourceLocation id) {
        if (species.putIfAbsent(id.toString(), "") == null) {
            setDirty();
        }
    }

    /** Records a catch; true when nobody had caught this species before. */
    boolean claim(ResourceLocation id, String catcher) {
        if (species.putIfAbsent(id.toString(), catcher) != null) {
            return false;
        }
        setDirty();
        return true;
    }
}
