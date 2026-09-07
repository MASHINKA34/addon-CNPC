package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The sweep: beams swept round the boss for a while after the cast.
 *
 * <p>Owned by {@link TeleportPathController}, and only the cast. Nothing is aimed, exactly
 * as the gravity field is not: the length is the shape, and the cast only asks whether
 * anybody is inside it worth spending a cooldown on. Where the first beam starts is settled
 * here rather than when the sweep begins, for the reason the line strike's corridor is: the
 * lines the wind-up draws are a promise. What the beams do from then on belongs to
 * {@link BossBeamScheduler}, because a sweep lasts seconds and the boss is back on its
 * rotation the moment the cast lands.</p>
 */
final class BossBeamCastRuntime {

    /** How long a cast that found nothing, or a sweep already running, waits before looking again. */
    private static final float FULL_TURN_DEGREES = 360.0F;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossBeamCastRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.beam().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.BEAM)) return false;
        // One sweep at a time: a second set of beams on top of the first would double the
        // hits and leave nowhere to walk to.
        if (BossBeamScheduler.isSweeping(npc)) {
            boss.setAbilityScheduleAt(BossAbility.BEAM, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        if (!hasTargets(level, phase)) {
            boss.setAbilityScheduleAt(BossAbility.BEAM, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boss.commitYaw(phase.beam().getStartMode() == BossPhaseData.BEAM_START_RANDOM
                ? npc.getRandom().nextFloat() * FULL_TURN_DEGREES : npc.getYRot());
        boss.beginAction(BossAbility.BEAM, phase.beam().getAnimation(),
                phase.beam().getActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would switch the beams on before the charge does.
        boss.setAbilityScheduleAt(BossAbility.BEAM, gameTime + phase.beam().getActionDelayTicks()
                + boss.rageDown(phase.beam().getCooldownTicks()));
        return true;
    }

    boolean hasTargets(ServerLevel level, BossPhaseData phase) {
        return !boss.beamVictims(level, BossBeamScheduler.centreOf(npc), phase.beam().getLength()).isEmpty();
    }

    /**
     * Hands the sweep over, and nothing else.
     *
     * <p>Nobody is hurt here: the shape, the turn and the damage - with the enrage bonus on
     * it - are snapshotted on this tick and the scheduler drives the beams on its own
     * clock, following the boss wherever it walks in the meantime if it was told to. The
     * length, the speed and the timer are deliberately left alone by the enrage: they are
     * the room a player gets to walk, not a number the fight may turn down.</p>
     */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        BossBeamScheduler.start(level, npc, phase, boss.committedYaw(),
                boss.rageUp(phase.beam().getDamage()), boss.rageUp(phase.beam().getKnockback()), gameTime);
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        long left = BossBeamScheduler.remainingTicks(npc, gameTime);
        if (left > 0L) {
            return "Beam: sweeping " + left;
        }
        BossPhaseData phase = boss.activePhase();
        if (phase == null || !phase.beam().isEnabled()) {
            return "Beam: disabled";
        }
        long remaining = boss.abilityCooldownLeft(BossAbility.BEAM, gameTime);
        return remaining > 0L ? "Beam: cooldown " + remaining : "Beam: ready";
    }
}
