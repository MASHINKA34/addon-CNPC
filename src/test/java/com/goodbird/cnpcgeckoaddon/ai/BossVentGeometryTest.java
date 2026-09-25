package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import com.goodbird.cnpcgeckoaddon.data.BossVentZoneList;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphLineGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a vent reaches and which way it goes, checked without a world.
 *
 * <p>Every number here is a block a player stands on or does not: a volume one block short is a
 * flame that visibly licks somebody it does not burn, and a wall pushing the wrong way out of the
 * east wall throws the party into the brickwork.</p>
 */
class BossVentGeometryTest {

    private static final double EPSILON = 1.0E-9D;

    /** A player's box, feet at {@code (x, y, z)}. */
    private static AABB player(double x, double y, double z) {
        return new AABB(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D);
    }

    private static void assertBox(AABB expected, AABB actual) {
        assertEquals(expected.minX, actual.minX, EPSILON, "minX of " + actual);
        assertEquals(expected.minY, actual.minY, EPSILON, "minY of " + actual);
        assertEquals(expected.minZ, actual.minZ, EPSILON, "minZ of " + actual);
        assertEquals(expected.maxX, actual.maxX, EPSILON, "maxX of " + actual);
        assertEquals(expected.maxY, actual.maxY, EPSILON, "maxY of " + actual);
        assertEquals(expected.maxZ, actual.maxZ, EPSILON, "maxZ of " + actual);
    }

    @Test
    @DisplayName("each of the six faces fires its own way: up, down, and into the arena out of each wall")
    void theSixFacesFireTheirOwnWay() {
        assertEquals(new Vec3(0, 1, 0), BossVentGeometry.dir(BossVentZone.FACE_FLOOR));
        assertEquals(new Vec3(0, -1, 0), BossVentGeometry.dir(BossVentZone.FACE_CEILING));
        assertEquals(new Vec3(0, 0, 1), BossVentGeometry.dir(BossVentZone.FACE_NORTH), "the north wall fires south");
        assertEquals(new Vec3(0, 0, -1), BossVentGeometry.dir(BossVentZone.FACE_SOUTH), "the south wall fires north");
        assertEquals(new Vec3(1, 0, 0), BossVentGeometry.dir(BossVentZone.FACE_WEST), "the west wall fires east");
        assertEquals(new Vec3(-1, 0, 0), BossVentGeometry.dir(BossVentZone.FACE_EAST), "the east wall fires west");
        assertEquals(Direction.Axis.Y, BossVentGeometry.axis(BossVentZone.FACE_CEILING));
        assertEquals(Direction.Axis.Z, BossVentGeometry.axis(BossVentZone.FACE_SOUTH));
        assertEquals(Direction.Axis.X, BossVentGeometry.axis(BossVentZone.FACE_EAST));
        for (int face = BossVentZone.FACE_FLOOR; face <= BossVentZone.FACE_EAST; face++) {
            assertEquals(1.0D, BossVentGeometry.dir(face).length(), EPSILON, "face " + face + " is one block long");
        }
    }

    @Test
    @DisplayName("a floor vent acts on the column over it, as tall as its reach")
    void aFloorVentReachesUp() {
        AABB grate = new AABB(0, 64, 0, 3, 65, 3);
        assertEquals(65.0D, BossVentGeometry.faceCoordinate(grate, BossVentZone.FACE_FLOOR), EPSILON);
        assertBox(new AABB(0, 65, 0, 3, 69, 3), BossVentGeometry.volume(grate, BossVentZone.FACE_FLOOR, 4));
        assertEquals(69.0D, BossVentGeometry.farCoordinate(grate, BossVentZone.FACE_FLOOR, 4), EPSILON);
    }

    @Test
    @DisplayName("a ceiling vent acts on the column under it, down to its reach")
    void aCeilingVentReachesDown() {
        AABB grate = new AABB(0, 70, 0, 3, 71, 3);
        assertEquals(70.0D, BossVentGeometry.faceCoordinate(grate, BossVentZone.FACE_CEILING), EPSILON);
        assertBox(new AABB(0, 66, 0, 3, 70, 3), BossVentGeometry.volume(grate, BossVentZone.FACE_CEILING, 4));
        assertEquals(66.0D, BossVentGeometry.farCoordinate(grate, BossVentZone.FACE_CEILING, 4), EPSILON);
    }

