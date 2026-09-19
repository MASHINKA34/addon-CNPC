package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import com.goodbird.cnpcgeckoaddon.utils.GuardSelfTest;
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
 *
 * <p>Each is also ticked behind a guard of its own. They all run inside the level tick, where
 * an exception is the server's crash report; behind the guard it is a log line, and the
 * scheduler that threw drops what it had in this level - an entry that failed this tick would
 * be the first one run on the next, and a wave that vanishes is better than a queue failing
 * twenty times a second. The others never notice. The calls are method references rather than
 * lambdas so that none of this allocates on a tick, and {@code SchedulerTicksGuardedTest}
 * keeps a scheduler from being added here without one.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class BossSchedulerEvents {

    private BossSchedulerEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        EventGuard.handle("scheduler.level_tick", event, BossSchedulerEvents::tickSchedulers);
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        EventGuard.handle("scheduler.level_unload", event, BossSchedulerEvents::clearSchedulers);
    }

    private static void tickSchedulers(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (BossExplosionScheduler.hasPending()) {
            CrashGuard.tick("scheduler.explosion", level, BossExplosionScheduler::tick, BossExplosionScheduler::clear);
        }
        // Asked inside its guard: whether a chest is owing is read out of the level's saved data.
        CrashGuard.tick("scheduler.chest", level, BossSchedulerEvents::tickChests, BossChestScheduler::clear);
        if (BossAreaVfxScheduler.hasPending()) {
            CrashGuard.tick("scheduler.area_vfx", level, BossAreaVfxScheduler::tick, BossAreaVfxScheduler::clear);
        }
        if (BossGeyserScheduler.hasPending() || selfTestArmed(level)) {
            CrashGuard.tick("scheduler.geyser", level, BossSchedulerEvents::tickGeysers, BossGeyserScheduler::clear);
        }
        // Right after the geyser, whose throw it shares: a ring's launch and a column's go up
        // the same tick they would have alone.
        if (BossSeismicScheduler.hasPending()) {
            CrashGuard.tick("scheduler.seismic", level, BossSeismicScheduler::tick, BossSeismicScheduler::clear);
        }
        if (BossMarkScheduler.hasPending()) {
            CrashGuard.tick("scheduler.mark", level, BossMarkScheduler::tick, BossMarkScheduler::clear);
        }
        if (BossBoulderRainScheduler.hasPending()) {
            CrashGuard.tick("scheduler.boulder_rain", level, BossBoulderRainScheduler::tick,
                    BossBoulderRainScheduler::clear);
        }
        if (BossCloneRespawnGuard.hasPending()) {
            CrashGuard.tick("scheduler.clone_respawn_guard", level, BossCloneRespawnGuard::tick,
                    BossCloneRespawnGuard::clear);
        }
        CrashGuard.tick("scheduler.minion_removal", level, BossMinionUtil::tick, BossMinionUtil::clearPending);
        // Before the capture, so a held victim the field also reaches is pinned back last.
        if (BossGravityScheduler.hasPending()) {
            CrashGuard.tick("scheduler.gravity", level, BossGravityScheduler::tick, BossGravityScheduler::clear);
        }
        // After the field and before the capture for the same reason: a storm never takes a
        // captured victim, and the capture's pin is the one that has to land last.
        if (BossHurricaneScheduler.hasPending()) {
            CrashGuard.tick("scheduler.hurricane", level, BossHurricaneScheduler::tick, BossHurricaneScheduler::clear);
        }
        if (BossBeamScheduler.hasPending()) {
            CrashGuard.tick("scheduler.beam", level, BossBeamScheduler::tick, BossBeamScheduler::clear);
        }
        if (BossPlatformScheduler.hasPending()) {
            CrashGuard.tick("scheduler.platform", level, BossPlatformScheduler::tick, BossPlatformScheduler::clear);
        }
        if (BossCaptureManager.hasPending()) {
            CrashGuard.tick("scheduler.capture", level, BossCaptureManager::tick, BossCaptureManager::clearLevel);
        }
        if (BossTetherManager.hasPending()) {
            CrashGuard.tick("scheduler.tether", level, BossTetherManager::tick, BossTetherManager::clearLevel);
        }
        if (BossCocoonManager.hasPending()) {
            CrashGuard.tick("scheduler.cocoon", level, BossCocoonManager::tick, BossCocoonManager::clearLevel);
        }
        if (BossFireTracker.hasPending()) {
            CrashGuard.tick("scheduler.fire", level, BossFireTracker::tick, BossFireTracker::clearLevel);
        }
        // Only the rift dimension's own tick runs the rifts; a failure there brings everyone home.
        if (BossRiftManager.hasPending()) {
            CrashGuard.tick("scheduler.rift", level, BossRiftManager::tick, BossRiftManager::clearLevel);
        }
        // Last of all, and deliberately so: every warning drawn this tick - by the schedulers
        // above and by the bosses that ticked before them - goes out as one frame per boss,
        // so the shapes of a tick never flicker against each other.
        if (BossTelegraphFrames.hasPending()) {
            CrashGuard.tick("scheduler.telegraph_frames", level, BossTelegraphFrames::tick,
                    BossTelegraphFrames::clear);
        }
    }

    private static void tickChests(ServerLevel level) {
        if (BossChestScheduler.hasPending(level)) {
            BossChestScheduler.tick(level);
        }
    }

    /** The geyser's step, with the self test's wire laid in front of it. */
    private static void tickGeysers(ServerLevel level) {
        if (selfTestArmed(level)) {
            GuardSelfTest.trip(selfTestWire(level));
        }
        BossGeyserScheduler.tick(level);
    }

    private static boolean selfTestArmed(ServerLevel level) {
        return GuardSelfTest.anyArmed() && GuardSelfTest.isArmed(selfTestWire(level));
    }

    /**
     * The wire {@code /cnpcgecko selftest scheduler} lays for one level: the drill fails the
     * schedulers of the level the tester stands in, not whichever level happens to tick first.
     */
    public static String selfTestWire(ServerLevel level) {
        return GuardSelfTest.SCHEDULER + "@" + level.dimension().location();
    }

    /** Each on its own, so one that cannot let go of a level does not keep the rest holding on. */
    private static void clearSchedulers(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CrashGuard.run("unload.explosion", level, BossExplosionScheduler::clear);
        CrashGuard.run("unload.chest", level, BossChestScheduler::clear);
        CrashGuard.run("unload.area_vfx", level, BossAreaVfxScheduler::clear);
        CrashGuard.run("unload.geyser", level, BossGeyserScheduler::clear);
        CrashGuard.run("unload.seismic", level, BossSeismicScheduler::clear);
        CrashGuard.run("unload.mark", level, BossMarkScheduler::clear);
        CrashGuard.run("unload.boulder_rain", level, BossBoulderRainScheduler::clear);
        CrashGuard.run("unload.gravity", level, BossGravityScheduler::clear);
        CrashGuard.run("unload.hurricane", level, BossHurricaneScheduler::clear);
        CrashGuard.run("unload.beam", level, BossBeamScheduler::clear);
        CrashGuard.run("unload.platform", level, BossPlatformScheduler::clear);
        CrashGuard.run("unload.clone_respawn_guard", level, BossCloneRespawnGuard::clear);
        CrashGuard.run("unload.minion_removal", level, BossMinionUtil::clearPending);
        CrashGuard.run("unload.capture", level, BossCaptureManager::clearLevel);
        CrashGuard.run("unload.tether", level, BossTetherManager::clearLevel);
        CrashGuard.run("unload.cocoon", level, BossCocoonManager::clearLevel);
        CrashGuard.run("unload.fire", level, BossFireTracker::clearLevel);
        CrashGuard.run("unload.rift", level, BossRiftManager::clearLevel);
        CrashGuard.run("unload.telegraph_frames", level, BossTelegraphFrames::clear);
        CrashGuard.run("unload.owned_entity_index", level, BossSchedulerEvents::invalidateOwnedEntities);
        CrashGuard.run("unload.controllers", level, TeleportPathController::shutdownLevel);
    }

    private static void invalidateOwnedEntities(ServerLevel level) {
        BossOwnedEntityIndex.invalidate();
    }
}
