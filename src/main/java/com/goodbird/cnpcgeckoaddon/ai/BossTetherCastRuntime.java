package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The leash: victims tied to the boss, to a spot or to each other for a while.
 *
 * <p>Owned by {@link TeleportPathController}, and only the cast. Nothing is measured here -
 * the leashes go to {@link BossTetherManager} the moment the cast lands, because the boss is
 * back on its rotation long before anyone has run far enough, or failed to.</p>
 */
final class BossTetherCastRuntime {

    /**
     * How far a leash tied to a spot or to a partner looks for its victims: the arena, not
     * the world. One tied to the boss reaches exactly as far as it breaks, so nobody is
     * leashed already standing outside the ring.
     */
    private static final double REACH = 32.0D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossTetherCastRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.tether().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.TETHER)) return false;
        List<LivingEntity> targets = boss.selectAbilityTargets(level, phase.tether().getTargetMode(),
                reach(phase), candidate -> isValidTarget(candidate, phase),
                phase.tether().getTargetCount());
        if (targets.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.TETHER, gameTime + RETRY_TICKS);
            return false;
        }
        boss.rememberExtraTargets(targets);
        boss.beginAction(BossAbility.TETHER, phase.tether().getAnimation(),
                phase.tether().getActionDelayTicks(), gameTime, targets.get(0), data, phase);
        boss.setAbilityScheduleAt(BossAbility.TETHER, gameTime + phase.tether().getActionDelayTicks()
                + boss.rageDown(phase.tether().getCooldownTicks()));
        return true;
    }

    /** How far this cast picks its victims from; see {@link #REACH}. */
    private static double reach(BossPhaseData phase) {
        return phase.tether().getAnchor() == BossPhaseData.TETHER_ANCHOR_BOSS
                ? phase.tether().getBreakDistance() : REACH;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || target.level() != npc.level() || !target.isAlive()
                || target.isRemoved() || !boss.isAbilityTarget(target, BossAbilityKind.TETHER)
                || BossTetherManager.isTethered(target.getUUID())) {
            return false;
        }
        double reach = reach(phase);
        if (npc.distanceToSqr(target) > reach * reach) {
            return false;
        }
        // A leash that reaches through a wall looks broken, so honour the NPC line-of-sight flag.
        return !npc.ais.directLOS || npc.canNpcSee(target);
    }

    /** Leashes everyone this cast wound up on. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<LivingEntity> victims = new ArrayList<>();
        LivingEntity primary = boss.pendingTarget(level);
        if (primary != null && isValidTarget(primary, phase)) {
            victims.add(primary);
        }
        for (int id : boss.pendingExtraTargets()) {
            if (level.getEntity(id) instanceof LivingEntity extra
                    && isValidTarget(extra, phase) && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        if (victims.isEmpty()) {
            return;
        }
        // The break distance and the timer are deliberately left alone by the enrage: they
        // are the window a player gets to run, not a number the fight is allowed to turn down.
        if (BossTetherManager.start(level, npc, victims, phase, boss.currentPhaseIndex(),
                boss.rageUp(phase.tether().getFailDamage()), gameTime) == 0) {
            return;
        }
        for (LivingEntity victim : victims) {
            if (victim instanceof ServerPlayer player) {
                boss.trackParticipant(player);
            }
        }
    }
}
