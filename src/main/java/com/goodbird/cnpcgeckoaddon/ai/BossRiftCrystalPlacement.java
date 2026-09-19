package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalPoint;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a rift's crystals hang, when one is collected, and how one moves while it waits - all of
 * it worked out without a world.
 *
 * <p>The floor is the one thing a plan cannot know by itself, so it is asked for: the rift hands
 * in the level's own search, a test hands in a floor of its own making, and both get the same
 * arithmetic. Everything else here is numbers in and numbers out, which is what lets the spacing
 * of a ring, the height of a hover and the reach of a zone be checked without a pocket dimension
 * to stand them in.</p>
 */
public final class BossRiftCrystalPlacement {

    /**
     * How far above the floor's own height the search for it starts, in blocks. Two, so a zone
     * whose floor is a step up or down from the platform still finds it.
     */
    public static final int FLOOR_SEARCH_LIFT = 2;
    /**
     * How far above and below the floor of a zone a player's feet may be and still count as
     * inside it. Enough for a jump and for a step down, and not enough to reach through a ceiling.
     */
    public static final double ZONE_HEIGHT_REACH = 2.0D;

    /** The floor under a spot, as the level knows it. */
    public interface FloorFinder {
        /**
         * @param fromY where to start looking, going down
         * @return the y of the top surface a crystal hovers over, or null for nothing within reach
         */
        Double floorTop(double x, double z, double fromY);
    }

    /**
     * One crystal's place in the world.
     *
     * @param pointId the zone it belongs to, or nought for one of a ring
     * @param y       where the crystal hangs: the floor, plus the hover
     * @param floorTop the surface of its zone, which is what a player stands on
     * @param radius  how far into the zone counts, in blocks
     * @param block   the block it is drawn as, empty for the rift's own
     * @param color   the tint it is painted in
     */
    public record Spot(int pointId, double x, double y, double z, double floorTop, double radius,
                       String block, int color) {
    }

    private BossRiftCrystalPlacement() {
    }

    /**
     * Where this rift's crystals go: one to each zone the builder listed, or a ring round the
     * middle of the platform when the list is empty.
     *
     * <p>A zone with no floor under it within reach is left out rather than given a crystal
     * hanging in the void, and the caller says so in the log; a ring whose every point misses
     * the floor - a platform smaller than the ring - comes back empty, which the rift reads as
     * "nothing to gather".</p>
     *
     * @param centreX  the middle of the platform, where an offset zone is measured from
     * @param centreY  the height of the platform's floor block
     * @param turn     where the ring starts, in radians, so two rifts do not stand theirs alike
     */
    public static List<Spot> plan(BossRiftSettings settings, double centreX, double centreY, double centreZ,
                                 double turn, FloorFinder floors) {
        List<Spot> spots = new ArrayList<>();
        List<BossRiftCrystalPoint> points = settings.getCrystalPoints().enabled();
        double hover = settings.crystalHover();
        if (!points.isEmpty()) {
            for (BossRiftCrystalPoint point : points) {
                boolean fixed = point.getCoordinateMode() == BossRiftCrystalPoint.COORDINATE_FIXED;
                double x = fixed ? point.getX() + 0.5D : centreX + point.getX();
                double z = fixed ? point.getZ() + 0.5D : centreZ + point.getZ();
                double y = fixed ? point.getY() : centreY + point.getY();
                double radius = point.getRadiusTenths() > 0
                        ? point.getRadiusTenths() / 10.0D : settings.crystalCollectRadius();
                int color = point.getColorOverride() < 0 ? settings.getCrystalColor() : point.getColorOverride();
                add(spots, floors, point.getPointId(), x, y, z, hover, radius, point.getBlockOverride(), color);
            }
            return spots;
        }
        int count = settings.getCrystalCount();
        double ring = settings.getCrystalRingRadius();
        for (int i = 0; i < count; i++) {
            double angle = turn + Mth.TWO_PI * i / count;
            add(spots, floors, 0, centreX + Math.cos(angle) * ring, centreY,
                    centreZ + Math.sin(angle) * ring, hover, settings.crystalCollectRadius(), "",
                    settings.getCrystalColor());
        }
        return spots;
    }

    private static void add(List<Spot> spots, FloorFinder floors, int pointId, double x, double y, double z,
                            double hover, double radius, String block, int color) {
        Double floorTop = floors.floorTop(x, z, y + FLOOR_SEARCH_LIFT);
        if (floorTop == null) {
            return;
        }
        spots.add(new Spot(pointId, x, floorTop + hover, z, floorTop, radius, block, color));
    }

    /**
     * Whether a player standing here has walked into this zone: within its radius flat out, and
     * with their feet within {@link #ZONE_HEIGHT_REACH} of its floor, so somebody on the storey
     * above or in the cellar below is not collecting through it.
     */
    public static boolean inZone(Spot spot, double playerX, double playerFeetY, double playerZ) {
        if (Math.abs(playerFeetY - spot.floorTop()) > ZONE_HEIGHT_REACH) {
            return false;
        }
        double dx = playerX - spot.x();
        double dz = playerZ - spot.z();
        return dx * dx + dz * dz <= spot.radius() * spot.radius();
    }

    /**
     * Whether a player standing here has reached the crystal itself: the distance measured from
     * whichever part of them is nearest its height, so a crystal hovering two blocks up is
     * touched by standing under it rather than only by jumping at it.
     */
    public static boolean touches(Spot spot, double playerX, double playerFeetY, double playerZ,
                                  double playerHeight) {
        double nearestY = Mth.clamp(spot.y(), playerFeetY, playerFeetY + Math.max(0.0D, playerHeight));
        double dx = playerX - spot.x();
        double dy = spot.y() - nearestY;
        double dz = playerZ - spot.z();
        return dx * dx + dy * dy + dz * dz <= spot.radius() * spot.radius();
    }

    /**
     * Whether this player collects this crystal now, by whichever of the two the phase asked for.
     */
    public static boolean collects(Spot spot, int collectMode, double playerX, double playerFeetY, double playerZ,
                                   double playerHeight) {
        return collectMode == BossRiftSettings.COLLECT_TOUCH
                ? touches(spot, playerX, playerFeetY, playerZ, playerHeight)
                : inZone(spot, playerX, playerFeetY, playerZ);
    }

    /** How far round a crystal has turned after this many ticks, in degrees. */
    public static float spinAngle(double ageTicks, int spinDegrees) {
        if (spinDegrees <= 0) {
            return 0.0F;
        }
        // Wrapped rather than left to grow: a crystal standing for an hour would otherwise hand
        // the renderer an angle large enough to lose its own fraction.
        return (float) Mth.positiveModulo(ageTicks * spinDegrees, 360.0D);
    }

    /** How far off its resting height a crystal is bobbing after this many ticks, in blocks. */
    public static double bobOffset(double ageTicks, int bobTenths, int bobPeriodTicks) {
        if (bobTenths <= 0 || bobPeriodTicks <= 0) {
            return 0.0D;
        }
        return Math.sin(Mth.TWO_PI * ageTicks / bobPeriodTicks) * bobTenths / 10.0D;
    }
}
