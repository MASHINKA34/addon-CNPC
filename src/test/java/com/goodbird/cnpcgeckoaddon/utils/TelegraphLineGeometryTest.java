package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shapes a drawn warning is made of, and the curves it is animated by, checked without
 * a world or a window.
 *
 * <p>None of this throws when it is wrong. A ring that does not close, a fan missing a side,
 * a flood that reaches the edge a second late - each of them is a warning that reads as
 * something other than the ability about to land, and the only place it shows is somebody's
 * screen in the middle of a fight.</p>
 */
class TelegraphLineGeometryTest {

    private static final double EPSILON = 1.0E-6D;
    private static final int RED = 0xFF0000;

    @Test
    @DisplayName("a ring closes on itself and every point of it sits on the radius")
    void ringIsAClosedCircle() {
        Vec3 centre = new Vec3(10.0D, 64.0D, -4.0D);
        List<TelegraphLineGeometry.Run> runs = TelegraphLineGeometry.contours(
                TelegraphShape.ring(centre, 6.0D, RED, false));
        assertEquals(1, runs.size());
        TelegraphLineGeometry.Run run = runs.getFirst();
        assertTrue(run.closed());
        assertEquals(run.points().getFirst(), run.points().getLast(),
                "a ring that does not come back to its start is drawn with a gap in it");
        for (Vec3 point : run.points()) {
            double dx = point.x - centre.x;
            double dz = point.z - centre.z;
            assertEquals(6.0D, Math.sqrt(dx * dx + dz * dz), 1.0E-9D);
            assertEquals(centre.y, point.y, EPSILON, "a ring is flat until the floor is found");
        }
        // Close enough to the circumference to be read as a circle rather than a polygon.
        assertEquals(2.0D * Math.PI * 6.0D, run.length(), 0.1D);
    }

    @Test
    @DisplayName("a small ring is still a circle rather than a triangle")
    void aSmallRingKeepsItsPoints() {
        TelegraphLineGeometry.Run run = TelegraphLineGeometry.contours(
                TelegraphShape.ring(Vec3.ZERO, 0.2D, RED, false)).getFirst();
        assertTrue(run.points().size() >= 9, "too few points to read as a circle");
    }

    @Test
    @DisplayName("a rectangle closes and passes through all four of its corners")
    void rectangleHasFourCorners() {
        List<TelegraphLineGeometry.Run> runs = TelegraphLineGeometry.contours(
                TelegraphShape.rectangle(-3.0D, 1.0D, 5.0D, 9.0D, 70.0D, RED, false));
        assertEquals(1, runs.size());
        TelegraphLineGeometry.Run run = runs.getFirst();
        assertTrue(run.closed());
        assertEquals(run.points().getFirst(), run.points().getLast());
        for (double x : new double[]{-3.0D, 5.0D}) {
            for (double z : new double[]{1.0D, 9.0D}) {
                assertTrue(has(run.points(), x, z),
                        "the corner " + x + "/" + z + " is not on the outline");
            }
        }
        assertEquals(2.0D * (8.0D + 8.0D), run.length(), EPSILON);
    }

    @Test
    @DisplayName("a rectangle read from either pair of corners is the same box")
    void rectangleTakesItsCornersInEitherOrder() {
        double straight = TelegraphLineGeometry.contours(
                TelegraphShape.rectangle(-3.0D, 1.0D, 5.0D, 9.0D, 70.0D, RED, false))
                .getFirst().length();
        double swapped = TelegraphLineGeometry.contours(
                TelegraphShape.rectangle(5.0D, 9.0D, -3.0D, 1.0D, 70.0D, RED, false))
                .getFirst().length();
        assertEquals(straight, swapped, EPSILON);
    }

