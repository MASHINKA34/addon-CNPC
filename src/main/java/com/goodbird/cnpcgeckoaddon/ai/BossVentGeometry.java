package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where a vent reaches and which way it goes, kept apart from the world so it can be checked
 * without one.
 *
 * <p>A vent is a box in the floor, the ceiling or a wall, and one face of it is the one it
 * fires out of. What it acts on is that face pushed out along the way it fires by the vent's
 * reach: the box's own cross-section, as long as the reach. The plane at the end of that is
 * the far side - where a wall shoves whoever it catches past, or pins them against.</p>
 *
 * <p>Everything along the way a vent fires is measured in one number: how far out from the
 * face, whichever axis and whichever sign the face has. That is what lets the six faces share
 * one piece of arithmetic instead of six.</p>
 */
final class BossVentGeometry {

    /** A body this close to its resting place is on it: what a pin settles for. */
    static final double REST_EPSILON = 1.0E-3D;
    /**
     * What a player keeps of their sideways speed between the server's pass over them and their
     * own client's: block friction times the air drag on the floor, the air drag alone off it.
     * See BossGravityScheduler for why a speed set on a player is set over it.
     */
    static final double GROUND_DRAG = 0.6D * 0.91D;
    static final double AIR_DRAG = 0.91D;
    /** What every tick takes off a vertical speed, and what it takes off before that: vanilla's own two numbers. */
    static final double VERTICAL_DRAG = 0.98D;
    static final double GRAVITY = 0.08D;
    /**
     * How much of the way back to where a wall pins them a player is sent each tick. Less than
     * all of it: the server sees where a player is a tick or two late, and sending the whole gap
     * every tick would throw them past the spot and back.
     */
    static final double HOLD_GAIN = 0.5D;
    /** The fastest a pinned player is sent back towards their spot, in blocks a tick. */
    static final double HOLD_MAX_SPEED = 1.0D;

    /**
     * One vent resolved into the world on a cast: its box, the face it fires out of, how far it
     * reaches, what it does, its shift in a volley and its weight in a random one.
     *
     * @param id   the vent's zone id, what a status line or a test names it by
     * @param mode what this vent does, the phase's default already put in for one without its own
     */
    record Vent(int id, AABB box, int face, int reach, int mode, int delayTicks, int weight) {

        /** What the vent acts on: its face pushed out by its reach. */
        AABB volume() {
            return BossVentGeometry.volume(box, face, reach);
        }

        /** The way the vent fires, one block long. */
        Vec3 dir() {
            return BossVentGeometry.dir(face);
        }
    }

    private BossVentGeometry() {
    }

    /** The axis a vent in this face fires along. */
    static Direction.Axis axis(int face) {
        return switch (face) {
            case BossVentZone.FACE_FLOOR, BossVentZone.FACE_CEILING -> Direction.Axis.Y;
            case BossVentZone.FACE_NORTH, BossVentZone.FACE_SOUTH -> Direction.Axis.Z;
            default -> Direction.Axis.X;
        };
    }

    /**
     * Which way along its axis a vent in this face fires: up out of the floor, down out of the
     * ceiling, and into the arena out of a wall - south out of the north wall, north out of the
     * south one, east out of the west wall and west out of the east one.
     */
    static int sign(int face) {
        return switch (face) {
            case BossVentZone.FACE_CEILING, BossVentZone.FACE_SOUTH, BossVentZone.FACE_EAST -> -1;
            default -> 1;
        };
    }

    /** The way a vent in this face fires, one block long. */
    static Vec3 dir(int face) {
        int sign = sign(face);
        return switch (axis(face)) {
            case Y -> new Vec3(0.0D, sign, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, sign);
            default -> new Vec3(sign, 0.0D, 0.0D);
        };
    }

    /** Where the face a vent fires out of lies on its axis: the side of the box it fires from. */
    static double faceCoordinate(AABB box, int face) {
        Direction.Axis axis = axis(face);
        return sign(face) > 0 ? box.max(axis) : box.min(axis);
    }

    /** Where the far side lies on the vent's axis: its face, pushed out by the reach. */
    static double farCoordinate(AABB box, int face, double reach) {
        return faceCoordinate(box, face) + sign(face) * reach;
    }

    /**
     * What a vent acts on: the box's cross-section, from its face out to the far side. For a
     * floor vent that is the column over it, for a wall's the slab of air in front of it.
     */
    static AABB volume(AABB box, int face, double reach) {
        double near = faceCoordinate(box, face);
        double far = near + sign(face) * reach;
        double low = Math.min(near, far);
        double high = Math.max(near, far);
        return switch (axis(face)) {
            case Y -> new AABB(box.minX, low, box.minZ, box.maxX, high, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, low, box.maxX, box.maxY, high);
            default -> new AABB(low, box.minY, box.minZ, high, box.maxY, box.maxZ);
        };
    }

