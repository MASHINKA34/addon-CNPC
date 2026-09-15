package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Drives everything a boss left in the world that outlives the tick it was started on, and
 * drops all of it when the level goes away.
 *
 * <p>Each scheduler is asked whether it has anything owing before it is ticked, so a level
 * with no boss in it walks a handful of empty checks rather than a dozen tick methods.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class BossSchedulerEvents {

    private BossSchedulerEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (BossExplosionScheduler.hasPending()) {
            BossExplosionScheduler.tick(level);
        }
        if (BossChestScheduler.hasPending(level)) {
            BossChestScheduler.tick(level);
        }
        if (BossAreaVfxScheduler.hasPending()) {
            BossAreaVfxScheduler.tick(level);
        }
        if (BossGeyserScheduler.hasPending()) {
            BossGeyserScheduler.tick(level);
        }
        // Right after the geyser, whose throw it shares: a ring's launch and a column's go up
        // the same tick they would have alone.
        if (BossSeismicScheduler.hasPending()) {
            BossSeismicScheduler.tick(level);
        }
        if (BossMarkScheduler.hasPending()) {
            BossMarkScheduler.tick(level);
        }
        if (BossBoulderRainScheduler.hasPending()) {
            BossBoulderRainScheduler.tick(level);
        }
        if (BossCloneRespawnGuard.hasPending()) {
            BossCloneRespawnGuard.tick(level);
        }
        BossMinionUtil.tick(level);
        // Before the capture, so a held victim the field also reaches is pinned back last.
        if (BossGravityScheduler.hasPending()) {
            BossGravityScheduler.tick(level);
        }
        // After the field and before the capture for the same reason: a storm never takes a
        // captured victim, and the capture's pin is the one that has to land last.
        if (BossHurricaneScheduler.hasPending()) {
            BossHurricaneScheduler.tick(level);
        }
        if (BossBeamScheduler.hasPending()) {
            BossBeamScheduler.tick(level);
        }
        if (BossPlatformScheduler.hasPending()) {
            BossPlatformScheduler.tick(level);
        }
        if (BossCaptureManager.hasPending()) {
            BossCaptureManager.tick(level);
        }
        if (BossTetherManager.hasPending()) {
            BossTetherManager.tick(level);
        }
        if (BossCocoonManager.hasPending()) {
            BossCocoonManager.tick(level);
        }
        if (BossFireTracker.hasPending()) {
            BossFireTracker.tick(level);
        }
        // Last of all, and deliberately so: every warning drawn this tick - by the schedulers
        // above and by the bosses that ticked before them - goes out as one frame per boss,
        // so the shapes of a tick never flicker against each other.
        if (BossTelegraphFrames.hasPending()) {
            BossTelegraphFrames.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BossExplosionScheduler.clear(level);
            BossChestScheduler.clear(level);
            BossAreaVfxScheduler.clear(level);
            BossGeyserScheduler.clear(level);
            BossSeismicScheduler.clear(level);
            BossMarkScheduler.clear(level);
            BossBoulderRainScheduler.clear(level);
            BossGravityScheduler.clear(level);
            BossHurricaneScheduler.clear(level);
            BossBeamScheduler.clear(level);
            BossPlatformScheduler.clear(level);
            BossCloneRespawnGuard.clear(level);
            BossMinionUtil.clearPending(level);
            BossCaptureManager.clearLevel(level);
            BossTetherManager.clearLevel(level);
            BossCocoonManager.clearLevel(level);
            BossFireTracker.clearLevel(level);
            BossTelegraphFrames.clear(level);
            BossOwnedEntityIndex.invalidate();
            TeleportPathController.shutdownLevel(level);
        }
    }
}
