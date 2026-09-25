package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clicks of "select in the world" without a game: which click does what, what comes out,
 * and what a change of mind leaves behind. A wrong answer here is a zone written a block off,
 * or a corner that silently becomes the other corner, and neither shows until a fight.
 */
class ZoneSelectionTest {

    private static final BlockPos A = new BlockPos(10, 64, -5);
    private static final BlockPos B = new BlockPos(-3, 70, 12);

    @Test
    @DisplayName("a box takes a first corner, then a second, then is done")
    void firstCornerThenSecondThenDone() {
        ZoneSelection selection = ZoneSelection.box(ZoneSelection.Pick.CLICKED);
        assertTrue(selection.isActive());
        assertFalse(selection.isPoint());
        assertEquals(ZoneSelection.Stage.FIRST_CORNER, selection.stage());
        assertEquals(ZoneSelection.HINT_FIRST, selection.hintKey());
        assertNull(selection.firstCorner());

        assertFalse(selection.click(A, Direction.UP, 0.0F), "one corner does not finish a box");
        assertEquals(ZoneSelection.Stage.SECOND_CORNER, selection.stage());
        assertEquals(ZoneSelection.HINT_SECOND, selection.hintKey());
        assertEquals(A, selection.firstCorner());
        assertNull(selection.result(), "no box until the second corner is in");

        assertTrue(selection.click(B, Direction.NORTH, 0.0F), "the second corner finishes it");
        assertEquals(ZoneSelection.Stage.DONE, selection.stage());
        assertFalse(selection.isActive());
        assertNull(selection.hintKey(), "nothing is waiting any more");
        assertEquals(new ZoneSelection.Box(new BlockPos(-3, 64, -5), new BlockPos(10, 70, 12)),
                selection.result());
    }

    @Test
    @DisplayName("the corners come out as the lowest and the highest on every axis, in either order")
    void cornersAreNormalised() {
        ZoneSelection.Box forward = select(A, B);
        ZoneSelection.Box backward = select(B, A);
        assertEquals(forward, backward, "clicking the corners the other way round is the same box");
        assertEquals(new BlockPos(-3, 64, -5), forward.min());
        assertEquals(new BlockPos(10, 70, 12), forward.max());

        // Mixed per axis: the first click is lowest on one axis and highest on the others.
        ZoneSelection.Box mixed = select(new BlockPos(5, 80, 1), new BlockPos(7, 60, -1));
        assertEquals(new BlockPos(5, 60, -1), mixed.min());
        assertEquals(new BlockPos(7, 80, 1), mixed.max());

        ZoneSelection.Box single = select(A, A);
        assertEquals(A, single.min(), "the same block twice is a one-block box");
        assertEquals(A, single.max());
    }

    @Test
    @DisplayName("a cancel leaves nothing behind, at either corner, and later clicks change nothing")
    void cancelLeavesNoResult() {
        ZoneSelection beforeAny = ZoneSelection.box(ZoneSelection.Pick.CLICKED);
        beforeAny.cancel();
        assertEquals(ZoneSelection.Stage.CANCELLED, beforeAny.stage());
        assertFalse(beforeAny.isActive());
        assertFalse(beforeAny.click(A, Direction.UP, 0.0F), "a cancelled selection takes no more clicks");
        assertNull(beforeAny.result());
        assertNull(beforeAny.hintKey());

        ZoneSelection halfway = ZoneSelection.box(ZoneSelection.Pick.CLICKED);
        halfway.click(A, Direction.UP, 0.0F);
        halfway.cancel();
        assertEquals(ZoneSelection.Stage.CANCELLED, halfway.stage());
        assertFalse(halfway.click(B, Direction.UP, 0.0F));
        assertNull(halfway.result(), "the first corner alone is never handed on as a box");

        ZoneSelection point = ZoneSelection.point(ZoneSelection.Pick.IN_FRONT);
        point.cancel();
        assertEquals(ZoneSelection.Stage.CANCELLED, point.stage());
        assertFalse(point.click(A, Direction.UP, 90.0F));
        assertNull(point.pointResult());
    }

