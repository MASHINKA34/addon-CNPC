package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.world.phys.Vec3;

/**
 * One figure of a boss warning, in the terms it was drawn with rather than as points.
 *
 * <p>A ring is a centre and a radius all the way from the server to the screen. Sending the
 * hundreds of points it breaks into instead would cost a packet per tick per shape and would
 * still be wrong: the client has to lay every point on its own floor, and only it knows which
 * blocks the player has loaded.</p>
 *
 * <p>The five numbers after the anchor mean different things per kind, which is what keeps one
 * record - and therefore one codec and one renderer - able to carry all six figures. Each kind
 * has its own factory and its own named readers below, so nothing outside this file has to
 * remember which slot is which. Floats rather than doubles because that is what goes over the
 * wire: a shape built here is exactly the shape that arrives.</p>
 */
public record TelegraphShape(byte kind, int rgb, boolean faded,
                             float x, float y, float z,
                             float a, float b, float c, float d, float e) {

    /** A full circle on the floor: the anchor is its centre, {@code a} its radius. */
    public static final byte KIND_RING = 0;
    /** A flat box: the anchor is its lower corner and {@code y}, {@code a} and {@code b} the upper. */
    public static final byte KIND_RECTANGLE = 1;
    /** A slice of a circle's edge: centre, radius, facing yaw, half opening. */
    public static final byte KIND_ARC = 2;
    /** The same slice with its two straight sides drawn in. */
    public static final byte KIND_SECTOR = 3;
    /** A lane: origin, flat axis, length, width, and the softer band beside it. */
    public static final byte KIND_CORRIDOR = 4;
    /** The straight run from the boss' eye to what it picked out; not on the floor. */
    public static final byte KIND_LINK = 5;

    public static TelegraphShape ring(Vec3 centre, double radius, int rgb, boolean faded) {
        return new TelegraphShape(KIND_RING, rgb, faded, (float) centre.x, (float) centre.y,
                (float) centre.z, (float) radius, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public static TelegraphShape rectangle(double minX, double minZ, double maxX, double maxZ,
                                           double y, int rgb, boolean faded) {
        return new TelegraphShape(KIND_RECTANGLE, rgb, faded, (float) minX, (float) y, (float) minZ,
                (float) maxX, (float) maxZ, 0.0F, 0.0F, 0.0F);
    }

    public static TelegraphShape arc(Vec3 centre, double radius, float yaw, double halfAngle,
                                     int rgb, boolean faded) {
        return fan(KIND_ARC, centre, radius, yaw, halfAngle, rgb, faded);
    }

    public static TelegraphShape sector(Vec3 centre, double radius, float yaw, double halfAngle,
                                        int rgb, boolean faded) {
        return fan(KIND_SECTOR, centre, radius, yaw, halfAngle, rgb, faded);
    }

    private static TelegraphShape fan(byte kind, Vec3 centre, double radius, float yaw,
                                      double halfAngle, int rgb, boolean faded) {
        return new TelegraphShape(kind, rgb, faded, (float) centre.x, (float) centre.y,
                (float) centre.z, (float) radius, yaw, (float) halfAngle, 0.0F, 0.0F);
    }

    public static TelegraphShape corridor(Vec3 origin, Vec3 axis, double length, double width,
                                          double sideWidth, int rgb, boolean faded) {
        return new TelegraphShape(KIND_CORRIDOR, rgb, faded, (float) origin.x, (float) origin.y,
                (float) origin.z, (float) axis.x, (float) axis.z, (float) length, (float) width,
                (float) sideWidth);
    }

    public static TelegraphShape link(Vec3 from, Vec3 to, int rgb, boolean faded) {
        return new TelegraphShape(KIND_LINK, rgb, faded, (float) from.x, (float) from.y,
                (float) from.z, (float) to.x, (float) to.y, (float) to.z, 0.0F, 0.0F);
    }

    /** The anchor: a centre, a lower corner, a lane's origin or the start of a run. */
    public Vec3 anchor() {
        return new Vec3(x, y, z);
    }

    /** Ring, arc and sector. */
    public double radius() {
        return a;
    }

    /** Arc and sector: where the fan looks, in Minecraft's own degrees. */
    public float yaw() {
        return b;
    }

    /** Arc and sector: how far it opens to either side of that, in degrees. */
    public double halfAngle() {
        return c;
    }

    public double maxX() {
        return a;
    }

    public double maxZ() {
        return b;
    }

    /** Corridor: the flat unit direction it runs in. */
    public Vec3 axis() {
        return new Vec3(a, 0.0D, b);
    }

    public double length() {
        return c;
    }

    public double width() {
        return d;
    }

    /** Corridor: how far past its own side the softer band reaches, or zero for none. */
    public double sideWidth() {
        return e;
    }

    /** Link: the far end of the run. */
    public Vec3 to() {
        return new Vec3(a, b, c);
    }

    /**
     * How far from {@code origin} the furthest part of this shape can lie.
     *
     * <p>What decides who is sent the frame: a player is shown it when they are within the
     * ordinary audience range of the boss plus this, because a mark can sit a long way from
     * whoever cast it and a hazard's edge is by nature nowhere near its middle.</p>
     */
    public double reachFrom(Vec3 origin) {
        return switch (kind) {
            case KIND_RING, KIND_ARC, KIND_SECTOR -> origin.distanceTo(anchor()) + Math.abs(radius());
            case KIND_RECTANGLE -> {
                double far = 0.0D;
                for (double cornerX : new double[]{x, maxX()}) {
                    for (double cornerZ : new double[]{z, maxZ()}) {
                        far = Math.max(far, origin.distanceTo(new Vec3(cornerX, y, cornerZ)));
                    }
                }
                yield far;
            }
            case KIND_CORRIDOR -> origin.distanceTo(anchor())
                    + Math.abs(length()) + Math.abs(width()) * 0.5D + Math.abs(sideWidth());
            case KIND_LINK -> Math.max(origin.distanceTo(anchor()), origin.distanceTo(to()));
            default -> origin.distanceTo(anchor());
        };
    }
}
