package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules a cone strike is started, kept and paid for by, checked without a world.
 *
 * <p>None of it throws when it is wrong. What the tester saw was "the boss uses it rarely":
 * a cone called off because its target ran past its length, a start refused because the
 * main target was of a kind the cone may not hit, a cooldown that had run out before a long
 * series was over. Each is one wrong boolean in here, and the only symptom is a boss that
 * seems to have forgotten one of its abilities.</p>
 */
class BossConeStartTest {

    private static final Vec3 ORIGIN = new Vec3(0.0D, 64.0D, 0.0D);
    private static final Vec3 EAST = new Vec3(1.0D, 0.0D, 0.0D);

    @Test
    @DisplayName("the warning's end calls nothing off by default, the fan under one rule, the reach under the other")
    void eachDodgeRuleAsksItsOwnQuestion() {
        for (boolean anyoneInFan : new boolean[]{true, false}) {
            for (boolean targetInReach : new boolean[]{true, false}) {
                assertTrue(BossConeRuntime.survivesWarning(BossPhaseData.CONE_DODGE_NEVER, anyoneInFan, targetInReach),
                        "the promised fan is the fan that lands, whoever left it");
                assertEquals(anyoneInFan, BossConeRuntime.survivesWarning(BossPhaseData.CONE_DODGE_SECTOR,
                        anyoneInFan, targetInReach), "the fan rule reads the fan and nothing else");
                assertEquals(targetInReach, BossConeRuntime.survivesWarning(BossPhaseData.CONE_DODGE_RANGE,
                        anyoneInFan, targetInReach), "the reach rule reads the reach and nothing else");
            }
        }
    }

    /**
     * The three rules on one committed fan: sixty degrees, ten blocks, opening east. The fan is
     * judged the way the strike judges it and the reach the way the target pick does, so this is
     * what each rule makes of a target that stayed, one that sidestepped and one that ran.
     */
    @Test
    @DisplayName("on a committed fan, sidestepping only dodges the fan rule and running only the reach rule")
    void theRulesOnACommittedFan() {
        assertDodge(5.0D, 10.0D, true, true, true, "still in the fan, nobody dodged anything");
        assertDodge(5.0D, 60.0D, true, false, true, "sidestepped out of the fan but still in reach");
        assertDodge(12.0D, 0.0D, true, false, false, "ran straight down the fan past its length");
        assertDodge(12.0D, 60.0D, true, false, false, "out of the fan and out of reach both");
    }

    private static void assertDodge(double distance, double degrees, boolean never, boolean sector, boolean range,
                                    String where) {
        double radians = Math.toRadians(degrees);
        double x = ORIGIN.x + Math.cos(radians) * distance;
        double z = ORIGIN.z + Math.sin(radians) * distance;
        boolean inFan = BossConeRuntime.inSector(ORIGIN, EAST, 60, 10, 3, x, ORIGIN.y, z);
        boolean inReach = distance <= 10.0D;
        assertEquals(never, BossConeRuntime.survivesWarning(BossPhaseData.CONE_DODGE_NEVER, inFan, inReach), where);
        assertEquals(sector, BossConeRuntime.survivesWarning(BossPhaseData.CONE_DODGE_SECTOR, inFan, inReach), where);
        assertEquals(range, BossConeRuntime.survivesWarning(BossPhaseData.CONE_DODGE_RANGE, inFan, inReach), where);
        assertFalse(inFan && !inReach, "nobody can be in a fan ten blocks long without being within ten blocks");
    }
}