    @Test
    @DisplayName("a finished selection keeps its result through a late cancel and a stray click")
    void doneIsFinal() {
        ZoneSelection selection = ZoneSelection.box(ZoneSelection.Pick.CLICKED);
        selection.click(A, Direction.UP, 0.0F);
        selection.click(B, Direction.UP, 0.0F);
        ZoneSelection.Box box = selection.result();
        selection.cancel();
        assertEquals(ZoneSelection.Stage.DONE, selection.stage());
        assertFalse(selection.click(new BlockPos(100, 100, 100), Direction.UP, 0.0F));
        assertEquals(box, selection.result());
    }

    @Test
    @DisplayName("a point is one click: the block, its face, and the block in front of that face")
    void pointIsOneClick() {
        ZoneSelection selection = ZoneSelection.point(ZoneSelection.Pick.IN_FRONT);
        assertTrue(selection.isPoint());
        assertEquals(ZoneSelection.Stage.POINT, selection.stage());
        assertEquals(ZoneSelection.HINT_POINT, selection.hintKey());
        assertNull(selection.liveBox(A, Direction.UP), "a point has no box to outline");

        assertTrue(selection.click(A, Direction.UP, 45.0F), "one click finishes a point");
        assertEquals(ZoneSelection.Stage.DONE, selection.stage());
        ZoneSelection.Point point = selection.pointResult();
        assertEquals(A, point.block());
        assertEquals(Direction.UP, point.face());
        assertEquals(A.above(), point.inFront(), "clicked on a floor, the point stands on it");
        assertNull(selection.result(), "a point is not a box");

        ZoneSelection wall = ZoneSelection.point(ZoneSelection.Pick.IN_FRONT);
        wall.click(A, Direction.EAST, 0.0F);
        assertEquals(A.east(), wall.pointResult().inFront(), "clicked on a wall, it is the block in front");
    }

    @Test
    @DisplayName("a point's marker stands on the block its editor writes: the one in front, or the one clicked")
    void pointMarkerStandsOnTheBlockWritten() {
        ZoneSelection spot = ZoneSelection.point(ZoneSelection.Pick.IN_FRONT);
        assertEquals(A.above(), spot.picked(A, Direction.UP), "a spot's marker stands where the feet go");
        spot.click(A, Direction.UP, 0.0F);
        assertEquals(spot.pointResult().inFront(), spot.picked(A, Direction.UP));

        // A launch pad writes the block clicked: on a floor its marker is the floor block, not the one over it.
        ZoneSelection landing = ZoneSelection.point(ZoneSelection.Pick.CLICKED);
        assertEquals(A, landing.picked(A, Direction.UP));
        assertEquals(A, landing.picked(A, Direction.EAST), "on a wall too, the block clicked");
        landing.click(A, Direction.UP, 0.0F);
        assertEquals(landing.pointResult().block(), landing.picked(A, Direction.UP));
    }

    @Test
    @DisplayName("the yaw is the look, wrapped into -180..180 and rounded to a degree")
    void yawIsTheLook() {
        assertEquals(45.0F, clickPoint(45.0F));
        assertEquals(37.0F, clickPoint(37.4F));
        assertEquals(38.0F, clickPoint(37.6F));
        assertEquals(-170.0F, clickPoint(190.0F), "past half a turn is the other way round");
        assertEquals(170.0F, clickPoint(-190.0F));
        assertEquals(1.0F, clickPoint(720.6F), "whole turns are dropped");
        assertEquals(-90.0F, clickPoint(-450.0F));
        assertEquals(-180.0F, clickPoint(-180.0F));
        float nearlyHalf = clickPoint(179.6F);
        assertTrue(nearlyHalf >= -180.0F && nearlyHalf <= 180.0F,
                "a rounded yaw stays inside what the facing fields take: " + nearlyHalf);
    }

    @Test
    @DisplayName("between the clicks the outline runs from the first corner to the block aimed at")
    void liveBoxFollowsTheAim() {
        ZoneSelection selection = ZoneSelection.box(ZoneSelection.Pick.CLICKED);
        assertNull(selection.liveBox(B, Direction.UP), "nothing to outline before the first corner");
        selection.click(A, Direction.UP, 0.0F);
        assertEquals(ZoneSelection.normalize(A, B), selection.liveBox(B, Direction.UP));
        assertEquals(new ZoneSelection.Box(A, A), selection.liveBox(null, null),
                "with the crosshair on no block, the outline is the first corner alone");
    }