    @Test
    @DisplayName("a wall vent acts on the slab of air in front of it, out into the arena")
    void aWallVentReachesIntoTheArena() {
        // A slit three wide and three high in the north wall, the wall itself at z = -1.
        AABB north = new AABB(0, 64, -1, 3, 67, 0);
        assertBox(new AABB(0, 64, 0, 3, 67, 6), BossVentGeometry.volume(north, BossVentZone.FACE_NORTH, 6));
        assertEquals(6.0D, BossVentGeometry.farCoordinate(north, BossVentZone.FACE_NORTH, 6), EPSILON);
        AABB south = new AABB(0, 64, 10, 3, 67, 11);
        assertBox(new AABB(0, 64, 4, 3, 67, 10), BossVentGeometry.volume(south, BossVentZone.FACE_SOUTH, 6));
        assertEquals(4.0D, BossVentGeometry.farCoordinate(south, BossVentZone.FACE_SOUTH, 6), EPSILON);
        AABB west = new AABB(-1, 64, 0, 0, 67, 3);
        assertBox(new AABB(0, 64, 0, 6, 67, 3), BossVentGeometry.volume(west, BossVentZone.FACE_WEST, 6));
        AABB east = new AABB(10, 64, 0, 11, 67, 3);
        assertBox(new AABB(4, 64, 0, 10, 67, 3), BossVentGeometry.volume(east, BossVentZone.FACE_EAST, 6));
        assertEquals(4.0D, BossVentGeometry.farCoordinate(east, BossVentZone.FACE_EAST, 6), EPSILON);
    }

    @Test
    @DisplayName("whoever reaches into the volume is in it; touching it from outside or standing past it is not")
    void membershipIsTheBodyReachingIn() {
        AABB north = new AABB(0, 64, -1, 3, 67, 0);
        AABB volume = BossVentGeometry.volume(north, BossVentZone.FACE_NORTH, 6);
        assertTrue(BossVentGeometry.inVolume(volume, player(1.5, 64, 2)), "standing in the stream");
        assertTrue(BossVentGeometry.inVolume(volume, player(1.5, 64, 6.1)), "half out of its far side is still in it");
        assertFalse(BossVentGeometry.inVolume(volume, player(1.5, 64, 6.5)), "clear of the far side");
        assertFalse(BossVentGeometry.inVolume(volume, new AABB(3, 64, 1.7, 3.6, 65.8, 2.3)),
                "beside the slit, the box only touching");
        assertTrue(BossVentGeometry.inVolume(volume, player(1.5, 66.5, 2)), "a head in the stream is in it");
        // A floor vent: standing right on the grate is in the column over it.
        AABB grate = new AABB(0, 64, 0, 3, 65, 3);
        AABB column = BossVentGeometry.volume(grate, BossVentZone.FACE_FLOOR, 4);
        assertTrue(BossVentGeometry.inVolume(column, player(1.5, 65, 1.5)));
        assertFalse(BossVentGeometry.inVolume(column, player(1.5, 69, 1.5)), "feet on the far side is out of it");
    }

    @Test
    @DisplayName("a wall pushes along its way, and a floor vent's lift goes on top of the push and nowhere else")
    void thePushGoesAlongTheWayTheVentFires() {
        assertEquals(new Vec3(0, 0, 0.8), BossVentGeometry.push(BossVentZone.FACE_NORTH, 0.8, 0.2));
        assertEquals(new Vec3(-0.8, 0, 0), BossVentGeometry.push(BossVentZone.FACE_EAST, 0.8, 0.2));
        Vec3 up = BossVentGeometry.push(BossVentZone.FACE_FLOOR, 0.8, 0.2);
        assertEquals(0.0D, up.x, EPSILON);
        assertEquals(1.0D, up.y, EPSILON, "the push and the lift together");
        assertEquals(0.0D, up.z, EPSILON);
        assertEquals(new Vec3(0, -0.8, 0), BossVentGeometry.push(BossVentZone.FACE_CEILING, 0.8, 0.2),
                "a ceiling's wall has no lift: it is pushing down");
    }

