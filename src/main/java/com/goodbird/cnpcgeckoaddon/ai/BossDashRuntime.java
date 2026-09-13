package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The dash: a wind-up on the spot with its corridor marked, then a run straight down it that
 * ends on the first victim it meets, against a wall, on the chain of a pair tether, or at the
 * end of the lane.
 *
 * <p>Owned by {@link TeleportPathController}. Which way the boss runs is settled as it
 * commits, the way the line strike's corridor is: the warning on the floor promises one lane,
 * and a boss that swung round after a sidestepping player would turn the warning into a lie.
 * The target, where there is one, is only there to point the lane.</p>
 *
 * <p>The run itself is physics, the way the leap's flight is: the boss is given its speed
 * down the lane every tick and the game moves it, so floors, steps, drops and walls are the
 * game's own. It is ticked above the controller's gates and above the stationary pin, and
 * what it covered is read off where the boss really got to rather than off where it was sent.
 * Nothing here is saved: a server that goes down mid run leaves the boss where it stopped.</p>
 */
final class BossDashRuntime {

    /** The turn left over from the eased wind-up, finished on the tick the run starts. */
    private static final float SNAP_DEGREES = 360.0F;
    /** A lane the home leash cuts shorter than this is not worth running. */
    static final double MIN_REACH = 1.0D;
    /** How close to the end of its lane a run counts as having got there. */
    private static final double ARRIVAL_SLACK = 0.05D;
    /**
     * A tick's progress below this share of the step it was given, with the boss up against
     * something, is a wall stopping it rather than a corner it is scraping past.
     */
    static final double WALL_PROGRESS_SHARE = 0.25D;
    /**
     * How far down the lane past the first victim somebody still counts as met on the same
     * step: two people standing side by side both take a run that stops on the first of them,
     * one a pace behind the other does not. The throw's collision slice.
     */
    static final double CONTACT_SLICE = 0.4D;
    /** The most the run steers back toward its line in one tick, in blocks. */
    private static final double MAX_STEER = 0.5D;
    /** Extra room round the swept box, so a boss that drifted off its line still finds the lane's edge. */
    private static final double SWEEP_SLACK = 1.0D;
    /** A move this much longer than a step in one tick was a carry or a teleport, not the run. */
    private static final double TELEPORT_SLACK = 2.0D;
    /** How far above the boss' head or below its feet a chain still counts as across its path. */
    private static final double CHAIN_HEIGHT_SLACK = 0.5D;
    private static final int SLAM_VFX_TICKS = 20;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    private boolean running;
    /** The committed line, flat and unit length, for the run in progress. */
    private Vec3 axis;
    /** The corridor measured from where the run started. */
    private Lane lane;
    /** Where the boss stood at the end of the last tick of the run. */
    private Vec3 last;
    /** How far down the lane this run may go: its length, or less where the home leash cuts it. */
    private double reach;
    /** Blocks a tick the run was set up with. */
    private double step;
    /** Blocks the boss was sent this tick, which is less than a step on the last stretch. */
    private double sent;
    /** Game time the safety net gives up at. */
    private long endsAt = NOT_SCHEDULED;
    /** Phase the run started in: what it hits for belongs to the settings that launched it. */
    private int phaseIndex = -1;
    /** Everyone this run has already hit, so a run that carries on hits each of them once. */
    private final Set<UUID> struck = new HashSet<>();
    /** A run that ended over a drop still owns the fall it left the boss in. */
    private boolean fallGuard;

    BossDashRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Whether the boss is running right now, which the pin and the busy gate stand aside for. */
    boolean isRunning() {
        return running;
    }

    /** The line the run in progress keeps to, or null between runs. */
    Vec3 axis() {
        return axis;
    }

