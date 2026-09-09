package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The path the boss blinks along: which point comes next, whether it is safe to stand on,
 * and how long until the next hop.
 *
 * <p>Owned by {@link TeleportPathController}. The path itself belongs to CustomNPCs - this
 * only ever reads {@code ais.getMovingPath()} - so what is kept here is the walk over it:
 * the point the boss came from, the direction a ping-pong order is currently going in, and
 * the size the path had last time it was looked at, which is what notices a builder editing
 * the path mid fight.</p>
 */
final class BossPathRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    private int lastIndex = -1;
    private int pingPongDirection = 1;
    private int previousSize;

    BossPathRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Forgets the walk, so the next look at the path starts from the closest point again. */
    void clear() {
        lastIndex = -1;
        previousSize = 0;
        pingPongDirection = 1;
    }

    /** Drops only the walk's position, for a boss going back to the spot it started at. */
    void forgetPosition() {
        lastIndex = -1;
        previousSize = 0;
    }

    /**
     * Takes the boss' bearings on the path before anything is allowed to use it.
     *
     * <p>A path of fewer than two points is no path at all, and one whose length changed
     * under the boss is a path the builder has just edited - both disarm the teleport rather
     * than blinking to whatever now happens to sit at the remembered index.</p>
     */
    void prepare(List<int[]> points) {
        if (points.size() < 2) {
            boss.setAbilityScheduleAt(BossAbility.TELEPORT, NOT_SCHEDULED);
            lastIndex = -1;
            previousSize = points.size();
            return;
        }
        if (points.size() != previousSize || lastIndex < 0 || lastIndex >= points.size()) {
            lastIndex = findClosestPoint(points);
            pingPongDirection = 1;
            previousSize = points.size();
            boss.setAbilityScheduleAt(BossAbility.TELEPORT, NOT_SCHEDULED);
        }
    }

    /** Runs one hop: the blink itself, the animation over it and the clock for the next one. */
    void perform(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        List<int[]> points = npc.ais.getMovingPath();
        if (points.size() >= 2 && teleportToNextSafePoint(level, points, data)) {
            playArrivalAnimation(phase, gameTime);
        }
        scheduleNext(gameTime, phase);
    }

    /** When the boss is next allowed to blink, somewhere inside this phase's delay window. */
    void scheduleNext(long gameTime, BossPhaseData phase) {
        int min = boss.rageDown(phase.teleport().getMinDelayTicks());
        int spread = Math.max(0, boss.rageDown(phase.teleport().getMaxDelayTicks()) - min);
        int delay = min + (spread == 0 ? 0 : npc.getRandom().nextInt(spread + 1));
        boss.setAbilityScheduleAt(BossAbility.TELEPORT, gameTime + delay);
    }

    private void playArrivalAnimation(BossPhaseData phase, long gameTime) {
        if (phase.getAppearanceAnimation().isEmpty()) {
            return;
        }
        boss.playAnimation(phase.getAppearanceAnimation());
        boss.holdBusyUntil(gameTime + phase.getAppearanceLockTicks());
    }

    private boolean teleportToNextSafePoint(ServerLevel level, List<int[]> points, TeleportPathData data) {
        for (int attempt = 0; attempt < points.size(); attempt++) {
            int candidate = nextIndex(points.size(), data.getOrder());
            lastIndex = candidate;
            int[] point = points.get(candidate);
            if (point == null || point.length < 3) continue;

            Vec3 destination = BossTeleportUtil.findSafeDestination(level, npc,
                    point[0] + 0.5D, point[1], point[2] + 0.5D);
            if (destination == null) continue;
            // A hop a script vetoed leaves the boss where it is until its next window rather
            // than trying the rest of the path on the same tick.
            return BossTeleportUtil.teleport(level, npc, boss, destination, data.shouldPlaySound(),
                    "path point " + candidate);
        }
        return false;
    }

    private int nextIndex(int size, int order) {
        if (order == TeleportPathData.ORDER_RANDOM) {
            int candidate = npc.getRandom().nextInt(size - 1);
            return candidate >= lastIndex ? candidate + 1 : candidate;
        }
        if (order == TeleportPathData.ORDER_PING_PONG) {
            int candidate = lastIndex + pingPongDirection;
            if (candidate < 0 || candidate >= size) {
                pingPongDirection *= -1;
                candidate = lastIndex + pingPongDirection;
            }
            return candidate;
        }
        return (lastIndex + 1) % size;
    }

    private int findClosestPoint(List<int[]> points) {
        int closest = 0;
        double closestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            if (point == null || point.length < 3) continue;
            double distance = npc.distanceToSqr(point[0] + 0.5D, point[1], point[2] + 0.5D);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = i;
            }
        }
        return closest;
    }
}
