package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.BossProjectileTuning;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import net.minecraft.server.level.ServerLevel;
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
        if (!boss.mayStart(BossAbility.FLUID_SPIT, phase) || gameTime < boss.abilityScheduleAt(BossAbility.FLUID_SPIT)) return false;
        LivingEntity target = boss.selectAbilityTarget(level, phase.fluidSpit().getTargetMode(),
                phase.fluidSpit().getMaxRange(), candidate -> isValidTarget(candidate, phase));
        if (target == null || FluidBlockUtil.resolve(phase.fluidSpit().getBlock()) == null) {
            boss.setAbilityScheduleAt(BossAbility.FLUID_SPIT, gameTime + boss.retryLongTicks());
            return false;
        }
        boss.beginAction(BossAbility.FLUID_SPIT, phase.fluidSpit().getAnimation(),
                phase.fluidSpit().getActionDelayTicks(), gameTime, target, data, phase);
        boss.setAbilityScheduleAt(BossAbility.FLUID_SPIT, gameTime + phase.fluidSpit().getActionDelayTicks()
                + boss.rageDown(phase.fluidSpit().getCooldownTicks()));
        return true;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive()
                || !boss.isAbilityTarget(target, BossAbilityKind.FLUID)) return false;
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.fluidSpit().getMinRange();
        double max = phase.fluidSpit().getMaxRange();
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        LivingEntity target = boss.pendingTarget(level);
        if (!isValidTarget(target, phase)) return;
        BlockState fluid = FluidBlockUtil.resolve(phase.fluidSpit().getBlock());
        if (fluid == null) {
            if (!phase.fluidSpit().getBlock().equals(reportedBrokenFluid)) {
                reportedBrokenFluid = phase.fluidSpit().getBlock();
                LOGGER.warn("Boss {} cannot spit {}: that block is not a fluid",
                        npc.getName().getString(), phase.fluidSpit().getBlock());
            }
            return;
        }
        reportedBrokenFluid = "";

        float aim = phase.fluidSpit().getAimTurnDegrees();
        npc.getLookControl().setLookAt(target, aim, aim);
        EntityFluidSpit spit = new EntityFluidSpit(EntityRegistry.entityFluidSpit, npc, level);
        spit.configure(fluid, phase.fluidSpit().getLifetimeTicks(), phase.fluidSpit().getRadius(),
                boss.rageUp(phase.fluidSpit().getDamage()));
        // The glob outlives the phase that spat it, so what it is to do in the air goes with
        // it rather than being looked up again when it lands.
        BossProjectileTuning.put(spit, BossProjectileTuning.GRAVITY,
                phase.fluidSpit().getGravityThousandths());
        BossProjectileTuning.put(spit, BossProjectileTuning.LIFE_TICKS,
                phase.fluidSpit().getProjectileLifeTicks());
        BossProjectileTuning.put(spit, BossProjectileTuning.SPLASH_BASE,
                phase.fluidSpit().getSplashBase());
        BossProjectileTuning.put(spit, BossProjectileTuning.SPLASH_PER_RADIUS,
                phase.fluidSpit().getSplashPerRadius());
        spit.setPos(npc.getX(), npc.getEyeY() - 0.1D, npc.getZ());

        // Aim at the feet with a slight arc so the puddle lands on the ground the target
        // stands on instead of splashing against their chest.
        double dx = target.getX() - spit.getX();
        double dy = target.getY() - spit.getY();
        double dz = target.getZ() - spit.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spit.shoot(dx, dy + horizontal * phase.fluidSpit().getArcLift(), dz,
                phase.fluidSpit().getVelocity(), phase.fluidSpit().getInaccuracy());

        if (!level.addFreshEntity(spit)) {
            return;
        }
        phase.fluidSpit().getSpitSound().play(level, npc.getX(), npc.getY(), npc.getZ(),
                SoundSource.HOSTILE);
    }
}
