package com.grunclepug.cobblephase.pda;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One notification rule. A Pokémon matches when it is one of {@code species} or carries one of {@code labels}
 * (either list may be empty), it came from one of {@code buckets} (empty means any rarity), if {@code shinyOnly}
 * it is shiny, and if {@code uncaughtOnly} the player hasn't caught its species yet (per their Pokédex).
 * Its {@code filters} (type, form, nature, level, IVs, hidden ability) must match too. Unknown species, labels or
 * buckets are kept so a datapack change can't wipe them.
 */
public record PdaRule(
    List<ResourceLocation> species,
    List<String> labels,
    List<String> buckets,
    boolean shinyOnly,
    boolean uncaughtOnly,
    PdaFilters filters,
    String sound,
    boolean enabled
) {
    public static final Codec<PdaRule> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceLocation.CODEC.listOf().optionalFieldOf("species", List.of()).forGetter(PdaRule::species),
        Codec.STRING.listOf().optionalFieldOf("labels", List.of()).forGetter(PdaRule::labels),
        Codec.STRING.listOf().optionalFieldOf("buckets", List.of()).forGetter(PdaRule::buckets),
        Codec.BOOL.optionalFieldOf("shiny_only", false).forGetter(PdaRule::shinyOnly),
        Codec.BOOL.optionalFieldOf("uncaught_only", false).forGetter(PdaRule::uncaughtOnly),
        PdaFilters.CODEC.optionalFieldOf("filters", PdaFilters.NONE).forGetter(PdaRule::filters),
        Codec.STRING.optionalFieldOf("sound", PdaSound.DEFAULT.id).forGetter(PdaRule::sound),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(PdaRule::enabled)
    ).apply(i, PdaRule::new));

    public PdaRule {
        species = List.copyOf(species);
        labels = List.copyOf(labels);
        buckets = List.copyOf(buckets);
    }

    public static PdaRule empty() {
        return new PdaRule(List.of(), List.of(), List.of(), false, false, PdaFilters.NONE, PdaSound.DEFAULT.id, true);
    }

    /** A rule with no criteria at all would match every wild Pokémon, so it matches nothing instead. */
    public boolean complete() {
        return !species.isEmpty() || !labels.isEmpty() || !buckets.isEmpty() || shinyOnly || uncaughtOnly || !filters.isEmpty();
    }

    public boolean active() {
        return enabled && complete();
    }

    public PdaRule withSpecies(List<ResourceLocation> value) {
        return new PdaRule(value, labels, buckets, shinyOnly, uncaughtOnly, filters, sound, enabled);
    }

    public PdaRule toggleSpecies(ResourceLocation id) {
        return withSpecies(toggled(species, id));
    }

    public PdaRule toggleLabel(String label) {
        return new PdaRule(species, toggled(labels, label), buckets, shinyOnly, uncaughtOnly, filters, sound, enabled);
    }

    public PdaRule toggleBucket(String bucket) {
        return new PdaRule(species, labels, toggled(buckets, bucket), shinyOnly, uncaughtOnly, filters, sound, enabled);
    }

    public PdaRule withShinyOnly(boolean value) {
        return new PdaRule(species, labels, buckets, value, uncaughtOnly, filters, sound, enabled);
    }

    public PdaRule withUncaughtOnly(boolean value) {
        return new PdaRule(species, labels, buckets, shinyOnly, value, filters, sound, enabled);
    }

    public PdaRule withFilters(PdaFilters value) {
        return new PdaRule(species, labels, buckets, shinyOnly, uncaughtOnly, value, sound, enabled);
    }

    public PdaRule withSound(String value) {
        return new PdaRule(species, labels, buckets, shinyOnly, uncaughtOnly, filters, value, enabled);
    }

    public PdaRule withEnabled(boolean value) {
        return new PdaRule(species, labels, buckets, shinyOnly, uncaughtOnly, filters, sound, value);
    }

    private static <T> List<T> toggled(List<T> list, T value) {
        List<T> copy = new ArrayList<>(list);
        if (!copy.remove(value)) {
            copy.add(value);
        }
        return copy;
    }
}
