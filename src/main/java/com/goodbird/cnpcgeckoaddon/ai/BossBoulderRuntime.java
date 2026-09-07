package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The stones: one rolled or thrown down a committed corridor, and the ring of them dropped
 * out of the sky.
 *
 * <p>Owned by {@link TeleportPathController}. The two share a class because they share
 * everything that matters - the block they are made of, the way a broken block id is
 * reported, and the entity they spawn - and differ only in whether they are aimed. What a
 * fallen stone does afterwards belongs to {@link EntityBossBoulder}; the volley's own clock
 * belongs to {@link BossBoulderRainScheduler}.</p>
 */
final class BossBoulderRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** How long a cast that found nobody, or no such block, waits before looking again. */
    /** The turn left over from the eased wind-up, finished on the tick the stone leaves. */
    private static final float SNAP_DEGREES = 360.0F;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The last block id that turned out not to exist; one line per broken id, not per cast. */
    private String reportedBrokenBlock = "";
    private String reportedBrokenRainBlock = "";

    BossBoulderRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Lets the next broken block id say so again, for a boss going back to idle. */
    void clear() {
        reportedBrokenBlock = "";
        reportedBrokenRainBlock = "";
    }

    /**
     * A stone sent rolling or thrown down a corridor in front of the boss.
     *
     * <p>Where it goes is settled here, exactly as the line strike's corridor is: the
     * warning on the floor promises one path, and the boss keeps that promise even if
     * whoever it picked spends the whole wind-up running sideways.</p>
     */
    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.boulder().canLaunch() || gameTime < boss.abilityScheduleAt(BossAbility.BOULDER)) return false;
        LivingEntity target = boss.selectAbilityTarget(level, phase.boulder().getTargetMode(),
                phase.boulder().getRange(), candidate -> isValidTarget(candidate, phase));
        if (target == null || EntityBossBoulder.resolveBlock(phase.boulder().getBlock()) == null) {
            boss.setAbilityScheduleAt(BossAbility.BOULDER, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        Vec3 flat = new Vec3(target.getX() - npc.getX(), 0.0D, target.getZ() - npc.getZ());
        // Somebody standing inside the boss leaves no direction to read off them, so the
        // gaze decides rather than the aim collapsing to nothing.
        boss.commitAxis(flat.lengthSqr() < 1.0E-6D ? boss.facingAxis() : flat.normalize());
        boss.beginAction(BossAbility.BOULDER, phase.boulder().getAnimation(),
                phase.boulder().getActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would launch the stone before the swing does.
        boss.setAbilityScheduleAt(BossAbility.BOULDER, gameTime + phase.boulder().getActionDelayTicks()
                + boss.rageDown(phase.boulder().getCooldownTicks()));
        return true;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.BOULDER)) {
            return false;
        }
        // Measured flat, the way the corridor itself is laid out.
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double range = phase.boulder().getRange();
        return dx * dx + dz * dz <= range * range;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        Vec3 axis = boss.committedAxis();
        if (axis == null) {
            return;
        }
        BlockState block = EntityBossBoulder.resolveBlock(phase.boulder().getBlock());
        if (block == null) {
            if (!phase.boulder().getBlock().equals(reportedBrokenBlock)) {
                reportedBrokenBlock = phase.boulder().getBlock();
                LOGGER.warn("Boss {} cannot launch a boulder of {}: no such block",
                        npc.getName().getString(), phase.boulder().getBlock());
            }
            return;
        }
        reportedBrokenBlock = "";
        // Whatever the eased turn had left to cover is finished on the tick the stone
        // leaves, so the boss really faces down the corridor it promised.
        boss.turnTowardAxis(axis, phase.boulder().getRange(), SNAP_DEGREES);

        EntityBossBoulder boulder = new EntityBossBoulder(EntityRegistry.entityBossBoulder, level);
        boulder.setOwner(npc);
        boulder.configure(block, phase.boulder().getStyle(), phase.boulder().getScale(),
                boss.rageUp(phase.boulder().getDamage()), boss.rageUp(phase.boulder().getKnockback()),
                phase.boulder().isStopsOnHit(), phase.boulder().getShatterRadius(),
                boss.rageUp(phase.boulder().getShatterDamage()), phase.boulder().getVfx(),
                phase.boulder().getEffects());
        double offset = npc.getBbWidth() * 0.5D + phase.boulder().getScale() / 20.0D + 0.25D;
        boolean rolls = phase.boulder().getMode() == BossPhaseData.BOULDER_MODE_ROLL;
        boulder.setPos(npc.getX() + axis.x * offset,
                rolls ? npc.getY() + 0.1D : npc.getY() + npc.getBbHeight() * 0.6D,
                npc.getZ() + axis.z * offset);
        // The corridor is measured from the boss, so the spawn offset comes off the travel
        // budget rather than being rolled past the far end of the warning.
        double travel = Math.max(2.0D, phase.boulder().getRange() - offset);
        if (rolls) {
            boulder.launchRoll(axis, phase.boulder().getSpeed(), travel);
        } else {
            boulder.launchThrow(axis, phase.boulder().getSpeed(), travel);
        }
        if (!level.addFreshEntity(boulder)) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                block.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    /**
     * A ring of stones dropped out of the sky around wherever the boss is standing.
     *
     * <p>Nothing is aimed: the ring is the shape, and the cast only asks whether there is
     * anybody inside it worth spending a cooldown on. Where each stone comes down is settled
     * by {@link BossBoulderRainScheduler} on the tick the cast lands, because the boss is
     * back on its rotation long before the last of them arrives.</p>
     */
    boolean tryStartRain(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.boulderRain().canLaunch()
                || gameTime < boss.abilityScheduleAt(BossAbility.BOULDER_RAIN)) return false;
        if (EntityBossBoulder.resolveBlock(phase.boulderRain().getBlock()) == null
                || !hasRainTargets(level, phase)) {
            boss.setAbilityScheduleAt(BossAbility.BOULDER_RAIN, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boss.beginAction(BossAbility.BOULDER_RAIN, phase.boulderRain().getAnimation(),
                phase.boulderRain().getActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would start the volley before the swing does.
        boss.setAbilityScheduleAt(BossAbility.BOULDER_RAIN, gameTime + phase.boulderRain().getActionDelayTicks()
                + boss.rageDown(phase.boulderRain().getCooldownTicks()));
        return true;
    }

    /**
     * Whether the ring has anybody in it.
     *
     * <p>Swept to the outer edge and no further: somebody standing in the dead zone at the
     * boss' feet is not a reason to rain, because not one stone can reach them there.</p>
     */
    boolean hasRainTargets(ServerLevel level, BossPhaseData phase) {
        double min = phase.boulderRain().getMinRadius();
        for (LivingEntity target : boss.getTargetsAround(level, npc.position(),
                phase.boulderRain().getRadius(), BossAbilityKind.BOULDER_RAIN)) {
            if (target.position().distanceToSqr(npc.position()) >= min * min) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the whole volley over, and nothing else.
     *
     * <p>Not one stone falls here: the points, the damage and the enrage bonus are snapshotted
     * on this tick and the scheduler drops them on its own clock, which is what lets the boss
     * carry on fighting while its rain is still in the air.</p>
     */
    void performRain(ServerLevel level, BossPhaseData phase, long gameTime) {
        BlockState block = EntityBossBoulder.resolveBlock(phase.boulderRain().getBlock());
        if (block == null) {
            if (!phase.boulderRain().getBlock().equals(reportedBrokenRainBlock)) {
                reportedBrokenRainBlock = phase.boulderRain().getBlock();
                LOGGER.warn("Boss {} cannot rain boulders of {}: no such block",
                        npc.getName().getString(), phase.boulderRain().getBlock());
            }
            return;
        }
        reportedBrokenRainBlock = "";
        BossBoulderRainScheduler.schedule(level, npc, phase, npc.position(), block,
                boss.rageUp(phase.boulderRain().getDamage()), boss.rageUp(phase.boulderRain().getKnockback()),
                boss.rageUp(phase.boulderRain().getShatterDamage()), gameTime);
    }
}