    /** Whether a fall the boss takes right now is the run's: in the run, or dropping out of the end of one. */
    boolean guardsOwnFall() {
        return running || fallGuard;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.DASH, phase) || gameTime < boss.abilityScheduleAt(BossAbility.DASH)
                || running) {
            return false;
        }
        if (!npc.onGround()) {
            // Nothing to push off from: a boss knocked into the air tries again shortly.
            boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + boss.retryTicks());
            return false;
        }
        LivingEntity target = null;
        Vec3 committed;
        if (phase.dash().getDirection() == BossPhaseData.DASH_DIRECTION_TARGET) {
            target = boss.selectAbilityTarget(level, phase.dash().getTargetMode(), phase.dash().getLength(),
                    candidate -> isValidTarget(candidate, phase));
            if (target == null) {
                boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + boss.retryTicks());
                return false;
            }
            committed = axisToward(target);
        } else {
            committed = boss.facingAxis();
        }
        double allowed = reachFrom(data, phase, npc.position(), committed);
        if (allowed < MIN_REACH) {
            // Up against the edge of its leash with the lane pointing out: the run would end
            // before it began, so the boss looks again once it has moved.
            boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + boss.retryLongTicks());
            return false;
        }
        // An empty lane is no reason to charge, the line strike's rule: the run would cross bare
        // floor and spend a whole cooldown doing it. A lane aimed at somebody has them in it.
        if (target == null && !anyoneInLane(level, phase, committed, allowed)) {
            boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + boss.retryTicks());
            return false;
        }
        boss.commitAxis(committed);
        boss.beginAction(BossAbility.DASH, phase.dash().getAnimation(),
                phase.dash().getActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.DASH, gameTime + phase.dash().getActionDelayTicks()
                + boss.rageDown(phase.dash().getCooldownTicks()));
        return true;
    }

    /**
     * Whether one candidate is worth running at: in reach of the lane's length, measured flat,
     * and inside the band the run hits in - from the boss' feet up - so the run aimed at them
     * really does go through them rather than over or under them.
     */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.DASH)) {
            return false;
        }
        if (target.getY() + target.getBbHeight() <= npc.getY()
                || target.getY() >= npc.getY() + phase.dash().getHeight()) {
            return false;
        }
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double length = phase.dash().getLength();
        return dx * dx + dz * dz <= length * length;
    }

    /** Whether anybody the run could hit stands in the lane it would run from here, end to end. */
    private boolean anyoneInLane(ServerLevel level, BossPhaseData phase, Vec3 committed, double allowed) {
        Vec3 start = npc.position();
        Lane planned = new Lane(start.x, start.z, committed.x, committed.z, phase.dash().getWidth() * 0.5D);
        double bossHalf = npc.getBbWidth() * 0.5D;
        double top = start.y + phase.dash().getHeight();
        double side = planned.halfWidth() + bossHalf + SWEEP_SLACK;
        AABB box = new AABB(start, start.add(committed.scale(allowed))).inflate(side, 0.0D, side)
                .expandTowards(0.0D, phase.dash().getHeight(), 0.0D);
        return !level.getEntitiesOfClass(LivingEntity.class, box, target -> target != npc && target.isAlive()
                && boss.isAbilityTarget(target, BossAbilityKind.DASH)
                && planned.covers(0.0D, allowed, bossHalf + target.getBbWidth() * 0.5D, start.y, top,
                target.getX(), target.getZ(), target.getY(), target.getY() + target.getBbHeight())).isEmpty();
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
        Vec3 committed = boss.committedAxis();
        if (committed == null) {
            return;
        }
        // Whatever the eased turn left is finished now, so the boss sets off facing its lane.
        boss.turnTowardAxis(committed, phase.dash().getLength(), SNAP_DEGREES);
        Vec3 start = npc.position();
        double allowed = reachFrom(data, phase, start, committed);
        if (allowed < MIN_REACH) {
            // A wind-up left free to walk carried the boss to the edge of its leash: there is
            // no lane left to run, and the cooldown it spent comes round as usual.
            return;
        }
        running = true;
        axis = committed;
        lane = new Lane(start.x, start.z, committed.x, committed.z, phase.dash().getWidth() * 0.5D);
        last = start;
        reach = allowed;
        step = phase.dash().getSpeed() / 10.0D;
        endsAt = gameTime + timeoutTicks(phase.dash().getLength(), phase.dash().getSpeed());
        phaseIndex = boss.currentPhaseIndex();
        struck.clear();
        fallGuard = false;
        npc.getNavigation().stop();
        npc.fallDistance = 0.0F;
        drive(start);
        boss.holdBusyUntil(gameTime + 1);

        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 1.2F, 1.4F);
        level.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 0.1D, npc.getZ(), 12,
                npc.getBbWidth() * 0.5D, 0.05D, npc.getBbWidth() * 0.5D, 0.02D);
    }

    /**
     * Carries a run one tick further.
     *
     * <p>Runs every tick, above everything that could return early: the boss is moving under
     * its own speed until the run is over, and what stops it has to be caught wherever that
     * is. The order is what a charging body meets first - whoever is standing in the stretch it
     * just covered up to a tether's chain strung across it, then the chain, the wall it is
     * pressed against, and last the end of its lane.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!running) {
            guardLanding();
            return;
        }
        // Nothing may steer the boss while it runs, and a ledge it runs off must not hurt it.
        npc.getNavigation().stop();
        npc.fallDistance = 0.0F;
        // Holds every other ability off until the run is over.
        boss.holdBusyUntil(gameTime + 1);

        BossPhaseData phase = phaseOf(data);
        Vec3 now = npc.position();
        double movedX = now.x - last.x;
        double movedZ = now.z - last.z;
        double longest = step + TELEPORT_SLACK;
        if (phase == null || movedX * movedX + movedZ * movedZ > longest * longest) {
            // Its phase was deleted from under it, or something moved the boss further than a
            // run could in a tick - a carry, a script's teleport: this is not the run any more.
            finish(gameTime);
            return;
        }
        double fromAlong = lane.along(last.x, last.z);
        double toAlong = lane.along(now.x, now.z);
        // Where the stretch crossed a tether's chain, if it did: whoever stood before the chain
        // was met first and takes the hit, and nobody beyond it is reached at all.
        double chainAt = phase.dash().isChainStun() ? chainCrossing(level, now, fromAlong, toAlong) : Double.NaN;
        boolean chained = !Double.isNaN(chainAt);
        if (runOver(level, phase, now, fromAlong, chained ? Math.min(toAlong, chainAt) : toAlong)
                && phase.dash().isStopOnHit()) {
            finish(gameTime);
            return;
        }
        if (chained) {
            breakOnChain(level, phase, now, gameTime);
            return;
        }
        if (stoppedByWall(npc.horizontalCollision, toAlong - fromAlong, sent)) {
            hitWall(level, data, phase, gameTime);
            return;
        }
        if (toAlong >= reach - ARRIVAL_SLACK || gameTime >= endsAt) {
            // The whole lane, or as much as the safety net allows: a quiet stop and the usual pause.
            finish(gameTime);
            return;
        }
        last = now;
        drive(now);
        kickUpFloor(level);
    }

    /**
     * Gives the boss its speed down the lane for the coming tick.
     *
     * <p>Re-applied every tick for the leap's reason: vanilla's friction would have the boss
     * cover its first block and then coast, and a run has to cover the lane it drew. Only as
     * much as the lane has left, so the last step lands on its end rather than past it; and
     * steered back onto the committed line, because the npc's own chase pushes it off whenever
     * it re-paths, and a lane that wandered would hit people the warning never covered.</p>
     */
    private void drive(Vec3 now) {
        sent = Math.min(step, Math.max(0.0D, reach - lane.along(now.x, now.z)));
        double across = Mth.clamp(lane.across(now.x, now.z), -MAX_STEER, MAX_STEER);
        npc.setDeltaMovement(axis.x * sent - axis.z * across, npc.getDeltaMovement().y,
                axis.z * sent + axis.x * across);
        // Mobs are position-synced, but the velocity keeps the client's picture of a charge smooth.
        npc.hurtMarked = true;
    }

    /**
     * Hits whoever the stretch just covered ran into.
     *
     * <p>The box round where the boss was and where it is now is only a pre-filter; the lane
     * itself decides, measured off the committed line so it covers exactly what the warning
     * drew. A run that stops on its first victim hits the nearest of them down the lane, and
     * anyone standing level with them.</p>
     *
     * @return whether anybody was hit
     */
    private boolean runOver(ServerLevel level, BossPhaseData phase, Vec3 now, double fromAlong, double toAlong) {
        double bossHalf = npc.getBbWidth() * 0.5D;
        double bottom = Math.min(last.y, now.y);
        double top = Math.max(last.y, now.y) + phase.dash().getHeight();
        AABB box = npc.getBoundingBox();
        AABB sweep = box.minmax(box.move(last.x - now.x, last.y - now.y, last.z - now.z))
                .inflate(lane.halfWidth() + SWEEP_SLACK, 0.0D, lane.halfWidth() + SWEEP_SLACK)
                .expandTowards(0.0D, phase.dash().getHeight(), 0.0D);
        List<LivingEntity> met = level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target != npc
                && target.isAlive() && !struck.contains(target.getUUID())
                && boss.isAbilityTarget(target, BossAbilityKind.DASH)
                && lane.covers(fromAlong, toAlong, bossHalf + target.getBbWidth() * 0.5D, bottom, top,
                target.getX(), target.getZ(), target.getY(), target.getY() + target.getBbHeight()));
        if (met.isEmpty()) {
            return false;
        }
        met.sort(Comparator.comparingDouble(target -> lane.along(target.getX(), target.getZ())));
        double first = lane.along(met.getFirst().getX(), met.getFirst().getZ());
        boolean stops = phase.dash().isStopOnHit();
        int damage = boss.rageUp(phase.dash().getDamage());
        int knockback = boss.rageUp(phase.dash().getKnockback());
        for (LivingEntity target : met) {
            if (stops && lane.along(target.getX(), target.getZ()) > first + CONTACT_SLICE) {
                break;
            }
            struck.add(target.getUUID());
            // Thrown on down the lane rather than away from the boss: vanilla shoves against the
            // vector it is handed, which is why the axis goes in negated.
            BossAbilityDamageUtil.hit(target, BossAbilityKind.DASH, npc, damage, phase.dash().getEffects(),
                    knockback, -axis.x, -axis.z);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    SoundSource.HOSTILE, 1.2F, 0.7F);
        }
        return true;
    }

    /**
     * Met a wall with nobody in the way: the run is over, and the wall's rule says what that
     * costs the boss - a stun, a slam round itself, or its own enrage.
     *
     * <p>The enrage rule sets the boss' enrage off early and never a second one, the barrier's
     * rule for the same choice; a boss with no enrage to set off is stunned instead, so the
     * wall always does something.</p>
     */
    private void hitWall(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        Vec3 impact = npc.position();
        playWallFeedback(level, impact, axis);
        int mode = phase.dash().getWallMode();
        if (mode == BossPhaseData.DASH_WALL_SLAM) {
            finish(gameTime);
            slam(level, phase, impact);
            return;
        }
        // A stun and an enrage are what a fight costs the boss. One that acts out of combat and
        // runs into a wall with no fight on only stops: the barrier drops a window opened with
        // no encounter at once, and an enrage would carry over into the next pull.
        if (!boss.isEncounterRunning()) {
            finish(gameTime);
            return;
        }
        if (mode == BossPhaseData.DASH_WALL_RAGE && data.isRageEnabled() && !boss.isRageActive()) {
            finish(gameTime);
            boss.beginRage(level, gameTime, data);
            return;
        }
        // Staggered while the run still counts as under way, so the stagger is what ends it: a run
        // a stun cut short hands on to no follow-up chained after it.
        stun(gameTime, phase.dash().getStunTicks(), phase.dash().getStunDamagePercent(),
                phase.dash().getStunAnimation());
        finish(gameTime);
    }

    /** The wall's slam: everyone round the boss is hit and thrown off it, with a wave to see it by. */
    private void slam(ServerLevel level, BossPhaseData phase, Vec3 impact) {
        double radius = phase.dash().getSlamRadius();
        // Started before the hits so the wave leaves at the same moment the damage lands.
        BossAreaVfxScheduler.schedule(level, impact, phase.dash().getSlamVfx(), radius, SLAM_VFX_TICKS,
                false, BossWaveTuning.of(npc, phase.dash().getSlamVfx()));
        int damage = boss.rageUp(phase.dash().getSlamDamage());
        int knockback = boss.rageUp(phase.dash().getSlamKnockback());
        for (LivingEntity target : boss.getTargetsAround(level, impact, radius, BossAbilityKind.DASH)) {
            BossAbilityDamageUtil.hit(target, BossAbilityKind.DASH, npc, damage, phase.dash().getEffects(),
                    knockback, impact.x - target.getX(), impact.z - target.getZ());
        }
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.2D, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * How far down the lane the stretch just run crossed the chain of a pair leash in the level -
     * the nearest crossing, if it crossed several - or NaN when it crossed none.
     *
     * <p>Worked out on the floor plan, and then held to height: a chain strung between two
     * people on a balcony is not across a lane on the floor below it.</p>
     */
    private double chainCrossing(ServerLevel level, Vec3 now, double fromAlong, double toAlong) {
        double nearest = Double.NaN;
        for (BossTetherManager.Chain chain : BossTetherManager.pairChains(level)) {
            Crossing crossing = crossing(last.x, last.z, now.x, now.z,
                    chain.from().x, chain.from().z, chain.to().x, chain.to().z);
            if (crossing == null) {
                continue;
            }
            double chainY = chain.from().y + (chain.to().y - chain.from().y) * crossing.chain();
            if (chainY < Math.min(last.y, now.y) - CHAIN_HEIGHT_SLACK
                    || chainY > Math.max(last.y, now.y) + npc.getBbHeight() + CHAIN_HEIGHT_SLACK) {
                continue;
            }
            double along = fromAlong + (toAlong - fromAlong) * crossing.run();
            if (Double.isNaN(nearest) || along < nearest) {
                nearest = along;
            }
        }
        return nearest;
    }

    /** Ran into a tether's chain: the run is over and the boss is stunned, the leash untouched. */
    private void breakOnChain(ServerLevel level, BossPhaseData phase, Vec3 now, long gameTime) {
        level.playSound(null, now.x, now.y, now.z, SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.TETHER), now.x, now.y + npc.getBbHeight() * 0.5D,
                now.z, 20, npc.getBbWidth() * 0.5D, npc.getBbHeight() * 0.3D, npc.getBbWidth() * 0.5D, 0.0D);
        // The wall's reasons: only a fight stuns, and the stagger is what ends the run.
        if (boss.isEncounterRunning()) {
            stun(gameTime, phase.dash().getChainStunTicks(), phase.dash().getChainDamagePercent(),
                    phase.dash().getStunAnimation());
        }
        finish(gameTime);
    }

    /** The stagger's window, for as long as the rule gives; a stun of no ticks is no stun. */
    private void stun(long gameTime, int ticks, int percent, String animation) {
        if (ticks > 0) {
            boss.stagger(gameTime + ticks, percent, animation);
        }
    }

    /** The crunch of the boss meeting the wall, and a spray of whatever the wall is made of. */
    private void playWallFeedback(ServerLevel level, Vec3 impact, Vec3 ahead) {
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                SoundSource.HOSTILE, 1.0F, 0.7F);
        if (ahead == null) {
            return;
        }
        double reach = npc.getBbWidth() * 0.5D + 0.5D;
        BlockPos wall = BlockPos.containing(impact.x + ahead.x * reach, impact.y + 0.5D, impact.z + ahead.z * reach);
        BlockState state = level.getBlockState(wall);
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    impact.x + ahead.x * reach, impact.y + npc.getBbHeight() * 0.5D, impact.z + ahead.z * reach,
                    30, 0.2D, npc.getBbHeight() * 0.3D, 0.2D, 0.15D);
        }
    }

    /** The end every run but an interrupted one comes to: stopped, and the usual pause after a cast. */
    private void finish(long gameTime) {
        clear();
        boss.holdBusyUntil(gameTime + boss.postActionLockTicks());
    }

    /**
     * Stops a run where it got to and hands the boss back to the stationary pin.
     *
     * <p>Idempotent and the one road out: a hit, a wall, the end of the lane, a phase change,
     * a reset and a death all stop the boss here, so none of them can leave it still sliding.</p>
     */
    void clear() {
        if (!running) {
            return;
        }
        Vec3 movement = npc.getDeltaMovement();
        npc.setDeltaMovement(0.0D, movement.y, 0.0D);
        npc.hurtMarked = true;
        npc.getNavigation().stop();
        fallGuard = !npc.onGround();
        running = false;
        axis = null;
        lane = null;
        last = null;
        reach = 0.0D;
        step = 0.0D;
        sent = 0.0D;
        endsAt = NOT_SCHEDULED;
        phaseIndex = -1;
        struck.clear();
        // The pin was let go for the run, so it has to be moved to wherever this ended.
        boss.rememberCurrentPosition();
    }

    /** Keeps the fall off a run's end harmless until the boss is back on its feet. */
    private void guardLanding() {
        if (!fallGuard) {
            return;
        }
        npc.fallDistance = 0.0F;
        if (npc.onGround() || npc.isInWater()) {
            fallGuard = false;
        }
    }

    /** A little of the floor thrown up behind the boss, so a charge reads as a charge. */
    private void kickUpFloor(ServerLevel level) {
        BlockPos below = BlockPos.containing(npc.getX(), npc.getY() - 0.2D, npc.getZ());
        BlockState floor = level.getBlockState(below);
        if (floor.isAir()) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), npc.getX(), npc.getY() + 0.1D,
                npc.getZ(), 3, npc.getBbWidth() * 0.4D, 0.05D, npc.getBbWidth() * 0.4D, 0.1D);
    }

    /** The phase a run belongs to, so a phase that disappears mid run cannot rewrite its hit. */
    private BossPhaseData phaseOf(TeleportPathData data) {
        return phaseIndex >= 0 && phaseIndex < data.getPhaseCount() ? data.getPhase(phaseIndex) : null;
    }

    /** How long the corridor the wind-up marks is: the lane from where the boss stands now. */
    double previewReach(TeleportPathData data, BossPhaseData phase, Vec3 committed) {
        return reachFrom(data, phase, npc.position(), committed);
    }

    /**
     * How far down {@code committed} a run from {@code start} may go: its length, cut where the
     * lane would leave the home leash.
     *
     * <p>Not the leap's radial clamp. That pulls one landing spot back toward home, which for a
     * straight run would bend the lane off the line the warning promised; a run is cut where
     * its own line crosses the leash instead, with the leap's margin inside the edge so the
     * stop does not count as leaving. A vertical leash leaves the flat circle its height at the
     * boss' feet allows.</p>
     */
    double reachFrom(TeleportPathData data, BossPhaseData phase, Vec3 start, Vec3 committed) {
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
                committed.x, committed.z, limit));
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

    /**
     * The safety net on a run's length in ticks: twice what the lane takes at full speed, and
     * a second on top, so water, a slope or a crowd can slow it without stranding it.
     */
    static int timeoutTicks(int length, int speedTenths) {
        return (int) Math.ceil(length * 10.0D / Math.max(1, speedTenths) * 2.0D) + 20;
    }

    /** Whether a tick that got this far of what it was sent, with the boss against something, was a wall. */
    static boolean stoppedByWall(boolean collided, double progress, double sent) {
        return collided && progress < sent * WALL_PROGRESS_SHARE;
    }

    /** Where a run's stretch crossed a chain: the fraction of the way along each of the two. */
    record Crossing(double run, double chain) {
    }

    /**
     * Where the flat segment p-q (the stretch run) crosses the flat segment a-b (the chain), or
     * null when the two do not cross.
     *
     * <p>The ends count: a run that stops exactly on the chain has met it, and so has one that
     * clips the very end of it at somebody's chest. Segments running parallel never cross - a
     * run along a chain rather than through it has nothing to break on - and neither does a
     * tick in which the boss did not move.</p>
     */
    static Crossing crossing(double px, double pz, double qx, double qz,
                             double ax, double az, double bx, double bz) {
        double rx = qx - px;
        double rz = qz - pz;
        double sx = bx - ax;
        double sz = bz - az;
        double denominator = rx * sz - rz * sx;
        if (Math.abs(denominator) < 1.0E-9D) {
            return null;
        }
        double wx = ax - px;
        double wz = az - pz;
        double alongRun = (wx * sz - wz * sx) / denominator;
        double alongChain = (wx * rz - wz * rx) / denominator;
        return alongRun >= 0.0D && alongRun <= 1.0D && alongChain >= 0.0D && alongChain <= 1.0D
                ? new Crossing(alongRun, alongChain) : null;
    }

    /**
     * The corridor a run was committed to, measured along and across its line from where the
     * run started. Flat: height is the caller's band, not part of the line.
     */
    record Lane(double originX, double originZ, double axisX, double axisZ, double halfWidth) {

        /** How far down the line a point is; negative behind the start. */
        double along(double x, double z) {
            return (x - originX) * axisX + (z - originZ) * axisZ;
        }

        /** How far off the line a point is, signed: positive on the left looking down the lane. */
        double across(double x, double z) {
            return (x - originX) * axisZ - (z - originZ) * axisX;
        }

        /**
         * Whether a body is in the stretch a run covered from {@code fromAlong} to
         * {@code toAlong}: its middle inside the corridor's width, within {@code touch} of the
         * stretch down the line - the two bodies' half widths, which is where they meet - and
         * with its height overlapping the band the run fills.
         *
         * <p>Never behind the start of the lane, though: somebody pressed against the boss' back
         * when it sets off is not in its path, and the corridor the warning drew starts at the
         * boss rather than behind it.</p>
         */
        boolean covers(double fromAlong, double toAlong, double touch, double bandBottom, double bandTop,
                       double x, double z, double bodyBottom, double bodyTop) {
            double along = along(x, z);
            return along >= Math.max(0.0D, fromAlong - touch) && along <= toAlong + touch
                    && Math.abs(across(x, z)) <= halfWidth
                    && bodyTop > bandBottom && bodyBottom < bandTop;
        }
    }
}
