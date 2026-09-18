package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A player leaving a fight, and one arriving in the middle of one.
 *
 * <p>Logging out and stepping through a portal are the same thing to everything a boss can
 * have hold of: whatever was gripping the player has nothing to grip any more, so it lets
 * go rather than being left pointing at somebody who is no longer there. Coming into range
 * is the mirror of it - a late arrival is handed the beams already strung across the arena,
 * because they were sent once, to whoever could see them at the time.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class BossPlayerEvents {

    private BossPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        EventGuard.handle("player.player_logout", event, BossPlayerEvents::handlePlayerLogout);
    }

    private static void handlePlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseFromEverything(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        EventGuard.handle("player.player_changed_dimension", event, BossPlayerEvents::handlePlayerChangedDimension);
    }

    private static void handlePlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseFromEverything(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerStartsTracking(final PlayerEvent.StartTracking event) {
        EventGuard.handle("player.player_starts_tracking", event, BossPlayerEvents::handlePlayerStartsTracking);
    }

    private static void handlePlayerStartsTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportPathController.syncTotemLinksForTracking(player, event.getTarget());
            BossCaptureManager.syncLinkForTracking(player, event.getTarget());
            BossTetherManager.syncLinkForTracking(player, event.getTarget());
        }
    }

    private static void releaseFromEverything(ServerPlayer player) {
        BossCaptureManager.releaseVictim(player);
        BossTetherManager.releaseVictim(player);
        BossCocoonManager.releaseVictim(player);
        BossHurricaneScheduler.releaseVictim(player);
        BossFireTracker.forget(player);
        TeleportPathController.removePlayerFromEncounters(player);
    }
}
