package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftCrystalPlacement.Spot;
import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalPoint;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a rift's crystals end up: the ring when the phase lists no zones, the zones when it does,
 * the hover over whatever floor is under each of them, and the zone that has no floor at all.
 *
 * <p>A crystal a hand's breadth inside the wall, or one hanging in the void over a hole in the
 * platform, is a task nobody can finish and a rift that fails on its limit every time - and
 * neither throws anything, so this is where it is caught.</p>
 */
class BossRiftCrystalPlacementTest {

    private static final double EPSILON = 1.0E-6D;
    /** The middle of a platform: its centre block at y 64, standing spot in the middle of it. */
    private static final double CENTRE_X = 100.5D;
    private static final double CENTRE_Y = 64.0D;
    private static final double CENTRE_Z = -60.5D;
    /** What a flat platform answers: the top of its floor block, one above the block itself. */
    private static final double FLOOR_TOP = CENTRE_Y + 1.0D;

    /** A platform with floor everywhere, at one height. */
    private static final BossRiftCrystalPlacement.FloorFinder FLAT = (x, z, fromY) -> FLOOR_TOP;
    /** The same platform with nothing under it past ten blocks from the middle. */
    private static final BossRiftCrystalPlacement.FloorFinder SMALL = (x, z, fromY) -> {
        double dx = x - CENTRE_X;
        double dz = z - CENTRE_Z;
        return dx * dx + dz * dz <= 100.0D ? FLOOR_TOP : null;
    };

    @Test
    @DisplayName("no zones: four crystals stand on the ring, evenly spaced, two blocks over the floor")
    void theRingIsEvenlySpaced() {
        BossRiftSettings rift = new BossRiftSettings();
        List<Spot> spots = plan(rift, FLAT);
        assertEquals(4, spots.size(), "the shipped default is four");
        for (Spot spot : spots) {
            double dx = spot.x() - CENTRE_X;
            double dz = spot.z() - CENTRE_Z;
            assertEquals(8.0D, Math.sqrt(dx * dx + dz * dz), EPSILON, "the shipped ring radius");
            assertEquals(FLOOR_TOP + 2.0D, spot.y(), EPSILON, "two blocks over the floor it found");
            assertEquals(FLOOR_TOP, spot.floorTop(), EPSILON);
            assertEquals(1.5D, spot.radius(), EPSILON, "the rift's own collecting radius");
            assertEquals(rift.getCrystalColor(), spot.color());
            assertEquals("", spot.block(), "a ring crystal is made of the rift's own block");
            assertEquals(0, spot.pointId(), "a ring crystal belongs to no zone");
        }
        // Evenly spaced means every pair of neighbours is the same distance apart.
        double first = distance(spots.get(0), spots.get(1));
        for (int i = 1; i < spots.size(); i++) {
            assertEquals(first, distance(spots.get(i), spots.get((i + 1) % spots.size())), EPSILON);
        }

        rift.setCrystalCount(6);
        rift.setCrystalRingRadius(12);
        rift.setCrystalHoverTenths(5);
        List<Spot> wider = plan(rift, FLAT);
        assertEquals(6, wider.size());
        assertEquals(12.0D, Math.hypot(wider.get(0).x() - CENTRE_X, wider.get(0).z() - CENTRE_Z), EPSILON);
        assertEquals(FLOOR_TOP + 0.5D, wider.get(0).y(), EPSILON, "half a block of hover");

        // The turn is what keeps two rifts from standing their crystals on exactly the same spots.
        List<Spot> turned = BossRiftCrystalPlacement.plan(rift, CENTRE_X, CENTRE_Y, CENTRE_Z, 1.0D, FLAT);
        assertTrue(Math.abs(turned.get(0).x() - wider.get(0).x()) > 0.1D, "a different turn, different spots");
    }

    @Test
    @DisplayName("a ring wider than the platform stands no crystals, which is a rift with nothing to gather")
    void aRingOffThePlatformStandsNothing() {
        BossRiftSettings rift = new BossRiftSettings();
        rift.setCrystalRingRadius(20);
        assertEquals(0, plan(rift, SMALL).size(), "every point of the ring is over the void");
        rift.setCrystalRingRadius(8);
        assertEquals(4, plan(rift, SMALL).size(), "and inside the platform they all stand");
    }

    @Test
    @DisplayName("zones replace the ring: offsets count from the platform, fixed ones are spots in the dimension")
    void zonesReplaceTheRing() {
        BossRiftSettings rift = new BossRiftSettings();
        BossRiftCrystalPoint offset = rift.getCrystalPoints().add();
        offset.setPosition(4, 0, -3);
        BossRiftCrystalPoint fixed = rift.getCrystalPoints().add();
        fixed.setCoordinateMode(BossRiftCrystalPoint.COORDINATE_FIXED);
        fixed.setPosition(200, 70, 300);
        BossRiftCrystalPoint dressed = rift.getCrystalPoints().add();
        dressed.setPosition(-5, 0, 0);
        dressed.setRadiusTenths(40);
        dressed.setBlockOverride("minecraft:diamond_block");
        dressed.setColorOverride(0x00FF00);

        List<Spot> spots = plan(rift, FLAT);
        assertEquals(3, spots.size(), "one crystal to a zone, and no ring");
        assertEquals(CENTRE_X + 4.0D, spots.get(0).x(), EPSILON);
        assertEquals(CENTRE_Z - 3.0D, spots.get(0).z(), EPSILON);
        assertEquals(FLOOR_TOP + 2.0D, spots.get(0).y(), EPSILON);
        assertEquals(offset.getPointId(), spots.get(0).pointId());
        assertEquals(200.5D, spots.get(1).x(), EPSILON, "a fixed zone stands in the middle of its block");
        assertEquals(300.5D, spots.get(1).z(), EPSILON);
        assertEquals(1.5D, spots.get(1).radius(), EPSILON, "a zone with no radius of its own takes the rift's");
        assertEquals(4.0D, spots.get(2).radius(), EPSILON, "and one with its own keeps it");
        assertEquals("minecraft:diamond_block", spots.get(2).block());
        assertEquals(0x00FF00, spots.get(2).color());
        assertEquals(rift.getCrystalColor(), spots.get(1).color(), "no colour of its own is the rift's");
    }

