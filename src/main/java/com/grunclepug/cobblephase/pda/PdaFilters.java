package com.grunclepug.cobblephase.pda;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * A rule's extra filters. Each one that's set must match; within a list, any entry matches. Types and forms are
 * stored lowercase; levels of 0 mean no limit.
 */
public record PdaFilters(
    List<String> types,
    List<String> forms,
    List<ResourceLocation> natures,
    int minLevel,
    int maxLevel,
    int minPerfectIvs,
    boolean hiddenAbility
) {
    public static final PdaFilters NONE = new PdaFilters(List.of(), List.of(), List.of(), 0, 0, 0, false);
    public static final int MAX_LEVEL = 100;
    public static final int LEVEL_STEP = 5;
    public static final int MAX_PERFECT_IVS = 6;

    public static final Codec<PdaFilters> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.listOf().optionalFieldOf("types", List.of()).forGetter(PdaFilters::types),
        Codec.STRING.listOf().optionalFieldOf("forms", List.of()).forGetter(PdaFilters::forms),
        ResourceLocation.CODEC.listOf().optionalFieldOf("natures", List.of()).forGetter(PdaFilters::natures),
        Codec.INT.optionalFieldOf("min_level", 0).forGetter(PdaFilters::minLevel),
        Codec.INT.optionalFieldOf("max_level", 0).forGetter(PdaFilters::maxLevel),
        Codec.INT.optionalFieldOf("min_perfect_ivs", 0).forGetter(PdaFilters::minPerfectIvs),
        Codec.BOOL.optionalFieldOf("hidden_ability", false).forGetter(PdaFilters::hiddenAbility)
    ).apply(i, PdaFilters::new));

    public PdaFilters {
        types = List.copyOf(types);
        forms = List.copyOf(forms);
        natures = List.copyOf(natures);
    }

    public boolean isEmpty() {
        return equals(NONE);
    }

    public int count() {
        int count = 0;
        count += types.isEmpty() ? 0 : 1;
        count += forms.isEmpty() ? 0 : 1;
        count += natures.isEmpty() ? 0 : 1;
        count += minLevel > 0 || maxLevel > 0 ? 1 : 0;
        count += minPerfectIvs > 0 ? 1 : 0;
        count += hiddenAbility ? 1 : 0;
        return count;
    }

    public boolean matches(Pokemon pokemon) {
        if (!types.isEmpty() && !hasType(pokemon)) {
            return false;
        }
        if (!forms.isEmpty() && !forms.contains(pokemon.getForm().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!natures.isEmpty() && !natures.contains(pokemon.getNature().getName())) {
            return false;
        }
        int level = pokemon.getLevel();
        if ((minLevel > 0 && level < minLevel) || (maxLevel > 0 && level > maxLevel)) {
            return false;
        }
        if (minPerfectIvs > 0 && perfectIvs(pokemon) < minPerfectIvs) {
            return false;
        }
        return !hiddenAbility || hasHiddenAbility(pokemon);
    }

    private boolean hasType(Pokemon pokemon) {
        for (ElementalType type : pokemon.getTypes()) {
            if (types.contains(type.getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int perfectIvs(Pokemon pokemon) {
        int perfect = 0;
        for (Map.Entry<? extends Stat, ? extends Integer> iv : pokemon.getIvs()) {
            if (iv.getValue() >= IVs.MAX_VALUE) {
                perfect++;
            }
        }
        return perfect;
    }

    /** Whether its ability is one of its form's hidden abilities. */
    private static boolean hasHiddenAbility(Pokemon pokemon) {
        for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
            if (potential instanceof HiddenAbility
                && potential.getTemplate().getName().equals(pokemon.getAbility().getTemplate().getName())) {
                return true;
            }
        }
        return false;
    }

    public PdaFilters toggleType(String type) {
        return new PdaFilters(toggled(types, type), forms, natures, minLevel, maxLevel, minPerfectIvs, hiddenAbility);
    }

    public PdaFilters toggleForm(String form) {
        return new PdaFilters(types, toggled(forms, form), natures, minLevel, maxLevel, minPerfectIvs, hiddenAbility);
    }

    public PdaFilters toggleNature(ResourceLocation nature) {
        return new PdaFilters(types, forms, toggled(natures, nature), minLevel, maxLevel, minPerfectIvs, hiddenAbility);
    }

    /** Steps the minimum level by {@code delta}, wrapping between "no limit" and the maximum. */
    public PdaFilters stepMinLevel(int delta) {
        return new PdaFilters(types, forms, natures, stepLevel(minLevel, delta), maxLevel, minPerfectIvs, hiddenAbility);
    }

    public PdaFilters stepMaxLevel(int delta) {
        return new PdaFilters(types, forms, natures, minLevel, stepLevel(maxLevel, delta), minPerfectIvs, hiddenAbility);
    }

    public PdaFilters stepPerfectIvs(int delta) {
        int next = Math.floorMod(minPerfectIvs + delta, MAX_PERFECT_IVS + 1);
        return new PdaFilters(types, forms, natures, minLevel, maxLevel, next, hiddenAbility);
    }

    public PdaFilters withHiddenAbility(boolean value) {
        return new PdaFilters(types, forms, natures, minLevel, maxLevel, minPerfectIvs, value);
    }

    /** Steps a level limit, wrapping between 0 (no limit) and {@link #MAX_LEVEL}. */
    private static int stepLevel(int level, int delta) {
        int next = level + delta;
        if (next > MAX_LEVEL) {
            return 0;
        }
        if (next < 0) {
            return MAX_LEVEL;
        }
        return next;
    }

    private static <T> List<T> toggled(List<T> list, T value) {
        List<T> copy = new ArrayList<>(list);
        if (!copy.remove(value)) {
            copy.add(value);
        }
        return copy;
    }
}
