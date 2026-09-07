package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The hit that goes off all round the boss, and the wave of floor it lifts.
 *
 * <p>Owned by {@link TeleportPathController}. The radius is the whole shape - nothing is
 * aimed - so the cast only asks whether there is anybody standing inside it worth spending
 * a cooldown on.</p>
 */
final class BossAreaAttackRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossAreaAttackRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.areaAttack().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.GROUND_ATTACK)) {
            return false;
        }
        if (targets(level, phase).isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.GROUND_ATTACK, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.GROUND_ATTACK, phase.areaAttack().getAnimation(),
                phase.areaAttack().getActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would land the hit before the swing does.
        boss.setAbilityScheduleAt(BossAbility.GROUND_ATTACK, gameTime + phase.areaAttack().getActionDelayTicks()
                + boss.rageDown(phase.areaAttack().getCooldownTicks()));
        return true;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        // Purely for show, and started before the hits so the wave leaves at the same moment
        // the damage lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, npc.position(), phase);
        for (LivingEntity target : targets(level, phase)) {
            BossAbilityDamageUtil.hit(target, BossAbilityKind.AREA, npc,
                    boss.rageUp(phase.areaAttack().getDamage()), phase.areaAttack().getEffects(),
                    boss.rageUp(phase.areaAttack().getKnockback()),
                    npc.getX() - target.getX(), npc.getZ() - target.getZ());
        }
    }

    List<LivingEntity> targets(ServerLevel level, BossPhaseData phase) {
        return boss.getTargetsAround(level, npc.position(), phase.areaAttack().getRadius(),
                BossAbilityKind.AREA);
    }
}
