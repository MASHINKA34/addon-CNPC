package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zones a rift's crystals stand in, saved and read back: the coordinates, the per-zone
 * overrides of what the crystal is made of and what colour it is, and the ids that have to
 * outlive a deleted row.
 *
 * <p>A zone that loses its override on the next load is a crystal that quietly turns back into
 * the rift's own block, which nobody notices until the fight is built and the colours are wrong.</p>
 */
class BossRiftCrystalListTest {

    @Test
    @DisplayName("a zone with everything set on it survives the save exactly")
    void aFullZoneRoundTrips() {
        BossRiftCrystalList list = new BossRiftCrystalList();
        BossRiftCrystalPoint point = list.add();
        point.setCoordinateMode(BossRiftCrystalPoint.COORDINATE_FIXED);
        point.setPosition(-120, 70, 340);
        point.setRadiusTenths(25);
        point.setBlockOverride("minecraft:diamond_block");
        point.setColorOverride(0x33FF99);
        BossRiftCrystalPoint off = list.add();
        off.setEnabled(false);
        off.setPosition(3, 0, -4);

        BossRiftCrystalList reread = new BossRiftCrystalList();
        reread.readFromNBT(list.writeToNBT());
        assertEquals(2, reread.size());
        BossRiftCrystalPoint first = reread.get(0);
        assertTrue(first.isEnabled());
        assertEquals(BossRiftCrystalPoint.COORDINATE_FIXED, first.getCoordinateMode());
        assertEquals(-120, first.getX());
        assertEquals(70, first.getY());
        assertEquals(340, first.getZ());
        assertEquals(25, first.getRadiusTenths());
        assertEquals("minecraft:diamond_block", first.getBlockOverride());
        assertEquals(0x33FF99, first.getColorOverride());
        assertEquals(point.getPointId(), first.getPointId());
        assertFalse(reread.get(1).isEnabled(), "a zone switched off stays switched off");
        assertEquals(1, reread.enabled().size());
        assertTrue(reread.hasEnabled());
    }

    @Test
    @DisplayName("a zone with nothing set on it reads back as the rift's own block, colour and radius")
    void afreshZoneDefersToTheRift() {
        BossRiftCrystalList list = new BossRiftCrystalList();
        BossRiftCrystalPoint point = list.add();
        assertTrue(point.isEnabled());
        assertEquals(BossRiftCrystalPoint.COORDINATE_ARENA_OFFSET, point.getCoordinateMode());
        assertEquals(0, point.getRadiusTenths(), "nought is the rift's own radius");
        assertEquals("", point.getBlockOverride());
        assertEquals(BossRiftCrystalPoint.NO_COLOR, point.getColorOverride());

        BossRiftCrystalList reread = new BossRiftCrystalList();
        reread.readFromNBT(list.writeToNBT());
        assertEquals(0, reread.get(0).getRadiusTenths());
        assertEquals("", reread.get(0).getBlockOverride());
        assertEquals(BossRiftCrystalPoint.NO_COLOR, reread.get(0).getColorOverride());
    }

    @Test
    @DisplayName("a zone out of an edited save is clamped, and a colour below nought is no colour")
    void poisonedValuesAreClamped() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CoordinateMode", 99);
        tag.putInt("X", Integer.MAX_VALUE);
        tag.putInt("Y", Integer.MIN_VALUE);
        tag.putInt("RadiusTenths", 9999);
        tag.putInt("ColorOverride", Integer.MAX_VALUE);
        ListTag list = new ListTag();
        list.add(tag);
        BossRiftCrystalList read = new BossRiftCrystalList();
        read.readFromNBT(list);
        BossRiftCrystalPoint point = read.get(0);
        assertEquals(BossRiftCrystalPoint.COORDINATE_FIXED, point.getCoordinateMode());
        assertEquals(BossRiftCrystalPoint.MAX_COORDINATE, point.getX());
        assertEquals(-BossRiftCrystalPoint.MAX_COORDINATE, point.getY());
        assertEquals(BossRiftCrystalPoint.MAX_RADIUS_TENTHS, point.getRadiusTenths());
        assertEquals(BossRiftSettings.MAX_COLOR, point.getColorOverride());
        point.setColorOverride(-7);
        assertEquals(BossRiftCrystalPoint.NO_COLOR, point.getColorOverride(), "anything below nought is no colour");
    }

    @Test
    @DisplayName("ids are unique, survive a deleted row, and the list stops at its cap")
    void idsAndCap() {
        BossRiftCrystalList list = new BossRiftCrystalList();
        BossRiftCrystalPoint first = list.add();
        BossRiftCrystalPoint second = list.add();
        BossRiftCrystalPoint third = list.add();
        assertNotEquals(first.getPointId(), second.getPointId());
        assertNotEquals(second.getPointId(), third.getPointId());
        int keptId = third.getPointId();
        list.remove(1);
        assertEquals(keptId, list.get(1).getPointId(), "deleting a row does not renumber the rest");

        while (list.size() < BossRiftCrystalList.MAX_ENTRIES) {
            assertNotNull(list.add());
        }
        assertNull(list.add(), "the list is full");
        assertEquals(BossRiftCrystalList.MAX_ENTRIES, list.size());

        // Two rows saved with the same id - a hand-edited save - come back with one of them moved.
        ListTag clashing = new ListTag();
        CompoundTag one = new CompoundTag();
        one.putInt("PointId", 5);
        CompoundTag two = new CompoundTag();
        two.putInt("PointId", 5);
        clashing.add(one);
        clashing.add(two);
        BossRiftCrystalList read = new BossRiftCrystalList();
        read.readFromNBT(clashing);
        assertNotEquals(read.get(0).getPointId(), read.get(1).getPointId());
    }

    @Test
    @DisplayName("the rift carries its zones through a save and through a copy of itself")
    void theRiftCarriesTheList() {
        BossPhaseData phase = new BossPhaseData();
        BossRiftCrystalPoint point = phase.rift().getCrystalPoints().add();
        point.setPosition(6, 1, -6);
        point.setBlockOverride("minecraft:emerald_block");
        point.setColorOverride(0x00FF00);

        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(phase.writeToNBT());
        assertEquals(1, reread.rift().getCrystalPoints().size());
        assertEquals("minecraft:emerald_block", reread.rift().getCrystalPoints().get(0).getBlockOverride());
        assertEquals(0x00FF00, reread.rift().getCrystalPoints().get(0).getColorOverride());
        assertEquals(6, reread.rift().getCrystalPoints().get(0).getX());

        BossRiftSettings copy = phase.rift().copy();
        assertEquals(1, copy.getCrystalPoints().size());
        assertEquals(0x00FF00, copy.getCrystalPoints().get(0).getColorOverride());
        copy.getCrystalPoints().clear();
        assertEquals(1, phase.rift().getCrystalPoints().size(), "the copy is its own object");
    }
}
