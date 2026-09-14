package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimPoint;
import com.goodbird.cnpcgeckoaddon.data.BossConeSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The cone strike: a wind-up with its sector marked, then a hit over the whole fan at once -
 * toward whoever it picked, along the boss' gaze, or at the builder's points one after another.
 *
 * <p>Owned by {@link TeleportPathController}. Which way the cone opens is settled as the boss
 * commits, the line strike's rule: the sector drawn on the floor is a promise, and a boss that
 * swung it round after a sidestepping player would turn the warning into a lie. A point is
 * the one aim that is not a direction - it is the block lying on the middle of its cone - so
 * that cone is laid from wherever the boss stands toward it.</p>
 *
 * <p>A series over the points is the boss' own swings, one after another, so it holds the boss
 * busy and on its spot until the last of them lands. Nothing here is saved: a server that goes
 * down mid series leaves the rest of it unstruck.</p>
 */
final class BossConeRuntime {

    /** Closer than this (squared, flat) somebody stands inside the boss and has no direction of their own. */
    private static final double CENTRE_EPSILON = 1.0E-6D;
    /** Slack on the sector's edges, its length and its height, so standing exactly on one is standing in the cone. */
    static final double EDGE_EPSILON = 1.0E-7D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final BossMinionSpawnRuntime minionSpawns;

    /** Where the points the cast being wound up strikes stand, in order; empty unless it is aimed at points. */
    private final List<Vec3> planned = new ArrayList<>();
    /** The points a series under way has still to strike, or null between series. */
    private Series<Vec3> series;
    /** Phase the series started in: what its cones hit for belongs to the settings that launched it. */
    private int phaseIndex = -1;

    BossConeRuntime(TeleportPathController boss, EntityNPCInterface npc, BossMinionSpawnRuntime minionSpawns) {
        this.boss = boss;
        this.npc = npc;
        this.minionSpawns = minionSpawns;
    }

    /** Whether a series is under way, which the busy gate, the pin and the chains all wait for. */
    boolean isSequencing() {
        return series != null;
    }

