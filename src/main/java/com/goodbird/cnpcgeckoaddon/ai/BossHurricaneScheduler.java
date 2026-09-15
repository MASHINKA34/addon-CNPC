package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossHurricaneSettings;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Drives the storms a hurricane let loose: each one a point on the arena floor with a heading,
 * a reach and a lifetime, moved from the level tick long after the cast that made it landed.
 *
 * <p>A storm is not an entity. It has no model, takes no hits and is never saved; it is a record
 * here, the way a geyser's fuse is, and everything it needs is snapshotted on the cast - the
 * enrage bonus included - so a builder editing the ability while the storms are out cannot
 * change what the party is already running from.</p>
 *
 * <p>Nothing here is persisted. A storm lives for seconds, and a server that shuts down inside
 * that window should not have one waiting for whoever logs in first on the next start.</p>
 */
public final class BossHurricaneScheduler {

    /**
     * How many storms one level tick moves. A typhoon is twelve at most and a boss rarely has
     * two casts out at once, so this is a runaway stop rather than a limit a fight meets.
     */
    private static final int MAX_PER_TICK = 128;
    /** How many random points a typhoon's storm tries before giving up on finding floor. */
    private static final int TYPHOON_POINT_TRIES = 8;
    /** The storm's box starts this far above its floor, so the floor itself is never the wall. */
    private static final double BOX_SKIN = 0.05D;
    /** A step shorter than this is the end of a path rather than a move. */
    private static final double PATH_EPSILON = 1.0E-6D;

    /**
     * What a storm does and looks like, taken off the settings on the tick it was let go.
     *
     * <p>Split out of {@link Storm} so it can be taken without a world to let a storm go in.</p>
     */
    static final class Look {
        final int launchMode;
        final int count;
        final double speed;
        final double range;
        final int lifetimeTicks;
        final double radius;
        final int bounces;
        final double spiralDegrees;
        final double spiralGrowth;
        final double liftHeight;
        final int liftTicks;
        final int holdTicks;
        final float spinDegrees;
        final double orbitRadius;
        final boolean spinView;
        final int maxVictims;
        /** What a held victim takes on each clock, enrage already counted in. */
        final int damage;
        final int damageIntervalTicks;
        final BossEffectSet effects;
        final double throwSide;
        final double throwUp;
        final int graceTicks;
        final int floorSearch;
        final double columnHeight;
        final int columnDensity;
        final int loopIntervalTicks;
        final BossSoundCue launchSound;
        final BossSoundCue loopSound;
        final BossSoundCue catchSound;
        final BossSoundCue releaseSound;
        final BossParticleCue columnParticles;
        final BossParticleCue baseParticles;
        final BossParticleCue trailParticles;
        final BossParticleCue catchParticles;

        private Look(BossHurricaneSettings hurricane, int damage) {
            launchMode = hurricane.getLaunchMode();
            count = hurricane.getCount();
            speed = hurricane.getSpeed();
            range = hurricane.getRange();
            lifetimeTicks = hurricane.getLifetimeTicks();
            radius = hurricane.getRadius();
            bounces = hurricane.getBounces();
            spiralDegrees = hurricane.getSpiralDegrees();
            spiralGrowth = hurricane.getSpiralGrowth();
            liftHeight = hurricane.getLiftHeight();
            liftTicks = hurricane.getLiftTicks();
            holdTicks = hurricane.getHoldTicks();
            spinDegrees = hurricane.getSpinDegrees();
            orbitRadius = hurricane.getOrbitRadius();
            spinView = hurricane.isSpinView();
            maxVictims = hurricane.getMaxVictims();
            this.damage = damage;
            damageIntervalTicks = hurricane.getDamageIntervalTicks();
            effects = hurricane.getEffects();
            throwSide = hurricane.getThrow();
            throwUp = hurricane.getThrowUp();
            graceTicks = hurricane.getGraceTicks();
            floorSearch = hurricane.getFloorSearch();
            columnHeight = hurricane.getColumnHeight();
            columnDensity = hurricane.getColumnDensity();
            loopIntervalTicks = hurricane.getLoopIntervalTicks();
            launchSound = hurricane.getLaunchSound().copy();
            loopSound = hurricane.getLoopSound().copy();
            catchSound = hurricane.getCatchSound().copy();
            releaseSound = hurricane.getReleaseSound().copy();
            columnParticles = hurricane.getColumnParticles().copy();
            baseParticles = hurricane.getBaseParticles().copy();
            trailParticles = hurricane.getTrailParticles().copy();
            catchParticles = hurricane.getCatchParticles().copy();
        }
    }

