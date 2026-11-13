package com.grunclepug.cobblephase.pda;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Wires the Trainer PDA into the game: saved data, right-click handling, the tracker tick and chat search. */
public final class Pda {
    /** A player's rules and settings; kept through death. */
    public static final AttachmentType<PdaSettings> SETTINGS = AttachmentRegistry.create(id("settings"),
        builder -> builder.persistent(PdaSettings.CODEC).copyOnDeath());

    /** The spawn bucket a Pokémon came from, recorded at spawn time; saved with the entity. */
    public static final AttachmentType<String> SPAWN_BUCKET = AttachmentRegistry.create(id("spawn_bucket"),
        builder -> builder.persistent(Codec.STRING));

    private Pda() {
    }

    public static void init() {
        // Load the spawn action now so a broken rarity mixin fails at startup rather than on the first spawn.
        try {
            Class.forName("com.cobblemon.mod.common.api.spawning.detail.SingleEntitySpawnAction", false,
                Pda.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cobblemon's spawn action class is missing", e);
        }
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!PdaItem.is(stack) || player.isSpectator() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            use(serverPlayer);
            return InteractionResultHolder.success(stack);
        });
        // Using a compass on a lodestone would rebind it, and the client won't follow up with an item use.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!PdaItem.is(player.getItemInHand(hand)) || player.isSpectator()
                || !(player instanceof ServerPlayer serverPlayer)
                || !level.getBlockState(hit.getBlockPos()).is(Blocks.LODESTONE)) {
                return InteractionResult.PASS;
            }
            use(serverPlayer);
            // Undo the client's predicted rebind.
            serverPlayer.inventoryMenu.sendAllDataToRemote();
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PdaTracker.tick(server);
            PdaSearch.tick(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PdaTracker.forget(handler.player);
            PdaSearch.forget(handler.player);
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(PdaSearch::onChat);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                PdaSearch.forget(player);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> PdaCatalog.invalidate());
    }

    public static PdaSettings settings(ServerPlayer player) {
        return player.getAttachedOrElse(SETTINGS, PdaSettings.DEFAULT);
    }

    public static void save(ServerPlayer player, PdaSettings settings) {
        player.setAttached(SETTINGS, settings);
    }

    private static void use(ServerPlayer player) {
        // One click can reach both callbacks, or both hands; act on it once.
        if (!PdaTracker.claimUse(player)) {
            return;
        }
        // Reopening the PDA abandons a pending chat search, so it can't land on a rule that has since moved.
        PdaSearch.forget(player);
        if (player.isShiftKeyDown()) {
            PdaTracker.cycle(player);
        } else {
            PdaMenu.open(player, PdaMenu.Screen.HOME);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblephase", path);
    }
}
