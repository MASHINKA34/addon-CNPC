package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Holds a boss' sweeping beam: for a while after the cast one or more lines turn round it,
 * and whoever they catch up with is burned.
 *
 * <p>The whole mechanic is the turn. A beam comes round at a walking pace or slower, so a
 * player who keeps stepping the way it is going is never in it and one who stops is; and
 * because a beam ends at the first solid block on its line, a pillar is somewhere to stand.
 * Both are why the beams are drawn on every tick and whatever the warning settings say: a
 * beam nobody can see is not a mechanic, it is a trap.</p>
 *
 * <p>Everything the sweep needs is snapshotted on the cast - the enrage bonus included - and
 * driven from the level tick, the way a gravity field is: the boss is back on its rotation
 * the moment the cast lands. Only the centre is read fresh, and only when the beams are set
 * to follow the boss.</p>
 *
 * <p>Nothing here is persisted. A sweep lives for seconds, and a server that shuts down
 * inside that window should not burn whoever logs in first.</p>
 */
public final class BossBeamScheduler {

    /** Sweeps one level tick works on. One per boss is all a fight has, so this is a runaway stop. */
    private static final int MAX_PER_TICK = 16;
    /** Beyond this nobody can see a beam, so it turns without costing anything. */
    private static final double AUDIENCE_RANGE = 64.0D;
    /**
     * How high above the boss' feet the beams leave. Its waist, for a boss of about a
     * player's size; a giant's waist would pass clean over everyone's head, so the beams
     * are never raised above a block.
     */
    private static final double MAX_HEIGHT = 1.0D;
    private static final double TICKS_PER_SECOND = 20.0D;

    /** One sweep, mid turn. */
    private static final class Sweep {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final int count;
        private final double length;
        /** Minecraft yaw of the first beam on the tick the sweep began, in degrees. */
        private final float startYaw;
        /** Degrees turned per tick, sign included. */
        private final double degreesPerTick;
        private final boolean followsBoss;
        private final boolean stopsAtWalls;
        private final long startedAt;
        private final long endsAt;
        /** Where the beams turn round: the boss, tick by tick, or the spot it cast them from. */
        private Vec3 centre;

        private Sweep(ResourceKey<Level> dimension, EntityNPCInterface boss, BossPhaseData phase,
                      float startYaw, long gameTime) {
            this.dimension = dimension;
            this.boss = boss;
            this.count = phase.getBeamCount();
            this.length = phase.getBeamLength();
            this.startYaw = startYaw;
            this.degreesPerTick = phase.getBeamDegreesPerSecond() / TICKS_PER_SECOND;
            this.followsBoss = phase.isBeamFollowsBoss();
            this.stopsAtWalls = phase.isBeamStopsAtWalls();
            this.startedAt = gameTime;
            this.endsAt = gameTime + phase.getBeamDurationTicks();
            this.centre = centreOf(boss);
        }
    }

    private static final TickQueue<Sweep> SWEEPS = new TickQueue<>("boss beam sweeps", MAX_PER_TICK);

    private BossBeamScheduler() {
    }

