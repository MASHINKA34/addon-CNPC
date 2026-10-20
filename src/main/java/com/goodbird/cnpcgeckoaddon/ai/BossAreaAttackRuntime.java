package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;


/**
 * The hit that goes off all round the boss, and the wave of floor it lifts.
 *
 * <p>Owned by {@link TeleportPathController}. The radius is the whole shape - nothing is
 * aimed - so the cast only asks whether there is anybody standing inside it worth spending
 * a cooldown on. The ring goes round the boss, or round the spot a target stood on as the cast
 * began when the settings centre it on a target.</p>
 */
final class BossAreaAttackRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossAreaAttackRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.GROUND_ATTACK, phase) || gameTime < boss.abilityScheduleAt(BossAbility.GROUND_ATTACK)) {
            return false;
        }
        Vec3 centre = boss.castCentre(level, BossAbility.GROUND_ATTACK, phase.areaAttack());
        if (targets(level, phase, centre != null ? centre : npc.position()).isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.GROUND_ATTACK, gameTime + boss.retryLongTicks());
            return false;
        }
        boss.commitCentre(centre);
        boss.beginAction(BossAbility.GROUND_ATTACK, phase.areaAttack().getAnimation(),
                phase.areaAttack().getActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would land the hit before the swing does.
        boss.setAbilityScheduleAt(BossAbility.GROUND_ATTACK, boss.cooldownAt(BossAbility.GROUND_ATTACK, phase, gameTime,
                phase.areaAttack().getActionDelayTicks()));
        return true;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        Vec3 centre = boss.committedCentreOrBoss();
        // Purely for show, and started before the hits so the wave leaves at the same moment
        // the damage lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, centre, phase,
                BossWaveTuning.of(npc, phase.areaAttack().getVfx()));
        DustParticleOptions dust = BossTelegraphUtil.dust(phase, BossAbilityKind.AREA);
        for (LivingEntity target : targets(level, phase, centre)) {
            // Shoved away from the middle of the ring, wherever the ring went off.
            BossAbilityDamageUtil.hit(target, BossAbilityKind.AREA, npc,
                    boss.damageUp(BossAbilityKind.AREA, phase.areaAttack().getDamage()),
                    phase.areaAttack().getEffects(),
                    boss.rageUp(BossAbilityKind.AREA, phase.areaAttack().getKnockback()),
                    centre.x - target.getX(), centre.z - target.getZ());
            // The slam's own noise and puff at everyone it came down on, while the builder has them on.
            BossCuePlay.atEntity(level, target, phase.areaAttack().getHitSound(),
                    phase.areaAttack().getHitParticles(), dust);
        }
    }

    /** Everyone the slam being wound up would catch, round the point it committed to. */
    List<LivingEntity> targets(ServerLevel level, BossPhaseData phase) {
        return targets(level, phase, boss.committedCentreOrBoss());
    }

    private List<LivingEntity> targets(ServerLevel level, BossPhaseData phase, Vec3 centre) {
        return boss.getTargetsAround(level, centre, phase.areaAttack().getRadius(), BossAbilityKind.AREA);
    }
}