    /**
     * Whether a body is in front of the vent at all: its box reaches into the volume. Touching
     * one of the volume's sides from outside is not in it.
     */
    static boolean inVolume(AABB volume, AABB body) {
        return volume.intersects(body);
    }

    /** How far out from the face the near side of a body is, along the way the vent fires. */
    static double start(AABB box, int face, AABB body) {
        Direction.Axis axis = axis(face);
        double near = faceCoordinate(box, face);
        return sign(face) > 0 ? body.min(axis) - near : near - body.max(axis);
    }

    /** How long a body is along the way the vent fires. */
    static double extent(int face, AABB body) {
        Direction.Axis axis = axis(face);
        return body.max(axis) - body.min(axis);
    }

    /**
     * Where the near side of a body rests once a wall has carried it to the far side: with its
     * leading side on the far side, or - for a body longer than the reach - right against the
     * face, so a pin never pushes anybody back into the vent it came out of.
     */
    static double restStart(double reach, double extent) {
        return Math.max(0.0D, reach - extent);
    }

    /**
     * How far a body still has to travel along the way the vent fires to rest at the far side:
     * positive while it is on its way, nought on it, negative once it has gone past.
     */
    static double remaining(AABB box, int face, double reach, AABB body) {
        return restStart(reach, extent(face, body)) - start(box, face, body);
    }

    /** Whether a body has been carried to the far side, or past it. */
    static boolean atFarSide(AABB box, int face, double reach, AABB body) {
        return remaining(box, face, reach, body) <= REST_EPSILON;
    }

    /**
     * The speed a wall carries a body along at, in blocks a tick: along the way the vent fires,
     * with a floor vent's lift on top of its push so nobody is left scraping along the floor.
     */
    static Vec3 push(int face, double push, double lift) {
        Vec3 along = dir(face).scale(push);
        return face == BossVentZone.FACE_FLOOR ? along.add(0.0D, lift, 0.0D) : along;
    }

    /**
     * Where a body a wall has carried to its far side is held: moved back onto the far side if it
     * went past, and left where it is if something stopped it short - a real wall or the floor
     * it is pressed against is as far as a wall carries anybody.
     */
    static Vec3 pinAt(Vec3 position, AABB box, int face, double reach, AABB body) {
        return position.add(dir(face).scale(Math.min(0.0D, remaining(box, face, reach, body))));
    }

    /**
     * One axis of a speed a wall carries a body along at: at least the wall's own its way, and
     * whatever faster the body already had that way left alone. A wall that does not move on this
     * axis leaves it as it was.
     */
    static double carriedBy(double own, double push) {
        if (push > 0.0D) {
            return Math.max(own, push);
        }
        if (push < 0.0D) {
            return Math.min(own, push);
        }
        return own;
    }

    /** What a wall carrying a mob sets on it: its own speed, carried along on every axis the wall moves on. */
    static Vec3 mobCarry(Vec3 own, Vec3 push) {
        return new Vec3(carriedBy(own.x, push.x), carriedBy(own.y, push.y), carriedBy(own.z, push.z));
    }

    /**
     * What a wall carrying a player sets on them, so that what reaches their client after the
     * server's own pass is the wall's push on top of their own run.
     *
     * <p>Sideways, the step their client reported is worn by the drag the client applies anyway,
     * carried along, and set over the drag the server is about to apply once more; with no push
     * that sideways is exactly their own run. Upward or downward, the push is set as it has to be
     * for the server's drag and gravity to leave it as the push; with none, the height they are
     * already moving at is left alone, since the server's idea of a player's fall runs behind.</p>
     *
     * @param step     the movement their client last reported
     * @param vertical the vertical speed the server holds for them now
     * @param drag     {@link #GROUND_DRAG} on the floor, {@link #AIR_DRAG} off it
     */
    static Vec3 playerCarry(Vec3 step, double vertical, Vec3 push, double drag) {
        double x = carriedBy(step.x * drag, push.x) / drag;
        double z = carriedBy(step.z * drag, push.z) / drag;
        double y = push.y == 0.0D ? vertical : push.y / VERTICAL_DRAG + GRAVITY;
        return new Vec3(x, y, z);
    }

