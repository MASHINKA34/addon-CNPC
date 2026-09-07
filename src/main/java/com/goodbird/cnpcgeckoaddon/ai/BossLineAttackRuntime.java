package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The line strike: a corridor laid straight out in front of the boss, with a weaker wave
 * running along each flank of it.
 *
 * <p>Owned by {@link TeleportPathController}. Where the corridor goes is settled as the
 * boss commits rather than when the hit lands: the warning drawn on the floor promises one
 * corridor, and the boss has to keep that promise even if whoever it picked spends the
 * whole wind-up running sideways.</p>
 */
final class BossLineAttackRuntime {

    /** How long a strike that found an empty corridor waits before looking again. */
    /** The turn left over from the eased wind-up, finished on the tick the strike lands. */
    private static final float SNAP_DEGREES = 360.0F;

    /** Where somebody is standing relative to a line strike: in it, beside it, or clear. */
    private enum Band { MISS, CORRIDOR, SIDE }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    BossLineAttackRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.lineAttack().isEnabled()
                || gameTime < boss.abilityScheduleAt(BossAbility.LINE_ATTACK)) return false;
        LivingEntity target = boss.selectAbilityTarget(level, phase.lineAttack().getTargetMode(),
                phase.lineAttack().getLength(), candidate -> isValidTarget(candidate, phase));
        Vec3 axis = resolveAxis(phase, target);
        // An empty corridor is no reason to swing: the strike would land on bare floor and
        // spend a whole cooldown doing it.
        if (axis == null || targetsIn(level, npc.position(), axis, phase).isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.LINE_ATTACK, gameTime + RETRY_TICKS);
            return false;
        }
        boss.commitAxis(axis);
        boss.beginAction(BossAbility.LINE_ATTACK, phase.lineAttack().getAnimation(),
                phase.lineAttack().getActionDelayTicks(), gameTime, target, data, phase);
        // Only the cooldown is scaled: the action delay is measured against the attack
        // animation, and shortening it would land the hit before the swing does.
        boss.setAbilityScheduleAt(BossAbility.LINE_ATTACK, gameTime + phase.lineAttack().getActionDelayTicks()
                + boss.rageDown(phase.lineAttack().getCooldownTicks()));
        return true;
    }

    /** Which way this strike goes: at whoever it picked, or wherever the boss is looking. */
    private Vec3 resolveAxis(BossPhaseData phase, LivingEntity target) {
        if (phase.lineAttack().getDirection() != BossPhaseData.LINE_DIRECTION_TARGET) {
            return boss.facingAxis();
        }
        if (target == null) {
            return null;
        }
        Vec3 flat = new Vec3(target.getX() - npc.getX(), 0.0D, target.getZ() - npc.getZ());
        // Somebody standing inside the boss leaves no direction to read off them, so the
        // gaze decides rather than the aim collapsing to nothing.
        return flat.lengthSqr() < 1.0E-6D ? boss.facingAxis() : flat.normalize();
    }

    /**
     * Whether one candidate is worth aiming a line strike at.
     *
     * <p>Measured flat and against the same height band the strike itself uses, so the
     * corridor laid down toward whoever this picks really does cover them.</p>
     */
    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive()
                || !boss.isAbilityTarget(target, BossAbilityKind.LINE)) return false;
        if (Math.abs(target.getY() - npc.getY()) > phase.lineAttack().getHeight()) return false;
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double length = phase.lineAttack().getLength();
        return dx * dx + dz * dz <= length * length;
    }

    void perform(ServerLevel level, BossPhaseData phase) {
        Vec3 axis = boss.committedAxis();
        if (axis == null) {
            return;
        }
        if (phase.lineAttack().isFaceAxis()) {
            // Whatever the eased turn had left to cover is finished on the tick the strike
            // lands, so the model points exactly down the corridor it hits.
            boss.turnTowardAxis(axis, phase.lineAttack().getLength(), SNAP_DEGREES);
        }
        Vec3 origin = npc.position();
        // Purely for show, and started before the hits so the wave leaves at the same moment
        // the damage lands rather than a tick behind it.
        BossAreaVfxScheduler.scheduleLine(level, origin, axis, phase);
        int damage = boss.rageUp(phase.lineAttack().getDamage());
        int sideDamage = sideWaveDamage(damage, phase.lineAttack().getSidePercent());
        int knockback = boss.rageUp(phase.lineAttack().getKnockback());
        for (LivingEntity target : targetsIn(level, origin, axis, phase)) {
            boolean side = bandOf(origin, axis, phase, target) == Band.SIDE;
            // Pushed down the line rather than away from the boss: this is a strike forward
            // and not a blast, so everyone it catches is thrown the same way. Vanilla shoves
            // against the vector it is handed, which is why the axis goes in negated.
            BossAbilityDamageUtil.hit(target, BossAbilityKind.LINE, npc, side ? sideDamage : damage,
                    phase.lineAttack().getEffects(), knockback, -axis.x, -axis.z);
        }
    }

    /**
     * What the wave beside the corridor hits for.
     *
     * <p>Rounded up so a light strike does not lose its side wave to integer division, and
     * capped at the corridor's own damage so a hundred percent is as hard as it gets.</p>
     */
    private static int sideWaveDamage(int damage, int percent) {
        return Math.min(damage, Mth.ceil(damage * percent / 100.0D));
    }

    /**
     * Everyone a line strike laid along {@code axis} currently covers, flanks included.
     *
     * <p>The box around the whole strike is only a pre-filter, exactly as the area attack's
     * is - it is what keeps the boss from sweeping the world every time it swings - and the
     * shape itself is decided per candidate. Who may be hit at all is left to the
     * controller's own area rule, so a corridor and an area slam can never end up with
     * different ideas of who counts as an enemy.</p>
     */
    private List<LivingEntity> targetsIn(ServerLevel level, Vec3 origin, Vec3 axis,
                                         BossPhaseData phase) {
        double reach = phase.lineAttack().getWidth() * 0.5D + phase.lineAttack().getSideWidth() + 1.0D;
        AABB box = new AABB(origin, origin.add(axis.scale(phase.lineAttack().getLength())))
                .inflate(reach, phase.lineAttack().getHeight() + 1.0D, reach);
        return level.getEntitiesOfClass(LivingEntity.class, box, target -> target != npc
                && target.isAlive() && boss.isAbilityTarget(target, BossAbilityKind.LINE)
                && bandOf(origin, axis, phase, target) != Band.MISS);
    }

    /**
     * Which part of a line strike covers one entity.
     *
     * <p>Worked along and across the axis: how far down the line they are has to fall inside
     * its length, and how far off it decides whether the corridor itself reaches them or
     * only the weaker wave running beside it.</p>
     */
    private Band bandOf(Vec3 origin, Vec3 axis, BossPhaseData phase, LivingEntity target) {
        if (Math.abs(target.getY() - origin.y) > phase.lineAttack().getHeight()) {
            return Band.MISS;
        }
        double dx = target.getX() - origin.x;
        double dz = target.getZ() - origin.z;
        double along = dx * axis.x + dz * axis.z;
        if (along < 0.0D || along > phase.lineAttack().getLength()) {
            return Band.MISS;
        }
        // The axis is flat and unit length, so a quarter turn of it gives the across
        // measurement without a second normalize.
        double across = Math.abs(dx * axis.z - dz * axis.x);
        double half = phase.lineAttack().getWidth() * 0.5D;
        if (across <= half) {
            return Band.CORRIDOR;
        }
        return phase.lineAttack().getSideWidth() > 0 && across <= half + phase.lineAttack().getSideWidth()
                ? Band.SIDE : Band.MISS;
    }
}
