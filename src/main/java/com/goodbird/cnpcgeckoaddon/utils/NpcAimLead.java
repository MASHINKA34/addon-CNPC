package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Where a shot is actually pointed: ahead of a target that is running, and off the aim by
 * however much the burst is allowed to fan out.
 *
 * <p>Both are pure arithmetic on vectors, kept out of the mixin that fires so the numbers can
 * be tested without a world: a projectile aimed at where somebody stood is a projectile that
 * never hits anybody who keeps moving, and a fan of shots that all land on the same point is
 * not a fan at all.</p>
 */
public final class NpcAimLead {

    /** Below this a velocity is somebody standing still, and there is nothing to lead. */
    private static final double STILL = 1.0E-4D;

    private NpcAimLead() {
    }

    /**
     * The point to aim at so a moving target walks into the shot.
     *
     * @param muzzle           where the projectile leaves from
     * @param target           where the target is now
     * @param targetVelocity   how far it moves in one tick; for a player, its client's own
     *                         last reported step
     * @param projectileSpeed  blocks a tick the projectile travels
     * @param leadPercent      how much of the travel to aim ahead by, 0..100
     * @return the aim point, which for a still target or no lead at all is the target itself
     */
    public static Vec3 aimPoint(Vec3 muzzle, Vec3 target, Vec3 targetVelocity,
                                double projectileSpeed, int leadPercent) {
        int lead = Mth.clamp(leadPercent, 0, 100);
        if (lead <= 0 || projectileSpeed <= 0.0D || targetVelocity == null
                || targetVelocity.lengthSqr() < STILL * STILL) {
            return target;
        }
        // Where the target is now, not where it will be: leading off the led point would chase
        // its own tail, and one round of it is what a bow does anyway.
        double flightTicks = target.subtract(muzzle).length() / projectileSpeed;
        if (!Double.isFinite(flightTicks) || flightTicks <= 0.0D) {
            return target;
        }
        return target.add(targetVelocity.scale(flightTicks * lead / 100.0D));
    }

    /**
     * The same direction, turned off the aim at random by up to half the fan either way.
     *
     * <p>The yaw takes the whole fan and the pitch half of it: a wall of arrows across the
     * doorway reads as spread, while the same scatter up and down reads as a miss.</p>
     *
     * @param direction     from the muzzle to the aim point; its length is kept
     * @param spreadDegrees how wide the fan is; 0 hands the direction straight back
     */
    public static Vec3 spread(Vec3 direction, int spreadDegrees, RandomSource random) {
        int spread = Mth.clamp(spreadDegrees, 0, 360);
        double length = direction.length();
        if (spread <= 0 || random == null || length < STILL) {
            return direction;
        }
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double yaw = Math.atan2(direction.x, direction.z) + Math.toRadians(offset(random, spread));
        double pitch = Math.atan2(direction.y, horizontal) + Math.toRadians(offset(random, spread / 2.0D));
        // Held off the poles: a shot turned past straight up would come out flipped in yaw.
        double limit = Math.toRadians(89.0D);
        pitch = Mth.clamp(pitch, -limit, limit);
        double flat = Math.cos(pitch) * length;
        return new Vec3(Math.sin(yaw) * flat, Math.sin(pitch) * length, Math.cos(yaw) * flat);
    }

    /** An angle inside the fan: half of it either way, flat rather than bunched in the middle. */
    private static double offset(RandomSource random, double degrees) {
        return (random.nextDouble() - 0.5D) * degrees;
    }
}
