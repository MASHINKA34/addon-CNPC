package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation;
import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataRanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Server-authoritative controller for a stationary worm/boss: animated path teleports, health
 * phases, clone minion summoning and three independently timed attacks.
 */
public final class TeleportPathController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);
    static final long NOT_SCHEDULED = Long.MIN_VALUE;
    /** How often a controller whose tick keeps throwing is allowed to say so in the log. */
    private static final int TICK_FAILURE_LOG_INTERVAL_TICKS = 200;
    static final int POST_ACTION_LOCK_TICKS = 10;

    @FunctionalInterface
    private interface AbilityStarter {
        boolean start(TeleportPathController controller, ServerLevel level, TeleportPathData data,
                      BossPhaseData phase, long gameTime);
    }

    /**
     * What actually starts each ability, keyed by its row in the table.
     *
     * <p>Keyed rather than ordered: a list matched the rotation by position alone, so a row
     * inserted in one place and not the other silently ran the wrong ability's starter -
     * an attack firing under another one's cooldown, which reads as a balance problem
     * rather than as the wiring mistake it is. The check below turns that into a crash on
     * load instead.</p>
     */
    private static final Map<BossAbility, AbilityStarter> ABILITY_STARTERS = new EnumMap<>(Map.ofEntries(
            Map.entry(BossAbility.GROUND_ATTACK, TeleportPathController::tryStartGroundAttack),
            Map.entry(BossAbility.RANGED_ATTACK, TeleportPathController::tryStartRangedAttack),
            Map.entry(BossAbility.MELEE_ATTACK, TeleportPathController::tryStartMeleeAttack),
            Map.entry(BossAbility.FLUID_SPIT, (controller, level, data, phase, gameTime) ->
                    controller.fluidSpit.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.HOOK, (controller, level, data, phase, gameTime) ->
                    controller.hook.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.CAPTURE, (controller, level, data, phase, gameTime) ->
                    controller.capture.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.LEAP, (controller, level, data, phase, gameTime) ->
                    controller.leap.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.LINE_ATTACK, (controller, level, data, phase, gameTime) ->
                    controller.lineAttack.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.GEYSER, (controller, level, data, phase, gameTime) ->
                    controller.geyser.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.BOULDER, (controller, level, data, phase, gameTime) ->
                    controller.boulder.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.BOULDER_RAIN, (controller, level, data, phase, gameTime) ->
                    controller.boulder.tryStartRain(level, data, phase, gameTime)),
            Map.entry(BossAbility.TETHER, (controller, level, data, phase, gameTime) ->
                    controller.tether.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.GRAVITY, (controller, level, data, phase, gameTime) ->
                    controller.gravity.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.MARK, (controller, level, data, phase, gameTime) ->
                    controller.mark.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.COVER, (controller, level, data, phase, gameTime) ->
                    controller.coverRuntime.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.HUNT, TeleportPathController::tryStartHunt),
            Map.entry(BossAbility.BEAM, (controller, level, data, phase, gameTime) ->
                    controller.beam.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.COCOON, (controller, level, data, phase, gameTime) ->
                    controller.cocoon.tryStart(level, data, phase, gameTime)),
            Map.entry(BossAbility.SUMMON, TeleportPathController::tryStartSummon)));

    @FunctionalInterface
    private interface AbilityPerformer {
        void perform(TeleportPathController controller, ServerLevel level, TeleportPathData data,
                     BossPhaseData phase, long gameTime);
    }

    /**
     * What each wound-up action actually does when its delay runs out.
     *
     * <p>The twin of {@link #ABILITY_STARTERS}, and keyed for the same reason. This was a
     * chain of twenty {@code else if}s comparing the same field against a different constant
     * each time, which reads as a table anyway - except that a chain cannot be checked and a
     * table can. An action with no branch simply fell off the end of the chain: the boss wound
     * up, the wind-up expired, and nothing happened, for the life of the server.</p>
     *
     * <p>The teleport is here as well as on the rotation, because a hop is wound up and
     * executed exactly like an attack even though its clock is its own.</p>
     */
    private static final Map<BossAbility, AbilityPerformer> ABILITY_PERFORMERS =
            new EnumMap<>(Map.ofEntries(
            Map.entry(BossAbility.GROUND_ATTACK, (controller, level, data, phase, gameTime) ->
                    controller.performAreaAttack(level, phase)),
            Map.entry(BossAbility.RANGED_ATTACK, (controller, level, data, phase, gameTime) ->
                    controller.performRangedAttack(level, phase)),
            Map.entry(BossAbility.MELEE_ATTACK, (controller, level, data, phase, gameTime) ->
                    controller.performMeleeAttack(level, phase)),
            Map.entry(BossAbility.FLUID_SPIT, (controller, level, data, phase, gameTime) ->
                    controller.fluidSpit.perform(level, phase)),
            Map.entry(BossAbility.HOOK, (controller, level, data, phase, gameTime) ->
                    controller.hook.perform(level, phase, gameTime)),
            Map.entry(BossAbility.CAPTURE, (controller, level, data, phase, gameTime) ->
                    controller.capture.perform(level, phase, gameTime)),
            Map.entry(BossAbility.LEAP, (controller, level, data, phase, gameTime) ->
                    controller.leap.perform(level, data, phase, gameTime)),
            Map.entry(BossAbility.LINE_ATTACK, (controller, level, data, phase, gameTime) ->
                    controller.lineAttack.perform(level, phase)),
            Map.entry(BossAbility.GEYSER, (controller, level, data, phase, gameTime) ->
                    controller.geyser.perform(level, phase, gameTime)),
            Map.entry(BossAbility.BOULDER, (controller, level, data, phase, gameTime) ->
                    controller.boulder.perform(level, phase)),
            Map.entry(BossAbility.BOULDER_RAIN, (controller, level, data, phase, gameTime) ->
                    controller.boulder.performRain(level, phase, gameTime)),
            Map.entry(BossAbility.TETHER, (controller, level, data, phase, gameTime) ->
                    controller.tether.perform(level, phase, gameTime)),
            Map.entry(BossAbility.GRAVITY, (controller, level, data, phase, gameTime) ->
                    controller.gravity.perform(level, phase, gameTime)),
            Map.entry(BossAbility.MARK, (controller, level, data, phase, gameTime) ->
                    controller.mark.perform(level, phase, gameTime)),
            Map.entry(BossAbility.COVER, (controller, level, data, phase, gameTime) ->
                    controller.coverRuntime.perform(level)),
            Map.entry(BossAbility.HUNT, (controller, level, data, phase, gameTime) ->
                    controller.huntRuntime.perform(level, phase, gameTime)),
            Map.entry(BossAbility.BEAM, (controller, level, data, phase, gameTime) ->
                    controller.beam.perform(level, phase, gameTime)),
            Map.entry(BossAbility.COCOON, (controller, level, data, phase, gameTime) ->
                    controller.cocoon.perform(level, phase, gameTime)),
            Map.entry(BossAbility.SUMMON, (controller, level, data, phase, gameTime) ->
                    controller.performSummon(level, phase)),
            Map.entry(BossAbility.TELEPORT, (controller, level, data, phase, gameTime) ->
                    controller.path.perform(level, data, phase, gameTime))));

    /** Every action that is wound up and then carried out: the rotation, plus the teleport. */
    static final Set<BossAbility> PERFORMED_ACTIONS = performedActions();

    private static Set<BossAbility> performedActions() {
        Set<BossAbility> all = EnumSet.copyOf(BossAbility.ROTATION);
        all.add(BossAbility.TELEPORT);
        return Set.copyOf(all);
    }

    static {
        // An ability on the rotation with nothing to start it would be skipped in silence
        // for the life of the server, so it is worth refusing to load over.
        if (!ABILITY_STARTERS.keySet().equals(Set.copyOf(BossAbility.ROTATION))) {
            throw new IllegalStateException("Boss ability rotation and starter table disagree: "
                    + BossAbility.ROTATION + " vs " + ABILITY_STARTERS.keySet());
        }
        // And one that starts but never lands is the same mistake one step later.
        if (!ABILITY_PERFORMERS.keySet().equals(PERFORMED_ACTIONS)) {
            throw new IllegalStateException("Boss ability rotation and performer table disagree: "
                    + PERFORMED_ACTIONS + " vs " + ABILITY_PERFORMERS.keySet());
        }
    }

    /** Tries at finding floor and room for one shelter before the wind-up gives it up. */
    /**
     * Where the lower sight line is aimed, above the victim's feet. A slab hides the legs
     * and has to count; a carpet lies under them and must not, so the line ends a quarter
     * block up rather than on the floor.
     */
    /** Dust stacked over a shelter's centre, so it can be picked out from across the arena. */
    /** How fast the take cover strike's wave runs, in blocks a tick: a shockwave, not a stroll. */
    /** Quietest gap that still reads as one clang per hit rather than a rattle. */
    private static final int BLOCK_FEEDBACK_INTERVAL_TICKS = 5;
    /**
     * The leap is a plain ballistic push, so its speed has to be worked out against the
     * numbers vanilla actually moves a living entity by: every airborne tick the position
     * advances by the current speed, then gravity is taken off the vertical one and both
     * are scaled by their drag. Solving that discretely is what makes a leap land on its
     * mark instead of a good block short of it.
     */
    /**
     * Trim on the horizontal speed, tunable in one place.
     *
     * <p>The flight length is counted in whole ticks while the boss touches down partway
     * through one, and it loses a sliver of speed to every corner it clips on the way, so
     * the arc lands a few percent short of its mark. Measured against a tick-for-tick
     * replay of the movement above: without it a leap is up to 4% short, with it the error
     * is inside 3% either way, which the smallest slam radius swallows.</p>
     */
    /** Enough to clear the 64 block height ceiling; only the solver's search uses it. */
    /**
     * A badly set up arena must not fling the boss across the world. Far above anything a
     * sane jump asks for: even a hundred block leap only needs about 1.3 blocks a tick.
     */
    /** Ticks the boss gets to leave the floor before a leap counts as never started. */
    /** Rough spacing between the landing marker's particles, in blocks. */
    /** How long the landing wave runs for; the leap has no length setting of its own. */
    /** Kept clear of the leash edge so a landing cannot start the reset countdown. */
    /** How often the wind-up mark is repainted. Every other tick reads as a steady shape. */
    static final int TELEGRAPH_INTERVAL_TICKS = 2;
    /** With no player this close the mark cannot be seen, so it is not worth the particles. */
    static final double TELEGRAPH_AUDIENCE_RANGE = 64.0D;
    /** How far to either side of its gaze a melee swing is marked. */
    private static final double TELEGRAPH_MELEE_HALF_ANGLE = 60.0D;
    /** Small enough to read as "one climbs out here" rather than as an attack zone. */
    private static final double TELEGRAPH_SPAWN_RING_RADIUS = 1.0D;
    /** Ceiling on the spawn points marked at once, so a long list cannot flood the floor. */
    private static final int TELEGRAPH_MAX_SPAWN_RINGS = 8;
    /** One quiet note as the boss commits, never a rattle every tick it winds up for. */
    private static final float TELEGRAPH_SOUND_VOLUME = 0.8F;
    /** Well under the bell's own pitch, which is what turns a ding into a gong. */
    private static final float TELEGRAPH_SOUND_PITCH = 0.6F;
    /** A dodged ability comes back round in a couple of seconds, not a whole cooldown. */
    private static final int TELEGRAPH_DODGE_RETRY_TICKS = 40;
    /** Yaw eased onto a wound-up line strike's axis per tick; the hit itself snaps the rest. */
    private static final float LINE_FACE_TURN_DEGREES_PER_TICK = 15.0F;
    private static final int MINION_ALIVE_SCAN_INTERVAL_TICKS = 5;
    private static final Set<TeleportPathController> INSTANCES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private final EntityNPCInterface npc;
    /** Server-side encounter membership, independent of whether any boss bar is visible. */
    private final Set<UUID> encounterParticipants = new HashSet<>();
    private boolean active;
    private int currentPhase = -1;
    private int highestPhaseReached;
    /** Lowest phase the health lookup may hand back; an immune phase advances it by hand. */
    private int forcedPhaseFloor;
    /** Game time the immune window closes at, or NOT_SCHEDULED while the boss is vulnerable. */
    private long invulnerableUntil = NOT_SCHEDULED;
    /** Whether a summon has already run in the current immune window. */
    private boolean invulnerableSummonedOnce;
    /** Phase the last immune window belonged to, so a phase only turns immune once per fight. */
    private int invulnerablePhaseIndex = -1;
    /** Earliest game time the next blocked-hit clang may play at. */
    private long nextBlockFeedbackAt;
    /** Earliest game time the tick guard may report the next failure at. */
    private long nextTickFailureLogAt;
    private long outOfCombatSince = NOT_SCHEDULED;
    private long outsideHomeLeashSince = NOT_SCHEDULED;
    private boolean encounterResetDone;
    private boolean encounterRunning;
    private long encounterBeganAt = NOT_SCHEDULED;
    private double lockedX;
    private double lockedZ;
    /** Where the boss stood when it activated - the spot a reset sends it back to. */
    private double homeX;
    private double homeY;
    private double homeZ;
    private long busyUntil;
    /**
     * Game time each ability is next allowed to start at, indexed by {@link BossAbility}.
     *
     * <p>One array rather than twenty fields, so the rotation, the cancel and the per-phase
     * arming can all walk {@link BossAbility#ROTATION} instead of naming every ability
     * again in four places that had no way of noticing one had gone missing.</p>
     */
    private final long[] abilityScheduleAt = newSchedule();

    /** The styled boss bar and the countdown printed under it. */
    private final BossBarRuntime bar;
    /** The aggro zone that starts the fight and the retargeting that keeps it honest. */
    private final BossTargetingRuntime targeting;
    /** The enrage clock and the attribute bonus it hangs on the boss. */
    private final BossRageRuntime rage;
    /** The arena turning dangerous for a phase; armed on every phase this boss enters. */
    private final BossHazardRuntime hazardRuntime;
    /** The shield with a timer, armed on every phase this boss enters inside a fight. */
    private final BossBarrierRuntime barrierRuntime;
    /** The chase this boss is running, handed over once the hunt ability lands. */
    private final BossHuntRuntime huntRuntime;
    /** The boss' health scaled to how many players turned up. */
    private final BossHealthScalingRuntime healthScalingRuntime;
    /** The protection totems standing round this boss and the beams that tie them to it. */
    private final BossTotemRuntime totems;
    /** The channel that hits the whole arena and spares only whoever hid from it. */
    private final BossCoverRuntime coverRuntime;
    /** The chain the boss throws, and the drag that keeps hold of whoever it caught. */
    private final BossHookRuntime hook;
    /** The jump, its flight and the slam it lands with. */
    private final BossLeapRuntime leap;
    /** Where a summon puts its clones, and the order it takes its points in. */
    private final BossMinionSpawnRuntime minionSpawns;
    /** The walk over the teleport path: which point is next, and when. */
    private final BossPathRuntime path;
    /** The shell the boss closes round a victim, and the guard posted beside it. */
    private final BossCocoonRuntime cocoon;
    /** The grab, and the beam it holds its victim in. */
    private final BossCaptureRuntime capture;
    /** The fuse under a victim's feet and the column that follows it. */
    private final BossGeyserRuntime geyser;
    /** The circle handed to a victim that goes off wherever they take it. */
    private final BossMarkRuntime mark;
    /** The leash tied to the boss, to a spot or between two victims. */
    private final BossTetherCastRuntime tether;
    /** The field that pulls, pushes or throws everything around the boss. */
    private final BossGravityCastRuntime gravity;
    /** The stone rolled down a corridor, and the ring of them dropped out of the sky. */
    private final BossBoulderRuntime boulder;
    /** The corridor struck straight out in front of the boss, and its two flank waves. */
    private final BossLineAttackRuntime lineAttack;
    /** The lobbed ball of fluid and the puddle it leaves. */
    private final BossFluidSpitRuntime fluidSpit;
    /** The beams swept round the boss after the cast. */
    private final BossBeamCastRuntime beam;

    /**
     * Which way the action being wound up is going to go, unit length and flat, or null for
     * one that is not aimed along a line at all.
     *
     * <p>Fixed the moment the boss commits and never touched again: a corridor that swung
     * round after a running player would turn its own warning into a lie. One field rather
     * than one per aimed ability, because exactly one action is ever pending - three fields
     * only made it possible for the wrong one to be left standing.</p>
     */
    private Vec3 committedAxis;

    /**
     * The Minecraft yaw the action being wound up leaves at, in degrees, for the one that
     * is aimed by angle rather than by direction.
     *
     * <p>Committed the same way the axis is, and for the same reason: the lines the wind-up
     * draws promise where the beams start, and a boss that went on turning after its target
     * would break that promise on the sweep's first tick.</p>
     */
    private float committedYaw;


    /** Victims beyond the first, captured when a multi-target ability starts winding up. */
    private final List<Integer> pendingExtraTargets = new ArrayList<>();
    private BossAbility pendingAction = BossAbility.NONE;
    private long pendingActionAt = NOT_SCHEDULED;
    private int pendingTargetId = -1;
    /**
     * Game time the wind-up animation starts at. Until then the boss is only warning: the
     * target is picked and marked, and nothing is animating yet.
     */
    private long pendingWarningEndsAt = NOT_SCHEDULED;
    /** The wind-up animation the warning is holding back. */
    private String pendingAnimation = "";
    /** Warning ticks put in front of this action, owed back to its cooldown afterwards. */
    private int pendingLeadTicks;
    /**
     * Whether the action running right now holds a walking boss on its spot. While it is
     * set, lockedX/Z stop following the boss and the stationary pin takes over, so the
     * position from the start of the wind-up is the one the whole cast happens on.
     */
    private boolean castRootActive;
    /** Game time the pin a finished action left behind lets go, or NOT_SCHEDULED mid wind-up. */
    private long castRootUntil = NOT_SCHEDULED;
    private int nextAbilityPriority;
    private long minionAliveScanAt = NOT_SCHEDULED;
    private boolean minionAliveScan;

    public TeleportPathController(EntityNPCInterface npc) {
        this.npc = npc;
        this.bar = new BossBarRuntime(this, npc);
        this.targeting = new BossTargetingRuntime(this, npc);
        this.rage = new BossRageRuntime(this, npc);
        this.hazardRuntime = new BossHazardRuntime(this, npc);
        this.barrierRuntime = new BossBarrierRuntime(this, npc);
        this.huntRuntime = new BossHuntRuntime(this, npc);
        this.healthScalingRuntime = new BossHealthScalingRuntime(this, npc);
        this.totems = new BossTotemRuntime(this, npc);
        this.coverRuntime = new BossCoverRuntime(this, npc);
        this.hook = new BossHookRuntime(this, npc);
        this.leap = new BossLeapRuntime(this, npc);
        this.minionSpawns = new BossMinionSpawnRuntime(this, npc);
        this.path = new BossPathRuntime(this, npc);
        this.cocoon = new BossCocoonRuntime(this, npc);
        this.capture = new BossCaptureRuntime(this, npc);
        this.geyser = new BossGeyserRuntime(this, npc);
        this.mark = new BossMarkRuntime(this, npc);
        this.tether = new BossTetherCastRuntime(this, npc);
        this.gravity = new BossGravityCastRuntime(this, npc);
        this.boulder = new BossBoulderRuntime(this, npc);
        this.lineAttack = new BossLineAttackRuntime(this, npc);
        this.fluidSpit = new BossFluidSpitRuntime(this, npc);
        this.beam = new BossBeamCastRuntime(this, npc);
        INSTANCES.add(this);
    }

    /**
     * Runs the controller's whole tick behind a guard.
     *
     * <p>The call is injected into the NPC's own tick, and an exception escaping from there
     * takes the level tick - and the server - down with it. One boss with a configuration
     * or a world state nothing here foresaw is not worth that: it is logged and skipped,
     * and the rest of the tick carries on.</p>
     */
    public void tick() {
        try {
            tickGuarded();
        } catch (Throwable error) {
            long gameTime = npc.level() instanceof ServerLevel level ? level.getGameTime() : 0L;
            // One line every ten seconds at worst, not one per tick for as long as it lasts.
            if (gameTime >= nextTickFailureLogAt) {
                nextTickFailureLogAt = gameTime + TICK_FAILURE_LOG_INTERVAL_TICKS;
                LOGGER.error("Boss controller tick failed for NPC {}; skipping this tick",
                        npc.getName().getString(), error);
            }
        }
    }

    private void tickGuarded() {
        TeleportPathData data = settings();
        if (!data.isEnabled() || !(npc.level() instanceof ServerLevel level) || !npc.isAlive()) {
            // `active` is only true between activate() and reset(), so this runs exactly
            // once on the tick the boss dies rather than every tick it lies dead.
            if (active && npc.level() instanceof ServerLevel inactiveLevel) {
                if (!npc.isAlive()) {
                    if (data.isClearMinionsOnDeath()) {
                        BossMinionUtil.clear(inactiveLevel, npc, data.getMinionRemovalMode());
                    }
                    totems.removeOnBossDeath(inactiveLevel, data);
                } else if (!data.isEnabled()) {
                    totems.removeConfigured(inactiveLevel);
                }
            }
            reset();
            return;
        }
        if (NpcCarryManager.isCarried(npc)) {
            // The builder tool owns the boss' position while it is held, so the stationary
            // pin and the home leash have to keep their hands off it. Following the carry
            // here is what stops the pin from yanking it back on the tick it is put down.
            rememberCurrentPosition();
            return;
        }

        long gameTime = level.getGameTime();
        if (!active) {
            activate(level, gameTime, data);
        }
        targeting.updateAggroZone(level, data, gameTime);
        targeting.updateNearest(level, data, gameTime);
        // After the two above and before anything reads the target: whatever they, the
        // vanilla aggro or a script did to it since the last tick is put back here.
        huntRuntime.tick(level, data, gameTime);
        if (hasCombatTarget()) {
            beginEncounter(level, gameTime, data);
        }
        healthScalingRuntime.tickPlayerCount(level, gameTime, data);
        healthScalingRuntime.tick(data);
        // A leash reset owns the rest of this tick, including already-due abilities.
        if (tickHomeLeash(level, gameTime, data)) {
            return;
        }
        tickCastRoot(gameTime);
        // Held and rooted are read in this order, not merged: the root has to keep its own
        // deadline so the last totem falling mid wind-up cannot cut the swing short, and the
        // hold has to outlive that deadline so the end of a cast cannot set the boss loose.
        // A boss stunned by its broken barrier is pinned the way a held one is, and for as
        // long: the pin is the stun.
        if ((data.isStationary() || totems.isHolding() || isBarrierStunned()) && !leap.isAirborne()) {
            keepStationary();
        } else if (castRootActive) {
            // A rooted wind-up borrows the stationary pin: lockedX/Z stopped following the
            // boss when the action began, so this holds the spot its warning was shown on.
            keepStationary();
        } else {
            // A leap owns the boss' position while it is in the air - the pin would drag it
            // straight back to the take-off spot - so it follows the flight to the landing.
            rememberCurrentPosition();
        }
        hook.tick(level, gameTime);
        faceCombatTarget(data);
        // Runs before updatePhase so the phase it unlocks is switched to in this same tick,
        // and above the busy/no-target early returns so a locked boss cannot stay immune.
        tickInvulnerability(level, gameTime, data);
        updatePhase(level, gameTime, data);
        totems.tick(level, gameTime, data);
        rage.tick(level, gameTime, data);
        bar.update(level, data);
        bar.syncTimer(gameTime, data);
        BossPhaseData phase = data.getPhase(currentPhase);
        // Above the combat-only return and the busy gate on purpose: a leap already in the
        // air has to come down and land even if the boss loses its target mid flight.
        leap.tick(level, data, gameTime);
        // Above the busy gate and the pending block below on purpose: a wind-up has to stay
        // marked through a lock, and the mark has to stop on the tick the ability goes off.
        tickTelegraph(level, data, gameTime);
        hazardRuntime.tick(level, data, gameTime);
        // Above the gates for the hazard's reason: the party's clock does not stop because
        // the boss is held in an animation or lost sight of its target for a moment.
        barrierRuntime.tick(level, data, gameTime);

        if (data.isCombatOnly() && !hasCombatTarget()) {
            cancelPendingAndSchedules();
            return;
        }
        if (gameTime < busyUntil) {
            return;
        }
        if (pendingAction != BossAbility.NONE) {
            if (pendingWarningEndsAt != NOT_SCHEDULED && gameTime >= pendingWarningEndsAt
                    && !endTelegraphWarning(level, data, phase, gameTime)) {
                return;
            }
            if (gameTime >= pendingActionAt) {
                executePendingAction(level, data, phase, gameTime);
                // The cooldown was counted from before the warning was put in front of the
                // wind-up. Handing those ticks back keeps a warned ability on exactly the
                // rhythm it had without one.
                delayAbilitySchedule(pendingAction, pendingLeadTicks);
                clearPendingAction();
                busyUntil = Math.max(busyUntil, gameTime + POST_ACTION_LOCK_TICKS);
                holdCastRootThroughLock(gameTime);
            }
            return;
        }

        List<int[]> points = npc.ais.getMovingPath();
        path.prepare(points);
        scheduleMissingAbilities(gameTime, phase, points.size() >= 2);

        // A held boss is barred from the path as well as from walking it: leaving the spot the
        // totems pin it to is exactly what the hold is there to stop, however it is done. A
        // silenced hunt bars it too: the boss is meant to be running its prey down, not away.
        // And a stun: a boss that cannot walk cannot blink out of the window either.
        if (points.size() >= 2 && gameTime >= abilityScheduleAt(BossAbility.TELEPORT) && !totems.isHolding() && !huntRuntime.isSilenced()
                && !isBarrierStunned() && (!isInvulnerable() || phase.isInvulnerableAllowTeleport())) {
            setAbilityScheduleAt(BossAbility.TELEPORT, NOT_SCHEDULED);
            beginAction(BossAbility.TELEPORT, phase.getTeleportPreparationAnimation(),
                    phase.getTeleportPreparationTicks(), gameTime, null, data, phase);
            return;
        }

        tryStartDueAbility(level, data, phase, gameTime);
    }

    /**
     * The phase this boss is fighting in right now, or null when it is not an active boss.
     * Used by delayed effects - a projectile only lands several ticks after it was fired.
     */
    public BossPhaseData activePhase() {
        if (!active || currentPhase < 0) {
            return null;
        }
        TeleportPathData data = settings();
        return data.isEnabled() ? data.getPhase(currentPhase) : null;
    }

    /** Keeps an active capture tied to the phase configuration that started it. */
    boolean isCaptureEnabledForPhase(int phaseIndex) {
        if (!active || !encounterRunning) {
            return false;
        }
        TeleportPathData data = settings();
        return data.isEnabled() && phaseIndex >= 0 && phaseIndex < data.getPhaseCount()
                && data.getPhase(phaseIndex).isCaptureEnabled();
    }

    /** Keeps a cocoon tied to the phase configuration that closed it, the way a capture is. */
    boolean isCocoonEnabledForPhase(int phaseIndex) {
        if (!active || !encounterRunning) {
            return false;
        }
        TeleportPathData data = settings();
        return data.isEnabled() && phaseIndex >= 0 && phaseIndex < data.getPhaseCount()
                && data.getPhase(phaseIndex).isCocoonEnabled();
    }

    /** Keeps a leash tied to the phase configuration that threw it, the way a capture is. */
    boolean isTetherEnabledForPhase(int phaseIndex) {
        if (!active || !encounterRunning) {
            return false;
        }
        TeleportPathData data = settings();
        return data.isEnabled() && phaseIndex >= 0 && phaseIndex < data.getPhaseCount()
                && data.getPhase(phaseIndex).isTetherEnabled();
    }

    TeleportPathData settings() {
        return ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
    }

    /** The phase being fought by index, which is -1 until the boss is activated. */
    int currentPhaseIndex() {
        return currentPhase;
    }

    /** Whether a chase owns the target right now, which every other picker stands aside for. */
    boolean isHunting() {
        return huntRuntime.isHunting();
    }

    /** The arena box the aggro zone is drawn in, or null when its corners leave no room. */
    AABB aggroZoneBounds(ServerLevel level, TeleportPathData data) {
        return targeting.zoneBounds(level, data);
    }

    void setTargetIfChanged(LivingEntity target) {
        targeting.setTargetIfChanged(target);
    }

    /** The phase whose immune window is running, for the countdown that draws it. */
    int invulnerablePhaseIndex() {
        return invulnerablePhaseIndex;
    }

    /** Game time the fight started at, or NOT_SCHEDULED while no encounter is running. */
    long encounterStartedAt() {
        return rage.encounterStartedAt();
    }

    /** True from the moment the enrage clock runs out until the fight ends. */
    public boolean isRageActive() {
        return rage.isActive();
    }

    /** @return ticks left before the enrage, or 0 when there is nothing to count */
    public int rageTicksLeft() {
        return rage.ticksLeft();
    }

    public int rageTotalTicks() {
        return rage.totalTicks();
    }

    /** Takes the enrage bonus off and stops the clock; every ending of a fight goes through it. */
    public void clearRage() {
        rage.clear();
    }

    void beginRage(ServerLevel level, long gameTime, TeleportPathData data) {
        rage.begin(level, gameTime, data);
    }

    /** Scales a number the enrage makes bigger - damage, knockback, a pull's strength. */
    int rageUp(int value) {
        return rage.up(value);
    }

    /** The other half of {@link #rageUp}, for cooldowns - the boss acts more often, not less. */
    int rageDown(int value) {
        return rage.down(value);
    }

    /** What the enrage multiplies by, for the hit scaling that cannot go through {@link #rageUp}. */
    double rageMultiplier() {
        return rage.multiplier();
    }

    /** Everyone this fight is being run against, read-only for the subsystems that address them. */
    Set<UUID> encounterParticipants() {
        return encounterParticipants;
    }

    private void activate(ServerLevel level, long gameTime, TeleportPathData data) {
        active = true;
        lockedX = npc.getX();
        lockedZ = npc.getZ();
        homeX = npc.getX();
        homeY = npc.getY();
        homeZ = npc.getZ();
        outsideHomeLeashSince = NOT_SCHEDULED;
        highestPhaseReached = data.resolvePhaseIndex(healthPercent());
        currentPhase = highestPhaseReached;
        outOfCombatSince = NOT_SCHEDULED;
        encounterResetDone = false;
        path.forgetPosition();
        totems.initialize(level, gameTime, data);
        // A cocoon that outlived its hold - the server went down with somebody inside - is
        // a shell with nobody in it, and goes the way a totem the boss no longer knows does.
        BossCocoonUtil.removeStrayCocoons(level, npc);
        enterPhase(level, gameTime, data, data.getPhase(currentPhase));
    }

    /**
     * Opens the encounter exactly once, when the first real combat target appears.
     * A short target loss deliberately leaves this latch and its original timestamp alone.
     */
    private void beginEncounter(ServerLevel level, long gameTime, TeleportPathData data) {
        if (encounterRunning) {
            return;
        }
        encounterRunning = true;
        encounterBeganAt = gameTime;
        encounterResetDone = false;
        if (npc.getTarget() instanceof ServerPlayer player) {
            trackParticipant(player);
        }
        registerInitialPartyCandidates(level, data);
        // The phase the fight opens in was entered long before anyone pulled - on load, or
        // at the end of the last fight - so its hazard is armed from here instead.
        hazardRuntime.arm(level, gameTime, data.getPhase(currentPhase));
        // The barrier for the same reason: a shield with nobody to break it is not a check.
        barrierRuntime.arm(level, gameTime, data.getPhase(currentPhase));
        armPhaseInvulnerability(gameTime, data.getPhase(currentPhase));
        healthScalingRuntime.beginEncounter(level, gameTime, data);
        totems.beginEncounter(gameTime, data);
    }

    /** Whether a target has started a fight which has not yet completed its reset. */
    public boolean isEncounterRunning() {
        return encounterRunning;
    }

    /** @return the first combat tick, or {@link #NOT_SCHEDULED} outside an encounter */
    public long encounterBeganAt() {
        return encounterBeganAt;
    }

    private void clearEncounter() {
        encounterRunning = false;
        encounterBeganAt = NOT_SCHEDULED;
        outsideHomeLeashSince = NOT_SCHEDULED;
        encounterParticipants.clear();
        healthScalingRuntime.endEncounter();
    }

    /** @return true when crossing the leash ended the encounter this tick */
    private boolean tickHomeLeash(ServerLevel level, long gameTime, TeleportPathData data) {
        // A leap in flight is not the boss wandering off: its landing spot was pulled
        // inside the radius before it pushed off, and with a vertical leash the arc over
        // the arena would otherwise reset the fight the boss is in the middle of.
        if (!data.isHomeLeashEnabled() || !encounterRunning || !active || !npc.isAlive() || leap.isAirborne()) {
            outsideHomeLeashSince = NOT_SCHEDULED;
            return false;
        }

        double radius = data.getHomeLeashRadius();
        if (homeLeashDistanceSquared(data.isHomeLeashVertical()) <= radius * radius) {
            outsideHomeLeashSince = NOT_SCHEDULED;
            return false;
        }
        if (outsideHomeLeashSince == NOT_SCHEDULED) {
            outsideHomeLeashSince = gameTime;
        }
        if (gameTime - outsideHomeLeashSince < data.getHomeLeashGraceTicks()) {
            return false;
        }

        endEncounter(level, data);
        return true;
    }

    private double homeLeashDistanceSquared(boolean includeVertical) {
        double dx = npc.getX() - homeX;
        double dz = npc.getZ() - homeZ;
        if (!includeVertical) {
            return dx * dx + dz * dz;
        }
        double dy = npc.getY() - homeY;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Read-only status used by the boss diagnostic command. */
    public String homeLeashStatus(long gameTime, TeleportPathData data) {
        if (!data.isHomeLeashEnabled()) {
            return "Home leash: off";
        }
        double distance = Math.sqrt(homeLeashDistanceSquared(data.isHomeLeashVertical()));
        String prefix = String.format(java.util.Locale.ROOT, "Home leash: %.1f / %d blocks, ",
                distance, data.getHomeLeashRadius());
        if (distance <= data.getHomeLeashRadius()) {
            return prefix + "inside";
        }
        long elapsed = outsideHomeLeashSince == NOT_SCHEDULED
                ? 0L : Math.max(0L, gameTime - outsideHomeLeashSince);
        long remaining = Math.max(0L, data.getHomeLeashGraceTicks() - elapsed);
        return prefix + "resets in " + remaining + " ticks";
    }

    private void updatePhase(ServerLevel level, long gameTime, TeleportPathData data) {
        if (hasCombatTarget()) {
            outOfCombatSince = NOT_SCHEDULED;
            encounterResetDone = false;
        } else if (outOfCombatSince == NOT_SCHEDULED) {
            outOfCombatSince = gameTime;
        }

        if (outOfCombatSince != NOT_SCHEDULED
                && gameTime - outOfCombatSince >= data.getResetTicks()) {
            // The phase is deliberately not recomputed from health here. A boss that was
            // beaten down and then walked away from does not heal on its own, so reading
            // the phase back off its health would leave it stuck in the phase the last
            // fight ended in and open the next one with late-phase abilities.
            endEncounter(level, data);
            return;
        }

        // Inside a fight the phase only ever advances, so healing the boss - a potion, a
        // script, a regeneration effect - cannot rewind the encounter mid-combat.
        int healthPhase = Math.max(data.resolvePhaseIndex(healthPercent()), forcedPhaseFloor);
        highestPhaseReached = Math.max(highestPhaseReached, healthPhase);
        if (highestPhaseReached == currentPhase) {
            return;
        }
        currentPhase = highestPhaseReached;
        cancelPendingAndSchedules();
        // After the schedules are wiped, so an immediate summon is not cleared again.
        enterPhase(level, gameTime, data, data.getPhase(currentPhase));
        playAnimation(data.getPhaseTransitionAnimation());
        if (!data.getPhaseTransitionAnimation().isEmpty()) {
            busyUntil = gameTime + data.getPhaseTransitionLockTicks();
        }
    }

    /**
     * Arms what a phase brings with it when the boss steps into one: the arena hazard, the
     * totem wave and the immune window, whichever of them the phase has.
     *
     * <p>The immune window is keyed on the phase index so a phase only turns immune once
     * per encounter: the last phase has nowhere to advance to, and would otherwise re-arm
     * itself forever.</p>
     */
    private void enterPhase(ServerLevel level, long gameTime, TeleportPathData data, BossPhaseData phase) {
        // Only inside a fight: the boss also enters its first phase when it merely loads, and
        // an arena that burns with nobody in it is armed from the pull instead.
        if (encounterRunning) {
            hazardRuntime.arm(level, gameTime, phase);
        }
        // Asked outside a fight too: it only drops the last phase's barrier then, and a
        // window or a count left over from the last fight must not survive into this one.
        barrierRuntime.arm(level, gameTime, phase);
        totems.onPhaseEntered(gameTime, data);
        if (encounterRunning || !data.isCombatOnly()) {
            armPhaseInvulnerability(gameTime, phase);
        }
    }

    private void armPhaseInvulnerability(long gameTime, BossPhaseData phase) {
        if (!phase.isInvulnerableEnabled() || invulnerablePhaseIndex == currentPhase) {
            return;
        }
        invulnerablePhaseIndex = currentPhase;
        invulnerableUntil = gameTime + phase.getInvulnerableDurationTicks();
        invulnerableSummonedOnce = false;
        if (phase.isInvulnerableSummonImmediately()) {
            // Set to now rather than left unscheduled: scheduleMissingAbilities fills an
            // unscheduled summon in with a whole fresh cooldown.
            setAbilityScheduleAt(BossAbility.SUMMON, gameTime);
        }
    }

    /**
     * Closes the immune window once its phase's exit condition is met.
     *
     * <p>Only the phase floor is raised here. {@link #updatePhase} runs later in the same
     * tick and performs the switch, which keeps the transition animation and its lock in a
     * single place.</p>
     */
    private void tickInvulnerability(ServerLevel level, long gameTime, TeleportPathData data) {
        if (invulnerableUntil == NOT_SCHEDULED) {
            return;
        }
        BossPhaseData phase = data.getPhase(invulnerablePhaseIndex);
        // The flag being switched off mid-fight ends the window too, rather than stranding
        // the boss immune until its timer happens to run out.
        if (phase.isInvulnerableEnabled() && !isInvulnerableWindowOver(level, phase, gameTime)) {
            return;
        }
        invulnerableUntil = NOT_SCHEDULED;
        // The floor is what actually moves the boss on: it lost no health while immune, so
        // the health lookup would keep handing back the phase it has just finished.
        forcedPhaseFloor = Math.min(invulnerablePhaseIndex + 1, data.getPhaseCount() - 1);
    }

    private boolean anyMinionAlive(ServerLevel level, long gameTime) {
        if (minionAliveScanAt == NOT_SCHEDULED
                || gameTime - minionAliveScanAt >= MINION_ALIVE_SCAN_INTERVAL_TICKS
                || gameTime < minionAliveScanAt) {
            minionAliveScanAt = gameTime;
            minionAliveScan = BossMinionUtil.hasAlive(level, npc);
        }
        return minionAliveScan;
    }

    private boolean isInvulnerableWindowOver(ServerLevel level, BossPhaseData phase, long gameTime) {
        boolean timerDone = gameTime >= invulnerableUntil;
        if (!phase.invulnerableWaitsForMinions()) {
            return timerDone;
        }
        // "All of them are dead" only means anything once a wave has been called for -
        // otherwise the phase would end on the very tick it began. The summon counts as
        // called for even if nothing spawned, so a boss walled into a corner with nowhere
        // to put its clones still gets out of the phase.
        boolean minionsDone = invulnerableSummonedOnce && !anyMinionAlive(level, gameTime);
        if (!phase.invulnerableWaitsForTimer()) {
            return minionsDone;
        }
        return phase.getInvulnerableEndMode() == BossPhaseData.INVULNERABLE_END_TIMER_AND_MINIONS
                ? timerDone && minionsDone
                : timerDone || minionsDone;
    }

    /** Whether a leap is in the air right now. Read by the fall damage handler. */
    public boolean isLeaping() {
        return active && leap.isAirborne();
    }

    /** Whether the boss is in an immune phase right now. Read by the damage handler and the HUD. */
    public boolean isInvulnerable() {
        return active && invulnerableUntil != NOT_SCHEDULED;
    }

    /**
     * Where the boss stood when this fight started - the same spot {@code reset_return}
     * sends it back to.
     *
     * @return null while no fight is running, because there is no arena to speak of then
     */
    public BlockPos getArenaHome() {
        return active ? BlockPos.containing(homeX, homeY, homeZ) : null;
    }

    /**
     * Shield clang and a puff of sparks for a hit that bounced off an immune boss.
     *
     * <p>Self-throttling: a boss ringed by players takes several hits per tick, and one
     * clang each reads as a bug rather than as immunity.</p>
     */
    public void playInvulnerableHitFeedback() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        if (!claimBlockFeedback(level.getGameTime())) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.SHIELD_BLOCK,
                SoundSource.HOSTILE, 0.8F, 0.9F + npc.getRandom().nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.ENCHANT, npc.getX(), npc.getY(0.6D), npc.getZ(), 8,
                npc.getBbWidth() * 0.6D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.6D, 0.05D);
    }

    /** Beacon-like feedback points at the living object that is supplying the protection. */
    public void playTotemHitFeedback() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        if (!claimBlockFeedback(level.getGameTime())) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.HOSTILE, 1.0F, 1.2F + npc.getRandom().nextFloat() * 0.15F);

        Entity linked = totems.firstLoadedAlive(level);
        Vec3 from = npc.position().add(0.0D, npc.getBbHeight() * 0.6D, 0.0D);
        Vec3 to = linked == null
                ? from.add(0.0D, npc.getBbHeight() * 0.5D, 0.0D)
                : linked.position().add(0.0D, linked.getBbHeight() * 0.5D, 0.0D);
        Vec3 delta = to.subtract(from);
        int points = Mth.clamp((int) Math.ceil(delta.length() * 2.0D), 4, 24);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(delta.scale((double) i / points));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** @return ticks left on the immune window, or 0 when the boss is vulnerable */
    public int invulnerableTicksLeft() {
        if (!isInvulnerable() || !(npc.level() instanceof ServerLevel level)) {
            return 0;
        }
        return (int) Math.max(0L, invulnerableUntil - level.getGameTime());
    }

    private void clearInvulnerability() {
        invulnerableUntil = NOT_SCHEDULED;
        invulnerableSummonedOnce = false;
        invulnerablePhaseIndex = -1;
        forcedPhaseFloor = 0;
        nextBlockFeedbackAt = 0L;
    }

    /**
     * Puts the boss back the way it was before anyone aggroed it: phase one, no minions,
     * no boss bar, no scheduled abilities. Everything that is allowed to survive a fight
     * but not the fight after it unwinds here, so later mechanics only have one place to
     * hook into.
     *
     * <p>Runs once per encounter - the flag is only cleared by the next tick that finds a
     * real combat target - so repeated calls while the boss idles are free.</p>
     */
    private void endEncounter(ServerLevel level, TeleportPathData data) {
        if (encounterResetDone) {
            return;
        }
        encounterResetDone = true;

        npc.setTarget(null);
        // Dropping the target on its own is not enough: CustomNPCs remembers everyone who
        // hurt the boss and picks a new target off that list within ten ticks, which would
        // restart the fight the moment it was declared over.
        npc.combatHandler.reset();

        currentPhase = 0;
        highestPhaseReached = 0;
        // Restore the base maximum before reset healing decides whether to fill it.
        healthScalingRuntime.clear(data, data.isResetHeal());
        clearEncounter();
        clearInvulnerability();
        minionSpawns.clearCursor();
        rage.clear();
        cancelPendingAndSchedules();
        hook.clear();
        hazardRuntime.clear();
        barrierRuntime.clear();
        BossGeyserScheduler.clearBoss(npc);
        BossMarkScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        BossGravityScheduler.clearBoss(npc);
        // Before the return below: clearing the leap re-pins the boss where it stands, and
        // the return then moves that pin home rather than the other way round.
        leap.clear();
        BossCaptureManager.releaseByBoss(npc);
        BossTetherManager.releaseByBoss(npc);
        // Before the minions are cleared: a cocoon is discarded, never killed, and the
        // clear would run it through the builder's removal mode like any other minion.
        BossCocoonManager.releaseByBoss(npc);
        busyUntil = 0L;

        if (data.isClearMinionsOnReset()) {
            BossMinionUtil.clear(level, npc, data.getMinionRemovalMode());
        }
        // After the clear, so a guard goes the way the builder's removal mode says when
        // that is on; and whatever it says, a guard was posted for a fight that is over.
        BossCocoonUtil.removeGuards(level, npc);
        totems.resetAfterEncounter(level, data);
        bar.hide();

        if (data.isResetReturn()) {
            npc.teleportTo(homeX, homeY, homeZ);
            npc.fallDistance = 0.0F;
            npc.setDeltaMovement(Vec3.ZERO);
            npc.getNavigation().stop();
            // A stationary boss is pinned to lockedX/lockedZ every tick, so without this
            // the teleport is undone before anyone sees it.
            lockedX = homeX;
            lockedZ = homeZ;
        }
    }

    /** The bar this boss' countdown belongs on, and the audience every announcement uses. */
    ServerBossEvent timerBossEvent() {
        return bar.timerBossEvent();
    }

    private int healthPercent() {
        float maximum = npc.getMaxHealth();
        return maximum <= 0.0F ? 100 : Math.round(npc.getHealth() * 100.0F / maximum);
    }

    /**
     * Whether the boss is really fighting someone right now.
     *
     * <p>Deliberately stricter than asking CustomNPCs whether it has a target: it holds on
     * to one until the victim leaves the NPC's own aggro range, which is configured apart
     * from the boss' search radius and is routinely far wider, so a player who just walked
     * off would otherwise keep the encounter alive forever.</p>
     */
    boolean hasCombatTarget() {
        LivingEntity target = npc.getTarget();
        if (target == null || target.isRemoved() || !target.isAlive()
                || target.level() != npc.level()) {
            return false;
        }
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        // Half a search radius of slack on top, so a target standing right on the edge of
        // it does not flicker the fight on and off from one tick to the next.
        double leash = settings().getTargetSearchRadius() * 1.5D;
        return npc.distanceToSqr(target) <= leash * leash;
    }

    public void trackParticipant(ServerPlayer player) {
        TeleportPathData data = settings();
        if (player.level() != npc.level() || !data.isEnabled() || !isParticipant(player)) {
            return;
        }
        encounterParticipants.add(player.getUUID());
        if (BossBarStyles.isEnabled(data.getBossBarStyle())) {
            bar.addViewer(player);
        }
    }

    /** Kept for integrations compiled against the old bar-specific participant API. */
    public void trackBossBarPlayer(ServerPlayer player) {
        trackParticipant(player);
    }

    boolean isParticipant(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.isRemoved()
                && npc.canAttack(player) && !npc.isAlliedTo(player);
    }

    /** Adds the whole nearby group before a lock-at-start encounter takes its snapshot. */
    void registerInitialPartyCandidates(ServerLevel level, TeleportPathData data) {
        double radius = data.getTargetSearchRadius();
        double radiusSquared = radius * radius;
        AABB zone = data.isAggroZoneEnabled() ? targeting.zoneBounds(level, data) : null;
        for (ServerPlayer player : level.players()) {
            if (player.level() == level && isParticipant(player)
                    && (npc.distanceToSqr(player) <= radiusSquared
                    || zone != null && zone.contains(player.position()))) {
                encounterParticipants.add(player.getUUID());
            }
        }
    }

    public int scaledPlayerCount() {
        return healthScalingRuntime.scaledPlayerCount();
    }

    /** Read-only party-health snapshot for /cnpcgecko boss. */
    public String partyHealthStatus(TeleportPathData data) {
        return healthScalingRuntime.status(data);
    }

    /** Clears administrative NEVER holes and reconciles every enabled slot on the next tick. */
    public void restoreAllTotemsNow() {
        totems.restoreAllNow();
    }

    /** True only while at least one configured wave entity is known to be alive. */
    public boolean isTotemWardStanding() {
        return totems.isWardStanding();
    }

    /** True while a standing formation is the reason the boss cannot be hurt. */
    public boolean isTotemProtected() {
        return totems.isProtecting();
    }

    /** True while a standing formation nails the boss to the spot it is fighting on. */
    public boolean isTotemHeld() {
        return totems.isHolding();
    }

    /** True while a standing formation keeps the boss from starting anything of its own. */
    public boolean isTotemSilenced() {
        return totems.isSilencing();
    }

    /** True while a standing formation keeps the boss off everyone else's aiming list. */
    public boolean isTotemHidden() {
        return totems.isHiding();
    }

    public int aliveTotemCount() {
        return totems.aliveCount();
    }

    public int configuredTotemCount() {
        return totems.configuredCount();
    }

    public int getTotemProtectionMode() {
        return settings().getTotemProtectionMode();
    }

    /** Gives a viewer an immediate snapshot when either endpoint starts being tracked. */
    public void syncTotemLinksTo(ServerPlayer player) {
        totems.syncLinksTo(player);
    }

    /** Where this boss was standing when it was last at rest; the totems anchor off it. */
    Vec3 homePosition() {
        return new Vec3(homeX, homeY, homeZ);
    }

    /** Health as a status line wants it: whole where it is whole, and never a trailing zero. */
    static String formatHealth(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    public void removeBossBarPlayer(ServerPlayer player) {
        bar.removeViewer(player);
    }

    public void removeParticipant(ServerPlayer player) {
        // Keep encounter history so a dynamic participant is counted again after returning.
        healthScalingRuntime.recountSoon();
        removeBossBarPlayer(player);
    }

    public String captureStatus(long gameTime) {
        String victim = BossCaptureManager.capturedVictimName(npc.getUUID());
        if (victim != null) {
            return "Capture: holding " + victim;
        }
        BossPhaseData phase = activePhase();
        if (phase == null || !phase.isCaptureEnabled()) {
            return "Capture: disabled";
        }
        long remaining = abilityCooldownLeft(BossAbility.CAPTURE, gameTime);
        return remaining > 0L ? "Capture: cooldown " + remaining : "Capture: ready";
    }

    public String tetherStatus(long gameTime) {
        int held = BossTetherManager.countForBoss(npc.getUUID());
        if (held > 0) {
            return "Tether: holding " + held;
        }
        BossPhaseData phase = activePhase();
        if (phase == null || !phase.isTetherEnabled()) {
            return "Tether: disabled";
        }
        long remaining = abilityCooldownLeft(BossAbility.TETHER, gameTime);
        return remaining > 0L ? "Tether: cooldown " + remaining : "Tether: ready";
    }

    public String gravityStatus(long gameTime) {
        long open = BossGravityScheduler.remainingTicks(npc, gameTime);
        if (open > 0L) {
            return "Gravity: field open " + open;
        }
        BossPhaseData phase = activePhase();
        if (phase == null || !phase.isGravityEnabled()) {
            return "Gravity: disabled";
        }
        long remaining = abilityCooldownLeft(BossAbility.GRAVITY, gameTime);
        return remaining > 0L ? "Gravity: cooldown " + remaining : "Gravity: ready";
    }

    public static void syncTotemLinksForTracking(ServerPlayer player, Entity tracked) {
        for (TeleportPathController controller : List.copyOf(INSTANCES)) {
            if (controller.npc.level() == player.level()
                    && (tracked == controller.npc || BossTotemUtil.isTotemOf(tracked, controller.npc))) {
                controller.totems.syncLinksTo(player);
            }
        }
    }

    /** Removes protection in the same death event, before another hit can reach the boss. */
    public static void onTotemDeath(Entity deadTotem) {
        if (!(deadTotem.level() instanceof ServerLevel level)) {
            return;
        }
        for (TeleportPathController controller : List.copyOf(INSTANCES)) {
            if (controller.npc.level() == level && BossTotemUtil.isTotemOf(deadTotem, controller.npc)) {
                controller.totems.markDead(BossTotemUtil.slotId(deadTotem), level.getGameTime(),
                        controller.settings());
                return;
            }
        }
    }

    /** Captured by the death event before the NPC can disappear without another tick. */
    public void onDeath() {
        releaseRuntime();
        if (npc.level() instanceof ServerLevel level) {
            totems.removeOnBossDeath(level, settings());
        }
    }

    private void releaseRuntime() {
        stopBossBar();
        rage.clear();
        huntRuntime.end();
        barrierRuntime.clear();
        healthScalingRuntime.clear(settings(), false);
        clearEncounter();
        BossCaptureManager.releaseByBoss(npc);
        BossTetherManager.releaseByBoss(npc);
        BossCocoonManager.releaseByBoss(npc);
        BossGeyserScheduler.clearBoss(npc);
        BossMarkScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        BossGravityScheduler.clearBoss(npc);
        BossBeamScheduler.clearBoss(npc);
    }

    public void shutdown() {
        // The level is going away with the boss still enraged: the modifier is transient and
        // never reaches the save file, but the entity object outlives an unload, so it is
        // taken off here rather than left for a tick that may never come.
        releaseRuntime();
        if (npc.level() instanceof ServerLevel level) {
            totems.dropAllLinks(level);
        }
        totems.clearRuntime();
        INSTANCES.remove(this);
        // Off the npc as well as off the list. A controller that stayed on a reviving npc
        // would keep ticking outside every static sweep, which is worse than none at all;
        // dropped, the next tick simply builds one that is on the list again.
        if (npc instanceof IBossController holder) {
            holder.cnpcgeckoaddon$clearTeleportPathController();
        }
    }

    public void stopBossBar() {
        bar.stop();
    }

    public static void removePlayerFromEncounters(ServerPlayer player) {
        for (TeleportPathController controller : List.copyOf(INSTANCES)) {
            controller.removeParticipant(player);
        }
    }

    public static void shutdownLevel(ServerLevel level) {
        for (TeleportPathController controller : List.copyOf(INSTANCES)) {
            if (controller.npc.level() == level) {
                controller.shutdown();
            }
        }
    }

    private void keepStationary() {
        npc.getNavigation().stop();
        Vec3 movement = npc.getDeltaMovement();
        npc.setDeltaMovement(0.0D, movement.y, 0.0D);
        if (Math.abs(npc.getX() - lockedX) > 1.0E-4D || Math.abs(npc.getZ() - lockedZ) > 1.0E-4D) {
            npc.setPos(lockedX, npc.getY(), lockedZ);
        }
    }

    void rememberCurrentPosition() {
        lockedX = npc.getX();
        lockedZ = npc.getZ();
    }

    /**
     * Fixes which way the action now being wound up is aimed.
     *
     * <p>Set by the ability as it commits and read by everything downstream that has to keep
     * the same promise - the eased turn, the corridor drawn on the floor, and the ability's
     * own landing. Cleared with the rest of the pending action.</p>
     */
    void commitAxis(Vec3 axis) {
        committedAxis = axis;
    }

    Vec3 committedAxis() {
        return committedAxis;
    }

    /** The same, for an ability aimed by angle rather than by direction. */
    void commitYaw(float yaw) {
        committedYaw = yaw;
    }

    float committedYaw() {
        return committedYaw;
    }

    /**
     * Pins a walking boss for the action that is just starting, when this phase casts the
     * ability standing still.
     *
     * <p>The stationary boss is pinned every tick anyway, so the root stays out of its way.
     * A leap always roots its crouch - the flight is what has to stay free, and
     * {@link BossLeapRuntime} lets go at the push - while a teleport is never held: moving
     * away is the whole ability.</p>
     */
    private void beginCastRoot(TeleportPathData data, BossPhaseData phase, BossAbility action) {
        if (data.isStationary()) {
            return;
        }
        if (action != BossAbility.LEAP && !phase.isCastRooted(action.kind())) {
            return;
        }
        // Freeze the pin on the spot the boss commits on: the warning zone is being shown
        // around it, and staying there is how the boss keeps that promise.
        rememberCurrentPosition();
        castRootActive = true;
        castRootUntil = NOT_SCHEDULED;
    }

    /** Keeps the root up for the pause a finished action leaves, instead of for ever. */
    private void holdCastRootThroughLock(long gameTime) {
        if (castRootActive) {
            castRootUntil = gameTime + POST_ACTION_LOCK_TICKS;
        }
    }

    /**
     * Lets go of a root that has nothing holding it any more.
     *
     * <p>Mid wind-up the pending action is what holds the root, afterwards the
     * {@code castRootUntil} deadline does. One with neither - its action was cancelled by
     * a dodge, a phase change or a reset - drops here, so no way out of an action can
     * leave the boss nailed to the floor for good.</p>
     */
    private void tickCastRoot(long gameTime) {
        if (!castRootActive) {
            return;
        }
        if (castRootUntil == NOT_SCHEDULED
                ? pendingAction == BossAbility.NONE
                : gameTime >= castRootUntil) {
            endCastRoot();
        }
    }

    void endCastRoot() {
        if (!castRootActive) {
            return;
        }
        castRootActive = false;
        castRootUntil = NOT_SCHEDULED;
        // Hand the pin back to the walk from wherever the boss was released.
        rememberCurrentPosition();
    }

    /**
     * Moves the arena to wherever the boss was just put down by the carry tool.
     *
     * <p>The home is captured once, on the tick the boss first activates, so an encounter
     * that is already running would otherwise leash and reset the boss back to the room it
     * was carried out of.</p>
     */
    public void onRelocated() {
        homeX = npc.getX();
        homeY = npc.getY();
        homeZ = npc.getZ();
        rememberCurrentPosition();
        outsideHomeLeashSince = NOT_SCHEDULED;
    }

    /** The removed vanilla damage must not also remove the boss' visual tracking of its target. */
    private void faceCombatTarget(TeleportPathData data) {
        if (faceCommittedAxis(data)) {
            return;
        }
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) return;
        npc.getLookControl().setLookAt(target, 90.0F, 90.0F);
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-5D) return;
        double dy = target.getEyeY() - npc.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        npc.setYRot(yaw);
        npc.yBodyRot = yaw;
        npc.yHeadRot = yaw;
        npc.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
    }

    /**
     * Turns a boss winding up a line strike or a boulder onto the corridor it committed to,
     * instead of after the target.
     *
     * <p>The axis was fixed the moment the warning went down, but the tracking above kept
     * swinging the model after the runner, so the swing read as aimed one way while the hit
     * came down another. Eased rather than snapped: the wind-up doubles as the turn, and
     * the perform squares up whatever is left on the tick the ability goes off.</p>
     *
     * @return true when the wind-up owns the rotation this tick
     */
    private boolean faceCommittedAxis(TeleportPathData data) {
        if (pendingAction != BossAbility.LINE_ATTACK && pendingAction != BossAbility.BOULDER) {
            return false;
        }
        BossPhaseData phase = data.getPhase(currentPhase);
        if (pendingAction == BossAbility.LINE_ATTACK) {
            if (committedAxis == null || !phase.isLineAttackFaceAxis()) {
                return false;
            }
            turnTowardAxis(committedAxis, phase.getLineAttackLength(), LINE_FACE_TURN_DEGREES_PER_TICK);
            return true;
        }
        // The boulder has no opt-out: its corridor is exactly as wide as the stone, so one
        // launched off the boss' shoulder reads as broken rather than as a style choice.
        if (committedAxis == null) {
            return false;
        }
        turnTowardAxis(committedAxis, phase.getBoulderRange(), LINE_FACE_TURN_DEGREES_PER_TICK);
        return true;
    }

    /** Body, head and gaze onto the committed axis, moving at most {@code maxTurn} degrees. */
    void turnTowardAxis(Vec3 axis, double lookDistance, float maxTurn) {
        if (axis == null) {
            return;
        }
        float wanted = (float) (Mth.atan2(axis.z, axis.x) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(npc.getYRot(), wanted, maxTurn);
        npc.setYRot(yaw);
        npc.yBodyRot = yaw;
        npc.yHeadRot = yaw;
        // The corridor is flat, so the gaze levels out instead of staying dipped at
        // wherever the victim's eyes last were.
        npc.setXRot(Mth.approach(npc.getXRot(), 0.0F, maxTurn));
        // The look control is pointed down the corridor too, or its own tick drags the
        // head straight back to the target between two of these.
        npc.getLookControl().setLookAt(npc.getX() + axis.x * lookDistance,
                npc.getEyeY(), npc.getZ() + axis.z * lookDistance, 90.0F, 90.0F);
    }

    /**
     * Arms every ability this phase switched on, and takes the deadline off the rest.
     *
     * <p>Walks {@link BossAbility#ROTATION} instead of holding one block per ability. The
     * blocks were identical bar two method names, which is exactly the shape that lets an
     * ability be added to the rotation and quietly never armed - it fires never rather than
     * wrongly, so nothing crashes and nobody finds out. A row that exists is armed here.</p>
     *
     * <p>An ability switched off mid fight loses its deadline on this tick rather than
     * firing once more off a stale one, and one switched back on starts a full cooldown
     * later rather than immediately.</p>
     */
    private void scheduleMissingAbilities(long gameTime, BossPhaseData phase, boolean hasPath) {
        // The teleport keeps its own arming: it runs off a random delay range rather than a
        // flat cooldown, and a boss with fewer than two path points has nowhere to go.
        if (hasPath && abilityScheduleAt(BossAbility.TELEPORT) == NOT_SCHEDULED) {
            path.scheduleNext(gameTime, phase);
        }
        for (BossAbility ability : BossAbility.ROTATION) {
            if (!ability.isEnabledIn(phase)) {
                setAbilityScheduleAt(ability, NOT_SCHEDULED);
            } else if (abilityScheduleAt(ability) == NOT_SCHEDULED) {
                setAbilityScheduleAt(ability, gameTime + rageDown(ability.cooldownTicks(phase)));
            }
        }
    }

    /** Rotates ability priority so short cooldowns cannot permanently starve another attack. */
    private boolean tryStartDueAbility(ServerLevel level, TeleportPathData data,
                                       BossPhaseData phase, long gameTime) {
        // New casts only, and every kind of them - the call for help an immune boss would
        // still make included. A wind-up already under way was resolved above this, and the
        // cooldowns keep running down underneath, so a boss whose last totem falls after two
        // hours of silence swings on the very tick it comes loose. A stunned boss is quiet
        // the same way, and its wind-up was already dropped when the stun landed.
        if (totems.isSilencing() || huntRuntime.isSilenced() || isBarrierStunned()) {
            return false;
        }
        if (isInvulnerable()) {
            // An immune boss only calls for help. The other attacks keep their timers and
            // pick up where they left off once it can be hurt again.
            return tryStartSummon(level, data, phase, gameTime);
        }
        int count = BossAbility.ROTATION.size();
        for (int offset = 0; offset < count; offset++) {
            int index = (nextAbilityPriority + offset) % count;
            BossAbility ability = BossAbility.ROTATION.get(index);
            if (ABILITY_STARTERS.get(ability).start(this, level, data, phase, gameTime)) {
                nextAbilityPriority = (index + 1) % count;
                return true;
            }
        }
        return false;
    }

    private boolean tryStartGroundAttack(ServerLevel level, TeleportPathData data,
                                         BossPhaseData phase, long gameTime) {
        if (!phase.isAreaAttackEnabled() || gameTime < abilityScheduleAt(BossAbility.GROUND_ATTACK)) return false;
        if (!hasAreaTargets(level, phase)) {
            setAbilityScheduleAt(BossAbility.GROUND_ATTACK, gameTime + 20);
            return false;
        }
        beginAction(BossAbility.GROUND_ATTACK, phase.getAreaAttackAnimation(),
                phase.getAreaAttackActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would land the hit before the swing does.
        setAbilityScheduleAt(BossAbility.GROUND_ATTACK, gameTime + phase.getAreaAttackActionDelayTicks()
                + rageDown(phase.getAreaAttackCooldownTicks()));
        return true;
    }

    /** Where the boss is looking, flattened onto the plane the corridor is worked out in. */
    Vec3 facingAxis() {
        double yaw = npc.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private boolean tryStartRangedAttack(ServerLevel level, TeleportPathData data,
                                         BossPhaseData phase, long gameTime) {
        if (!phase.isRangedAttackEnabled() || gameTime < abilityScheduleAt(BossAbility.RANGED_ATTACK)) return false;
        LivingEntity target = selectAbilityTarget(level, phase.getRangedAttackTargetMode(),
                phase.getRangedAttackMaxRange(), candidate -> isValidRangedTarget(candidate, phase));
        if (target == null || !ProjectileEntityUtil.canShoot(npc)) {
            setAbilityScheduleAt(BossAbility.RANGED_ATTACK, gameTime + 10);
            return false;
        }
        beginAction(BossAbility.RANGED_ATTACK, phase.getRangedAttackAnimation(),
                phase.getRangedAttackActionDelayTicks(), gameTime, target, data, phase);
        setAbilityScheduleAt(BossAbility.RANGED_ATTACK, gameTime + phase.getRangedAttackActionDelayTicks()
                + rageDown(phase.getRangedAttackCooldownTicks()));
        return true;
    }

    private boolean tryStartMeleeAttack(ServerLevel level, TeleportPathData data,
                                        BossPhaseData phase, long gameTime) {
        if (!phase.isMeleeAttackEnabled() || gameTime < abilityScheduleAt(BossAbility.MELEE_ATTACK)) return false;
        // Melee reach is measured hitbox to hitbox, so the search box carries the boss own
        // half-width on top of the configured range or a wide boss loses candidates to it.
        LivingEntity target = selectAbilityTarget(level, phase.getMeleeAttackTargetMode(),
                phase.getMeleeAttackRange() + npc.getBbWidth() * 0.5D,
                candidate -> isValidMeleeTarget(candidate, phase));
        if (target == null) {
            setAbilityScheduleAt(BossAbility.MELEE_ATTACK, gameTime + 5);
            return false;
        }
        beginAction(BossAbility.MELEE_ATTACK, phase.getMeleeAttackAnimation(),
                phase.getMeleeAttackActionDelayTicks(), gameTime, target, data, phase);
        setAbilityScheduleAt(BossAbility.MELEE_ATTACK, gameTime + phase.getMeleeAttackActionDelayTicks()
                + rageDown(phase.getMeleeAttackCooldownTicks()));
        return true;
    }

    /**
     * Singles one victim out and winds up to go after them.
     *
     * <p>Who it is gets settled here and never again: the whole ability is the promise that
     * the boss ignores everyone else, and a chase that could switch prey halfway would be
     * an ordinary fight with extra steps. The pick reaches as far as the boss looks for a
     * target at all, because that is the one range this ability has - it has no shape on
     * the floor, only somebody to run at.</p>
     */
    private boolean tryStartHunt(ServerLevel level, TeleportPathData data,
                                 BossPhaseData phase, long gameTime) {
        if (!phase.isHuntEnabled() || gameTime < abilityScheduleAt(BossAbility.HUNT)) return false;
        if (huntRuntime.isHunting()) {
            // One prey at a time. A cooldown shorter than the chase looks again once it is over.
            setAbilityScheduleAt(BossAbility.HUNT, gameTime + 20);
            return false;
        }
        LivingEntity prey = selectAbilityTarget(level, phase.getHuntTargetMode(),
                data.getTargetSearchRadius(), candidate -> huntRuntime.isValidTarget(candidate, data));
        if (prey == null) {
            setAbilityScheduleAt(BossAbility.HUNT, gameTime + 10);
            return false;
        }
        beginAction(BossAbility.HUNT, phase.getHuntAnimation(),
                phase.getHuntActionDelayTicks(), gameTime, prey, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the roar it plays.
        setAbilityScheduleAt(BossAbility.HUNT, gameTime + phase.getHuntActionDelayTicks()
                + rageDown(phase.getHuntCooldownTicks()));
        return true;
    }

    /** Read-only status used by the boss diagnostic command. */
    public String beamStatus(long gameTime) {
        return beam.status(gameTime);
    }

    /** Read-only status used by the boss diagnostic command. */
    public String cocoonStatus(long gameTime) {
        return cocoon.status(gameTime);
    }

    public String huntStatus(long gameTime) {
        return huntRuntime.status(gameTime, activePhase(), abilityScheduleAt(BossAbility.HUNT));
    }

    /** Read-only status used by the boss diagnostic command. */
    public String barrierStatus(long gameTime) {
        return barrierRuntime.status(gameTime);
    }

    /** Whether a barrier stands right now. Read by the damage handler and the status line. */
    public boolean isBarrierUp() {
        return barrierRuntime.isUp();
    }

    /** What the standing barrier still absorbs, or 0 when none stands. */
    public float barrierLeft() {
        return barrierRuntime.left();
    }

    /**
     * Pays one hit out of the barrier, and breaks it when the hit is the last it can take.
     *
     * @param bypassesCooldown whether the source is one vanilla lets straight through the cooldown
     * @return how much the barrier took; 0 for a hit the cooldown dropped whole
     */
    public float absorbIntoBarrier(float amount, boolean bypassesCooldown) {
        return barrierRuntime.absorb(amount, bypassesCooldown);
    }

    /** True while a broken barrier's window keeps the boss on its spot and off its abilities. */
    public boolean isBarrierStunned() {
        return barrierRuntime.isExposed();
    }

    /** True while a broken barrier's window has the boss taking more than it usually does. */
    public boolean isBarrierExposed() {
        return barrierRuntime.isExposed();
    }

    /** What the boss takes inside the window, as a percentage; 100 outside one. */
    public int barrierExposedPercent() {
        return barrierRuntime.exposedPercent();
    }

    /**
     * The stagger a broken barrier's window brings: whatever the boss was winding up is
     * dropped where it stands, and comes back round as soon as the window shuts rather than
     * after its whole cooldown. A leap already in the air is physics and keeps flying, the
     * way every cancel leaves it.
     */
    void interruptForBarrierStun(long windowEndsAt) {
        if (pendingAction == BossAbility.NONE) {
            return;
        }
        BossAbility interrupted = pendingAction;
        endCastRoot();
        clearPendingAction();
        bringAbilityScheduleForward(interrupted, windowEndsAt);
    }

    /** Holds the boss off its own rotation until this game time; never brings it forward. */
    void lockActionsUntil(long gameTime) {
        busyUntil = Math.max(busyUntil, gameTime);
    }

    /** Whether this boss is between its activation and its reset. */
    boolean isActive() {
        return active;
    }

    /**
     * Takes the shared blocked-hit feedback slot for this tick, so the immune clang and the
     * barrier chime cannot both go off on the same hit.
     */
    boolean claimBlockFeedback(long gameTime) {
        if (gameTime < nextBlockFeedbackAt) {
            return false;
        }
        nextBlockFeedbackAt = gameTime + BLOCK_FEEDBACK_INTERVAL_TICKS;
        return true;
    }

    private boolean tryStartSummon(ServerLevel level, TeleportPathData data,
                                   BossPhaseData phase, long gameTime) {
        if (!phase.canSummon() || gameTime < abilityScheduleAt(BossAbility.SUMMON)) return false;
        if (BossMinionUtil.countAlive(level, npc, phase.getMaxAliveMinions()) >= phase.getMaxAliveMinions()) {
            setAbilityScheduleAt(BossAbility.SUMMON, gameTime + 20);
            return false;
        }
        beginAction(BossAbility.SUMMON, phase.getSummonAnimation(),
                phase.getSummonActionDelayTicks(), gameTime, null, data, phase);
        setAbilityScheduleAt(BossAbility.SUMMON, gameTime + phase.getSummonActionDelayTicks() + rageDown(phase.getSummonCooldownTicks()));
        return true;
    }

    void beginAction(BossAbility action, String animation, int actionDelay, long gameTime,
                             LivingEntity target, TeleportPathData data, BossPhaseData phase) {
        pendingAction = action;
        pendingTargetId = target == null ? -1 : target.getId();
        pendingLeadTicks = telegraphLead(data, action, actionDelay);
        beginCastRoot(data, phase, action);
        if (pendingLeadTicks <= 0 && actionDelay <= 0) {
            playAnimation(animation);
            if (npc.level() instanceof ServerLevel level) {
                executePendingAction(level, data, phase, gameTime);
            }
            clearPendingAction();
            busyUntil = Math.max(busyUntil, gameTime + POST_ACTION_LOCK_TICKS);
            holdCastRootThroughLock(gameTime);
            return;
        }
        pendingActionAt = gameTime + pendingLeadTicks + actionDelay;
        if (pendingLeadTicks > 0) {
            // The animation is deliberately left standing: it is cut to the length of the
            // wind-up, and stretching it over the warning would leave the swing playing
            // after the hit. The warning is mark and sound only.
            pendingWarningEndsAt = gameTime + pendingLeadTicks;
            pendingAnimation = animation;
        } else {
            playAnimation(animation);
        }
        announceTelegraph(data, action);
        // Painted here as well as on the clock, so the mark is up on the very tick the boss
        // commits rather than a tick into a wind-up that may only last a handful.
        if (npc.level() instanceof ServerLevel level) {
            paintTelegraph(level, data);
        }
    }

    /**
     * Paints what the boss is about to do, for as long as it is winding up.
     *
     * <p>The wind-up is the gap {@link #beginAction} opens between the animation starting and
     * {@link #executePendingAction} firing, so the mark needs no clock of its own: it is up
     * for exactly that gap and stops on the tick the ability lands.</p>
     */
    private void tickTelegraph(ServerLevel level, TeleportPathData data, long gameTime) {
        if (pendingAction == BossAbility.NONE || gameTime >= pendingActionAt
                || gameTime % TELEGRAPH_INTERVAL_TICKS != 0L) {
            return;
        }
        paintTelegraph(level, data);
    }

    private void paintTelegraph(ServerLevel level, TeleportPathData data) {
        int ability = pendingAction.kind();
        if (ability < 0) {
            return;
        }
        boolean warns = telegraphs(data, ability);
        // The gravity ring is the mechanic - where to be standing, or not, when the field
        // opens - so it is painted whatever the warning settings say, the way a geyser's fuse
        // is: an edge nobody can see is not a warning left off, it is a trap. The name, the
        // note and the aura still go through the settings like everyone else's.
        boolean fieldEdge = pendingAction == BossAbility.GRAVITY;
        // The take cover strike is nothing but its wind-up, so the whole warning is
        // unconditional: the countdown, the aura on the boss and the shelters on the floor
        // are the only way anyone ever learns the arena is about to be hit. Only the note
        // and the lead still go through the settings.
        boolean cover = pendingAction == BossAbility.COVER;
        if (cover) {
            // Before the audience check: it goes to whoever the strike can reach, and the
            // strike reaches further than a mark on the floor can be seen from.
            coverRuntime.announceCountdown(level, pendingActionAt);
        }
        if (pendingAction == BossAbility.HUNT) {
            // Before the audience check as well, and whatever the warning settings say: the
            // prey has to know it was picked, or the chase is only a boss that suddenly runs.
            huntRuntime.announce(pendingTarget(level));
        }
        if (!warns && !fieldEdge && !cover) {
            return;
        }
        // Decoration only, so an arena with nobody in it costs nothing to warn.
        if (level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(),
                TELEGRAPH_AUDIENCE_RANGE, false) == null) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(ability);
        if (fieldEdge || cover || data.isTelegraphZone()) {
            drawTelegraphZone(level, data, ability, dust);
        }
        if (cover || (warns && data.isTelegraphAura())) {
            BossTelegraphUtil.aura(level, npc, dust);
        }
    }

    /**
     * The one-off half of the warning: a note and a name, both at the moment the boss
     * commits rather than on every tick the wind-up runs for.
     */
    private void announceTelegraph(TeleportPathData data, BossAbility action) {
        int ability = action.kind();
        if (ability < 0 || !telegraphs(data, ability)) {
            return;
        }
        if (data.isTelegraphSound() && npc.level() instanceof ServerLevel level) {
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE,
                    TELEGRAPH_SOUND_VOLUME, TELEGRAPH_SOUND_PITCH);
        }
        if (!data.isTelegraphAnnounce()) {
            return;
        }
        // In the ability's own colour, so the name and the shape on the floor read as one
        // warning rather than as two.
        Component name = Component.translatable(BossAbilityKind.LABELS[ability])
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(ability)));
        // The audience the countdown already goes to: whoever this fight belongs to.
        for (ServerPlayer player : timerBossEvent().getPlayers()) {
            player.displayClientMessage(name, true);
        }
    }

    /** The ground the ability being wound up is about to cover. */
    private void drawTelegraphZone(ServerLevel level, TeleportPathData data, int ability,
                                   DustParticleOptions dust) {
        BossPhaseData phase = data.getPhase(currentPhase);
        switch (pendingAction) {
            case GROUND_ATTACK -> BossTelegraphUtil.ring(level, npc.position(),
                    phase.getAreaAttackRadius(), dust);
            case LINE_ATTACK -> {
                if (committedAxis != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), committedAxis,
                            phase.getLineAttackLength(), phase.getLineAttackWidth(),
                            phase.getLineAttackSideWidth(), dust,
                            BossTelegraphUtil.fadedDust(ability));
                }
            }
            case BOULDER -> {
                if (committedAxis != null) {
                    // As wide as the stone itself and with no softer flank: standing a step
                    // outside this corridor really is standing clear.
                    BossTelegraphUtil.corridor(level, npc.position(), committedAxis,
                            phase.getBoulderRange(), phase.getBoulderScale() / 10.0D,
                            0.0D, dust, BossTelegraphUtil.fadedDust(ability));
                }
            }
            case MELEE_ATTACK -> BossTelegraphUtil.arc(level, npc.position(),
                    phase.getMeleeAttackRange(), npc.getYRot(), TELEGRAPH_MELEE_HALF_ANGLE, dust);
            // The hunt marks its prey the way the aimed abilities do: the line says who was
            // picked, which is the one thing everybody else needs to know.
            case RANGED_ATTACK, FLUID_SPIT, CAPTURE, HUNT ->
                    drawTelegraphTargetZone(level, data, pendingTarget(level), dust);
            // The cocoon marks everyone it is about to close round, the way the marks do:
            // the ring is where the shell will stand, which is where the rescue will be.
            case HOOK, GEYSER, MARK, COCOON -> {
                drawTelegraphTargetZone(level, data, pendingTarget(level), dust);
                for (int id : pendingExtraTargets) {
                    if (level.getEntity(id) instanceof LivingEntity victim) {
                        drawTelegraphTargetZone(level, data, victim, dust);
                    }
                }
            }
            case SUMMON -> drawTelegraphSpawnRings(level, phase, dust);
            // The field is centred on the boss and the ring is its edge: out of it for the
            // pull and the throw, into it for nobody.
            case GRAVITY -> BossTelegraphUtil.ring(level, npc.position(), phase.getGravityRadius(), dust);
            // The ring is how far the beams reach, and the lines are where they start: a
            // player has to know which way round they will come.
            case BEAM -> {
                BossTelegraphUtil.ring(level, npc.position(), phase.getBeamLength(), dust);
                BossBeamScheduler.paintStart(level, npc, committedYaw, phase.getBeamCount(),
                        phase.getBeamLength(), phase.isBeamStopsAtWalls());
            }
            // The shelters, where the wind-up put them; under the sight rule there are none,
            // and the cover is whatever the arena was built with.
            case COVER -> coverRuntime.drawShelters(level, dust);
            case TETHER -> {
                if (phase.getTetherAnchor() == BossPhaseData.TETHER_ANCHOR_BOSS) {
                    // The ring is the leash's length: get past it and the leash is broken.
                    BossTelegraphUtil.ring(level, npc.position(), phase.getTetherBreakDistance(), dust);
                } else if (!data.isTelegraphAura()) {
                    // A leash to a spot or to a partner has no ground to mark, so the boss
                    // itself lights up instead - here only when the style is not doing it anyway.
                    BossTelegraphUtil.aura(level, npc, dust);
                }
            }
            case LEAP -> {
                BossPhaseData leaping = leap.phaseOf(data);
                Vec3 landing = leap.destination();
                if (leaping != null && landing != null) {
                    BossTelegraphUtil.ring(level, landing, leaping.getLeapImpactRadius(), dust);
                }
            }
            default -> {
                // A teleport picks its point as it goes, so there is nothing to promise in
                // advance, and NONE never gets this far.
            }
        }
    }

    /**
     * Who an aimed ability has picked, and the ground that puts at risk.
     *
     * <p>A line on its own says which player is being aimed at and nothing at all about
     * where they should not be standing, which is no use to the one player it matters to.
     * The ring is walked from where the victim is on this very tick, so someone running
     * sees the zone travel with them rather than a mark left on the spot they were called
     * out from.</p>
     */
    private void drawTelegraphTargetZone(ServerLevel level, TeleportPathData data,
                                         LivingEntity target, DustParticleOptions dust) {
        if (target == null) {
            return;
        }
        drawTelegraphLine(level, target, dust);
        BossTelegraphUtil.ring(level, target.position(), data.getTelegraphZoneRadius(), dust);
    }

    /** The line an aimed ability is about to run along, from the same two points it uses. */
    private void drawTelegraphLine(ServerLevel level, LivingEntity target, DustParticleOptions dust) {
        if (target == null) {
            return;
        }
        BossTelegraphUtil.line(level, new Vec3(npc.getX(), npc.getEyeY() - 0.2D, npc.getZ()),
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), dust);
    }

    /**
     * A small ring on every spot a minion is about to climb out of.
     *
     * <p>A phase that scatters its minions has no points to mark, so its spawn radius is
     * ringed instead: the warning still says where not to be standing.</p>
     */
    private void drawTelegraphSpawnRings(ServerLevel level, BossPhaseData phase,
                                         DustParticleOptions dust) {
        int drawn = 0;
        if (phase.getMinionSpawnMode() != BossPhaseData.MINION_SPAWN_RANDOM_RADIUS) {
            for (BossMinionSpawnPoint point : phase.getMinionSpawnPoints().entries()) {
                if (drawn >= TELEGRAPH_MAX_SPAWN_RINGS) {
                    break;
                }
                if (point.isEnabled()) {
                    BossTelegraphUtil.ring(level, minionSpawns.pointAnchor(point),
                            TELEGRAPH_SPAWN_RING_RADIUS, dust);
                    drawn++;
                }
            }
        }
        if (drawn == 0) {
            BossTelegraphUtil.ring(level, npc.position(), phase.getMinionRadius(), dust);
        }
    }

    /**
     * Whether this ability warns at all: the master switch, its own bit of the mask, and the
     * leap's older per-phase flag.
     */
    private boolean telegraphs(TeleportPathData data, int ability) {
        if (!data.isTelegraphEnabled() || !data.isTelegraphAbility(ability)) {
            return false;
        }
        // The leap had a mark of its own before there was a general warning. That flag stays
        // on as a per-phase override, so a boss already set up without one keeps its silence.
        if (ability != BossAbilityKind.LEAP) {
            return true;
        }
        BossPhaseData phase = leap.phaseOf(data);
        return phase == null || phase.isLeapTelegraph();
    }

    /**
     * Ticks of plain warning to put in front of a wind-up too short to react to.
     *
     * <p>Zero for an ability nothing is drawn for: a pause nobody can see is not a warning,
     * it is the boss standing there doing nothing.</p>
     */
    private int telegraphLead(TeleportPathData data, BossAbility action, int actionDelay) {
        int ability = action.kind();
        if (ability < 0 || !telegraphs(data, ability)) {
            return 0;
        }
        return Math.max(0, data.getTelegraphLeadTicks() - actionDelay);
    }

    /**
     * Closes the warning phase: the target gets one last look, and only then does the
     * wind-up animation start, in step with the hit it belongs to.
     *
     * @return false when the ability was dropped because whoever it was aimed at got out
     */
    private boolean endTelegraphWarning(ServerLevel level, TeleportPathData data,
                                        BossPhaseData phase, long gameTime) {
        // Zero unless something held the boss - a rage lock, a carry - past the moment the
        // warning was due to end.
        long overdue = gameTime - pendingWarningEndsAt;
        pendingWarningEndsAt = NOT_SCHEDULED;
        if (data.isTelegraphDodge() && !pendingTargetStillValid(level, phase)) {
            BossAbility dodged = pendingAction;
            endCastRoot();
            clearPendingAction();
            // A short retry rather than the whole cooldown: a boss left standing for ten
            // seconds because somebody stepped aside is a worse fight than the one this
            // replaced.
            bringAbilityScheduleForward(dodged, gameTime + TELEGRAPH_DODGE_RETRY_TICKS);
            return false;
        }
        playAnimation(pendingAnimation);
        // The wind-up is measured off the animation, so a warning held past its end drags
        // the hit along with it instead of landing on a swing that has only just started.
        pendingActionAt += overdue;
        pendingAnimation = "";
        return true;
    }

    /**
     * Whether the ability still has somebody to land on, judged by the rule that picked
     * them in the first place.
     *
     * <p>This is the whole point of the warning: the same check that chose the victim gets
     * to say, once they have had their time, whether they got away with it.</p>
     */
    private boolean pendingTargetStillValid(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = pendingTarget(level);
        return switch (pendingAction) {
            case GROUND_ATTACK -> hasAreaTargets(level, phase);
            case GRAVITY -> gravity.hasTargets(level, phase);
            // Nobody left inside the beams' reach is a sweep not worth switching on.
            case BEAM -> beam.hasTargets(level, phase);
            case RANGED_ATTACK -> isValidRangedTarget(target, phase)
                    && ProjectileEntityUtil.canShoot(npc);
            case MELEE_ATTACK -> isValidMeleeTarget(target, phase);
            case FLUID_SPIT -> fluidSpit.isValidTarget(target, phase);
            case HOOK -> hasWoundUpVictim(level, candidate -> hook.isValidTarget(candidate, phase));
            case GEYSER -> hasWoundUpVictim(level, candidate -> geyser.isValidTarget(candidate, phase));
            case TETHER -> hasWoundUpVictim(level, candidate -> tether.isValidTarget(candidate, phase));
            case MARK -> hasWoundUpVictim(level, mark::isValidTarget);
            case COCOON -> hasWoundUpVictim(level, cocoon::isValidTarget);
            case CAPTURE -> capture.isValidTarget(target, phase);
            // A prey that got out of reach before the boss even set off is a hunt not worth
            // starting; one that got out afterwards ends it on its own.
            case HUNT -> huntRuntime.isValidTarget(target, settings());
            // A leap at a fixed spot lands there whoever is standing on it.
            case LEAP -> phase.getLeapMode() != BossPhaseData.LEAP_MODE_TARGET
                    || leap.isValidTarget(target, phase);
            // The corridor was committed to when the warning went up, so there is nothing
            // left to call off: walking out of it already is the dodge, and cancelling
            // would only bring the same strike back round in two seconds.
            case LINE_ATTACK, BOULDER -> true;
            // And the rain is not aimed at anybody at all: the ring is centred on the boss
            // and lands on ground, so there is nobody in particular who could have left it.
            case BOULDER_RAIN -> true;
            // Nor is the take cover strike: hiding is the dodge, and it is judged per victim
            // on the tick the strike lands, not by calling the whole thing off.
            case COVER -> true;
            // Nobody to dodge a summon, a teleport, or an action that is not running.
            case NONE, SUMMON, TELEPORT -> true;
        };
    }

    /**
     * Whether anyone a multi-victim ability wound up on is still worth landing it on.
     *
     * <p>Per victim rather than all or nothing: one of three walking out of range takes
     * only their own share with them, which is exactly what dodging should buy them.</p>
     */
    private boolean hasWoundUpVictim(ServerLevel level, Predicate<LivingEntity> valid) {
        if (valid.test(pendingTarget(level))) {
            return true;
        }
        for (int id : pendingExtraTargets) {
            if (level.getEntity(id) instanceof LivingEntity extra && valid.test(extra)) {
                return true;
            }
        }
        return false;
    }

    /** A schedule with every ability idle, which is what a boss between fights owns. */
    private static long[] newSchedule() {
        long[] schedule = new long[BossAbility.values().length];
        Arrays.fill(schedule, NOT_SCHEDULED);
        return schedule;
    }

    /**
     * Ticks left on one ability's cooldown, or 0 when it is ready or not on a clock. Shared
     * by the status lines, which all used to spell the same ternary out for themselves.
     */
    long abilityCooldownLeft(BossAbility ability, long gameTime) {
        long at = abilityScheduleAt(ability);
        return at == NOT_SCHEDULED ? 0L : at - gameTime;
    }

    /**
     * Records the victims beyond the first of a multi-target ability, for the wind-up to
     * mark and the hit to land on.
     *
     * <p>The first of the list is the pending target proper and is held by
     * {@link #beginAction}; these are the rest. Every multi-target ability wrote the same
     * three lines out for itself before this, which is three lines each to get the off-by-one
     * wrong in.</p>
     */
    void rememberExtraTargets(List<LivingEntity> targets) {
        pendingExtraTargets.clear();
        for (int i = 1; i < targets.size(); i++) {
            pendingExtraTargets.add(targets.get(i).getId());
        }
    }

    /** The victims beyond the first that the wind-up is holding, by entity id. */
    List<Integer> pendingExtraTargets() {
        return pendingExtraTargets;
    }

    /** Where the boss stood when it activated - the spot a reset, and an arena offset, read from. */
    double homeX() {
        return homeX;
    }

    double homeY() {
        return homeY;
    }

    double homeZ() {
        return homeZ;
    }

    /** The action being wound up or run right now, which is NONE between casts. */
    BossAbility pendingAction() {
        return pendingAction;
    }

    /**
     * Holds the boss in its after-cast pause until at least this tick, never letting a
     * shorter claim cut an existing one short.
     */
    void holdBusyUntil(long gameTime) {
        busyUntil = Math.max(busyUntil, gameTime);
    }

    /** When one ability is next allowed to start. */
    long abilityScheduleAt(BossAbility action) {
        return this.abilityScheduleAt[action.ordinal()];
    }

    void setAbilityScheduleAt(BossAbility action, long at) {
        if (action == BossAbility.NONE) {
            // Nothing was running, so there is no schedule to move. Kept as its own case
            // rather than as a slot nobody reads: an idle boss must not come away from
            // this holding a deadline.
            return;
        }
        this.abilityScheduleAt[action.ordinal()] = at;
    }

    /** Pulls a schedule in, and never pushes one out past the cooldown it already had. */
    private void bringAbilityScheduleForward(BossAbility action, long at) {
        long current = abilityScheduleAt(action);
        if (current == NOT_SCHEDULED || current > at) {
            setAbilityScheduleAt(action, at);
        }
    }

    /** Pays back the ticks an ability spent warning before its wind-up ever started. */
    private void delayAbilitySchedule(BossAbility action, int ticks) {
        long current = abilityScheduleAt(action);
        if (ticks > 0 && current != NOT_SCHEDULED) {
            setAbilityScheduleAt(action, current + ticks);
        }
    }

    private void executePendingAction(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        AbilityPerformer performer = ABILITY_PERFORMERS.get(pendingAction);
        if (performer != null) {
            performer.perform(this, level, data, phase, gameTime);
        }
    }

    /** The summon, plus the two clocks a fresh wave of minions resets. */
    private void performSummon(ServerLevel level, BossPhaseData phase) {
        minionSpawns.summon(level, phase);
        invulnerableSummonedOnce = true;
        minionAliveScanAt = NOT_SCHEDULED;
    }

    void playAnimation(String animation) {
        if (animation == null || animation.isBlank()) {
            return;
        }
        try {
            RawAnimation raw = RawAnimation.begin().then(animation.trim(), Animation.LoopType.PLAY_ONCE);
            // Only whoever has the boss loaded: a client without it drops the packet anyway.
            NetworkWrapper.sendToTracking(npc, new PacketSyncAnimation(npc.getId(), raw));
        } catch (Throwable error) {
            LOGGER.warn("Could not play boss animation {} for NPC {}: {}", animation,
                    npc.getName().getString(), error.getMessage());
        }
    }

    private boolean hasAreaTargets(ServerLevel level, BossPhaseData phase) {
        return !getAreaTargets(level, phase).isEmpty();
    }

    /**
     * Everyone within {@code reach} of {@code centre} this boss may catch with {@code ability},
     * plus whatever else the caller's own rule demands of them.
     *
     * <p>One scan behind every sweep an ability makes, because the box, the round distance
     * test and the immunity door are the same question every time and used to be answered by
     * five copies of it. What differs between the abilities is only {@code extra}.</p>
     */
    private List<LivingEntity> victimsAround(ServerLevel level, Vec3 centre, double reach,
                                             int ability, Predicate<LivingEntity> extra) {
        double reachSquared = reach * reach;
        // A whole block of slack on the box: it only pre-filters, and an entity standing
        // exactly on the rim should still be handed to the distance test below.
        AABB box = new AABB(centre, centre).inflate(reach + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, box, target ->
                target != npc && target.isAlive()
                        && target.position().distanceToSqr(centre) <= reachSquared
                        && isAbilityTarget(target, ability)
                        && extra.test(target));
    }

    /**
     * The rule the abilities that sweep the whole arena share: they keep to the species the
     * boss is set to fight and pass over anyone hidden by their own totems. A field that
     * throws the cattle about, or drags a warded boss out of its formation, reads as a bug
     * rather than as a mechanic.
     */
    private List<LivingEntity> arenaVictims(ServerLevel level, Vec3 centre, double reach, int ability) {
        TeleportPathData data = settings();
        return victimsAround(level, centre, reach, ability,
                target -> matchesAbilityTargetKind(target, data)
                        && !BossMechanicUtil.hiddenByTotems(target));
    }

    private List<LivingEntity> getAreaTargets(ServerLevel level, BossPhaseData phase) {
        return getTargetsAround(level, npc.position(), phase.getAreaAttackRadius(), BossAbilityKind.AREA);
    }

    /**
     * Everyone an area hit centred on {@code centre} is allowed to catch.
     *
     * <p>Split out of {@link #getAreaTargets} so the leap slam, which lands wherever the
     * boss came down rather than where it stands now, cannot end up with its own idea of
     * who counts as an enemy - which is also why the ability being swept for is handed in
     * rather than assumed.</p>
     */
    List<LivingEntity> getTargetsAround(ServerLevel level, Vec3 centre, double radius,
                                                int ability) {
        return victimsAround(level, centre, radius, ability, target -> true);
    }

    /**
     * Everyone an eruption at this spot may catch, judged by this boss.
     *
     * <p>Asked for by {@link BossGeyserScheduler}, which runs the eruption seconds after the
     * cast and has no idea on its own who this boss counts as an enemy.</p>
     */
    List<LivingEntity> geyserVictims(ServerLevel level, Vec3 centre, double radius) {
        return getTargetsAround(level, centre, radius, BossAbilityKind.GEYSER);
    }

    /**
     * Everyone a gravity field around {@code centre} may move, judged by this boss.
     *
     * <p>Asked for by {@link BossGravityScheduler} on every tick the field is open, for the
     * reason {@link #geyserVictims} exists.</p>
     */
    List<LivingEntity> gravityVictims(ServerLevel level, Vec3 centre, double radius) {
        return arenaVictims(level, centre, radius, BossAbilityKind.GRAVITY);
    }

    /**
     * Everyone the beams turning round {@code centre} may catch, judged by this boss.
     *
     * <p>Asked for by {@link BossBeamScheduler} on every tick the sweep runs, and by the
     * cast before it spends a cooldown.</p>
     */
    List<LivingEntity> beamVictims(ServerLevel level, Vec3 centre, double reach) {
        return arenaVictims(level, centre, reach, BossAbilityKind.BEAM);
    }

    /**
     * Everyone a mark going off at this spot counts and hurts, judged by this boss.
     *
     * <p>Asked for by {@link BossMarkScheduler} seconds after the cast, for the reason
     * {@link #geyserVictims} exists. One list rather than two: a gather takes its head count
     * and shares its damage out over exactly the same people, or {@code damage / count} would
     * stop being what anybody actually took.</p>
     *
     * <p>Which is also why players have to belong to this fight rather than merely be standing
     * in the ring. The circle is a problem the party is being set, and a passer-by walking
     * through it can neither be what solved it nor be made to pay for it. Npcs come in by the
     * ordinary victim rules and by the species the boss is set to fight, so one aimed only at
     * players never counts the cattle as bodies in the circle.</p>
     */
    List<LivingEntity> markVictims(ServerLevel level, Vec3 centre, double radius) {
        TeleportPathData data = settings();
        return victimsAround(level, centre, radius, BossAbilityKind.MARK,
                target -> (!(target instanceof Player player) || isEncounterParticipant(player))
                        && matchesAbilityTargetKind(target, data));
    }

    /**
     * Everyone a take cover strike from {@code centre} may land on, judged by this boss.
     *
     * <p>Who got out of the way is decided per victim at the strike, not here - this is only
     * who is in reach.</p>
     */
    List<LivingEntity> coverVictims(ServerLevel level, Vec3 centre, double range) {
        return arenaVictims(level, centre, range, BossAbilityKind.COVER);
    }

    /** Whether this player is one of the people this boss' fight is being run against. */
    boolean isEncounterParticipant(Player player) {
        return encounterParticipants.contains(player.getUUID());
    }

    /**
     * Whether a boulder this boss launched may run this one over.
     *
     * <p>Asked by {@link com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder} every tick of
     * its flight, for the reason {@link #geyserVictims} exists: the hits land seconds after
     * the cast, and the entity has no idea on its own who this boss counts as an enemy. The
     * species filter is applied too, so a boss aimed only at players rolls straight through
     * the cattle.</p>
     *
     * <p>The ability is handed in rather than assumed: the same stone falls for the boulder
     * rain, and an npc made immune to one of the two must not be passed over by the other.</p>
     */
    public boolean isBoulderVictim(LivingEntity target, int ability) {
        return target != npc && target.isAlive()
                && matchesAbilityTargetKind(target, settings())
                && isAbilityTarget(target, ability);
    }

    /**
     * Whether this candidate is a species the boss is configured to aim at.
     *
     * <p>Deliberately only the species filter: whether the boss may hit something at all
     * stays in {@link #isAreaTarget}, so a hook and an area slam can never end up with
     * different ideas of who counts as an enemy.</p>
     */
    boolean matchesAbilityTargetKind(LivingEntity candidate, TeleportPathData data) {
        if (candidate instanceof Player) {
            return true;
        }
        return switch (data.getAbilityTargetKind()) {
            case TeleportPathData.ABILITY_TARGET_ALL -> true;
            case TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS ->
                    candidate instanceof EntityNPCInterface;
            default -> false;
        };
    }

    /**
     * Everyone one ability is allowed to pick, looked up in a box around the boss.
     *
     * <p>{@code searchRange} is that ability's own maximum reach, so the box is only a
     * cheap pre-filter for the range and sight tests {@code canHit} runs anyway - it is
     * what keeps the boss from sweeping the whole world every time it wants to attack.</p>
     *
     * <p>Somebody hidden by their own totems drops out here rather than inside {@code canHit},
     * because this is the aiming list: an area sweep asks {@link #isAbilityTarget} instead
     * and is meant to catch them anyway.</p>
     */
    List<LivingEntity> abilityCandidates(ServerLevel level, double searchRange,
                                                 Predicate<LivingEntity> canHit) {
        TeleportPathData data = settings();
        AABB box = new AABB(npc.position(), npc.position()).inflate(searchRange + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, box, candidate -> candidate != npc
                && matchesAbilityTargetKind(candidate, data)
                && !BossMechanicUtil.hiddenByTotems(candidate) && canHit.test(candidate));
    }

    boolean isAreaTarget(LivingEntity target) {
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (BossMinionUtil.isMinionOf(target, npc)) return false;
        if (BossTotemUtil.isTotemOf(target, npc)) return false;
        return npc.canAttack(target) && !npc.isAlliedTo(target);
    }

    /**
     * Whether one ability may pick this victim: the boss' own idea of who counts as an enemy,
     * plus the victim's say in it.
     *
     * <p>An npc immune to an ability is never chosen by it, not merely left unhurt. A hook
     * that reels in somebody it cannot move, or a grab closing on somebody it cannot hold,
     * would spend the boss' turn on nothing and read as a bug.</p>
     */
    boolean isAbilityTarget(LivingEntity target, int ability) {
        return isAreaTarget(target) && !BossAbilityDamageUtil.isImmune(target, ability);
    }

    private void performAreaAttack(ServerLevel level, BossPhaseData phase) {
        // Purely for show, and started before the hits so the wave leaves at the same moment
        // the damage lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, npc.position(), phase);
        for (LivingEntity target : getAreaTargets(level, phase)) {
            BossAbilityDamageUtil.hit(target, BossAbilityKind.AREA, npc,
                    rageUp(phase.getAreaAttackDamage()), phase.getAreaAttackEffects(),
                    rageUp(phase.getAreaAttackKnockback()),
                    npc.getX() - target.getX(), npc.getZ() - target.getZ());
        }
    }

    private boolean isValidRangedTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.RANGED)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getRangedAttackMinRange();
        double max = phase.getRangedAttackMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) return false;
        return !npc.ais.directLOS || npc.canNpcSee(target) || npc.stats.ranged.getFireType() == 2;
    }

    private boolean isValidMeleeTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.MELEE)) return false;
        double range = phase.getMeleeAttackRange() + (npc.getBbWidth() + target.getBbWidth()) * 0.5D;
        return npc.distanceToSqr(target) <= range * range;
    }

    /**
     * Picks who this one ability goes after.
     *
     * <p>{@code canHit} is the ability's own validity check, so the candidate list already
     * respects its range window and line-of-sight rule. That is what makes FARTHEST useful:
     * it returns the player at the back of the room only while that player is still inside
     * the attack's maximum range, never someone the boss could not reach anyway.</p>
     *
     * <p>Which species may reach that list is the boss' ability-target setting, because
     * letting every living thing in would mean FARTHEST happily settling on a cow thirty
     * blocks out instead of the tank. When nobody qualifies the NPC falls back to its
     * normal combat target, so turning a mode on never makes an ability quieter than MAIN
     * would have been.</p>
     */
    LivingEntity selectAbilityTarget(ServerLevel level, int mode, double searchRange,
                                             Predicate<LivingEntity> canHit) {
        LivingEntity main = npc.getTarget();
        // The main target goes through the same hiding check every mode's candidates do, or
        // MAIN would be the one way an ability could still settle on a shielded statue.
        LivingEntity fallback = main != null && !BossMechanicUtil.hiddenByTotems(main)
                && canHit.test(main) ? main : null;
        if (mode == BossTargetMode.MAIN) {
            return fallback;
        }

        List<LivingEntity> candidates = abilityCandidates(level, searchRange, canHit);
        if (candidates.isEmpty()) {
            return fallback;
        }
        if (mode == BossTargetMode.RANDOM) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }

        boolean farthest = mode == BossTargetMode.FARTHEST;
        LivingEntity best = null;
        double bestDistance = farthest ? -1.0D : Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = npc.distanceToSqr(candidate);
            if (farthest ? distance > bestDistance : distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The multi-victim form of {@link #selectAbilityTarget}. Candidates are ordered by the
     * same rule, so FARTHEST with a count of three grabs the three victims furthest away.
     */
    List<LivingEntity> selectAbilityTargets(ServerLevel level, int mode, double searchRange,
                                                    Predicate<LivingEntity> canHit, int count) {
        List<LivingEntity> result = new ArrayList<>();
        if (count <= 1) {
            LivingEntity single = selectAbilityTarget(level, mode, searchRange, canHit);
            if (single != null) {
                result.add(single);
            }
            return result;
        }

        List<LivingEntity> candidates = abilityCandidates(level, searchRange, canHit);
        if (candidates.isEmpty()) {
            LivingEntity fallback = selectAbilityTarget(level, mode, searchRange, canHit);
            if (fallback != null) {
                result.add(fallback);
            }
            return result;
        }
        if (mode == BossTargetMode.MAIN) {
            LivingEntity main = selectAbilityTarget(level, mode, searchRange, canHit);
            if (main != null) {
                result.add(main);
                candidates.remove(main);
            }
        }
        if (mode == BossTargetMode.RANDOM) {
            Util.shuffle(candidates, npc.getRandom());
        } else {
            boolean farthest = mode == BossTargetMode.FARTHEST;
            candidates.sort((left, right) -> {
                int order = Double.compare(npc.distanceToSqr(left), npc.distanceToSqr(right));
                return farthest ? -order : order;
            });
        }
        int remaining = count - result.size();
        for (int i = 0; i < Math.min(remaining, candidates.size()); i++) {
            result.add(candidates.get(i));
        }
        return result;
    }

    LivingEntity pendingTarget(ServerLevel level) {
        if (pendingTargetId < 0) return null;
        Entity entity = level.getEntity(pendingTargetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private void performRangedAttack(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = pendingTarget(level);
        if (!isValidRangedTarget(target, phase) || !ProjectileEntityUtil.canShoot(npc)) return;
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        DataRanged ranged = npc.stats.ranged;
        int previousDamage = ranged.getStrength();
        try {
            ranged.setStrength(rageUp(phase.getRangedAttackDamage()));
            double distanceSquared = npc.distanceToSqr(target);
            boolean indirect = ranged.getFireType() == 2
                    ? !npc.getSensing().hasLineOfSight(target)
                    : ranged.getFireType() == 1
                    && distanceSquared > phase.getRangedAttackMaxRange() * phase.getRangedAttackMaxRange() / 2.0D;
            npc.performRangedAttack(target, indirect ? 1.0F : 0.0F);
        } catch (Throwable error) {
            LOGGER.warn("Could not perform configured ranged attack for NPC {}: {}",
                    npc.getName().getString(), error.getMessage());
        } finally {
            ranged.setStrength(previousDamage);
        }
    }

    private void performMeleeAttack(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = pendingTarget(level);
        if (!isValidMeleeTarget(target, phase)) return;
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // Swinging makes the model play its generic attack animation from the "Attack"
        // list. With a phase animation configured that second animation is queued behind
        // the one already running, so it only becomes visible after the hit has landed -
        // which reads as the animation playing after the damage instead of before it.
        if (phase.getMeleeAttackAnimation().isEmpty()) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
        BossAbilityDamageUtil.hit(target, BossAbilityKind.MELEE, npc,
                rageUp(phase.getMeleeAttackDamage()), phase.getMeleeAttackEffects(),
                rageUp(phase.getMeleeAttackKnockback()),
                npc.getX() - target.getX(), npc.getZ() - target.getZ());
    }

    /**
     * Drops whatever the boss was winding up, warning phase and all, leaving the schedules
     * alone. Everything the action was holding goes in one place so a new stage of it - the
     * warning was the last one - cannot be left behind by one of the callers.
     */
    private void clearPendingAction() {
        pendingAction = BossAbility.NONE;
        pendingActionAt = NOT_SCHEDULED;
        pendingWarningEndsAt = NOT_SCHEDULED;
        pendingAnimation = "";
        pendingLeadTicks = 0;
        pendingTargetId = -1;
        pendingExtraTargets.clear();
        committedAxis = null;
        committedYaw = 0.0F;
        coverRuntime.clear();
        leap.forgetPlanIfGrounded();
    }

    private void cancelPendingAndSchedules() {
        // A cancelled wind-up frees the boss at once; only a completed one holds the pin
        // on through its after-pause.
        endCastRoot();
        clearPendingAction();
        // Every clock at once, the teleport's included: a boss that lost its target owes
        // nobody the attack it was halfway to. One line rather than twenty, so an ability
        // added later cannot be the one left running after the fight ended.
        Arrays.fill(abilityScheduleAt, NOT_SCHEDULED);
        // A chase does not outlive the phase, the fight or the boss that started it, and
        // every one of those ends up here. Nor does a sweep.
        huntRuntime.end();
        BossBeamScheduler.clearBoss(npc);
    }

    private void reset() {
        bar.stop();
        active = false;
        highestPhaseReached = 0;
        currentPhase = -1;
        clearInvulnerability();
        rage.clear();
        healthScalingRuntime.clear(settings(), false);
        outOfCombatSince = NOT_SCHEDULED;
        encounterResetDone = false;
        hook.clear();
        hazardRuntime.clear();
        barrierRuntime.clear();
        BossGeyserScheduler.clearBoss(npc);
        BossMarkScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        BossGravityScheduler.clearBoss(npc);
        leap.clear();
        BossCaptureManager.releaseByBoss(npc);
        BossTetherManager.releaseByBoss(npc);
        BossCocoonManager.releaseByBoss(npc);
        busyUntil = 0L;
        cancelPendingAndSchedules();
        path.clear();
        nextAbilityPriority = 0;
        targeting.reset();
        clearEncounter();
        totems.clearRuntime();
        minionSpawns.clear();
        cocoon.clear();
        geyser.clear();
        boulder.clear();
        fluidSpit.clear();
    }
}
