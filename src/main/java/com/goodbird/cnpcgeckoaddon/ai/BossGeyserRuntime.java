package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The geyser: a fuse lit under a handful of victims, and a column up through the floor when
 * it burns down.
 *
 * <p>Owned by {@link TeleportPathController}. Nothing erupts here - the marks go to
 * {@link BossGeyserScheduler} the moment the cast lands, because the boss is back on its
 * rotation long before the first column comes up, which is the whole point of the ability.</p>
 */
final class BossGeyserRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);


    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The last fluid id that turned out not to be a fluid; one line per broken id, not per cast. */
    private String reportedBrokenFluid = "";

    BossGeyserRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Lets the next broken fluid id say so again, for a boss going back to idle. */
    void clear() {
        reportedBrokenFluid = "";
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.geyser().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.GEYSER)) return false;
        List<LivingEntity> targets = boss.selectAbilityTargets(level, phase.geyser().getTargetMode(),
                phase.geyser().getMaxRange(), candidate -> isValidTarget(candidate, phase),
                phase.geyser().getTargetCount());
        if (targets.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.GEYSER, gameTime + RETRY_TICKS);
            return false;
        }
        boss.rememberExtraTargets(targets);
        boss.beginAction(BossAbility.GEYSER, phase.geyser().getAnimation(),
                phase.geyser().getActionDelayTicks(), gameTime, targets.get(0), data, phase);
        boss.setAbilityScheduleAt(BossAbility.GEYSER, gameTime + phase.geyser().getActionDelayTicks()
                + boss.rageDown(phase.geyser().getCooldownTicks()));
        return true;
    }

    /**
     * Line of sight is deliberately not required: the column comes up through the floor, so
     * a wall someone is standing behind is nothing for it to reach around.
     */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.GEYSER)) {
            return false;
        }
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.geyser().getMinRange();
        double max = phase.geyser().getMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    /** Lights a fuse under everyone this cast wound up on. */
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
        BlockState fluid = fluid(phase);
        // The fuse is deliberately left alone by the enrage: it is the window a player gets
        // to read the mark and step off it, not a number the fight is allowed to turn up.
        int damage = boss.rageUp(phase.geyser().getDamage());
        int launch = boss.rageUp(phase.geyser().getLaunch());
        for (LivingEntity victim : victims) {
            BossGeyserScheduler.schedule(level, npc, victim, phase, fluid, damage, launch, gameTime);
        }
    }

    /** What the eruption pools, or null when it pools nothing or the id is not a fluid. */
    private BlockState fluid(BossPhaseData phase) {
        if (!phase.geyser().leavesGeyserFluid()) {
            return null;
        }
        BlockState fluid = FluidBlockUtil.resolve(phase.geyser().getFluid());
        if (fluid == null) {
            // The geyser still goes off; only the puddle is dropped. Reported once per broken
            // id rather than once per eruption.
            if (!phase.geyser().getFluid().equals(reportedBrokenFluid)) {
                reportedBrokenFluid = phase.geyser().getFluid();
                LOGGER.warn("Boss {} cannot pool {}: that block is not a fluid",
                        npc.getName().getString(), phase.geyser().getFluid());
            }
            return null;
        }
        reportedBrokenFluid = "";
        return fluid;
    }
}