    @Test
    @DisplayName("a zone switched off gets no crystal, and one with no floor under it is left out")
    void zonesWithoutFloorOrSwitchAreLeftOut() {
        BossRiftSettings rift = new BossRiftSettings();
        BossRiftCrystalPoint off = rift.getCrystalPoints().add();
        off.setEnabled(false);
        off.setPosition(2, 0, 2);
        BossRiftCrystalPoint onPlatform = rift.getCrystalPoints().add();
        onPlatform.setPosition(3, 0, 0);
        BossRiftCrystalPoint overTheVoid = rift.getCrystalPoints().add();
        overTheVoid.setPosition(40, 0, 0);

        List<Spot> spots = plan(rift, SMALL);
        assertEquals(1, spots.size(), "the switched-off zone and the one over the void are both out");
        assertEquals(onPlatform.getPointId(), spots.get(0).pointId());

        // Every zone out is a rift with nothing to gather, which its manager reads as such.
        off.setEnabled(false);
        onPlatform.setPosition(40, 0, 0);
        assertEquals(0, plan(rift, SMALL).size());
    }

    @Test
    @DisplayName("the floor is looked for from two blocks above the zone, so a step up or down still counts")
    void theFloorIsLookedForFromAboveTheZone() {
        BossRiftSettings rift = new BossRiftSettings();
        rift.getCrystalPoints().add().setPosition(0, 0, 0);
        double[] seen = new double[1];
        BossRiftCrystalPlacement.plan(rift, CENTRE_X, CENTRE_Y, CENTRE_Z, 0.0D, (x, z, fromY) -> {
            seen[0] = fromY;
            return FLOOR_TOP;
        });
        assertEquals(CENTRE_Y + BossRiftCrystalPlacement.FLOOR_SEARCH_LIFT, seen[0], EPSILON);

        // A fixed zone is looked for from its own height rather than from the platform's.
        BossRiftSettings elsewhere = new BossRiftSettings();
        BossRiftCrystalPoint fixed = elsewhere.getCrystalPoints().add();
        fixed.setCoordinateMode(BossRiftCrystalPoint.COORDINATE_FIXED);
        fixed.setPosition(0, 120, 0);
        BossRiftCrystalPlacement.plan(elsewhere, CENTRE_X, CENTRE_Y, CENTRE_Z, 0.0D, (x, z, fromY) -> {
            seen[0] = fromY;
            return 121.0D;
        });
        assertEquals(122.0D, seen[0], EPSILON);
    }

    @Test
    @DisplayName("the turn and the bob come out of the tick, wrapped, and a nought is a crystal that hangs still")
    void theTurnAndTheBob() {
        assertEquals(0.0F, BossRiftCrystalPlacement.spinAngle(0.0D, 3), EPSILON);
        assertEquals(30.0F, BossRiftCrystalPlacement.spinAngle(10.0D, 3), EPSILON);
        assertEquals(0.0F, BossRiftCrystalPlacement.spinAngle(120.0D, 3), EPSILON, "a whole turn is none");
        assertEquals(15.0F, BossRiftCrystalPlacement.spinAngle(125.0D, 3), EPSILON, "and it keeps going round");
        assertTrue(BossRiftCrystalPlacement.spinAngle(1_000_000.0D, 7) < 360.0F, "never a number too big to draw");
        assertEquals(0.0F, BossRiftCrystalPlacement.spinAngle(50.0D, 0), EPSILON, "nought degrees a tick is still");

        assertEquals(0.0D, BossRiftCrystalPlacement.bobOffset(0.0D, 3, 40), EPSILON);
        assertEquals(0.3D, BossRiftCrystalPlacement.bobOffset(10.0D, 3, 40), EPSILON, "a quarter through, at the top");
        assertEquals(-0.3D, BossRiftCrystalPlacement.bobOffset(30.0D, 3, 40), EPSILON, "and at the bottom later");
        assertEquals(0.0D, BossRiftCrystalPlacement.bobOffset(40.0D, 3, 40), EPSILON, "back where it began");
        assertEquals(0.0D, BossRiftCrystalPlacement.bobOffset(10.0D, 0, 40), EPSILON, "no amplitude is level");
    }

    private static List<Spot> plan(BossRiftSettings rift, BossRiftCrystalPlacement.FloorFinder floors) {
        return BossRiftCrystalPlacement.plan(rift, CENTRE_X, CENTRE_Y, CENTRE_Z, 0.0D, floors);
    }

    private static double distance(Spot one, Spot two) {
        return Math.hypot(one.x() - two.x(), one.z() - two.z());
    }
}
