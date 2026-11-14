package com.grunclepug.cobblephase.tracker;

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition;
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition;
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition;
import com.cobblemon.mod.common.api.spawning.MoonPhaseRange;
import com.cobblemon.mod.common.api.spawning.condition.AreaTypeSpawningCondition;
import com.cobblemon.mod.common.api.spawning.condition.GroundedTypeSpawningCondition;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.multiplier.WeightMultiplier;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Human-readable lines for a spawn entry's conditions. */
public final class RouteText {
    private RouteText() {
    }

    /** Lore lines describing where and when the route spawns. */
    public static List<Component> describe(SpawnIndex.Route route) {
        SpawnDetail detail = route.detail();
        List<Component> lines = new ArrayList<>();
        lines.add(line("Rarity: ", capitalize(detail.getBucket()) + " · weight " + trim(detail.getWeight())
            + (route.herd() ? " · in herds" : "")));
        lines.add(line("Level: ", route.levelText() + " · " + where(detail.getSpawnablePositionType().getName())));

        List<SpawningCondition<?>> conditions = new ArrayList<>(SpawnIndex.orEmpty(detail.getConditions()));
        if (conditions.isEmpty()) {
            lines.add(line("Biomes: ", "any"));
        }
        for (int i = 0; i < conditions.size(); i++) {
            String prefix = conditions.size() > 1 ? (i == 0 ? "Either " : "Or ") : "";
            lines.addAll(condition(prefix, conditions.get(i)));
        }
        for (SpawningCondition<?> anti : SpawnIndex.orEmpty(detail.getAnticonditions())) {
            List<String> not = new ArrayList<>();
            if (!SpawnIndex.orEmpty(anti.getBiomes()).isEmpty()) {
                not.add("in " + biomes(anti.getBiomes()));
            }
            if (!SpawnIndex.orEmpty(anti.getStructures()).isEmpty()) {
                not.add("in " + structures(anti.getStructures()));
            }
            if (anti instanceof AreaTypeSpawningCondition<?> area && !SpawnIndex.orEmpty(area.getNeededNearbyBlocks()).isEmpty()) {
                not.add("near " + blocks(area.getNeededNearbyBlocks()));
            }
            if (anti instanceof GroundedTypeSpawningCondition<?> grounded && !SpawnIndex.orEmpty(grounded.getNeededBaseBlocks()).isEmpty()) {
                not.add("on " + blocks(grounded.getNeededBaseBlocks()));
            }
            if (!not.isEmpty()) {
                // Separate entries are separate rules: any one of them rules a spawn out.
                lines.add(line("Not: ", String.join(", ", not)));
            }
        }
        for (WeightMultiplier multiplier : SpawnIndex.orEmpty(detail.getWeightMultipliers())) {
            lines.add(Component.literal("×" + trim(multiplier.getMultiplier()) + " more likely " + boostText(multiplier))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return lines;
    }

    private static List<Component> condition(String prefix, SpawningCondition<?> condition) {
        List<Component> lines = new ArrayList<>();
        lines.add(line(prefix + (prefix.isEmpty() ? "Biomes: " : "biomes: "),
            SpawnIndex.orEmpty(condition.getBiomes()).isEmpty() ? "any" : biomes(condition.getBiomes())));
        if (!SpawnIndex.orEmpty(condition.getStructures()).isEmpty()) {
            lines.add(line("  In structure: ", structures(condition.getStructures())));
        }
        if (condition instanceof GroundedTypeSpawningCondition<?> grounded && !SpawnIndex.orEmpty(grounded.getNeededBaseBlocks()).isEmpty()) {
            lines.add(line("  Standing on: ", blocks(grounded.getNeededBaseBlocks())));
        }
        if (condition instanceof AreaTypeSpawningCondition<?> area && !SpawnIndex.orEmpty(area.getNeededNearbyBlocks()).isEmpty()) {
            lines.add(line("  Near: ", blocks(area.getNeededNearbyBlocks())));
        }
        List<String> extra = new ArrayList<>();
        if (condition.getTimeRange() != null) {
            extra.add(timeName(condition));
        }
        if (Boolean.TRUE.equals(condition.isThundering())) {
            extra.add("thunderstorms");
        } else if (Boolean.TRUE.equals(condition.isRaining())) {
            extra.add("rain");
        } else if (Boolean.FALSE.equals(condition.isRaining())) {
            extra.add("no rain");
        }
        if (Boolean.TRUE.equals(condition.getCanSeeSky())) {
            extra.add("open sky");
        } else if (Boolean.FALSE.equals(condition.getCanSeeSky())) {
            extra.add("under cover");
        }
        if (condition.getMinY() != null || condition.getMaxY() != null) {
            extra.add("y " + (condition.getMinY() == null ? "…" : trim(condition.getMinY())) + " to "
                + (condition.getMaxY() == null ? "…" : trim(condition.getMaxY())));
        }
        if (condition.getMinLight() != null || condition.getMaxLight() != null) {
            extra.add("light " + (condition.getMinLight() == null ? 0 : condition.getMinLight()) + "–"
                + (condition.getMaxLight() == null ? 15 : condition.getMaxLight()));
        }
        if (condition.getMoonPhase() != null) {
            extra.add(moonName(condition.getMoonPhase()));
        }
        if (!SpawnIndex.orEmpty(condition.getDimensions()).isEmpty()) {
            List<String> dims = new ArrayList<>();
            for (ResourceLocation dim : condition.getDimensions()) {
                dims.add(dim.getPath().replace('_', ' '));
            }
            extra.add("in " + String.join("/", dims));
        }
        if (!extra.isEmpty()) {
            lines.add(line("  Needs: ", String.join(", ", extra)));
        }
        return lines;
    }

    private static String boostText(WeightMultiplier multiplier) {
        List<String> parts = new ArrayList<>();
        for (SpawningCondition<?> condition : SpawnIndex.orEmpty(multiplier.getConditions())) {
            if (Boolean.TRUE.equals(condition.isThundering())) {
                parts.add("during thunderstorms");
            } else if (Boolean.TRUE.equals(condition.isRaining())) {
                parts.add("in rain");
            }
            if (condition.getTimeRange() != null) {
                parts.add("at " + timeName(condition));
            }
            if (!SpawnIndex.orEmpty(condition.getBiomes()).isEmpty()) {
                parts.add("in " + biomes(condition.getBiomes()));
            }
        }
        return parts.isEmpty() ? "under some conditions" : String.join(", ", parts);
    }

    /** "Mountain, Forest biomes, Plains": tags as categories, plain ids as biome names. */
    public static String biomes(java.util.Collection<RegistryLikeCondition<Biome>> biomes) {
        List<String> names = new ArrayList<>();
        for (RegistryLikeCondition<Biome> condition : biomes) {
            if (condition instanceof RegistryLikeTagCondition<Biome> tag) {
                names.add(tagName(tag.getTag()));
            } else if (condition instanceof RegistryLikeIdentifierCondition<Biome> id) {
                names.add(idName(id.getIdentifier()));
            } else {
                names.add("custom");
            }
        }
        return String.join(", ", names);
    }

    private static String blocks(java.util.Collection<? extends RegistryLikeCondition<?>> blocks) {
        List<String> names = new ArrayList<>();
        for (RegistryLikeCondition<?> condition : blocks) {
            if (condition instanceof RegistryLikeTagCondition<?> tag) {
                names.add(tagName(tag.getTag()));
            } else if (condition instanceof RegistryLikeIdentifierCondition<?> id) {
                names.add(idName(id.getIdentifier()));
            }
        }
        return names.isEmpty() ? "certain blocks" : String.join(", ", names);
    }

    private static String structures(List<Either<ResourceLocation, TagKey<Structure>>> structures) {
        List<String> names = new ArrayList<>();
        for (Either<ResourceLocation, TagKey<Structure>> structure : structures) {
            names.add(structure.map(RouteText::idName, RouteText::tagName));
        }
        return String.join(", ", names);
    }

    /** "#cobblemon:is_mountain" → "Mountain biomes". */
    static String tagName(TagKey<?> tag) {
        String path = tag.location().getPath();
        path = path.substring(path.lastIndexOf('/') + 1);
        if (path.startsWith("is_")) {
            path = path.substring(3);
        }
        return capitalize(path.replace('_', ' ')) + " (group)";
    }

    /** "minecraft:windswept_hills" → "Windswept hills". */
    public static String idName(ResourceLocation id) {
        String path = id.getPath();
        return capitalize(path.substring(path.lastIndexOf('/') + 1).replace('_', ' '));
    }

    private static String timeName(SpawningCondition<?> condition) {
        String name = condition.getTimeRange().displayName() == null ? "" : condition.getTimeRange().displayName().getString();
        return name.isBlank() ? "certain times" : name.toLowerCase(Locale.ROOT);
    }

    private static String moonName(MoonPhaseRange range) {
        for (Map.Entry<String, MoonPhaseRange> entry : MoonPhaseRange.Companion.getMoonPhaseRanges().entrySet()) {
            if (entry.getValue().getRanges().equals(range.getRanges())) {
                return entry.getKey().replace('_', ' ');
            }
        }
        return "some moon phases";
    }

    private static String where(String positionType) {
        return switch (positionType) {
            case "grounded" -> "on land";
            case "submerged" -> "underwater";
            case "surface" -> "on water";
            case "seafloor" -> "on the sea floor";
            case "lavafloor", "lava_surface" -> "in lava";
            case "fishing" -> "fishing";
            default -> positionType.replace('_', ' ');
        };
    }

    private static MutableComponent line(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static String trim(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : Float.toString(value);
    }
}
