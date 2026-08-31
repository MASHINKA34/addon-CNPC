package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.BossTotemEntry;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossBarStyle;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossLink;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossTimer;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncHookCord;
import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataRanged;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
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
    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");
    private static final long NOT_SCHEDULED = Long.MIN_VALUE;
    /** How often a controller whose tick keeps throwing is allowed to say so in the log. */
    private static final int TICK_FAILURE_LOG_INTERVAL_TICKS = 200;
    private static final int POST_ACTION_LOCK_TICKS = 10;
    private static final int ABILITY_COUNT = 12;
    /** Quietest gap that still reads as one clang per hit rather than a rattle. */
    private static final int BLOCK_FEEDBACK_INTERVAL_TICKS = 5;
    /**
     * The leap is a plain ballistic push, so its speed has to be worked out against the
     * numbers vanilla actually moves a living entity by: every airborne tick the position
     * advances by the current speed, then gravity is taken off the vertical one and both
     * are scaled by their drag. Solving that discretely is what makes a leap land on its
     * mark instead of a good block short of it.
     */
    private static final double LEAP_GRAVITY = 0.08D;
    private static final double LEAP_VERTICAL_DRAG = 0.98D;
    /**
     * Trim on the horizontal speed, tunable in one place.
     *
     * <p>The flight length is counted in whole ticks while the boss touches down partway
     * through one, and it loses a sliver of speed to every corner it clips on the way, so
     * the arc lands a few percent short of its mark. Measured against a tick-for-tick
     * replay of the movement above: without it a leap is up to 4% short, with it the error
     * is inside 3% either way, which the smallest slam radius swallows.</p>
     */
    private static final double LEAP_REACH_CORRECTION = 1.03D;
    /** Enough to clear the 64 block height ceiling; only the solver's search uses it. */
    private static final double LEAP_MAX_RISE_SPEED = 5.0D;
    /**
     * A badly set up arena must not fling the boss across the world. Far above anything a
     * sane jump asks for: even a hundred block leap only needs about 1.3 blocks a tick.
     */
    private static final double LEAP_MAX_HORIZONTAL_SPEED = 4.0D;
    /** Ticks the boss gets to leave the floor before a leap counts as never started. */
    private static final int LEAP_LAUNCH_GRACE_TICKS = 5;
    /** Rough spacing between the landing marker's particles, in blocks. */
    private static final double LEAP_MARKER_SPACING = 0.7D;
    private static final int LEAP_MARKER_INTERVAL_TICKS = 4;
    /** How long the landing wave runs for; the leap has no length setting of its own. */
    private static final int LEAP_VFX_DURATION_TICKS = 20;
    /** Kept clear of the leash edge so a landing cannot start the reset countdown. */
    private static final double LEAP_LEASH_MARGIN = 1.5D;
    /** How often the wind-up mark is repainted. Every other tick reads as a steady shape. */
    private static final int TELEGRAPH_INTERVAL_TICKS = 2;
    /** With no player this close the mark cannot be seen, so it is not worth the particles. */
    private static final double TELEGRAPH_AUDIENCE_RANGE = 64.0D;
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
    /** The client counts down on its own, so the server only has to correct it now and then. */
    private static final int TIMER_SYNC_INTERVAL_TICKS = 5;
    private static final int TOTEM_RETRY_INTERVAL_TICKS = 20;
    private static final int TOTEM_LINK_DURATION_TICKS = 200;
    private static final int TOTEM_LINK_REFRESH_TICKS = 160;
    private static final ResourceLocation RAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "boss_rage");
    /** Health is deliberately absent: enrage makes the boss hit harder, not last longer. */
    private static final List<Holder<Attribute>> RAGE_ATTRIBUTES =
            List.of(Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE);
    private static final Set<TeleportPathController> INSTANCES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private enum PendingAction {
        NONE, TELEPORT, SUMMON, GROUND_ATTACK, RANGED_ATTACK, MELEE_ATTACK, FLUID_SPIT, HOOK, CAPTURE,
        LEAP, LINE_ATTACK, GEYSER, BOULDER, BOULDER_RAIN
    }

    private final EntityNPCInterface npc;
    private final ServerBossEvent bossEvent;
    private final Set<UUID> bossBarParticipants = new HashSet<>();
    /** Server-side encounter membership, independent of whether any boss bar is visible. */
    private final Set<UUID> encounterParticipants = new HashSet<>();
    private String activeBossBarStyle = BossBarStyles.NONE;
    private int activeBossBarScalePercent = TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT;
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
    private int scaledPlayerCount = 1;
    private int lockedPlayerCount;
    private long nextHealthScalingCheckAt = NOT_SCHEDULED;
    private int lastHealthScalingUpdateMode = -1;
    private int lastHealthScalingPlayerCap = -1;
    private int lastHealthScalingRecheckTicks = -1;
    private double baseMaxHealth;
    private boolean healthScalingApplied;
    private boolean healthScalingUnavailable;
    private long lastHealthScalingConfiguration = Long.MIN_VALUE;
    /** Game time the fight started at, or NOT_SCHEDULED while no encounter is running. */
    private long encounterStartedAt = NOT_SCHEDULED;
    /** Once set, stays set until the encounter ends - a phase change does not calm the boss. */
    private boolean rageActive;
    private byte lastTimerState = PacketSyncBossTimer.STATE_NONE;
    private long nextTimerSyncAt;
    private double lockedX;
    private double lockedZ;
    /** Where the boss stood when it activated - the spot a reset sends it back to. */
    private double homeX;
    private double homeY;
    private double homeZ;
    private long busyUntil;
    private long nextTeleportAt = NOT_SCHEDULED;
    private long nextSummonAt = NOT_SCHEDULED;
    private long nextGroundAttackAt = NOT_SCHEDULED;
    private long nextRangedAttackAt = NOT_SCHEDULED;
    private long nextMeleeAttackAt = NOT_SCHEDULED;
    private long nextFluidSpitAt = NOT_SCHEDULED;
    private long nextHookAt = NOT_SCHEDULED;
    private long nextCaptureAt = NOT_SCHEDULED;
    private long nextLeapAt = NOT_SCHEDULED;
    private long nextLineAttackAt = NOT_SCHEDULED;
    private long nextGeyserAt = NOT_SCHEDULED;
    private long nextBoulderAt = NOT_SCHEDULED;
    private long nextBoulderRainAt = NOT_SCHEDULED;

    /**
     * Which way the line strike being wound up is going to go, unit length and flat.
     *
     * <p>Fixed the moment the boss commits and never touched again: a corridor that swung
     * round after a running player would turn its own warning into a lie.</p>
     */
    private Vec3 lineAttackAxis;

    /**
     * Which way the boulder being wound up is going to travel, unit length and flat.
     * Committed the same way the line strike's axis is, and for the same reason: the
     * corridor on the floor is a promise.
     */
    private Vec3 boulderAxis;

    /** Where the leap being wound up or flown right now is meant to come down. */
    private Vec3 leapDestination;
    /** Phase the leap started in: the slam belongs to the settings that launched it. */
    private int leapPhaseIndex = -1;
    private boolean leapAirborne;
    /** The boss really left the floor, which is what makes a later onGround() a landing. */
    private boolean leapLeftGround;
    private long leapLaunchedAt = NOT_SCHEDULED;
    /** Game time the airborne safety net gives up at. */
    private long leapAirTimeoutAt = NOT_SCHEDULED;
    /** Horizontal speed the flight holds, re-applied every tick it stays in the air. */
    private double leapDriveX;
    private double leapDriveZ;

    /** Victims beyond the first, captured when a multi-target ability starts winding up. */
    private final List<Integer> pendingExtraTargets = new ArrayList<>();
    /** Entity id -> game time at which the drag ends. */
    private final List<HookPull> activePulls = new ArrayList<>();

    /** A gather point of null means "keep pulling toward the boss wherever it is". */
    private record HookPull(int targetId, long endsAt, double strength, double stopDistance,
                            Vec3 gatherPoint, String cordStyle) {
    }
    private PendingAction pendingAction = PendingAction.NONE;
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
    private int lastPathIndex = -1;
    private int pingPongDirection = 1;
    private int previousPathSize;
    private int nextAbilityPriority;
    private long nextRetargetAt = NOT_SCHEDULED;
    private long nextAggroZoneAt = NOT_SCHEDULED;
    private final Set<String> reportedBrokenMinionClones = new HashSet<>();
    private final Set<String> reportedBlockedMinionPoints = new HashSet<>();
    /** Phase index -> the last point that successfully spawned in round-robin order. */
    private final Map<Integer, Integer> minionRoundRobinCursor = new HashMap<>();
    private String reportedBrokenFluid = "";
    private String reportedBrokenGeyserFluid = "";
    private String reportedBrokenBoulderBlock = "";
    private String reportedBrokenBoulderRainBlock = "";
    private final Map<Integer, TotemRuntime> totemRuntime = new HashMap<>();
    private final Set<Integer> deadTotemSlots = new HashSet<>();
    private final Set<Integer> resetTotemHealthSlots = new HashSet<>();
    private final Set<Integer> reportedEmptyTotemSlots = new HashSet<>();
    private final Set<Integer> reportedBlockedTotemSlots = new HashSet<>();
    private final Set<String> reportedBrokenTotemClones = new HashSet<>();
    private boolean totemWaveActivated;
    private long totemActivationDeadline = NOT_SCHEDULED;
    private long nextTotemStructuralReconcileAt;
    /** Game time the shared totem scan below was collected on. */
    private long totemScanAt = NOT_SCHEDULED;
    /** Every loaded totem of this boss, collected at most once per tick and shared. */
    private List<Entity> totemScan = List.of();

    private static final class TotemRuntime {
        private UUID entityId;
        private long nextRespawnAt = NOT_SCHEDULED;
        private long nextLinkSyncAt;

        private TotemRuntime(UUID entityId) {
            this.entityId = entityId;
        }
    }

    public TeleportPathController(EntityNPCInterface npc) {
        this.npc = npc;
        this.bossEvent = new ServerBossEvent(npc.getDisplayName(), BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS);
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
                    removeTotemsOnBossDeath(inactiveLevel, data);
                } else if (!data.isEnabled()) {
                    removeConfiguredTotems(inactiveLevel);
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
        updateAggroZone(level, data, gameTime);
        updateNearestTarget(level, data, gameTime);
        if (hasCombatTarget()) {
            beginEncounter(level, gameTime, data);
        }
        tickHealthScalingPlayerCount(level, gameTime, data);
        tickHealthScaling(data);
        // A leash reset owns the rest of this tick, including already-due abilities.
        if (tickHomeLeash(level, gameTime, data)) {
            return;
        }
        tickCastRoot(gameTime);
        // Held and rooted are read in this order, not merged: the root has to keep its own
        // deadline so the last totem falling mid wind-up cannot cut the swing short, and the
        // hold has to outlive that deadline so the end of a cast cannot set the boss loose.
        if ((data.isStationary() || isTotemHeld()) && !leapAirborne) {
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
        tickHookPulls(level, gameTime);
        faceCombatTarget(data);
        // Runs before updatePhase so the phase it unlocks is switched to in this same tick,
        // and above the busy/no-target early returns so a locked boss cannot stay immune.
        tickInvulnerability(level, gameTime, data);
        updatePhase(level, gameTime, data);
        tickTotems(level, gameTime, data);
        tickRage(level, gameTime, data);
        updateBossBar(level, data);
        syncBossTimer(gameTime, data);
        BossPhaseData phase = data.getPhase(currentPhase);
        // Above the combat-only return and the busy gate on purpose: a leap already in the
        // air has to come down and land even if the boss loses its target mid flight.
        tickLeap(level, data, gameTime);
        // Above the busy gate and the pending block below on purpose: a wind-up has to stay
        // marked through a lock, and the mark has to stop on the tick the ability goes off.
        tickTelegraph(level, data, gameTime);

        if (data.isCombatOnly() && !hasCombatTarget()) {
            cancelPendingAndSchedules();
            return;
        }
        if (gameTime < busyUntil) {
            return;
        }
        if (pendingAction != PendingAction.NONE) {
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
        preparePath(points);
        scheduleMissingAbilities(gameTime, phase, points.size() >= 2);

        // A held boss is barred from the path as well as from walking it: leaving the spot the
        // totems pin it to is exactly what the hold is there to stop, however it is done.
        if (points.size() >= 2 && gameTime >= nextTeleportAt && !isTotemHeld()
                && (!isInvulnerable() || phase.isInvulnerableAllowTeleport())) {
            nextTeleportAt = NOT_SCHEDULED;
            beginAction(PendingAction.TELEPORT, phase.getTeleportPreparationAnimation(),
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

    private TeleportPathData settings() {
        return ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
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
        lastPathIndex = -1;
        previousPathSize = 0;
        // A boss that was left wounded starts the next fight straight in a later phase, so
        // the immune window has to be armed here too and not only on a phase change.
        initializeTotems(level, gameTime, data);
        enterPhase(gameTime, data, data.getPhase(currentPhase));
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
        lockedPlayerCount = countEligibleHealthScalingPlayers(level, data, false);
        scaledPlayerCount = cappedHealthScalingPlayerCount(lockedPlayerCount, data);
        lastHealthScalingUpdateMode = data.getHealthScalingUpdateMode();
        lastHealthScalingPlayerCap = data.getHealthScalingPlayerCap();
        lastHealthScalingRecheckTicks = data.getHealthScalingRecheckTicks();
        nextHealthScalingCheckAt = data.getHealthScalingUpdateMode()
                == TeleportPathData.HEALTH_SCALING_DYNAMIC
                ? gameTime + data.getHealthScalingRecheckTicks() : NOT_SCHEDULED;
        if (!data.isTotemsEnabled()) {
            return;
        }
        if (data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_START
                || data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER
                && currentPhase + 1 == data.getTotemActivationPhase()) {
            activateTotemWave(gameTime, data);
        } else if (data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_TIMER) {
            totemActivationDeadline = gameTime + data.getTotemActivationDelayTicks();
        }
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
        scaledPlayerCount = 1;
        lockedPlayerCount = 0;
        nextHealthScalingCheckAt = NOT_SCHEDULED;
        lastHealthScalingUpdateMode = -1;
        lastHealthScalingPlayerCap = -1;
        lastHealthScalingRecheckTicks = -1;
    }

    /** @return true when crossing the leash ended the encounter this tick */
    private boolean tickHomeLeash(ServerLevel level, long gameTime, TeleportPathData data) {
        // A leap in flight is not the boss wandering off: its landing spot was pulled
        // inside the radius before it pushed off, and with a vertical leash the arc over
        // the arena would otherwise reset the fight the boss is in the middle of.
        if (!data.isHomeLeashEnabled() || !encounterRunning || !active || !npc.isAlive() || leapAirborne) {
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

    private void initializeTotems(ServerLevel level, long gameTime, TeleportPathData data) {
        totemRuntime.clear();
        deadTotemSlots.clear();
        deadTotemSlots.addAll(BossTotemUtil.readDeadSlots(npc));
        resetTotemHealthSlots.clear();
        totemWaveActivated = false;
        totemActivationDeadline = NOT_SCHEDULED;
        nextTotemStructuralReconcileAt = 0L;
        reconcileTotemStructure(level, gameTime, data);
        adoptLoadedTotems(level, gameTime, data);
        if (data.isTotemsEnabled()
                && data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
            activateTotemWave(gameTime, data);
        } else {
            // A server stopped during a triggered wave can save its clones. The next load
            // must restore the configured trigger instead of leaving those clones visible.
            removeConfiguredTotems(level);
        }
    }

    private void tickTotems(ServerLevel level, long gameTime, TeleportPathData data) {
        if (!data.isTotemsEnabled()) {
            if (totemWaveActivated || !totemRuntime.isEmpty()) {
                removeConfiguredTotems(level);
                clearTotemRuntime();
            }
            return;
        }

        if (gameTime >= nextTotemStructuralReconcileAt) {
            nextTotemStructuralReconcileAt = gameTime + TOTEM_RETRY_INTERVAL_TICKS;
            reconcileTotemStructure(level, gameTime, data);
            adoptLoadedTotems(level, gameTime, data);
        }

        if (!totemWaveActivated) {
            int activation = data.getTotemActivationMode();
            if (activation == TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
                activateTotemWave(gameTime, data);
            } else if (encounterRunning
                    && activation == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_START) {
                activateTotemWave(gameTime, data);
            } else if (encounterRunning
                    && activation == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER
                    && currentPhase + 1 == data.getTotemActivationPhase()) {
                activateTotemWave(gameTime, data);
            } else if (encounterRunning
                    && activation == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_TIMER) {
                if (totemActivationDeadline == NOT_SCHEDULED) {
                    totemActivationDeadline = gameTime + data.getTotemActivationDelayTicks();
                } else if (!hasCombatTarget()) {
                    // Moving the deadline forward freezes the remaining duration exactly,
                    // matching the rage clock rather than buying a new full delay.
                    totemActivationDeadline++;
                } else if (gameTime >= totemActivationDeadline) {
                    activateTotemWave(gameTime, data);
                }
            }
        }
        if (!totemWaveActivated) {
            return;
        }

        for (BossTotemEntry entry : data.getTotems().entries()) {
            tickTotemSlot(level, gameTime, data, entry);
        }
    }

    private void activateTotemWave(long gameTime, TeleportPathData data) {
        if (totemWaveActivated) {
            return;
        }
        totemWaveActivated = true;
        totemActivationDeadline = NOT_SCHEDULED;
        if (data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_DELAYED) {
            for (int slotId : deadTotemSlots) {
                TotemRuntime runtime = totemRuntime.computeIfAbsent(slotId,
                        ignored -> new TotemRuntime(null));
                runtime.nextRespawnAt = gameTime + data.getTotemRespawnDelayTicks();
            }
        }
    }

    private void tickTotemSlot(ServerLevel level, long gameTime, TeleportPathData data,
                               BossTotemEntry entry) {
        int slotId = entry.getSlotId();
        if (!entry.isEnabled() || entry.getCloneName().isEmpty()) {
            if (entry.isEnabled() && entry.getCloneName().isEmpty()
                    && reportedEmptyTotemSlots.add(slotId)) {
                LOGGER.warn("Boss {} protection-totem slot {} has no clone name",
                        npc.getName().getString(), slotId);
            }
            discardRuntimeTotem(level, slotId);
            return;
        }

        Vec3 anchor = totemAnchor(entry);
        BlockPos anchorBlock = BlockPos.containing(anchor);
        // This is the duplicate-prevention boundary. A missing UUID says nothing while
        // the anchor chunk is absent, because the saved entity is absent from level lookups too.
        if (!level.hasChunkAt(anchorBlock)) {
            return;
        }

        TotemRuntime runtime = totemRuntime.get(slotId);
        Entity totem = runtime == null || runtime.entityId == null
                ? null : level.getEntity(runtime.entityId);
        if (!isUsableTotem(totem, slotId)) {
            Entity adopted = findAliveTotem(level, gameTime, slotId);
            if (adopted != null) {
                runtime = totemRuntime.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
                runtime.entityId = adopted.getUUID();
                runtime.nextRespawnAt = NOT_SCHEDULED;
                deadTotemSlots.remove(slotId);
                saveDeadTotemSlots();
                totem = adopted;
            } else if (runtime != null && runtime.entityId != null) {
                markTotemDead(slotId, gameTime, data);
                runtime = totemRuntime.get(slotId);
                totem = null;
            }
        }

        if (totem != null) {
            pinTotem(totem, entry, anchor);
            if (resetTotemHealthSlots.remove(slotId) && totem instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }
            syncTotemLink(data, entry, totem, runtime, gameTime, false);
            return;
        }

        if (deadTotemSlots.contains(slotId)) {
            if (data.getTotemRespawnMode() != TeleportPathData.TOTEM_RESPAWN_DELAYED) {
                return;
            }
            runtime = totemRuntime.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
            if (runtime.nextRespawnAt == NOT_SCHEDULED) {
                runtime.nextRespawnAt = gameTime + data.getTotemRespawnDelayTicks();
            }
            if (gameTime < runtime.nextRespawnAt) {
                return;
            }
        } else if (runtime != null && runtime.nextRespawnAt != NOT_SCHEDULED
                && gameTime < runtime.nextRespawnAt) {
            return;
        }

        Entity spawned = spawnTotem(level, entry, anchor);
        runtime = totemRuntime.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
        if (spawned == null) {
            runtime.nextRespawnAt = gameTime + TOTEM_RETRY_INTERVAL_TICKS;
            return;
        }
        runtime.entityId = spawned.getUUID();
        runtime.nextRespawnAt = NOT_SCHEDULED;
        syncTotemLink(data, entry, spawned, runtime, gameTime, true);
        deadTotemSlots.remove(slotId);
        resetTotemHealthSlots.remove(slotId);
        saveDeadTotemSlots();
    }

    private Entity spawnTotem(ServerLevel level, BossTotemEntry entry, Vec3 anchor) {
        int slotId = entry.getSlotId();
        BlockPos pos = BlockPos.containing(anchor);
        if (!level.getWorldBorder().isWithinBounds(pos)
                || anchor.y < level.getMinBuildHeight()
                || anchor.y + 1.8D >= level.getMaxBuildHeight()) {
            warnBlockedTotem(slotId, "outside the world border or build height");
            return null;
        }
        AABB box = new AABB(anchor.x - 0.3D, anchor.y, anchor.z - 0.3D,
                anchor.x + 0.3D, anchor.y + 1.8D, anchor.z + 0.3D);
        if (!level.noCollision(box)) {
            warnBlockedTotem(slotId, "spawn box is occupied");
            return null;
        }

        String cloneKey = entry.getCloneTab() + ":" + entry.getCloneName();
        try {
            IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(anchor.x, anchor.y, anchor.z,
                    entry.getCloneTab(), entry.getCloneName(), NpcAPI.Instance().getIWorld(level));
            if (wrapper == null || wrapper.getMCEntity() == null) {
                if (reportedBrokenTotemClones.add(cloneKey)) {
                    LOGGER.warn("Cannot summon protection-totem clone {} for boss {}: clone returned no entity",
                            cloneKey, npc.getName().getString());
                }
                return null;
            }
            Entity spawned = wrapper.getMCEntity();
            BossTotemUtil.markAsTotem(spawned, npc, slotId);
            BossTotemUtil.cacheVulnerability(spawned, entry);
            BossCloneRespawnGuard.suppressSelfRespawn(spawned);
            pinTotem(spawned, entry, anchor);
            reportedBlockedTotemSlots.remove(slotId);
            return spawned;
        } catch (Throwable error) {
            if (reportedBrokenTotemClones.add(cloneKey)) {
                LOGGER.warn("Cannot summon protection-totem clone {} for boss {}: {}", cloneKey,
                        npc.getName().getString(), error.getMessage());
            }
            return null;
        }
    }

    private void warnBlockedTotem(int slotId, String reason) {
        if (reportedBlockedTotemSlots.add(slotId)) {
            LOGGER.warn("Cannot place protection-totem slot {} for boss {}: {}", slotId,
                    npc.getName().getString(), reason);
        }
    }

    private void pinTotem(Entity totem, BossTotemEntry entry, Vec3 anchor) {
        if (Math.abs(totem.getX() - anchor.x) > 1.0E-4D
                || Math.abs(totem.getY() - anchor.y) > 1.0E-4D
                || Math.abs(totem.getZ() - anchor.z) > 1.0E-4D
                || Math.abs(Mth.wrapDegrees(totem.getYRot() - entry.getYaw())) > 0.01F) {
            totem.moveTo(anchor.x, anchor.y, anchor.z, entry.getYaw(), 0.0F);
        }
        totem.setDeltaMovement(Vec3.ZERO);
        totem.fallDistance = 0.0F;
        if (totem instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.yBodyRot = entry.getYaw();
            mob.yHeadRot = entry.getYaw();
        }
    }

    private Vec3 totemAnchor(BossTotemEntry entry) {
        if (entry.getCoordinateMode() == BossTotemEntry.COORDINATE_FIXED) {
            return new Vec3(entry.getX(), entry.getY(), entry.getZ());
        }
        return new Vec3(homeX + entry.getX(), homeY + entry.getY(), homeZ + entry.getZ());
    }

    private boolean isUsableTotem(Entity entity, int slotId) {
        return entity != null && entity.isAlive() && !entity.isRemoved()
                && BossTotemUtil.isTotemOf(entity, npc) && BossTotemUtil.slotId(entity) == slotId;
    }

    private void markTotemDead(int slotId, long gameTime, TeleportPathData data) {
        deadTotemSlots.add(slotId);
        TotemRuntime runtime = totemRuntime.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
        if (runtime.entityId != null && npc.level() instanceof ServerLevel level) {
            Entity dead = level.getEntity(runtime.entityId);
            if (dead != null) {
                dropTotemLink(dead, slotId);
            }
        }
        runtime.entityId = null;
        runtime.nextRespawnAt = data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_DELAYED
                ? gameTime + data.getTotemRespawnDelayTicks() : NOT_SCHEDULED;
        saveDeadTotemSlots();
    }

    private void saveDeadTotemSlots() {
        BossTotemUtil.writeDeadSlots(npc, deadTotemSlots);
    }

    private void adoptLoadedTotems(ServerLevel level, long gameTime, TeleportPathData data) {
        Set<Integer> configured = configuredTotemSlotIds(data, true);
        for (Entity totem : loadedTotems(level, gameTime)) {
            if (totem.isRemoved()) {
                // Discarded earlier in this same tick, by the reconcile that shares the scan.
                continue;
            }
            int slotId = BossTotemUtil.slotId(totem);
            if (!configured.contains(slotId) || !totemWaveActivated && data.getTotemActivationMode()
                    != TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
                dropTotemLink(totem, slotId);
                totem.discard();
                totemRuntime.remove(slotId);
                continue;
            }
            if (!totem.isAlive()) {
                continue;
            }
            TotemRuntime runtime = totemRuntime.get(slotId);
            if (runtime != null && runtime.entityId != null && !runtime.entityId.equals(totem.getUUID())) {
                // A duplicate can only be stale data from an interrupted older reconcile.
                dropTotemLink(totem, slotId);
                totem.discard();
                continue;
            }
            totemRuntime.computeIfAbsent(slotId, ignored -> new TotemRuntime(totem.getUUID()))
                    .entityId = totem.getUUID();
        }
    }

    private void reconcileTotemStructure(ServerLevel level, long gameTime, TeleportPathData data) {
        Set<Integer> allConfigured = configuredTotemSlotIds(data, false);
        Set<Integer> enabledConfigured = configuredTotemSlotIds(data, true);
        boolean changed = deadTotemSlots.retainAll(allConfigured);
        totemRuntime.keySet().removeIf(slotId -> !enabledConfigured.contains(slotId));
        resetTotemHealthSlots.retainAll(enabledConfigured);
        for (Entity totem : loadedTotems(level, gameTime)) {
            if (totem.isRemoved()) {
                continue;
            }
            int slotId = BossTotemUtil.slotId(totem);
            if (!enabledConfigured.contains(slotId)) {
                dropTotemLink(totem, slotId);
                totem.discard();
                continue;
            }
            // Refreshed here rather than only at spawn, so an edited vulnerability list is
            // obeyed by the totems already standing instead of only by the next wave.
            BossTotemEntry entry = totemEntry(data, slotId);
            if (entry != null) {
                BossTotemUtil.cacheVulnerability(totem, entry);
            }
        }
        if (changed) {
            saveDeadTotemSlots();
        }
    }

    private BossTotemEntry totemEntry(TeleportPathData data, int slotId) {
        for (BossTotemEntry entry : data.getTotems().entries()) {
            if (entry.getSlotId() == slotId) {
                return entry;
            }
        }
        return null;
    }

    private Set<Integer> configuredTotemSlotIds(TeleportPathData data, boolean enabledOnly) {
        Set<Integer> result = new HashSet<>();
        for (BossTotemEntry entry : data.getTotems().entries()) {
            if (!enabledOnly || entry.isEnabled() && !entry.getCloneName().isEmpty()) {
                result.add(entry.getSlotId());
            }
        }
        return result;
    }

    /**
     * Every loaded totem of this boss, scanned at most once per tick.
     *
     * <p>The scan walks every entity in the level. Without the memo it ran once per empty
     * slot per tick, plus twice per structural reconcile - on a populated server that is
     * most of what a totem boss cost. Entities discarded after the scan was taken are
     * filtered out again wherever the list is read.</p>
     */
    private List<Entity> loadedTotems(ServerLevel level, long gameTime) {
        if (totemScanAt != gameTime) {
            totemScanAt = gameTime;
            totemScan = BossTotemUtil.findAllLoaded(level, npc);
        }
        return totemScan;
    }

    /** The shared-scan form of {@link BossTotemUtil#findAlive}, with the same answer. */
    private Entity findAliveTotem(ServerLevel level, long gameTime, int slotId) {
        for (Entity entity : loadedTotems(level, gameTime)) {
            if (entity.isAlive() && !entity.isRemoved()
                    && BossTotemUtil.slotId(entity) == slotId && BossTotemUtil.isTotemOf(entity, npc)) {
                return entity;
            }
        }
        return null;
    }

    private void discardRuntimeTotem(ServerLevel level, int slotId) {
        TotemRuntime runtime = totemRuntime.remove(slotId);
        if (runtime != null && runtime.entityId != null) {
            Entity entity = level.getEntity(runtime.entityId);
            if (entity != null && BossTotemUtil.isTotemOf(entity, npc)) {
                dropTotemLink(entity, slotId);
                entity.discard();
            }
        }
    }

    private void removeConfiguredTotems(ServerLevel level) {
        for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
            dropTotemLink(totem, BossTotemUtil.slotId(totem));
            totem.discard();
        }
        totemRuntime.clear();
        resetTotemHealthSlots.clear();
    }

    private void clearTotemRuntime() {
        totemRuntime.clear();
        resetTotemHealthSlots.clear();
        totemWaveActivated = false;
        totemActivationDeadline = NOT_SCHEDULED;
        nextTotemStructuralReconcileAt = 0L;
        // Dropped so the memo cannot keep entity references alive past the fight.
        totemScanAt = NOT_SCHEDULED;
        totemScan = List.of();
    }

    private void syncTotemLink(TeleportPathData data, BossTotemEntry entry, Entity totem,
                               TotemRuntime runtime, long gameTime, boolean force) {
        if (!force && gameTime < runtime.nextLinkSyncAt) {
            return;
        }
        runtime.nextLinkSyncAt = gameTime + TOTEM_LINK_REFRESH_TICKS;
        PacketSyncBossLink packet = totemLinkPacket(data, entry, totem, TOTEM_LINK_DURATION_TICKS);
        // Either endpoint can enter a player's tracking range first. Duplicate delivery is
        // harmless because the client replaces the same keyed link.
        NetworkWrapper.sendToTracking(npc, packet);
        NetworkWrapper.sendToTracking(totem, packet);
    }

    private PacketSyncBossLink totemLinkPacket(TeleportPathData data, BossTotemEntry entry,
                                               Entity totem, int durationTicks) {
        String style = entry.getBeamStyleOverride().isEmpty()
                ? data.getTotemBeamStyle() : entry.getBeamStyleOverride();
        int width = entry.getBeamWidthPercentOverride() == 0
                ? data.getTotemBeamWidthPercent() : entry.getBeamWidthPercentOverride();
        return new PacketSyncBossLink(PacketSyncBossLink.KIND_PROTECTION_TOTEM,
                totem.getId(), npc.getId(), entry.getSlotId(), style, durationTicks,
                width, data.getTotemBeamSagPercent(), false);
    }

    private void dropTotemLink(Entity totem, int slotId) {
        PacketSyncBossLink packet = new PacketSyncBossLink(PacketSyncBossLink.KIND_PROTECTION_TOTEM,
                totem.getId(), npc.getId(), slotId, HookCordStyles.PARTICLES, 0, 100, 0, false);
        NetworkWrapper.sendToTracking(npc, packet);
        NetworkWrapper.sendToTracking(totem, packet);
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
        enterPhase(gameTime, data, data.getPhase(currentPhase));
        playAnimation(data.getPhaseTransitionAnimation());
        if (!data.getPhaseTransitionAnimation().isEmpty()) {
            busyUntil = gameTime + data.getPhaseTransitionLockTicks();
        }
    }

    /**
     * Arms the immune window when the boss steps into a phase that has one.
     *
     * <p>Keyed on the phase index so a phase only turns immune once per encounter: the last
     * phase has nowhere to advance to, and would otherwise re-arm itself forever.</p>
     */
    private void enterPhase(long gameTime, TeleportPathData data, BossPhaseData phase) {
        if (encounterRunning && data.isTotemsEnabled() && !totemWaveActivated
                && data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER
                && currentPhase + 1 == data.getTotemActivationPhase()) {
            activateTotemWave(gameTime, data);
        }
        if (!phase.isInvulnerableEnabled() || invulnerablePhaseIndex == currentPhase) {
            return;
        }
        invulnerablePhaseIndex = currentPhase;
        invulnerableUntil = gameTime + phase.getInvulnerableDurationTicks();
        invulnerableSummonedOnce = false;
        if (phase.isInvulnerableSummonImmediately()) {
            // Set to now rather than left unscheduled: scheduleMissingAbilities fills an
            // unscheduled summon in with a whole fresh cooldown.
            nextSummonAt = gameTime;
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

    private boolean isInvulnerableWindowOver(ServerLevel level, BossPhaseData phase, long gameTime) {
        boolean timerDone = gameTime >= invulnerableUntil;
        if (!phase.invulnerableWaitsForMinions()) {
            return timerDone;
        }
        // "All of them are dead" only means anything once a wave has been called for -
        // otherwise the phase would end on the very tick it began. The summon counts as
        // called for even if nothing spawned, so a boss walled into a corner with nowhere
        // to put its clones still gets out of the phase.
        // Asked every tick the phase waits, so the walk stops at the first living minion.
        boolean minionsDone = invulnerableSummonedOnce && !BossMinionUtil.hasAlive(level, npc);
        if (!phase.invulnerableWaitsForTimer()) {
            return minionsDone;
        }
        return phase.getInvulnerableEndMode() == BossPhaseData.INVULNERABLE_END_TIMER_AND_MINIONS
                ? timerDone && minionsDone
                : timerDone || minionsDone;
    }

    /** Whether a leap is in the air right now. Read by the fall damage handler. */
    public boolean isLeaping() {
        return active && leapAirborne;
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
        long gameTime = level.getGameTime();
        if (gameTime < nextBlockFeedbackAt) {
            return;
        }
        nextBlockFeedbackAt = gameTime + BLOCK_FEEDBACK_INTERVAL_TICKS;
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
        long gameTime = level.getGameTime();
        if (gameTime < nextBlockFeedbackAt) {
            return;
        }
        nextBlockFeedbackAt = gameTime + BLOCK_FEEDBACK_INTERVAL_TICKS;
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.HOSTILE, 1.0F, 1.2F + npc.getRandom().nextFloat() * 0.15F);

        Entity linked = firstLoadedAliveTotem(level);
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

    private Entity firstLoadedAliveTotem(ServerLevel level) {
        for (TotemRuntime runtime : totemRuntime.values()) {
            Entity entity = runtime.entityId == null ? null : level.getEntity(runtime.entityId);
            if (entity != null && entity.isAlive() && BossTotemUtil.isTotemOf(entity, npc)) {
                return entity;
            }
        }
        return null;
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
        clearHealthScaling(data, data.isResetHeal());
        clearEncounter();
        clearInvulnerability();
        minionRoundRobinCursor.clear();
        clearRage();
        cancelPendingAndSchedules();
        clearHookPulls();
        BossGeyserScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        // Before the return below: clearing the leap re-pins the boss where it stands, and
        // the return then moves that pin home rather than the other way round.
        clearLeap();
        BossCaptureManager.releaseByBoss(npc);
        busyUntil = 0L;

        if (data.isClearMinionsOnReset()) {
            BossMinionUtil.clear(level, npc, data.getMinionRemovalMode());
        }
        resetTotemsAfterEncounter(level, data);
        hideBossBar();

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

    private void resetTotemsAfterEncounter(ServerLevel level, TeleportPathData data) {
        if (!data.isTotemsEnabled()) {
            removeConfiguredTotems(level);
            clearTotemRuntime();
            return;
        }
        if (data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_NEXT_ENCOUNTER) {
            deadTotemSlots.clear();
            saveDeadTotemSlots();
        }
        if (data.isTotemResetHealth()) {
            for (BossTotemEntry entry : data.getTotems().entries()) {
                if (entry.isEnabled() && !entry.getCloneName().isEmpty()) {
                    resetTotemHealthSlots.add(entry.getSlotId());
                }
            }
            for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
                if (totem instanceof LivingEntity living && living.isAlive()) {
                    living.setHealth(living.getMaxHealth());
                    resetTotemHealthSlots.remove(BossTotemUtil.slotId(totem));
                }
            }
        }

        if (data.getTotemActivationMode() != TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
            removeConfiguredTotems(level);
            totemWaveActivated = false;
            totemActivationDeadline = NOT_SCHEDULED;
            return;
        }
        // ALWAYS remains active in idle. NEXT_ENCOUNTER slots are now immediately ready,
        // while NEVER keeps its persisted holes and DELAYED keeps its remaining deadline.
        totemWaveActivated = true;
    }

    /**
     * Runs the enrage countdown and sets the boss off once it expires.
     *
     * <p>The clock freezes instead of resetting whenever the boss is left without a combat
     * target: a lap around the nearest corner is not supposed to buy a fresh timer. Pushing
     * the start forward is what freezes it, and keeps the deadline exactly
     * {@code encounterStartedAt + delay}. Only {@link #endEncounter} clears the whole thing,
     * at the same moment the phase rolls back.</p>
     */
    private void tickRage(ServerLevel level, long gameTime, TeleportPathData data) {
        if (!data.isRageEnabled()) {
            // Switching the timer off mid-fight has to take the bonus away with it,
            // otherwise the boss stays enraged until somebody kills it.
            clearRage();
            return;
        }
        if (encounterStartedAt == NOT_SCHEDULED) {
            if (!hasCombatTarget()) {
                return;
            }
            encounterStartedAt = gameTime;
        }
        if (rageActive) {
            return;
        }
        if (!hasCombatTarget()) {
            encounterStartedAt++;
            return;
        }
        if (gameTime < encounterStartedAt + data.getRageDelayTicks()) {
            return;
        }
        beginRage(level, gameTime, data);
    }

    private void beginRage(ServerLevel level, long gameTime, TeleportPathData data) {
        rageActive = true;
        applyRageAttributes(rageMultiplier());
        playAnimation(data.getRageAnimation());
        busyUntil = Math.max(busyUntil, gameTime + data.getRageLockTicks());
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, npc.getX(), npc.getY(0.9D), npc.getZ(), 40,
                npc.getBbWidth() * 0.8D, npc.getBbHeight() * 0.5D, npc.getBbWidth() * 0.8D, 0.1D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, npc.getX(), npc.getY(0.4D), npc.getZ(), 30,
                npc.getBbWidth() * 0.7D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.7D, 0.02D);
    }

    /** Whether the boss is enraged right now. Read by the HUD and by the stat scaling below. */
    public boolean isRageActive() {
        return active && rageActive;
    }

    /** @return ticks left before the boss enrages, or 0 when it already has or never will */
    public int rageTicksLeft() {
        TeleportPathData data = settings();
        if (rageActive || !data.isRageEnabled() || encounterStartedAt == NOT_SCHEDULED
                || !(npc.level() instanceof ServerLevel level)) {
            return 0;
        }
        return (int) Math.max(0L, encounterStartedAt + data.getRageDelayTicks() - level.getGameTime());
    }

    /** @return the full length of the countdown, for the HUD to draw a fill fraction against */
    public int rageTotalTicks() {
        TeleportPathData data = settings();
        return data.isRageEnabled() ? data.getRageDelayTicks() : 0;
    }

    private double rageMultiplier() {
        return settings().getRageMultiplierPercent() / 100.0D;
    }

    /**
     * Scales a stat that grows with the rage: damage, knockback, pull strength.
     *
     * <p>Applied everywhere the setting is read rather than written back into the phase,
     * because a phase is persisted NBT - multiplying it in place would save the doubled
     * value and let every fight stack another factor on top of the last one.</p>
     *
     * <p>A zero passes through untouched: zero knockback and zero hook damage mean "none at
     * all", and the rage is not supposed to invent an effect the boss never had.</p>
     */
    private int rageUp(int value) {
        if (!rageActive || value <= 0) {
            return value;
        }
        return Math.max(1, (int) Math.round(value * rageMultiplier()));
    }

    /** The other half of {@link #rageUp}, for cooldowns - the boss acts more often, not less. */
    private int rageDown(int value) {
        if (!rageActive || value <= 0) {
            return value;
        }
        return Math.max(1, (int) Math.round(value / rageMultiplier()));
    }

    /**
     * Hangs the rage bonus on the entity itself.
     *
     * <p>Transient on purpose: a permanent modifier is written into the entity NBT, and the
     * boss would come back from a world reload still doubled, forever.</p>
     */
    private void applyRageAttributes(double multiplier) {
        // ADD_MULTIPLIED_TOTAL is the 1.21 name of the old MULTIPLY_TOTAL - it scales the
        // finished value by 1 + amount, so a 200% setting has to be handed 1.0.
        AttributeModifier modifier = new AttributeModifier(RAGE_MODIFIER_ID, multiplier - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (Holder<Attribute> attribute : RAGE_ATTRIBUTES) {
            AttributeInstance instance = npc.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(RAGE_MODIFIER_ID);
            instance.addTransientModifier(modifier);
        }
    }

    /**
     * Calms the boss down and takes the attribute bonus off again.
     *
     * <p>Idempotent, so every path that ends a fight - a reset, a death, the level being
     * unloaded - can call it without checking first.</p>
     */
    public void clearRage() {
        if (!rageActive && encounterStartedAt == NOT_SCHEDULED) {
            return;
        }
        rageActive = false;
        encounterStartedAt = NOT_SCHEDULED;
        for (Holder<Attribute> attribute : RAGE_ATTRIBUTES) {
            AttributeInstance instance = npc.getAttribute(attribute);
            if (instance != null) {
                // Removing a modifier that is not there is a no-op, not an error.
                instance.removeModifier(RAGE_MODIFIER_ID);
            }
        }
    }

    /**
     * Keeps the countdown on everyone watching the boss bar in step with the server.
     *
     * <p>A state change goes out at once; a running countdown only needs the occasional
     * correction, because the client subtracts the ticks itself in between. The two states
     * with nothing left to count are sent once and then left alone.</p>
     */
    private void syncBossTimer(long gameTime, TeleportPathData data) {
        ServerBossEvent bar = timerBossEvent();
        if (bar.getPlayers().isEmpty()) {
            return;
        }
        byte state = timerState(data);
        boolean counting = state == PacketSyncBossTimer.STATE_COUNTDOWN
                || state == PacketSyncBossTimer.STATE_INVULNERABLE;
        if (state == lastTimerState && (!counting || gameTime < nextTimerSyncAt)) {
            return;
        }
        lastTimerState = state;
        nextTimerSyncAt = gameTime + TIMER_SYNC_INTERVAL_TICKS;
        PacketSyncBossTimer packet = buildTimerPacket(data);
        for (ServerPlayer player : bar.getPlayers()) {
            NetworkWrapper.send(player, packet);
        }
    }

    /**
     * The bar the countdown belongs on: the styled one while it is up, the NPC's own bar
     * otherwise. Without this a boss left on style {@code none} would count down against a
     * bar id nobody is drawing.
     */
    private ServerBossEvent timerBossEvent() {
        return BossBarStyles.isEnabled(activeBossBarStyle) ? bossEvent : npc.bossInfo;
    }

    private byte timerState(TeleportPathData data) {
        if (isInvulnerable()) {
            return PacketSyncBossTimer.STATE_INVULNERABLE;
        }
        if (rageActive) {
            return PacketSyncBossTimer.STATE_RAGE;
        }
        if (!data.isRageEnabled() || encounterStartedAt == NOT_SCHEDULED) {
            return PacketSyncBossTimer.STATE_NONE;
        }
        return PacketSyncBossTimer.STATE_COUNTDOWN;
    }

    /** The immune window borrows the same countdown, so the HUD only has one thing to draw. */
    private PacketSyncBossTimer buildTimerPacket(TeleportPathData data) {
        byte state = timerState(data);
        int remaining = 0;
        int total = 0;
        if (state == PacketSyncBossTimer.STATE_INVULNERABLE) {
            remaining = invulnerableTicksLeft();
            total = data.getPhase(invulnerablePhaseIndex).getInvulnerableDurationTicks();
        } else if (state == PacketSyncBossTimer.STATE_COUNTDOWN) {
            remaining = rageTicksLeft();
            total = data.getRageDelayTicks();
        } else if (state == PacketSyncBossTimer.STATE_RAGE) {
            total = data.getRageDelayTicks();
        }
        return new PacketSyncBossTimer(timerBossEvent().getId(), remaining, total, state);
    }

    private int healthPercent() {
        float maximum = npc.getMaxHealth();
        return maximum <= 0.0F ? 100 : Math.round(npc.getHealth() * 100.0F / maximum);
    }

    /**
     * Starts combat when an eligible player enters the configured block volume. The spatial
     * check walks the dedicated server player list rather than every entity or every section,
     * so a distant or accidentally huge absolute box never loads chunks or scans empty space.
     */
    private void updateAggroZone(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!data.isAggroZoneEnabled()) {
            nextAggroZoneAt = NOT_SCHEDULED;
            return;
        }
        if (nextAggroZoneAt != NOT_SCHEDULED && gameTime < nextAggroZoneAt) {
            return;
        }
        nextAggroZoneAt = gameTime + data.getAggroZoneRecheckTicks();

        AABB zone = aggroZoneBounds(level, data);
        List<ServerPlayer> candidates = zone == null
                ? List.of() : eligibleAggroZonePlayers(level, data, zone);
        for (ServerPlayer player : candidates) {
            // Everyone who crossed the trigger together belongs to the fight, even when
            // only one of them is chosen as the NPC's immediate target.
            trackParticipant(player);
        }

        LivingEntity current = npc.getTarget();
        boolean currentIsCandidate = current instanceof ServerPlayer player && candidates.contains(player);
        if (data.isAggroZoneKeepInside() && current instanceof Player && !currentIsCandidate) {
            setTargetIfChanged(selectAggroZoneTarget(candidates, data));
            return;
        }
        if (!hasValidZoneCombatTarget(current, data)) {
            ServerPlayer selected = selectAggroZoneTarget(candidates, data);
            if (selected != null) {
                setTargetIfChanged(selected);
            }
        }
    }

    private List<ServerPlayer> eligibleAggroZonePlayers(ServerLevel level, TeleportPathData data,
                                                         AABB zone) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.level() == level && zone.contains(player.position())
                    && isTargetableCandidate(player, data)) {
                candidates.add(player);
            }
        }
        return candidates;
    }

    /** Intersects Y with this dimension's real build height instead of an obsolete 0..255 range. */
    private AABB aggroZoneBounds(ServerLevel level, TeleportPathData data) {
        int minY = Math.max(Math.min(data.getAggroZoneY1(), data.getAggroZoneY2()),
                level.getMinBuildHeight());
        int maxY = Math.min(Math.max(data.getAggroZoneY1(), data.getAggroZoneY2()),
                level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return null;
        }
        int minX = Math.min(data.getAggroZoneX1(), data.getAggroZoneX2());
        int minZ = Math.min(data.getAggroZoneZ1(), data.getAggroZoneZ2());
        int maxX = Math.max(data.getAggroZoneX1(), data.getAggroZoneX2());
        int maxZ = Math.max(data.getAggroZoneZ1(), data.getAggroZoneZ2());
        // The upper AABB bounds are exclusive, so adding one includes every block of corner 2.
        return new AABB(minX, minY, minZ, (double) maxX + 1.0D,
                (double) maxY + 1.0D, (double) maxZ + 1.0D);
    }

    private ServerPlayer selectAggroZoneTarget(List<ServerPlayer> candidates, TeleportPathData data) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (data.getAggroZoneTargetMode() == TeleportPathData.AGGRO_ZONE_TARGET_RANDOM) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }
        ServerPlayer nearest = candidates.getFirst();
        double nearestDistance = npc.distanceToSqr(nearest);
        for (int i = 1; i < candidates.size(); i++) {
            ServerPlayer candidate = candidates.get(i);
            double distance = npc.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean hasValidZoneCombatTarget(LivingEntity target, TeleportPathData data) {
        if (!hasCombatTarget()) {
            return false;
        }
        return !(target instanceof Player player) || isTargetableCandidate(player, data);
    }

    private void setTargetIfChanged(LivingEntity target) {
        if (npc.getTarget() != target) {
            npc.setTarget(target);
        }
    }

    /**
     * Locks the boss onto the closest reachable enemy. Without this a boss keeps chasing
     * whoever aggroed it first, which lets a group trivially kite it with one player.
     */
    private void updateNearestTarget(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!data.isTargetNearestPlayer()) {
            nextRetargetAt = NOT_SCHEDULED;
            return;
        }
        if (nextRetargetAt != NOT_SCHEDULED && gameTime < nextRetargetAt) {
            return;
        }
        nextRetargetAt = gameTime + data.getTargetRecheckTicks();

        boolean restrictToZone = data.isAggroZoneEnabled() && data.isAggroZoneKeepInside();
        AABB zoneConstraint = restrictToZone ? aggroZoneBounds(level, data) : null;
        double radius = data.getTargetSearchRadius();
        double radiusSquared = radius * radius;
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Iterable<? extends Player> players = !restrictToZone
                ? level.players() : zoneConstraint == null
                ? List.of() : eligibleAggroZonePlayers(level, data, zoneConstraint);
        for (Player player : players) {
            double distance = npc.distanceToSqr(player);
            if ((!restrictToZone && distance > radiusSquared) || distance >= nearestDistance) {
                continue;
            }
            if (!isTargetableCandidate(player, data)) {
                continue;
            }
            nearest = player;
            nearestDistance = distance;
        }
        for (LivingEntity candidate : nearbyNonPlayerTargets(level, data, radius, restrictToZone,
                zoneConstraint)) {
            double distance = npc.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        LivingEntity current = npc.getTarget();
        if (nearest == null) {
            // Only release players: a mob target was picked by the CustomNPCs faction AI
            // and dropping it here would fight with that system every recheck.
            if ((restrictToZone || !data.isKeepTargetOutOfRange()) && current instanceof Player) {
                npc.setTarget(null);
            }
            return;
        }
        if (current != nearest) {
            npc.setTarget(nearest);
        }
    }

    /**
     * The non-player half of the retarget search.
     *
     * <p>Players keep their own scan because inside an aggro zone the zone, not the search
     * radius, is their range, and walking a builder-sized zone section by section would
     * cost far more than the player list it replaced. Everything else is looked up in a
     * box around the boss and then, when the zone holds the fight, trimmed down to it.</p>
     */
    private List<LivingEntity> nearbyNonPlayerTargets(ServerLevel level, TeleportPathData data,
                                                      double radius, boolean restrictToZone,
                                                      AABB zoneConstraint) {
        if (data.getAbilityTargetKind() == TeleportPathData.ABILITY_TARGET_PLAYERS
                || restrictToZone && zoneConstraint == null) {
            return List.of();
        }
        double radiusSquared = radius * radius;
        AABB box = new AABB(npc.position(), npc.position()).inflate(radius + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, box, candidate ->
                candidate != npc && !(candidate instanceof Player)
                        && npc.distanceToSqr(candidate) <= radiusSquared
                        && (!restrictToZone || zoneConstraint.contains(candidate.position()))
                        && matchesAbilityTargetKind(candidate, data)
                        && isTargetableCandidate(candidate, data));
    }

    /**
     * Whether the retarget search may lock the boss onto this candidate.
     *
     * <p>Defers to {@link #isAreaTarget} so the boss can never decide to chase something
     * its own attacks would refuse to hit, its minions and totems included.</p>
     */
    private boolean isTargetableCandidate(LivingEntity candidate, TeleportPathData data) {
        if (!candidate.isAlive() || candidate.isRemoved() || !isAreaTarget(candidate)) {
            return false;
        }
        return !data.isTargetRequiresLineOfSight() || npc.getSensing().hasLineOfSight(candidate);
    }

    /**
     * Whether the boss is really fighting someone right now.
     *
     * <p>Deliberately stricter than asking CustomNPCs whether it has a target: it holds on
     * to one until the victim leaves the NPC's own aggro range, which is configured apart
     * from the boss' search radius and is routinely far wider, so a player who just walked
     * off would otherwise keep the encounter alive forever.</p>
     */
    private boolean hasCombatTarget() {
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

    private void updateBossBar(ServerLevel level, TeleportPathData data) {
        String style = BossBarStyles.normalize(data.getBossBarStyle());
        int scalePercent = data.getBossBarScalePercent();
        if (!BossBarStyles.isEnabled(style)) {
            hideBossBar();
            restoreNativeBossBar();
            return;
        }

        npc.bossInfo.setVisible(false);
        if (!hasCombatTarget()) {
            hideBossBar();
            return;
        }

        bossEvent.setName(npc.getDisplayName());
        float maximum = npc.getMaxHealth();
        bossEvent.setProgress(maximum <= 0.0F ? 0.0F : Mth.clamp(npc.getHealth() / maximum, 0.0F, 1.0F));
        bossEvent.setVisible(true);

        if (!style.equals(activeBossBarStyle) || scalePercent != activeBossBarScalePercent) {
            activeBossBarStyle = style;
            activeBossBarScalePercent = scalePercent;
            for (ServerPlayer player : bossEvent.getPlayers()) {
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), style, scalePercent));
            }
        }

        double radiusSquared = data.getTargetSearchRadius() * (double) data.getTargetSearchRadius();
        LivingEntity target = npc.getTarget();
        if (target instanceof ServerPlayer player) {
            bossBarParticipants.add(player.getUUID());
        }
        Set<ServerPlayer> eligible = new HashSet<>();
        for (UUID playerId : Set.copyOf(bossBarParticipants)) {
            Player player = level.getPlayerByUUID(playerId);
            if (player instanceof ServerPlayer serverPlayer && isBossBarViewer(serverPlayer)
                    && (serverPlayer == target || npc.distanceToSqr(serverPlayer) <= radiusSquared)) {
                eligible.add(serverPlayer);
            } else {
                bossBarParticipants.remove(playerId);
            }
        }

        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (!eligible.contains(player)) {
                bossEvent.removePlayer(player);
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE,
                        TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT));
            }
        }
        for (ServerPlayer player : eligible) {
            if (!bossEvent.getPlayers().contains(player)) {
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), style, scalePercent));
                // Whoever just joined the bar has no countdown yet, and the throttled sync
                // below would leave them staring at an empty timer for up to five ticks.
                NetworkWrapper.send(player, buildTimerPacket(data));
                bossEvent.addPlayer(player);
            }
        }
    }

    private boolean isBossBarViewer(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.isRemoved()
                && (player == npc.getTarget() || npc.canAttack(player) && !npc.isAlliedTo(player));
    }

    public void trackParticipant(ServerPlayer player) {
        TeleportPathData data = settings();
        if (player.level() != npc.level() || !data.isEnabled() || !isParticipant(player)) {
            return;
        }
        encounterParticipants.add(player.getUUID());
        if (BossBarStyles.isEnabled(data.getBossBarStyle())) {
            bossBarParticipants.add(player.getUUID());
        }
    }

    /** Kept for integrations compiled against the old bar-specific participant API. */
    public void trackBossBarPlayer(ServerPlayer player) {
        trackParticipant(player);
    }

    private boolean isParticipant(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.isRemoved()
                && npc.canAttack(player) && !npc.isAlliedTo(player);
    }

    /** Adds the whole nearby group before a lock-at-start encounter takes its snapshot. */
    private void registerInitialPartyCandidates(ServerLevel level, TeleportPathData data) {
        double radius = data.getTargetSearchRadius();
        double radiusSquared = radius * radius;
        AABB zone = data.isAggroZoneEnabled() ? aggroZoneBounds(level, data) : null;
        for (ServerPlayer player : level.players()) {
            if (player.level() == level && isParticipant(player)
                    && (npc.distanceToSqr(player) <= radiusSquared
                    || zone != null && zone.contains(player.position()))) {
                encounterParticipants.add(player.getUUID());
            }
        }
    }

    private void tickHealthScalingPlayerCount(ServerLevel level, long gameTime,
                                               TeleportPathData data) {
        if (!encounterRunning) {
            return;
        }
        if (!data.isHealthScalingEnabled()) {
            scaledPlayerCount = 1;
            lockedPlayerCount = 0;
            nextHealthScalingCheckAt = NOT_SCHEDULED;
            lastHealthScalingUpdateMode = -1;
            lastHealthScalingPlayerCap = -1;
            lastHealthScalingRecheckTicks = -1;
            return;
        }

        int updateMode = data.getHealthScalingUpdateMode();
        boolean updateModeChanged = updateMode != lastHealthScalingUpdateMode;
        boolean countSettingsChanged = updateModeChanged
                || data.getHealthScalingPlayerCap() != lastHealthScalingPlayerCap
                || data.getHealthScalingRecheckTicks() != lastHealthScalingRecheckTicks;
        if (countSettingsChanged) {
            if (updateMode == TeleportPathData.HEALTH_SCALING_LOCK_AT_START) {
                if (updateModeChanged || lockedPlayerCount < 1) {
                    registerInitialPartyCandidates(level, data);
                    lockedPlayerCount = countEligibleHealthScalingPlayers(level, data, true);
                }
                scaledPlayerCount = cappedHealthScalingPlayerCount(lockedPlayerCount, data);
                nextHealthScalingCheckAt = NOT_SCHEDULED;
            } else {
                scaledPlayerCount = cappedHealthScalingPlayerCount(
                        countEligibleHealthScalingPlayers(level, data, true), data);
                nextHealthScalingCheckAt = gameTime + data.getHealthScalingRecheckTicks();
            }
            lastHealthScalingUpdateMode = updateMode;
            lastHealthScalingPlayerCap = data.getHealthScalingPlayerCap();
            lastHealthScalingRecheckTicks = data.getHealthScalingRecheckTicks();
            return;
        }

        if (updateMode == TeleportPathData.HEALTH_SCALING_LOCK_AT_START) {
            scaledPlayerCount = cappedHealthScalingPlayerCount(lockedPlayerCount, data);
            return;
        }
        if (nextHealthScalingCheckAt != NOT_SCHEDULED && gameTime < nextHealthScalingCheckAt) {
            return;
        }
        scaledPlayerCount = cappedHealthScalingPlayerCount(
                countEligibleHealthScalingPlayers(level, data, true), data);
        nextHealthScalingCheckAt = gameTime + data.getHealthScalingRecheckTicks();
    }

    private int countEligibleHealthScalingPlayers(ServerLevel level, TeleportPathData data,
                                                   boolean dynamic) {
        Set<ServerPlayer> candidates = new HashSet<>();
        if (npc.getTarget() instanceof ServerPlayer target) {
            candidates.add(target);
        }
        for (UUID playerId : encounterParticipants) {
            Player player = level.getPlayerByUUID(playerId);
            if (player instanceof ServerPlayer serverPlayer) {
                candidates.add(serverPlayer);
            }
        }

        ServerPlayer currentTarget = npc.getTarget() instanceof ServerPlayer target ? target : null;
        double dynamicRadius = data.getTargetSearchRadius() * 1.5D;
        double dynamicRadiusSquared = dynamicRadius * dynamicRadius;
        AABB zone = dynamic && data.isAggroZoneEnabled() ? aggroZoneBounds(level, data) : null;
        int count = 0;
        for (ServerPlayer player : candidates) {
            if (player.level() != level || !isParticipant(player)) {
                continue;
            }
            if (dynamic && player != currentTarget
                    && npc.distanceToSqr(player) > dynamicRadiusSquared
                    && (zone == null || !zone.contains(player.position()))) {
                continue;
            }
            count++;
        }
        return Math.max(1, count);
    }

    private static int cappedHealthScalingPlayerCount(int count, TeleportPathData data) {
        return Mth.clamp(count, 1, data.getHealthScalingPlayerCap());
    }

    public int scaledPlayerCount() {
        return scaledPlayerCount;
    }

    /** Read-only party-health snapshot for /cnpcgecko boss. */
    public String partyHealthStatus(TeleportPathData data) {
        double baseline = healthScalingApplied ? baseMaxHealth : npc.getMaxHealth();
        if (!data.isHealthScalingEnabled()) {
            return "Party health: off, base " + formatHealth(baseline)
                    + ", scaled " + formatHealth(npc.getMaxHealth());
        }
        int players = Math.max(1, scaledPlayerCount);
        String update = data.getHealthScalingUpdateMode()
                == TeleportPathData.HEALTH_SCALING_LOCK_AT_START ? "locked" : "dynamic";
        String adjustment = data.getHealthScalingAdjustment()
                == TeleportPathData.HEALTH_SCALING_KEEP_CURRENT ? "keep current" : "keep percent";
        String mode = switch (data.getHealthScalingMode()) {
            case TeleportPathData.HEALTH_SCALING_FLAT -> "+"
                    + data.getHealthPerPlayerFlat() + " HP/player";
            case TeleportPathData.HEALTH_SCALING_PERCENT_AND_FLAT -> "+"
                    + data.getHealthPerPlayerPercent() + "% +"
                    + data.getHealthPerPlayerFlat() + " HP/player";
            default -> "+" + data.getHealthPerPlayerPercent() + "%/player";
        };
        return "Party health: " + players + " players " + update + " ("
                + Math.max(0, players - 1) + " extra), cap " + data.getHealthScalingPlayerCap()
                + ", base " + formatHealth(baseline) + ", scaled "
                + formatHealth(npc.getMaxHealth()) + ", mode " + mode + ", adjust " + adjustment;
    }

    private static String formatHealth(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void tickHealthScaling(TeleportPathData data) {
        if (!encounterRunning || !data.isHealthScalingEnabled()) {
            clearHealthScaling(data, false);
            return;
        }
        if (healthScalingUnavailable) {
            return;
        }
        long configuration = healthScalingConfiguration(data);
        if (!healthScalingApplied || configuration != lastHealthScalingConfiguration) {
            applyHealthScaling(data, configuration);
        }
    }

    private long healthScalingConfiguration(TeleportPathData data) {
        return scaledPlayerCount
                | (long) data.getHealthScalingMode() << 8
                | (long) data.getHealthPerPlayerPercent() << 10
                | (long) data.getHealthPerPlayerFlat() << 21;
    }

    /** Applies one numeric ADD_VALUE bonus without ever changing the attribute base value. */
    private void applyHealthScaling(TeleportPathData data, long configuration) {
        AttributeInstance instance = npc.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            healthScalingUnavailable = true;
            LOGGER.warn("Boss {} has no MAX_HEALTH attribute; party health scaling is disabled",
                    npc.getName().getString());
            return;
        }

        float oldMax = npc.getMaxHealth();
        float oldHealth = npc.getHealth();
        if (!healthScalingApplied) {
            // A controller can be rebuilt around a still-loaded entity, so discard only our id.
            instance.removeModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
            baseMaxHealth = finiteHealth(npc.getMaxHealth(), 1.0D);
            healthScalingApplied = true;
        } else {
            instance.removeModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
        }

        double bonus = healthScalingBonus(instance, data);
        if (bonus > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID, bonus,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        lastHealthScalingConfiguration = configuration;
        adjustCurrentHealth(oldHealth, oldMax, npc.getMaxHealth(), data.getHealthScalingAdjustment());
    }

    private double healthScalingBonus(AttributeInstance instance, TeleportPathData data) {
        double desiredMax = data.calculateScaledMaxHealth(baseMaxHealth, scaledPlayerCount);
        double sanitizedMax = instance.getAttribute().value().sanitizeValue(desiredMax);
        return Math.max(0.0D, sanitizedMax - baseMaxHealth);
    }

    private static double finiteHealth(double value, double nonFiniteFallback) {
        if (!Double.isFinite(value)) {
            return nonFiniteFallback;
        }
        return Math.max(1.0D, value);
    }

    private void adjustCurrentHealth(float oldHealth, float oldMax, float newMax, int adjustment) {
        if (!npc.isAlive()) {
            return;
        }
        double wanted = adjustment == TeleportPathData.HEALTH_SCALING_KEEP_CURRENT
                ? oldHealth
                : oldMax <= 0.0F ? newMax : (double) newMax * oldHealth / oldMax;
        npc.setHealth((float) Mth.clamp(wanted, 1.0D, Math.max(1.0F, newMax)));
    }

    /** Removes the party modifier and restores HP according to reset and adjustment policy. */
    private void clearHealthScaling(TeleportPathData data, boolean resetHeal) {
        AttributeInstance instance = npc.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            healthScalingApplied = false;
            baseMaxHealth = 0.0D;
            lastHealthScalingConfiguration = Long.MIN_VALUE;
            return;
        }
        boolean hadModifier = instance.hasModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
        if (!healthScalingApplied && !hadModifier) {
            if (resetHeal && npc.isAlive()) {
                npc.setHealth(npc.getMaxHealth());
            }
            return;
        }

        float oldMax = npc.getMaxHealth();
        float oldHealth = npc.getHealth();
        instance.removeModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
        float newMax = npc.getMaxHealth();
        if (npc.isAlive()) {
            if (resetHeal) {
                npc.setHealth(newMax);
            } else {
                adjustCurrentHealth(oldHealth, oldMax, newMax, data.getHealthScalingAdjustment());
            }
        }
        healthScalingApplied = false;
        baseMaxHealth = 0.0D;
        lastHealthScalingConfiguration = Long.MIN_VALUE;
    }

    public void removeBossBarPlayer(ServerPlayer player) {
        bossBarParticipants.remove(player.getUUID());
        if (!bossEvent.getPlayers().contains(player)) {
            return;
        }
        bossEvent.removePlayer(player);
        NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE,
                TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT));
    }

    public void removeParticipant(ServerPlayer player) {
        // Keep encounter history so a dynamic participant is counted again after returning.
        nextHealthScalingCheckAt = 0L;
        removeBossBarPlayer(player);
    }

    /** Clears administrative NEVER holes and reconciles every enabled slot on the next tick. */
    public void restoreAllTotemsNow() {
        deadTotemSlots.clear();
        saveDeadTotemSlots();
        for (TotemRuntime runtime : totemRuntime.values()) {
            if (runtime.entityId == null) {
                runtime.nextRespawnAt = NOT_SCHEDULED;
            }
        }
        nextTotemStructuralReconcileAt = 0L;
    }

    /**
     * True only while at least one configured wave entity is known to be alive.
     *
     * <p>What a standing formation is worth is left to the two formation flags - it may ward,
     * hold, do both, or nothing but draw its beams - so this is only the condition they
     * share. It counts adopted runtime entries rather than a world scan on purpose: a totem
     * in an unloaded chunk is still standing.</p>
     */
    public boolean isTotemWardStanding() {
        TeleportPathData data = settings();
        return active && npc.isAlive() && data.isEnabled() && data.isTotemsEnabled()
                && totemWaveActivated && aliveTotemCount() > 0;
    }

    /** True while a standing formation is the reason the boss cannot be hurt. */
    public boolean isTotemProtected() {
        return settings().isTotemGrantInvulnerability() && isTotemWardStanding();
    }

    /**
     * True while a standing formation nails the boss to the spot it is fighting on.
     *
     * <p>The flag is read before the formation, not after: this is asked twice a tick and
     * again by the pounce ai, and a boss whose totems only ward should not be counting them
     * over and over to be told the same no.</p>
     */
    public boolean isTotemHeld() {
        return settings().isTotemHoldBoss() && isTotemWardStanding();
    }

    public int aliveTotemCount() {
        if (!totemWaveActivated) {
            return 0;
        }
        Set<Integer> enabled = configuredTotemSlotIds(settings(), true);
        int result = 0;
        for (Map.Entry<Integer, TotemRuntime> runtime : totemRuntime.entrySet()) {
            if (enabled.contains(runtime.getKey()) && runtime.getValue().entityId != null) {
                result++;
            }
        }
        return result;
    }

    public int configuredTotemCount() {
        return configuredTotemSlotIds(settings(), true).size();
    }

    public int getTotemProtectionMode() {
        return settings().getTotemProtectionMode();
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
        long remaining = nextCaptureAt == NOT_SCHEDULED ? 0L : nextCaptureAt - gameTime;
        return remaining > 0L ? "Capture: cooldown " + remaining : "Capture: ready";
    }

    /** Gives a viewer an immediate snapshot when either endpoint starts being tracked. */
    public void syncTotemLinksTo(ServerPlayer player) {
        if (!totemWaveActivated || player.level() != npc.level()
                || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        TeleportPathData data = settings();
        for (BossTotemEntry entry : data.getTotems().entries()) {
            TotemRuntime runtime = totemRuntime.get(entry.getSlotId());
            Entity totem = runtime == null || runtime.entityId == null
                    ? null : level.getEntity(runtime.entityId);
            if (entry.isEnabled() && !entry.getCloneName().isEmpty()
                    && isUsableTotem(totem, entry.getSlotId())) {
                NetworkWrapper.send(player, totemLinkPacket(data, entry, totem,
                        TOTEM_LINK_DURATION_TICKS));
            }
        }
    }

    public static void syncTotemLinksForTracking(ServerPlayer player, Entity tracked) {
        for (TeleportPathController controller : List.copyOf(INSTANCES)) {
            if (controller.npc.level() == player.level()
                    && (tracked == controller.npc || BossTotemUtil.isTotemOf(tracked, controller.npc))) {
                controller.syncTotemLinksTo(player);
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
                controller.markTotemDead(BossTotemUtil.slotId(deadTotem), level.getGameTime(),
                        controller.settings());
                return;
            }
        }
    }

    /** Captured by the death event before the NPC can disappear without another tick. */
    public void onDeath() {
        stopBossBar();
        clearRage();
        clearHealthScaling(settings(), false);
        clearEncounter();
        BossCaptureManager.releaseByBoss(npc);
        BossGeyserScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        if (npc.level() instanceof ServerLevel level) {
            removeTotemsOnBossDeath(level, settings());
        }
    }

    private void removeTotemsOnBossDeath(ServerLevel level, TeleportPathData data) {
        for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
            dropTotemLink(totem, BossTotemUtil.slotId(totem));
        }
        if (data.isTotemRemoveOnBossDeath()) {
            BossTotemUtil.removeLoaded(level, npc);
        }
        clearTotemRuntime();
    }

    public void shutdown() {
        stopBossBar();
        // The level is going away with the boss still enraged: the modifier is transient and
        // never reaches the save file, but the entity object outlives an unload, so it is
        // taken off here rather than left for a tick that may never come.
        clearRage();
        clearHealthScaling(settings(), false);
        clearEncounter();
        BossCaptureManager.releaseByBoss(npc);
        BossGeyserScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        if (npc.level() instanceof ServerLevel level) {
            for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
                dropTotemLink(totem, BossTotemUtil.slotId(totem));
            }
        }
        clearTotemRuntime();
        INSTANCES.remove(this);
    }

    public void stopBossBar() {
        hideBossBar();
        npc.bossInfo.setVisible(false);
    }

    public static void removePlayerFromBossBars(ServerPlayer player) {
        removePlayerFromEncounters(player);
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

    private void hideBossBar() {
        if (BossBarStyles.isEnabled(activeBossBarStyle)) {
            // Only when the styled bar really was up. On style `none` this runs every tick,
            // and resetting the throttle there would put a countdown packet on every one.
            lastTimerState = PacketSyncBossTimer.STATE_NONE;
            nextTimerSyncAt = 0L;
        }
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            bossEvent.removePlayer(player);
            NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE,
                    TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT));
        }
        activeBossBarStyle = BossBarStyles.NONE;
        activeBossBarScalePercent = TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT;
        bossBarParticipants.clear();
    }

    private void restoreNativeBossBar() {
        int mode = npc.display.getBossbar();
        npc.bossInfo.setVisible(npc.isAlive() && !npc.isRemoved()
                && (mode == 1 || mode == 2 && hasCombatTarget()));
    }

    private void keepStationary() {
        npc.getNavigation().stop();
        Vec3 movement = npc.getDeltaMovement();
        npc.setDeltaMovement(0.0D, movement.y, 0.0D);
        if (Math.abs(npc.getX() - lockedX) > 1.0E-4D || Math.abs(npc.getZ() - lockedZ) > 1.0E-4D) {
            npc.setPos(lockedX, npc.getY(), lockedZ);
        }
    }

    private void rememberCurrentPosition() {
        lockedX = npc.getX();
        lockedZ = npc.getZ();
    }

    /**
     * Pins a walking boss for the action that is just starting, when this phase casts the
     * ability standing still.
     *
     * <p>The stationary boss is pinned every tick anyway, so the root stays out of its way.
     * A leap always roots its crouch - the flight is what has to stay free, and
     * {@link #performLeap} lets go at the push - while a teleport is never held: moving
     * away is the whole ability.</p>
     */
    private void beginCastRoot(TeleportPathData data, BossPhaseData phase, PendingAction action) {
        if (data.isStationary()) {
            return;
        }
        if (action != PendingAction.LEAP && !phase.isCastRooted(abilityKind(action))) {
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
                ? pendingAction == PendingAction.NONE
                : gameTime >= castRootUntil) {
            endCastRoot();
        }
    }

    private void endCastRoot() {
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
        if (pendingAction != PendingAction.LINE_ATTACK && pendingAction != PendingAction.BOULDER) {
            return false;
        }
        BossPhaseData phase = data.getPhase(currentPhase);
        if (pendingAction == PendingAction.LINE_ATTACK) {
            if (lineAttackAxis == null || !phase.isLineAttackFaceAxis()) {
                return false;
            }
            turnTowardAxis(lineAttackAxis, phase.getLineAttackLength(), LINE_FACE_TURN_DEGREES_PER_TICK);
            return true;
        }
        // The boulder has no opt-out: its corridor is exactly as wide as the stone, so one
        // launched off the boss' shoulder reads as broken rather than as a style choice.
        if (boulderAxis == null) {
            return false;
        }
        turnTowardAxis(boulderAxis, phase.getBoulderRange(), LINE_FACE_TURN_DEGREES_PER_TICK);
        return true;
    }

    /** Body, head and gaze onto the committed axis, moving at most {@code maxTurn} degrees. */
    private void turnTowardAxis(Vec3 axis, double lookDistance, float maxTurn) {
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

    private void preparePath(List<int[]> points) {
        if (points.size() < 2) {
            nextTeleportAt = NOT_SCHEDULED;
            lastPathIndex = -1;
            previousPathSize = points.size();
            return;
        }
        if (points.size() != previousPathSize || lastPathIndex < 0 || lastPathIndex >= points.size()) {
            lastPathIndex = findClosestPoint(points);
            pingPongDirection = 1;
            previousPathSize = points.size();
            nextTeleportAt = NOT_SCHEDULED;
        }
    }

    private void scheduleMissingAbilities(long gameTime, BossPhaseData phase, boolean hasPath) {
        if (hasPath && nextTeleportAt == NOT_SCHEDULED) scheduleNextTeleport(gameTime, phase);
        if (phase.canSummon()) {
            if (nextSummonAt == NOT_SCHEDULED) nextSummonAt = gameTime + rageDown(phase.getSummonCooldownTicks());
        } else {
            nextSummonAt = NOT_SCHEDULED;
        }
        if (phase.isAreaAttackEnabled()) {
            if (nextGroundAttackAt == NOT_SCHEDULED) {
                nextGroundAttackAt = gameTime + rageDown(phase.getAreaAttackCooldownTicks());
            }
        } else {
            nextGroundAttackAt = NOT_SCHEDULED;
        }
        if (phase.isLineAttackEnabled()) {
            if (nextLineAttackAt == NOT_SCHEDULED) {
                nextLineAttackAt = gameTime + rageDown(phase.getLineAttackCooldownTicks());
            }
        } else {
            nextLineAttackAt = NOT_SCHEDULED;
        }
        if (phase.isRangedAttackEnabled()) {
            if (nextRangedAttackAt == NOT_SCHEDULED) {
                nextRangedAttackAt = gameTime + rageDown(phase.getRangedAttackCooldownTicks());
            }
        } else {
            nextRangedAttackAt = NOT_SCHEDULED;
        }
        if (phase.isMeleeAttackEnabled()) {
            if (nextMeleeAttackAt == NOT_SCHEDULED) {
                nextMeleeAttackAt = gameTime + rageDown(phase.getMeleeAttackCooldownTicks());
            }
        } else {
            nextMeleeAttackAt = NOT_SCHEDULED;
        }
        if (phase.canSpitFluid()) {
            if (nextFluidSpitAt == NOT_SCHEDULED) {
                nextFluidSpitAt = gameTime + rageDown(phase.getFluidSpitCooldownTicks());
            }
        } else {
            nextFluidSpitAt = NOT_SCHEDULED;
        }
        if (phase.isHookEnabled()) {
            if (nextHookAt == NOT_SCHEDULED) {
                nextHookAt = gameTime + rageDown(phase.getHookCooldownTicks());
            }
        } else {
            nextHookAt = NOT_SCHEDULED;
        }
        if (phase.isCaptureEnabled()) {
            if (nextCaptureAt == NOT_SCHEDULED) {
                nextCaptureAt = gameTime + rageDown(phase.getCaptureCooldownTicks());
            }
        } else {
            nextCaptureAt = NOT_SCHEDULED;
        }
        if (phase.isLeapEnabled()) {
            if (nextLeapAt == NOT_SCHEDULED) {
                nextLeapAt = gameTime + rageDown(phase.getLeapCooldownTicks());
            }
        } else {
            nextLeapAt = NOT_SCHEDULED;
        }
        if (phase.isGeyserEnabled()) {
            if (nextGeyserAt == NOT_SCHEDULED) {
                nextGeyserAt = gameTime + rageDown(phase.getGeyserCooldownTicks());
            }
        } else {
            nextGeyserAt = NOT_SCHEDULED;
        }
        if (phase.canLaunchBoulder()) {
            if (nextBoulderAt == NOT_SCHEDULED) {
                nextBoulderAt = gameTime + rageDown(phase.getBoulderCooldownTicks());
            }
        } else {
            nextBoulderAt = NOT_SCHEDULED;
        }
        if (phase.canLaunchBoulderRain()) {
            if (nextBoulderRainAt == NOT_SCHEDULED) {
                nextBoulderRainAt = gameTime + rageDown(phase.getBoulderRainCooldownTicks());
            }
        } else {
            nextBoulderRainAt = NOT_SCHEDULED;
        }
    }

    private void scheduleNextTeleport(long gameTime, BossPhaseData phase) {
        int min = rageDown(phase.getTeleportMinDelayTicks());
        int spread = Math.max(0, rageDown(phase.getTeleportMaxDelayTicks()) - min);
        int delay = min + (spread == 0 ? 0 : npc.getRandom().nextInt(spread + 1));
        nextTeleportAt = gameTime + delay;
    }

    /** Rotates ability priority so short cooldowns cannot permanently starve another attack. */
    private boolean tryStartDueAbility(ServerLevel level, TeleportPathData data,
                                       BossPhaseData phase, long gameTime) {
        if (isInvulnerable()) {
            // An immune boss only calls for help. The other attacks keep their timers and
            // pick up where they left off once it can be hurt again.
            return tryStartSummon(level, data, phase, gameTime);
        }
        for (int offset = 0; offset < ABILITY_COUNT; offset++) {
            int ability = (nextAbilityPriority + offset) % ABILITY_COUNT;
            boolean started = switch (ability) {
                case 0 -> tryStartGroundAttack(level, data, phase, gameTime);
                case 1 -> tryStartRangedAttack(level, data, phase, gameTime);
                case 2 -> tryStartMeleeAttack(level, data, phase, gameTime);
                case 3 -> tryStartFluidSpit(level, data, phase, gameTime);
                case 4 -> tryStartHook(level, data, phase, gameTime);
                case 5 -> tryStartCapture(level, data, phase, gameTime);
                case 6 -> tryStartLeap(level, data, phase, gameTime);
                case 7 -> tryStartLineAttack(level, data, phase, gameTime);
                case 8 -> tryStartGeyser(level, data, phase, gameTime);
                case 9 -> tryStartBoulder(level, data, phase, gameTime);
                case 10 -> tryStartBoulderRain(level, data, phase, gameTime);
                default -> tryStartSummon(level, data, phase, gameTime);
            };
            if (started) {
                nextAbilityPriority = (ability + 1) % ABILITY_COUNT;
                return true;
            }
        }
        return false;
    }

    private boolean tryStartGroundAttack(ServerLevel level, TeleportPathData data,
                                         BossPhaseData phase, long gameTime) {
        if (!phase.isAreaAttackEnabled() || gameTime < nextGroundAttackAt) return false;
        if (!hasAreaTargets(level, phase)) {
            nextGroundAttackAt = gameTime + 20;
            return false;
        }
        beginAction(PendingAction.GROUND_ATTACK, phase.getAreaAttackAnimation(),
                phase.getAreaAttackActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would land the hit before the swing does.
        nextGroundAttackAt = gameTime + phase.getAreaAttackActionDelayTicks()
                + rageDown(phase.getAreaAttackCooldownTicks());
        return true;
    }

    /**
     * A strike straight down a corridor in front of the boss.
     *
     * <p>Where it goes is settled here rather than when the hit lands: the warning on the
     * floor promises one corridor, and the boss has to keep that promise even if whoever it
     * picked spends the whole wind-up running sideways.</p>
     */
    private boolean tryStartLineAttack(ServerLevel level, TeleportPathData data,
                                       BossPhaseData phase, long gameTime) {
        if (!phase.isLineAttackEnabled() || gameTime < nextLineAttackAt) return false;
        LivingEntity target = selectAbilityTarget(level, phase.getLineAttackTargetMode(),
                phase.getLineAttackLength(), candidate -> isValidLineTarget(candidate, phase));
        Vec3 axis = resolveLineAxis(phase, target);
        // An empty corridor is no reason to swing: the strike would land on bare floor and
        // spend a whole cooldown doing it.
        if (axis == null || lineTargets(level, npc.position(), axis, phase).isEmpty()) {
            nextLineAttackAt = gameTime + 10;
            return false;
        }
        lineAttackAxis = axis;
        beginAction(PendingAction.LINE_ATTACK, phase.getLineAttackAnimation(),
                phase.getLineAttackActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would land the hit before the swing does.
        nextLineAttackAt = gameTime + phase.getLineAttackActionDelayTicks()
                + rageDown(phase.getLineAttackCooldownTicks());
        return true;
    }

    /** Which way this strike goes: at whoever it picked, or wherever the boss is looking. */
    private Vec3 resolveLineAxis(BossPhaseData phase, LivingEntity target) {
        if (phase.getLineAttackDirection() != BossPhaseData.LINE_DIRECTION_TARGET) {
            return facingAxis();
        }
        if (target == null) {
            return null;
        }
        Vec3 flat = new Vec3(target.getX() - npc.getX(), 0.0D, target.getZ() - npc.getZ());
        // Somebody standing inside the boss leaves no direction to read off them, so the
        // gaze decides rather than the aim collapsing to nothing.
        return flat.lengthSqr() < 1.0E-6D ? facingAxis() : flat.normalize();
    }

    /** Where the boss is looking, flattened onto the plane the corridor is worked out in. */
    private Vec3 facingAxis() {
        double yaw = npc.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    /**
     * Whether one candidate is worth aiming a line strike at.
     *
     * <p>Measured flat and against the same height band the strike itself uses, so the
     * corridor laid down toward whoever this picks really does cover them.</p>
     */
    private boolean isValidLineTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.LINE)) return false;
        if (Math.abs(target.getY() - npc.getY()) > phase.getLineAttackHeight()) return false;
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double length = phase.getLineAttackLength();
        return dx * dx + dz * dz <= length * length;
    }

    private boolean tryStartRangedAttack(ServerLevel level, TeleportPathData data,
                                         BossPhaseData phase, long gameTime) {
        if (!phase.isRangedAttackEnabled() || gameTime < nextRangedAttackAt) return false;
        LivingEntity target = selectAbilityTarget(level, phase.getRangedAttackTargetMode(),
                phase.getRangedAttackMaxRange(), candidate -> isValidRangedTarget(candidate, phase));
        if (target == null || npc.inventory.getProjectile() == null) {
            nextRangedAttackAt = gameTime + 10;
            return false;
        }
        beginAction(PendingAction.RANGED_ATTACK, phase.getRangedAttackAnimation(),
                phase.getRangedAttackActionDelayTicks(), gameTime, target, data, phase);
        nextRangedAttackAt = gameTime + phase.getRangedAttackActionDelayTicks()
                + rageDown(phase.getRangedAttackCooldownTicks());
        return true;
    }

    private boolean tryStartMeleeAttack(ServerLevel level, TeleportPathData data,
                                        BossPhaseData phase, long gameTime) {
        if (!phase.isMeleeAttackEnabled() || gameTime < nextMeleeAttackAt) return false;
        // Melee reach is measured hitbox to hitbox, so the search box carries the boss own
        // half-width on top of the configured range or a wide boss loses candidates to it.
        LivingEntity target = selectAbilityTarget(level, phase.getMeleeAttackTargetMode(),
                phase.getMeleeAttackRange() + npc.getBbWidth() * 0.5D,
                candidate -> isValidMeleeTarget(candidate, phase));
        if (target == null) {
            nextMeleeAttackAt = gameTime + 5;
            return false;
        }
        beginAction(PendingAction.MELEE_ATTACK, phase.getMeleeAttackAnimation(),
                phase.getMeleeAttackActionDelayTicks(), gameTime, target, data, phase);
        nextMeleeAttackAt = gameTime + phase.getMeleeAttackActionDelayTicks()
                + rageDown(phase.getMeleeAttackCooldownTicks());
        return true;
    }

    private boolean tryStartFluidSpit(ServerLevel level, TeleportPathData data,
                                      BossPhaseData phase, long gameTime) {
        if (!phase.canSpitFluid() || gameTime < nextFluidSpitAt) return false;
        LivingEntity target = selectAbilityTarget(level, phase.getFluidSpitTargetMode(),
                phase.getFluidSpitMaxRange(), candidate -> isValidFluidSpitTarget(candidate, phase));
        if (target == null || FluidBlockUtil.resolve(phase.getFluidSpitBlock()) == null) {
            nextFluidSpitAt = gameTime + 20;
            return false;
        }
        beginAction(PendingAction.FLUID_SPIT, phase.getFluidSpitAnimation(),
                phase.getFluidSpitActionDelayTicks(), gameTime, target, data, phase);
        nextFluidSpitAt = gameTime + phase.getFluidSpitActionDelayTicks()
                + rageDown(phase.getFluidSpitCooldownTicks());
        return true;
    }

    private boolean isValidFluidSpitTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.FLUID)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getFluidSpitMinRange();
        double max = phase.getFluidSpitMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    private void performFluidSpit(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = pendingTarget(level);
        if (!isValidFluidSpitTarget(target, phase)) return;
        BlockState fluid = FluidBlockUtil.resolve(phase.getFluidSpitBlock());
        if (fluid == null) {
            if (!phase.getFluidSpitBlock().equals(reportedBrokenFluid)) {
                reportedBrokenFluid = phase.getFluidSpitBlock();
                LOGGER.warn("Boss {} cannot spit {}: that block is not a fluid",
                        npc.getName().getString(), phase.getFluidSpitBlock());
            }
            return;
        }
        reportedBrokenFluid = "";

        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        EntityFluidSpit spit = new EntityFluidSpit(EntityRegistry.entityFluidSpit, npc, level);
        spit.configure(fluid, phase.getFluidSpitLifetimeTicks(), phase.getFluidSpitRadius(),
                rageUp(phase.getFluidSpitDamage()));
        spit.setPos(npc.getX(), npc.getEyeY() - 0.1D, npc.getZ());

        // Aim at the feet with a slight arc so the puddle lands on the ground the target
        // stands on instead of splashing against their chest.
        double dx = target.getX() - spit.getX();
        double dy = target.getY() - spit.getY();
        double dz = target.getZ() - spit.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spit.shoot(dx, dy + horizontal * 0.2D, dz, 1.2F, 4.0F);

        if (!level.addFreshEntity(spit)) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.LLAMA_SPIT,
                SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    private boolean tryStartHook(ServerLevel level, TeleportPathData data,
                                 BossPhaseData phase, long gameTime) {
        if (!phase.isHookEnabled() || gameTime < nextHookAt) return false;
        List<LivingEntity> targets = selectAbilityTargets(level, phase.getHookTargetMode(),
                phase.getHookMaxRange(), candidate -> isValidHookTarget(candidate, phase),
                phase.getHookTargetCount());
        if (targets.isEmpty()) {
            nextHookAt = gameTime + 10;
            return false;
        }
        pendingExtraTargets.clear();
        for (int i = 1; i < targets.size(); i++) {
            pendingExtraTargets.add(targets.get(i).getId());
        }
        beginAction(PendingAction.HOOK, phase.getHookAnimation(),
                phase.getHookActionDelayTicks(), gameTime, targets.get(0), data, phase);
        nextHookAt = gameTime + phase.getHookActionDelayTicks() + rageDown(phase.getHookCooldownTicks());
        return true;
    }

    private boolean isValidHookTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.HOOK)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getHookMinRange();
        double max = phase.getHookMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) return false;
        // A chain that reaches through a wall looks broken, so honour the NPC line-of-sight flag.
        return !npc.ais.directLOS || npc.canNpcSee(target);
    }

    private boolean tryStartCapture(ServerLevel level, TeleportPathData data,
                                    BossPhaseData phase, long gameTime) {
        if (!phase.isCaptureEnabled() || gameTime < nextCaptureAt
                || BossCaptureManager.hasCaptureForBoss(npc.getUUID())) return false;
        LivingEntity target = selectCaptureTarget(level, phase);
        if (target == null) {
            nextCaptureAt = gameTime + 10;
            return false;
        }
        beginAction(PendingAction.CAPTURE, phase.getCaptureAnimation(),
                phase.getCaptureActionDelayTicks(), gameTime, target, data, phase);
        // Windup and hold timing stay aligned with the animation; rage only shortens cooldown.
        nextCaptureAt = gameTime + phase.getCaptureActionDelayTicks()
                + rageDown(phase.getCaptureCooldownTicks());
        return true;
    }

    /**
     * Capture keeps its own mode handling because MAIN falls back to a random victim here:
     * a grab animation that plays with nobody in the beam would look broken.
     */
    private LivingEntity selectCaptureTarget(ServerLevel level, BossPhaseData phase) {
        List<LivingEntity> candidates = abilityCandidates(level, phase.getCaptureMaxRange(),
                candidate -> isValidCaptureTarget(candidate, phase));
        if (candidates.isEmpty()) {
            return null;
        }
        LivingEntity main = npc.getTarget();
        if (phase.getCaptureTargetMode() == BossTargetMode.MAIN && candidates.contains(main)) {
            return main;
        }
        if (phase.getCaptureTargetMode() == BossTargetMode.RANDOM
                || phase.getCaptureTargetMode() == BossTargetMode.MAIN) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }
        boolean farthest = phase.getCaptureTargetMode() == BossTargetMode.FARTHEST;
        LivingEntity best = candidates.getFirst();
        double bestDistance = npc.distanceToSqr(best);
        for (int i = 1; i < candidates.size(); i++) {
            LivingEntity candidate = candidates.get(i);
            double distance = npc.distanceToSqr(candidate);
            if (farthest ? distance > bestDistance : distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isValidCaptureTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || target.level() != npc.level() || !target.isAlive()
                || target.isRemoved() || !isAbilityTarget(target, BossAbilityKind.CAPTURE)
                || BossCaptureManager.isCaptured(target.getUUID())) {
            return false;
        }
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getCaptureMinRange();
        double max = phase.getCaptureMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) {
            return false;
        }
        return !npc.ais.directLOS || npc.canNpcSee(target);
    }

    private void performCapture(ServerLevel level, BossPhaseData phase, long gameTime) {
        LivingEntity victim = pendingTarget(level);
        if (!isValidCaptureTarget(victim, phase)) {
            return;
        }
        if (!BossCaptureManager.start(npc, victim, phase, currentPhase, gameTime)) {
            return;
        }
        int receiver = phase.getCaptureEffectTarget();
        if (receiver == BossPhaseData.CAPTURE_EFFECT_PLAYER
                || receiver == BossPhaseData.CAPTURE_EFFECT_BOTH) {
            BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.CAPTURE, npc,
                    phase.getCaptureEffects());
        }
        if (receiver == BossPhaseData.CAPTURE_EFFECT_BOSS
                || receiver == BossPhaseData.CAPTURE_EFFECT_BOTH) {
            // Ungated on purpose: this half is the boss rewarding itself for a grab it pulled
            // off, not the grab landing on it.
            phase.getCaptureEffects().applyAll(npc, npc);
        }
        if (victim instanceof ServerPlayer player) {
            trackParticipant(player);
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
                victim.getZ(), 12, 0.25D, 0.5D, 0.25D, 0.02D);
    }

    private void performHook(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<LivingEntity> victims = new ArrayList<>();
        LivingEntity primary = pendingTarget(level);
        if (primary != null && isValidHookTarget(primary, phase)) {
            victims.add(primary);
        }
        for (int id : pendingExtraTargets) {
            if (level.getEntity(id) instanceof LivingEntity extra
                    && isValidHookTarget(extra, phase) && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        if (victims.isEmpty()) {
            return;
        }

        double strength = rageUp(phase.getHookPullStrength()) / 20.0D;
        long endsAt = gameTime + phase.getHookPullDurationTicks();
        // A cinch reels everyone onto one spot and keeps them there for the full duration,
        // so the release distance is deliberately ignored - the point is to end up with a
        // tight pile that the next area attack can catch.
        boolean cinch = phase.getHookMode() == BossPhaseData.HOOK_MODE_CINCH;
        Vec3 gatherPoint = cinch ? npc.position() : null;
        double stopDistance = cinch ? 0.0D : phase.getHookStopDistance();
        String cordStyle = phase.getHookCordStyle();
        boolean textured = HookCordStyles.isTextured(cordStyle);
        for (LivingEntity victim : victims) {
            if (textured) {
                sendHookCord(victim.getId(), cordStyle, phase.getHookPullDurationTicks());
            } else {
                drawHookChain(level, victim);
            }
            // No knockback here: what the hook shoves with is the pull below, which runs for
            // as long as the cord holds rather than for one tick.
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.HOOK, npc,
                    rageUp(phase.getHookDamage()), phase.getHookEffects(), 0, 0.0D, 0.0D);
            // Re-hooking someone already being dragged just refreshes their pull.
            activePulls.removeIf(pull -> pull.targetId() == victim.getId());
            activePulls.add(new HookPull(victim.getId(), endsAt, strength, stopDistance, gatherPoint,
                    cordStyle));
            applyPull(victim, strength, gatherPoint);
        }
        playHookSound(level, cordStyle);
    }

    /** Each cord gets the voice its artwork implies; the plain sparks keep the old clang. */
    private void playHookSound(ServerLevel level, String cordStyle) {
        switch (cordStyle) {
            case HookCordStyles.VINE -> level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.WEEPING_VINES_BREAK, SoundSource.HOSTILE, 2.0F, 0.7F);
            case HookCordStyles.CHAIN_INFERNAL -> level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0F, 0.4F);
            case HookCordStyles.TENTACLE -> level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
            case HookCordStyles.GHOST -> level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.8F);
            default -> level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
    }

    /**
     * Tells everyone watching the boss to draw - or, with a zero duration, to drop - one cord.
     *
     * <p>The guard is for the odd client-side call into a reset: the distributor would throw
     * rather than quietly do nothing there.</p>
     */
    private void sendHookCord(int victimId, String cordStyle, int durationTicks) {
        if (npc.level().isClientSide()) {
            return;
        }
        NetworkWrapper.sendToTracking(npc, new PacketSyncHookCord(npc.getId(), victimId, cordStyle,
                durationTicks));
    }

    /** Textured cords outlive their pull unless the client is told the pull is over. */
    private void dropHookCord(HookPull pull) {
        if (HookCordStyles.isTextured(pull.cordStyle())) {
            sendHookCord(pull.targetId(), pull.cordStyle(), 0);
        }
    }

    /** Wipes the pulls and every cord they are still drawing, for a reset or a fight end. */
    private void clearHookPulls() {
        for (HookPull pull : activePulls) {
            dropHookCord(pull);
        }
        activePulls.clear();
    }

    /**
     * Drags everyone currently hooked one tick closer.
     *
     * <p>Runs before the combat-only early return: a pull that is already in flight has to
     * finish even if the boss loses its target halfway through, otherwise the victim is left
     * hanging in mid-air.</p>
     */
    private void tickHookPulls(ServerLevel level, long gameTime) {
        if (activePulls.isEmpty()) {
            return;
        }
        Iterator<HookPull> iterator = activePulls.iterator();
        while (iterator.hasNext()) {
            HookPull pull = iterator.next();
            boolean expired = gameTime >= pull.endsAt();
            if (expired || !(level.getEntity(pull.targetId()) instanceof LivingEntity victim)
                    || !victim.isAlive() || victim.isRemoved()) {
                // The client counts the same duration down on its own, so only a cord cut
                // short - a death, a despawn - is worth a packet.
                if (!expired) {
                    dropHookCord(pull);
                }
                iterator.remove();
                continue;
            }
            Vec3 destination = pull.gatherPoint() != null ? pull.gatherPoint() : npc.position();
            double stop = pull.stopDistance();
            if (stop > 0.0D && victim.position().distanceToSqr(destination) <= stop * stop) {
                dropHookCord(pull);
                iterator.remove();
                continue;
            }
            applyPull(victim, pull.strength(), pull.gatherPoint());
            // A textured cord is drawn by the client and needs no top-up.
            if ((gameTime & 1L) == 0L && !HookCordStyles.isTextured(pull.cordStyle())) {
                drawHookChain(level, victim);
            }
        }
    }

    private void applyPull(LivingEntity victim, double strength, Vec3 gatherPoint) {
        // The drag is the hook rather than a side effect of it, so it asks for itself: a pull
        // already in flight when the mask - or a totem's ability list - changes must not keep
        // tugging.
        if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.HOOK)) {
            return;
        }
        Vec3 destination = gatherPoint != null ? gatherPoint : npc.position();
        Vec3 delta = destination.subtract(victim.position());
        double distance = delta.length();
        if (distance < 1.0E-4D) {
            return;
        }
        Vec3 velocity = delta.scale(strength / distance);
        // A flat yank grinds the victim into whatever is between them and the boss; a little
        // lift lets them clear a step or a fence instead of sticking to it.
        double lift = Math.min(0.35D, distance * 0.03D);
        victim.setDeltaMovement(velocity.x, velocity.y + lift, velocity.z);
        victim.fallDistance = 0.0F;
        // Players simulate their own movement, so the server has to push the new velocity
        // to them explicitly. hurtMarked is what makes ServerEntity send it.
        victim.hurtMarked = true;
    }

    private void drawHookChain(ServerLevel level, LivingEntity victim) {
        Vec3 from = new Vec3(npc.getX(), npc.getEyeY() - 0.2D, npc.getZ());
        Vec3 to = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        Vec3 step = to.subtract(from);
        int points = Mth.clamp((int) (step.length() * 2.0D), 1, 64);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private boolean tryStartGeyser(ServerLevel level, TeleportPathData data,
                                   BossPhaseData phase, long gameTime) {
        if (!phase.isGeyserEnabled() || gameTime < nextGeyserAt) return false;
        List<LivingEntity> targets = selectAbilityTargets(level, phase.getGeyserTargetMode(),
                phase.getGeyserMaxRange(), candidate -> isValidGeyserTarget(candidate, phase),
                phase.getGeyserTargetCount());
        if (targets.isEmpty()) {
            nextGeyserAt = gameTime + 10;
            return false;
        }
        pendingExtraTargets.clear();
        for (int i = 1; i < targets.size(); i++) {
            pendingExtraTargets.add(targets.get(i).getId());
        }
        beginAction(PendingAction.GEYSER, phase.getGeyserAnimation(),
                phase.getGeyserActionDelayTicks(), gameTime, targets.get(0), data, phase);
        nextGeyserAt = gameTime + phase.getGeyserActionDelayTicks()
                + rageDown(phase.getGeyserCooldownTicks());
        return true;
    }

    /**
     * Line of sight is deliberately not required: the column comes up through the floor, so
     * a wall someone is standing behind is nothing for it to reach around.
     */
    private boolean isValidGeyserTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.GEYSER)) {
            return false;
        }
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getGeyserMinRange();
        double max = phase.getGeyserMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    /**
     * Lights a fuse under everyone this cast wound up on.
     *
     * <p>Nothing erupts here. The mark goes on the floor and {@link BossGeyserScheduler}
     * owns it from now on, because the boss is back on its rotation long before the column
     * comes up - which is the whole point of the ability.</p>
     */
    private void performGeyser(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<LivingEntity> victims = new ArrayList<>();
        LivingEntity primary = pendingTarget(level);
        if (primary != null && isValidGeyserTarget(primary, phase)) {
            victims.add(primary);
        }
        for (int id : pendingExtraTargets) {
            if (level.getEntity(id) instanceof LivingEntity extra
                    && isValidGeyserTarget(extra, phase) && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        if (victims.isEmpty()) {
            return;
        }
        BlockState fluid = geyserFluid(phase);
        // The fuse is deliberately left alone by the enrage: it is the window a player gets
        // to read the mark and step off it, not a number the fight is allowed to turn up.
        int damage = rageUp(phase.getGeyserDamage());
        int launch = rageUp(phase.getGeyserLaunch());
        for (LivingEntity victim : victims) {
            BossGeyserScheduler.schedule(level, npc, victim, phase, fluid, damage, launch, gameTime);
        }
    }

    /** What the eruption pools, or null when it pools nothing or the id is not a fluid. */
    private BlockState geyserFluid(BossPhaseData phase) {
        if (!phase.leavesGeyserFluid()) {
            return null;
        }
        BlockState fluid = FluidBlockUtil.resolve(phase.getGeyserFluid());
        if (fluid == null) {
            // The geyser still goes off; only the puddle is dropped. Reported once per broken
            // id rather than once per eruption.
            if (!phase.getGeyserFluid().equals(reportedBrokenGeyserFluid)) {
                reportedBrokenGeyserFluid = phase.getGeyserFluid();
                LOGGER.warn("Boss {} cannot pool {}: that block is not a fluid",
                        npc.getName().getString(), phase.getGeyserFluid());
            }
            return null;
        }
        reportedBrokenGeyserFluid = "";
        return fluid;
    }

    /**
     * A stone sent rolling or thrown down a corridor in front of the boss.
     *
     * <p>Where it goes is settled here, exactly as the line strike's corridor is: the
     * warning on the floor promises one path, and the boss keeps that promise even if
     * whoever it picked spends the whole wind-up running sideways.</p>
     */
    private boolean tryStartBoulder(ServerLevel level, TeleportPathData data,
                                    BossPhaseData phase, long gameTime) {
        if (!phase.canLaunchBoulder() || gameTime < nextBoulderAt) return false;
        LivingEntity target = selectAbilityTarget(level, phase.getBoulderTargetMode(),
                phase.getBoulderRange(), candidate -> isValidBoulderTarget(candidate, phase));
        if (target == null || EntityBossBoulder.resolveBlock(phase.getBoulderBlock()) == null) {
            nextBoulderAt = gameTime + 20;
            return false;
        }
        Vec3 flat = new Vec3(target.getX() - npc.getX(), 0.0D, target.getZ() - npc.getZ());
        // Somebody standing inside the boss leaves no direction to read off them, so the
        // gaze decides rather than the aim collapsing to nothing.
        boulderAxis = flat.lengthSqr() < 1.0E-6D ? facingAxis() : flat.normalize();
        beginAction(PendingAction.BOULDER, phase.getBoulderAnimation(),
                phase.getBoulderActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would launch the stone before the swing does.
        nextBoulderAt = gameTime + phase.getBoulderActionDelayTicks()
                + rageDown(phase.getBoulderCooldownTicks());
        return true;
    }

    private boolean isValidBoulderTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.BOULDER)) {
            return false;
        }
        // Measured flat, the way the corridor itself is laid out.
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double range = phase.getBoulderRange();
        return dx * dx + dz * dz <= range * range;
    }

    private void performBoulder(ServerLevel level, BossPhaseData phase) {
        Vec3 axis = boulderAxis;
        if (axis == null) {
            return;
        }
        BlockState block = EntityBossBoulder.resolveBlock(phase.getBoulderBlock());
        if (block == null) {
            if (!phase.getBoulderBlock().equals(reportedBrokenBoulderBlock)) {
                reportedBrokenBoulderBlock = phase.getBoulderBlock();
                LOGGER.warn("Boss {} cannot launch a boulder of {}: no such block",
                        npc.getName().getString(), phase.getBoulderBlock());
            }
            return;
        }
        reportedBrokenBoulderBlock = "";
        // Whatever the eased turn had left to cover is finished on the tick the stone
        // leaves, so the boss really faces down the corridor it promised.
        turnTowardAxis(axis, phase.getBoulderRange(), 360.0F);

        EntityBossBoulder boulder = new EntityBossBoulder(EntityRegistry.entityBossBoulder, level);
        boulder.setOwner(npc);
        boulder.configure(block, phase.getBoulderStyle(), phase.getBoulderScale(),
                rageUp(phase.getBoulderDamage()), rageUp(phase.getBoulderKnockback()),
                phase.isBoulderStopsOnHit(), phase.getBoulderShatterRadius(),
                rageUp(phase.getBoulderShatterDamage()), phase.getBoulderVfx(),
                phase.getBoulderEffects());
        double offset = npc.getBbWidth() * 0.5D + phase.getBoulderScale() / 20.0D + 0.25D;
        boolean rolls = phase.getBoulderMode() == BossPhaseData.BOULDER_MODE_ROLL;
        boulder.setPos(npc.getX() + axis.x * offset,
                rolls ? npc.getY() + 0.1D : npc.getY() + npc.getBbHeight() * 0.6D,
                npc.getZ() + axis.z * offset);
        // The corridor is measured from the boss, so the spawn offset comes off the travel
        // budget rather than being rolled past the far end of the warning.
        double travel = Math.max(2.0D, phase.getBoulderRange() - offset);
        if (rolls) {
            boulder.launchRoll(axis, phase.getBoulderSpeed(), travel);
        } else {
            boulder.launchThrow(axis, phase.getBoulderSpeed(), travel);
        }
        if (!level.addFreshEntity(boulder)) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                block.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    /**
     * A ring of stones dropped out of the sky around wherever the boss is standing.
     *
     * <p>Nothing is aimed: the ring is the shape, and the cast only asks whether there is
     * anybody inside it worth spending a cooldown on. Where each stone comes down is settled
     * by {@link BossBoulderRainScheduler} on the tick the cast lands, because the boss is
     * back on its rotation long before the last of them arrives.</p>
     */
    private boolean tryStartBoulderRain(ServerLevel level, TeleportPathData data,
                                        BossPhaseData phase, long gameTime) {
        if (!phase.canLaunchBoulderRain() || gameTime < nextBoulderRainAt) return false;
        if (EntityBossBoulder.resolveBlock(phase.getBoulderRainBlock()) == null
                || !hasBoulderRainTargets(level, phase)) {
            nextBoulderRainAt = gameTime + 20;
            return false;
        }
        beginAction(PendingAction.BOULDER_RAIN, phase.getBoulderRainAnimation(),
                phase.getBoulderRainActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would start the volley before the swing does.
        nextBoulderRainAt = gameTime + phase.getBoulderRainActionDelayTicks()
                + rageDown(phase.getBoulderRainCooldownTicks());
        return true;
    }

    /**
     * Whether the ring has anybody in it.
     *
     * <p>Swept to the outer edge and no further: somebody standing in the dead zone at the
     * boss' feet is not a reason to rain, because not one stone can reach them there.</p>
     */
    private boolean hasBoulderRainTargets(ServerLevel level, BossPhaseData phase) {
        double min = phase.getBoulderRainMinRadius();
        for (LivingEntity target : getTargetsAround(level, npc.position(),
                phase.getBoulderRainRadius(), BossAbilityKind.BOULDER_RAIN)) {
            if (target.position().distanceToSqr(npc.position()) >= min * min) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the whole volley over, and nothing else.
     *
     * <p>Not one stone falls here: the points, the damage and the enrage bonus are snapshotted
     * on this tick and the scheduler drops them on its own clock, which is what lets the boss
     * carry on fighting while its rain is still in the air.</p>
     */
    private void performBoulderRain(ServerLevel level, BossPhaseData phase, long gameTime) {
        BlockState block = EntityBossBoulder.resolveBlock(phase.getBoulderRainBlock());
        if (block == null) {
            if (!phase.getBoulderRainBlock().equals(reportedBrokenBoulderRainBlock)) {
                reportedBrokenBoulderRainBlock = phase.getBoulderRainBlock();
                LOGGER.warn("Boss {} cannot rain boulders of {}: no such block",
                        npc.getName().getString(), phase.getBoulderRainBlock());
            }
            return;
        }
        reportedBrokenBoulderRainBlock = "";
        BossBoulderRainScheduler.schedule(level, npc, phase, npc.position(), block,
                rageUp(phase.getBoulderRainDamage()), rageUp(phase.getBoulderRainKnockback()),
                rageUp(phase.getBoulderRainShatterDamage()), gameTime);
    }

    private boolean tryStartLeap(ServerLevel level, TeleportPathData data,
                                 BossPhaseData phase, long gameTime) {
        if (!phase.isLeapEnabled() || gameTime < nextLeapAt || leapAirborne) return false;
        if (!npc.onGround()) {
            // Nothing to push off from. Knocked into the air or standing in a boat, the
            // boss simply tries again in half a second.
            nextLeapAt = gameTime + 10;
            return false;
        }
        LivingEntity target = null;
        if (phase.getLeapMode() == BossPhaseData.LEAP_MODE_TARGET) {
            target = selectAbilityTarget(level, phase.getLeapTargetMode(),
                    phase.getLeapMaxRange(), candidate -> isValidLeapTarget(candidate, phase));
            if (target == null) {
                nextLeapAt = gameTime + 10;
                return false;
            }
        }
        Vec3 destination = resolveLeapDestination(data, phase, target);
        if (destination == null) {
            nextLeapAt = gameTime + 20;
            return false;
        }
        leapDestination = destination;
        leapPhaseIndex = currentPhase;
        beginAction(PendingAction.LEAP, phase.getLeapAnimation(),
                phase.getLeapActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled - the windup is measured against the leap animation.
        nextLeapAt = gameTime + phase.getLeapActionDelayTicks() + rageDown(phase.getLeapCooldownTicks());
        return true;
    }

    /**
     * Line of sight is deliberately not required: clearing a wall someone is hiding behind
     * is the whole point of a jump, and a hook's rule would take that away.
     */
    private boolean isValidLeapTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAbilityTarget(target, BossAbilityKind.LEAP)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getLeapMinRange();
        double max = phase.getLeapMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    /** Where this leap is aimed, already pulled back inside the home leash. */
    private Vec3 resolveLeapDestination(TeleportPathData data, BossPhaseData phase, LivingEntity target) {
        Vec3 destination = switch (phase.getLeapMode()) {
            case BossPhaseData.LEAP_MODE_TARGET -> target == null ? null : target.position();
            case BossPhaseData.LEAP_MODE_FIXED -> new Vec3(phase.getLeapFixedX() + 0.5D,
                    phase.getLeapFixedY(), phase.getLeapFixedZ() + 0.5D);
            case BossPhaseData.LEAP_MODE_ARENA_OFFSET -> new Vec3(homeX + phase.getLeapOffsetX(),
                    homeY + phase.getLeapOffsetY(), homeZ + phase.getLeapOffsetZ());
            // Straight up: the boss comes back down onto the spot it left.
            default -> npc.position();
        };
        return destination == null ? null : clampToHomeLeash(data, destination);
    }

    /**
     * A landing outside the leash would end the encounter on the boss' own terms, so the
     * destination is pulled back to just inside the edge before anything is pushed off.
     */
    private Vec3 clampToHomeLeash(TeleportPathData data, Vec3 destination) {
        if (!data.isHomeLeashEnabled()) {
            return destination;
        }
        boolean vertical = data.isHomeLeashVertical();
        double limit = Math.max(0.0D, data.getHomeLeashRadius() - LEAP_LEASH_MARGIN);
        double dx = destination.x - homeX;
        double dz = destination.z - homeZ;
        double dy = vertical ? destination.y - homeY : 0.0D;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= limit || distance < 1.0E-4D) {
            return destination;
        }
        double scale = limit / distance;
        return new Vec3(homeX + dx * scale,
                vertical ? homeY + dy * scale : destination.y,
                homeZ + dz * scale);
    }

    /** The push itself, at the end of the windup. */
    private void performLeap(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        // The push, not a timer, is what frees a rooted crouch: from here to touchdown the
        // boss is a thrown object, and even a leap that aborts below has nothing left to
        // stand still for.
        endCastRoot();
        LivingEntity target = pendingTarget(level);
        // The victim may have died or run out of range during the windup. The boss still
        // jumps: the marker already promised that spot, and pulling out looks like a bug.
        Vec3 destination = resolveLeapDestination(data, phase,
                isValidLeapTarget(target, phase) ? target : null);
        if (destination == null) {
            destination = leapDestination;
        }
        if (destination == null) {
            clearLeap();
            return;
        }

        // An arc that peaks below where it is meant to come down never gets there, so a
        // destination above the boss raises the jump. The ceiling still has the final say.
        int wanted = Mth.clamp((int) Math.ceil(destination.y - npc.getY()) + 1,
                phase.getLeapHeight(), BossPhaseData.MAX_LEAP_HEIGHT);
        int height = availableLeapHeight(level, wanted);
        if (height < 1) {
            // Nowhere to jump to - a boss walled in under a slab would only bump its head
            // and stick. The cooldown has already been spent, so it will try again later.
            clearLeap();
            return;
        }

        double rise = leapSpeedForHeight(height);
        double drop = Math.max(0.0D, npc.getY() + height - destination.y);
        int flightTicks = Math.max(1, (int) Math.ceil(leapRiseTicks(rise)) + (int) Math.ceil(leapFallTicks(drop)));
        double dx = destination.x - npc.getX();
        double dz = destination.z - npc.getZ();
        double reach = Math.sqrt(dx * dx + dz * dz);
        double speed = reach < 1.0E-4D ? 0.0D : Math.min(LEAP_MAX_HORIZONTAL_SPEED,
                reach * LEAP_REACH_CORRECTION / flightTicks);
        leapDriveX = reach < 1.0E-4D ? 0.0D : dx / reach * speed;
        leapDriveZ = reach < 1.0E-4D ? 0.0D : dz / reach * speed;

        npc.getNavigation().stop();
        npc.fallDistance = 0.0F;
        npc.setDeltaMovement(leapDriveX, rise, leapDriveZ);
        // Mobs are position-synced, but handing the client the velocity too keeps the arc
        // smooth instead of letting it interpolate a straight line between updates.
        npc.hurtMarked = true;

        leapDestination = destination;
        leapAirborne = true;
        leapLeftGround = false;
        leapLaunchedAt = gameTime;
        leapAirTimeoutAt = gameTime + phase.getLeapMaxAirTicks();
        busyUntil = Math.max(busyUntil, gameTime + 1);

        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 1.5F, 1.2F);
        level.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 0.1D, npc.getZ(), 20,
                npc.getBbWidth() * 0.5D, 0.05D, npc.getBbWidth() * 0.5D, 0.05D);
    }

    /**
     * How far up there is actually room to jump.
     *
     * <p>Counts upward and stops at the first blocked block rather than looking for the
     * highest clear one: an opening above a low ceiling is not somewhere the boss can get
     * to, and aiming for it would just be a head-first bump.</p>
     */
    private int availableLeapHeight(ServerLevel level, int desired) {
        int clear = 0;
        for (int height = 1; height <= desired; height++) {
            if (!level.noCollision(npc, npc.getBoundingBox().move(0.0D, height, 0.0D))) {
                break;
            }
            clear = height;
        }
        return clear;
    }

    /** Ticks the climb from an upward push of {@code speed} takes to reach its peak. */
    private static double leapRiseTicks(double speed) {
        double terminal = leapTerminalSpeed();
        return Math.log(terminal / (speed + terminal)) / Math.log(LEAP_VERTICAL_DRAG);
    }

    /** How high that climb gets. */
    private static double leapPeakHeight(double speed) {
        return speed / (1.0D - LEAP_VERTICAL_DRAG) - leapTerminalSpeed() * leapRiseTicks(speed);
    }

    /** Ticks a fall from a standstill takes to cover {@code drop} blocks. */
    private static double leapFallTicks(double drop) {
        double terminal = leapTerminalSpeed();
        double low = 0.0D;
        double high = 400.0D;
        for (int step = 0; step < 24; step++) {
            double mid = (low + high) * 0.5D;
            double fallen = terminal * (mid - (1.0D - Math.pow(LEAP_VERTICAL_DRAG, mid)) / (1.0D - LEAP_VERTICAL_DRAG));
            if (fallen < drop) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5D;
    }

    /**
     * The push that gets the boss {@code height} blocks up.
     *
     * <p>Searched rather than solved: with the drag in it {@link #leapPeakHeight} has no
     * neat inverse, and a couple of dozen halvings once per leap costs nothing.</p>
     */
    private static double leapSpeedForHeight(double height) {
        double low = 0.0D;
        double high = LEAP_MAX_RISE_SPEED;
        for (int step = 0; step < 24; step++) {
            double mid = (low + high) * 0.5D;
            if (leapPeakHeight(mid) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5D;
    }

    /** The speed a falling entity settles at, which is what the drag is measured against. */
    private static double leapTerminalSpeed() {
        return LEAP_GRAVITY * LEAP_VERTICAL_DRAG / (1.0D - LEAP_VERTICAL_DRAG);
    }

    /**
     * Carries a leap through its windup and its flight.
     *
     * <p>Runs every tick, above everything that could return early: the boss is a thrown
     * object until it touches down, and the landing has to be caught wherever that is.</p>
     */
    private void tickLeap(ServerLevel level, TeleportPathData data, long gameTime) {
        if (leapAirborne) {
            // Nothing may steer the boss mid air, and its own arc must not hurt it.
            npc.getNavigation().stop();
            npc.fallDistance = 0.0F;
            // Holds every other ability off until the boss is back on the floor.
            busyUntil = Math.max(busyUntil, gameTime + 1);
            holdLeapCourse();

            if (!npc.onGround()) {
                leapLeftGround = true;
            } else if (leapLeftGround) {
                landLeap(level, data, gameTime);
                return;
            } else if (gameTime - leapLaunchedAt >= LEAP_LAUNCH_GRACE_TICKS) {
                // Never got off the ground - held down, or shoulder-deep in a slab.
                clearLeap();
                return;
            }
            if (gameTime >= leapAirTimeoutAt) {
                // A jump into a pit or into deep water never lands. The slam is dropped
                // rather than fired off somewhere nobody was standing.
                clearLeap();
                return;
            }
        }
        refreshLeapAim(level, data);
        drawLeapTelegraph(level, data, gameTime);
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
    private void holdLeapCourse() {
        if (npc.horizontalCollision || (leapDriveX == 0.0D && leapDriveZ == 0.0D)) {
            return;
        }
        npc.setDeltaMovement(leapDriveX, npc.getDeltaMovement().y, leapDriveZ);
    }

    /** Touchdown: the boss stops dead, plays its landing animation and slams. */
    private void landLeap(ServerLevel level, TeleportPathData data, long gameTime) {
        BossPhaseData phase = leapPhase(data);
        Vec3 impact = npc.position();
        // Re-pins the stationary boss on the spot it came down on, before anything else.
        clearLeap();
        busyUntil = Math.max(busyUntil, gameTime + POST_ACTION_LOCK_TICKS);
        if (phase == null) {
            return;
        }
        playAnimation(phase.getLeapLandAnimation());
        performLeapImpact(level, phase, impact);
    }

    private void performLeapImpact(ServerLevel level, BossPhaseData phase, Vec3 impact) {
        // Started before the hits so the wave leaves at the same moment the damage lands.
        BossAreaVfxScheduler.schedule(level, impact, phase.getLeapVfx(), phase.getLeapImpactRadius(),
                LEAP_VFX_DURATION_TICKS, phase.isLeapBlockWave());
        int damage = rageUp(phase.getLeapImpactDamage());
        for (LivingEntity target : getTargetsAround(level, impact, phase.getLeapImpactRadius(),
                BossAbilityKind.LEAP)) {
            BossAbilityDamageUtil.hit(target, BossAbilityKind.LEAP, npc, damage,
                    phase.getLeapEffects(), rageUp(phase.getLeapImpactKnockback()),
                    impact.x - target.getX(), impact.z - target.getZ());
        }
        playLeapImpactFeedback(level, impact);
    }

    private void playLeapImpactFeedback(ServerLevel level, Vec3 impact) {
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.2D, impact.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        BlockPos below = BlockPos.containing(impact.x, impact.y - 0.2D, impact.z);
        BlockState floor = level.getBlockState(below);
        if (!floor.isAir()) {
            // The floor it landed on, kicked up around its feet.
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor),
                    impact.x, impact.y + 0.1D, impact.z, 30, 0.6D, 0.1D, 0.6D, 0.15D);
        }
    }

    /**
     * Keeps a leap that has not been pushed off yet aimed at where its target is now, which
     * is what gives that target the chance to step out of the marked ring before it lands.
     *
     * <p>Kept out of the drawing below because the aim is not decoration: the push itself
     * falls back on this spot when the victim dies inside the windup.</p>
     */
    private void refreshLeapAim(ServerLevel level, TeleportPathData data) {
        if (pendingAction != PendingAction.LEAP || leapDestination == null) {
            return;
        }
        BossPhaseData phase = leapPhase(data);
        if (phase == null) {
            return;
        }
        Vec3 refreshed = resolveLeapDestination(data, phase, pendingTarget(level));
        if (refreshed != null) {
            leapDestination = refreshed;
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
    private void drawLeapTelegraph(ServerLevel level, TeleportPathData data, long gameTime) {
        boolean windup = pendingAction == PendingAction.LEAP;
        if (leapDestination == null || (!leapAirborne && !windup)) {
            return;
        }
        BossPhaseData phase = leapPhase(data);
        if (phase == null || !phase.isLeapTelegraph() || gameTime % LEAP_MARKER_INTERVAL_TICKS != 0L) {
            return;
        }
        // Never both marks over one ring: whichever of the two is drawing, it draws alone.
        if (windup && data.isTelegraphEnabled()
                && data.isTelegraphAbility(BossAbilityKind.LEAP)) {
            return;
        }
        double radius = phase.getLeapImpactRadius();
        int points = Mth.clamp((int) Math.round(Mth.TWO_PI * radius / LEAP_MARKER_SPACING), 8, 48);
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            level.sendParticles(ParticleTypes.SMALL_FLAME,
                    leapDestination.x + Math.cos(angle) * radius,
                    leapDestination.y + 0.15D,
                    leapDestination.z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** The phase a leap belongs to, so a phase change mid air cannot rewrite its slam. */
    private BossPhaseData leapPhase(TeleportPathData data) {
        return leapPhaseIndex >= 0 && leapPhaseIndex < data.getPhaseCount()
                ? data.getPhase(leapPhaseIndex) : null;
    }

    /** Drops a leap wherever it got to and hands the boss back to the stationary pin. */
    private void clearLeap() {
        if (leapAirborne) {
            npc.setDeltaMovement(Vec3.ZERO);
            npc.fallDistance = 0.0F;
            // The pin was let go for the flight, so it has to be moved to wherever this ended.
            lockedX = npc.getX();
            lockedZ = npc.getZ();
        }
        leapAirborne = false;
        leapLeftGround = false;
        leapDriveX = 0.0D;
        leapDriveZ = 0.0D;
        leapDestination = null;
        leapPhaseIndex = -1;
        leapLaunchedAt = NOT_SCHEDULED;
        leapAirTimeoutAt = NOT_SCHEDULED;
    }

    private boolean tryStartSummon(ServerLevel level, TeleportPathData data,
                                   BossPhaseData phase, long gameTime) {
        if (!phase.canSummon() || gameTime < nextSummonAt) return false;
        if (BossMinionUtil.countAlive(level, npc, phase.getMaxAliveMinions()) >= phase.getMaxAliveMinions()) {
            nextSummonAt = gameTime + 20;
            return false;
        }
        beginAction(PendingAction.SUMMON, phase.getSummonAnimation(),
                phase.getSummonActionDelayTicks(), gameTime, null, data, phase);
        nextSummonAt = gameTime + phase.getSummonActionDelayTicks() + rageDown(phase.getSummonCooldownTicks());
        return true;
    }

    private void beginAction(PendingAction action, String animation, int actionDelay, long gameTime,
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
        if (pendingAction == PendingAction.NONE || gameTime >= pendingActionAt
                || gameTime % TELEGRAPH_INTERVAL_TICKS != 0L) {
            return;
        }
        paintTelegraph(level, data);
    }

    private void paintTelegraph(ServerLevel level, TeleportPathData data) {
        int ability = abilityKind(pendingAction);
        if (ability < 0 || !telegraphs(data, ability)) {
            return;
        }
        // Decoration only, so an arena with nobody in it costs nothing to warn.
        if (level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(),
                TELEGRAPH_AUDIENCE_RANGE, false) == null) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(ability);
        if (data.isTelegraphZone()) {
            drawTelegraphZone(level, data, ability, dust);
        }
        if (data.isTelegraphAura()) {
            BossTelegraphUtil.aura(level, npc, dust);
        }
    }

    /**
     * The one-off half of the warning: a note and a name, both at the moment the boss
     * commits rather than on every tick the wind-up runs for.
     */
    private void announceTelegraph(TeleportPathData data, PendingAction action) {
        int ability = abilityKind(action);
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
                if (lineAttackAxis != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), lineAttackAxis,
                            phase.getLineAttackLength(), phase.getLineAttackWidth(),
                            phase.getLineAttackSideWidth(), dust,
                            BossTelegraphUtil.fadedDust(ability));
                }
            }
            case BOULDER -> {
                if (boulderAxis != null) {
                    // As wide as the stone itself and with no softer flank: standing a step
                    // outside this corridor really is standing clear.
                    BossTelegraphUtil.corridor(level, npc.position(), boulderAxis,
                            phase.getBoulderRange(), phase.getBoulderScale() / 10.0D,
                            0.0D, dust, BossTelegraphUtil.fadedDust(ability));
                }
            }
            case MELEE_ATTACK -> BossTelegraphUtil.arc(level, npc.position(),
                    phase.getMeleeAttackRange(), npc.getYRot(), TELEGRAPH_MELEE_HALF_ANGLE, dust);
            case RANGED_ATTACK, FLUID_SPIT, CAPTURE ->
                    drawTelegraphTargetZone(level, data, pendingTarget(level), dust);
            case HOOK, GEYSER -> {
                drawTelegraphTargetZone(level, data, pendingTarget(level), dust);
                for (int id : pendingExtraTargets) {
                    if (level.getEntity(id) instanceof LivingEntity victim) {
                        drawTelegraphTargetZone(level, data, victim, dust);
                    }
                }
            }
            case SUMMON -> drawTelegraphSpawnRings(level, phase, dust);
            case LEAP -> {
                BossPhaseData leaping = leapPhase(data);
                if (leaping != null && leapDestination != null) {
                    BossTelegraphUtil.ring(level, leapDestination, leaping.getLeapImpactRadius(), dust);
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
                    BossTelegraphUtil.ring(level, minionPointAnchor(point),
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
        BossPhaseData phase = leapPhase(data);
        return phase == null || phase.isLeapTelegraph();
    }

    /**
     * Which ability of the shared list an action performs. The warning and standing-cast
     * masks both index by it; -1 is a teleport or nothing at all, neither of which is on
     * the list.
     */
    private static int abilityKind(PendingAction action) {
        return switch (action) {
            case GROUND_ATTACK -> BossAbilityKind.AREA;
            case RANGED_ATTACK -> BossAbilityKind.RANGED;
            case MELEE_ATTACK -> BossAbilityKind.MELEE;
            case FLUID_SPIT -> BossAbilityKind.FLUID;
            case HOOK -> BossAbilityKind.HOOK;
            case CAPTURE -> BossAbilityKind.CAPTURE;
            case SUMMON -> BossAbilityKind.SUMMON;
            case LEAP -> BossAbilityKind.LEAP;
            case LINE_ATTACK -> BossAbilityKind.LINE;
            case GEYSER -> BossAbilityKind.GEYSER;
            case BOULDER -> BossAbilityKind.BOULDER;
            case BOULDER_RAIN -> BossAbilityKind.BOULDER_RAIN;
            case NONE, TELEPORT -> -1;
        };
    }

    /**
     * Ticks of plain warning to put in front of a wind-up too short to react to.
     *
     * <p>Zero for an ability nothing is drawn for: a pause nobody can see is not a warning,
     * it is the boss standing there doing nothing.</p>
     */
    private int telegraphLead(TeleportPathData data, PendingAction action, int actionDelay) {
        int ability = abilityKind(action);
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
            PendingAction dodged = pendingAction;
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
            case RANGED_ATTACK -> isValidRangedTarget(target, phase)
                    && npc.inventory.getProjectile() != null;
            case MELEE_ATTACK -> isValidMeleeTarget(target, phase);
            case FLUID_SPIT -> isValidFluidSpitTarget(target, phase);
            case HOOK -> hasWoundUpVictim(level, candidate -> isValidHookTarget(candidate, phase));
            case GEYSER -> hasWoundUpVictim(level, candidate -> isValidGeyserTarget(candidate, phase));
            case CAPTURE -> isValidCaptureTarget(target, phase);
            // A leap at a fixed spot lands there whoever is standing on it.
            case LEAP -> phase.getLeapMode() != BossPhaseData.LEAP_MODE_TARGET
                    || isValidLeapTarget(target, phase);
            // The corridor was committed to when the warning went up, so there is nothing
            // left to call off: walking out of it already is the dodge, and cancelling
            // would only bring the same strike back round in two seconds.
            case LINE_ATTACK, BOULDER -> true;
            // And the rain is not aimed at anybody at all: the ring is centred on the boss
            // and lands on ground, so there is nobody in particular who could have left it.
            case BOULDER_RAIN -> true;
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

    /** When one ability is next allowed to start. */
    private long abilityScheduleAt(PendingAction action) {
        return switch (action) {
            case SUMMON -> nextSummonAt;
            case GROUND_ATTACK -> nextGroundAttackAt;
            case LINE_ATTACK -> nextLineAttackAt;
            case RANGED_ATTACK -> nextRangedAttackAt;
            case MELEE_ATTACK -> nextMeleeAttackAt;
            case FLUID_SPIT -> nextFluidSpitAt;
            case HOOK -> nextHookAt;
            case CAPTURE -> nextCaptureAt;
            case LEAP -> nextLeapAt;
            case GEYSER -> nextGeyserAt;
            case BOULDER -> nextBoulderAt;
            case BOULDER_RAIN -> nextBoulderRainAt;
            case TELEPORT -> nextTeleportAt;
            case NONE -> NOT_SCHEDULED;
        };
    }

    private void setAbilityScheduleAt(PendingAction action, long at) {
        switch (action) {
            case SUMMON -> nextSummonAt = at;
            case GROUND_ATTACK -> nextGroundAttackAt = at;
            case LINE_ATTACK -> nextLineAttackAt = at;
            case RANGED_ATTACK -> nextRangedAttackAt = at;
            case MELEE_ATTACK -> nextMeleeAttackAt = at;
            case FLUID_SPIT -> nextFluidSpitAt = at;
            case HOOK -> nextHookAt = at;
            case CAPTURE -> nextCaptureAt = at;
            case LEAP -> nextLeapAt = at;
            case GEYSER -> nextGeyserAt = at;
            case BOULDER -> nextBoulderAt = at;
            case BOULDER_RAIN -> nextBoulderRainAt = at;
            case TELEPORT -> nextTeleportAt = at;
            case NONE -> {
                // Nothing was running, so there is no schedule to move.
            }
        }
    }

    /** Pulls a schedule in, and never pushes one out past the cooldown it already had. */
    private void bringAbilityScheduleForward(PendingAction action, long at) {
        long current = abilityScheduleAt(action);
        if (current == NOT_SCHEDULED || current > at) {
            setAbilityScheduleAt(action, at);
        }
    }

    /** Pays back the ticks an ability spent warning before its wind-up ever started. */
    private void delayAbilitySchedule(PendingAction action, int ticks) {
        long current = abilityScheduleAt(action);
        if (ticks > 0 && current != NOT_SCHEDULED) {
            setAbilityScheduleAt(action, current + ticks);
        }
    }

    private void executePendingAction(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (pendingAction == PendingAction.SUMMON) {
            summonMinions(level, phase);
            invulnerableSummonedOnce = true;
        } else if (pendingAction == PendingAction.GROUND_ATTACK) {
            performAreaAttack(level, phase);
        } else if (pendingAction == PendingAction.LINE_ATTACK) {
            performLineAttack(level, phase);
        } else if (pendingAction == PendingAction.RANGED_ATTACK) {
            performRangedAttack(level, phase);
        } else if (pendingAction == PendingAction.MELEE_ATTACK) {
            performMeleeAttack(level, phase);
        } else if (pendingAction == PendingAction.FLUID_SPIT) {
            performFluidSpit(level, phase);
        } else if (pendingAction == PendingAction.HOOK) {
            performHook(level, phase, gameTime);
        } else if (pendingAction == PendingAction.CAPTURE) {
            performCapture(level, phase, gameTime);
        } else if (pendingAction == PendingAction.LEAP) {
            performLeap(level, data, phase, gameTime);
        } else if (pendingAction == PendingAction.GEYSER) {
            performGeyser(level, phase, gameTime);
        } else if (pendingAction == PendingAction.BOULDER) {
            performBoulder(level, phase);
        } else if (pendingAction == PendingAction.BOULDER_RAIN) {
            performBoulderRain(level, phase, gameTime);
        } else if (pendingAction == PendingAction.TELEPORT) {
            List<int[]> points = npc.ais.getMovingPath();
            if (points.size() >= 2 && teleportToNextSafePoint(level, points, data)) {
                playPostTeleportAnimation(phase, gameTime);
            }
            scheduleNextTeleport(gameTime, phase);
        }
    }

    private void playPostTeleportAnimation(BossPhaseData phase, long gameTime) {
        if (phase.getAppearanceAnimation().isEmpty()) {
            return;
        }
        playAnimation(phase.getAppearanceAnimation());
        busyUntil = Math.max(busyUntil, gameTime + phase.getAppearanceLockTicks());
    }

    private void playAnimation(String animation) {
        if (animation == null || animation.isBlank()) {
            return;
        }
        try {
            RawAnimation raw = RawAnimation.begin().then(animation.trim(), Animation.LoopType.PLAY_ONCE);
            NetworkWrapper.sendAll(new PacketSyncAnimation(npc.getId(), raw));
        } catch (Throwable error) {
            LOGGER.warn("Could not play boss animation {} for NPC {}: {}", animation,
                    npc.getName().getString(), error.getMessage());
        }
    }

    private boolean teleportToNextSafePoint(ServerLevel level, List<int[]> points, TeleportPathData data) {
        for (int attempt = 0; attempt < points.size(); attempt++) {
            int candidate = nextPathIndex(points.size(), data.getOrder());
            lastPathIndex = candidate;
            int[] point = points.get(candidate);
            if (point == null || point.length < 3) continue;

            Vec3 destination = findSafePathDestination(level, point);
            if (destination == null) continue;
            double x = destination.x;
            double y = destination.y;
            double z = destination.z;
            try {
                if (data.shouldPlaySound()) {
                    level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ENDERMAN_TELEPORT,
                            SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                npc.teleportTo(x, y, z);
                npc.fallDistance = 0.0F;
                npc.setDeltaMovement(Vec3.ZERO);
                npc.getNavigation().stop();
                lockedX = x;
                lockedZ = z;
                npc.gameEvent(GameEvent.TELEPORT);
                if (data.shouldPlaySound()) {
                    level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT,
                            SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                return true;
            } catch (Throwable error) {
                // CustomNPCs is free to veto or break a teleport from a script hook. The
                // boss stays where it is and tries again on its next window, but somebody
                // debugging a boss that never moves deserves to find this in the log.
                LOGGER.warn("Boss {} could not teleport to path point {}: {}",
                        npc.getName().getString(), candidate, error.getMessage());
                lockedX = npc.getX();
                lockedZ = npc.getZ();
                return false;
            }
        }
        return false;
    }

    /**
     * The CustomNPCs pather stores the block that was clicked. For a normal floor click that block
     * is one block below the NPC's feet, while the initial path point already stores feet Y. Try the
     * exact coordinate first for compatibility, then transparently lift floor-clicked points by one.
     */
    private Vec3 findSafePathDestination(ServerLevel level, int[] point) {
        double x = point[0] + 0.5D;
        double z = point[2] + 0.5D;
        for (int yOffset = 0; yOffset <= 1; yOffset++) {
            double y = point[1] + yOffset;
            BlockPos blockPos = BlockPos.containing(x, y, z);
            AABB destinationBox = npc.getBoundingBox().move(x - npc.getX(), y - npc.getY(), z - npc.getZ());
            if (level.hasChunkAt(blockPos)
                    && level.getWorldBorder().isWithinBounds(blockPos)
                    && level.noCollision(npc, destinationBox)) {
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    private int nextPathIndex(int size, int order) {
        if (order == TeleportPathData.ORDER_RANDOM) {
            int candidate = npc.getRandom().nextInt(size - 1);
            return candidate >= lastPathIndex ? candidate + 1 : candidate;
        }
        if (order == TeleportPathData.ORDER_PING_PONG) {
            int candidate = lastPathIndex + pingPongDirection;
            if (candidate < 0 || candidate >= size) {
                pingPongDirection *= -1;
                candidate = lastPathIndex + pingPongDirection;
            }
            return candidate;
        }
        return (lastPathIndex + 1) % size;
    }

    private int findClosestPoint(List<int[]> points) {
        int closest = 0;
        double closestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            if (point == null || point.length < 3) continue;
            double distance = npc.distanceToSqr(point[0] + 0.5D, point[1], point[2] + 0.5D);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = i;
            }
        }
        return closest;
    }

    private void summonMinions(ServerLevel level, BossPhaseData phase) {
        // Capped at the ceiling it is subtracted from: past that the difference is never
        // positive anyway, and the walk does not have to finish counting a full arena.
        int available = phase.getMaxAliveMinions()
                - BossMinionUtil.countAlive(level, npc, phase.getMaxAliveMinions());
        int amount = Math.min(phase.getMinionCount(), Math.max(available, 0));
        if (amount <= 0) return;

        int spawned = 0;
        if (phase.getMinionSpawnMode() != BossPhaseData.MINION_SPAWN_RANDOM_RADIUS) {
            spawned = spawnConfiguredMinions(level, phase, amount);
        }

        boolean useRandom = phase.getMinionSpawnMode() == BossPhaseData.MINION_SPAWN_RANDOM_RADIUS
                || phase.getMinionSpawnMode() == BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM;
        if (!useRandom || phase.getMinionCloneName().isEmpty()) {
            return;
        }
        for (int i = spawned; i < amount; i++) {
            Vec3 position = findMinionPosition(level, phase.getMinionRadius());
            if (position == null) continue;
            spawnMinionClone(level, phase, phase.getMinionCloneName(), phase.getMinionCloneTab(),
                    position, Float.NaN, currentPhase, -1);
        }
    }

    private int spawnConfiguredMinions(ServerLevel level, BossPhaseData phase, int desired) {
        List<BossMinionSpawnPoint> points = orderedMinionSpawnPoints(level, phase);
        int spawned = 0;
        for (BossMinionSpawnPoint point : points) {
            if (spawned >= desired) {
                break;
            }
            Vec3 anchor = minionPointAnchor(point);
            Vec3 position = findConfiguredMinionPosition(level, anchor,
                    phase.getMinionPointSearchRadius(), currentPhase, point.getPointId());
            if (position == null) {
                continue;
            }
            String cloneName = point.getCloneNameOverride().isEmpty()
                    ? phase.getMinionCloneName() : point.getCloneNameOverride();
            int cloneTab = point.getCloneTabOverride() == 0
                    ? phase.getMinionCloneTab() : point.getCloneTabOverride();
            Entity minion = spawnMinionClone(level, phase, cloneName, cloneTab, position,
                    point.getYaw(), currentPhase, point.getPointId());
            if (minion != null) {
                spawned++;
                if (phase.getMinionSpawnOrder() == BossPhaseData.MINION_ORDER_ROUND_ROBIN) {
                    minionRoundRobinCursor.put(currentPhase, point.getPointId());
                }
            }
        }
        return spawned;
    }

    private List<BossMinionSpawnPoint> orderedMinionSpawnPoints(ServerLevel level, BossPhaseData phase) {
        List<BossMinionSpawnPoint> candidates = new ArrayList<>();
        for (BossMinionSpawnPoint point : phase.getMinionSpawnPoints().entries()) {
            if (!point.isEnabled()) {
                continue;
            }
            String cloneName = point.getCloneNameOverride().isEmpty()
                    ? phase.getMinionCloneName() : point.getCloneNameOverride();
            if (cloneName.isEmpty()) {
                continue;
            }
            if (!phase.isMinionReuseOccupiedPoints()
                    && BossMinionUtil.isSlotOccupied(level, npc, currentPhase, point.getPointId())) {
                warnBlockedMinionPoint(currentPhase, point.getPointId(),
                        "slot already has a living minion");
                continue;
            }
            candidates.add(point);
        }

        if (phase.getMinionSpawnOrder() == BossPhaseData.MINION_ORDER_RANDOM) {
            return weightedRandomMinionPoints(candidates);
        }
        if (phase.getMinionSpawnOrder() != BossPhaseData.MINION_ORDER_ROUND_ROBIN
                || candidates.size() < 2) {
            return candidates;
        }

        Integer lastPointId = minionRoundRobinCursor.get(currentPhase);
        if (lastPointId == null) {
            return candidates;
        }
        List<BossMinionSpawnPoint> configured = phase.getMinionSpawnPoints().entries();
        int lastIndex = -1;
        for (int i = 0; i < configured.size(); i++) {
            if (configured.get(i).getPointId() == lastPointId) {
                lastIndex = i;
                break;
            }
        }
        if (lastIndex < 0) {
            return candidates;
        }
        Set<Integer> candidateIds = new HashSet<>();
        for (BossMinionSpawnPoint point : candidates) {
            candidateIds.add(point.getPointId());
        }
        List<BossMinionSpawnPoint> rotated = new ArrayList<>(candidates.size());
        for (int offset = 1; offset <= configured.size(); offset++) {
            BossMinionSpawnPoint point = configured.get((lastIndex + offset) % configured.size());
            if (candidateIds.contains(point.getPointId())) {
                rotated.add(point);
            }
        }
        return rotated;
    }

    private List<BossMinionSpawnPoint> weightedRandomMinionPoints(List<BossMinionSpawnPoint> candidates) {
        List<BossMinionSpawnPoint> remaining = new ArrayList<>(candidates);
        List<BossMinionSpawnPoint> ordered = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            int totalWeight = 0;
            for (BossMinionSpawnPoint point : remaining) {
                totalWeight += point.getWeight();
            }
            int roll = npc.getRandom().nextInt(totalWeight);
            int selected = 0;
            for (int i = 0; i < remaining.size(); i++) {
                roll -= remaining.get(i).getWeight();
                if (roll < 0) {
                    selected = i;
                    break;
                }
            }
            ordered.add(remaining.remove(selected));
        }
        return ordered;
    }

    private Vec3 minionPointAnchor(BossMinionSpawnPoint point) {
        if (point.getCoordinateMode() == BossMinionSpawnPoint.COORDINATE_FIXED) {
            return new Vec3(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D);
        }
        return new Vec3(homeX + point.getX(), homeY + point.getY(), homeZ + point.getZ());
    }

    private Vec3 findConfiguredMinionPosition(ServerLevel level, Vec3 anchor, int radius,
                                               int phaseIndex, int pointId) {
        BlockPos anchorBlock = BlockPos.containing(anchor);
        if (!level.hasChunkAt(anchorBlock)) {
            warnBlockedMinionPoint(phaseIndex, pointId, "anchor chunk is not loaded");
            return null;
        }

        List<int[]> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    offsets.add(new int[] {x, z});
                }
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0]).thenComparingInt(offset -> offset[1]));

        boolean foundLoaded = false;
        boolean foundInsideWorld = false;
        boolean foundUnoccupied = false;
        for (int[] offset : offsets) {
            Vec3 candidate = anchor.add(offset[0], 0.0D, offset[1]);
            BlockPos feet = BlockPos.containing(candidate);
            if (!level.hasChunkAt(feet)) {
                continue;
            }
            foundLoaded = true;
            if (!level.getWorldBorder().isWithinBounds(feet)
                    || candidate.y < level.getMinBuildHeight()
                    || candidate.y + 1.8D >= level.getMaxBuildHeight()) {
                continue;
            }
            foundInsideWorld = true;
            AABB box = minionSpawnBox(candidate);
            if (!level.noCollision(box)
                    || !level.getEntities((Entity) null, box,
                    entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) {
                continue;
            }
            foundUnoccupied = true;
            BlockPos support = feet.below();
            if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                continue;
            }
            return candidate;
        }

        String reason = !foundLoaded ? "search chunks are not loaded"
                : !foundInsideWorld ? "outside the world border or build height"
                : !foundUnoccupied ? "spawn box is occupied" : "no solid support";
        warnBlockedMinionPoint(phaseIndex, pointId, reason);
        return null;
    }

    private static AABB minionSpawnBox(Vec3 position) {
        return new AABB(position.x - 0.35D, position.y, position.z - 0.35D,
                position.x + 0.35D, position.y + 1.8D, position.z + 0.35D);
    }

    private Entity spawnMinionClone(ServerLevel level, BossPhaseData phase,
                                    String cloneName, int cloneTab, Vec3 position,
                                    float yaw, int phaseIndex, int slotIndex) {
        if (cloneName == null || cloneName.isBlank()) {
            return null;
        }
        String cloneKey = cloneTab + ":" + cloneName;
        try {
            IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(position.x, position.y, position.z,
                    cloneTab, cloneName, NpcAPI.Instance().getIWorld(level));
            if (wrapper == null || wrapper.getMCEntity() == null) {
                warnBrokenMinionClone(cloneKey, "clone returned no entity");
                return null;
            }
            Entity minion = wrapper.getMCEntity();
            BossMinionUtil.markAsMinion(minion, npc, phaseIndex, slotIndex);
            BossCloneRespawnGuard.suppressSelfRespawn(minion);
            if (Float.isFinite(yaw)) {
                minion.setYRot(yaw);
                if (minion instanceof Mob mob) {
                    mob.setYHeadRot(yaw);
                    mob.yBodyRot = yaw;
                }
            }
            if (minion instanceof Mob mob && hasCombatTarget() && mob.canAttack(npc.getTarget())) {
                mob.setTarget(npc.getTarget());
            }
            return minion;
        } catch (Throwable error) {
            warnBrokenMinionClone(cloneKey, error.getMessage());
            return null;
        }
    }

    private void warnBrokenMinionClone(String cloneKey, String reason) {
        if (reportedBrokenMinionClones.add(cloneKey)) {
            LOGGER.warn("Cannot summon CustomNPC clone {} for boss {}: {}", cloneKey,
                    npc.getName().getString(), reason);
        }
    }

    private void warnBlockedMinionPoint(int phaseIndex, int pointId, String reason) {
        String key = phaseIndex + ":" + pointId + ":" + reason;
        if (reportedBlockedMinionPoints.add(key)) {
            LOGGER.warn("Cannot place minion point {} in boss {} phase {}: {}", pointId,
                    npc.getName().getString(), phaseIndex + 1, reason);
        }
    }

    private Vec3 findMinionPosition(ServerLevel level, int radius) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = npc.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 1.0D + npc.getRandom().nextDouble() * Math.max(radius - 1.0D, 0.0D);
            double x = npc.getX() + Math.cos(angle) * distance;
            double y = npc.getY();
            double z = npc.getZ() + Math.sin(angle) * distance;
            BlockPos pos = BlockPos.containing(x, y, z);
            AABB box = new AABB(x - 0.35D, y, z - 0.35D, x + 0.35D, y + 1.8D, z + 0.35D);
            if (level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.noCollision(box)) {
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    private boolean hasAreaTargets(ServerLevel level, BossPhaseData phase) {
        return !getAreaTargets(level, phase).isEmpty();
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
    private List<LivingEntity> getTargetsAround(ServerLevel level, Vec3 centre, double radius,
                                                int ability) {
        double radiusSquared = radius * radius;
        // A whole block of slack on the box: it only pre-filters, and an entity standing
        // exactly on the rim should still be handed to the distance test below.
        AABB box = new AABB(centre, centre).inflate(radius + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, box, target ->
                target != npc && target.isAlive() && target.position().distanceToSqr(centre) <= radiusSquared
                        && isAbilityTarget(target, ability));
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
    private boolean matchesAbilityTargetKind(LivingEntity candidate, TeleportPathData data) {
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
     */
    private List<LivingEntity> abilityCandidates(ServerLevel level, double searchRange,
                                                 Predicate<LivingEntity> canHit) {
        TeleportPathData data = settings();
        AABB box = new AABB(npc.position(), npc.position()).inflate(searchRange + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, box, candidate -> candidate != npc
                && matchesAbilityTargetKind(candidate, data) && canHit.test(candidate));
    }

    private boolean isAreaTarget(LivingEntity target) {
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
    private boolean isAbilityTarget(LivingEntity target, int ability) {
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

    /** Where somebody is standing relative to a line strike: in it, beside it, or clear. */
    private enum LineBand { MISS, CORRIDOR, SIDE }

    /**
     * Everyone a line strike laid along {@code axis} currently covers, flanks included.
     *
     * <p>The box around the whole strike is only a pre-filter, exactly as the area attack's
     * is - it is what keeps the boss from sweeping the world every time it swings - and the
     * shape itself is decided per candidate. Who may be hit at all is left to
     * {@link #isAreaTarget}, so a corridor and an area slam can never end up with different
     * ideas of who counts as an enemy.</p>
     */
    private List<LivingEntity> lineTargets(ServerLevel level, Vec3 origin, Vec3 axis,
                                           BossPhaseData phase) {
        double reach = phase.getLineAttackWidth() * 0.5D + phase.getLineAttackSideWidth() + 1.0D;
        AABB box = new AABB(origin, origin.add(axis.scale(phase.getLineAttackLength())))
                .inflate(reach, phase.getLineAttackHeight() + 1.0D, reach);
        return level.getEntitiesOfClass(LivingEntity.class, box, target -> target != npc
                && target.isAlive() && isAbilityTarget(target, BossAbilityKind.LINE)
                && lineBand(origin, axis, phase, target) != LineBand.MISS);
    }

    /**
     * Which part of a line strike covers one entity.
     *
     * <p>Worked along and across the axis: how far down the line they are has to fall inside
     * its length, and how far off it decides whether the corridor itself reaches them or
     * only the weaker wave running beside it.</p>
     */
    private LineBand lineBand(Vec3 origin, Vec3 axis, BossPhaseData phase, LivingEntity target) {
        if (Math.abs(target.getY() - origin.y) > phase.getLineAttackHeight()) {
            return LineBand.MISS;
        }
        double dx = target.getX() - origin.x;
        double dz = target.getZ() - origin.z;
        double along = dx * axis.x + dz * axis.z;
        if (along < 0.0D || along > phase.getLineAttackLength()) {
            return LineBand.MISS;
        }
        // The axis is flat and unit length, so a quarter turn of it gives the across
        // measurement without a second normalize.
        double across = Math.abs(dx * axis.z - dz * axis.x);
        double half = phase.getLineAttackWidth() * 0.5D;
        if (across <= half) {
            return LineBand.CORRIDOR;
        }
        return phase.getLineAttackSideWidth() > 0 && across <= half + phase.getLineAttackSideWidth()
                ? LineBand.SIDE : LineBand.MISS;
    }

    private void performLineAttack(ServerLevel level, BossPhaseData phase) {
        Vec3 axis = lineAttackAxis;
        if (axis == null) {
            return;
        }
        if (phase.isLineAttackFaceAxis()) {
            // Whatever the eased turn had left to cover is finished on the tick the strike
            // lands, so the model points exactly down the corridor it hits.
            turnTowardAxis(axis, phase.getLineAttackLength(), 360.0F);
        }
        Vec3 origin = npc.position();
        // Purely for show, and started before the hits so the wave leaves at the same moment
        // the damage lands rather than a tick behind it.
        BossAreaVfxScheduler.scheduleLine(level, origin, axis, phase);
        int damage = rageUp(phase.getLineAttackDamage());
        int sideDamage = sideWaveDamage(damage, phase.getLineAttackSidePercent());
        int knockback = rageUp(phase.getLineAttackKnockback());
        for (LivingEntity target : lineTargets(level, origin, axis, phase)) {
            boolean side = lineBand(origin, axis, phase, target) == LineBand.SIDE;
            // Pushed down the line rather than away from the boss: this is a strike forward
            // and not a blast, so everyone it catches is thrown the same way. Vanilla shoves
            // against the vector it is handed, which is why the axis goes in negated.
            BossAbilityDamageUtil.hit(target, BossAbilityKind.LINE, npc, side ? sideDamage : damage,
                    phase.getLineAttackEffects(), knockback, -axis.x, -axis.z);
        }
    }

    /**
     * What the wave beside the corridor hits for.
     *
     * <p>Rounded up so a light strike does not lose its side wave to integer division, and
     * capped at the corridor's own damage so a hundred percent is as hard as it gets.</p>
     */
    private static int sideWaveDamage(int damage, int percent) {
        return Math.min(damage, Mth.ceil(damage * percent / 100.0D));
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
    private LivingEntity selectAbilityTarget(ServerLevel level, int mode, double searchRange,
                                             Predicate<LivingEntity> canHit) {
        LivingEntity main = npc.getTarget();
        LivingEntity fallback = main != null && canHit.test(main) ? main : null;
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
    private List<LivingEntity> selectAbilityTargets(ServerLevel level, int mode, double searchRange,
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
        if (mode == BossTargetMode.RANDOM) {
            Collections.shuffle(candidates, new java.util.Random(npc.getRandom().nextLong()));
        } else {
            boolean farthest = mode == BossTargetMode.FARTHEST;
            candidates.sort((left, right) -> {
                int order = Double.compare(npc.distanceToSqr(left), npc.distanceToSqr(right));
                return farthest ? -order : order;
            });
        }
        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            result.add(candidates.get(i));
        }
        return result;
    }

    private LivingEntity pendingTarget(ServerLevel level) {
        if (pendingTargetId < 0) return null;
        Entity entity = level.getEntity(pendingTargetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private void performRangedAttack(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = pendingTarget(level);
        if (!isValidRangedTarget(target, phase) || npc.inventory.getProjectile() == null) return;
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
        pendingAction = PendingAction.NONE;
        pendingActionAt = NOT_SCHEDULED;
        pendingWarningEndsAt = NOT_SCHEDULED;
        pendingAnimation = "";
        pendingLeadTicks = 0;
        pendingTargetId = -1;
        pendingExtraTargets.clear();
        lineAttackAxis = null;
        boulderAxis = null;
        // A leap still in the air is physics and keeps going; only a plan that has not been
        // pushed off yet dies with the windup that was just thrown away.
        if (!leapAirborne) {
            leapDestination = null;
            leapPhaseIndex = -1;
        }
    }

    private void cancelPendingAndSchedules() {
        // A cancelled wind-up frees the boss at once; only a completed one holds the pin
        // on through its after-pause.
        endCastRoot();
        clearPendingAction();
        nextTeleportAt = NOT_SCHEDULED;
        nextSummonAt = NOT_SCHEDULED;
        nextGroundAttackAt = NOT_SCHEDULED;
        nextLineAttackAt = NOT_SCHEDULED;
        nextRangedAttackAt = NOT_SCHEDULED;
        nextMeleeAttackAt = NOT_SCHEDULED;
        nextFluidSpitAt = NOT_SCHEDULED;
        nextHookAt = NOT_SCHEDULED;
        nextCaptureAt = NOT_SCHEDULED;
        nextLeapAt = NOT_SCHEDULED;
        nextGeyserAt = NOT_SCHEDULED;
        nextBoulderAt = NOT_SCHEDULED;
        nextBoulderRainAt = NOT_SCHEDULED;
    }

    private void reset() {
        hideBossBar();
        restoreNativeBossBar();
        active = false;
        highestPhaseReached = 0;
        currentPhase = -1;
        clearInvulnerability();
        minionRoundRobinCursor.clear();
        clearRage();
        clearHealthScaling(settings(), false);
        outOfCombatSince = NOT_SCHEDULED;
        encounterResetDone = false;
        clearHookPulls();
        BossGeyserScheduler.clearBoss(npc);
        BossBoulderRainScheduler.clearBoss(npc);
        clearLeap();
        BossCaptureManager.releaseByBoss(npc);
        busyUntil = 0L;
        cancelPendingAndSchedules();
        lastPathIndex = -1;
        previousPathSize = 0;
        pingPongDirection = 1;
        nextAbilityPriority = 0;
        nextRetargetAt = NOT_SCHEDULED;
        nextAggroZoneAt = NOT_SCHEDULED;
        clearEncounter();
        clearTotemRuntime();
        reportedBrokenMinionClones.clear();
        reportedBlockedMinionPoints.clear();
        reportedBrokenFluid = "";
        reportedBrokenGeyserFluid = "";
        reportedBrokenBoulderBlock = "";
        reportedBrokenBoulderRainBlock = "";
    }
}
