package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftCrystalPlacement.Spot;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftOutcome.Result;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a crystal is collected and when a rift that asked for crystals is over.
 *
 * <p>Both halves are worth checking without a world because both are silent when they are wrong: a
 * zone that counts a player on the floor below it hands the party a free rift, one that never
 * counts anybody hands them a rift they cannot finish, and a "minions and crystals" rift that
 * settles on the crystals alone lets its players walk home past the minions still standing.</p>
 */
class BossRiftCollectTest {

    private static final long START = 1000L;
    private static final long END = 2200L;
    /** A crystal hanging two blocks over a floor whose surface is at y 65, reaching a block and a half. */
    private static final Spot CRYSTAL = new Spot(1, 10.0D, 67.0D, -4.0D, 65.0D, 1.5D, "", 0xB47AFF);
    /** A player's height, as vanilla stands them. */
    private static final double PLAYER_HEIGHT = 1.8D;

    @Test
    @DisplayName("walking into the zone collects it: flat out to its radius, whatever the crystal hangs at")
    void theZoneIsMeasuredFlat() {
        assertTrue(BossRiftCrystalPlacement.inZone(CRYSTAL, 10.0D, 65.0D, -4.0D), "standing under it");
        assertTrue(BossRiftCrystalPlacement.inZone(CRYSTAL, 11.4D, 65.0D, -4.0D), "just inside the radius");
        assertFalse(BossRiftCrystalPlacement.inZone(CRYSTAL, 11.6D, 65.0D, -4.0D), "just outside it");
        assertTrue(BossRiftCrystalPlacement.inZone(CRYSTAL, 11.0D, 65.0D, -3.0D), "inside on the diagonal");
        assertFalse(BossRiftCrystalPlacement.inZone(CRYSTAL, 11.2D, 65.0D, -2.8D), "outside on the diagonal");
    }

    @Test
    @DisplayName("a zone does not reach through a storey: feet two blocks off its floor at most")
    void theZoneKeepsToItsOwnStorey() {
        assertTrue(BossRiftCrystalPlacement.inZone(CRYSTAL, 10.0D, 67.0D, -4.0D), "jumped, or on a block of it");
        assertTrue(BossRiftCrystalPlacement.inZone(CRYSTAL, 10.0D, 63.0D, -4.0D), "two blocks down is still in");
        assertFalse(BossRiftCrystalPlacement.inZone(CRYSTAL, 10.0D, 68.0D, -4.0D), "the storey above is not");
        assertFalse(BossRiftCrystalPlacement.inZone(CRYSTAL, 10.0D, 62.0D, -4.0D), "nor the cellar below");
        assertEquals(2.0D, BossRiftCrystalPlacement.ZONE_HEIGHT_REACH, 1.0E-9D);
    }

    @Test
    @DisplayName("touching measures to the crystal from whichever part of the player is nearest its height")
    void touchingReachesUpToTheCrystal() {
        // Standing right under a crystal two blocks up: the player's own head is within reach, so a
        // crystal hung at the shipped height is touched by walking under it rather than jumping.
        assertTrue(BossRiftCrystalPlacement.touches(CRYSTAL, 10.0D, 65.0D, -4.0D, PLAYER_HEIGHT));
        assertTrue(BossRiftCrystalPlacement.touches(CRYSTAL, 11.0D, 67.0D, -4.0D, PLAYER_HEIGHT),
                "level with it, a block away, is a touch as well");
        assertFalse(BossRiftCrystalPlacement.touches(CRYSTAL, 12.0D, 65.0D, -4.0D, PLAYER_HEIGHT),
                "two blocks aside, nothing of the player reaches it");
        assertFalse(BossRiftCrystalPlacement.touches(CRYSTAL, 10.0D, 62.0D, -4.0D, PLAYER_HEIGHT),
                "nor three blocks under it");
        assertTrue(BossRiftCrystalPlacement.inZone(CRYSTAL, 10.0D, 63.0D, -4.0D),
                "which the zone, measured flat from its own floor, still counts");
    }

    @Test
    @DisplayName("a crystal hung out of arm's reach is in its zone and not touched, which is the difference")
    void aHighCrystalTellsTheTwoWaysApart() {
        // Three and a half blocks up: the zone under it counts anybody standing in it, while
        // touching it means getting up to it - a jump, or a block to stand on.
        Spot high = new Spot(3, 10.0D, 68.5D, -4.0D, 65.0D, 1.5D, "", 0xB47AFF);
        assertTrue(BossRiftCrystalPlacement.inZone(high, 10.0D, 65.0D, -4.0D), "standing under it is in the zone");
        assertFalse(BossRiftCrystalPlacement.touches(high, 10.0D, 65.0D, -4.0D, PLAYER_HEIGHT),
                "and out of reach of the crystal itself");
        assertTrue(BossRiftCrystalPlacement.touches(high, 10.0D, 66.0D, -4.0D, PLAYER_HEIGHT),
                "a jump reaches it");
    }

