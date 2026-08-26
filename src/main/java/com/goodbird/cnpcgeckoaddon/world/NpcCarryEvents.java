package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** Drives {@link NpcCarryManager}: the clicks that start a carry and the tick that runs it. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class NpcCarryEvents {

    private NpcCarryEvents() {
    }

    /**
     * Takes the npc into the carrier's hands instead of opening it.
     *
     * <p>Cancelling is the point: an untouched click runs CustomNPCs' own interaction, which
     * for an op is the whole npc editor and for a player is the npc's dialog. That dialog is
     * why a carryable npc asks for a sneak click by default - the plain one still belongs to
     * the npc.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!isServerMainHand(event)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (NpcCarryManager.isCarrying(player)) {
            // Hands are full, so this click puts the npc down whatever it landed on: a held
            // npc has no hitbox, and the crosshair reaches straight through it.
            if (NpcCarryManager.placeFromAim(player)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getTarget() instanceof EntityNPCInterface npc)) {
            return;
        }
        boolean builderTool = NpcCarryManager.isCarryMode(player);
        if (!builderTool && !NpcCarryManager.canPlayerCarry(player, npc)) {
            return;
        }
        if (NpcCarryManager.pickUp(player, npc, builderTool)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /**
     * Puts the held npc down where the ring is showing.
     *
     * <p>A held npc usually covers the crosshair itself, in which case the click arrives as
     * an entity interact instead and is placed from there; these two cover the rest, from a
     * short npc that leaves the aim clear to a click that reached a block first.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (isPlaceClick(event) && NpcCarryManager.placeFromAim((ServerPlayer) event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (isPlaceClick(event) && NpcCarryManager.placeFromAim((ServerPlayer) event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NpcCarryManager.tick(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NpcCarryManager.onPlayerGone(player);
        }
    }

    /** The npc stays in the dimension it was picked up in, so the carry ends with it. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NpcCarryManager.release(player);
        }
    }

    /**
     * Damage can knock the npc out of its carrier's hands.
     *
     * <p>Post, not incoming: damage that was cancelled or fully soaked up never reached the
     * carrier, and nothing a carrier did not feel should cost them the npc.</p>
     */
    @SubscribeEvent
    public static void onCarrierDamaged(final LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NpcCarryManager.onCarrierDamaged(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NpcCarryManager.release(player);
        } else if (!event.getEntity().level().isClientSide) {
            NpcCarryManager.releaseNpc(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(final EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) {
            NpcCarryManager.releaseNpc(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NpcCarryManager.clearLevel(level);
        }
    }

    /** Runs before the world is saved, which is the only chance to land a carry cleanly. */
    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        NpcCarryManager.shutdown(event.getServer());
    }

    /** Only the server knows who is carrying what, and only the main hand may act on it. */
    private static boolean isServerMainHand(PlayerInteractEvent event) {
        return !event.getLevel().isClientSide
                && event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer;
    }

    private static boolean isPlaceClick(PlayerInteractEvent event) {
        return isServerMainHand(event) && NpcCarryManager.isCarrying((ServerPlayer) event.getEntity());
    }
}