    @Test
    @DisplayName("a zone somebody stands in is cornered on the blocks in front of the faces clicked")
    void standingZoneTakesTheBlocksInFront() {
        BlockPos floorA = new BlockPos(10, 64, -5);
        BlockPos floorB = new BlockPos(-3, 64, 12);
        ZoneSelection.Box onFloor = select(ZoneSelection.Pick.IN_FRONT, floorA, Direction.UP, floorB, Direction.UP);
        assertEquals(new BlockPos(-3, 65, -5), onFloor.min(), "clicked on a floor, a corner is a block higher");
        assertEquals(new BlockPos(10, 65, 12), onFloor.max());

        // The fight's own test - the feet inside, the top face of the highest block outside - on
        // somebody standing on the floor clicked: inside this box, and not in one of the floor itself.
        Vec3 feet = new Vec3(0.5D, 65.0D, 0.5D);
        assertTrue(ZoneCoordinates.blockBox(onFloor.min(), onFloor.max()).contains(feet));
        ZoneSelection.Box floorItself = select(ZoneSelection.Pick.CLICKED, floorA, Direction.UP, floorB, Direction.UP);
        assertFalse(ZoneCoordinates.blockBox(floorItself.min(), floorItself.max()).contains(feet));

        // Under a ceiling, the block below it; on a wall from inside, the block before the wall.
        ZoneSelection.Box room = select(ZoneSelection.Pick.IN_FRONT,
                new BlockPos(0, 70, 0), Direction.DOWN, new BlockPos(8, 66, 3), Direction.WEST);
        assertEquals(new BlockPos(0, 66, 0), room.min());
        assertEquals(new BlockPos(7, 69, 3), room.max());
    }

    @Test
    @DisplayName("a zone made of the blocks it names, a vent, is cornered on the blocks clicked, unshifted")
    void blockZoneTakesTheBlocksClicked() {
        ZoneSelection.Box floor = select(ZoneSelection.Pick.CLICKED,
                new BlockPos(10, 64, -5), Direction.UP, new BlockPos(-3, 64, 12), Direction.UP);
        assertEquals(new BlockPos(-3, 64, -5), floor.min(), "a vent in the floor is the floor blocks");
        assertEquals(new BlockPos(10, 64, 12), floor.max());

        ZoneSelection.Box wall = select(ZoneSelection.Pick.CLICKED,
                new BlockPos(8, 66, 3), Direction.WEST, new BlockPos(8, 68, 6), Direction.WEST);
        assertEquals(new BlockPos(8, 66, 3), wall.min(), "a vent in a wall is the wall blocks");
        assertEquals(new BlockPos(8, 68, 6), wall.max());
    }

    @Test
    @DisplayName("the outline takes its corners by the rule the clicks go by, and is the box they write")
    void liveBoxFollowsThePickRule() {
        ZoneSelection selection = ZoneSelection.box(ZoneSelection.Pick.IN_FRONT);
        assertEquals(A.above(), selection.picked(A, Direction.UP),
                "before the first click, the block it would make a corner");
        selection.click(A, Direction.UP, 0.0F);
        assertEquals(A.above(), selection.firstCorner());
        assertEquals(new ZoneSelection.Box(A.above(), A.above()), selection.liveBox(null, null));

        ZoneSelection.Box outlined = selection.liveBox(B, Direction.NORTH);
        assertEquals(ZoneSelection.normalize(A.above(), B.north()), outlined);
        selection.click(B, Direction.NORTH, 0.0F);
        assertEquals(outlined, selection.result(), "the outline before the second click is the box it writes");
    }

    private static ZoneSelection.Box select(BlockPos first, BlockPos second) {
        return select(ZoneSelection.Pick.CLICKED, first, Direction.UP, second, Direction.UP);
    }

    private static ZoneSelection.Box select(ZoneSelection.Pick corners, BlockPos first, Direction firstFace,
                                            BlockPos second, Direction secondFace) {
        ZoneSelection selection = ZoneSelection.box(corners);
        selection.click(first, firstFace, 0.0F);
        selection.click(second, secondFace, 0.0F);
        return selection.result();
    }

    private static float clickPoint(float yRot) {
        ZoneSelection selection = ZoneSelection.point(ZoneSelection.Pick.IN_FRONT);
        selection.click(A, Direction.UP, yRot);
        return selection.pointResult().yaw();
    }
}
