package com.grunclepug.cobblephase.pda;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** A player's PDA settings, saved on the player. Rule order is priority order. */
public record PdaSettings(int radius, boolean muted, Repeat repeat, List<PdaRule> rules) {
    public static final int MAX_RULES = 27;
    public static final int[] RADII = {32, 64, 96, 128};
    public static final PdaSettings DEFAULT = new PdaSettings(64, false, Repeat.ALWAYS, List.of());

    /** When the selected result's sound repeats, faster the closer it is. */
    public enum Repeat {
        ALWAYS("always", "always"),
        HELD("held", "while held"),
        OFF("off", "off");

        static final Codec<Repeat> CODEC = Codec.STRING.xmap(Repeat::byId, r -> r.id);

        public final String id;
        public final String displayName;

        Repeat(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        static Repeat byId(String id) {
            for (Repeat repeat : values()) {
                if (repeat.id.equals(id)) {
                    return repeat;
                }
            }
            return ALWAYS;
        }

        Repeat next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final Codec<PdaSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("radius", DEFAULT.radius).forGetter(PdaSettings::radius),
        Codec.BOOL.optionalFieldOf("muted", false).forGetter(PdaSettings::muted),
        Repeat.CODEC.optionalFieldOf("repeat", Repeat.ALWAYS).forGetter(PdaSettings::repeat),
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
        return new PdaSettings(next, muted, repeat, rules);
    }

    public PdaSettings withMuted(boolean value) {
        return new PdaSettings(radius, value, repeat, rules);
    }

    public PdaSettings nextRepeat() {
        return new PdaSettings(radius, muted, repeat.next(), rules);
    }

    public PdaSettings addRule(PdaRule rule) {
        List<PdaRule> copy = new ArrayList<>(rules);
        copy.add(rule);
        return new PdaSettings(radius, muted, repeat, copy);
    }

    public PdaSettings updateRule(int index, UnaryOperator<PdaRule> change) {
        List<PdaRule> copy = new ArrayList<>(rules);
        copy.set(index, change.apply(copy.get(index)));
        return new PdaSettings(radius, muted, repeat, copy);
    }

    public PdaSettings removeRule(int index) {
        List<PdaRule> copy = new ArrayList<>(rules);
        copy.remove(index);
        return new PdaSettings(radius, muted, repeat, copy);
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
        return new PdaSettings(radius, muted, repeat, copy);
    }

    /** The sound for a rule index from an earlier snapshot; the rule may have been deleted since. */
    public PdaSound soundFor(int rule) {
        return rule < rules.size() ? PdaSound.byId(rules.get(rule).sound()) : PdaSound.DEFAULT;
    }
}
