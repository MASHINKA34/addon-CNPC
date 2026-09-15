package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossHurricaneSettings;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossSpinState;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the storms a hurricane let loose: each one a point on the arena floor with a heading,
 * a reach and a lifetime, moved from the level tick long after the cast that made it landed,
 * and the victims each of them has picked up along the way.
 *
 * <p>A storm is not an entity. It has no model, takes no hits and is never saved; it is a record
 * here, the way a geyser's fuse is, and everything it needs is snapshotted on the cast - the
 * enrage bonus included - so a builder editing the ability while the storms are out cannot
 * change what the party is already running from.</p>
 *
 * <p>Whoever a storm catches is held the way the capture holds: pinned every tick, their own
 * movement packets dropped, their client told the ride so it runs the same sums between
 * packets. Unlike the capture there are many of them per boss and the anchor moves, which is
 * why the holds live here rather than there.</p>
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
    /** How long a held player's client is trusted to run the ride on its own before it is told again. */
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final double POSITION_EPSILON_SQUARED = 1.0E-8D;

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
        /** Whoever this storm has picked up, in the order it took them. */
        final List<Held> held = new ArrayList<>();

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

    /** One victim on one storm's ride. */
    static final class Held {
        final UUID victimId;
        final Storm storm;
        final long caughtAt;
        final long liftEndsAt;
        final long endsAt;
        /** Where they were caught: what the lift starts from. */
        final double startY;
        /** Where on the circle round the eye they are, in degrees. */
        double angle;
        /** The view as the storm turns it; only read while the view spins. */
        float yaw;
        long nextHitAt;
        /** When their client was last told the ride, so it is told again at least every so often. */
        long syncedAt = Long.MIN_VALUE;

        private Held(LivingEntity victim, Storm storm, long gameTime) {
            this.victimId = victim.getUUID();
            this.storm = storm;
            this.caughtAt = gameTime;
            this.liftEndsAt = gameTime + storm.look.liftTicks;
            this.endsAt = gameTime + storm.look.holdTicks;
            this.startY = victim.getY();
            this.angle = BossHurricaneHold.startAngle(storm.pos, victim.getX(), victim.getZ());
            this.yaw = victim.getYRot();
            this.nextHitAt = gameTime + storm.look.damageIntervalTicks;
        }
    }

    /** How a move came out: the storm went, or it met a wall on these axes, or it is over. */
    private record Move(boolean moved, boolean dead, boolean wallX, boolean wallZ) {
        private static final Move MOVED = new Move(true, false, false, false);
        private static final Move DEAD = new Move(false, true, false, false);
    }

    private static final TickQueue<Storm> STORMS = new TickQueue<>("boss hurricanes", MAX_PER_TICK);
    /** Everyone on a ride, by whom; one storm at a time, however many pass over them. */
    private static final Map<UUID, Held> HELD = new HashMap<>();
    /** Whoever was let go lately, and the game time from which a storm may take them again. */
    private static final Map<UUID, Long> GRACE = new HashMap<>();

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

    /** Whether somebody is on a storm's ride right now. */
    public static boolean isHeld(UUID victimId) {
        return HELD.containsKey(victimId);
    }

    /** The diagnostic command's line. */
    public static String status(EntityNPCInterface boss) {
        int held = 0;
        for (Held ride : HELD.values()) {
            if (ride.storm.boss == boss) {
                held++;
            }
        }
        return "Hurricanes: " + STORMS.count(storm -> storm.boss == boss) + " storms, " + held + " held";
    }

    /**
     * Drops a held player's movement, the capture's way: their client keeps sending it, and
     * the ride only sticks because none of it is applied.
     *
     * <p>The pitch stays the player's own either way. The yaw does too while the view is not
     * being spun; while it is, the storm owns it and what the client says is ignored.</p>
     */
    public static boolean handleMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        Held held = HELD.get(player.getUUID());
        if (held == null || !player.level().dimension().equals(held.storm.dimension)) {
            return false;
        }
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yaw = packet.getYRot(player.getYRot());
        float pitch = packet.getXRot(player.getXRot());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return false;
        }
        if (packet.hasRotation()) {
            player.setXRot(Mth.wrapDegrees(pitch));
            if (!held.storm.look.spinView) {
                player.setYRot(Mth.wrapDegrees(yaw));
                player.setYHeadRot(player.getYRot());
            }
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return true;
    }

    /**
     * Lets one victim go quietly, where they are: for a player logging out or stepping through
     * a portal, who is no longer there to be thrown.
     */
    public static void releaseVictim(LivingEntity victim) {
        Held held = HELD.get(victim.getUUID());
        if (held == null) {
            return;
        }
        ServerLevel level = victim.level() instanceof ServerLevel server ? server : null;
        release(level, held.storm, held, victim, level == null ? 0L : level.getGameTime(), false);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (!GRACE.isEmpty()) {
            GRACE.values().removeIf(until -> until <= gameTime);
        }
        STORMS.sweep(storm -> storm.dimension.equals(level.dimension()),
                storm -> tickStorm(level, storm, gameTime));
    }

    /** Drops every storm in a level that is going away, and lets go of everyone on them. */
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
     * nothing more once the boss has stopped. Whoever was riding one is let go where they are.
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
        catchVictims(level, storm, gameTime);
        holdVictims(level, storm, gameTime);
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
        for (Held held : storm.held.toArray(new Held[0])) {
            LivingEntity victim = level != null && level.getEntity(held.victimId) instanceof LivingEntity found
                    ? found : null;
            release(level, storm, held, victim, gameTime, throwOut);
        }
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

    /** Picks up everyone standing in the storm's reach who may be picked up, up to its victims. */
    private static void catchVictims(ServerLevel level, Storm storm, long gameTime) {
        Look look = storm.look;
        if (!BossHurricaneHold.hasRoom(storm.held.size(), look.maxVictims)) {
            return;
        }
        TeleportPathController controller = controllerOf(storm.boss);
        if (controller == null) {
            return;
        }
        TeleportPathData data = controller.settings();
        AABB reach = new AABB(storm.pos.x - look.radius, storm.pos.y - 1.0D, storm.pos.z - look.radius,
                storm.pos.x + look.radius, storm.pos.y + look.columnHeight, storm.pos.z + look.radius);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, reach,
                candidate -> candidate != storm.boss && candidate.isAlive())) {
            if (!BossHurricaneHold.hasRoom(storm.held.size(), look.maxVictims)) {
                return;
            }
            if (canCatch(controller, data, storm, candidate, gameTime)) {
                catchVictim(level, storm, candidate, gameTime);
            }
        }
    }

    /**
     * Whether one candidate is the storm's to take: inside its reach, the rule every area hit
     * shares - an immune npc, an ally, a boss hidden by its totems, a kind this boss aims no
     * ability at, a totem this ability may not break - and not already in somebody's grip,
     * another storm's included, nor inside the grace they were let go with.
     */
    private static boolean canCatch(TeleportPathController controller, TeleportPathData data, Storm storm,
                                    LivingEntity candidate, long gameTime) {
        double dx = candidate.getX() - storm.pos.x;
        double dz = candidate.getZ() - storm.pos.z;
        double reach = storm.look.radius + candidate.getBbWidth() * 0.5D;
        if (dx * dx + dz * dz > reach * reach) {
            return false;
        }
        UUID id = candidate.getUUID();
        if (HELD.containsKey(id) || BossCaptureManager.isCaptured(id) || BossCocoonManager.isCocooned(id)
                || BossHurricaneHold.inGrace(GRACE.get(id), gameTime)) {
            return false;
        }
        return controller.isAbilityTarget(candidate, BossAbilityKind.HURRICANE)
                && controller.matchesAbilityTargetKind(candidate, data)
                && !BossMechanicUtil.hiddenByTotems(candidate)
                && !BossAbilityDamageUtil.passesBy(candidate, BossAbilityKind.HURRICANE);
    }

    private static void catchVictim(ServerLevel level, Storm storm, LivingEntity victim, long gameTime) {
        Held held = new Held(victim, storm, gameTime);
        HELD.put(held.victimId, held);
        storm.held.add(held);
        if (victim instanceof ServerPlayer player) {
            syncState(player, storm, held, gameTime, true);
        }
    }

    /** Keeps everyone on the ride where the ride has got to, and lets go of whoever is gone. */
    private static void holdVictims(ServerLevel level, Storm storm, long gameTime) {
        for (Held held : storm.held.toArray(new Held[0])) {
            LivingEntity victim = level.getEntity(held.victimId) instanceof LivingEntity found ? found : null;
            if (!isRideable(victim, storm)) {
                // Dead, gone, or no longer somebody a storm may hold: let go without a word.
                release(level, storm, held, victim, gameTime, false);
                continue;
            }
            hold(level, storm, held, victim, gameTime);
        }
    }

    private static boolean isRideable(LivingEntity victim, Storm storm) {
        return victim != null && !victim.isRemoved() && victim.isAlive()
                && victim.level().dimension().equals(storm.dimension)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /**
     * One tick of the ride: the lift, the circle round the eye and the turn of the view, the
     * capture's way - position set, movement zeroed, fall wiped, the tracker told.
     */
    private static void hold(ServerLevel level, Storm storm, Held held, LivingEntity victim, long gameTime) {
        Look look = storm.look;
        held.angle = BossHurricaneHold.advanceAngle(held.angle, look.spinDegrees);
        double y = BossHurricaneHold.liftY(held.startY, storm.pos.y + look.liftHeight, held.caughtAt,
                held.liftEndsAt, gameTime);
        Vec3 desired = BossHurricaneHold.orbitPoint(storm.pos.x, y, storm.pos.z, look.orbitRadius, held.angle);
        if (!level.noBlockCollision(victim, victim.getBoundingBox().move(desired.subtract(victim.position())))) {
            // The ride's point is inside a block - the storm is brushing a pillar - so the victim
            // keeps the eye itself for this tick, and their own spot when even that is blocked.
            Vec3 eye = new Vec3(storm.pos.x, y, storm.pos.z);
            desired = level.noBlockCollision(victim, victim.getBoundingBox().move(eye.subtract(victim.position())))
                    ? eye : victim.position();
        }
        boolean moved = victim.position().distanceToSqr(desired) > POSITION_EPSILON_SQUARED;
        boolean hadMotion = victim.getDeltaMovement().lengthSqr() > POSITION_EPSILON_SQUARED;
        if (moved) {
            victim.setPos(desired);
        }
        if (look.spinView) {
            held.yaw = BossHurricaneHold.spinYaw(held.yaw, look.spinDegrees);
            victim.setYRot(held.yaw);
            victim.setYHeadRot(held.yaw);
            // A mob re-aims its head off its body every tick, so the body is turned with it.
            if (victim instanceof Mob mob) {
                mob.yBodyRot = held.yaw;
            }
        }
        // Gravity still pulls on the victim during its own tick; the pin simply runs after it
        // every time, so what the trackers broadcast is always the ride's position.
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        victim.hurtMarked |= moved || hadMotion;
        if (victim instanceof ServerPlayer player) {
            player.setKnownMovement(Vec3.ZERO);
            if (storm.headingChanged || gameTime - held.syncedAt >= SYNC_INTERVAL_TICKS) {
                syncState(player, storm, held, gameTime, true);
            }
        }
        // A path left running would re-apply movement on the victim's own next tick, so it is
        // cut here for the same reason a pinned totem has its navigation stopped.
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    /**
     * Takes one victim off the ride.
     *
     * @param victim   who, or null when they are no longer anywhere to be found
     * @param throwOut whether they go out thrown clear of the eye, or are simply let go where
     *                 they are
     */
    private static void release(ServerLevel level, Storm storm, Held held, LivingEntity victim, long gameTime,
                                boolean throwOut) {
        if (!HELD.remove(held.victimId, held)) {
            return;
        }
        storm.held.remove(held);
        if (victim == null) {
            return;
        }
        // Let go where they are, with the nudge the capture gives so the client sees a drop
        // begin rather than a body hanging where it was pinned.
        victim.setDeltaMovement(0.0D, -0.05D, 0.0D);
        victim.fallDistance = 0.0F;
        victim.hurtMarked = true;
        if (victim instanceof ServerPlayer player) {
            player.setKnownMovement(Vec3.ZERO);
            NetworkWrapper.send(player, PacketSyncBossSpinState.released());
        }
    }

    /** Tells a held player's client the ride, as it stands this tick. */
    private static void syncState(ServerPlayer player, Storm storm, Held held, long gameTime, boolean active) {
        held.syncedAt = gameTime;
        if (!active) {
            NetworkWrapper.send(player, PacketSyncBossSpinState.released());
            return;
        }
        Look look = storm.look;
        Vec3 velocity = storm.course.velocity();
        NetworkWrapper.send(player, new PacketSyncBossSpinState(true, storm.pos.x, storm.pos.z, velocity.x,
                velocity.z, held.startY, storm.pos.y + look.liftHeight, held.caughtAt, held.liftEndsAt,
                look.orbitRadius, held.angle, look.spinDegrees, look.spinView, held.endsAt));
    }

    private static TeleportPathController controllerOf(EntityNPCInterface boss) {
        return boss instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }
}