    /**
     * What a wall holding a player sets on them: a share of the way back to where they are
     * pinned, on every axis, set over the server's own pass the way a carry is.
     */
    static Vec3 playerHold(Vec3 position, Vec3 anchor, double drag) {
        Vec3 gap = anchor.subtract(position);
        double x = Mth.clamp(gap.x * HOLD_GAIN, -HOLD_MAX_SPEED, HOLD_MAX_SPEED) / drag;
        double y = Mth.clamp(gap.y * HOLD_GAIN, -HOLD_MAX_SPEED, HOLD_MAX_SPEED) / VERTICAL_DRAG + GRAVITY;
        double z = Mth.clamp(gap.z * HOLD_GAIN, -HOLD_MAX_SPEED, HOLD_MAX_SPEED) / drag;
        return new Vec3(x, y, z);
    }

    /**
     * A point over the vent's cross-section, {@code out} blocks out from its face: {@code u} and
     * {@code v} run from 0 to 1 across the two axes the vent does not fire along.
     */
    static Vec3 planePoint(AABB box, int face, double out, double u, double v) {
        double at = faceCoordinate(box, face) + sign(face) * out;
        return switch (axis(face)) {
            case Y -> new Vec3(Mth.lerp(u, box.minX, box.maxX), at, Mth.lerp(v, box.minZ, box.maxZ));
            case Z -> new Vec3(Mth.lerp(u, box.minX, box.maxX), Mth.lerp(v, box.minY, box.maxY), at);
            default -> new Vec3(at, Mth.lerp(v, box.minY, box.maxY), Mth.lerp(u, box.minZ, box.maxZ));
        };
    }

    /** The middle of the plane {@code out} blocks out from the vent's face. */
    static Vec3 planeCentre(AABB box, int face, double out) {
        return planePoint(box, face, out, 0.5D, 0.5D);
    }

    /**
     * How far out a wall's front has come {@code elapsed} ticks after it went: out to the far
     * side over {@code travelTicks}, and standing there after that.
     */
    static double wallFront(double reach, long elapsed, int travelTicks) {
        if (travelTicks <= 0) {
            return reach;
        }
        return reach * Mth.clamp((elapsed + 1.0D) / travelTicks, 0.0D, 1.0D);
    }

    /**
     * The box one vent covers in the world: its corners in either order and both inclusive, the
     * way a platform's box is read, counted from the block the boss activated on for a vent given
     * as an offset, and cut to this dimension's build height.
     *
     * @return the box, or null when the build height leaves nothing of it
     */
    static AABB zoneBox(BossVentZone zone, BlockPos home, int minBuildHeight, int maxBuildHeight) {
        boolean fixed = zone.getCoordinateMode() == BossVentZone.COORDINATE_FIXED;
        long baseX = fixed ? 0L : home.getX();
        long baseY = fixed ? 0L : home.getY();
        long baseZ = fixed ? 0L : home.getZ();
        long minY = Math.max(Math.min(zone.getY1(), zone.getY2()) + baseY, minBuildHeight);
        long maxY = Math.min(Math.max(zone.getY1(), zone.getY2()) + baseY, maxBuildHeight - 1L);
        if (minY > maxY) {
            return null;
        }
        long minX = Math.min(zone.getX1(), zone.getX2()) + baseX;
        long minZ = Math.min(zone.getZ1(), zone.getZ2()) + baseZ;
        long maxX = Math.max(zone.getX1(), zone.getX2()) + baseX;
        long maxZ = Math.max(zone.getZ1(), zone.getZ2()) + baseZ;
        // The upper AABB bounds are exclusive, so adding one takes in every block of the far corner.
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    /**
     * Shares one tick's particle budget out between the cues that want some of it.
     *
     * <p>Within the budget every cue gets what it asked for. Past it each is cut in proportion,
     * rounded down, and a cue asking for any at all keeps at least one while the budget has room:
     * a flame whose smoke rounded to nothing would read as a different flame, not a cheaper one.</p>
     */
    static int[] shareBudget(int budget, int... counts) {
        int[] shared = new int[counts.length];
        long asked = 0L;
        for (int count : counts) {
            asked += Math.max(0, count);
        }
        if (asked <= budget) {
            for (int i = 0; i < counts.length; i++) {
                shared[i] = Math.max(0, counts[i]);
            }
            return shared;
        }
        int given = 0;
        for (int i = 0; i < counts.length; i++) {
            shared[i] = (int) (Math.max(0, counts[i]) * (long) Math.max(0, budget) / asked);
            given += shared[i];
        }
        for (int i = 0; i < counts.length && given < budget; i++) {
            if (counts[i] > 0 && shared[i] == 0) {
                shared[i] = 1;
                given++;
            }
        }
        return shared;
    }
}
