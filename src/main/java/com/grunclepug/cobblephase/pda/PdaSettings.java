package com.grunclepug.cobblephase.pda;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** A player's PDA settings, saved on the player. Rule order is priority order. */
public record PdaSettings(int radius, boolean muted, List<PdaRule> rules) {
    public static final int MAX_RULES = 27;
    public static final int[] RADII = {32, 64, 96, 128};
    public static final PdaSettings DEFAULT = new PdaSettings(64, false, List.of());

    public static final Codec<PdaSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("radius", DEFAULT.radius).forGetter(PdaSettings::radius),
        Codec.BOOL.optionalFieldOf("muted", false).forGetter(PdaSettings::muted),
        PdaRule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(PdaSettings::rules)
    ).apply(i, PdaSettings::new));

    public PdaSettings {
        rules = List.copyOf(rules);
    }

    public PdaSettings nextRadius() {
        int next = RADII[0];
        for (int i = 0; i < RADII.length; i++) {
            if (RADII[i] == radius) {
                next = RADII[(i + 1) % RADII.length];
            }
        }
        return new PdaSettings(next, muted, rules);
    }

    public PdaSettings withMuted(boolean value) {
        return new PdaSettings(radius, value, rules);
    }

    public PdaSettings addRule(PdaRule rule) {
        List<PdaRule> copy = new ArrayList<>(rules);
        copy.add(rule);
        return new PdaSettings(radius, muted, copy);
    }

    public PdaSettings updateRule(int index, UnaryOperator<PdaRule> change) {
        List<PdaRule> copy = new ArrayList<>(rules);
        copy.set(index, change.apply(copy.get(index)));
        return new PdaSettings(radius, muted, copy);
    }

    public PdaSettings removeRule(int index) {
        List<PdaRule> copy = new ArrayList<>(rules);
        copy.remove(index);
        return new PdaSettings(radius, muted, copy);
    }

    /** Swaps the rule at {@code index} with its neighbour {@code index + offset}, if that exists. */
    public PdaSettings moveRule(int index, int offset) {
        int other = index + offset;
        if (other < 0 || other >= rules.size()) {
            return this;
        }
        List<PdaRule> copy = new ArrayList<>(rules);
        PdaRule rule = copy.get(index);
        copy.set(index, copy.get(other));
        copy.set(other, rule);
        return new PdaSettings(radius, muted, copy);
    }
}
