package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The field: a pull, a push or a throw covering everything around the boss.
 *
 * <p>Owned by {@link TeleportPathController}, and only the cast. Nothing is aimed, exactly
 * as the boulder rain is not: the radius is the shape, and the cast only asks whether there
 * is anybody inside it worth spending a cooldown on. What the field does from then on
 * belongs to {@link BossGravityScheduler}, because a pull lasts seconds and the boss is back
 * on its rotation the moment the cast lands.</p>
 */
final class BossGravityCastRuntime {


    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossGravityCastRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.gravity().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.GRAVITY)) return false;
        if (!hasTargets(level, phase)) {
            boss.setAbilityScheduleAt(BossAbility.GRAVITY, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.GRAVITY, phase.gravity().getAnimation(),
                phase.gravity().getActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would open the field before the swing does.
        boss.setAbilityScheduleAt(BossAbility.GRAVITY, gameTime + phase.gravity().getActionDelayTicks()
                + boss.rageDown(phase.gravity().getCooldownTicks()));
        return true;
    }

    boolean hasTargets(ServerLevel level, BossPhaseData phase) {
        return !boss.gravityVictims(level, npc.position(), phase.gravity().getRadius()).isEmpty();
    }

    /**
     * Hands the field over, and nothing else.
     *
     * <p>Nobody is moved here: the radius, the force and the damage - with the enrage bonus
     * on it - are snapshotted on this tick and the scheduler drives the field on its own
     * clock, following the boss wherever it walks in the meantime. The radius, the force and
     * the timer are deliberately left alone by the enrage: they are the room a player gets to
     * run, not a number the fight may turn down.</p>
     */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        BossGravityScheduler.start(level, npc, phase, boss.rageUp(phase.gravity().getDamage()), gameTime);
    }
}