    @Test
    @DisplayName("a body rests with its leading side on the far side, or against the face when it is longer than the reach")
    void theFarSideIsWhereAWallPins() {
        AABB north = new AABB(0, 64, -1, 3, 67, 0);
        // Near side 1.7 out from the face, 0.6 long: 3.7 more to go to rest on the far side at 6.
        AABB standing = player(1.5, 64, 2);
        assertEquals(1.7D, BossVentGeometry.start(north, BossVentZone.FACE_NORTH, standing), 1.0E-6D);
        assertEquals(3.7D, BossVentGeometry.remaining(north, BossVentZone.FACE_NORTH, 6, standing), 1.0E-6D);
        assertFalse(BossVentGeometry.atFarSide(north, BossVentZone.FACE_NORTH, 6, standing));
        AABB resting = player(1.5, 64, 5.7);
        assertEquals(0.0D, BossVentGeometry.remaining(north, BossVentZone.FACE_NORTH, 6, resting), 1.0E-6D);
        assertTrue(BossVentGeometry.atFarSide(north, BossVentZone.FACE_NORTH, 6, resting));
        AABB past = player(1.5, 64, 6.1);
        assertTrue(BossVentGeometry.remaining(north, BossVentZone.FACE_NORTH, 6, past) < 0.0D, "past it reads negative");
        assertTrue(BossVentGeometry.atFarSide(north, BossVentZone.FACE_NORTH, 6, past));

        // The east wall counts the other way round the axis.
        AABB east = new AABB(10, 64, 0, 11, 67, 3);
        AABB beside = player(9.0, 64, 1.5);
        assertEquals(0.7D, BossVentGeometry.start(east, BossVentZone.FACE_EAST, beside), 1.0E-6D);
        assertEquals(4.7D, BossVentGeometry.remaining(east, BossVentZone.FACE_EAST, 6, beside), 1.0E-6D);

        // A floor vent lifts a player until their head is at the top of the column.
        AABB grate = new AABB(0, 64, 0, 3, 65, 3);
        AABB onGrate = player(1.5, 65, 1.5);
        assertEquals(2.2D, BossVentGeometry.remaining(grate, BossVentZone.FACE_FLOOR, 4, onGrate), 1.0E-6D);
        // A column shorter than the player is: they rest on the grate itself, never pushed into it.
        assertEquals(0.0D, BossVentGeometry.restStart(1, 1.8D), EPSILON);
        assertEquals(0.0D, BossVentGeometry.remaining(grate, BossVentZone.FACE_FLOOR, 1, onGrate), 1.0E-6D);
        // A ceiling vent presses down until the feet are on its far side.
        AABB ceiling = new AABB(0, 70, 0, 3, 71, 3);
        AABB under = player(1.5, 67.5, 1.5);
        assertEquals(1.5D, BossVentGeometry.remaining(ceiling, BossVentZone.FACE_CEILING, 4, under), 1.0E-6D);
    }

    @Test
    @DisplayName("points over the vent lie on the plane asked for, across the whole cross-section")
    void planePointsSpanTheCrossSection() {
        AABB north = new AABB(0, 64, -1, 3, 67, 0);
        assertEquals(new Vec3(0, 64, 0.1), BossVentGeometry.planePoint(north, BossVentZone.FACE_NORTH, 0.1, 0, 0));
        assertEquals(new Vec3(3, 67, 6), BossVentGeometry.planePoint(north, BossVentZone.FACE_NORTH, 6, 1, 1));
        assertEquals(new Vec3(1.5, 65.5, 6), BossVentGeometry.planeCentre(north, BossVentZone.FACE_NORTH, 6));
        AABB grate = new AABB(0, 64, 0, 3, 65, 3);
        assertEquals(new Vec3(1.5, 65, 1.5), BossVentGeometry.planeCentre(grate, BossVentZone.FACE_FLOOR, 0));
        AABB ceiling = new AABB(0, 70, 0, 3, 71, 3);
        assertEquals(new Vec3(1.5, 66, 1.5), BossVentGeometry.planeCentre(ceiling, BossVentZone.FACE_CEILING, 4),
                "the far side of a ceiling vent is under it");
        AABB east = new AABB(10, 64, 0, 11, 67, 3);
        assertEquals(new Vec3(8, 64, 3), BossVentGeometry.planePoint(east, BossVentZone.FACE_EAST, 2, 1, 0));
    }

    @Test
    @DisplayName("a wall's front comes out to the far side over its travel and stands there")
    void theWallFrontTravelsThenStands() {
        assertEquals(1.2D, BossVentGeometry.wallFront(6, 0, 5), EPSILON, "a fifth of the way on the first tick");
        assertEquals(6.0D, BossVentGeometry.wallFront(6, 4, 5), EPSILON, "at the far side on the fifth");
        assertEquals(6.0D, BossVentGeometry.wallFront(6, 30, 5), EPSILON, "and standing there after");
        assertEquals(6.0D, BossVentGeometry.wallFront(6, 0, 0), EPSILON, "no travel is there at once");
    }