    /** The cone a series strikes next, from where the boss stands now, or null between series. */
    Vec3 nextAxis() {
        return series == null || series.isOver() ? null : axisToward(series.upcoming());
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.CONE, phase) || gameTime < boss.abilityScheduleAt(BossAbility.CONE)
                || isSequencing()) {
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
            boss.setAbilityScheduleAt(BossAbility.CONE, gameTime + boss.retryTicks());
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
     * Whether the cone being wound up still has anyone to land on once the warning is over,
     * by the rule the phase chose.
     *
     * <p>The sector was committed to when the warning went up, so by default there is nothing
     * to call off - the line strike's rule: stepping out of the fan already is the dodge, and
     * calling the cast off would only bring the same cone back round in two seconds, after the
     * boss stood rooted through a swing it never made. A phase may instead have the cast called
     * off when nobody it may hit is left in the promised fan, or - the rule the cone started
     * with - when its target got further away than the cone reaches, in the fan or not; along
     * the gaze or at points that last rule has no target to judge and calls nothing off.</p>
     */
    boolean stillValid(ServerLevel level, TeleportPathData data, LivingEntity target, BossPhaseData phase) {
        BossConeSettings cone = phase.cone();
        int mode = cone.getDodgeMode();
        // Each rule's question is asked only under that rule: the fan's is an entity scan.
        boolean anyoneInFan = mode == BossPhaseData.CONE_DODGE_SECTOR
                && !victimsIn(level, data, cone, npc.position(), axesFor(boss.committedAxis())).isEmpty();
        boolean targetInReach = mode == BossPhaseData.CONE_DODGE_RANGE
                && (cone.getAimMode() != BossPhaseData.CONE_AIM_TARGET || isValidTarget(target, phase));
        return survivesWarning(mode, anyoneInFan, targetInReach);
    }

    /**
     * Whether the warning's end lets the cast go on: always under the first rule, with anyone
     * left in the promised fan under the second, with the target still in reach under the third.
     */
    static boolean survivesWarning(int dodgeMode, boolean anyoneInFan, boolean targetInReach) {
        return switch (dodgeMode) {
            case BossPhaseData.CONE_DODGE_SECTOR -> anyoneInFan;
            case BossPhaseData.CONE_DODGE_RANGE -> targetInReach;
            default -> true;
        };
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
        return axesToward(planned);
    }

    /**
     * The first cone at the end of the wind-up, and the series after it when the cast is aimed at
     * points: the rest follow one pause apart, or land with the first when there is no pause.
     */
    void perform(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        List<Vec3> points = List.copyOf(planned);
        planned.clear();
        if (points.isEmpty()) {
            Vec3 committed = boss.committedAxis();
            if (committed != null) {
                strike(level, data, phase, List.of(committed));
            }
            return;
        }
        Series<Vec3> started = new Series<>(points, phase.cone().getPointIntervalTicks(), gameTime);
        List<Vec3> first = started.due(gameTime);
        // Under way before the first cone lands rather than after: a hit can set off a script that
        // staggers or resets the boss, and whatever that ends has to find the series to end.
        if (!started.isOver()) {
            series = started;
            phaseIndex = boss.currentPhaseIndex();
            // Nothing else starts from this tick on until the last cone has landed.
            boss.holdBusyUntil(gameTime + 1);
        }
        strike(level, data, phase, axesToward(first));
    }

    /**
     * Carries a series one tick further: the next cone once its pause is over, and the usual
     * pause after a cast once the last one has landed.
     *
     * <p>Runs every tick above the controller's gates, the dash's way: the series holds the busy
     * gate shut itself, so it has to be ticked before that gate turns everything else away.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (series == null) {
            return;
        }
        boss.holdBusyUntil(gameTime + 1);
        BossPhaseData phase = phaseOf(data);
        if (phase == null) {
            // Its phase was deleted from under it, and what the rest would hit for with it.
            finish(gameTime);
            return;
        }
        List<Vec3> due = series.due(gameTime);
        if (!due.isEmpty()) {
            strike(level, data, phase, axesToward(due));
        }
        // Asked again rather than trusted: a hit can set off a script that kills or resets the
        // boss, and that clears the series under it.
        if (series != null && series.isOver()) {
            finish(gameTime);
        }
    }

    /** The end every series but a called-off one comes to: the usual pause after a cast. */
    private void finish(long gameTime) {
        clear();
        boss.holdBusyUntil(gameTime + boss.postActionLockTicks());
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
            boss.turnTowardAxis(axes.getFirst(), cone.getLength(), cone.getSnapDegrees());
        }
        Vec3 origin = npc.position();
        // Purely for show, and started before the hits so the flash goes out at the same moment
        // the damage lands rather than a tick behind it.
        flash(level, origin, axes, cone);
        int damage = boss.rageUp(cone.getDamage());
        int strength = boss.rageUp(cone.getImpulseStrength());
        for (LivingEntity victim : victimsIn(level, data, cone, origin, axes)) {
            // The impulse is this strike's own half rather than something on top of the damage,
            // the geyser's rule: a totem this cone may not break is left standing, not moved.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.CONE)) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.CONE, npc, damage, cone.getEffects(),
                    0, 0.0D, 0.0D);
            // Given whether the damage landed or not, the throw's way, so all three impulses answer
            // alike: a cone set to no damage still pulls, and a second cone of a series still shoves
            // somebody the first left in their hurt cooldown.
            if (cone.getImpulseMode() == BossPhaseData.CONE_IMPULSE_LIFT) {
                BossGeyserScheduler.launch(victim, strength);
            } else {
                // Vanilla shoves against the vector it is handed: the way to the boss throws the
                // victim off it, and the way from the boss draws them in.
                double towardX = origin.x - victim.getX();
                double towardZ = origin.z - victim.getZ();
                boolean pull = cone.getImpulseMode() == BossPhaseData.CONE_IMPULSE_PULL;
                shove(victim, strength, pull ? -towardX : towardX, pull ? -towardZ : towardZ);
            }
        }
    }

    /**
     * Knockback away from {@code x, z}, sent to a player even when no hurt went through to send it.
     * Shared with the platforms, which shove whoever stays on one whether its damage lands or not.
     */
    static void shove(LivingEntity victim, int strength, double x, double z) {
        if (strength <= 0) {
            return;
        }
        victim.knockback(strength, x, z);
        // Players simulate their own movement, and a hurt that landed is what usually marks the
        // new velocity for sending; one that did not leaves that to here.
        victim.hurtMarked = true;
    }

    /**
     * The strike seen and heard: a sweep's whoosh, and a quick flash of arcs spread evenly
     * along each fan, so the hit reads as travelling outward.
     */
    private void flash(ServerLevel level, Vec3 origin, List<Vec3> axes, BossConeSettings cone) {
        cone.getSwingSound().play(level, origin.x, origin.y, origin.z, SoundSource.HOSTILE);
        int arcs = cone.getFlashArcs();
        if (arcs <= 0
                || level.getNearestPlayer(origin.x, origin.y, origin.z,
                BossTelegraphUtil.audienceRange(npc), false) == null) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(BossAbilityKind.CONE);
        double halfAngle = cone.getAngle() * 0.5D;
        for (Vec3 axis : axes) {
            float yaw = yawOf(axis);
            for (int step = 1; step <= arcs; step++) {
                BossTelegraphUtil.arc(level, origin, cone.getLength() * step / (double) arcs, yaw,
                        halfAngle, dust);
            }
        }
    }

    /** The Minecraft yaw a flat unit axis points along, which is what the floor marks are turned by. */
    static float yawOf(Vec3 axis) {
        return (float) (Mth.atan2(axis.z, axis.x) * Mth.RAD_TO_DEG) - 90.0F;
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

    /**
     * Drops the points a wind-up was aimed at and whatever is left of a series.
     *
     * <p>Idempotent and the one road out: the last cone, a phase change, a reset, a death and a
     * stagger all end a series here, so none of them can leave the boss held busy for good.</p>
     */
    void clear() {
        planned.clear();
        series = null;
        phaseIndex = -1;
    }

    /** The phase a series belongs to, so a phase that disappears mid series cannot rewrite its hits. */
    private BossPhaseData phaseOf(TeleportPathData data) {
        return phaseIndex >= 0 && phaseIndex < data.getPhaseCount() ? data.getPhase(phaseIndex) : null;
    }

    /** The flat line from the boss to a spot, or the gaze for a spot inside the boss. */
    private Vec3 axisToward(Vec3 point) {
        Vec3 flat = new Vec3(point.x - npc.getX(), 0.0D, point.z - npc.getZ());
        return flat.lengthSqr() < CENTRE_EPSILON ? boss.facingAxis() : flat.normalize();
    }

    private List<Vec3> axesToward(List<Vec3> points) {
        List<Vec3> axes = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            axes.add(axisToward(point));
        }
        return axes;
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

    /**
     * The cones of one series still to strike, and when the next is due: a list and a clock,
     * kept apart from the world so the order and the timing can be tested without one.
     */
    static final class Series<T> {
        private final List<T> items;
        private final int interval;
        private int next;
        private long nextAt;

        Series(List<T> items, int interval, long startsAt) {
            this.items = List.copyOf(items);
            this.interval = Math.max(0, interval);
            this.nextAt = startsAt;
        }

        /**
         * The cones due at this tick, in order, taken off the series: all that are left when there
         * is no pause between them, otherwise the next one once its pause is over.
         *
         * <p>The pause is counted from when the cone before really landed, so a series held up -
         * a carried boss is not ticked - picks up again one cone at a time rather than landing
         * every cone that fell due in the meantime at once.</p>
         */
        List<T> due(long gameTime) {
            if (isOver() || gameTime < nextAt) {
                return List.of();
            }
            if (interval == 0) {
                List<T> rest = items.subList(next, items.size());
                next = items.size();
                return rest;
            }
            T item = items.get(next++);
            nextAt = gameTime + interval;
            return List.of(item);
        }

        boolean isOver() {
            return next >= items.size();
        }

        /** The next cone to strike, or null once the series is over. */
        T upcoming() {
            return isOver() ? null : items.get(next);
        }
    }
}
