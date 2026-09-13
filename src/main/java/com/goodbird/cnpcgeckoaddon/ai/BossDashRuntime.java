package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;
import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The dash: a wind-up on the spot with its corridor marked, then a run straight down it.
 *
 * <p>Owned by {@link TeleportPathController}. Which way the boss runs is settled as it
 * commits, the way the line strike's corridor is: the warning on the floor promises one lane,
 * and a boss that swung round after a sidestepping player would turn the warning into a lie.
 * The target, where there is one, is only there to point the lane.</p>
 */
final class BossDashRuntime {

    /** The turn left over from the eased wind-up, finished on the tick the run starts. */
    private static final float SNAP_DEGREES = 360.0F;
    /** A lane the home leash cuts shorter than this is not worth running. */
    static final double MIN_REACH = 1.0D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossDashRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.DASH, phase) || gameTime < boss.abilityScheduleAt(BossAbility.DASH)) {
            return false;
        }
        if (!npc.onGround()) {
            // Nothing to push off from: a boss knocked into the air tries again shortly.
            boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + RETRY_TICKS);
            return false;
        }
        LivingEntity target = null;
        Vec3 axis;
        if (phase.dash().getDirection() == BossPhaseData.DASH_DIRECTION_TARGET) {
            target = boss.selectAbilityTarget(level, phase.dash().getTargetMode(), phase.dash().getLength(),
                    candidate -> isValidTarget(candidate, phase));
            if (target == null) {
                boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + RETRY_TICKS);
                return false;
            }
            axis = axisToward(target);
        } else {
            axis = boss.facingAxis();
        }
        if (reachFrom(data, phase, npc.position(), axis) < MIN_REACH) {
            // Up against the edge of its leash with the lane pointing out: the run would end
            // before it began, so the boss looks again once it has moved.
            boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boss.commitAxis(axis);
        boss.beginAction(BossAbility.DASH, phase.dash().getAnimation(),
                phase.dash().getActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + phase.dash().getActionDelayTicks()
                + boss.rageDown(phase.dash().getCooldownTicks()));
        return true;
    }

    /**
     * Whether one candidate is worth running at: in reach of the lane's length, measured flat,
     * and inside its height, so the run aimed at them really does go through them.
     */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.DASH)) {
            return false;
        }
        if (Math.abs(target.getY() - npc.getY()) > phase.dash().getHeight()) {
            return false;
        }
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double length = phase.dash().getLength();
        return dx * dx + dz * dz <= length * length;
    }

    /** The flat line from the boss to a victim, or the gaze when they stand inside the boss. */
    private Vec3 axisToward(LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - npc.getX(), 0.0D, target.getZ() - npc.getZ());
        return flat.lengthSqr() < 1.0E-6D ? boss.facingAxis() : flat.normalize();
    }

    /**
     * The start of the run, at the end of the wind-up.
     *
     * <p>The run, not a timer, is what frees a rooted wind-up: from here the boss is on its
     * own legs, and even a dash that goes nowhere has nothing left to stand still for.</p>
     */
    void perform(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        boss.endCastRoot();
        Vec3 axis = boss.committedAxis();
        if (axis == null) {
            return;
        }
        // Whatever the eased turn left is finished now, so the boss sets off facing its lane.
        boss.turnTowardAxis(axis, phase.dash().getLength(), SNAP_DEGREES);
    }

    /** How long the corridor the wind-up marks is: the lane from where the boss stands now. */
    double previewReach(TeleportPathData data, BossPhaseData phase, Vec3 axis) {
        return reachFrom(data, phase, npc.position(), axis);
    }

    /**
     * How far down {@code axis} a run from {@code start} may go: its length, cut where the lane
     * would leave the home leash.
     *
     * <p>Not the leap's radial clamp. That pulls one landing spot back toward home, which for a
     * straight run would bend the lane off the line the warning promised; a run is cut where
     * its own line crosses the leash instead, with the leap's margin inside the edge so the
     * stop does not count as leaving. A vertical leash leaves the flat circle its height at the
     * boss' feet allows.</p>
     */
    double reachFrom(TeleportPathData data, BossPhaseData phase, Vec3 start, Vec3 axis) {
        double length = phase.dash().getLength();
        if (!data.isHomeLeashEnabled()) {
            return length;
        }
        double limit = Math.max(0.0D, data.getHomeLeashRadius() - BossLeapRuntime.LEASH_MARGIN);
        if (data.isHomeLeashVertical()) {
            double dy = start.y - boss.homeY();
            limit = limit * limit > dy * dy ? Math.sqrt(limit * limit - dy * dy) : 0.0D;
        }
        return Math.min(length, leashReach(start.x - boss.homeX(), start.z - boss.homeZ(),
                axis.x, axis.z, limit));
    }

    /**
     * The furthest a run may go along a flat unit axis and still stand inside a circle round
     * home, from a start {@code offset} away from its centre; 0 when no forward step of the
     * run is inside it.
     *
     * <p>A start outside the circle is allowed the far edge of it when the lane points back in:
     * a boss that strayed past its leash and dashes home is running the right way.</p>
     */
    static double leashReach(double offsetX, double offsetZ, double axisX, double axisZ, double limit) {
        if (limit <= 0.0D) {
            return 0.0D;
        }
        // |offset + t * axis| = limit, solved for t with the axis at unit length.
        double b = offsetX * axisX + offsetZ * axisZ;
        double c = offsetX * offsetX + offsetZ * offsetZ - limit * limit;
        double discriminant = b * b - c;
        if (discriminant < 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, -b + Math.sqrt(discriminant));
    }
}