    @Test
    @DisplayName("the phase's switch picks which of the two counts")
    void theSwitchPicksTheWay() {
        Spot high = new Spot(3, 10.0D, 68.5D, -4.0D, 65.0D, 1.5D, "", 0xB47AFF);
        assertTrue(BossRiftCrystalPlacement.collects(high, BossRiftSettings.COLLECT_ZONE,
                10.0D, 65.0D, -4.0D, PLAYER_HEIGHT), "in the zone, and the zone is what counts");
        assertFalse(BossRiftCrystalPlacement.collects(high, BossRiftSettings.COLLECT_TOUCH,
                10.0D, 65.0D, -4.0D, PLAYER_HEIGHT), "the same spot does not reach the crystal");
        assertTrue(BossRiftCrystalPlacement.collects(high, BossRiftSettings.COLLECT_TOUCH,
                10.0D, 66.0D, -4.0D, PLAYER_HEIGHT));
        assertFalse(BossRiftCrystalPlacement.collects(high, BossRiftSettings.COLLECT_ZONE,
                10.0D, 68.0D, -4.0D, PLAYER_HEIGHT), "and a zone is still only its own storey");
    }

    @Test
    @DisplayName("a zone of its own width is measured by its own width")
    void aZoneKeepsItsOwnRadius() {
        Spot wide = new Spot(2, 0.0D, 67.0D, 0.0D, 65.0D, 4.0D, "", 0xFFFFFF);
        assertTrue(BossRiftCrystalPlacement.inZone(wide, 3.9D, 65.0D, 0.0D));
        assertFalse(BossRiftCrystalPlacement.inZone(wide, 4.1D, 65.0D, 0.0D));
        assertTrue(BossRiftCrystalPlacement.touches(wide, 3.0D, 65.0D, 0.0D, PLAYER_HEIGHT),
                "a wide zone reaches the crystal from further off as well");
    }

    @Test
    @DisplayName("gathering them all ends a crystal rift, and one left at the limit fails it")
    void theCrystalWayOutCounts() {
        assertEquals(Result.RUNNING, judge(BossRiftSettings.EXIT_CRYSTALS, START + 5, 0, 0, 4),
                "four still hanging");
        assertEquals(Result.RUNNING, judge(BossRiftSettings.EXIT_CRYSTALS, START + 5, 0, 0, 1),
                "one still hanging");
        assertEquals(Result.SUCCESS, judge(BossRiftSettings.EXIT_CRYSTALS, START + 5, 0, 0, 0),
                "the last one collected ends it at once");
        assertEquals(Result.FAILURE, judge(BossRiftSettings.EXIT_CRYSTALS, END, 0, 0, 1),
                "one left when the limit runs out");
        assertEquals(Result.SUCCESS, judge(BossRiftSettings.EXIT_CRYSTALS, END, 0, 0, 0),
                "and all of them gathered is a success at the limit too");
    }

    @Test
    @DisplayName("minions and crystals wants both: either one alone leaves the rift running")
    void bothWantsBoth() {
        assertEquals(Result.RUNNING, judge(BossRiftSettings.EXIT_BOTH, START + 5, 3, 2, 0),
                "every crystal gathered, two minions standing");
        assertEquals(Result.RUNNING, judge(BossRiftSettings.EXIT_BOTH, START + 5, 3, 0, 2),
                "every minion down, two crystals hanging");
        assertEquals(Result.SUCCESS, judge(BossRiftSettings.EXIT_BOTH, START + 5, 3, 0, 0), "both done");
        assertEquals(Result.FAILURE, judge(BossRiftSettings.EXIT_BOTH, END, 3, 1, 0), "a minion left at the limit");
        assertEquals(Result.FAILURE, judge(BossRiftSettings.EXIT_BOTH, END, 3, 0, 1), "a crystal left at the limit");
        // A rift whose clone could not be stood up asks for the crystals alone, rather than
        // hanging its players on minions that were never there.
        assertEquals(Result.SUCCESS, judge(BossRiftSettings.EXIT_BOTH, START + 5, 0, 0, 0));
        assertEquals(Result.RUNNING, judge(BossRiftSettings.EXIT_BOTH, START + 5, 0, 0, 1));
    }

    @Test
    @DisplayName("a rift that hung no crystals runs without them: its way out is lowered, not failed")
    void nothingToGatherIsLowered() {
        // What the manager does when not one crystal could be hung: the crystal way out becomes
        // the survival one, and "minions and crystals" becomes the minions alone.
        assertEquals(Result.SUCCESS, judge(BossRiftSettings.EXIT_SURVIVE, END, 0, 0, 3),
                "a lowered crystal rift succeeds on its time, whatever the crystals say");
        assertEquals(Result.RUNNING, judge(BossRiftSettings.EXIT_MINIONS, START + 5, 3, 1, 3),
                "and a lowered 'both' rift still waits for its minions");
        assertEquals(Result.SUCCESS, judge(BossRiftSettings.EXIT_MINIONS, START + 5, 3, 0, 3),
                "which is all it waits for");
    }

    private static Result judge(int exitMode, long now, int minionsSpawned, int minionsLeft, int crystalsLeft) {
        return BossRiftOutcome.judge(exitMode, now, END, 1, 1, 0, true, true,
                minionsSpawned, minionsLeft, crystalsLeft);
    }
}
