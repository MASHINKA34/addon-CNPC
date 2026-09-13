package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The health link's seven boss-wide settings, on their way to the save and back.
 *
 * <p>Two of the ways they can be wrong change a live server quietly: an old boss read back with
 * a group would start sharing health with a stranger on the day the update went in, and a window
 * read back unclamped is a downed boss that never gets up. The persistence and range sweeps
 * reach these fields too; this pins the exact bounds and the old-save reading on top of them.</p>
 */
class BossHealthLinkSettingsTest {

    private static final List<String> KEYS = List.of(
            "GeckoBossHealthLinkGroup", "GeckoBossHealthLinkMode", "GeckoBossHealthLinkRange",
            "GeckoBossHealthLinkWindowTicks", "GeckoBossHealthLinkRevivePercent",
            "GeckoBossHealthLinkDownedAnimation", "GeckoBossHealthLinkReviveAnimation");

    @Test
    @DisplayName("a boss saved before the link existed is linked to nobody and keeps the defaults")
    void anOldSaveIsNotLinked() {
        CompoundTag tag = configured().writeToNBT(new CompoundTag());
        for (String key : KEYS) {
            assertTrue(tag.contains(key), "a configured boss writes " + key);
            tag.remove(key);
        }

        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(tag);
        assertEquals("", reread.getHealthLinkGroup());
        assertFalse(reread.isHealthLinked(), "a tag with no group has to read as no link at all");
        assertEquals(TeleportPathData.HEALTH_LINK_SHARED, reread.getHealthLinkMode());
        assertEquals(64, reread.getHealthLinkRange());
        assertEquals(200, reread.getHealthLinkWindowTicks());
        assertEquals(50, reread.getHealthLinkRevivePercent());
        assertEquals("", reread.getHealthLinkDownedAnimation());
        assertEquals("", reread.getHealthLinkReviveAnimation());
    }

    @Test
    @DisplayName("every setting comes back from the save it was written to")
    void theSettingsSurviveTheRoundTrip() {
        TeleportPathData saved = configured();
        saved.setHealthLinkGroup("twins");
        saved.setHealthLinkMode(TeleportPathData.HEALTH_LINK_TOGETHER);
        saved.setHealthLinkRange(0);
        saved.setHealthLinkWindowTicks(333);
        saved.setHealthLinkRevivePercent(75);
        saved.setHealthLinkDownedAnimation("animation.boss.down");
        saved.setHealthLinkReviveAnimation("animation.boss.up");

        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(saved.writeToNBT(new CompoundTag()));
        assertEquals("twins", reread.getHealthLinkGroup());
        assertTrue(reread.isHealthLinked());
        assertEquals(TeleportPathData.HEALTH_LINK_TOGETHER, reread.getHealthLinkMode());
        assertEquals(0, reread.getHealthLinkRange(), "a range of nothing is the whole level, not a default to restore");
        assertEquals(333, reread.getHealthLinkWindowTicks());
        assertEquals(75, reread.getHealthLinkRevivePercent());
        assertEquals("animation.boss.down", reread.getHealthLinkDownedAnimation());
        assertEquals("animation.boss.up", reread.getHealthLinkReviveAnimation());
    }

    @Test
    @DisplayName("the setters hold every number inside its own range")
    void settersClampToTheirRanges() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthLinkMode(-1);
        assertEquals(TeleportPathData.HEALTH_LINK_SHARED, data.getHealthLinkMode());
        data.setHealthLinkMode(7);
        assertEquals(TeleportPathData.HEALTH_LINK_TOGETHER, data.getHealthLinkMode());

        data.setHealthLinkRange(-5);
        assertEquals(0, data.getHealthLinkRange());
        data.setHealthLinkRange(300);
        assertEquals(256, data.getHealthLinkRange());

        data.setHealthLinkWindowTicks(0);
        assertEquals(20, data.getHealthLinkWindowTicks(), "a window shorter than a second is nobody's chance");
        data.setHealthLinkWindowTicks(99999);
        assertEquals(6000, data.getHealthLinkWindowTicks());

        data.setHealthLinkRevivePercent(0);
        assertEquals(1, data.getHealthLinkRevivePercent(), "a boss that gets up with no health is a dead one");
        data.setHealthLinkRevivePercent(150);
        assertEquals(100, data.getHealthLinkRevivePercent());
    }

    @Test
    @DisplayName("a save holding numbers out of range is read back inside the ranges")
    void aPoisonedSaveIsClamped() {
        CompoundTag tag = configured().writeToNBT(new CompoundTag());
        tag.putInt("GeckoBossHealthLinkMode", 9);
        tag.putInt("GeckoBossHealthLinkRange", -40);
        tag.putInt("GeckoBossHealthLinkWindowTicks", Integer.MAX_VALUE);
        tag.putInt("GeckoBossHealthLinkRevivePercent", Integer.MIN_VALUE);

        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(tag);
        assertEquals(TeleportPathData.HEALTH_LINK_TOGETHER, reread.getHealthLinkMode());
        assertEquals(0, reread.getHealthLinkRange());
        assertEquals(6000, reread.getHealthLinkWindowTicks());
        assertEquals(1, reread.getHealthLinkRevivePercent());
    }

    @Test
    @DisplayName("a group is trimmed, and one of spaces alone links nobody")
    void theGroupIsTrimmed() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthLinkGroup("  twins ");
        assertEquals("twins", data.getHealthLinkGroup());
        assertTrue(data.isHealthLinked());

        data.setHealthLinkGroup("   ");
        assertFalse(data.isHealthLinked(), "a group of blanks would tie together every boss left blank by mistake");
        data.setHealthLinkGroup(null);
        assertEquals("", data.getHealthLinkGroup());
    }

    private static TeleportPathData configured() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        return data;
    }
}
