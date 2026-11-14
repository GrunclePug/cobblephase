package com.grunclepug.cobblephase.pda;

import com.mojang.serialization.Codec;
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity;
import com.grunclepug.cobblephase.compat.Rct;
import com.grunclepug.cobblephase.habitat.HabitatObserver;
import com.grunclepug.cobblephase.habitat.HabitatReminders;
import com.grunclepug.cobblephase.news.NewsLog;
import com.grunclepug.cobblephase.party.EvolutionWatch;
import com.grunclepug.cobblephase.tracker.Locator;
import com.grunclepug.cobblephase.world.ConditionAlerts;
import com.grunclepug.cobblephase.world.Outbreaks;
import com.grunclepug.cobblephase.tracker.SpawnIndex;
import java.util.Objects;
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
        // Attachment types must exist before any player data loads, or their saved data is dropped.
        Objects.requireNonNull(HabitatReminders.REMINDERS);
        Objects.requireNonNull(EvolutionWatch.PINNED);
        Objects.requireNonNull(NewsLog.LAST_SEEN);
        Objects.requireNonNull(Rct.VISIT_COUNTDOWN);
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
        // With a PDA in hand, a habitat block reports its real phase instead of opening.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (PdaItem.is(player.getItemInHand(hand)) && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(hit.getBlockPos()) instanceof HabitatBlockEntity habitat) {
                if (PdaTracker.claimUse(serverPlayer)) {
                    HabitatObserver.describe(serverPlayer, habitat);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
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
            ChatPrompt.tick(server);
            HabitatReminders.tick(server);
            Locator.tick(server);
            Outbreaks.tick(server);
            ConditionAlerts.tick(server);
            EvolutionWatch.tick(server);
            Rct.tick(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PdaTracker.forget(handler.player);
            ChatPrompt.forget(handler.player);
            PdaMenu.forget(handler.player);
            Locator.cancel(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Locator.shutdown());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> HabitatReminders.onJoin(handler.player));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(ChatPrompt::onChat);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                ChatPrompt.forget(player);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            PdaCatalog.invalidate();
            SpawnIndex.invalidate();
        });
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
        ChatPrompt.forget(player);
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
