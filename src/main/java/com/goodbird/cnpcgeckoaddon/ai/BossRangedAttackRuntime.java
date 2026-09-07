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

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The projectile the boss throws.
 *
 * <p>Owned by {@link TeleportPathController}. What is actually fired is whatever CustomNPCs
 * has configured on the npc: the phase only decides who it is aimed at, how hard it hits and
 * how often. The strength is put on the npc's own ranged stats for the length of one shot and
 * put back afterwards, because that is the only number the shot is built from.</p>
 */
final class BossRangedAttackRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossRangedAttackRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.rangedAttack().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.RANGED_ATTACK)) {
            return false;
        }
        LivingEntity target = boss.selectAbilityTarget(level, phase.rangedAttack().getTargetMode(),
                phase.rangedAttack().getMaxRange(), candidate -> isValidTarget(candidate, phase));
        if (target == null || !ProjectileEntityUtil.canShoot(npc)) {
            boss.setAbilityScheduleAt(BossAbility.RANGED_ATTACK, gameTime + RETRY_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.RANGED_ATTACK, phase.rangedAttack().getAnimation(),
                phase.rangedAttack().getActionDelayTicks(), gameTime, target, data, phase);
        boss.setAbilityScheduleAt(BossAbility.RANGED_ATTACK, gameTime + phase.rangedAttack().getActionDelayTicks()
                + boss.rageDown(phase.rangedAttack().getCooldownTicks()));
        return true;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = boss.pendingTarget(level);
        if (!isValidTarget(target, phase) || !ProjectileEntityUtil.canShoot(npc)) return;
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        DataRanged ranged = npc.stats.ranged;
        int previousDamage = ranged.getStrength();
        try {
            ranged.setStrength(boss.rageUp(phase.rangedAttack().getDamage()));
            double distanceSquared = npc.distanceToSqr(target);
            boolean indirect = ranged.getFireType() == 2
                    ? !npc.getSensing().hasLineOfSight(target)
                    : ranged.getFireType() == 1
                    && distanceSquared > phase.rangedAttack().getMaxRange() * phase.rangedAttack().getMaxRange() / 2.0D;
            npc.performRangedAttack(target, indirect ? 1.0F : 0.0F);
        } catch (Throwable error) {
            LOGGER.warn("Could not perform configured ranged attack for NPC {}: {}",
                    npc.getName().getString(), error.getMessage());
        } finally {
            ranged.setStrength(previousDamage);
        }
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
