package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossTotemEntry;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossLink;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The protection totems standing round a boss: their wave, their slots, their respawns and
 * the beams that tie them to it.
 *
 * <p>Owned by {@link TeleportPathController}. What a standing formation is worth is left to
 * the settings - it may ward, hold the boss on its spot, silence its abilities, hide it from
 * everyone's aim, or nothing but draw its beams - so everything here only answers whether one
 * is standing. Which slots are dead survives a reload on the boss' own persistent data;
 * everything else is rebuilt from the world on the first tick after one.</p>
 */
final class BossTotemRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int LINK_DURATION_TICKS = 200;
    private static final int LINK_REFRESH_TICKS = 160;

    private static final class TotemRuntime {
        private UUID entityId;
        private long nextRespawnAt = NOT_SCHEDULED;
        private long nextLinkSyncAt;

        private TotemRuntime(UUID entityId) {
            this.entityId = entityId;
        }
    }

    private final Map<Integer, TotemRuntime> slots = new HashMap<>();
    private final Set<Integer> deadSlots = new HashSet<>();
    private final Set<Integer> resetHealthSlots = new HashSet<>();
    private final Set<Integer> reportedEmptySlots = new HashSet<>();
    private final Set<Integer> reportedBlockedSlots = new HashSet<>();
    private final Set<String> reportedBrokenClones = new HashSet<>();
    private boolean waveActivated;
    private long activationDeadline = NOT_SCHEDULED;
    private long nextStructuralReconcileAt;
    /** Game time the shared totem scan below was collected on. */
    private long scanAt = NOT_SCHEDULED;
    /** Every loaded totem of this boss, collected at most once per tick and shared. */
    private List<Entity> scan = List.of();

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossTotemRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    void initialize(ServerLevel level, long gameTime, TeleportPathData data) {
        slots.clear();
        deadSlots.clear();
        deadSlots.addAll(BossTotemUtil.readDeadSlots(npc));
        resetHealthSlots.clear();
        waveActivated = false;
        activationDeadline = NOT_SCHEDULED;
        nextStructuralReconcileAt = 0L;
        reconcileStructure(level, gameTime, data);
        adoptLoaded(level, gameTime, data);
        if (data.isTotemsEnabled()
                && data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
            activateWave(gameTime, data);
        } else {
            // A server stopped during a triggered wave can save its clones. The next load
            // must restore the configured trigger instead of leaving those clones visible.
            removeConfigured(level);
        }
    }

    void tick(ServerLevel level, long gameTime, TeleportPathData data) {
        if (!data.isTotemsEnabled()) {
            if (waveActivated || !slots.isEmpty()) {
                removeConfigured(level);
                clearRuntime();
            }
            return;
        }

        if (gameTime >= nextStructuralReconcileAt) {
            nextStructuralReconcileAt = gameTime + RETRY_INTERVAL_TICKS;
            reconcileStructure(level, gameTime, data);
            adoptLoaded(level, gameTime, data);
        }

        if (!waveActivated) {
            int activation = data.getTotemActivationMode();
            if (activation == TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
                activateWave(gameTime, data);
            } else if (boss.isEncounterRunning()
                    && activation == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_START) {
                activateWave(gameTime, data);
            } else if (boss.isEncounterRunning()
                    && activation == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER
                    && boss.currentPhaseIndex() + 1 == data.getTotemActivationPhase()) {
                activateWave(gameTime, data);
            } else if (boss.isEncounterRunning()
                    && activation == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_TIMER) {
                if (activationDeadline == NOT_SCHEDULED) {
                    activationDeadline = gameTime + data.getTotemActivationDelayTicks();
                } else if (!boss.hasCombatTarget()) {
                    // Moving the deadline forward freezes the remaining duration exactly,
                    // matching the rage clock rather than buying a new full delay.
                    activationDeadline++;
                } else if (gameTime >= activationDeadline) {
                    activateWave(gameTime, data);
                }
            }
        }
        if (!waveActivated) {
            return;
        }

        for (BossTotemEntry entry : data.getTotems().entries()) {
            tickSlot(level, gameTime, data, entry);
        }
    }

    /**
     * Opens the wave the pull is supposed to open, or starts the clock the timer rule waits
     * on. Called once, on the tick somebody pulls the boss.
     */
    void beginEncounter(long gameTime, TeleportPathData data) {
        if (!data.isTotemsEnabled()) {
            return;
        }
        if (data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_START
                || data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER
                && boss.currentPhaseIndex() + 1 == data.getTotemActivationPhase()) {
            activateWave(gameTime, data);
        } else if (data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_TIMER) {
            activationDeadline = gameTime + data.getTotemActivationDelayTicks();
        }
    }

    /** Opens the wave a phase-enter rule owes, for the phase the boss has just stepped into. */
    void onPhaseEntered(long gameTime, TeleportPathData data) {
        if (boss.isEncounterRunning() && data.isTotemsEnabled() && !waveActivated
                && data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER
                && boss.currentPhaseIndex() + 1 == data.getTotemActivationPhase()) {
            activateWave(gameTime, data);
        }
    }

    /** Takes the beams off every loaded totem of this boss, leaving the totems standing. */
    void dropAllLinks(ServerLevel level) {
        for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
            dropLink(totem, BossTotemUtil.slotId(totem));
        }
    }

    /** The boss died: the beams go, and the totems too when the settings say they should. */
    void removeOnBossDeath(ServerLevel level, TeleportPathData data) {
        dropAllLinks(level);
        if (data.isTotemRemoveOnBossDeath()) {
            BossTotemUtil.removeLoaded(level, npc);
        }
        clearRuntime();
    }

    void activateWave(long gameTime, TeleportPathData data) {
        if (waveActivated) {
            return;
        }
        waveActivated = true;
        activationDeadline = NOT_SCHEDULED;
        if (data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_DELAYED) {
            for (int slotId : deadSlots) {
                TotemRuntime runtime = slots.computeIfAbsent(slotId,
                        ignored -> new TotemRuntime(null));
                runtime.nextRespawnAt = gameTime + data.getTotemRespawnDelayTicks();
            }
        }
    }

    void tickSlot(ServerLevel level, long gameTime, TeleportPathData data,
                               BossTotemEntry entry) {
        int slotId = entry.getSlotId();
        if (!entry.isEnabled() || entry.getCloneName().isEmpty()) {
            if (entry.isEnabled() && entry.getCloneName().isEmpty()
                    && reportedEmptySlots.add(slotId)) {
                LOGGER.warn("Boss {} protection-totem slot {} has no clone name",
                        npc.getName().getString(), slotId);
            }
            discardRuntime(level, slotId);
            return;
        }

        Vec3 anchor = anchorOf(entry);
        BlockPos anchorBlock = BlockPos.containing(anchor);
        // This is the duplicate-prevention boundary. A missing UUID says nothing while
        // the anchor chunk is absent, because the saved entity is absent from level lookups too.
        if (!level.hasChunkAt(anchorBlock)) {
            return;
        }

        TotemRuntime runtime = slots.get(slotId);
        Entity totem = runtime == null || runtime.entityId == null
                ? null : level.getEntity(runtime.entityId);
        if (!isUsable(totem, slotId)) {
            Entity adopted = findAlive(level, gameTime, slotId);
            if (adopted != null) {
                runtime = slots.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
                runtime.entityId = adopted.getUUID();
                runtime.nextRespawnAt = NOT_SCHEDULED;
                deadSlots.remove(slotId);
                saveDeadSlots();
                totem = adopted;
            } else if (runtime != null && runtime.entityId != null) {
                markDead(slotId, gameTime, data);
                runtime = slots.get(slotId);
                totem = null;
            }
        }

        if (totem != null) {
            pin(totem, entry, anchor);
            if (resetHealthSlots.remove(slotId) && totem instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }
            syncLink(data, entry, totem, runtime, gameTime, false);
            return;
        }

        if (deadSlots.contains(slotId)) {
            if (data.getTotemRespawnMode() != TeleportPathData.TOTEM_RESPAWN_DELAYED) {
                return;
            }
            runtime = slots.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
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

        Entity spawned = spawn(level, entry, anchor);
        runtime = slots.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
        if (spawned == null) {
            runtime.nextRespawnAt = gameTime + RETRY_INTERVAL_TICKS;
            return;
        }
        runtime.entityId = spawned.getUUID();
        runtime.nextRespawnAt = NOT_SCHEDULED;
        syncLink(data, entry, spawned, runtime, gameTime, true);
        deadSlots.remove(slotId);
        resetHealthSlots.remove(slotId);
        saveDeadSlots();
    }

    Entity spawn(ServerLevel level, BossTotemEntry entry, Vec3 anchor) {
        int slotId = entry.getSlotId();
        BlockPos pos = BlockPos.containing(anchor);
        if (!level.getWorldBorder().isWithinBounds(pos)
                || anchor.y < level.getMinBuildHeight()
                || anchor.y + 1.8D >= level.getMaxBuildHeight()) {
            warnBlocked(slotId, "outside the world border or build height");
            return null;
        }
        AABB box = new AABB(anchor.x - 0.3D, anchor.y, anchor.z - 0.3D,
                anchor.x + 0.3D, anchor.y + 1.8D, anchor.z + 0.3D);
        if (!level.noCollision(box)) {
            warnBlocked(slotId, "spawn box is occupied");
            return null;
        }

        String cloneKey = entry.getCloneTab() + ":" + entry.getCloneName();
        try {
            IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(anchor.x, anchor.y, anchor.z,
                    entry.getCloneTab(), entry.getCloneName(), NpcAPI.Instance().getIWorld(level));
            if (wrapper == null || wrapper.getMCEntity() == null) {
                if (reportedBrokenClones.add(cloneKey)) {
                    LOGGER.warn("Cannot summon protection-totem clone {} for boss {}: clone returned no entity",
                            cloneKey, npc.getName().getString());
                }
                return null;
            }
            Entity spawned = wrapper.getMCEntity();
            BossTotemUtil.markAsTotem(spawned, npc, slotId);
            BossTotemUtil.cacheVulnerability(spawned, entry);
            BossCloneRespawnGuard.suppressSelfRespawn(spawned);
            pin(spawned, entry, anchor);
            reportedBlockedSlots.remove(slotId);
            return spawned;
        } catch (Throwable error) {
            if (reportedBrokenClones.add(cloneKey)) {
                LOGGER.warn("Cannot summon protection-totem clone {} for boss {}: {}", cloneKey,
                        npc.getName().getString(), error.getMessage());
            }
            return null;
        }
    }

    void warnBlocked(int slotId, String reason) {
        if (reportedBlockedSlots.add(slotId)) {
            LOGGER.warn("Cannot place protection-totem slot {} for boss {}: {}", slotId,
                    npc.getName().getString(), reason);
        }
    }

    void pin(Entity totem, BossTotemEntry entry, Vec3 anchor) {
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

    Vec3 anchorOf(BossTotemEntry entry) {
        if (entry.getCoordinateMode() == BossTotemEntry.COORDINATE_FIXED) {
            return new Vec3(entry.getX(), entry.getY(), entry.getZ());
        }
        Vec3 home = boss.homePosition();
        return new Vec3(home.x + entry.getX(), home.y + entry.getY(), home.z + entry.getZ());
    }

    boolean isUsable(Entity entity, int slotId) {
        return entity != null && entity.isAlive() && !entity.isRemoved()
                && BossTotemUtil.isTotemOf(entity, npc) && BossTotemUtil.slotId(entity) == slotId;
    }

    void markDead(int slotId, long gameTime, TeleportPathData data) {
        deadSlots.add(slotId);
        TotemRuntime runtime = slots.computeIfAbsent(slotId, ignored -> new TotemRuntime(null));
        if (runtime.entityId != null && npc.level() instanceof ServerLevel level) {
            Entity dead = level.getEntity(runtime.entityId);
            if (dead != null) {
                dropLink(dead, slotId);
            }
        }
        runtime.entityId = null;
        runtime.nextRespawnAt = data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_DELAYED
                ? gameTime + data.getTotemRespawnDelayTicks() : NOT_SCHEDULED;
        saveDeadSlots();
    }

    void saveDeadSlots() {
        BossTotemUtil.writeDeadSlots(npc, deadSlots);
    }

    void adoptLoaded(ServerLevel level, long gameTime, TeleportPathData data) {
        Set<Integer> configured = configuredSlotIds(data, true);
        for (Entity totem : loadedTotems(level, gameTime)) {
            if (totem.isRemoved()) {
                // Discarded earlier in this same tick, by the reconcile that shares the scan.
                continue;
            }
            int slotId = BossTotemUtil.slotId(totem);
            if (!configured.contains(slotId) || !waveActivated && data.getTotemActivationMode()
                    != TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
                dropLink(totem, slotId);
                totem.discard();
                slots.remove(slotId);
                continue;
            }
            if (!totem.isAlive()) {
                continue;
            }
            TotemRuntime runtime = slots.get(slotId);
            if (runtime != null && runtime.entityId != null && !runtime.entityId.equals(totem.getUUID())) {
                // A duplicate can only be stale data from an interrupted older reconcile.
                dropLink(totem, slotId);
                totem.discard();
                continue;
            }
            slots.computeIfAbsent(slotId, ignored -> new TotemRuntime(totem.getUUID()))
                    .entityId = totem.getUUID();
        }
    }

    void reconcileStructure(ServerLevel level, long gameTime, TeleportPathData data) {
        Set<Integer> allConfigured = configuredSlotIds(data, false);
        Set<Integer> enabledConfigured = configuredSlotIds(data, true);
        boolean changed = deadSlots.retainAll(allConfigured);
        slots.keySet().removeIf(slotId -> !enabledConfigured.contains(slotId));
        resetHealthSlots.retainAll(enabledConfigured);
        for (Entity totem : loadedTotems(level, gameTime)) {
            if (totem.isRemoved()) {
                continue;
            }
            int slotId = BossTotemUtil.slotId(totem);
            if (!enabledConfigured.contains(slotId)) {
                dropLink(totem, slotId);
                totem.discard();
                continue;
            }
            // Refreshed here rather than only at spawn, so an edited vulnerability list is
            // obeyed by the totems already standing instead of only by the next wave.
            BossTotemEntry entry = entryOf(data, slotId);
            if (entry != null) {
                BossTotemUtil.cacheVulnerability(totem, entry);
            }
        }
        if (changed) {
            saveDeadSlots();
        }
    }

    BossTotemEntry entryOf(TeleportPathData data, int slotId) {
        for (BossTotemEntry entry : data.getTotems().entries()) {
            if (entry.getSlotId() == slotId) {
                return entry;
            }
        }
        return null;
    }

    Set<Integer> configuredSlotIds(TeleportPathData data, boolean enabledOnly) {
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
    List<Entity> loadedTotems(ServerLevel level, long gameTime) {
        if (scanAt != gameTime) {
            scanAt = gameTime;
            scan = BossTotemUtil.findAllLoaded(level, npc);
        }
        return scan;
    }

    /** The shared-scan form of {@link BossTotemUtil#findAlive}, with the same answer. */
    Entity findAlive(ServerLevel level, long gameTime, int slotId) {
        for (Entity entity : loadedTotems(level, gameTime)) {
            if (entity.isAlive() && !entity.isRemoved()
                    && BossTotemUtil.slotId(entity) == slotId && BossTotemUtil.isTotemOf(entity, npc)) {
                return entity;
            }
        }
        return null;
    }

    void discardRuntime(ServerLevel level, int slotId) {
        TotemRuntime runtime = slots.remove(slotId);
        if (runtime != null && runtime.entityId != null) {
            Entity entity = level.getEntity(runtime.entityId);
            if (entity != null && BossTotemUtil.isTotemOf(entity, npc)) {
                dropLink(entity, slotId);
                entity.discard();
            }
        }
    }

    void removeConfigured(ServerLevel level) {
        for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
            dropLink(totem, BossTotemUtil.slotId(totem));
            totem.discard();
        }
        slots.clear();
        resetHealthSlots.clear();
    }

    void clearRuntime() {
        slots.clear();
        resetHealthSlots.clear();
        waveActivated = false;
        activationDeadline = NOT_SCHEDULED;
        nextStructuralReconcileAt = 0L;
        // Dropped so the memo cannot keep entity references alive past the fight.
        scanAt = NOT_SCHEDULED;
        scan = List.of();
    }

    void syncLink(TeleportPathData data, BossTotemEntry entry, Entity totem,
                               TotemRuntime runtime, long gameTime, boolean force) {
        if (!force && gameTime < runtime.nextLinkSyncAt) {
            return;
        }
        runtime.nextLinkSyncAt = gameTime + LINK_REFRESH_TICKS;
        PacketSyncBossLink packet = linkPacket(data, entry, totem, LINK_DURATION_TICKS);
        // Either endpoint can enter a player's tracking range first. Duplicate delivery is
        // harmless because the client replaces the same keyed link.
        NetworkWrapper.sendToTracking(npc, packet);
        NetworkWrapper.sendToTracking(totem, packet);
    }

    PacketSyncBossLink linkPacket(TeleportPathData data, BossTotemEntry entry,
                                               Entity totem, int durationTicks) {
        String style = entry.getBeamStyleOverride().isEmpty()
                ? data.getTotemBeamStyle() : entry.getBeamStyleOverride();
        int width = entry.getBeamWidthPercentOverride() == 0
                ? data.getTotemBeamWidthPercent() : entry.getBeamWidthPercentOverride();
        return new PacketSyncBossLink(PacketSyncBossLink.KIND_PROTECTION_TOTEM,
                totem.getId(), npc.getId(), entry.getSlotId(), style, durationTicks,
                width, data.getTotemBeamSagPercent(), false);
    }

    void dropLink(Entity totem, int slotId) {
        PacketSyncBossLink packet = new PacketSyncBossLink(PacketSyncBossLink.KIND_PROTECTION_TOTEM,
                totem.getId(), npc.getId(), slotId, HookCordStyles.PARTICLES, 0, 100, 0, false);
        NetworkWrapper.sendToTracking(npc, packet);
        NetworkWrapper.sendToTracking(totem, packet);
    }

    Entity firstLoadedAlive(ServerLevel level) {
        for (TotemRuntime runtime : slots.values()) {
            Entity entity = runtime.entityId == null ? null : level.getEntity(runtime.entityId);
            if (entity != null && entity.isAlive() && BossTotemUtil.isTotemOf(entity, npc)) {
                return entity;
            }
        }
        return null;
    }

    void resetAfterEncounter(ServerLevel level, TeleportPathData data) {
        if (!data.isTotemsEnabled()) {
            removeConfigured(level);
            clearRuntime();
            return;
        }
        if (data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_NEXT_ENCOUNTER) {
            deadSlots.clear();
            saveDeadSlots();
        }
        if (data.isTotemResetHealth()) {
            for (BossTotemEntry entry : data.getTotems().entries()) {
                if (entry.isEnabled() && !entry.getCloneName().isEmpty()) {
                    resetHealthSlots.add(entry.getSlotId());
                }
            }
            for (Entity totem : BossTotemUtil.findAllLoaded(level, npc)) {
                if (totem instanceof LivingEntity living && living.isAlive()) {
                    living.setHealth(living.getMaxHealth());
                    resetHealthSlots.remove(BossTotemUtil.slotId(totem));
                }
            }
        }

        if (data.getTotemActivationMode() != TeleportPathData.TOTEM_ACTIVATION_ALWAYS) {
            removeConfigured(level);
            waveActivated = false;
            activationDeadline = NOT_SCHEDULED;
            return;
        }
        // ALWAYS remains active in idle. NEXT_ENCOUNTER slots are now immediately ready,
        // while NEVER keeps its persisted holes and DELAYED keeps its remaining deadline.
        waveActivated = true;
    }

    /** Clears administrative NEVER holes and reconciles every enabled slot on the next tick. */
    void restoreAllNow() {
        deadSlots.clear();
        saveDeadSlots();
        for (TotemRuntime runtime : slots.values()) {
            if (runtime.entityId == null) {
                runtime.nextRespawnAt = NOT_SCHEDULED;
            }
        }
        nextStructuralReconcileAt = 0L;
    }

    /**
     * True only while at least one configured wave entity is known to be alive.
     *
     * <p>What a standing formation is worth is left to the two formation flags - it may ward,
     * hold, do both, or nothing but draw its beams - so this is only the condition they
     * share. It counts adopted runtime entries rather than a world scan on purpose: a totem
     * in an unloaded chunk is still standing.</p>
     */
    boolean isWardStanding() {
        TeleportPathData data = boss.settings();
        return boss.isActive() && npc.isAlive() && data.isEnabled() && data.isTotemsEnabled()
                && waveActivated && aliveCount() > 0;
    }

    /** True while a standing formation is the reason the boss cannot be hurt. */
    boolean isProtecting() {
        return boss.settings().isTotemGrantInvulnerability() && isWardStanding();
    }

    /**
     * True while a standing formation nails the boss to the spot it is fighting on.
     *
     * <p>The flag is read before the formation, not after: this is asked twice a tick and
     * again by the pounce ai, and a boss whose totems only ward should not be counting them
     * over and over to be told the same no.</p>
     */
    boolean isHolding() {
        return boss.settings().isTotemHoldBoss() && isWardStanding();
    }

    /** True while a standing formation keeps the boss from starting anything of its own. */
    boolean isSilencing() {
        return boss.settings().isTotemSuppressAbilities() && isWardStanding();
    }

    /** True while a standing formation keeps the boss off everyone else's aiming list. */
    boolean isHiding() {
        return boss.settings().isTotemUntargetable() && isWardStanding();
    }

    int aliveCount() {
        if (!waveActivated) {
            return 0;
        }
        Set<Integer> enabled = configuredSlotIds(boss.settings(), true);
        int result = 0;
        for (Map.Entry<Integer, TotemRuntime> runtime : slots.entrySet()) {
            if (enabled.contains(runtime.getKey()) && runtime.getValue().entityId != null) {
                result++;
            }
        }
        return result;
    }

    int configuredCount() {
        return configuredSlotIds(boss.settings(), true).size();
    }

    int getTotemProtectionMode() {
        return boss.settings().getTotemProtectionMode();
    }

    /** Gives a viewer an immediate snapshot when either endpoint starts being tracked. */
    void syncLinksTo(ServerPlayer player) {
        if (!waveActivated || player.level() != npc.level()
                || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        TeleportPathData data = boss.settings();
        for (BossTotemEntry entry : data.getTotems().entries()) {
            TotemRuntime runtime = slots.get(entry.getSlotId());
            Entity totem = runtime == null || runtime.entityId == null
                    ? null : level.getEntity(runtime.entityId);
            if (entry.isEnabled() && !entry.getCloneName().isEmpty()
                    && isUsable(totem, entry.getSlotId())) {
                NetworkWrapper.send(player, linkPacket(data, entry, totem,
                        LINK_DURATION_TICKS));
            }
        }
    }
}
