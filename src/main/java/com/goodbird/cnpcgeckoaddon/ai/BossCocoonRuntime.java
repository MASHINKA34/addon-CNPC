package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The cocoon: the shell the boss closes round a victim, and the guard posted beside it.
 *
 * <p>Owned by {@link TeleportPathController}. Only the cast lives here - who is picked, the
 * clones that stand in for the shell and its guard, and the one-line-per-problem log book
 * that keeps a broken clone name from filling the console. Everything the hold does from
 * then on belongs to {@link BossCocoonManager}: a lock lasts a while and the boss is back on
 * its rotation the moment the cast lands.</p>
 */
final class BossCocoonRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /**
     * How far a cocoon is handed out: the arena, not the world. A cocoon has no reach of
     * its own - it closes wherever its victim is standing - so it borrows the mark's.
     */
    private static final double REACH = 32.0D;
    /** How far from a cocoon its guard is posted, and how many spots round it are tried. */
    private static final double GUARD_DISTANCE = 2.0D;
    private static final int GUARD_ATTEMPTS = 8;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    private final Set<String> reportedBrokenClones = new HashSet<>();
    /** Phases already told off for a cocoon with no clone name; never cleared, one line is the deal. */
    private final Set<Integer> reportedEmptyPhases = new HashSet<>();

    BossCocoonRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Forgets what has already been complained about, for a boss going back to idle. */
    void clear() {
        reportedBrokenClones.clear();
    }

    /**
     * Winds the boss up to close cocoons round whoever this cast picked.
     *
     * <p>Aimed the way the marks are, at up to a handful of victims anywhere in the arena.</p>
     */
    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.cocoon().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.COCOON)) return false;
        if (!phase.cocoon().canCocoon()) {
            // Switched on with no clone to close round anybody: said once, then left quiet.
            if (reportedEmptyPhases.add(boss.currentPhaseIndex())) {
                LOGGER.warn("Boss {} phase {} has the cocoon on but no cocoon clone name; it will not fire",
                        npc.getName().getString(), boss.currentPhaseIndex() + 1);
            }
            return false;
        }
        List<LivingEntity> targets = boss.selectAbilityTargets(level, phase.cocoon().getTargetMode(),
                REACH, this::isValidTarget, phase.cocoon().getTargetCount());
        if (targets.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.COCOON, gameTime + RETRY_TICKS);
            return false;
        }
        boss.rememberExtraTargets(targets);
        boss.beginAction(BossAbility.COCOON, phase.cocoon().getAnimation(),
                phase.cocoon().getActionDelayTicks(), gameTime, targets.get(0), data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would close the cocoons before the cast does.
        boss.setAbilityScheduleAt(BossAbility.COCOON, gameTime + phase.cocoon().getActionDelayTicks()
                + boss.rageDown(phase.cocoon().getCooldownTicks()));
        return true;
    }

    boolean isValidTarget(LivingEntity target) {
        // Somebody already held, by a cocoon or a capture, is left alone: two holds on one
        // victim would fight over their spot and their client's lock.
        if (target == null || target.level() != npc.level() || !target.isAlive()
                || target.isRemoved() || !boss.isAbilityTarget(target, BossAbilityKind.COCOON)
                || BossCocoonManager.isCocooned(target.getUUID())
                || BossCaptureManager.isCaptured(target.getUUID())) {
            return false;
        }
        return npc.distanceToSqr(target) <= REACH * REACH;
    }

    /**
     * Closes a cocoon round everyone this cast wound up on.
     *
     * <p>Each victim gets a clone of their own, spawned on the spot they are standing on;
     * one whose clone cannot be spawned is simply not held, and the reason is said once.
     * Nothing is measured here: the shells go to {@link BossCocoonManager}, which owns
     * them from now on.</p>
     */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<LivingEntity> victims = new ArrayList<>();
        LivingEntity primary = boss.pendingTarget(level);
        if (primary != null && isValidTarget(primary)) {
            victims.add(primary);
        }
        for (int id : boss.pendingExtraTargets()) {
            if (level.getEntity(id) instanceof LivingEntity extra
                    && isValidTarget(extra) && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        for (LivingEntity victim : victims) {
            Entity shell = spawnClone(level, phase.cocoon().getCloneName(), phase.cocoon().getCloneTab(),
                    victim.position(), victim.getYRot());
            if (shell == null) {
                continue;
            }
            BossCocoonUtil.markAsCocoon(shell, npc);
            // The time limit and the rescue are deliberately left alone by the enrage: they
            // are the room a party gets to answer, not a number the fight may turn down.
            if (!BossCocoonManager.start(level, npc, victim, shell, phase, boss.currentPhaseIndex(),
                    boss.rageUp(phase.cocoon().getFailDamage()), gameTime)) {
                // Refused - held by somebody else after all, or standing in a wall - so the
                // shell goes back the way it came, without a death.
                shell.discard();
                continue;
            }
            spawnGuard(level, phase, victim.position());
            if (victim instanceof ServerPlayer player) {
                boss.trackParticipant(player);
            }
        }
    }

    /**
     * The guard posted beside a cocoon, on the first free spot round it.
     *
     * <p>Optional, and never on the cocoon itself: it is there to be fought past on the way
     * to the rescue, and one standing inside the shell would take every swing meant for
     * the shell. A guard is an ordinary minion in everything but the caps: it fights, it
     * stays when the cocoon opens, and it goes when the fight does.</p>
     */
    private void spawnGuard(ServerLevel level, BossPhaseData phase, Vec3 cocoon) {
        if (phase.cocoon().getGuardName().isEmpty()) {
            return;
        }
        String cloneKey = phase.cocoon().getGuardTab() + ":" + phase.cocoon().getGuardName();
        Vec3 spot = findGuardSpot(level, cocoon);
        if (spot == null) {
            warnBrokenClone(cloneKey, "no room beside the cocoon for the guard");
            return;
        }
        // Facing the cocoon it was posted at, in Minecraft's own degrees.
        float yaw = (float) (Mth.atan2(cocoon.z - spot.z, cocoon.x - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
        Entity guard = spawnClone(level, phase.cocoon().getGuardName(), phase.cocoon().getGuardTab(), spot, yaw);
        if (guard == null) {
            return;
        }
        BossCocoonUtil.markAsGuard(guard, npc);
        if (guard instanceof Mob mob && boss.hasCombatTarget() && mob.canAttack(npc.getTarget())) {
            mob.setTarget(npc.getTarget());
        }
    }

    /** A free spot a couple of blocks off the cocoon, tried the way round from a random start. */
    private Vec3 findGuardSpot(ServerLevel level, Vec3 cocoon) {
        double start = npc.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int attempt = 0; attempt < GUARD_ATTEMPTS; attempt++) {
            double angle = start + attempt * Math.PI * 2.0D / GUARD_ATTEMPTS;
            Vec3 candidate = new Vec3(cocoon.x + Math.cos(angle) * GUARD_DISTANCE, cocoon.y,
                    cocoon.z + Math.sin(angle) * GUARD_DISTANCE);
            BlockPos pos = BlockPos.containing(candidate);
            if (level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                    && level.noCollision(BossMinionSpawnRuntime.spawnBox(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * One cocoon clone, spawned where it is told and facing the way it is told.
     *
     * <p>The minion spawn's shape without its slot bookkeeping: a cocoon belongs to a
     * victim, not to a spawn point. The caller marks it, because what it is - a shell, or
     * the guard beside one - is the caller's to say.</p>
     */
    private Entity spawnClone(ServerLevel level, String cloneName, int cloneTab, Vec3 position, float yaw) {
        if (cloneName == null || cloneName.isBlank()) {
            return null;
        }
        String cloneKey = cloneTab + ":" + cloneName;
        try {
            IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(position.x, position.y, position.z,
                    cloneTab, cloneName, NpcAPI.Instance().getIWorld(level));
            if (wrapper == null || wrapper.getMCEntity() == null) {
                warnBrokenClone(cloneKey, "clone returned no entity");
                return null;
            }
            Entity spawned = wrapper.getMCEntity();
            BossCloneRespawnGuard.suppressSelfRespawn(spawned);
            spawned.setYRot(yaw);
            if (spawned instanceof Mob mob) {
                mob.setYHeadRot(yaw);
                mob.yBodyRot = yaw;
            }
            return spawned;
        } catch (Throwable error) {
            warnBrokenClone(cloneKey, error.getMessage());
            return null;
        }
    }

    private void warnBrokenClone(String cloneKey, String reason) {
        if (reportedBrokenClones.add(cloneKey)) {
            LOGGER.warn("Cannot spawn cocoon clone {} for boss {}: {}", cloneKey,
                    npc.getName().getString(), reason);
        }
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        int held = BossCocoonManager.countForBoss(npc.getUUID());
        String holding = held > 0 ? ", holding " + BossCocoonManager.victimNamesForBoss(npc.getUUID()) : "";
        BossPhaseData phase = boss.activePhase();
        if (phase == null || !phase.cocoon().isEnabled()) {
            return "Cocoon: disabled" + holding;
        }
        if (!phase.cocoon().canCocoon()) {
            return "Cocoon: no clone name" + holding;
        }
        long remaining = boss.abilityCooldownLeft(BossAbility.COCOON, gameTime);
        return (remaining > 0L ? "Cocoon: cooldown " + remaining : "Cocoon: ready") + holding;
    }
}
