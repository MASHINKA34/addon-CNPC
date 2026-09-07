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
 * The mark: a circle handed to a handful of victims that goes off where they are standing
 * when its fuse runs out.
 *
 * <p>Owned by {@link TeleportPathController}. Nothing goes off here - the marks go to
 * {@link BossMarkScheduler}, which owns them from now on, because the boss is back on its
 * rotation long before any of them burns down.</p>
 */
final class BossMarkRuntime {

    /**
     * How far a mark is handed out: the arena, not the world. A mark has no reach of its
     * own - what it does happens where its carrier takes it - so it borrows the leash's.
     */
    private static final double REACH = 32.0D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossMarkRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.mark().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.MARK)) return false;
        List<LivingEntity> targets = boss.selectAbilityTargets(level, phase.mark().getTargetMode(),
                REACH, this::isValidTarget, phase.mark().getTargetCount());
        if (targets.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.MARK, gameTime + RETRY_TICKS);
            return false;
        }
        boss.rememberExtraTargets(targets);
        boss.beginAction(BossAbility.MARK, phase.mark().getAnimation(),
                phase.mark().getActionDelayTicks(), gameTime, targets.get(0), data, phase);
        boss.setAbilityScheduleAt(BossAbility.MARK, gameTime + phase.mark().getActionDelayTicks()
                + boss.rageDown(phase.mark().getCooldownTicks()));
        return true;
    }

    /**
     * Line of sight is deliberately not required, for the reason the geyser does not need
     * it either: a mark is put on somebody rather than thrown at them.
     *
     * <p>Anyone already carrying one is passed over, this boss' marks and another boss'
     * alike. Two circles on one person is two countdowns in one action bar and two answers
     * to give at once, which is not a harder mechanic, only an unreadable one.</p>
     */
    boolean isValidTarget(LivingEntity target) {
        if (target == null || target.level() != npc.level() || !target.isAlive()
                || target.isRemoved() || !boss.isAbilityTarget(target, BossAbilityKind.MARK)
                || BossMarkScheduler.isMarked(target.getUUID())) {
            return false;
        }
        return npc.distanceToSqr(target) <= REACH * REACH;
    }

    /** Marks everyone this cast wound up on. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<LivingEntity> victims = new ArrayList<>();
        LivingEntity primary = boss.pendingTarget(level);
        if (primary != null && isValidTarget(primary)) {
            victims.add(primary);
        }
        for (int id : boss.pendingExtraTargets()) {
            if (level.getEntity(id) instanceof LivingEntity extra && isValidTarget(extra)
                    && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        if (victims.isEmpty()) {
            return;
        }
        // The fuse, the radius and the head count are deliberately left alone by the enrage:
        // they are the problem the party is set, not numbers the fight is allowed to turn.
        int damage = boss.rageUp(phase.mark().getDamage());
        int failDamage = boss.rageUp(phase.mark().getFailDamage());
        int selfDamage = boss.rageUp(phase.mark().getSelfDamage());
        for (LivingEntity victim : victims) {
            if (!BossMarkScheduler.schedule(level, npc, victim, phase, damage, failDamage,
                    selfDamage, gameTime)) {
                continue;
            }
            // The head count only counts this fight's own members, and a carrier is one of
            // them by the fact that the boss has just picked them out.
            if (victim instanceof ServerPlayer player) {
                boss.trackParticipant(player);
            }
        }
    }
}
