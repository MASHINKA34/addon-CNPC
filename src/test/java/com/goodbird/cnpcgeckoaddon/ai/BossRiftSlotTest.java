package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a rift's platform lies in the void: which slot a boss takes, and where that slot's centre
 * block is. Worked out without a world, since two bosses sent to the same spot, or a slot laid
 * past the world border, only ever shows up as players landing on somebody else's platform.
 */
class BossRiftSlotTest {

    private static final int STEP = BossRiftDimension.SLOT_SPACING;

    @Test
    @DisplayName("the four corners of the grid sit where the formula puts them")
    void cornersOfTheGrid() {
        assertEquals(-32 * STEP + STEP / 2, BossRiftDimension.slotCentreX(0));
        assertEquals(-32 * STEP + STEP / 2, BossRiftDimension.slotCentreZ(0));
        assertEquals(31 * STEP + STEP / 2, BossRiftDimension.slotCentreX(63), "the last column");
        assertEquals(-32 * STEP + STEP / 2, BossRiftDimension.slotCentreZ(63), "still the first row");
        assertEquals(-32 * STEP + STEP / 2, BossRiftDimension.slotCentreX(64), "the next row starts over");
        assertEquals(-31 * STEP + STEP / 2, BossRiftDimension.slotCentreZ(64));
        assertEquals(31 * STEP + STEP / 2, BossRiftDimension.slotCentreX(BossRiftDimension.SLOTS - 1));
        assertEquals(31 * STEP + STEP / 2, BossRiftDimension.slotCentreZ(BossRiftDimension.SLOTS - 1));
    }

    @Test
    @DisplayName("neighbouring slots are one step of 2048 apart, and every slot has a centre of its own")
    void slotsAreASteppedGrid() {
        assertEquals(2048, STEP);
        Set<Long> centres = new HashSet<>();
        for (int slot = 0; slot < BossRiftDimension.SLOTS; slot++) {
            int x = BossRiftDimension.slotCentreX(slot);
            int z = BossRiftDimension.slotCentreZ(slot);
            assertTrue(centres.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)), "slot " + slot + " shares a centre");
            assertTrue(Math.abs(x) <= 4_000_000 && Math.abs(z) <= 4_000_000, "slot " + slot + " is out of bounds");
            assertEquals(STEP / 2, Math.floorMod(x, STEP), "a centre is the middle of its cell");
            assertEquals(STEP / 2, Math.floorMod(z, STEP));
            if ((slot & 63) != 63) {
                assertEquals(STEP, BossRiftDimension.slotCentreX(slot + 1) - x, "one column along");
                assertEquals(z, BossRiftDimension.slotCentreZ(slot + 1));
            }
            if (slot + 64 < BossRiftDimension.SLOTS) {
                assertEquals(STEP, BossRiftDimension.slotCentreZ(slot + 64) - z, "one row along");
                assertEquals(x, BossRiftDimension.slotCentreX(slot + 64));
            }
        }
        assertEquals(BossRiftDimension.SLOTS, centres.size());
    }

    @Test
    @DisplayName("a boss with no slot of its own always hashes to the same one, inside the grid")
    void theHashIsStableAndInside() {
        Random random = new Random(89L);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            int slot = BossRiftDimension.hashSlot(id);
            assertTrue(slot >= 0 && slot < BossRiftDimension.SLOTS, "slot " + slot + " is off the grid");
            assertEquals(slot, BossRiftDimension.hashSlot(new UUID(id.getMostSignificantBits(),
                    id.getLeastSignificantBits())), "the same UUID must land on the same slot");
            assertEquals(slot, BossRiftDimension.resolveSlot(0, id), "nought means by the boss");
            seen.add(slot);
        }
        assertTrue(seen.size() > 1000, "two thousand bosses crowd onto " + seen.size() + " slots");
        assertEquals(0, BossRiftDimension.hashSlot(null));
    }

    @Test
    @DisplayName("a slot the builder named wins over the hash, and one past the grid is its last slot")
    void aNamedSlotWins() {
        UUID id = UUID.fromString("0b6f1b8e-4f0c-4c4e-9d8b-5a8f3c2d1e0f");
        assertEquals(5, BossRiftDimension.resolveSlot(5, id));
        assertEquals(BossRiftDimension.SLOTS - 1, BossRiftDimension.resolveSlot(9999, id));
        BlockPos centre = BossRiftDimension.slotCentre(5, 64);
        assertEquals(new BlockPos(-27 * STEP + STEP / 2, 64, -32 * STEP + STEP / 2), centre);
        assertEquals(new Vec3(centre.getX() + 0.5D, 65.0D, centre.getZ() + 0.5D),
                BossRiftDimension.standingSpot(centre), "players land on top of the centre block");
    }
}
