package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The jump: a wind-up on the spot, a flight the boss does not steer, and a slam where it lands.
 *
 * <p>Owned by {@link TeleportPathController}. Once the boss is off the ground the flight is
 * physics rather than a plan - it is ticked above the controller's combat gates and above the
 * pin that holds a stationary boss still, so a leap already in the air comes down and lands
 * even if its target is gone, the fight ended, or the boss was told to stand still halfway.
 * That is also why a cancelled wind-up only drops a leap that has not left the ground.</p>
 *
 * <p>The arc is worked out from the same numbers vanilla falls with, in {@link ArcPhysics}, so
 * the boss reaches where the warning ring was drawn rather than somewhere near it. Nothing here
 * is saved: a server that goes down mid flight puts the boss back on the floor it was standing
 * on.</p>
 */
final class BossLeapRuntime {

    private static final double MARKER_SPACING = 0.7D;
    private static final int MARKER_INTERVAL_TICKS = 4;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** Where the leap being wound up or flown right now is meant to come down. */
    private Vec3 destination;
    /** Phase the leap started in: the slam belongs to the settings that launched it. */
    private int phaseIndex = -1;
    private boolean airborne;
    /** The boss really left the floor, which is what makes a later onGround() a landing. */
    private boolean leftGround;
    private long launchedAt = NOT_SCHEDULED;
    /** Game time the airborne safety net gives up at. */
    private long airTimeoutAt = NOT_SCHEDULED;
    /** Horizontal speed the flight holds, re-applied every tick it stays in the air. */
    private double driveX;
    private double driveZ;
    /**
     * The grace the push was given, frozen with it: a flight belongs to the settings that
     * launched it, and its phase can be deleted from under it while it is still in the air.
     */
    private int launchGraceTicks;

    BossLeapRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Whether the boss is off the ground on a leap, which several of its gates stand aside for. */
    boolean isAirborne() {
        return airborne;
    }

    /** Where this leap is coming down, or null when none is planned or in the air. */
    Vec3 destination() {
        return destination;
    }

    /**
     * Drops a leap that was only planned, keeping one already in the air.
     *
     * <p>A flight is physics and keeps going; only a plan that has not been pushed off yet
     * dies with the wind-up that was thrown away.</p>
     */
    void forgetPlanIfGrounded() {
        if (!airborne) {
            destination = null;
            phaseIndex = -1;
        }
    }