    /** What a storm will do, whatever the builder does next. */
    static Look look(BossHurricaneSettings hurricane, int damage) {
        return new Look(hurricane, damage);
    }

    /** One storm, out on the floor. */
    static final class Storm {
        final ResourceKey<Level> dimension;
        final EntityNPCInterface boss;
        final Look look;
        /** Where the eye stands: on the floor, one block up from the block it lies on. */
        Vec3 pos;
        /** Which way and how fast, and how many walls are left in it. */
        final BossHurricanePath.Course course;
        /** What a straight storm still has to travel before it dies at the end of its path. */
        double remainingPath;
        final long diesAt;
        /** The boss' feet on the cast: what a spiral winds round and a typhoon wanders about. */
        final Vec3 centre;
        double spiralAngle;
        double spiralRadius;
        /** Which way round the spiral turns; a wall sends it back the other way. */
        int spiralTurn = 1;
        /** Where a typhoon's storm is heading, or null until it has picked somewhere. */
        Vec3 target;
        /** Set on a bounce or a new point, so whoever rides the storm is told the new heading. */
        boolean headingChanged;

        private Storm(ResourceKey<Level> dimension, EntityNPCInterface boss, Look look, Vec3 pos,
                      Vec3 velocity, double remainingPath, long diesAt, Vec3 centre) {
            this.dimension = dimension;
            this.boss = boss;
            this.look = look;
            this.pos = pos;
            this.course = new BossHurricanePath.Course(velocity, look.bounces);
            this.remainingPath = remainingPath;
            this.diesAt = diesAt;
            this.centre = centre;
        }
    }

    /** How a move came out: the storm went, or it met a wall on these axes, or it is over. */
    private record Move(boolean moved, boolean dead, boolean wallX, boolean wallZ) {
        private static final Move MOVED = new Move(true, false, false, false);
        private static final Move DEAD = new Move(false, true, false, false);
    }

    private static final TickQueue<Storm> STORMS = new TickQueue<>("boss hurricanes", MAX_PER_TICK);

    private BossHurricaneScheduler() {
    }

    /**
     * Lets a cast's storms go from where the boss stands.
     *
     * @param axis   the committed heading: the straight storm's own, what a cross is laid out
     *               from, and where a spiral's first storm sets off
     * @param damage what a held victim takes on each clock, with the enrage bonus already in it
     */
    public static void launch(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, Vec3 axis,
                              int damage, long gameTime) {
        Look look = look(phase.hurricane(), damage);
        Vec3 origin = floorUnder(level, boss, look);
        long diesAt = gameTime + look.lifetimeTicks;
        switch (look.launchMode) {
            case BossPhaseData.HURRICANE_MODE_SPIRAL -> {
                double gaze = BossHurricanePath.angleOf(origin, origin.x + axis.x, origin.z + axis.z);
                for (int i = 0; i < look.count; i++) {
                    Storm storm = new Storm(level.dimension(), boss, look, origin, Vec3.ZERO, 0.0D, diesAt, origin);
                    storm.spiralAngle = BossHurricanePath.spiralStartAngle(gaze, i, look.count);
                    STORMS.add(storm);
                }
            }
            case BossPhaseData.HURRICANE_MODE_TYPHOON -> {
                for (int i = 0; i < look.count; i++) {
                    Storm storm = new Storm(level.dimension(), boss, look, origin, Vec3.ZERO, 0.0D, diesAt, origin);
                    // A storm with nowhere to go - no floor anywhere in reach - is not let go at all.
                    if (retarget(level, storm)) {
                        STORMS.add(storm);
                    }
                }
            }
            default -> {
                for (Vec3 heading : BossHurricanePath.launchAxes(look.launchMode, axis)) {
                    STORMS.add(new Storm(level.dimension(), boss, look, origin, heading.scale(look.speed),
                            look.range, diesAt, origin));
                }
            }
        }
    }

    /** The boss' feet on the floor, or its own height when it stands over nothing within reach. */
    private static Vec3 floorUnder(ServerLevel level, EntityNPCInterface boss, Look look) {
        BlockPos floor = BossFloorUtil.findFloor(level, boss.getX(), boss.getY(), boss.getZ(), look.floorSearch);
        return new Vec3(boss.getX(), floor == null ? boss.getY() : floor.getY() + 1.0D, boss.getZ());
    }

