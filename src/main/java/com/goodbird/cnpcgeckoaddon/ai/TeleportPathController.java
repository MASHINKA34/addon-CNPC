package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossBarStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
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
    private static final int ABILITY_COUNT = 6;
    private static final Set<TeleportPathController> INSTANCES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private enum PendingAction { NONE, TELEPORT, SUMMON, GROUND_ATTACK, RANGED_ATTACK, MELEE_ATTACK, FLUID_SPIT, HOOK }

    private final EntityNPCInterface npc;
    private final ServerBossEvent bossEvent;
    private final Set<UUID> bossBarParticipants = new HashSet<>();
    private String activeBossBarStyle = BossBarStyles.NONE;
    private boolean active;
    private int currentPhase = -1;
    private int highestPhaseReached;
    private long outOfCombatSince = NOT_SCHEDULED;
    private boolean encounterResetDone;
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

    /** Victims beyond the first, captured when a multi-target ability starts winding up. */
    private final List<Integer> pendingExtraTargets = new ArrayList<>();
    /** Entity id -> game time at which the drag ends. */
    private final List<HookPull> activePulls = new ArrayList<>();

    /** A gather point of null means "keep pulling toward the boss wherever it is". */
    private record HookPull(int targetId, long endsAt, double strength, double stopDistance,
                            Vec3 gatherPoint) {
    }
    private PendingAction pendingAction = PendingAction.NONE;
    private long pendingActionAt = NOT_SCHEDULED;
    private int pendingTargetId = -1;
    private int lastPathIndex = -1;
    private int pingPongDirection = 1;
    private int previousPathSize;
    private int nextAbilityPriority;
    private long nextRetargetAt = NOT_SCHEDULED;
    private String reportedBrokenClone = "";
    private String reportedBrokenFluid = "";

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
            if (active && !npc.isAlive() && data.isClearMinionsOnDeath()
                    && npc.level() instanceof ServerLevel deathLevel) {
                BossMinionUtil.clear(deathLevel, npc, data.getMinionRemovalMode());
            }
            reset();
            return;
        }

        long gameTime = level.getGameTime();
        if (!active) {
            activate(gameTime, data);
        }
        if (data.isStationary()) {
            keepStationary();
        } else {
            rememberCurrentPosition();
        }
        updateNearestPlayerTarget(level, data, gameTime);
        tickHookPulls(level, gameTime);
        faceCombatTarget();
        updatePhase(level, gameTime, data);
        updateBossBar(level, data);
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

        if (points.size() >= 2 && gameTime >= nextTeleportAt) {
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

    private void activate(long gameTime, TeleportPathData data) {
        active = true;
        lockedX = npc.getX();
        lockedZ = npc.getZ();
        homeX = npc.getX();
        homeY = npc.getY();
        homeZ = npc.getZ();
        highestPhaseReached = data.resolvePhaseIndex(healthPercent());
        currentPhase = highestPhaseReached;
        outOfCombatSince = NOT_SCHEDULED;
        encounterResetDone = false;
        lastPathIndex = -1;
        previousPathSize = 0;
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
        highestPhaseReached = Math.max(highestPhaseReached, data.resolvePhaseIndex(healthPercent()));
        if (highestPhaseReached == currentPhase) {
            return;
        }
        currentPhase = highestPhaseReached;
        cancelPendingAndSchedules();
        playAnimation(data.getPhaseTransitionAnimation());
        if (!data.getPhaseTransitionAnimation().isEmpty()) {
            busyUntil = gameTime + data.getPhaseTransitionLockTicks();
        }
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
        cancelPendingAndSchedules();
        activePulls.clear();
        busyUntil = 0L;

        if (data.isClearMinionsOnReset()) {
            BossMinionUtil.clear(level, npc, data.getMinionRemovalMode());
        }
        hideBossBar();

        if (data.isResetHeal()) {
            npc.setHealth(npc.getMaxHealth());
        }
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

    private int healthPercent() {
        float maximum = npc.getMaxHealth();
        return maximum <= 0.0F ? 100 : Math.round(npc.getHealth() * 100.0F / maximum);
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

        double radius = data.getTargetSearchRadius();
        double radiusSquared = radius * radius;
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : level.players()) {
            double distance = npc.distanceToSqr(player);
            if (distance > radiusSquared || distance >= nearestDistance) {
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
            if (!data.isKeepTargetOutOfRange() && current instanceof Player) {
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
                bossEvent.addPlayer(player);
            }
        }
    }

    private boolean isBossBarViewer(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.isRemoved()
                && (player == npc.getTarget() || npc.canAttack(player) && !npc.isAlliedTo(player));
    }

    public void trackBossBarPlayer(ServerPlayer player) {
        TeleportPathData data = settings();
        if (player.level() == npc.level() && data.isEnabled()
                && BossBarStyles.isEnabled(data.getBossBarStyle()) && isBossBarViewer(player)) {
            bossBarParticipants.add(player.getUUID());
        }
    }

    public void removeBossBarPlayer(ServerPlayer player) {
        bossBarParticipants.remove(player.getUUID());
        if (!bossEvent.getPlayers().contains(player)) {
            return;
        }
        bossEvent.removePlayer(player);
        NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE));
    }

    public void shutdown() {
        stopBossBar();
        INSTANCES.remove(this);
    }

    public void stopBossBar() {
        hideBossBar();
        npc.bossInfo.setVisible(false);
    }

    public static void removePlayerFromBossBars(ServerPlayer player) {
        for (TeleportPathController controller : List.copyOf(INSTANCES)) {
            controller.removeBossBarPlayer(player);
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
            if (nextSummonAt == NOT_SCHEDULED) nextSummonAt = gameTime + phase.getSummonCooldownTicks();
        } else {
            nextSummonAt = NOT_SCHEDULED;
        }
        if (phase.isAreaAttackEnabled()) {
            if (nextGroundAttackAt == NOT_SCHEDULED) {
                nextGroundAttackAt = gameTime + phase.getAreaAttackCooldownTicks();
            }
        } else {
            nextGroundAttackAt = NOT_SCHEDULED;
        }
        if (phase.isRangedAttackEnabled()) {
            if (nextRangedAttackAt == NOT_SCHEDULED) {
                nextRangedAttackAt = gameTime + phase.getRangedAttackCooldownTicks();
            }
        } else {
            nextRangedAttackAt = NOT_SCHEDULED;
        }
        if (phase.isMeleeAttackEnabled()) {
            if (nextMeleeAttackAt == NOT_SCHEDULED) {
                nextMeleeAttackAt = gameTime + phase.getMeleeAttackCooldownTicks();
            }
        } else {
            nextMeleeAttackAt = NOT_SCHEDULED;
        }
        if (phase.canSpitFluid()) {
            if (nextFluidSpitAt == NOT_SCHEDULED) {
                nextFluidSpitAt = gameTime + phase.getFluidSpitCooldownTicks();
            }
        } else {
            nextFluidSpitAt = NOT_SCHEDULED;
        }
        if (phase.isHookEnabled()) {
            if (nextHookAt == NOT_SCHEDULED) {
                nextHookAt = gameTime + phase.getHookCooldownTicks();
            }
        } else {
            nextHookAt = NOT_SCHEDULED;
        }
    }

    private void scheduleNextTeleport(long gameTime, BossPhaseData phase) {
        int min = phase.getTeleportMinDelayTicks();
        int spread = phase.getTeleportMaxDelayTicks() - min;
        int delay = min + (spread == 0 ? 0 : npc.getRandom().nextInt(spread + 1));
        nextTeleportAt = gameTime + delay;
    }

    /** Rotates ability priority so short cooldowns cannot permanently starve another attack. */
    private boolean tryStartDueAbility(ServerLevel level, TeleportPathData data,
                                       BossPhaseData phase, long gameTime) {
        for (int offset = 0; offset < ABILITY_COUNT; offset++) {
            int ability = (nextAbilityPriority + offset) % ABILITY_COUNT;
            boolean started = switch (ability) {
                case 0 -> tryStartGroundAttack(level, data, phase, gameTime);
                case 1 -> tryStartRangedAttack(level, data, phase, gameTime);
                case 2 -> tryStartMeleeAttack(level, data, phase, gameTime);
                case 3 -> tryStartFluidSpit(level, data, phase, gameTime);
                case 4 -> tryStartHook(level, data, phase, gameTime);
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
        nextGroundAttackAt = gameTime + phase.getAreaAttackActionDelayTicks()
                + phase.getAreaAttackCooldownTicks();
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
                + phase.getRangedAttackCooldownTicks();
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
                + phase.getMeleeAttackCooldownTicks();
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
                + phase.getFluidSpitCooldownTicks();
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
                phase.getFluidSpitDamage());
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
        nextHookAt = gameTime + phase.getHookActionDelayTicks() + phase.getHookCooldownTicks();
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

        double strength = phase.getHookPullStrength() / 20.0D;
        long endsAt = gameTime + phase.getHookPullDurationTicks();
        // A cinch reels everyone onto one spot and keeps them there for the full duration,
        // so the release distance is deliberately ignored - the point is to end up with a
        // tight pile that the next area attack can catch.
        boolean cinch = phase.getHookMode() == BossPhaseData.HOOK_MODE_CINCH;
        Vec3 gatherPoint = cinch ? npc.position() : null;
        double stopDistance = cinch ? 0.0D : phase.getHookStopDistance();
        for (LivingEntity victim : victims) {
            drawHookChain(level, victim);
            if (phase.getHookDamage() > 0) {
                victim.hurt(level.damageSources().mobAttack(npc), phase.getHookDamage());
            }
            phase.getHookEffects().applyAll(victim, npc);
            // Re-hooking someone already being dragged just refreshes their pull.
            activePulls.removeIf(pull -> pull.targetId() == victim.getId());
            activePulls.add(new HookPull(victim.getId(), endsAt, strength, stopDistance, gatherPoint));
            applyPull(victim, strength, gatherPoint);
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.CHAIN_PLACE,
                SoundSource.HOSTILE, 2.0F, 0.6F);
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
            if (gameTime >= pull.endsAt() || !(level.getEntity(pull.targetId()) instanceof LivingEntity victim)
                    || !victim.isAlive() || victim.isRemoved()) {
                iterator.remove();
                continue;
            }
            Vec3 destination = pull.gatherPoint() != null ? pull.gatherPoint() : npc.position();
            double stop = pull.stopDistance();
            if (stop > 0.0D && victim.position().distanceToSqr(destination) <= stop * stop) {
                iterator.remove();
                continue;
            }
            applyPull(victim, pull.strength(), pull.gatherPoint());
            if ((gameTime & 1L) == 0L) {
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
        nextSummonAt = gameTime + phase.getSummonActionDelayTicks() + phase.getSummonCooldownTicks();
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
        if (amount <= 0 || phase.getMinionCloneName().isEmpty()) return;

        String cloneKey = phase.getMinionCloneTab() + ":" + phase.getMinionCloneName();
        for (int i = 0; i < amount; i++) {
            Vec3 position = findMinionPosition(level, phase.getMinionRadius());
            if (position == null) continue;
            try {
                IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(position.x, position.y, position.z,
                        phase.getMinionCloneTab(), phase.getMinionCloneName(), NpcAPI.Instance().getIWorld(level));
                if (wrapper == null || wrapper.getMCEntity() == null) continue;
                Entity minion = wrapper.getMCEntity();
                BossMinionUtil.markAsMinion(minion, npc);
                if (minion instanceof Mob mob && hasCombatTarget() && mob.canAttack(npc.getTarget())) {
                    mob.setTarget(npc.getTarget());
                }
                reportedBrokenClone = "";
            } catch (Throwable error) {
                if (!cloneKey.equals(reportedBrokenClone)) {
                    reportedBrokenClone = cloneKey;
                    LOGGER.warn("Cannot summon CustomNPC clone {} for boss {}: {}", cloneKey,
                            npc.getName().getString(), error.getMessage());
                }
                return;
            }
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
        return npc.canAttack(target) && !npc.isAlliedTo(target);
    }

    private void performAreaAttack(ServerLevel level, BossPhaseData phase) {
        for (LivingEntity target : getAreaTargets(level, phase)) {
            boolean damaged = target.hurt(level.damageSources().mobAttack(npc), phase.getAreaAttackDamage());
            // Applied even when the hit was absorbed by invulnerability frames or armour:
            // a plague aura that stops working because the victim was briefly immune would
            // feel broken rather than fair.
            phase.getAreaAttackEffects().applyAll(target, npc);
            if (damaged && phase.getAreaAttackKnockback() > 0) {
                target.knockback(phase.getAreaAttackKnockback(),
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
            ranged.setStrength(phase.getRangedAttackDamage());
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
        boolean damaged = target.hurt(level.damageSources().mobAttack(npc), phase.getMeleeAttackDamage());
        phase.getMeleeAttackEffects().applyAll(target, npc);
        if (damaged && phase.getMeleeAttackKnockback() > 0) {
            target.knockback(phase.getMeleeAttackKnockback(),
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
    }

    private void reset() {
        hideBossBar();
        restoreNativeBossBar();
        active = false;
        highestPhaseReached = 0;
        currentPhase = -1;
        outOfCombatSince = NOT_SCHEDULED;
        encounterResetDone = false;
        activePulls.clear();
        busyUntil = 0L;
        cancelPendingAndSchedules();
        lastPathIndex = -1;
        previousPathSize = 0;
        pingPongDirection = 1;
        nextAbilityPriority = 0;
        nextRetargetAt = NOT_SCHEDULED;
        reportedBrokenClone = "";
        reportedBrokenFluid = "";
    }
}