    @Test
    @DisplayName("a vent's box is counted from the arena or fixed, corners either way round, and cut to the build height")
    void theBoxResolvesLikeAPlatform() {
        BossVentZone zone = new BossVentZoneList().add();
        assertNotNull(zone);
        zone.setCorner1(2, -1, 5);
        zone.setCorner2(-1, 1, 3);
        BlockPos home = new BlockPos(100, 64, 200);
        assertBox(new AABB(99, 63, 203, 103, 66, 206), BossVentGeometry.zoneBox(zone, home, -64, 320));
        zone.setCoordinateMode(BossVentZone.COORDINATE_FIXED);
        assertBox(new AABB(-1, -1, 3, 3, 2, 6), BossVentGeometry.zoneBox(zone, home, -64, 320));
        // The part under the world is cut away.
        assertBox(new AABB(-1, 0, 3, 3, 2, 6), BossVentGeometry.zoneBox(zone, home, 0, 320));
        zone.setCorner1(0, 400, 0);
        zone.setCorner2(0, 500, 0);
        assertNull(BossVentGeometry.zoneBox(zone, home, -64, 320), "a vent wholly over the world is none");
    }

    @Test
    @DisplayName("the particle budget is shared out in proportion and every cue that asked keeps at least one")
    void theBudgetIsSharedOut() {
        assertArrayEquals(new int[]{8, 2}, BossVentGeometry.shareBudget(48, 8, 2), "within the budget nothing is cut");
        assertArrayEquals(new int[]{40, 10}, BossVentGeometry.shareBudget(50, 80, 20), "past it, in proportion");
        assertArrayEquals(new int[]{8, 1}, BossVentGeometry.shareBudget(9, 100, 1),
                "the small cue keeps one rather than rounding to nothing");
        int[] shared = BossVentGeometry.shareBudget(10, 30, 30, 30);
        int total = 0;
        for (int count : shared) {
            total += count;
        }
        assertTrue(total <= 10, "never over the budget: " + total);
        assertArrayEquals(new int[]{0, 0}, BossVentGeometry.shareBudget(48, 0, -3), "nothing asked is nothing given");
    }

    @Test
    @DisplayName("a box standing in the air is outlined by its twelve edges, each running the length of one axis")
    void theOutlineIsTwelveEdges() {
        AABB volume = new AABB(0, 64, 0, 3, 67, 6);
        List<TelegraphLineGeometry.Edge> edges = TelegraphLineGeometry.boxEdges(volume);
        assertEquals(12, edges.size());
        Set<Vec3> corners = new HashSet<>();
        int[] perAxis = new int[3];
        for (TelegraphLineGeometry.Edge edge : edges) {
            Vec3 span = edge.to().subtract(edge.from());
            int moving = (span.x != 0 ? 1 : 0) + (span.y != 0 ? 1 : 0) + (span.z != 0 ? 1 : 0);
            assertEquals(1, moving, "an edge runs along exactly one axis: " + edge);
            if (span.x != 0) {
                assertEquals(3.0D, span.x, EPSILON);
                perAxis[0]++;
            } else if (span.y != 0) {
                assertEquals(3.0D, span.y, EPSILON);
                perAxis[1]++;
            } else {
                assertEquals(6.0D, span.z, EPSILON);
                perAxis[2]++;
            }
            corners.add(edge.from());
            corners.add(edge.to());
        }
        assertArrayEquals(new int[]{4, 4, 4}, perAxis, "four edges along each axis");
        assertEquals(8, corners.size(), "and between them every corner of the box");
    }

    @Test
    @DisplayName("a resolved vent's volume and way are its face's")
    void aResolvedVentCarriesItsGeometry() {
        BossVentGeometry.Vent vent = new BossVentGeometry.Vent(7, new AABB(0, 64, -1, 3, 67, 0),
                BossVentZone.FACE_NORTH, 6, 1, 0, 1);
        assertBox(new AABB(0, 64, 0, 3, 67, 6), vent.volume());
        assertEquals(new Vec3(0, 0, 1), vent.dir());
    }
}