    public static boolean hasPending() {
        return !STORMS.isEmpty();
    }

    /** Whether this boss still has a storm out; what a chain, a finish hold and a cast spot's stay wait on. */
    public static boolean hasPending(EntityNPCInterface boss) {
        return !STORMS.isEmpty() && STORMS.find(storm -> storm.boss == boss) != null;
    }

    /** The diagnostic command's line. */
    public static String status(EntityNPCInterface boss) {
        return "Hurricanes: " + STORMS.count(storm -> storm.boss == boss) + " storms, 0 held";
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        STORMS.sweep(storm -> storm.dimension.equals(level.dimension()),
                storm -> tickStorm(level, storm, gameTime));
    }

    /** Drops every storm in a level that is going away. */
    public static void clear(ServerLevel level) {
        STORMS.removeIf(storm -> {
            if (!storm.dimension.equals(level.dimension())) {
                return false;
            }
            extinguish(level, storm, level.getGameTime(), false);
            return true;
        });
    }

    /**
     * Drops the storms one boss let go, for its death, a phase that is over and the end of its
     * fight: a storm is the boss doing something, not weather, and the arena owes the party
     * nothing more once the boss has stopped.
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (STORMS.isEmpty()) {
            return;
        }
        ServerLevel level = boss.level() instanceof ServerLevel server ? server : null;
        long gameTime = level == null ? 0L : level.getGameTime();
        STORMS.removeIf(storm -> {
            if (storm.boss != boss) {
                return false;
            }
            extinguish(level, storm, gameTime, false);
            return true;
        });
    }

    /** @return whether this storm is still going and belongs back in the queue */
    private static boolean tickStorm(ServerLevel level, Storm storm, long gameTime) {
        if (!storm.boss.isAlive() || storm.boss.isRemoved()) {
            extinguish(level, storm, gameTime, false);
            return false;
        }
        storm.headingChanged = false;
        if (gameTime >= storm.diesAt || !advance(level, storm)) {
            extinguish(level, storm, gameTime, true);
            return false;
        }
        return true;
    }

    /**
     * The end of a storm, whatever ended it.
     *
     * @param throwOut whether whoever it held goes out the usual way, thrown clear, rather than
     *                 simply let go where they are - the boss dying, its fight ending, the level
     *                 going away
     */
    private static void extinguish(ServerLevel level, Storm storm, long gameTime, boolean throwOut) {
    }

    /** One tick of travel. @return false when the storm is over: no floor, no bounce left, no path left */
    private static boolean advance(ServerLevel level, Storm storm) {
        return switch (storm.look.launchMode) {
            case BossPhaseData.HURRICANE_MODE_SPIRAL -> advanceSpiral(level, storm);
            case BossPhaseData.HURRICANE_MODE_TYPHOON -> advanceTyphoon(level, storm);
            default -> advanceStraight(level, storm);
        };
    }

    /** A straight storm, and each arm of a cross: along its heading until its path runs out. */
    private static boolean advanceStraight(ServerLevel level, Storm storm) {
        if (storm.remainingPath <= PATH_EPSILON) {
            return false;
        }
        double step = Math.min(storm.look.speed, storm.remainingPath);
        Vec3 heading = storm.course.velocity();
        Move move = tryMove(level, storm, storm.pos.add(heading.scale(step / storm.look.speed)));
        if (move.dead()) {
            return false;
        }
        if (move.moved()) {
            // A wall does not lengthen the path: what is left to travel is what was left.
            storm.remainingPath -= step;
            return true;
        }
        return bounce(storm, move);
    }

    /** A spiral's storm: round the boss, wider each tick until its range, then round at that. */
    private static boolean advanceSpiral(ServerLevel level, Storm storm) {
        double nextAngle = storm.spiralAngle + storm.look.spiralDegrees * storm.spiralTurn;
        double nextRadius = BossHurricanePath.spiralRadius(storm.spiralRadius, storm.look.spiralGrowth,
                storm.look.range);
        Vec3 before = storm.pos;
        Move move = tryMove(level, storm, BossHurricanePath.orbitPoint(storm.centre, nextRadius, nextAngle));
        if (move.dead()) {
            return false;
        }
        if (move.moved()) {
            storm.spiralAngle = nextAngle;
            storm.spiralRadius = nextRadius;
            storm.course.setVelocity(new Vec3(storm.pos.x - before.x, 0.0D, storm.pos.z - before.z));
            return true;
        }
        // A spiral has no straight heading to turn round: coming off a wall is winding back the
        // other way, from where it is, still growing.
        if (!bounce(storm, move)) {
            return false;
        }
        storm.spiralTurn = -storm.spiralTurn;
        return true;
    }