    boolean tryStart(ServerLevel level, TeleportPathData data,
                                 BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.LEAP, phase) || gameTime < boss.abilityScheduleAt(BossAbility.LEAP) || airborne) return false;
        if (!npc.onGround()) {
            // Nothing to push off from. Knocked into the air or standing in a boat, the
            // boss simply tries again in half a second.
            boss.setAbilityScheduleAt(BossAbility.LEAP, gameTime + boss.retryTicks());
            return false;
        }
        LivingEntity target = null;
        if (phase.leap().getMode() == BossPhaseData.LEAP_MODE_TARGET) {
            target = boss.selectAbilityTarget(level, phase.leap().getTargetMode(),
                    phase.leap().getMaxRange(), candidate -> isValidTarget(candidate, phase));
            if (target == null) {
                boss.setAbilityScheduleAt(BossAbility.LEAP, gameTime + boss.retryTicks());
                return false;
            }
        }
        Vec3 planned = resolveDestination(data, phase, target);
        if (planned == null) {
            boss.setAbilityScheduleAt(BossAbility.LEAP, gameTime + boss.retryLongTicks());
            return false;
        }
        this.destination = planned;
        phaseIndex = boss.currentPhaseIndex();
        boss.beginAction(BossAbility.LEAP, phase.leap().getAnimation(),
                phase.leap().getActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled - the windup is measured against the leap animation.
        boss.setAbilityScheduleAt(BossAbility.LEAP, gameTime + phase.leap().getActionDelayTicks() + boss.rageDown(phase.leap().getCooldownTicks()));
        return true;
    }

    /**
     * Line of sight is deliberately not required: clearing a wall someone is hiding behind
     * is the whole point of a jump, and a hook's rule would take that away.
     */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.LEAP)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.leap().getMinRange();
        double max = phase.leap().getMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    /** Where this leap is aimed, already pulled back inside the home leash. */
    Vec3 resolveDestination(TeleportPathData data, BossPhaseData phase, LivingEntity target) {
        Vec3 aimed = switch (phase.leap().getMode()) {
            case BossPhaseData.LEAP_MODE_TARGET -> target == null ? null : target.position();
            case BossPhaseData.LEAP_MODE_FIXED -> new Vec3(phase.leap().getFixedX() + 0.5D,
                    phase.leap().getFixedY(), phase.leap().getFixedZ() + 0.5D);
            case BossPhaseData.LEAP_MODE_ARENA_OFFSET -> new Vec3(boss.homeX() + phase.leap().getOffsetX(),
                    boss.homeY() + phase.leap().getOffsetY(), boss.homeZ() + phase.leap().getOffsetZ());
            // Straight up: the boss comes back down onto the spot it left.
            default -> npc.position();
        };
        return aimed == null ? null : clampToHomeLeash(data, phase, aimed);
    }

    /**
     * A landing outside the leash would end the encounter on the boss' own terms, so the
     * destination is pulled back to just inside the edge before anything is pushed off.
     */
    Vec3 clampToHomeLeash(TeleportPathData data, BossPhaseData phase, Vec3 spot) {
        if (!data.isHomeLeashEnabled()) {
            return spot;
        }
        boolean vertical = data.isHomeLeashVertical();
        double limit = Math.max(0.0D, data.getHomeLeashRadius() - phase.leap().getLeashMargin());
        double dx = spot.x - boss.homeX();
        double dz = spot.z - boss.homeZ();
        double dy = vertical ? spot.y - boss.homeY() : 0.0D;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= limit || distance < 1.0E-4D) {
            return spot;
        }
        double scale = limit / distance;
        return new Vec3(boss.homeX() + dx * scale,
                vertical ? boss.homeY() + dy * scale : spot.y,
                boss.homeZ() + dz * scale);
    }

    /** The push itself, at the end of the windup. */
    void perform(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        // The push, not a timer, is what frees a rooted crouch: from here to touchdown the
        // boss is a thrown object, and even a leap that aborts below has nothing left to
        // stand still for.
        boss.endCastRoot();
        LivingEntity target = boss.pendingTarget(level);
        // The victim may have died or run out of range during the windup. The boss still
        // jumps: the marker already promised that spot, and pulling out looks like a bug.
        Vec3 landing = resolveDestination(data, phase,
                isValidTarget(target, phase) ? target : null);
        if (landing == null) {
            // Whoever it was aimed at is gone, so the spot the warning promised stands.
            landing = this.destination;
        }
        if (landing == null) {
            clear();
            return;
        }

        // An arc that peaks below where it is meant to come down never gets there, so a
        // destination above the boss raises the jump. The ceiling still has the final say.
        int wanted = Mth.clamp((int) Math.ceil(landing.y - npc.getY()) + 1,
                phase.leap().getHeight(), BossPhaseData.MAX_LEAP_HEIGHT);
        int height = availableHeight(level, wanted);
        if (height < 1) {
            // Nowhere to jump to - a boss walled in under a slab would only bump its head
            // and stick. The cooldown has already been spent, so it will try again later.
            clear();
            return;
        }

        double rise = ArcPhysics.speedForHeight(height);
        double drop = Math.max(0.0D, npc.getY() + height - landing.y);
        int flightTicks = Math.max(1, (int) Math.ceil(ArcPhysics.riseTicks(rise))
                + (int) Math.ceil(ArcPhysics.fallTicks(drop)));
        double dx = landing.x - npc.getX();
        double dz = landing.z - npc.getZ();
        double reach = Math.sqrt(dx * dx + dz * dz);
        double speed = reach < 1.0E-4D ? 0.0D : Math.min(phase.leap().getMaxSpeed(),
                reach * phase.leap().getReachCorrection() / flightTicks);
        driveX = reach < 1.0E-4D ? 0.0D : dx / reach * speed;
        driveZ = reach < 1.0E-4D ? 0.0D : dz / reach * speed;

        npc.getNavigation().stop();
        npc.fallDistance = 0.0F;
        npc.setDeltaMovement(driveX, rise, driveZ);
        // Mobs are position-synced, but handing the client the velocity too keeps the arc
        // smooth instead of letting it interpolate a straight line between updates.
        npc.hurtMarked = true;

        this.destination = landing;
        airborne = true;
        leftGround = false;
        launchedAt = gameTime;
        launchGraceTicks = phase.leap().getLaunchGraceTicks();
        airTimeoutAt = gameTime + phase.leap().getMaxAirTicks();
        boss.holdBusyUntil(gameTime + 1);

        phase.leap().getTakeoffSound().play(level, npc.getX(), npc.getY(), npc.getZ(), SoundSource.HOSTILE);
        phase.leap().getTakeoffParticles().emitDust(level, npc.getX(), npc.getY() + 0.1D, npc.getZ(),
                npc.getBbWidth() * 0.5D, 0.05D, npc.getBbWidth() * 0.5D, 0.05D, BossAbilityKind.LEAP);
    }

    /**
     * How far up there is actually room to jump.
     *
     * <p>Counts upward and stops at the first blocked block rather than looking for the
     * highest clear one: an opening above a low ceiling is not somewhere the boss can get
     * to, and aiming for it would just be a head-first bump.</p>
     */
    int availableHeight(ServerLevel level, int desired) {
        int clear = 0;
        for (int height = 1; height <= desired; height++) {
            if (!level.noCollision(npc, npc.getBoundingBox().move(0.0D, height, 0.0D))) {
                break;
            }
            clear = height;
        }
        return clear;
    }

    /**
     * Carries a leap through its windup and its flight.
     *
     * <p>Runs every tick, above everything that could return early: the boss is a thrown
     * object until it touches down, and the landing has to be caught wherever that is.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (airborne) {
            // Nothing may steer the boss mid air, and its own arc must not hurt it.
            npc.getNavigation().stop();
            npc.fallDistance = 0.0F;
            // Holds every other ability off until the boss is back on the floor.
            boss.holdBusyUntil(gameTime + 1);
            holdCourse();

            if (!npc.onGround()) {
                leftGround = true;
            } else if (leftGround) {
                land(level, data, gameTime);
                return;
            } else if (gameTime - launchedAt >= launchGraceTicks) {
                // Never got off the ground - held down, or shoulder-deep in a slab.
                clear();
                return;
            }
            if (gameTime >= airTimeoutAt) {
                // A jump into a pit or into deep water never lands. The slam is dropped
                // rather than fired off somewhere nobody was standing.
                clear();
                return;
            }
        }
        refreshAim(level, data);
        drawTelegraph(level, data, gameTime);
    }

    /**
     * Keeps the flight on the line it was aimed along.
     *
     * <p>Vanilla shaves nearly a tenth off an airborne entity's horizontal speed every
     * tick, which is fine for a shove and useless for an aimed jump: the boss would cover
     * most of the ground in the first few ticks and then crawl the rest, landing a third of
     * the way short. Re-applying the speed instead makes the distance exactly speed times
     * flight time, which is what the arc was solved for. A wall still stops the boss - once
     * it is up against something, pushing harder would only scrape it along the surface.</p>
     */
    void holdCourse() {
        if (npc.horizontalCollision || (driveX == 0.0D && driveZ == 0.0D)) {
            return;
        }
        npc.setDeltaMovement(driveX, npc.getDeltaMovement().y, driveZ);
    }

    /** Touchdown: the boss stops dead, plays its landing animation and slams. */
    void land(ServerLevel level, TeleportPathData data, long gameTime) {
        BossPhaseData phase = phaseOf(data);
        Vec3 impact = npc.position();
        // Re-pins the stationary boss on the spot it came down on, before anything else.
        clear();
        boss.holdBusyUntil(gameTime + boss.postActionLockTicks());
        if (phase == null) {
            return;
        }
        boss.playAnimation(phase.leap().getLandAnimation());
        performImpact(level, phase, impact);
    }

    void performImpact(ServerLevel level, BossPhaseData phase, Vec3 impact) {
        // Started before the hits so the wave leaves at the same moment the damage lands.
        BossAreaVfxScheduler.schedule(level, impact, phase.leap().getVfx(), phase.leap().getImpactRadius(),
                phase.leap().getVfxTicks(), phase.leap().isBlockWave(),
                BossWaveTuning.of(npc, phase.leap().getVfx()));
        int damage = boss.damageUp(phase.leap().getImpactDamage());
        for (LivingEntity target : boss.getTargetsAround(level, impact, phase.leap().getImpactRadius(),
                BossAbilityKind.LEAP)) {
            BossAbilityDamageUtil.hit(target, BossAbilityKind.LEAP, npc, damage,
                    phase.leap().getEffects(), boss.rageUp(phase.leap().getImpactKnockback()),
                    impact.x - target.getX(), impact.z - target.getZ());
        }
        playImpactFeedback(level, phase, impact);
    }

    void playImpactFeedback(ServerLevel level, BossPhaseData phase, Vec3 impact) {
        phase.leap().getLandingSound().play(level, impact.x, impact.y, impact.z, SoundSource.HOSTILE);
        phase.leap().getLandingParticles().emitDust(level, impact.x, impact.y + 0.2D, impact.z,
                0.0D, 0.0D, 0.0D, 0.0D, BossAbilityKind.LEAP);
        int debris = phase.leap().getDebrisCount();
        if (debris <= 0) {
            return;
        }
        BlockPos below = BlockPos.containing(impact.x, impact.y - 0.2D, impact.z);
        BlockState floor = level.getBlockState(below);
        if (!floor.isAir()) {
            // The floor it landed on, kicked up around its feet.
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor),
                    impact.x, impact.y + 0.1D, impact.z, debris, 0.6D, 0.1D, 0.6D, 0.15D);
        }
    }

    /**
     * Keeps a leap that has not been pushed off yet aimed at where its target is now, which
     * is what gives that target the chance to step out of the marked ring before it lands.
     *
     * <p>Kept out of the drawing below because the aim is not decoration: the push itself
     * falls back on this spot when the victim dies inside the windup.</p>
     */
    void refreshAim(ServerLevel level, TeleportPathData data) {
        if (boss.pendingAction() != BossAbility.LEAP || destination == null) {
            return;
        }
        BossPhaseData phase = phaseOf(data);
        if (phase == null) {
            return;
        }
        Vec3 refreshed = resolveDestination(data, phase, boss.pendingTarget(level));
        if (refreshed != null) {
            destination = refreshed;
        }
    }

    /**
     * Paints the ring the slam is going to cover.
     *
     * <p>A jump this heavy landing without warning reads as an unfair death rather than as
     * a mechanic, so the mark is up for the whole windup and the whole flight.</p>
     *
     * <p>The general ability warning owns the windup wherever it is switched on for the
     * leap, and this keeps the flight, which no wind-up mark can cover: by then the ability
     * has gone off and the boss is a thrown object on its way down.</p>
     */
    void drawTelegraph(ServerLevel level, TeleportPathData data, long gameTime) {
        boolean windup = boss.pendingAction() == BossAbility.LEAP;
        if (destination == null || (!airborne && !windup)) {
            return;
        }
        BossPhaseData phase = phaseOf(data);
        if (phase == null || !phase.leap().isTelegraph() || gameTime % MARKER_INTERVAL_TICKS != 0L) {
            return;
        }
        // Never both marks over one ring: whichever of the two is drawing, it draws alone.
        if (windup && data.isTelegraphEnabled()
                && data.isTelegraphAbility(BossAbilityKind.LEAP)) {
            return;
        }
        double radius = phase.leap().getImpactRadius();
        int points = Mth.clamp((int) Math.round(Mth.TWO_PI * radius / MARKER_SPACING), 8, 48);
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            phase.leap().getTrailParticles().emitDust(level,
                    destination.x + Math.cos(angle) * radius,
                    destination.y + 0.15D,
                    destination.z + Math.sin(angle) * radius,
                    0.0D, 0.0D, 0.0D, 0.0D, BossAbilityKind.LEAP);
        }
    }

    /** The phase a leap belongs to, so a phase change mid air cannot rewrite its slam. */
    BossPhaseData phaseOf(TeleportPathData data) {
        return phaseIndex >= 0 && phaseIndex < data.getPhaseCount()
                ? data.getPhase(phaseIndex) : null;
    }

    /** Drops a leap wherever it got to and hands the boss back to the stationary pin. */
    void clear() {
        if (airborne) {
            npc.setDeltaMovement(Vec3.ZERO);
            npc.fallDistance = 0.0F;
            // The pin was let go for the flight, so it has to be moved to wherever this ended.
            boss.rememberCurrentPosition();
        }
        airborne = false;
        leftGround = false;
        driveX = 0.0D;
        driveZ = 0.0D;
        launchGraceTicks = 0;
        destination = null;
        phaseIndex = -1;
        launchedAt = NOT_SCHEDULED;
        airTimeoutAt = NOT_SCHEDULED;
    }
}
