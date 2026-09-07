package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_SHORT_TICKS;

/**
 * The swing the boss makes at whoever is in reach.
 *
 * <p>Owned by {@link TeleportPathController}. The reach is measured hitbox to hitbox rather
 * than centre to centre, so a wide boss does not lose the people standing against it.</p>
 */
final class BossMeleeAttackRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossMeleeAttackRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.meleeAttack().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.MELEE_ATTACK)) {
            return false;
        }
        // Melee reach is measured hitbox to hitbox, so the search box carries the boss own
        // half-width on top of the configured range or a wide boss loses candidates to it.
        LivingEntity target = boss.selectAbilityTarget(level, phase.meleeAttack().getTargetMode(),
                phase.meleeAttack().getRange() + npc.getBbWidth() * 0.5D,
                candidate -> isValidTarget(candidate, phase));
        if (target == null) {
            boss.setAbilityScheduleAt(BossAbility.MELEE_ATTACK, gameTime + RETRY_SHORT_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.MELEE_ATTACK, phase.meleeAttack().getAnimation(),
                phase.meleeAttack().getActionDelayTicks(), gameTime, target, data, phase);
        boss.setAbilityScheduleAt(BossAbility.MELEE_ATTACK, gameTime + phase.meleeAttack().getActionDelayTicks()
                + boss.rageDown(phase.meleeAttack().getCooldownTicks()));
        return true;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = boss.pendingTarget(level);
        if (!isValidTarget(target, phase)) return;
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // Swinging makes the model play its generic attack animation from the "Attack"
        // list. With a phase animation configured that second animation is queued behind
        // the one already running, so it only becomes visible after the hit has landed -
        // which reads as the animation playing after the damage instead of before it.
        if (phase.meleeAttack().getAnimation().isEmpty()) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
        BossAbilityDamageUtil.hit(target, BossAbilityKind.MELEE, npc,
                boss.rageUp(phase.meleeAttack().getDamage()), phase.meleeAttack().getEffects(),
                boss.rageUp(phase.meleeAttack().getKnockback()),
                npc.getX() - target.getX(), npc.getZ() - target.getZ());
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.MELEE)) return false;
        double range = phase.meleeAttack().getRange() + (npc.getBbWidth() + target.getBbWidth()) * 0.5D;
        return npc.distanceToSqr(target) <= range * range;
    }
}
