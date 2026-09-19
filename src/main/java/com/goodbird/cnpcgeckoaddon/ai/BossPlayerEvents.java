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
            // A rift is the one hold that outlives a logout: its record is saved with the player.
            BossRiftManager.handleLogout(player);
        }
    }

    /**
     * A player coming in who was in a reality rift: back into it if it is still open, back to
     * where they were taken from if it closed while they were away.
     */
    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        EventGuard.handle("player.player_login", event, BossPlayerEvents::handlePlayerLogin);
    }

    private static void handlePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossRiftManager.handleLogin(player);
        }
    }

    /** A player respawning with a rift record and no rift to go with it. */
    @SubscribeEvent
    public static void onPlayerRespawn(final PlayerEvent.PlayerRespawnEvent event) {
        EventGuard.handle("player.player_respawn", event, BossPlayerEvents::handlePlayerRespawn);
    }

    private static void handlePlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossRiftManager.handleRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        EventGuard.handle("player.player_changed_dimension", event, BossPlayerEvents::handlePlayerChangedDimension);
    }

    private static void handlePlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseFromEverything(player);
            // Leaving a rift by any other road than its own lets go of it for good.
            BossRiftManager.handleDimensionChange(player, event.getFrom(), event.getTo());
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
