package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two aggro zone flags that decide who may start a fight and who may hurt the boss.
 *
 * <p>Both are read on every tick and every hit, and both fail quietly: a flag that survived
 * the zone being switched off locks the boss out of every fight, and a save that read an old
 * boss as exclusive would change a live arena the day the update went in.</p>
 */
class BossAggroZoneFlagsTest {

    @Test
    @DisplayName("a boss saved before the flags existed keeps the old zone rules")
    void anOldSaveReadsBothFlagsAsOff() {
        TeleportPathData saved = zoneBoss();
        saved.setAggroZoneKeepInside(true);
        CompoundTag tag = saved.writeToNBT(new CompoundTag());
        tag.remove("GeckoBossAggroZoneExclusive");
        tag.remove("GeckoBossAggroZoneBlockOutside");

        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(tag);
        assertFalse(reread.isAggroZoneExclusive(), "a tag with no exclusive key has to read as off");
        assertFalse(reread.isAggroZoneBlocksOutsideDamage(), "a tag with no block key has to read as off");
        assertFalse(reread.isAggroZoneOnlyWayIn());
        assertFalse(reread.blocksHitsFromOutsideAggroZone());
        assertTrue(reread.holdsTargetsInAggroZone(), "the keep flag it did save still holds the targets in");
    }

    @Test
    @DisplayName("both flags come back from the save they were written to")
    void bothFlagsSurviveTheRoundTrip() {
        TeleportPathData saved = zoneBoss();
        saved.setAggroZoneExclusive(true);
        saved.setAggroZoneBlocksOutsideDamage(true);

        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(saved.writeToNBT(new CompoundTag()));
        assertTrue(reread.isAggroZoneExclusive());
        assertTrue(reread.isAggroZoneBlocksOutsideDamage());
    }

    @Test
    @DisplayName("without a zone both flags are ignored, not obeyed")
    void flagsNeedTheZone() {
        TeleportPathData data = zoneBoss();
        data.setAggroZoneExclusive(true);
        data.setAggroZoneBlocksOutsideDamage(true);
        data.setAggroZoneKeepInside(true);
        data.setAggroZoneEnabled(false);

        assertFalse(data.isAggroZoneOnlyWayIn(), "with no box there is no outside to keep a fight out of");
        assertFalse(data.blocksHitsFromOutsideAggroZone(), "with no box nobody stands outside it");
        assertFalse(data.holdsTargetsInAggroZone());
        assertTrue(data.isAggroZoneExclusive(), "the stored flag waits for the zone rather than being wiped");
    }

    @Test
    @DisplayName("the only way in holds targets in the zone without touching the keep flag")
    void exclusiveImpliesKeepInside() {
        TeleportPathData data = zoneBoss();
        assertFalse(data.holdsTargetsInAggroZone(), "a plain zone only starts the fight");

        data.setAggroZoneExclusive(true);
        assertTrue(data.isAggroZoneOnlyWayIn());
        assertTrue(data.holdsTargetsInAggroZone(), "an exclusive zone keeps its fight inside too");
        assertFalse(data.isAggroZoneKeepInside(), "the builder's keep flag is left as it was set");
        assertFalse(data.blocksHitsFromOutsideAggroZone(), "the two flags are independent");
    }

    private static TeleportPathData zoneBoss() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        data.setAggroZoneEnabled(true);
        data.setAggroZoneCorner1(0, 64, 0);
        data.setAggroZoneCorner2(10, 70, 10);
        return data;
    }
}
