package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The call for help: whether the boss may summon right now, and the wave it summons.
 *
 * <p>Owned by {@link TeleportPathController}, and only the decision. Where the clones are
 * actually put is {@link BossMinionSpawnRuntime}'s, because the points, their order and the
 * search for standing room are a configuration of their own.</p>
 *
 * <p>This is the one ability an immune boss is still allowed to start, which is why the
 * controller reaches for it by name rather than through the rotation while a phase's window
 * is open.</p>
 */
final class BossSummonRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossSummonRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.summon().canSummon() || gameTime < boss.abilityScheduleAt(BossAbility.SUMMON)) {
            return false;
        }
        if (BossMinionUtil.countAlive(level, npc, phase.summon().getMaxAlives())
                >= phase.summon().getMaxAlives()) {
            boss.setAbilityScheduleAt(BossAbility.SUMMON, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.SUMMON, phase.summon().getAnimation(),
                phase.summon().getActionDelayTicks(), gameTime, null, data, phase);
        boss.setAbilityScheduleAt(BossAbility.SUMMON, gameTime + phase.summon().getActionDelayTicks()
                + boss.rageDown(phase.summon().getCooldownTicks()));
        return true;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        boss.onSummonPerformed(level, phase);
    }
}
