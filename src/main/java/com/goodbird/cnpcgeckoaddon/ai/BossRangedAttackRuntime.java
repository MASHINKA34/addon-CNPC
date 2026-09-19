package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataRanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The projectile the boss throws.
 *
 * <p>Owned by {@link TeleportPathController}. What is actually fired is whatever CustomNPCs
 * has configured on the npc: the phase only decides who it is aimed at, how hard it hits and
 * how often. The strength is put on the npc's own ranged stats for the length of one shot and
 * put back afterwards, because that is the only number the shot is built from.</p>
 *
 * <p>A cast may be a burst rather than a single shot, the cone's series over again: the first
 * shot goes off at the end of the wind-up and the rest follow one pause apart, holding the boss
 * busy until the last of them leaves and counting the cooldown from there. Nothing of that is
 * saved: a server that goes down mid burst leaves the rest of it unfired.</p>
 */
final class BossRangedAttackRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    /** The shots this cast still owes, or nothing between casts. */
    private final BossRangedBurst burst = new BossRangedBurst();
    /** Phase the cast started in: what its shots hit for belongs to the settings that launched it. */
    private int phaseIndex = -1;
    /**
     * Whoever the burst is aimed at, by id: the controller lets go of its own pending target
     * the moment the cast is performed, and the shots still owed have to find them again.
     */
    private int targetId = -1;

    BossRangedAttackRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.RANGED_ATTACK, phase) || gameTime < boss.abilityScheduleAt(BossAbility.RANGED_ATTACK)
                || isSequencing()) {
            return false;
        }
        LivingEntity target = boss.selectAbilityTarget(level, phase.rangedAttack().getTargetMode(),
                phase.rangedAttack().getMaxRange(), candidate -> isValidTarget(candidate, phase));
        if (target == null || !ProjectileEntityUtil.canShoot(npc)) {
            boss.setAbilityScheduleAt(BossAbility.RANGED_ATTACK, gameTime + boss.retryTicks());
            return false;
        }
        boss.beginAction(BossAbility.RANGED_ATTACK, phase.rangedAttack().getAnimation(),
                phase.rangedAttack().getActionDelayTicks(), gameTime, target, data, phase);
        boss.setAbilityScheduleAt(BossAbility.RANGED_ATTACK, gameTime + phase.rangedAttack().getActionDelayTicks()
                + boss.rageDown(phase.rangedAttack().getCooldownTicks()));
        return true;
    }

    /** Whether a burst is still running, which the busy gate and the finish gate wait for. */
    boolean isSequencing() {
        return burst.isRunning();
    }

    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        // Noted before anything is fired, the cone's reason: a hit can set off a script that
        // staggers or resets the boss, and whatever ends the cast has to find the cast to end.
        phaseIndex = boss.currentPhaseIndex();
        LivingEntity target = boss.pendingTarget(level);
        if (!shoot(target, phase)) {
            clear();
            return;
        }
        if (phase.rangedAttack().getBurstShots() <= 1) {
            clear();
            return;
        }
        targetId = target.getId();
        burst.start(phase.rangedAttack().getBurstShots(), phase.rangedAttack().getBurstDelayTicks(), gameTime);
        // Nothing else starts from this tick on until the last shot has left.
        boss.holdBusyUntil(gameTime + 1);
    }

    /**
     * Carries a burst one tick further: the next shot once its pause is over, and the cooldown
     * and the usual pause after a cast once the last one has left.
     *
     * <p>Runs every tick above the controller's gates, the cone's way: the burst holds the busy
     * gate shut itself, so it has to be ticked before that gate turns everything else away.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!burst.isRunning()) {
            return;
        }
        boss.holdBusyUntil(gameTime + 1);
        BossPhaseData phase = phaseOf(data);
        if (phase == null) {
            // Its phase was deleted from under it, and what the rest would hit for with it.
            finish(null, gameTime);
            return;
        }
        if (!burst.due(gameTime, phase.rangedAttack().getBurstDelayTicks())) {
            return;
        }
        if (!shoot(level.getEntity(targetId) instanceof LivingEntity target ? target : null, phase)) {
            // Whoever it was aimed at is gone, or there is nothing left to fire: the rest of the
            // burst is dropped rather than sprayed at nobody, and the cast ends here.
            finish(phase, gameTime);
            return;
        }
        // Asked again rather than trusted: a hit can set off a script that kills or resets the
        // boss, and that clears the burst under it.
        if (!burst.isRunning()) {
            finish(phase, gameTime);
        }
    }

    /**
     * The end every burst but a cut-short one comes to: the cooldown counted from the last shot,
     * and the usual pause after a cast.
     */
    private void finish(BossPhaseData phase, long gameTime) {
        clear();
        if (phase != null) {
            // Never shorter than what the start already stamped: the wind-up's own stamp is what
            // keeps a cast from being started twice while its burst is still going.
            boss.setAbilityScheduleAt(BossAbility.RANGED_ATTACK,
                    Math.max(boss.abilityScheduleAt(BossAbility.RANGED_ATTACK),
                            gameTime + boss.rageDown(phase.rangedAttack().getCooldownTicks())));
        }
        boss.holdBusyUntil(gameTime + boss.postActionLockTicks());
    }

    /**
     * Ends a burst cut short - a stagger - and counts the cooldown from here: a cast cut short is
     * still a cast, and the stun's end must not find the shot ready again.
     */
    void interrupt(TeleportPathData data, long gameTime) {
        if (burst.isRunning()) {
            finish(phaseOf(data), gameTime);
        }
    }

    /** Drops whatever is left of a burst: a phase change, a stagger, a reset, a death. */
    void clear() {
        burst.clear();
        phaseIndex = -1;
        targetId = -1;
    }

    /** The phase a burst belongs to, so a phase that disappears mid burst cannot rewrite its hits. */
    private BossPhaseData phaseOf(TeleportPathData data) {
        return phaseIndex >= 0 && phaseIndex < data.getPhaseCount() ? data.getPhase(phaseIndex) : null;
    }

    /**
     * One shot of a cast.
     *
     * @return false when there was nobody left to shoot at or nothing to shoot with, which ends
     *         the cast wherever it had got to
     */
    private boolean shoot(LivingEntity target, BossPhaseData phase) {
        if (!isValidTarget(target, phase) || !ProjectileEntityUtil.canShoot(npc)) {
            return false;
        }
        float aim = phase.rangedAttack().getAimTurnDegrees();
        npc.getLookControl().setLookAt(target, aim, aim);
        DataRanged ranged = npc.stats.ranged;
        int previousDamage = ranged.getStrength();
        try {
            ranged.setStrength(boss.damageUp(phase.rangedAttack().getDamage()));
            double distanceSquared = npc.distanceToSqr(target);
            boolean indirect = ranged.getFireType() == 2
                    ? !npc.getSensing().hasLineOfSight(target)
                    : ranged.getFireType() == 1
                    && distanceSquared > phase.rangedAttack().lobBeyondSquared();
            npc.performRangedAttack(target, indirect ? 1.0F : 0.0F);
        } catch (Throwable error) {
            LOGGER.warn("Could not perform configured ranged attack for NPC {}: {}",
                    npc.getName().getString(), error.getMessage());
        } finally {
            ranged.setStrength(previousDamage);
        }
        return true;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.RANGED)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.rangedAttack().getMinRange();
        double max = phase.rangedAttack().getMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) return false;
        return !npc.ais.directLOS || npc.canNpcSee(target) || npc.stats.ranged.getFireType() == 2;
    }
}
