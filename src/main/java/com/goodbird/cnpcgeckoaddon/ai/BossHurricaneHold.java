package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The sums a storm holds a victim by, with no world in them.
 *
 * <p>Public because the victim's own client runs exactly these between the server's packets:
 * the lift, the circle round the eye and the turn of the view. One set of sums on both sides
 * is what keeps a held player from shivering between where the server puts them and where
 * their client thinks they are.</p>
 */
public final class BossHurricaneHold {

    private BossHurricaneHold() {
    }

    /**
     * Where the victim is on the way up: from where they were caught to the storm's carrying
     * height over the lift's ticks, and at that height from then on. A lift of no ticks is up
     * at once.
     */
    public static double liftY(double startY, double targetY, long caughtAt, long liftEndsAt, long now) {
        if (liftEndsAt <= caughtAt) {
            return targetY;
        }
        double progress = Mth.clamp((now - caughtAt) / (double) (liftEndsAt - caughtAt), 0.0D, 1.0D);
        return Mth.lerp(progress, startY, targetY);
    }

    /** The victim's place on the circle round the eye: nought degrees is east of it, ninety south. */
    public static Vec3 orbitPoint(double centreX, double y, double centreZ, double radius, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        return new Vec3(centreX + Math.cos(angle) * radius, y, centreZ + Math.sin(angle) * radius);
    }

    /** One tick further round the circle, kept inside a turn so the number never runs away. */
    public static double advanceAngle(double angleDegrees, float spinDegrees) {
        double next = angleDegrees + spinDegrees;
        return next >= 360.0D ? next - 360.0D : next;
    }

    /** One tick further round the view, as a yaw the entity can be handed. */
    public static float spinYaw(float yaw, float spinDegrees) {
        return Mth.wrapDegrees(yaw + spinDegrees);
    }

    /** The angle a victim is caught at, so the first tick does not drag them across the eye. */
    static double startAngle(Vec3 centre, double x, double z) {
        return BossHurricanePath.angleOf(centre, x, z);
    }

    /** Whether the hold has run its ticks. */
    static boolean isOver(long endsAt, long now) {
        return now >= endsAt;
    }

    /** When somebody let go may be taken again: never before this. */
    static long graceUntil(long now, int graceTicks) {
        return now + graceTicks;
    }

    /** Whether somebody is still inside the grace they were let go with. */
    static boolean inGrace(Long graceUntil, long now) {
        return graceUntil != null && now < graceUntil;
    }

    /** Whether a storm holding this many has room for one more. */
    static boolean hasRoom(int held, int maxVictims) {
        return held < maxVictims;
    }
}
