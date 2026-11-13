package com.grunclepug.cobblephase.pda;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

/** Builders for the menu's button items and their text. */
final class MenuItems {
    private MenuItems() {
    }

    static ItemStack icon(ItemLike item, Component name, List<Component> lore, boolean glint) {
        return icon(new ItemStack(item), name, lore, glint);
    }

    static ItemStack icon(ItemStack stack, Component name, List<Component> lore, boolean glint) {
        stack.set(DataComponents.CUSTOM_NAME, Component.empty().withStyle(s -> s.withItalic(false)).append(name));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream()
            .map(line -> (Component) Component.empty().withStyle(s -> s.withItalic(false)).append(line)).toList()));
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    static Component toggleTitle(String name, boolean on) {
        return toggleTitle(Component.literal(name), on);
    }

    static Component toggleTitle(Component name, boolean on) {
        return Component.literal(on ? "✔ " : "").append(name).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    static MutableComponent text(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    static Component hint(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    static Component dim(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }
}