    @Test
    @DisplayName("a sector is its arc and a straight side out to each end of it")
    void sectorIsAnArcAndTwoSides() {
        Vec3 centre = new Vec3(0.0D, 64.0D, 0.0D);
        List<TelegraphLineGeometry.Run> runs = TelegraphLineGeometry.contours(
                TelegraphShape.sector(centre, 8.0D, 0.0F, 30.0D, RED, false));
        assertEquals(3, runs.size(), "a cone without its sides says nothing about which way is out");
        TelegraphLineGeometry.Run arc = runs.getFirst();
        for (Vec3 point : arc.points()) {
            assertEquals(8.0D, flatDistance(centre, point), 1.0E-9D);
        }
        assertEquals(Math.toRadians(60.0D) * 8.0D, arc.length(), 0.05D);
        for (TelegraphLineGeometry.Run side : runs.subList(1, 3)) {
            assertEquals(centre, side.points().getFirst(), "a side has to start at the boss");
            assertEquals(8.0D, side.length(), EPSILON);
            assertEquals(8.0D, flatDistance(centre, side.points().getLast()), 1.0E-9D);
        }
        // The two sides are the ends of the arc, so the fan really is shut.
        assertTrue(arc.points().getFirst().distanceTo(runs.get(1).points().getLast()) < EPSILON);
        assertTrue(arc.points().getLast().distanceTo(runs.get(2).points().getLast()) < EPSILON);
    }

    @Test
    @DisplayName("an arc on its own is drawn without the sides a cone gets")
    void arcIsJustTheArc() {
        assertEquals(1, TelegraphLineGeometry.contours(
                TelegraphShape.arc(Vec3.ZERO, 4.0D, 90.0F, 60.0D, RED, false)).size());
    }

    @Test
    @DisplayName("a lane with bands beside it draws them faded and its own sides bright")
    void corridorBandsAreFaded() {
        List<TelegraphLineGeometry.Run> runs = TelegraphLineGeometry.contours(
                TelegraphShape.corridor(Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                        12.0D, 3.0D, 2.0D, RED, false));
        assertEquals(3, runs.size());
        assertFalse(runs.getFirst().faded(), "the lane itself is the half that kills");
        assertTrue(runs.getFirst().closed());
        assertEquals(2.0D * (12.0D + 3.0D), runs.getFirst().length(), EPSILON);
        for (TelegraphLineGeometry.Run band : runs.subList(1, 3)) {
            assertTrue(band.faded(), "a band beside the lane hurts less and has to read as less");
            assertFalse(band.closed());
            // Out from the lane's side, down the lane and back in: the inner edge is the
            // lane's own side and is never drawn twice.
            assertEquals(2.0D + 12.0D + 2.0D, band.length(), EPSILON);
        }
        // One band each side of the lane rather than two on the same side.
        assertTrue(runs.get(1).points().getFirst().z * runs.get(2).points().getFirst().z < 0.0D);
    }

