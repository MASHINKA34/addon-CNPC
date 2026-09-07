package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The grab: one victim lifted or pinned in a beam for as long as the hold lasts.
 *
 * <p>Owned by {@link TeleportPathController}. Only the cast lives here - who is picked and
 * what the grab costs them - because the hold itself outlives the cast by a long way and
 * belongs to {@link BossCaptureManager}.</p>
 */
final class BossCaptureRuntime {


    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossCaptureRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.capture().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.CAPTURE)
                || BossCaptureManager.hasCaptureForBoss(npc.getUUID())) return false;
        LivingEntity target = selectTarget(level, phase);
        if (target == null) {
            boss.setAbilityScheduleAt(BossAbility.CAPTURE, gameTime + RETRY_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.CAPTURE, phase.capture().getAnimation(),
                phase.capture().getActionDelayTicks(), gameTime, target, data, phase);
        // Windup and hold timing stay aligned with the animation; rage only shortens cooldown.
        boss.setAbilityScheduleAt(BossAbility.CAPTURE, gameTime + phase.capture().getActionDelayTicks()
                + boss.rageDown(phase.capture().getCooldownTicks()));
        return true;
    }

    /**
     * Capture keeps its own mode handling because MAIN falls back to a random victim here:
     * a grab animation that plays with nobody in the beam would look broken.
     */
    private LivingEntity selectTarget(ServerLevel level, BossPhaseData phase) {
        List<LivingEntity> candidates = boss.abilityCandidates(level, phase.capture().getMaxRange(),
                candidate -> isValidTarget(candidate, phase));
        if (candidates.isEmpty()) {
            return null;
        }
        LivingEntity main = npc.getTarget();
        if (phase.capture().getTargetMode() == BossTargetMode.MAIN && candidates.contains(main)) {
            return main;
        }
        if (phase.capture().getTargetMode() == BossTargetMode.RANDOM
                || phase.capture().getTargetMode() == BossTargetMode.MAIN) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }
        boolean farthest = phase.capture().getTargetMode() == BossTargetMode.FARTHEST;
        LivingEntity best = candidates.getFirst();
        double bestDistance = npc.distanceToSqr(best);
        for (int i = 1; i < candidates.size(); i++) {
            LivingEntity candidate = candidates.get(i);
            double distance = npc.distanceToSqr(candidate);
            if (farthest ? distance > bestDistance : distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        // Somebody in a cocoon is left alone too: the two holds share the victim's client
        // lock, and a capture ending would let go of a player the cocoon still has.
        if (target == null || target.level() != npc.level() || !target.isAlive()
                || target.isRemoved() || !boss.isAbilityTarget(target, BossAbilityKind.CAPTURE)
                || BossCaptureManager.isCaptured(target.getUUID())
                || BossCocoonManager.isCocooned(target.getUUID())) {
            return false;
        }
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.capture().getMinRange();
        double max = phase.capture().getMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) {
            return false;
        }
        return !npc.ais.directLOS || npc.canNpcSee(target);
    }

    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        LivingEntity victim = boss.pendingTarget(level);
        if (!isValidTarget(victim, phase)) {
            return;
        }
        if (!BossCaptureManager.start(npc, victim, phase, boss.currentPhaseIndex(), gameTime)) {
            return;
        }
        int receiver = phase.capture().getEffectTarget();
        if (receiver == BossPhaseData.CAPTURE_EFFECT_PLAYER
                || receiver == BossPhaseData.CAPTURE_EFFECT_BOTH) {
            BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.CAPTURE, npc,
                    phase.capture().getEffects());
        }
        if (receiver == BossPhaseData.CAPTURE_EFFECT_BOSS
                || receiver == BossPhaseData.CAPTURE_EFFECT_BOTH) {
            // Ungated on purpose: this half is the boss rewarding itself for a grab it pulled
            // off, not the grab landing on it.
            phase.capture().getEffects().applyAll(npc, npc);
        }
        if (victim instanceof ServerPlayer player) {
            boss.trackParticipant(player);
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
                victim.getZ(), 12, 0.25D, 0.5D, 0.25D, 0.02D);
    }
}
