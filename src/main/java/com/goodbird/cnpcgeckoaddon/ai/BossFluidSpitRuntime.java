package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The spit: a lobbed ball of fluid that leaves a puddle where it lands.
 *
 * <p>Owned by {@link TeleportPathController}. The puddle itself, its lifetime and the
 * terrain it has to put back belong to {@link EntityFluidSpit} and the temporary fluid
 * store; what lives here is only picking a victim and throwing the thing.</p>
 */
final class BossFluidSpitRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** How long a cast that found nobody, or no such fluid, waits before looking again. */
    private static final int RETRY_TICKS = 20;
    /** How much of the flat distance is added as lift, so the ball arcs onto the feet. */
    private static final double ARC_LIFT = 0.2D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The last block id that turned out not to be a fluid; one line per broken id, not per cast. */
    private String reportedBrokenFluid = "";

    BossFluidSpitRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Lets the next broken fluid id say so again, for a boss going back to idle. */
    void clear() {
        reportedBrokenFluid = "";
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.canSpitFluid() || gameTime < boss.abilityScheduleAt(BossAbility.FLUID_SPIT)) return false;
        LivingEntity target = boss.selectAbilityTarget(level, phase.getFluidSpitTargetMode(),
                phase.getFluidSpitMaxRange(), candidate -> isValidTarget(candidate, phase));
        if (target == null || FluidBlockUtil.resolve(phase.getFluidSpitBlock()) == null) {
            boss.setAbilityScheduleAt(BossAbility.FLUID_SPIT, gameTime + RETRY_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.FLUID_SPIT, phase.getFluidSpitAnimation(),
                phase.getFluidSpitActionDelayTicks(), gameTime, target, data, phase);
        boss.setAbilityScheduleAt(BossAbility.FLUID_SPIT, gameTime + phase.getFluidSpitActionDelayTicks()
                + boss.rageDown(phase.getFluidSpitCooldownTicks()));
        return true;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive()
                || !boss.isAbilityTarget(target, BossAbilityKind.FLUID)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.getFluidSpitMinRange();
        double max = phase.getFluidSpitMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = boss.pendingTarget(level);
        if (!isValidTarget(target, phase)) return;
        BlockState fluid = FluidBlockUtil.resolve(phase.getFluidSpitBlock());
        if (fluid == null) {
            if (!phase.getFluidSpitBlock().equals(reportedBrokenFluid)) {
                reportedBrokenFluid = phase.getFluidSpitBlock();
                LOGGER.warn("Boss {} cannot spit {}: that block is not a fluid",
                        npc.getName().getString(), phase.getFluidSpitBlock());
            }
            return;
        }
        reportedBrokenFluid = "";

        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        EntityFluidSpit spit = new EntityFluidSpit(EntityRegistry.entityFluidSpit, npc, level);
        spit.configure(fluid, phase.getFluidSpitLifetimeTicks(), phase.getFluidSpitRadius(),
                boss.rageUp(phase.getFluidSpitDamage()));
        spit.setPos(npc.getX(), npc.getEyeY() - 0.1D, npc.getZ());

        // Aim at the feet with a slight arc so the puddle lands on the ground the target
        // stands on instead of splashing against their chest.
        double dx = target.getX() - spit.getX();
        double dy = target.getY() - spit.getY();
        double dz = target.getZ() - spit.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spit.shoot(dx, dy + horizontal * ARC_LIFT, dz, 1.2F, 4.0F);

        if (!level.addFreshEntity(spit)) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.LLAMA_SPIT,
                SoundSource.HOSTILE, 1.0F, 0.8F);
    }
}