    /**
     * Switches the beams on round the boss.
     *
     * @param startYaw the Minecraft yaw the first beam leaves at; the rest are spaced evenly after it
     */
    public static void start(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, float startYaw,
                             long gameTime) {
        SWEEPS.add(new Sweep(level.dimension(), boss, phase, startYaw, gameTime));
        Vec3 centre = centreOf(boss);
        // The guardian's own zap, deeper: the one sound in the game that already means a beam.
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GUARDIAN_ATTACK,
                SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    public static boolean hasPending() {
        return !SWEEPS.isEmpty();
    }

    /** Whether one boss has beams turning right now. */
    public static boolean isSweeping(EntityNPCInterface boss) {
        return SWEEPS.find(sweep -> sweep.boss == boss) != null;
    }

    /** Ticks the sweep one boss has running has left, or 0 when it has none. */
    public static long remainingTicks(EntityNPCInterface boss, long gameTime) {
        Sweep sweep = SWEEPS.find(candidate -> candidate.boss == boss);
        return sweep == null ? 0L : Math.max(0L, sweep.endsAt - gameTime);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        SWEEPS.sweep(sweep -> sweep.dimension.equals(level.dimension()),
                sweep -> tickSweep(level, sweep, gameTime));
    }

    /** Drops anything still turning in a level that is going away. */
    public static void clear(ServerLevel level) {
        SWEEPS.removeIf(sweep -> sweep.dimension.equals(level.dimension()));
    }

    /**
     * Switches the beams one boss has running off, for its death, the end of its fight and
     * a change of phase.
     *
     * <p>A beam is the boss doing something, not a fixture of the arena: killing it mid
     * sweep is a win, and the arena owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (SWEEPS.isEmpty()) {
            return;
        }
        SWEEPS.removeIf(sweep -> sweep.boss == boss);
    }

    /** @return whether this sweep is still turning and belongs back in the queue */
    private static boolean tickSweep(ServerLevel level, Sweep sweep, long gameTime) {
        EntityNPCInterface boss = sweep.boss;
        if (!boss.isAlive() || boss.isRemoved() || boss.level() != level || gameTime >= sweep.endsAt) {
            return false;
        }
        if (sweep.followsBoss) {
            // Read fresh every tick: the beams turn round wherever the boss is now.
            sweep.centre = centreOf(boss);
        }
        Vec3[] ends = new Vec3[sweep.count];
        for (int i = 0; i < sweep.count; i++) {
            ends[i] = reach(level, boss, sweep.centre, direction(beamYaw(sweep, i, gameTime)),
                    sweep.length, sweep.stopsAtWalls);
        }
        paint(level, sweep.centre, ends);
        return true;
    }

    /**
     * Where one beam points on this tick, as a Minecraft yaw.
     *
     * <p>The turn is taken off the yaw rather than added to it. Minecraft's yaw grows
     * clockwise seen from above, and the setting reads the other way round - positive is
     * anticlockwise, the way a compass rose and every maths lesson turn.</p>
     */
    private static float beamYaw(Sweep sweep, int index, long gameTime) {
        double turned = sweep.degreesPerTick * (gameTime - sweep.startedAt);
        return (float) (sweep.startYaw - turned + index * 360.0D / sweep.count);
    }

    /** The flat unit direction a Minecraft yaw points along, the way vanilla reads it. */
    private static Vec3 direction(float yaw) {
        double radians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    /**
     * Where the beams leave the boss: on its centre, at its waist or a block up, whichever
     * is lower.
     */
    public static Vec3 centreOf(EntityNPCInterface boss) {
        return boss.position().add(0.0D, Math.min(boss.getBbHeight() * 0.5D, MAX_HEIGHT), 0.0D);
    }

    /** Where a beam leaving {@code centre} this way ends: at its full length, for now. */
    private static Vec3 reach(ServerLevel level, EntityNPCInterface boss, Vec3 centre, Vec3 direction,
                              double length, boolean stopsAtWalls) {
        return centre.add(direction.scale(length));
    }

    /**
     * The lines the beams would leave along right now, for the wind-up's mark.
     *
     * <p>Painted from the boss' centre at the yaw the cast committed to, and cut short by
     * the same walls the sweep will be: a warning that ran through a pillar would promise a
     * beam the pillar is going to stop.</p>
     */
    public static void paintStart(ServerLevel level, EntityNPCInterface boss, float startYaw, int count,
                                  double length, boolean stopsAtWalls) {
        Vec3 centre = centreOf(boss);
        Vec3[] ends = new Vec3[count];
        for (int i = 0; i < count; i++) {
            float yaw = (float) (startYaw + i * 360.0D / count);
            ends[i] = reach(level, boss, centre, direction(yaw), length, stopsAtWalls);
        }
        paint(level, centre, ends);
    }

    /**
     * The beams themselves, one run of dust each from the centre to wherever they end.
     *
     * <p>At the wind-up mark's own spacing and ceiling, so four beams a tick cost what a
     * corridor's outline does every other one; and only with somebody near enough to see
     * them, since a beam is drawn on every tick it turns.</p>
     */
    private static void paint(ServerLevel level, Vec3 centre, Vec3[] ends) {
        if (level.getNearestPlayer(centre.x, centre.y, centre.z, AUDIENCE_RANGE, false) == null) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(BossAbilityKind.BEAM);
        for (Vec3 end : ends) {
            BossTelegraphUtil.line(level, centre, end, dust);
        }
    }
}
