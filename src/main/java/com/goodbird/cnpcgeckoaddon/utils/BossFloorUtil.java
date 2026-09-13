package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** The one rule every mark a boss paints follows about what counts as the arena floor. */
public final class BossFloorUtil {

    /**
     * How far below the shape a floor may be and still count. A wave, a warning ring or a
     * drawn band hanging in mid air over a balcony edge looks worse than one that simply
     * skips the gap, so past this the point is dropped rather than stretched down to
     * whatever is at the bottom.
     */
    public static final int FLOOR_SEARCH_DEPTH = 4;

    private BossFloorUtil() {
    }

    /**
     * The block a shape lies on at one point of it, or null when there is none within reach.
     *
     * <p>Takes a {@link LevelReader} rather than a server level because the client asks the
     * same question of the same blocks: a band drawn along a staircase has to step exactly
     * where the dust did, and break over exactly the same hole, which only holds while both
     * sides run this one search.</p>
     */
    public static BlockPos findFloor(LevelReader level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int depth = 0; depth <= FLOOR_SEARCH_DEPTH; depth++) {
            // What Level.isLoaded is, spelled out: the level a client holds answers the same
            // question, it simply does not carry that method down from the reader interface.
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                return pos.immutable();
            }
            pos.move(Direction.DOWN);
        }
        return null;
    }
}
