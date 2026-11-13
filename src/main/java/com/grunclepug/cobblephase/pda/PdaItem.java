package com.grunclepug.cobblephase.pda;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * The Trainer PDA is a vanilla compass marked with custom data, so clients need nothing installed. Its needle is
 * an untracked lodestone target, which vanilla never touches on its own.
 */
public final class PdaItem {
    private static final String TAG_KEY = "cobblephase";
    private static final String TAG_VALUE = "pda";
    private static final LodestoneTracker NO_TARGET = new LodestoneTracker(Optional.empty(), false);

    private PdaItem() {
    }

    public static ItemStack create() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KEY, TAG_VALUE);
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Trainer PDA")
            .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            lore("Pings you when Pokémon you want are nearby."),
            lore("Right-click: open. Sneak + right-click: next result."))));
        stack.set(DataComponents.LODESTONE_TRACKER, NO_TARGET);
        return stack;
    }

    public static boolean is(ItemStack stack) {
        if (!stack.is(Items.COMPASS)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && TAG_VALUE.equals(data.copyTag().getString(TAG_KEY));
    }

    public static boolean held(Player player) {
        return is(player.getMainHandItem()) || is(player.getOffhandItem());
    }

    public static boolean carried(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (is(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Points every carried PDA at {@code target} (or makes it spin), writing only stacks that differ. */
    public static void pointAll(Player player, GlobalPos target) {
        LodestoneTracker wanted = target == null ? NO_TARGET : new LodestoneTracker(Optional.of(target), false);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (is(stack) && !wanted.equals(stack.get(DataComponents.LODESTONE_TRACKER))) {
                stack.set(DataComponents.LODESTONE_TRACKER, wanted);
            }
        }
    }

    private static Component lore(String text) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY));
    }
}
