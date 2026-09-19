package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.NpcAnimationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

/**
 * The ranged fight the addon runs for an npc whose switch is on: it holds a distance, closes in
 * or backs off, waits for a clear shot or lobs one over what is in the way, and fires in bursts
 * with a pause between them.
 *
 * <p>What to do is {@link NpcRangedPlan}'s, tick by tick and without a world; this is the half
 * that needs the npc - the distance, the legs, the shot, the warning ring. What is fired and
 * where it is pointed is neither's: that is settled inside {@code performRangedAttack}, so the
 * same projectile, fallback, lead and spread serve a boss and a plain npc alike.</p>
 *
 * <p>Added to every npc that can attack and asked on every use whether it is wanted, rather
 * than added only to the ones whose switch is on: the switch is edited from a screen, and a
 * goal list is only rebuilt when CustomNPCs decides to, so a goal added on the strength of the
 * old setting would go on running the old way until the world was reloaded.</p>
 */
public final class NpcRangedAttackGoal extends Goal {

    /** Every other tick, the interval every warning in the addon is drawn on. */
    private static final int WARN_INTERVAL_TICKS = 2;

    /** How fast the npc walks up to its window, and how fast it backs out of it. */
    private static final double APPROACH_SPEED = 1.0D;
    private static final double RETREAT_SPEED = 1.2D;

    /** How far each step of a retreat goes; the same three blocks the keep-distance goal takes. */
    private static final double RETREAT_STEP = 3.0D;

    /** The animation CustomNPCs plays while aiming, during which it swings nothing. */
    private static final int AIM_ANIMATION = 6;

    private final EntityNPCInterface npc;
    private final NpcRangedPlan plan = new NpcRangedPlan();
    private LivingEntity target;

    public NpcRangedAttackGoal(EntityNPCInterface npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        try {
            if (!NpcRangedAi.runsRangedAi(npc) || npc.isKilled() || NpcRangedAi.yieldsToMelee(npc)) {
                return false;
            }
            LivingEntity candidate = npc.getTarget();
            // The npc's own aggro range, the way CustomNPCs' goals judge it: a window wider
            // than the npc is willing to chase is still held to what it will chase.
            if (candidate == null || !candidate.isAlive()
                    || !npc.isInRange(candidate, npc.stats.aggroRange)) {
                return false;
            }
            this.target = candidate;
            return true;
        } catch (Throwable error) {
            CrashGuard.caught("ai.npc_ranged.can_use", error);
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        plan.start(npc.stats.ranged.getDelayMin());
    }

    @Override
    public void stop() {
        this.target = null;
        npc.getNavigation().stop();
        // Deliberately not setTarget(null): CustomNPCs' own ranged goal drops the target when
        // it stops, and this one steps aside whenever the npc is to fight in melee instead -
        // taking the target with it would call the whole fight off.
    }

    @Override
    public void tick() {
        try {
            step();
        } catch (Throwable error) {
            CrashGuard.caught("ai.npc_ranged.tick", error);
        }
    }

    private void step() {
        if (target == null) {
            return;
        }
        RangedExtraData extra = NpcRangedAi.extra(npc);
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = Math.sqrt(npc.distanceToSqr(target));
        boolean lineOfSight = npc.getSensing().hasLineOfSight(target);
        NpcRangedPlan.Step step = plan.tick(NpcRangedAi.settings(npc), distance, lineOfSight,
                () -> npc.stats.ranged.getDelayRNG());
        move(step.move());
        if (step.warnLob()) {
            warnLob(extra);
        }
        if (step.reloadStarted()) {
            reload(extra);
        }
        if (step.fire()) {
            fire(step.indirect());
        }
    }

    private void move(NpcRangedPlan.Move move) {
        switch (move) {
            case APPROACH -> npc.getNavigation().moveTo(target, APPROACH_SPEED);
            case RETREAT -> retreat();
            // Melee is CustomNPCs' to run and this goal is about to stand down for it, and a
            // window the npc is standing in is a window it stays standing in.
            case HOLD, MELEE -> npc.getNavigation().stop();
        }
    }

    /**
     * One step directly away from the target, the keep-distance goal's technique: a path to
     * "somewhere behind me" is not something the navigation can be asked for, and the move
     * control walks there without one.
     */
    private void retreat() {
        Vec3 away = npc.position().subtract(target.position());
        if (away.horizontalDistanceSqr() < 1.0E-4D) {
            // Standing inside the npc: any direction will do, and none is already chosen.
            away = new Vec3(npc.getRandom().nextDouble() - 0.5D, 0.0D, npc.getRandom().nextDouble() - 0.5D);
        }
        away = away.normalize().scale(RETREAT_STEP);
        npc.getMoveControl().setWantedPosition(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z,
                RETREAT_SPEED);
    }

    /** The ring on the floor where a shot the npc cannot see its target for is about to land. */
    private void warnLob(RangedExtraData extra) {
        if (extra.getLobWarnTicks() <= 0 || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % WARN_INTERVAL_TICKS != 0L) {
            return;
        }
        Vec3 aim = NpcRangedAi.aimPoint(npc, target, extra);
        // On the floor under the aim where there is one, so the ring lies flat rather than
        // hanging in the air over a hole the target is standing beside.
        BlockPos floor = BossFloorUtil.findFloor(level, aim.x, aim.y, aim.z);
        Vec3 centre = floor == null ? aim : new Vec3(aim.x, floor.getY() + 1.05D, aim.z);
        double radius = extra.getLobWarnRadiusTenths() / 10.0D;
        // A plain npc has no telegraph paint of its own, so the shot's own colour serves.
        BossTelegraphUtil.ring(level, centre, radius, BossTelegraphUtil.dust(BossAbilityKind.RANGED));
        extra.getLobWarnParticles().emitDust(level, centre.x, centre.y, centre.z,
                radius * 0.5D, 0.1D, radius * 0.5D, 0.0D, BossAbilityKind.RANGED);
    }

    /** The pause after a burst: what the model does during it, and what it sounds like. */
    private void reload(RangedExtraData extra) {
        NpcAnimationUtil.play(npc, extra.getReloadAnimation());
        if (npc.level() instanceof ServerLevel level) {
            extra.getReloadSound().play(level, npc.getX(), npc.getY(), npc.getZ(), SoundSource.HOSTILE);
        }
    }

    /**
     * One shot. What leaves the bow - the npc's own entity, its projectile item, the fallback -
     * and where it is pointed are settled inside {@code performRangedAttack}.
     */
    private void fire(boolean indirect) {
        npc.performRangedAttack(target, indirect ? 1.0F : 0.0F);
        if (npc.currentAnimation != AIM_ANIMATION) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }
}
