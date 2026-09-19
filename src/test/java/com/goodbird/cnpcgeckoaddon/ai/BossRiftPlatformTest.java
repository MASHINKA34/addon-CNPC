package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftPlatform.Part;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftPlatform.Placement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platform a rift is fought on, block by block: the disc and the layer under it, the wall on
 * its rim, the lights in the floor and the roof. The shape is the whole arena, so a wall with a
 * gap in it or a light where the slot check looks for floor is a broken fight.
 */
class BossRiftPlatformTest {

    @Test
    @DisplayName("a bare disc is two layers of floor, every cell within the radius and nothing else")
    void aBareDisc() {
        List<Placement> blocks = BossRiftPlatform.placements(4, 0, 0, false);
        Map<Part, Integer> counts = counts(blocks);
        // Radius four: 9 + 2 * 7 + 2 * 7 + 2 * 5 + 2 * 1 cells.
        assertEquals(49, counts.getOrDefault(Part.FLOOR, 0));
        assertEquals(49, counts.getOrDefault(Part.SUBFLOOR, 0));
        assertEquals(98, blocks.size(), "no wall, light or roof was asked for");
        for (Placement block : blocks) {
            assertTrue(block.dx() * block.dx() + block.dz() * block.dz() <= 16, block + " is outside the disc");
            assertEquals(block.part() == Part.FLOOR ? 0 : -1, block.dy());
        }
        assertEquals(Part.FLOOR, at(positions(blocks), 0, 0, 0), "the centre is floor");
        assertEquals(Part.FLOOR, at(positions(blocks), 4, 0, 0), "the radius itself is inside");
        assertNull(at(positions(blocks), 4, 0, 1), "one past it is not");
        assertEquals(0, BossRiftPlatform.placements(-3, 0, 0, false).stream()
                .filter(block -> block.dx() != 0 || block.dz() != 0).count(), "a negative radius is the centre alone");
    }

