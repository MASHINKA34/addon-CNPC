package com.goodbird.cnpcgeckoaddon.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        ZoneSelection selection = ZoneSelection.box();
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
        ZoneSelection beforeAny = ZoneSelection.box();
        beforeAny.cancel();
        assertEquals(ZoneSelection.Stage.CANCELLED, beforeAny.stage());
        assertFalse(beforeAny.isActive());
        assertFalse(beforeAny.click(A, Direction.UP, 0.0F), "a cancelled selection takes no more clicks");
        assertNull(beforeAny.result());
        assertNull(beforeAny.hintKey());

        ZoneSelection halfway = ZoneSelection.box();
        halfway.click(A, Direction.UP, 0.0F);
        halfway.cancel();
        assertEquals(ZoneSelection.Stage.CANCELLED, halfway.stage());
        assertFalse(halfway.click(B, Direction.UP, 0.0F));
        assertNull(halfway.result(), "the first corner alone is never handed on as a box");

        ZoneSelection point = ZoneSelection.point();
        point.cancel();
        assertEquals(ZoneSelection.Stage.CANCELLED, point.stage());
        assertFalse(point.click(A, Direction.UP, 90.0F));
        assertNull(point.pointResult());
    }

    @Test
    @DisplayName("a finished selection keeps its result through a late cancel and a stray click")
    void doneIsFinal() {
        ZoneSelection selection = ZoneSelection.box();
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
        ZoneSelection selection = ZoneSelection.point();
        assertTrue(selection.isPoint());
        assertEquals(ZoneSelection.Stage.POINT, selection.stage());
        assertEquals(ZoneSelection.HINT_POINT, selection.hintKey());
        assertNull(selection.liveBox(A), "a point has no box to outline");

        assertTrue(selection.click(A, Direction.UP, 45.0F), "one click finishes a point");
        assertEquals(ZoneSelection.Stage.DONE, selection.stage());
        ZoneSelection.Point point = selection.pointResult();
        assertEquals(A, point.block());
        assertEquals(Direction.UP, point.face());
        assertEquals(A.above(), point.inFront(), "clicked on a floor, the point stands on it");
        assertNull(selection.result(), "a point is not a box");

        ZoneSelection wall = ZoneSelection.point();
        wall.click(A, Direction.EAST, 0.0F);
        assertEquals(A.east(), wall.pointResult().inFront(), "clicked on a wall, it is the block in front");
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
        ZoneSelection selection = ZoneSelection.box();
        assertNull(selection.liveBox(B), "nothing to outline before the first corner");
        selection.click(A, Direction.UP, 0.0F);
        assertEquals(ZoneSelection.normalize(A, B), selection.liveBox(B));
        assertEquals(new ZoneSelection.Box(A, A), selection.liveBox(null),
                "with the crosshair on no block, the outline is the first corner alone");
    }

    private static ZoneSelection.Box select(BlockPos first, BlockPos second) {
        ZoneSelection selection = ZoneSelection.box();
        selection.click(first, Direction.UP, 0.0F);
        selection.click(second, Direction.UP, 0.0F);
        return selection.result();
    }

    private static float clickPoint(float yRot) {
        ZoneSelection selection = ZoneSelection.point();
        selection.click(A, Direction.UP, yRot);
        return selection.pointResult().yaw();
    }
}