    @Test
    @DisplayName("a lane with no bands is only the lane")
    void corridorWithoutBands() {
        assertEquals(1, TelegraphLineGeometry.contours(
                TelegraphShape.corridor(Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D),
                        6.0D, 2.0D, 0.0D, RED, false)).size());
    }

    @Test
    @DisplayName("a faded shape hands its fade down to every line it is drawn as")
    void afadedShapeFadesAllOfItself() {
        for (TelegraphLineGeometry.Run run : TelegraphLineGeometry.contours(
                TelegraphShape.sector(Vec3.ZERO, 5.0D, 0.0F, 20.0D, RED, true))) {
            assertTrue(run.faded());
        }
    }

    @Test
    @DisplayName("the run to a target keeps its height instead of being flattened")
    void theLinkRunIsNotFlat() {
        TelegraphLineGeometry.Run run = TelegraphLineGeometry.contours(
                TelegraphShape.link(new Vec3(0.0D, 70.0D, 0.0D), new Vec3(0.0D, 64.0D, 8.0D),
                        RED, false)).getFirst();
        assertEquals(70.0D, run.points().getFirst().y, EPSILON);
        assertEquals(64.0D, run.points().getLast().y, EPSILON);
        assertEquals(10.0D, run.length(), EPSILON);
    }

    @Test
    @DisplayName("dashes cover the share of a run their pattern says they do")
    void dashesCoverTheirShare() {
        double total = 30.0D;
        List<TelegraphLineGeometry.Span> dashes = TelegraphLineGeometry.dashes(total,
                TelegraphLineGeometry.DASH_LENGTH, TelegraphLineGeometry.DASH_GAP);
        assertEquals(20.0D, TelegraphLineGeometry.covered(dashes), 1.0D,
                "a block of band to half a block of gap is two thirds drawn");
        List<TelegraphLineGeometry.Span> dots = TelegraphLineGeometry.dashes(total,
                TelegraphLineGeometry.DOT_LENGTH, TelegraphLineGeometry.DOT_GAP);
        assertEquals(total * 3.0D / 7.0D, TelegraphLineGeometry.covered(dots), 0.5D);
        assertTrue(dots.size() > dashes.size(), "dots have to be thicker on the ground than dashes");
        // The pattern starts on a mark, so a closed contour never begins with a gap that
        // cannot be told from a hole in the floor.
        assertEquals(0.0D, dashes.getFirst().from(), EPSILON);
        // And nothing runs off the end of the contour it is drawn along.
        assertTrue(dashes.getLast().to() <= total + EPSILON);
    }

    @Test
    @DisplayName("an unbroken style covers the whole run")
    void aSolidRunIsWhollyDrawn() {
        assertEquals(12.0D, TelegraphLineGeometry.covered(
                List.of(new TelegraphLineGeometry.Span(0.0D, 12.0D))), EPSILON);
    }

    @Test
    @DisplayName("a contour drawing itself is half done at the halfway point")
    void traceIsHalfWayAtHalfProgress() {
        double total = 40.0D;
        List<TelegraphLineGeometry.Span> whole = List.of(new TelegraphLineGeometry.Span(0.0D, total));
        double half = total * TelegraphLineGeometry.reach(0.5F);
        assertEquals(20.0D, TelegraphLineGeometry.covered(
                TelegraphLineGeometry.clip(whole, half)), EPSILON);
        assertEquals(0.0D, TelegraphLineGeometry.covered(
                TelegraphLineGeometry.clip(whole, total * TelegraphLineGeometry.reach(0.0F))), EPSILON);
        assertEquals(total, TelegraphLineGeometry.covered(
                TelegraphLineGeometry.clip(whole, total * TelegraphLineGeometry.reach(1.0F))), EPSILON);
        // A dashed contour drawing itself keeps its gaps rather than closing them up.
        List<TelegraphLineGeometry.Span> dashes = TelegraphLineGeometry.dashes(total, 1.0D, 1.0D);
        assertTrue(TelegraphLineGeometry.covered(TelegraphLineGeometry.clip(dashes, half))
                < TelegraphLineGeometry.covered(dashes));
    }

    @Test
    @DisplayName("a warning with no end to count towards is shown whole")
    void noEndMeansNoGrowing() {
        assertEquals(1.0F, TelegraphLineGeometry.reach(-1.0F), 0.0F);
        assertEquals(1.0F, TelegraphLineGeometry.fadeAlpha(-1.0F), 0.0F);
    }

    @Test
    @DisplayName("a fading band only ever dims, and never out of sight")
    void fadeFallsAndStaysVisible() {
        float last = Float.MAX_VALUE;
        for (int step = 0; step <= 20; step++) {
            float alpha = TelegraphLineGeometry.fadeAlpha(step / 20.0F);
            assertTrue(alpha <= last + EPSILON, "the fade brightened at " + step);
            assertTrue(alpha >= TelegraphLineGeometry.FADE_END_ALPHA - EPSILON);
            assertTrue(alpha <= 1.0F);
            last = alpha;
        }
        assertEquals(1.0F, TelegraphLineGeometry.fadeAlpha(0.0F), EPSILON);
        assertEquals(TelegraphLineGeometry.FADE_END_ALPHA,
                TelegraphLineGeometry.fadeAlpha(1.0F), EPSILON);
    }

    @Test
    @DisplayName("a flood grows and gets to the edge exactly as the ability lands")
    void fillGrowsToTheEdge() {
        float last = -1.0F;
        for (int step = 0; step <= 20; step++) {
            float reach = TelegraphLineGeometry.reach(step / 20.0F);
            assertTrue(reach >= last, "the flood went backwards at " + step);
            assertTrue(reach >= 0.0F && reach <= 1.0F);
            last = reach;
        }
        assertEquals(0.0F, TelegraphLineGeometry.reach(0.0F), EPSILON);
        assertEquals(1.0F, TelegraphLineGeometry.reach(1.0F), EPSILON);
    }

    @Test
    @DisplayName("the pulse blinks faster as the hit nears, and never jumps back")
    void pulseCrowdsTowardsTheHit() {
        assertTrue(TelegraphLineGeometry.pulsePeriodTicks(1.0F)
                < TelegraphLineGeometry.pulsePeriodTicks(0.0F));
        assertEquals(TelegraphLineGeometry.PULSE_PERIOD_START_TICKS,
                TelegraphLineGeometry.pulsePeriodTicks(-1.0F), EPSILON);

        float windUp = 60.0F;
        float last = -1.0F;
        for (int step = 0; step <= 60; step++) {
            float progress = step / 60.0F;
            float phase = TelegraphLineGeometry.pulsePhase(progress, windUp, 0.0F);
            assertTrue(phase >= last, "the pulse jumped back at " + progress);
            last = phase;
            float alpha = TelegraphLineGeometry.pulseAlpha(phase);
            assertTrue(alpha >= TelegraphLineGeometry.PULSE_FLOOR_ALPHA - EPSILON
                    && alpha <= 1.0F + EPSILON, "the pulse left the band at " + alpha);
        }
        // The second half of a wind-up holds more blinks than the first, which is the point.
        float middle = TelegraphLineGeometry.pulsePhase(0.5F, windUp, 0.0F);
        assertTrue(last - middle > middle, "the blinks did not crowd towards the hit");
    }

    @Test
    @DisplayName("a flood of a shape is laid out from its middle outwards")
    void fillCellsGrowOutwards() {
        List<TelegraphLineGeometry.Cell> ring = TelegraphLineGeometry.fill(
                TelegraphShape.ring(Vec3.ZERO, 8.0D, RED, false));
        assertFalse(ring.isEmpty());
        double innermost = Double.MAX_VALUE;
        double outermost = 0.0D;
        for (TelegraphLineGeometry.Cell cell : ring) {
            assertTrue(cell.reach() > 0.0D && cell.reach() <= 1.0D);
            innermost = Math.min(innermost, cell.reach());
            outermost = Math.max(outermost, cell.reach());
            for (Vec3 corner : List.of(cell.a(), cell.b(), cell.c(), cell.d())) {
                assertTrue(flatDistance(Vec3.ZERO, corner) <= 8.0D + EPSILON,
                        "a flood spilled out past the shape it belongs to");
            }
        }
        assertTrue(innermost < 0.3D, "nothing of the flood starts near the middle");
        assertEquals(1.0D, outermost, EPSILON, "the flood never reaches the edge");

        // A lane floods away from the boss rather than out of its own middle.
        List<TelegraphLineGeometry.Cell> lane = TelegraphLineGeometry.fill(
                TelegraphShape.corridor(Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                        10.0D, 4.0D, 0.0D, RED, false));
        assertFalse(lane.isEmpty());
        for (TelegraphLineGeometry.Cell cell : lane) {
            assertEquals(cell.reach(), cell.c().x / 10.0D, EPSILON);
        }
        // And the run to a target has no inside to flood at all.
        assertTrue(TelegraphLineGeometry.fill(
                TelegraphShape.link(Vec3.ZERO, new Vec3(4.0D, 0.0D, 0.0D), RED, false)).isEmpty());
    }

    @Test
    @DisplayName("a shape says how far from the boss its furthest part lies")
    void reachIsMeasuredFromTheOwner() {
        assertEquals(14.0D, TelegraphShape.ring(new Vec3(10.0D, 0.0D, 0.0D), 4.0D, RED, false)
                .reachFrom(Vec3.ZERO), EPSILON);
        assertEquals(5.0D, TelegraphShape.link(Vec3.ZERO, new Vec3(3.0D, 4.0D, 0.0D), RED, false)
                .reachFrom(Vec3.ZERO), EPSILON);
        assertTrue(TelegraphShape.rectangle(-20.0D, -20.0D, 20.0D, 20.0D, 0.0D, RED, false)
                .reachFrom(Vec3.ZERO) > 28.0D);
    }

    private static boolean has(List<Vec3> points, double x, double z) {
        for (Vec3 point : points) {
            if (Math.abs(point.x - x) < EPSILON && Math.abs(point.z - z) < EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static double flatDistance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
