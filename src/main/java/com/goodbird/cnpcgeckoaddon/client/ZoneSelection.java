package com.goodbird.cnpcgeckoaddon.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The clicks behind "select in the world", with nothing of the game around them.
 *
 * <p>A box takes two blocks and comes back as its lowest and its highest corner, whichever
 * order the builder clicked them in: every box a boss keeps is read with both corners
 * inclusive and in either order, so handing it the minimum and the maximum is the one form
 * nobody has to think about. A point takes one block, the face it was clicked on and the way
 * the builder was looking at the time. Kept apart from the client so the order of the clicks,
 * the swap of the corners and the angle can be checked without a game running.</p>
 */
public final class ZoneSelection {

    public static final String HINT_FIRST = "cnpcgeckoaddon.zone.hint_first";
    public static final String HINT_SECOND = "cnpcgeckoaddon.zone.hint_second";
    public static final String HINT_POINT = "cnpcgeckoaddon.zone.hint_point";

    /** Where a selection has got to. */
    public enum Stage {
        /** A box waiting for its first corner. */
        FIRST_CORNER,
        /** A box with one corner, waiting for the other. */
        SECOND_CORNER,
        /** A point waiting for its one click. */
        POINT,
        /** Finished; the result is ready. */
        DONE,
        /** Given up; there is no result. */
        CANCELLED
    }

    /** A finished box: its lowest corner and its highest, both inclusive. */
    public record Box(BlockPos min, BlockPos max) {
    }

    /** A finished point: the block clicked, the face it was clicked on, and the yaw of the look. */
    public record Point(BlockPos block, Direction face, float yaw) {

        /**
         * The block in front of the face that was clicked - the one a block placed there would
         * fill. Clicked on a floor, that is the block somebody standing on it has their feet in,
         * which is what every "use my position" button of a point has always written.
         */
        public BlockPos inFront() {
            return block.relative(face);
        }
    }

    private final boolean pointMode;
    private Stage stage;
    private BlockPos first;
    private Box box;
    private Point point;

    private ZoneSelection(Stage stage) {
        this.stage = stage;
        this.pointMode = stage == Stage.POINT;
    }

    /** A selection of two corners. */
    public static ZoneSelection box() {
        return new ZoneSelection(Stage.FIRST_CORNER);
    }

    /** A selection of one block. */
    public static ZoneSelection point() {
        return new ZoneSelection(Stage.POINT);
    }

    /**
     * One click on a block.
     *
     * @param block the block clicked
     * @param face  the face of it the click landed on
     * @param yRot  the builder's yaw at the click, in any range the game hands it over in
     * @return true when this click finished the selection
     */
    public boolean click(BlockPos block, Direction face, float yRot) {
        switch (stage) {
            case FIRST_CORNER -> {
                first = block.immutable();
                stage = Stage.SECOND_CORNER;
                return false;
            }
            case SECOND_CORNER -> {
                box = normalize(first, block);
                stage = Stage.DONE;
                return true;
            }
            case POINT -> {
                point = new Point(block.immutable(), face, lookYaw(yRot));
                stage = Stage.DONE;
                return true;
            }
            default -> {
                // A click after the end changes nothing: the result has already been handed on.
                return false;
            }
        }
    }

    /** Gives the selection up; a finished one keeps its result. */
    public void cancel() {
        if (isActive()) {
            stage = Stage.CANCELLED;
        }
    }

    /** Whether the selection is still waiting for a click. */
    public boolean isActive() {
        return stage == Stage.FIRST_CORNER || stage == Stage.SECOND_CORNER || stage == Stage.POINT;
    }

    /** Whether this selects a point rather than a box. */
    public boolean isPoint() {
        return pointMode;
    }

    public Stage stage() {
        return stage;
    }

    /** The corner a box already has, or null before the first click. */
    public BlockPos firstCorner() {
        return first;
    }

    /** The finished box, or null until the second corner is in. */
    public Box result() {
        return box;
    }

    /** The finished point, or null until it is clicked. */
    public Point pointResult() {
        return point;
    }

    /** The line the builder is told what the next click does, or null once nothing is waiting. */
    public String hintKey() {
        return switch (stage) {
            case FIRST_CORNER -> HINT_FIRST;
            case SECOND_CORNER -> HINT_SECOND;
            case POINT -> HINT_POINT;
            default -> null;
        };
    }

    /**
     * The box the two clicks would make if the second landed on {@code aimed}: what the outline
     * between the clicks follows. Null before the first corner, and just that corner when the
     * crosshair is on no block.
     */
    public Box liveBox(BlockPos aimed) {
        if (first == null) {
            return null;
        }
        return normalize(first, aimed == null ? first : aimed);
    }

    /** Two corners in either order as the lowest and the highest, axis by axis. */
    public static Box normalize(BlockPos a, BlockPos b) {
        return new Box(
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    /**
     * A yaw as the facing fields hold it: wrapped into -180..180 and rounded to a whole degree,
     * because a field reading 37.18359 says nothing a builder can use that 37 does not.
     */
    public static float lookYaw(float yRot) {
        return Math.round(Mth.wrapDegrees(yRot));
    }
}
