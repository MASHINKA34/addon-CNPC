package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.BossTotemEntry;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
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
    private static final int POST_ACTION_LOCK_TICKS = 10;
    private static final int ABILITY_COUNT = 7;
    /** Quietest gap that still reads as one clang per hit rather than a rattle. */
    private static final int BLOCK_FEEDBACK_INTERVAL_TICKS = 5;
    /** The client counts down on its own, so the server only has to correct it now and then. */
    private static final int TIMER_SYNC_INTERVAL_TICKS = 5;
    private static final int TOTEM_RETRY_INTERVAL_TICKS = 20;
    private static final int TOTEM_LINK_DURATION_TICKS = 200;
    private static final int TOTEM_LINK_REFRESH_TICKS = 160;
    private static final ResourceLocation RAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "boss_rage");
    private static final ResourceLocation PARTY_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "boss_party_health");
    /** Health is deliberately absent: enrage makes the boss hit harder, not last longer. */
    private static final List<Holder<Attribute>> RAGE_ATTRIBUTES =
            List.of(Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE);
    private static final Set<TeleportPathController> INSTANCES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private enum PendingAction {
        NONE, TELEPORT, SUMMON, GROUND_ATTACK, RANGED_ATTACK, MELEE_ATTACK, FLUID_SPIT, HOOK, CAPTURE
    }

    private final EntityNPCInterface npc;
    private final ServerBossEvent bossEvent;
    private final Set<UUID> bossBarParticipants = new HashSet<>();
    /** Server-side encounter membership, independent of whether any boss bar is visible. */
    private final Set<UUID> encounterParticipants = new HashSet<>();
    private String activeBossBarStyle = BossBarStyles.NONE;
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
    private final Map<Integer, TotemRuntime> totemRuntime = new HashMap<>();
    private final Set<Integer> deadTotemSlots = new HashSet<>();
    private final Set<Integer> resetTotemHealthSlots = new HashSet<>();
    private final Set<Integer> reportedEmptyTotemSlots = new HashSet<>();
    private final Set<Integer> reportedBlockedTotemSlots = new HashSet<>();
    private final Set<String> reportedBrokenTotemClones = new HashSet<>();
    private boolean totemWaveActivated;
    private long totemActivationDeadline = NOT_SCHEDULED;
    private long nextTotemStructuralReconcileAt;

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

    public void tick() {
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

        long gameTime = level.getGameTime();
        if (!active) {
            activate(level, gameTime, data);
        }
        updateAggroZone(level, data, gameTime);
        updateNearestPlayerTarget(level, data, gameTime);
        if (hasCombatTarget()) {
            beginEncounter(level, gameTime, data);
        }
        tickHealthScalingPlayerCount(level, gameTime, data);
        tickHealthScaling(data);
        // A leash reset owns the rest of this tick, including already-due abilities.
        if (tickHomeLeash(level, gameTime, data)) {
            return;
        }
        if (data.isStationary()) {
            keepStationary();
        } else {
            rememberCurrentPosition();
        }
        tickHookPulls(level, gameTime);
        faceCombatTarget();
        // Runs before updatePhase so the phase it unlocks is switched to in this same tick,
        // and above the busy/no-target early returns so a locked boss cannot stay immune.
        tickInvulnerability(level, gameTime, data);
        updatePhase(level, gameTime, data);
        tickTotems(level, gameTime, data);
        tickRage(level, gameTime, data);
        updateBossBar(level, data);
        syncBossTimer(gameTime, data);
        BossPhaseData phase = data.getPhase(currentPhase);

        if (data.isCombatOnly() && !hasCombatTarget()) {
            cancelPendingAndSchedules();
            return;
        }
        if (gameTime < busyUntil) {
            return;
        }
        if (pendingAction != PendingAction.NONE) {
            if (gameTime >= pendingActionAt) {
                executePendingAction(level, data, phase, gameTime);
                pendingAction = PendingAction.NONE;
                pendingActionAt = NOT_SCHEDULED;
                pendingTargetId = -1;
            pendingExtraTargets.clear();
                busyUntil = Math.max(busyUntil, gameTime + POST_ACTION_LOCK_TICKS);
            }
            return;
        }

        List<int[]> points = npc.ais.getMovingPath();
        preparePath(points);
        scheduleMissingAbilities(gameTime, phase, points.size() >= 2);

        if (points.size() >= 2 && gameTime >= nextTeleportAt
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
        if (!data.isHomeLeashEnabled() || !encounterRunning || !active || !npc.isAlive()) {
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
        reconcileTotemStructure(level, data);
        adoptLoadedTotems(level, data);
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
            reconcileTotemStructure(level, data);
            adoptLoadedTotems(level, data);
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
            Entity adopted = BossTotemUtil.findAlive(level, npc, slotId);
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

    private void adoptLoadedTotems(ServerLevel level, TeleportPathData data) {
        Set<Integer> configured = configuredTotemSlotIds(data, true);
        for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
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

    private void reconcileTotemStructure(ServerLevel level, TeleportPathData data) {
        Set<Integer> allConfigured = configuredTotemSlotIds(data, false);
        Set<Integer> enabledConfigured = configuredTotemSlotIds(data, true);
        boolean changed = deadTotemSlots.retainAll(allConfigured);
        totemRuntime.keySet().removeIf(slotId -> !enabledConfigured.contains(slotId));
        resetTotemHealthSlots.retainAll(enabledConfigured);
        for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
            if (!enabledConfigured.contains(BossTotemUtil.slotId(totem))) {
                dropTotemLink(totem, BossTotemUtil.slotId(totem));
                totem.discard();
            }
        }
        if (changed) {
            saveDeadTotemSlots();
        }
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
        boolean minionsDone = invulnerableSummonedOnce && BossMinionUtil.countAlive(level, npc) == 0;
        if (!phase.invulnerableWaitsForTimer()) {
            return minionsDone;
        }
        return phase.getInvulnerableEndMode() == BossPhaseData.INVULNERABLE_END_TIMER_AND_MINIONS
                ? timerDone && minionsDone
                : timerDone || minionsDone;
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
                    && isTargetablePlayer(player, data)) {
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
        return !(target instanceof Player player) || isTargetablePlayer(player, data);
    }

    private void setTargetIfChanged(LivingEntity target) {
        if (npc.getTarget() != target) {
            npc.setTarget(target);
        }
    }

    /**
     * Locks the boss onto the closest reachable player. Without this a boss keeps chasing
     * whoever aggroed it first, which lets a group trivially kite it with one player.
     */
    private void updateNearestPlayerTarget(ServerLevel level, TeleportPathData data, long gameTime) {
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
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Iterable<? extends Player> players = !restrictToZone
                ? level.players() : zoneConstraint == null
                ? List.of() : eligibleAggroZonePlayers(level, data, zoneConstraint);
        for (Player player : players) {
            double distance = npc.distanceToSqr(player);
            if ((!restrictToZone && distance > radiusSquared) || distance >= nearestDistance) {
                continue;
            }
            if (!isTargetablePlayer(player, data)) {
                continue;
            }
            nearest = player;
            nearestDistance = distance;
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

    private boolean isTargetablePlayer(Player player, TeleportPathData data) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.isRemoved()) {
            return false;
        }
        if (!npc.canAttack(player) || npc.isAlliedTo(player)) {
            return false;
        }
        return !data.isTargetRequiresLineOfSight() || npc.getSensing().hasLineOfSight(player);
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

        if (!style.equals(activeBossBarStyle)) {
            activeBossBarStyle = style;
            for (ServerPlayer player : bossEvent.getPlayers()) {
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), style));
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
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE));
            }
        }
        for (ServerPlayer player : eligible) {
            if (!bossEvent.getPlayers().contains(player)) {
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), style));
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
            instance.removeModifier(PARTY_HEALTH_MODIFIER_ID);
            baseMaxHealth = finiteHealth(npc.getMaxHealth(), 1.0D);
            healthScalingApplied = true;
        } else {
            instance.removeModifier(PARTY_HEALTH_MODIFIER_ID);
        }

        double bonus = healthScalingBonus(instance, data);
        if (bonus > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(PARTY_HEALTH_MODIFIER_ID, bonus,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        lastHealthScalingConfiguration = configuration;
        adjustCurrentHealth(oldHealth, oldMax, npc.getMaxHealth(), data.getHealthScalingAdjustment());
    }

    private double healthScalingBonus(AttributeInstance instance, TeleportPathData data) {
        int extraPlayers = Math.max(0, scaledPlayerCount - 1);
        double percentBonus = baseMaxHealth * extraPlayers
                * data.getHealthPerPlayerPercent() / 100.0D;
        double flatBonus = (double) extraPlayers * data.getHealthPerPlayerFlat();
        double rawBonus = switch (data.getHealthScalingMode()) {
            case TeleportPathData.HEALTH_SCALING_FLAT -> flatBonus;
            case TeleportPathData.HEALTH_SCALING_PERCENT_AND_FLAT -> percentBonus + flatBonus;
            default -> percentBonus;
        };
        double desiredMax = finiteHealth(baseMaxHealth + rawBonus, Double.MAX_VALUE);
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
        boolean hadModifier = instance.hasModifier(PARTY_HEALTH_MODIFIER_ID);
        if (!healthScalingApplied && !hadModifier) {
            if (resetHeal && npc.isAlive()) {
                npc.setHealth(npc.getMaxHealth());
            }
            return;
        }

        float oldMax = npc.getMaxHealth();
        float oldHealth = npc.getHealth();
        instance.removeModifier(PARTY_HEALTH_MODIFIER_ID);
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
        NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE));
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

    /** True only while at least one configured wave entity is known to be alive. */
    public boolean isTotemProtected() {
        TeleportPathData data = settings();
        return active && npc.isAlive() && data.isEnabled() && data.isTotemsEnabled()
                && totemWaveActivated && aliveTotemCount() > 0;
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
        String victim = BossCaptureManager.capturedPlayerName(npc.getUUID());
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
            NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE));
        }
        activeBossBarStyle = BossBarStyles.NONE;
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

    /** The removed vanilla damage must not also remove the boss' visual tracking of its target. */
    private void faceCombatTarget() {
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

    private boolean tryStartRangedAttack(ServerLevel level, TeleportPathData data,
                                         BossPhaseData phase, long gameTime) {
        if (!phase.isRangedAttackEnabled() || gameTime < nextRangedAttackAt) return false;
        LivingEntity target = selectAbilityTarget(level, phase.getRangedAttackTargetMode(),
                candidate -> isValidRangedTarget(candidate, phase));
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
        LivingEntity target = selectAbilityTarget(level, phase.getMeleeAttackTargetMode(),
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
                candidate -> isValidFluidSpitTarget(candidate, phase));
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
        if (target == null || !target.isAlive() || !isAreaTarget(target)) return false;
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
                candidate -> isValidHookTarget(candidate, phase), phase.getHookTargetCount());
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
        if (target == null || !target.isAlive() || !isAreaTarget(target)) return false;
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
        ServerPlayer target = selectCaptureTarget(level, phase);
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

    private ServerPlayer selectCaptureTarget(ServerLevel level, BossPhaseData phase) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (isValidCaptureTarget(player, phase)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (phase.getCaptureTargetMode() == BossTargetMode.MAIN
                && npc.getTarget() instanceof ServerPlayer main && candidates.contains(main)) {
            return main;
        }
        if (phase.getCaptureTargetMode() == BossTargetMode.RANDOM
                || phase.getCaptureTargetMode() == BossTargetMode.MAIN) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }
        boolean farthest = phase.getCaptureTargetMode() == BossTargetMode.FARTHEST;
        ServerPlayer best = candidates.getFirst();
        double bestDistance = npc.distanceToSqr(best);
        for (int i = 1; i < candidates.size(); i++) {
            ServerPlayer candidate = candidates.get(i);
            double distance = npc.distanceToSqr(candidate);
            if (farthest ? distance > bestDistance : distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isValidCaptureTarget(ServerPlayer player, BossPhaseData phase) {
        if (player == null || player.level() != npc.level() || !player.isAlive()
                || player.isRemoved() || player.isCreative() || player.isSpectator()
                || !npc.canAttack(player) || npc.isAlliedTo(player)
                || BossCaptureManager.isCaptured(player.getUUID())) {
            return false;
        }
        double distanceSquared = npc.distanceToSqr(player);
        double min = phase.getCaptureMinRange();
        double max = phase.getCaptureMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) {
            return false;
        }
        return !npc.ais.directLOS || npc.canNpcSee(player);
    }

    private void performCapture(ServerLevel level, BossPhaseData phase, long gameTime) {
        LivingEntity pending = pendingTarget(level);
        if (!(pending instanceof ServerPlayer player) || !isValidCaptureTarget(player, phase)) {
            return;
        }
        if (!BossCaptureManager.start(npc, player, phase, gameTime)) {
            return;
        }
        int receiver = phase.getCaptureEffectTarget();
        if (receiver == BossPhaseData.CAPTURE_EFFECT_PLAYER
                || receiver == BossPhaseData.CAPTURE_EFFECT_BOTH) {
            phase.getCaptureEffects().applyAll(player, npc);
        }
        if (receiver == BossPhaseData.CAPTURE_EFFECT_BOSS
                || receiver == BossPhaseData.CAPTURE_EFFECT_BOTH) {
            phase.getCaptureEffects().applyAll(npc, npc);
        }
        trackParticipant(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + player.getBbHeight() * 0.5D,
                player.getZ(), 12, 0.25D, 0.5D, 0.25D, 0.02D);
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
            if (phase.getHookDamage() > 0) {
                victim.hurt(level.damageSources().mobAttack(npc), rageUp(phase.getHookDamage()));
            }
            phase.getHookEffects().applyAll(victim, npc);
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

    private boolean tryStartSummon(ServerLevel level, TeleportPathData data,
                                   BossPhaseData phase, long gameTime) {
        if (!phase.canSummon() || gameTime < nextSummonAt) return false;
        if (BossMinionUtil.countAlive(level, npc) >= phase.getMaxAliveMinions()) {
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
        playAnimation(animation);
        pendingAction = action;
        pendingTargetId = target == null ? -1 : target.getId();
        if (actionDelay <= 0) {
            if (npc.level() instanceof ServerLevel level) {
                executePendingAction(level, data, phase, gameTime);
            }
            pendingAction = PendingAction.NONE;
            pendingActionAt = NOT_SCHEDULED;
            pendingTargetId = -1;
            pendingExtraTargets.clear();
            busyUntil = Math.max(busyUntil, gameTime + POST_ACTION_LOCK_TICKS);
            return;
        }
        pendingActionAt = gameTime + actionDelay;
    }

    private void executePendingAction(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (pendingAction == PendingAction.SUMMON) {
            summonMinions(level, phase);
            invulnerableSummonedOnce = true;
        } else if (pendingAction == PendingAction.GROUND_ATTACK) {
            performAreaAttack(level, phase);
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
            } catch (Throwable ignored) {
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
        int available = phase.getMaxAliveMinions() - BossMinionUtil.countAlive(level, npc);
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
        double radius = phase.getAreaAttackRadius();
        double radiusSquared = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(radius), target ->
                target != npc && target.isAlive() && npc.distanceToSqr(target) <= radiusSquared
                        && isAreaTarget(target));
    }

    private boolean isAreaTarget(LivingEntity target) {
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (BossMinionUtil.isMinionOf(target, npc)) return false;
        if (BossTotemUtil.isTotemOf(target, npc)) return false;
        return npc.canAttack(target) && !npc.isAlliedTo(target);
    }

    private void performAreaAttack(ServerLevel level, BossPhaseData phase) {
        for (LivingEntity target : getAreaTargets(level, phase)) {
            boolean damaged = target.hurt(level.damageSources().mobAttack(npc),
                    rageUp(phase.getAreaAttackDamage()));
            // Applied even when the hit was absorbed by invulnerability frames or armour:
            // a plague aura that stops working because the victim was briefly immune would
            // feel broken rather than fair.
            phase.getAreaAttackEffects().applyAll(target, npc);
            if (damaged && phase.getAreaAttackKnockback() > 0) {
                target.knockback(rageUp(phase.getAreaAttackKnockback()),
                        npc.getX() - target.getX(), npc.getZ() - target.getZ());
            }
        }
    }

    private boolean isValidRangedTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAreaTarget(target)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getRangedAttackMinRange();
        double max = phase.getRangedAttackMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) return false;
        return !npc.ais.directLOS || npc.canNpcSee(target) || npc.stats.ranged.getFireType() == 2;
    }

    private boolean isValidMeleeTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !isAreaTarget(target)) return false;
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
     * <p>Players are the only candidates for the distance-based modes - a boss fight is
     * about the party, and picking the farthest "target" would otherwise happily settle on
     * a cow. When no player qualifies the NPC falls back to its normal combat target, so
     * turning a mode on never makes an ability quieter than MAIN would have been.</p>
     */
    private LivingEntity selectAbilityTarget(ServerLevel level, int mode,
                                             Predicate<LivingEntity> canHit) {
        LivingEntity main = npc.getTarget();
        LivingEntity fallback = main != null && canHit.test(main) ? main : null;
        if (mode == BossTargetMode.MAIN) {
            return fallback;
        }

        List<Player> candidates = new ArrayList<>();
        for (Player player : level.players()) {
            if (canHit.test(player)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return fallback;
        }
        if (mode == BossTargetMode.RANDOM) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }

        boolean farthest = mode == BossTargetMode.FARTHEST;
        Player best = null;
        double bestDistance = farthest ? -1.0D : Double.MAX_VALUE;
        for (Player player : candidates) {
            double distance = npc.distanceToSqr(player);
            if (farthest ? distance > bestDistance : distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The multi-victim form of {@link #selectAbilityTarget}. Candidates are ordered by the
     * same rule, so FARTHEST with a count of three grabs the three players furthest away.
     */
    private List<LivingEntity> selectAbilityTargets(ServerLevel level, int mode,
                                                    Predicate<LivingEntity> canHit, int count) {
        List<LivingEntity> result = new ArrayList<>();
        if (count <= 1) {
            LivingEntity single = selectAbilityTarget(level, mode, canHit);
            if (single != null) {
                result.add(single);
            }
            return result;
        }

        List<Player> candidates = new ArrayList<>();
        for (Player player : level.players()) {
            if (canHit.test(player)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            LivingEntity fallback = selectAbilityTarget(level, mode, canHit);
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
        boolean damaged = target.hurt(level.damageSources().mobAttack(npc),
                rageUp(phase.getMeleeAttackDamage()));
        phase.getMeleeAttackEffects().applyAll(target, npc);
        if (damaged && phase.getMeleeAttackKnockback() > 0) {
            target.knockback(rageUp(phase.getMeleeAttackKnockback()),
                    npc.getX() - target.getX(), npc.getZ() - target.getZ());
        }
    }

    private void cancelPendingAndSchedules() {
        pendingAction = PendingAction.NONE;
        pendingActionAt = NOT_SCHEDULED;
        pendingTargetId = -1;
        pendingExtraTargets.clear();
        nextTeleportAt = NOT_SCHEDULED;
        nextSummonAt = NOT_SCHEDULED;
        nextGroundAttackAt = NOT_SCHEDULED;
        nextRangedAttackAt = NOT_SCHEDULED;
        nextMeleeAttackAt = NOT_SCHEDULED;
        nextFluidSpitAt = NOT_SCHEDULED;
        nextHookAt = NOT_SCHEDULED;
        nextCaptureAt = NOT_SCHEDULED;
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
    }
}
