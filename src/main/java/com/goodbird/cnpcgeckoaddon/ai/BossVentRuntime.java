package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;
import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * The vents: a wind-up that works out which of the builder's boxes the timer will fire, and
 * hands them to {@link BossVentScheduler} as it lands.
 *
 * <p>Owned by {@link TeleportPathController}. The boxes are resolved into the world as the boss
 * commits, so the warning drawn over the wind-up is the vents that go; nothing is hit here - the
 * timer keeps its own beat on the level tick while the boss goes back to its rotation.</p>
 *
 * <p>A cast while the last timer still runs does what the phase's rule says, decided as the boss
 * commits: it starts the timer over, stops it - the cast is spent on that - or is turned away and
 * tried again shortly.</p>
 */
final class BossVentRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The vents the cast being wound up fires, in list order; empty outside a wind-up or for a stop. */
    private final List<BossVentGeometry.Vent> committed = new ArrayList<>();
    /** What the cast being wound up does to the timer, one of the {@code BossVentPlan.CAST_*}. */
    private int committedCast = BossVentPlan.CAST_START;

    BossVentRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.VENT, phase) || gameTime < boss.abilityScheduleAt(BossAbility.VENT)) {
            return false;
        }
        clear();
        BossVentSettings vent = phase.vent();
        int cast = BossVentPlan.onCast(vent.getRecast(), BossVentScheduler.hasPending(npc));
        if (cast == BossVentPlan.CAST_REFUSE) {
            // The timer is left to run: the boss looks again shortly, the way a platform cast does
            // while its fuses still burn, and gets to cast once the timer is over.
            boss.setAbilityScheduleAt(BossAbility.VENT, gameTime + boss.retryTicks());
            return false;
        }
        if (cast != BossVentPlan.CAST_STOP) {
            List<BossVentGeometry.Vent> vents = resolve(level, vent);
            // Every vent switched on lies outside the world: nothing to fire, so the boss looks
            // again shortly rather than spending a whole cooldown on an empty timer.
            if (vents.isEmpty()) {
                boss.setAbilityScheduleAt(BossAbility.VENT, gameTime + boss.retryTicks());
                return false;
            }
            committed.addAll(vents);
        }
        committedCast = cast;
        boss.beginAction(BossAbility.VENT, vent.getAnimation(), vent.getActionDelayTicks(), gameTime,
                null, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.VENT, gameTime + vent.getActionDelayTicks()
                + boss.rageDown(vent.getCooldownTicks()));
        return true;
    }

    /**
     * Starts the timer on the vents the wind-up picked, or stops the running one.
     *
     * <p>What the vents hit for is taken now, the enrage bonus and the copies' stacks included, so
     * a builder editing the phase or the enrage running out mid timer cannot change what the party
     * is already answering: the damage through the damage door, the blast's throw and the wall's
     * push through the enrage alone.</p>
     */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<BossVentGeometry.Vent> vents = List.copyOf(committed);
        int cast = committedCast;
        clear();
        if (cast == BossVentPlan.CAST_STOP) {
            BossVentScheduler.clearBoss(npc);
            return;
        }
        if (vents.isEmpty()) {
            return;
        }
        BossVentSettings vent = phase.vent();
        BossVentScheduler.start(level, npc, phase, new BossVentScheduler.Snapshot(vents,
                boss.damageUp(vent.getDamage()), boss.damageUp(vent.getWallDamage()),
                boss.rageUp(vent.getKnockback()), boss.rageUp(vent.getWallPushTenths())), gameTime);
    }

    /** Drops the vents a wind-up picked; a wind-up that was called off starts and stops nothing. */
    void clear() {
        committed.clear();
        committedCast = BossVentPlan.CAST_START;
    }

    /**
     * Whether a cast may come while the last timer still runs: it can, unless the phase turns such
     * a cast away. What lets a cast spot's walk set off for a cast that is going to start the
     * timer over or stop it, where for any other ability a running effect keeps the walk at home.
     */
    static boolean castsWhileRunning(BossPhaseData phase) {
        return phase.vent().getRecast() != BossVentSettings.RECAST_IGNORE;
    }

    /** Every switched-on vent of this phase that is still inside the world, in list order. */
    private List<BossVentGeometry.Vent> resolve(ServerLevel level, BossVentSettings vent) {
        BlockPos home = BlockPos.containing(boss.homeX(), boss.homeY(), boss.homeZ());
        List<BossVentGeometry.Vent> vents = new ArrayList<>();
        for (BossVentZone zone : vent.getZones().entries()) {
            if (!zone.isEnabled()) {
                continue;
            }
            AABB box = BossVentGeometry.zoneBox(zone, home, level.getMinBuildHeight(), level.getMaxBuildHeight());
            if (box != null) {
                vents.add(new BossVentGeometry.Vent(zone.getZoneId(), box, zone.getFace(), zone.getReach(),
                        zone.modeIn(vent.getMode()), zone.getDelayTicks(), zone.getWeight()));
            }
        }
        return vents;
    }
}
