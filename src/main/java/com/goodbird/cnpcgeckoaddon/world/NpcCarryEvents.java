package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** Drives {@link NpcCarryManager}: the clicks that start a carry and the tick that runs it. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class NpcCarryEvents {

    private NpcCarryEvents() {
    }

    /**
     * Takes the npc into the builder's hands instead of opening it.
     *
     * <p>Cancelling is the point: an untouched click runs CustomNPCs' own interaction, which
     * for an op is the whole npc editor.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!isCarryClick(event)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (event.getTarget() instanceof EntityNPCInterface npc && NpcCarryManager.pickUp(player, npc)) {
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

    /** Only the server knows who is in carry mode, and only the main hand may act on it. */
    private static boolean isCarryClick(PlayerInteractEvent event) {
        return !event.getLevel().isClientSide
                && event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player
                && NpcCarryManager.isCarryMode(player);
    }

    private static boolean isPlaceClick(PlayerInteractEvent event) {
        return isCarryClick(event) && NpcCarryManager.isCarrying((ServerPlayer) event.getEntity());
    }
}
