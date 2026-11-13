package com.grunclepug.cobblephase.pda;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The ping sounds a rule can use. Every client already has these: they're vanilla or Cobblemon sounds. */
public enum PdaSound {
    BELL("bell", "Bell", Items.NOTE_BLOCK, "minecraft:block.note_block.bell"),
    CHIME("chime", "Chime", Items.AMETHYST_SHARD, "minecraft:block.note_block.chime"),
    PLING("pling", "Pling", Items.GOLD_NUGGET, "minecraft:block.note_block.pling"),
    ORB("orb", "Orb", Items.EXPERIENCE_BOTTLE, "minecraft:entity.experience_orb.pickup"),
    LEVEL_UP("level_up", "Level Up", Items.EMERALD, "minecraft:entity.player.levelup"),
    VILLAGE_BELL("village_bell", "Village Bell", Items.BELL, "minecraft:block.bell.use"),
    SPARKLE("sparkle", "Shiny Sparkle", Items.GLOWSTONE_DUST, "cobblemon:particle.wild_shiny_chime"),
    POKEDEX("pokedex", "Pokédex Scan", Items.BOOK, "cobblemon:item.pokedex.scan_register_pokemon"),
    FISHING("fishing", "Fishing Bite", Items.FISHING_ROD, "cobblemon:fishing.notification");

    public static final PdaSound DEFAULT = BELL;
    /** How far from the player, toward the Pokémon, the sound plays, so it comes from the right side. */
    private static final double DIRECTION_OFFSET = 4.0;

    public final String id;
    public final String displayName;
    public final Item icon;
    private final ResourceLocation soundId;

    PdaSound(String id, String displayName, Item icon, String soundId) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.soundId = ResourceLocation.parse(soundId);
    }

    public static PdaSound byId(String id) {
        for (PdaSound sound : values()) {
            if (sound.id.equals(id)) {
                return sound;
            }
        }
        return DEFAULT;
    }

    /** Plays to {@code player} only, offset toward {@code toward} when given. */
    public void play(ServerPlayer player, Vec3 toward) {
        Vec3 pos = player.getEyePosition();
        if (toward != null) {
            Vec3 offset = toward.subtract(pos);
            if (offset.lengthSqr() > 1.0E-4) {
                pos = pos.add(offset.normalize().scale(Math.min(DIRECTION_OFFSET, offset.length())));
            }
        }
        player.connection.send(new ClientboundSoundPacket(holder(), SoundSource.MASTER,
            pos.x, pos.y, pos.z, 1.0f, 1.0f, player.getRandom().nextLong()));
    }

    private Holder<SoundEvent> holder() {
        // Cobblemon registers its sounds on the server too; the direct holder is only a fallback.
        return BuiltInRegistries.SOUND_EVENT.getHolder(soundId)
            .<Holder<SoundEvent>>map(h -> h)
            .orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(soundId)));
    }
}