    /** A typhoon's storm: to its point, then to another, for as long as it lives. */
    private static boolean advanceTyphoon(ServerLevel level, Storm storm) {
        if (storm.target == null && !retarget(level, storm)) {
            return false;
        }
        Vec3 before = storm.pos;
        Move move = tryMove(level, storm,
                storm.pos.add(BossHurricanePath.stepToward(storm.pos, storm.target, storm.look.speed)));
        if (move.dead()) {
            return false;
        }
        if (move.moved()) {
            storm.course.setVelocity(new Vec3(storm.pos.x - before.x, 0.0D, storm.pos.z - before.z));
            // Arrived: somewhere new, and a storm that finds nowhere to go is over.
            return !BossHurricanePath.arrived(storm.pos, storm.target, PATH_EPSILON) || retarget(level, storm);
        }
        if (!bounce(storm, move)) {
            return false;
        }
        // The point is seen through the wall from here on, so the reflected heading still
        // reaches it after exactly as far as was left.
        storm.target = BossHurricanePath.mirrorTarget(storm.pos, storm.target, move.wallX(), move.wallZ());
        storm.course.setVelocity(BossHurricanePath.stepToward(storm.pos, storm.target, storm.look.speed));
        return true;
    }

    /** Comes off the wall a move met. @return false when the storm had no bounce left and is over */
    private static boolean bounce(Storm storm, Move move) {
        if (!storm.course.bounce(move.wallX(), move.wallZ())) {
            return false;
        }
        storm.headingChanged = true;
        return true;
    }

    /** Picks a typhoon's storm a fresh random point with floor under it, within its range of the boss. */
    private static boolean retarget(ServerLevel level, Storm storm) {
        for (int attempt = 0; attempt < TYPHOON_POINT_TRIES; attempt++) {
            Vec3 point = BossHurricanePath.randomPointWithin(level.getRandom(), storm.centre, storm.look.range);
            BlockPos floor = BossFloorUtil.findFloor(level, point.x, storm.centre.y, point.z, storm.look.floorSearch);
            if (floor == null) {
                continue;
            }
            storm.target = new Vec3(point.x, floor.getY() + 1.0D, point.z);
            storm.course.setVelocity(BossHurricanePath.stepToward(storm.pos, storm.target, storm.look.speed));
            storm.headingChanged = true;
            return true;
        }
        return false;
    }

    /**
     * Puts the storm at {@code next}, on whatever floor is there: up a step of one block, down
     * a slope as far as its floor search reaches.
     *
     * <p>A wall is the storm's box meeting blocks at the new spot. Which way it came off is
     * judged an axis at a time - the box moved along X alone, then along Z alone - so a storm
     * running slantwise into a flat wall keeps the half of its heading the wall did not stop,
     * and one meeting a corner on the diagonal alone comes straight back.</p>
     */
    private static Move tryMove(ServerLevel level, Storm storm, Vec3 next) {
        BlockPos floor = BossFloorUtil.findFloor(level, next.x, storm.pos.y, next.z, storm.look.floorSearch);
        if (floor == null) {
            return Move.DEAD;
        }
        Vec3 landed = new Vec3(next.x, floor.getY() + 1.0D, next.z);
        if (level.noBlockCollision(null, box(storm.look, landed))) {
            storm.pos = landed;
            return Move.MOVED;
        }
        boolean wallX = !level.noBlockCollision(null, box(storm.look, new Vec3(next.x, storm.pos.y, storm.pos.z)));
        boolean wallZ = !level.noBlockCollision(null, box(storm.look, new Vec3(storm.pos.x, storm.pos.y, next.z)));
        if (!wallX && !wallZ) {
            wallX = true;
            wallZ = true;
        }
        return new Move(false, false, wallX, wallZ);
    }

    /** The storm's body: as wide as its reach, as tall as its column, standing just off its floor. */
    private static AABB box(Look look, Vec3 pos) {
        return new AABB(pos.x - look.radius, pos.y + BOX_SKIN, pos.z - look.radius,
                pos.x + look.radius, pos.y + look.columnHeight, pos.z + look.radius);
    }
}
