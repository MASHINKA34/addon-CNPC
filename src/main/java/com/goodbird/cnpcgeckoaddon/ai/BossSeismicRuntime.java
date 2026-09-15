package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * The seismic waves: a series of rings set off across the floor at the end of the wind-up.
 *
 * <p>Owned by {@link TeleportPathController}. Nothing hits here - the series goes to
 * {@link BossSeismicScheduler} the moment the cast lands, because the boss is back on its
 * rotation long before the last ring comes up, which is the whole point of the ability.</p>
 */
final class BossSeismicRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossSeismicRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Nobody to aim at: the rings go round the boss whoever is there. */
    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.SEISMIC, phase) || gameTime < boss.abilityScheduleAt(BossAbility.SEISMIC)) {
            return false;
        }
        BossSeismicSettings seismic = phase.seismic();
        if (BossSeismicScheduler.hasPending(npc)) {
            // A series still running: a second one on top of it would double every ring, so
            // the cast waits its turn the way the shadow copies' does while they are drawn in.
            boss.setAbilityScheduleAt(BossAbility.SEISMIC, gameTime + boss.retryTicks());
            return false;
        }
        boss.beginAction(BossAbility.SEISMIC, seismic.getAnimation(), seismic.getActionDelayTicks(),
                gameTime, null, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.SEISMIC, gameTime + seismic.getActionDelayTicks()
                + boss.rageDown(seismic.getCooldownTicks()));
        return true;
    }

    /** Sets the series off, with every number the enrage turns up already turned up. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        BossSeismicSettings seismic = phase.seismic();
        // The damage and the landing go through the damage door, the throw through the rage
        // alone: the stacks from the copies buff what a ring hits for, not how far anybody flies.
        BossSeismicScheduler.start(level, npc, phase, boss.damageUp(seismic.getDamage()),
                boss.damageUp(seismic.getSlamDamage()), boss.rageUp(seismic.getLaunch()), gameTime);
    }
}
