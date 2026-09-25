package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPlatformZone;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZoneList;
import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import com.goodbird.cnpcgeckoaddon.data.BossVentZoneList;
import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The box a zone is drawn as in the world against the box the fight acts in.
 *
 * <p>The outline a builder lines a platform or a vent up by is worked out on the client with
 * {@link ZoneCoordinates}, and the fight works its own out in the runtime. If the two ever read
 * the corners differently - a block of offset, the far face left out, the build height cut
 * another way - the outline sits a block away from where the floor will actually burn.</p>
 */
class ZoneBoxMatchesRuntimeTest {

    private static final int MIN_BUILD = -64;
    private static final int MAX_BUILD = 320;

    private static final List<BlockPos> ANCHORS = List.of(
            BlockPos.ZERO, new BlockPos(120, 64, -40), new BlockPos(-5000, -60, 7000), new BlockPos(0, 318, 0));

    /** Corner pairs as x1, y1, z1, x2, y2, z2: either order, mixed per axis, past the build height. */
    private static final int[][] CORNERS = {
            {0, 0, 0, 0, 0, 0},
            {-3, -1, 2, 4, 2, -5},
            {10, 5, 10, -10, -5, -10},
            {100, 64, 100, 90, 70, 110},
            {0, -200, 0, 3, 400, 3},
            {0, 500, 0, 1, 600, 1},
    };

    @Test
    @DisplayName("a platform is drawn over exactly the blocks the fight burns")
    void platformsMatch() {
        for (BlockPos anchor : ANCHORS) {
            for (int[] c : CORNERS) {
                for (int mode : new int[]{BossPlatformZone.COORDINATE_ARENA_OFFSET, BossPlatformZone.COORDINATE_FIXED}) {
                    BossPlatformZone zone = new BossPlatformZoneList().add();
                    zone.setCoordinateMode(mode);
                    zone.setCorner1(c[0], c[1], c[2]);
                    zone.setCorner2(c[3], c[4], c[5]);
                    AABB runtime = BossPlatformRuntime.zoneBox(zone, anchor, MIN_BUILD, MAX_BUILD);
                    AABB drawn = drawn(zone.getX1(), zone.getY1(), zone.getZ1(), zone.getX2(), zone.getY2(),
                            zone.getZ2(), mode == BossPlatformZone.COORDINATE_FIXED, anchor);
                    assertEquals(runtime, drawn, "platform " + java.util.Arrays.toString(c) + " mode " + mode
                            + " anchor " + anchor);
                }
            }
        }
    }

    @Test
    @DisplayName("a vent is drawn over exactly the blocks the fight fires out of")
    void ventsMatch() {
        for (BlockPos anchor : ANCHORS) {
            for (int[] c : CORNERS) {
                for (int mode : new int[]{BossVentZone.COORDINATE_ARENA_OFFSET, BossVentZone.COORDINATE_FIXED}) {
                    BossVentZone zone = new BossVentZoneList().add();
                    zone.setCoordinateMode(mode);
                    zone.setCorner1(c[0], c[1], c[2]);
                    zone.setCorner2(c[3], c[4], c[5]);
                    AABB runtime = BossVentGeometry.zoneBox(zone, anchor, MIN_BUILD, MAX_BUILD);
                    AABB drawn = drawn(zone.getX1(), zone.getY1(), zone.getZ1(), zone.getX2(), zone.getY2(),
                            zone.getZ2(), mode == BossVentZone.COORDINATE_FIXED, anchor);
                    assertEquals(runtime, drawn, "vent " + java.util.Arrays.toString(c) + " mode " + mode
                            + " anchor " + anchor);
                }
            }
        }
    }

    private static AABB drawn(int x1, int y1, int z1, int x2, int y2, int z2, boolean fixed, BlockPos anchor) {
        return ZoneCoordinates.clampToBuildHeight(ZoneCoordinates.box(x1, y1, z1, x2, y2, z2, fixed, anchor),
                MIN_BUILD, MAX_BUILD);
    }
}
