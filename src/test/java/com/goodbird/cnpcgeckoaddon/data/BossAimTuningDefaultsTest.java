package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the aim of the three strikes that look at their victim before they land, and the two
 * reach rules the melee and the ranged attack used to have hard-wired.
 *
 * <p>Thirty degrees a tick is what every {@code setLookAt} in these three asked for, and half
 * the squared range is where the shot started arcing. Both are what a builder's boss does
 * today, so both are written out as literals rather than read off the field they came from.</p>
 */
class BossAimTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("all three aim at thirty degrees a tick, the way their look calls asked")
    void theAimIsYesterdaysThirtyDegrees() {
        assertEquals(30, new BossMeleeAttackSettings().getAimTurnDegrees());
        assertEquals(30, new BossRangedAttackSettings().getAimTurnDegrees());
        assertEquals(30, new BossFluidSpitSettings().getAimTurnDegrees());
    }

    @Test
    @DisplayName("the melee reach still counts both models, and the shot still arcs past half")
    void theReachRulesAreYesterdays() {
        assertTrue(new BossMeleeAttackSettings().isReachAddsModels(),
                "the reach was measured hitbox to hitbox");
        BossRangedAttackSettings ranged = new BossRangedAttackSettings();
        assertEquals(50, ranged.getLobSharePercent());
        // The default range is 24, so half its square is what the shot compared against.
        assertEquals(24 * 24 / 2.0D, ranged.lobBeyondSquared(), EPSILON);
    }

    @Test
    @DisplayName("the share follows the range it is a share of")
    void theShareFollowsTheRange() {
        BossRangedAttackSettings ranged = new BossRangedAttackSettings();
        ranged.setRange(0, 40);
        assertEquals(40 * 40 / 2.0D, ranged.lobBeyondSquared(), EPSILON);
        ranged.setLobSharePercent(20);
        assertEquals(40 * 40 * 0.2D, ranged.lobBeyondSquared(), EPSILON,
                "a fifth of the square is nearly half the distance, so the shot arcs far more often");
        ranged.setLobSharePercent(100);
        assertEquals(40 * 40.0D, ranged.lobBeyondSquared(), EPSILON, "a hundred per cent never arcs");
    }

    @Test
    @DisplayName("a boss saved before any of this existed aims and reaches the way it always did")
    void anOldSaveKeepsTheOldBehaviour() {
        BossPhaseData written = new BossPhaseData();
        // Changed first, so the load has to put the defaults back rather than leave them be.
        written.meleeAttack().setAimTurnDegrees(1);
        written.meleeAttack().setReachAddsModels(false);
        written.rangedAttack().setAimTurnDegrees(180);
        written.rangedAttack().setLobSharePercent(10);
        written.fluidSpit().setAimTurnDegrees(90);
        CompoundTag old = written.writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (key.equals("MeleeAimTurn") || key.equals("MeleeReachModels")
                    || key.equals("RangedAimTurn") || key.equals("RangedLobShare")
                    || key.equals("FluidAimTurn")) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        assertEquals(30, reloaded.meleeAttack().getAimTurnDegrees());
        assertTrue(reloaded.meleeAttack().isReachAddsModels());
        assertEquals(30, reloaded.rangedAttack().getAimTurnDegrees());
        assertEquals(50, reloaded.rangedAttack().getLobSharePercent());
        assertEquals(30, reloaded.fluidSpit().getAimTurnDegrees());
    }
}