    @Test
    @DisplayName("the wall stands on the rim at every height up to its own, and closes the ring")
    void theWallClosesTheRing() {
        int radius = 16;
        int height = 4;
        Map<Long, Part> positions = positions(BossRiftPlatform.placements(radius, height, 0, false));
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                boolean inside = dx * dx + dz * dz <= radius * radius;
                boolean edge = inside && (!BossRiftPlatform.inDisc(dx + 1, dz, radius)
                        || !BossRiftPlatform.inDisc(dx - 1, dz, radius)
                        || !BossRiftPlatform.inDisc(dx, dz + 1, radius)
                        || !BossRiftPlatform.inDisc(dx, dz - 1, radius));
                for (int dy = 1; dy <= height; dy++) {
                    assertEquals(edge ? Part.WALL : null, at(positions, dx, dy, dz),
                            "wall at " + dx + ", " + dy + ", " + dz);
                }
                assertNull(at(positions, dx, height + 1, dz), "the wall stops at its height");
                if (edge) {
                    assertEquals(Part.FLOOR, at(positions, dx, 0, dz), "the wall stands on floor");
                }
            }
        }
        // Walking the rim cells, each one's neighbours along the ring are rim too: no gap to fall through.
        assertTrue(BossRiftPlatform.isRim(radius, 0, radius));
        assertTrue(BossRiftPlatform.isRim(0, -radius, radius));
        assertFalse(BossRiftPlatform.isRim(0, 0, radius));
        assertFalse(BossRiftPlatform.isRim(radius + 1, 0, radius), "outside the disc is not rim");
    }

    @Test
    @DisplayName("lights sit in the floor on the grid, off the rim and never on the centre block")
    void lightsOnTheGrid() {
        int radius = 16;
        int spacing = 6;
        List<Placement> blocks = BossRiftPlatform.placements(radius, 4, spacing, false);
        Map<Long, Part> positions = positions(blocks);
        assertEquals(Part.FLOOR, at(positions, 0, 0, 0), "the centre is what tells the slot is built");
        assertEquals(Part.LIGHT, at(positions, 6, 0, 0));
        assertEquals(Part.LIGHT, at(positions, -6, 0, 12));
        assertEquals(Part.LIGHT, at(positions, 12, 0, -6));
        assertEquals(Part.FLOOR, at(positions, 5, 0, 0), "between two lights is floor");
        assertEquals(Part.SUBFLOOR, at(positions, 6, -1, 0), "a light is backed by the layer under it");
        int lights = 0;
        for (Placement block : blocks) {
            if (block.part() != Part.LIGHT) {
                continue;
            }
            lights++;
            assertEquals(0, block.dy(), "a light is set into the floor");
            assertEquals(0, Math.floorMod(block.dx(), spacing));
            assertEquals(0, Math.floorMod(block.dz(), spacing));
            assertFalse(BossRiftPlatform.isRim(block.dx(), block.dz(), radius), block + " is under the wall");
        }
        // The grid points of a sixteen disc, less the centre: every multiple of six inside it.
        assertEquals(20, lights);
        assertEquals(0, counts(BossRiftPlatform.placements(radius, 4, 0, false)).getOrDefault(Part.LIGHT, 0),
                "a spacing of nought is no lights");
    }

    @Test
    @DisplayName("the roof covers the whole disc eight blocks over the wall's top")
    void theRoof() {
        int radius = 8;
        int height = 3;
        List<Placement> blocks = BossRiftPlatform.placements(radius, height, 0, true);
        int roofY = BossRiftPlatform.roofHeight(height);
        assertEquals(height + 8, roofY);
        Map<Long, Part> positions = positions(blocks);
        int roof = 0;
        for (Placement block : blocks) {
            if (block.part() == Part.ROOF) {
                roof++;
                assertEquals(roofY, block.dy());
            }
        }
        assertEquals(counts(blocks).get(Part.FLOOR) + counts(blocks).getOrDefault(Part.LIGHT, 0), roof,
                "the roof is as wide as the floor");
        assertEquals(Part.ROOF, at(positions, 0, roofY, 0));
        assertNull(at(positions, 0, roofY - 1, 0), "open between the wall's top and the roof");
        assertEquals(8, BossRiftPlatform.roofHeight(0), "no wall still leaves room to stand");
        assertEquals(0, counts(BossRiftPlatform.placements(radius, height, 0, false)).getOrDefault(Part.ROOF, 0));
    }

    @Test
    @DisplayName("every block is laid once, floor before anything stands on it")
    void everyBlockOnceFloorFirst() {
        List<Placement> blocks = BossRiftPlatform.placements(12, 5, 4, true);
        assertEquals(blocks.size(), positions(blocks).size(), "two placements share a position");
        boolean pastFloor = false;
        for (Placement block : blocks) {
            boolean floor = block.part() == Part.FLOOR || block.part() == Part.SUBFLOOR || block.part() == Part.LIGHT;
            if (!floor) {
                pastFloor = true;
            } else {
                assertFalse(pastFloor, block + " comes after the wall or the roof");
            }
        }
    }

    private static Map<Part, Integer> counts(List<Placement> blocks) {
        Map<Part, Integer> counts = new EnumMap<>(Part.class);
        for (Placement block : blocks) {
            counts.merge(block.part(), 1, Integer::sum);
        }
        return counts;
    }

    private static Map<Long, Part> positions(List<Placement> blocks) {
        Map<Long, Part> positions = new HashMap<>();
        for (Placement block : blocks) {
            positions.put(key(block.dx(), block.dy(), block.dz()), block.part());
        }
        return positions;
    }

    private static Part at(Map<Long, Part> positions, int dx, int dy, int dz) {
        return positions.get(key(dx, dy, dz));
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0xFFFFF) << 40) | ((long) (dy & 0xFFFFF) << 20) | (dz & 0xFFFFF);
    }
}
