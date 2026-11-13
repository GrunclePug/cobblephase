package com.grunclepug.cobblephase.pda;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What a rule can choose from, read live from Cobblemon so datapack and addon species (AllTheMons, Mega Showdown)
 * and any extra spawn buckets show up without changes here.
 */
public final class PdaCatalog {
    /** Species label groups a rule can match, with their display names. */
    public static final List<Label> LABELS = List.of(
        new Label("legendary", "Legendary", Items.DRAGON_EGG),
        new Label("mythical", "Mythical", Items.ENDER_EYE),
        new Label("ultra_beast", "Ultra Beast", Items.END_CRYSTAL),
        new Label("paradox", "Paradox", Items.CLOCK));

    private static volatile List<Species> species;

    private PdaCatalog() {
    }

    public record Label(String id, String displayName, Item icon) {
    }

    /** Implemented species, by dex number then name. Rebuilt after a datapack reload. */
    public static List<Species> species() {
        List<Species> list = species;
        if (list == null) {
            list = new ArrayList<>(PokemonSpecies.getImplemented());
            list.sort(Comparator.comparingInt(Species::getNationalPokedexNumber).thenComparing(Species::getName));
            species = list = List.copyOf(list);
        }
        return list;
    }

    public static void invalidate() {
        species = null;
    }

    /** Spawn bucket names from Cobblemon's spawner config, most common first. */
    public static List<String> buckets() {
        List<Map.Entry<String, Float>> entries =
            new ArrayList<>(Cobblemon.INSTANCE.getBestSpawner().getConfig().getWorldBuckets().entrySet());
        entries.sort(Map.Entry.<String, Float>comparingByValue().reversed());
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Float> entry : entries) {
            names.add(entry.getKey());
        }
        return names;
    }

    public static Item bucketIcon(String bucket) {
        return switch (bucket) {
            case "common" -> CobblemonItems.POKE_BALL;
            case "uncommon" -> CobblemonItems.GREAT_BALL;
            case "rare" -> CobblemonItems.ULTRA_BALL;
            case "ultra-rare" -> CobblemonItems.MASTER_BALL;
            case "boss" -> CobblemonItems.BEAST_BALL;
            default -> Items.SNOWBALL;
        };
    }

    /** "ultra-rare" → "Ultra-rare". */
    public static String bucketName(String bucket) {
        return bucket.isEmpty() ? bucket : bucket.substring(0, 1).toUpperCase(Locale.ROOT) + bucket.substring(1);
    }

    public static String labelName(String label) {
        for (Label known : LABELS) {
            if (known.id.equals(label)) {
                return known.displayName;
            }
        }
        return label;
    }

    /** "Shiny Zapdos (Galar)": the form is shown when it isn't the base one. */
    public static MutableComponent name(Pokemon pokemon) {
        MutableComponent name = Component.empty();
        if (pokemon.getShiny()) {
            name.append(Component.literal("Shiny ").withStyle(ChatFormatting.GOLD));
        }
        name.append(pokemon.getSpecies().getTranslatedName());
        String form = pokemon.getForm().getName();
        if (!form.equalsIgnoreCase("normal") && !form.equalsIgnoreCase(pokemon.getSpecies().getStandardForm().getName())) {
            name.append(Component.literal(" (" + form + ")"));
        }
        return name;
    }

    /** Lowercase letters and digits only, so "Mr. Mime", "mr_mime" and "mrmime" all compare equal. */
    public static String searchKey(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
