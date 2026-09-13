package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimPoint;
import com.goodbird.cnpcgeckoaddon.data.BossConeSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
    /** Slack on the sector's edges, its length and its height, so standing exactly on one is standing in the cone. */
    static final double EDGE_EPSILON = 1.0E-7D;

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
        // Nobody to aim at, no point switched on, or nobody in any of the cones: no reason to
        // swing, the strike would land on bare floor and spend a whole cooldown doing it.
        if (axis == null || victimsIn(level, data, cone, npc.position(), axesFor(axis)).isEmpty()) {
            planned.clear();
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

    /**
     * Every cone the cast being wound up lands, from where the boss stands now: one toward each
     * of its points, or the one it committed to.
     */
    List<Vec3> axesFor(Vec3 committed) {
        if (planned.isEmpty()) {
            return committed == null ? List.of() : List.of(committed);
        }
        List<Vec3> axes = new ArrayList<>(planned.size());
        for (Vec3 point : planned) {
            axes.add(axisToward(point));
        }
        return axes;
    }

    void perform(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        List<Vec3> axes = axesFor(boss.committedAxis());
        planned.clear();
        if (!axes.isEmpty()) {
            strike(level, data, phase, axes);
        }
    }

    /**
     * Lands the cones laid along {@code axes} at once. Whoever stands in any of them takes the
     * hit once, however many of the cones cover them.
     */
    private void strike(ServerLevel level, TeleportPathData data, BossPhaseData phase, List<Vec3> axes) {
        BossConeSettings cone = phase.cone();
        if (cone.isFaceAxis()) {
            // Whatever the eased turn had left is finished on the tick the cone lands, so the
            // model points exactly down the middle of the fan it hits.
            boss.turnTowardAxis(axes.getFirst(), cone.getLength(), SNAP_DEGREES);
        }
        Vec3 origin = npc.position();
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.5F, 0.6F);
        int damage = boss.rageUp(cone.getDamage());
        int strength = boss.rageUp(cone.getImpulseStrength());
        for (LivingEntity victim : victimsIn(level, data, cone, origin, axes)) {
            if (cone.getImpulseMode() == BossPhaseData.CONE_IMPULSE_LIFT) {
                // The throw is this strike's knockback rather than something on top of it, the
                // geyser's rule: a totem this cone may not break is left standing, not thrown.
                if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.CONE)) {
                    continue;
                }
                BossAbilityDamageUtil.hit(victim, BossAbilityKind.CONE, npc, damage, cone.getEffects(),
                        0, 0.0D, 0.0D);
                BossGeyserScheduler.launch(victim, strength);
                continue;
            }
            // Vanilla shoves against the vector it is handed: the way to the boss throws the
            // victim off it, and the way from the boss draws them in.
            double towardX = origin.x - victim.getX();
            double towardZ = origin.z - victim.getZ();
            boolean pull = cone.getImpulseMode() == BossPhaseData.CONE_IMPULSE_PULL;
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.CONE, npc, damage, cone.getEffects(), strength,
                    pull ? -towardX : towardX, pull ? -towardZ : towardZ);
        }
    }

    /**
     * Everyone the cones laid from {@code origin} along {@code axes} currently cover.
     *
     * <p>The box round the whole reach is only a pre-filter, the line strike's way, and the shape
     * itself is decided per candidate. Who may be hit at all is the rule every area hit shares -
     * an immune npc, an ally, a boss hidden by its totems and a kind this boss does not aim its
     * abilities at are all left alone - so a cone and a gravity field cannot disagree about it.</p>
     */
    private List<LivingEntity> victimsIn(ServerLevel level, TeleportPathData data, BossConeSettings cone,
                                         Vec3 origin, List<Vec3> axes) {
        if (axes.isEmpty()) {
            return List.of();
        }
        double reach = cone.getLength() + 1.0D;
        AABB box = new AABB(origin, origin).inflate(reach, cone.getHeight() + 1.0D, reach);
        return level.getEntitiesOfClass(LivingEntity.class, box, target -> target != npc && target.isAlive()
                && boss.isAbilityTarget(target, BossAbilityKind.CONE)
                && boss.matchesAbilityTargetKind(target, data)
                && !BossMechanicUtil.hiddenByTotems(target)
                && inAnySector(origin, axes, cone, target));
    }

    private static boolean inAnySector(Vec3 origin, List<Vec3> axes, BossConeSettings cone, LivingEntity target) {
        for (Vec3 axis : axes) {
            if (inSector(origin, axis, cone.getAngle(), cone.getLength(), cone.getHeight(),
                    target.getX(), target.getY(), target.getZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a spot is inside a cone laid from {@code origin} along the flat unit {@code axis}:
     * no further off the axis, seen from the boss, than half of {@code angle} degrees; no further
     * out than {@code length}, measured flat; and no more than {@code height} above or below the
     * boss' feet. Every edge counts as inside.
     *
     * <p>Somebody standing inside the boss has no direction to be judged by, and counts as in
     * the cone: nobody gets clear of a swing by hugging whoever swings it.</p>
     */
    static boolean inSector(Vec3 origin, Vec3 axis, double angle, double length, double height,
                            double x, double y, double z) {
        if (Math.abs(y - origin.y) > height + EDGE_EPSILON) {
            return false;
        }
        double dx = x - origin.x;
        double dz = z - origin.z;
        double distanceSqr = dx * dx + dz * dz;
        double reach = length + EDGE_EPSILON;
        if (distanceSqr > reach * reach) {
            return false;
        }
        if (distanceSqr < CENTRE_EPSILON) {
            return true;
        }
        // Compared as cosines rather than as angles: how far along the axis the spot is, over how
        // far away it is, is the cosine of how far off the axis it stands.
        double along = dx * axis.x + dz * axis.z;
        return along >= Math.sqrt(distanceSqr) * Math.cos(Math.toRadians(angle * 0.5D)) - EDGE_EPSILON;
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
