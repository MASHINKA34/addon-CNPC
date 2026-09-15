package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossHurricaneSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.function.Predicate;

/**
 * The hurricane: storms let loose on the arena floor at the end of the wind-up.
 *
 * <p>Owned by {@link TeleportPathController}. Nothing travels here - the storms go to
 * {@link BossHurricaneScheduler} the moment the cast lands, because the boss is back on its
 * rotation long before the first of them dies, which is the whole point of the ability.</p>
 */
final class BossHurricaneRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossHurricaneRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.HURRICANE, phase)
                || gameTime < boss.abilityScheduleAt(BossAbility.HURRICANE)) {
            return false;
        }
        BossHurricaneSettings hurricane = phase.hurricane();
        LivingEntity target = null;
        Vec3 axis = boss.facingAxis();
        // Only the one straight storm aimed at somebody needs somebody: a cross, a spiral and a
        // typhoon cover ground rather than a victim, and a storm sent along the gaze does too.
        if (hurricane.isAimedAtTarget()) {
            target = pickTarget(level, phase);
            if (target == null) {
                boss.setAbilityScheduleAt(BossAbility.HURRICANE, gameTime + boss.retryTicks());
                return false;
            }
            axis = axisToward(target);
        }
        // Committed now and read back by the corridor, the turn and the launch alike, so the
        // storms leave exactly where the warning said they would.
        boss.commitAxis(axis);
        boss.beginAction(BossAbility.HURRICANE, hurricane.getAnimation(), hurricane.getActionDelayTicks(),
                gameTime, target, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.HURRICANE, gameTime + hurricane.getActionDelayTicks()
                + boss.rageDown(hurricane.getCooldownTicks()));
        return true;
    }

    /**
     * Whoever the straight storm is aimed at: the phase's pick, and when that is the npc's own
     * target and the storm may not take them, the nearest it may - the cone strike's rule, so a
     * main target of a kind the boss aims nothing at cannot refuse the cast for ever.
     */
    private LivingEntity pickTarget(ServerLevel level, BossPhaseData phase) {
        BossHurricaneSettings hurricane = phase.hurricane();
        Predicate<LivingEntity> canHit = candidate -> isValidTarget(candidate, phase);
        LivingEntity picked = boss.selectAbilityTarget(level, hurricane.getTargetMode(), hurricane.getRange(), canHit);
        if (picked == null && BossConeRuntime.fallsBackToNearest(hurricane.getTargetMode())) {
            picked = boss.selectAbilityTarget(level, BossTargetMode.NEAREST, hurricane.getRange(), canHit);
        }
        return picked;
    }

    /** Whether one candidate is worth sending a storm at: alive, takeable, and inside the storm's path, flat. */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.HURRICANE)) {
            return false;
        }
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double range = phase.hurricane().getRange();
        return dx * dx + dz * dz <= range * range;
    }

    /** The flat line from the boss to a victim, or the gaze when they stand inside the boss. */
    private Vec3 axisToward(LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - npc.getX(), 0.0D, target.getZ() - npc.getZ());
        return flat.lengthSqr() < 1.0E-6D ? boss.facingAxis() : flat.normalize();
    }

    /** Lets the storms go, along whatever the wind-up committed to. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        Vec3 axis = boss.committedAxis();
        if (axis == null) {
            axis = boss.facingAxis();
        }
        // The enrage turns up what the storm hits for, not how long it holds: the hold is the
        // window the rest of the party gets to do something about it.
        int damage = boss.damageUp(phase.hurricane().getDamage());
        BossHurricaneScheduler.launch(level, npc, phase, axis, damage, gameTime);
    }
}
