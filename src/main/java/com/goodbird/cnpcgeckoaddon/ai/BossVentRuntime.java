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
 * The vents: a wind-up that works out which of the builder's boxes the timer will fire.
 *
 * <p>Owned by {@link TeleportPathController}. The boxes are resolved into the world as the boss
 * commits, so the warning drawn over the wind-up is the vents that go; nothing is hit here.</p>
 */
final class BossVentRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The vents the cast being wound up fires, in list order; empty outside a wind-up. */
    private final List<BossVentGeometry.Vent> committed = new ArrayList<>();

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
        List<BossVentGeometry.Vent> vents = resolve(level, vent);
        // Every vent switched on lies outside the world: nothing to fire, so the boss looks
        // again shortly rather than spending a whole cooldown on an empty timer.
        if (vents.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.VENT, gameTime + boss.retryTicks());
            return false;
        }
        committed.addAll(vents);
        boss.beginAction(BossAbility.VENT, vent.getAnimation(), vent.getActionDelayTicks(), gameTime,
                null, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.VENT, gameTime + vent.getActionDelayTicks()
                + boss.rageDown(vent.getCooldownTicks()));
        return true;
    }

    /** Lets the vents the wind-up picked go. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        clear();
    }

    /** Drops the vents a wind-up picked; a wind-up that was called off fires nothing. */
    void clear() {
        committed.clear();
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
