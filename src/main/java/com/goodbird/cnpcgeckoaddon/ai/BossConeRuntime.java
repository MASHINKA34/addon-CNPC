package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimPoint;
import com.goodbird.cnpcgeckoaddon.data.BossConeSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The cone strike: a wind-up with its sector marked, then a hit over the whole fan at once -
 * toward whoever it picked, along the boss' gaze, or at the builder's points.
 *
 * <p>Owned by {@link TeleportPathController}. Which way the cone opens is settled as the boss
 * commits, the line strike's rule: the sector drawn on the floor is a promise, and a boss that
 * swung it round after a sidestepping player would turn the warning into a lie. A point is
 * the one aim that is not a direction - it is the block lying on the middle of its cone - so
 * that cone is laid from wherever the boss stands toward it.</p>
 */
final class BossConeRuntime {

    /** The turn left over from the eased wind-up, finished on the tick the cone lands. */
    private static final float SNAP_DEGREES = 360.0F;
    /** Closer than this (squared, flat) somebody stands inside the boss and has no direction of their own. */
    private static final double CENTRE_EPSILON = 1.0E-6D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final BossMinionSpawnRuntime minionSpawns;

    /** Where the points the cast being wound up strikes stand, in order; empty unless it is aimed at points. */
    private final List<Vec3> planned = new ArrayList<>();

    BossConeRuntime(TeleportPathController boss, EntityNPCInterface npc, BossMinionSpawnRuntime minionSpawns) {
        this.boss = boss;
        this.npc = npc;
        this.minionSpawns = minionSpawns;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.CONE, phase) || gameTime < boss.abilityScheduleAt(BossAbility.CONE)) {
            return false;
        }
        BossConeSettings cone = phase.cone();
        planned.clear();
        LivingEntity target = null;
        Vec3 axis;
        if (cone.getAimMode() == BossPhaseData.CONE_AIM_TARGET) {
            target = boss.selectAbilityTarget(level, cone.getTargetMode(), cone.getLength(),
                    candidate -> isValidTarget(candidate, phase));
            axis = target == null ? null : axisToward(target.position());
        } else if (cone.getAimMode() == BossPhaseData.CONE_AIM_POINTS) {
            planned.addAll(pickPoints(cone));
            axis = planned.isEmpty() ? null : axisToward(planned.getFirst());
        } else {
            axis = boss.facingAxis();
        }
        if (axis == null) {
            // Nobody to aim at, or no point switched on: looked at again shortly.
            boss.setAbilityScheduleAt(BossAbility.CONE, gameTime + RETRY_TICKS);
            return false;
        }
        boss.commitAxis(axis);
        boss.beginAction(BossAbility.CONE, cone.getAnimation(), cone.getActionDelayTicks(), gameTime, target,
                data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.CONE, gameTime + cone.getActionDelayTicks()
                + boss.rageDown(cone.getCooldownTicks()));
        return true;
    }

    /**
     * Whether one candidate is worth aiming a cone at.
     *
     * <p>Measured flat and against the same height band the cone itself uses, the line strike's
     * way, so the sector laid down toward whoever this picks really does cover them.</p>
     */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.CONE)) {
            return false;
        }
        if (Math.abs(target.getY() - npc.getY()) > phase.cone().getHeight()) {
            return false;
        }
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double length = phase.cone().getLength();
        return dx * dx + dz * dz <= length * length;
    }

    /**
     * Whether the cone being wound up still has anyone to land on once the warning is over. Only
     * a cone aimed at a target can be dodged by its target: the gaze and the points promised a
     * sector, and stepping out of it already is the dodge.
     */
    boolean stillValid(LivingEntity target, BossPhaseData phase) {
        return phase.cone().getAimMode() != BossPhaseData.CONE_AIM_TARGET || isValidTarget(target, phase);
    }

    /** The cone the cast being wound up lands first, from where the boss stands now. */
    Vec3 firstAxis(Vec3 committed) {
        return planned.isEmpty() ? committed : axisToward(planned.getFirst());
    }

    void perform(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        Vec3 axis = firstAxis(boss.committedAxis());
        planned.clear();
        if (axis != null && phase.cone().isFaceAxis()) {
            // Whatever the eased turn had left is finished on the tick the cone lands.
            boss.turnTowardAxis(axis, phase.cone().getLength(), SNAP_DEGREES);
        }
    }

    /** Forgets the points a wind-up was aimed at: a phase change, a reset or a death called it off. */
    void clear() {
        planned.clear();
    }

    /** The flat line from the boss to a spot, or the gaze for a spot inside the boss. */
    private Vec3 axisToward(Vec3 point) {
        Vec3 flat = new Vec3(point.x - npc.getX(), 0.0D, point.z - npc.getZ());
        return flat.lengthSqr() < CENTRE_EPSILON ? boss.facingAxis() : flat.normalize();
    }

    /** Where the points one cast strikes stand in the world, in the order it strikes them. */
    private List<Vec3> pickPoints(BossConeSettings cone) {
        List<Vec3> anchors = new ArrayList<>();
        for (BossConeAimPoint point : cone.getPoints().entries()) {
            if (point.isEnabled()) {
                anchors.add(minionSpawns.anchor(point.getCoordinateMode() == BossConeAimPoint.COORDINATE_FIXED,
                        point.getX(), point.getY(), point.getZ()));
            }
        }
        return seriesOf(anchors, cone.getPointOrder(), cone.getPointCount(), npc.getRandom());
    }

    /**
     * The points one cast strikes, in the order it strikes them: the enabled ones in list order,
     * or shuffled afresh, cut to {@code count} of them - zero, or more than there are, is all.
     *
     * <p>Shuffled before it is cut, so a random series of two out of five picks its two at
     * random rather than always striking the first two in a random order.</p>
     */
    static <T> List<T> seriesOf(List<T> enabled, int order, int count, RandomSource random) {
        List<T> ordered = new ArrayList<>(enabled);
        if (order == BossPhaseData.CONE_ORDER_RANDOM) {
            for (int i = ordered.size() - 1; i > 0; i--) {
                Collections.swap(ordered, i, random.nextInt(i + 1));
            }
        }
        int taken = count <= 0 ? ordered.size() : Math.min(count, ordered.size());
        return List.copyOf(ordered.subList(0, taken));
    }
}
