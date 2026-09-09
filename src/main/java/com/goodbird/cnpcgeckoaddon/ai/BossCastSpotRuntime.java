package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;
import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.POST_ACTION_LOCK_TICKS;
import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The spot an ability is cast from: the journey there, and the hold that keeps the boss on it.
 *
 * <p>Owned by {@link TeleportPathController}. A boss that jumps about the arena and then
 * sweeps beams round itself drags the beams after it, which is not a mechanic anyone can
 * learn. So the rotation asks here before it starts an ability: one with a spot is taken
 * there first - by a blink, or on foot with a blink as the fallback - and started on
 * arrival, and afterwards the boss is pinned to the spot the way its totems pin it, for as
 * long as the spot's stay rule says. The cast itself is the ordinary one: the same starter,
 * the same wind-up, the same cast root.</p>
 *
 * <p>Nothing here is saved. A server that goes down mid journey puts the boss back on its
 * rotation, and every way out of a fight - a phase change, a reset, a death - drops the
 * journey and the hold together.</p>
 */
final class BossCastSpotRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** How close the walk has to get before the boss counts as standing on the spot. */
    private static final double ARRIVAL_DISTANCE = 1.0D;
    /** How far below a spot the floor may be before it counts as a spot over nothing. */
    private static final int GROUND_SEARCH_BLOCKS = 3;
    /** How often the walk re-asks for its path; the path is cached for the same target in between. */
    private static final int REPATH_INTERVAL_TICKS = 4;
    /**
     * How long an ability that refused to start on its spot waits before it is taken there
     * again. Its own retry is a matter of ticks, which for a spot means a boss blinking to
     * its pedestal every half second to find nobody in reach of it.
     */
    private static final int SPOT_RETRY_TICKS = 100;
    /** How far ahead a boss held to a fixed yaw looks; only sets the gaze, never a reach. */
    private static final double FIXED_LOOK_DISTANCE = 8.0D;
    private static final float SNAP_DEGREES = 360.0F;

    /** What the hold on the spot is waiting for, once the boss stands on it. */
    private enum Hold {
        /** Nothing: the boss is free to walk off. */
        NONE,
        /** The cast it went there for, which is winding up. */
        WINDUP,
        /** The stay rule: a timer, or the effect the cast left behind. */
        AFTER
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final BossHookRuntime hook;
    private final BossLeapRuntime leap;
    private final BossHuntRuntime hunt;

    /** The ability whose spot the boss is walking to, or NONE. */
    private BossAbility travelling = BossAbility.NONE;
    private Vec3 destination;
    /** Game time the walk gives up and blinks at. */
    private long travelDeadline = NOT_SCHEDULED;
    private long nextRepathAt = NOT_SCHEDULED;

    /** The ability whose spot the boss is standing on, or NONE. */
    private BossAbility occupied = BossAbility.NONE;
    private Hold hold = Hold.NONE;
    /** The spot's rules, frozen on arrival so an edit mid-cast cannot change them under the boss. */
    private int stayMode;
    private int stayTicks;
    private int yawMode;
    private float yaw;
    /** Game time the hold after a cast lets go at, before the stay rule is even asked. */
    private long holdUntil = NOT_SCHEDULED;

    /** The last spot each ability was found unusable at, so the log gets one line per spot. */
    private final Map<BossAbility, BlockPos> reportedUnsafe = new EnumMap<>(BossAbility.class);

    BossCastSpotRuntime(TeleportPathController boss, EntityNPCInterface npc, BossHookRuntime hook,
                        BossLeapRuntime leap, BossHuntRuntime hunt) {
        this.boss = boss;
        this.npc = npc;
        this.hook = hook;
        this.leap = leap;
        this.hunt = hunt;
    }

    /** Whether the boss is on its way to a spot right now. */
    boolean isTravelling() {
        return travelling != BossAbility.NONE;
    }

    /** Whether the boss is held on a spot right now, winding up or staying. */
    boolean isHolding() {
        return hold != Hold.NONE;
    }

    /**
     * Whether the hold keeps this ability from starting at all: the two that would carry
     * the boss off the spot it is holding. Everything else may start from the spot, or set
     * off for a spot of its own.
     */
    boolean blocks(BossAbility ability) {
        return isHolding() && (ability == BossAbility.LEAP || ability == BossAbility.HUNT);
    }

    /**
     * Takes the boss to this ability's spot if it has one and the ability is due, or starts
     * it there at once when the boss already stands on it.
     *
     * <p>Asked by the rotation before the ability's own starter, and quiet in every case
     * that is not its own: no spot, not due, held by totems - so the starter runs from
     * where the boss stands, exactly as it did before spots existed.</p>
     *
     * @return true when the rotation's turn was spent here: a journey began, or the ability
     *         started on its spot
     */
    boolean tryTravel(ServerLevel level, TeleportPathData data, BossPhaseData phase,
                      BossAbility ability, long gameTime) {
        BossCastSpot spot = ability.castSpot(phase);
        if (spot == null || !spot.isSet() || !ability.isEnabledIn(phase)
                || gameTime < boss.abilityScheduleAt(ability)) {
            return false;
        }
        // Nailed down by its totems, the boss casts from where it stands: the hold is exactly
        // what keeps it from going anywhere, however it would get there.
        if (boss.isTotemHeld()) {
            return false;
        }
        // The effect the last cast left is still running: a second sweep on top of the first
        // would double the hits, and the beam's own starter refuses that anyway. Looked at
        // again shortly, the way a starter that found a sweep already turning does.
        if (isRunning(ability, gameTime)) {
            boss.setAbilityScheduleAt(ability, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        Vec3 target = resolve(level, spot, ability);
        if (target == null) {
            return false;
        }
        if (npc.distanceToSqr(target) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE) {
            return arrive(level, data, phase, ability, gameTime);
        }
        // A new journey drops whatever hold the last cast left: the boss was told to be
        // somewhere else now.
        release();
        // A stationary boss cannot walk anywhere, so its walk is a blink.
        if (spot.getMode() == BossCastSpot.MODE_WALK && !data.isStationary()) {
            travelling = ability;
            destination = target;
            travelDeadline = gameTime + spot.getTravelTimeoutTicks();
            nextRepathAt = NOT_SCHEDULED;
            walk(gameTime);
            return true;
        }
        if (!BossTeleportUtil.teleport(level, npc, boss, target, data.shouldPlaySound(), ability + " cast spot")) {
            // Vetoed by a script: the boss casts from where it stands, the way a path hop
            // that was vetoed leaves it where it is.
            return false;
        }
        return arrive(level, data, phase, ability, gameTime);
    }

    /**
     * Carries a walk one tick further, and finishes it when it gets there or runs out of time.
     *
     * @return true while the boss is on its way, which is the rotation's turn spent
     */
    boolean tickTravel(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!isTravelling()) {
            return false;
        }
        BossAbility ability = travelling;
        // Held, silenced or turned immune on the way: the journey is over, and the rotation
        // decides next tick what the boss may do from where it stands.
        if (boss.isTotemHeld() || boss.abilitiesSilenced() || boss.isInvulnerable()) {
            clearTravel();
            return true;
        }
        if (npc.distanceToSqr(destination) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE) {
            arrive(level, data, phase, ability, gameTime);
            return true;
        }
        if (gameTime >= travelDeadline) {
            // Walled in, or pathing round the long way: the boss blinks the rest. A blink a
            // script vetoed leaves it casting from wherever the walk got it to.
            Vec3 target = destination;
            clearTravel();
            if (BossTeleportUtil.teleport(level, npc, boss, target, data.shouldPlaySound(), ability + " cast spot")) {
                arrive(level, data, phase, ability, gameTime);
            } else {
                boss.startAbility(ability, level, data, phase, gameTime);
            }
            return true;
        }
        walk(gameTime);
        return true;
    }

    /**
     * The boss is on the spot: it is pinned there, turned the way the spot asks, and the
     * ability it came for is started.
     *
     * @return whether the ability started; one that refused is not fetched back for a while
     */
    private boolean arrive(ServerLevel level, TeleportPathData data, BossPhaseData phase,
                           BossAbility ability, long gameTime) {
        clearTravel();
        release();
        npc.getNavigation().stop();
        Vec3 movement = npc.getDeltaMovement();
        npc.setDeltaMovement(0.0D, movement.y, 0.0D);
        boss.rememberCurrentPosition();
        BossCastSpot spot = ability.castSpot(phase);
        occupied = ability;
        yawMode = spot.getYawMode();
        yaw = spot.getYaw();
        stayMode = spot.getStayMode();
        stayTicks = spot.getStayTicks();
        hold = Hold.WINDUP;
        holdUntil = NOT_SCHEDULED;
        // Turned before the ability aims, not after: a line strike laid along the gaze has to
        // read the yaw the spot asks for, not the one the walk left the boss with.
        boss.faceCombatTarget(data);
        if (boss.startAbility(ability, level, data, phase, gameTime)) {
            return true;
        }
        // Refused on the spot - nobody in reach of it, say. The boss is let go, and this
        // ability is not taken back here for a while; its own retry alone would have the
        // boss blinking to its pedestal every half second to find the same nobody.
        release();
        long current = boss.abilityScheduleAt(ability);
        if (current != NOT_SCHEDULED) {
            boss.setAbilityScheduleAt(ability, Math.max(current, gameTime + SPOT_RETRY_TICKS));
        }
        return false;
    }

    /**
     * What the cast leaves the hold with once it lands: a timer, the effect it started, or
     * nothing beyond the after-pause.
     */
    void onActionPerformed(BossAbility action, long gameTime) {
        if (hold != Hold.WINDUP || action != occupied) {
            return;
        }
        // A leap or a hunt is the boss leaving its spot: the push and the chase are the
        // ability, and a hold that outlived them would cancel what the boss went there for.
        if (action == BossAbility.LEAP || action == BossAbility.HUNT) {
            release();
            return;
        }
        hold = Hold.AFTER;
        // Never shorter than the after-pause the cast root keeps, so an instant cast does
        // not let go on the very tick it lands.
        int ticks = stayMode == BossCastSpot.STAY_TICKS ? Math.max(stayTicks, POST_ACTION_LOCK_TICKS)
                : POST_ACTION_LOCK_TICKS;
        holdUntil = gameTime + ticks;
    }

    /**
     * Lets a hold go once nothing is holding it any more.
     *
     * <p>Mid wind-up the pending action is what holds the spot, and a wind-up that was called
     * off - a dodge, a stun, a phase change - lets go with it, the way the cast root does.
     * Afterwards the stay rule decides: the timer, or the effect the cast left behind.</p>
     */
    void tickHold(long gameTime, BossAbility pendingAction) {
        switch (hold) {
            case NONE -> {
            }
            case WINDUP -> {
                if (pendingAction == BossAbility.NONE) {
                    release();
                }
            }
            case AFTER -> {
                if (gameTime < holdUntil) {
                    return;
                }
                if (stayMode == BossCastSpot.STAY_ACTIVE && isRunning(occupied, gameTime)) {
                    return;
                }
                release();
            }
        }
    }

    /**
     * Turns a boss on its spot to the yaw the spot asks for, snapped rather than eased: the
     * turn is made once on arrival and only kept from drifting after that.
     *
     * @return true when the spot owns the facing this tick
     */
    boolean faceFixedYaw() {
        if (hold == Hold.NONE || yawMode != BossCastSpot.YAW_FIXED) {
            return false;
        }
        double radians = yaw * Mth.DEG_TO_RAD;
        boss.turnTowardAxis(new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians)), FIXED_LOOK_DISTANCE, SNAP_DEGREES);
        return true;
    }

    /**
     * Whether the effect this ability left behind on its last cast is still going.
     *
     * <p>Two things read it: the stay rule that keeps the boss on its spot "while it lasts",
     * and the journey, which does not set off for a cast whose last effect is still
     * running. The instant ones - a slam, a shot, a swing, a corridor, a rolled stone, the
     * take-cover strike, a summon - leave nothing behind that the boss is still doing, so
     * for them the stay ends with the after-pause.</p>
     */
    private boolean isRunning(BossAbility ability, long gameTime) {
        return switch (ability) {
            case HOOK -> hook.isPulling();
            case CAPTURE -> BossCaptureManager.hasCaptureForBoss(npc.getUUID());
            case LEAP -> leap.isAirborne();
            case GEYSER -> BossGeyserScheduler.hasPending(npc);
            case BOULDER_RAIN -> BossBoulderRainScheduler.hasPending(npc);
            case TETHER -> BossTetherManager.countForBoss(npc.getUUID()) > 0;
            case GRAVITY -> BossGravityScheduler.remainingTicks(npc, gameTime) > 0L;
            case MARK -> BossMarkScheduler.hasPending(npc);
            case HUNT -> hunt.isHunting();
            case BEAM -> BossBeamScheduler.isSweeping(npc);
            case COCOON -> BossCocoonManager.countForBoss(npc.getUUID()) > 0;
            default -> false;
        };
    }

    /**
     * Where the spot is in the world, lifted onto the floor the way a path point is, or null
     * for a spot the boss cannot stand on: inside a block, outside the loaded world, or over
     * a drop.
     *
     * <p>A spot that cannot be stood on is reported once - per spot, not per attempt: the
     * ability comes round every cooldown, and a line every cooldown is spam - and the cast
     * goes ahead from wherever the boss is.</p>
     */
    private Vec3 resolve(ServerLevel level, BossCastSpot spot, BossAbility ability) {
        Vec3 asked = spot.getCoordinateMode() == BossCastSpot.COORDINATE_ABSOLUTE
                ? new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D)
                : new Vec3(boss.homeX() + spot.getX(), boss.homeY() + spot.getY(), boss.homeZ() + spot.getZ());
        Vec3 safe = BossTeleportUtil.findSafeDestination(level, npc, asked.x, asked.y, asked.z);
        if (safe != null && hasGroundBelow(level, safe)) {
            reportedUnsafe.remove(ability);
            return safe;
        }
        BlockPos where = BlockPos.containing(asked);
        if (!where.equals(reportedUnsafe.get(ability))) {
            reportedUnsafe.put(ability, where);
            LOGGER.warn("Boss {} cannot stand on its {} cast spot at {}; it casts where it stands instead",
                    npc.getName().getString(), ability, where.toShortString());
        }
        return null;
    }

    /** Whether there is floor within reach under a spot; a spot over a pit is a fall, not a stand. */
    private boolean hasGroundBelow(ServerLevel level, Vec3 spot) {
        AABB feet = npc.getBoundingBox().move(spot.x - npc.getX(), spot.y - npc.getY(), spot.z - npc.getZ());
        for (int drop = 1; drop <= GROUND_SEARCH_BLOCKS; drop++) {
            if (!level.noCollision(npc, feet.move(0.0D, -drop, 0.0D))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the boss walking toward its spot.
     *
     * <p>Re-asked every few ticks rather than once: vanilla hands the same path back for
     * the same target, so this costs nothing while the path holds, and puts the boss back on
     * its way when something else - a script, a goal the chase redirect does not cover -
     * has steered it off since.</p>
     */
    private void walk(long gameTime) {
        if (nextRepathAt != NOT_SCHEDULED && gameTime < nextRepathAt) {
            return;
        }
        nextRepathAt = gameTime + REPATH_INTERVAL_TICKS;
        double speed = Mth.clamp(npc.ais.getWalkingSpeed() / 5.0D, 0.5D, 2.0D);
        npc.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
    }

    private void clearTravel() {
        if (isTravelling()) {
            npc.getNavigation().stop();
        }
        travelling = BossAbility.NONE;
        destination = null;
        travelDeadline = NOT_SCHEDULED;
        nextRepathAt = NOT_SCHEDULED;
    }

    private void release() {
        if (hold == Hold.NONE) {
            return;
        }
        hold = Hold.NONE;
        occupied = BossAbility.NONE;
        holdUntil = NOT_SCHEDULED;
        // Hand the pin back to the walk from the spot, the way the cast root does.
        boss.rememberCurrentPosition();
    }

    /** Drops a journey only; the hold stays, for a stagger that pins the boss anyway. */
    void abortTravel() {
        clearTravel();
    }

    /** Drops the journey and the hold both: a phase change, a reset, a death, a lost target. */
    void clear() {
        clearTravel();
        release();
    }

    /** Forgets which spots were reported, for a boss starting its life over. */
    void forgetReports() {
        reportedUnsafe.clear();
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        if (isTravelling()) {
            return "Cast spot: walking to " + travelling + " spot, blinks in "
                    + Math.max(0L, travelDeadline - gameTime) + " ticks";
        }
        if (hold == Hold.NONE) {
            return "Cast spot: free";
        }
        if (hold == Hold.WINDUP) {
            return "Cast spot: holding " + occupied + " spot through the wind-up";
        }
        String stay = switch (stayMode) {
            case BossCastSpot.STAY_ACTIVE -> "while it lasts";
            case BossCastSpot.STAY_TICKS -> "for " + Math.max(0L, holdUntil - gameTime) + " more ticks";
            default -> "through the after-pause";
        };
        return "Cast spot: holding " + occupied + " spot " + stay;
    }
}
