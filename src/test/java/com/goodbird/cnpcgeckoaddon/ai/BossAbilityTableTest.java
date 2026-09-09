package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that every row of {@link BossAbility} is wired to its own settings.
 *
 * <p>The table replaced five parallel switch statements, and the failure it has to be held
 * to is the one those had: a row copied from the one above it and left pointing at the
 * neighbour's getter. Nothing crashes when that happens - the ability simply runs on
 * somebody else's cooldown, or never fires because it reads a flag no one turns on - so it
 * is invisible in play and has to be caught here.</p>
 *
 * <p>The wiring below is written out a second time on purpose. A test that asked the table
 * what it holds would agree with any table at all; this one states independently what each
 * ability is supposed to answer to, so the two have to be changed together.</p>
 */
class BossAbilityTableTest {

    /**
     * How to switch one ability on, how to give it a cooldown, and where its cast spot lives,
     * said without the table.
     */
    private record Wiring(Consumer<BossPhaseData> enable, ObjIntConsumer<BossPhaseData> cooldown,
                          Function<BossPhaseData, BossCastSpot> spot) {
    }

    private static final Map<BossAbility, Wiring> WIRING = new EnumMap<>(Map.ofEntries(
            Map.entry(BossAbility.GROUND_ATTACK, new Wiring(
                    phase -> phase.areaAttack().setEnabled(true),
                    (phase, ticks) -> phase.areaAttack().setCooldownTicks(ticks),
                    phase -> phase.areaAttack().castSpot())),
            Map.entry(BossAbility.RANGED_ATTACK, new Wiring(
                    phase -> phase.rangedAttack().setEnabled(true),
                    (phase, ticks) -> phase.rangedAttack().setCooldownTicks(ticks),
                    phase -> phase.rangedAttack().castSpot())),
            Map.entry(BossAbility.MELEE_ATTACK, new Wiring(
                    phase -> phase.meleeAttack().setEnabled(true),
                    (phase, ticks) -> phase.meleeAttack().setCooldownTicks(ticks),
                    phase -> phase.meleeAttack().castSpot())),
            Map.entry(BossAbility.FLUID_SPIT, new Wiring(
                    phase -> phase.fluidSpit().setEnabled(true),
                    (phase, ticks) -> phase.fluidSpit().setCooldownTicks(ticks),
                    phase -> phase.fluidSpit().castSpot())),
            Map.entry(BossAbility.HOOK, new Wiring(
                    phase -> phase.hook().setEnabled(true),
                    (phase, ticks) -> phase.hook().setCooldownTicks(ticks),
                    phase -> phase.hook().castSpot())),
            Map.entry(BossAbility.CAPTURE, new Wiring(
                    phase -> phase.capture().setEnabled(true),
                    (phase, ticks) -> phase.capture().setCooldownTicks(ticks),
                    phase -> phase.capture().castSpot())),
            Map.entry(BossAbility.LEAP, new Wiring(
                    phase -> phase.leap().setEnabled(true),
                    (phase, ticks) -> phase.leap().setCooldownTicks(ticks),
                    phase -> phase.leap().castSpot())),
            Map.entry(BossAbility.LINE_ATTACK, new Wiring(
                    phase -> phase.lineAttack().setEnabled(true),
                    (phase, ticks) -> phase.lineAttack().setCooldownTicks(ticks),
                    phase -> phase.lineAttack().castSpot())),
            Map.entry(BossAbility.GEYSER, new Wiring(
                    phase -> phase.geyser().setEnabled(true),
                    (phase, ticks) -> phase.geyser().setCooldownTicks(ticks),
                    phase -> phase.geyser().castSpot())),
            Map.entry(BossAbility.BOULDER, new Wiring(
                    phase -> phase.boulder().setEnabled(true),
                    (phase, ticks) -> phase.boulder().setCooldownTicks(ticks),
                    phase -> phase.boulder().castSpot())),
            Map.entry(BossAbility.BOULDER_RAIN, new Wiring(
                    phase -> phase.boulderRain().setEnabled(true),
                    (phase, ticks) -> phase.boulderRain().setCooldownTicks(ticks),
                    phase -> phase.boulderRain().castSpot())),
            Map.entry(BossAbility.TETHER, new Wiring(
                    phase -> phase.tether().setEnabled(true),
                    (phase, ticks) -> phase.tether().setCooldownTicks(ticks),
                    phase -> phase.tether().castSpot())),
            Map.entry(BossAbility.GRAVITY, new Wiring(
                    phase -> phase.gravity().setEnabled(true),
                    (phase, ticks) -> phase.gravity().setCooldownTicks(ticks),
                    phase -> phase.gravity().castSpot())),
            Map.entry(BossAbility.MARK, new Wiring(
                    phase -> phase.mark().setEnabled(true),
                    (phase, ticks) -> phase.mark().setCooldownTicks(ticks),
                    phase -> phase.mark().castSpot())),
            Map.entry(BossAbility.COVER, new Wiring(
                    phase -> phase.cover().setEnabled(true),
                    (phase, ticks) -> phase.cover().setCooldownTicks(ticks),
                    phase -> phase.cover().castSpot())),
            Map.entry(BossAbility.HUNT, new Wiring(
                    phase -> phase.hunt().setEnabled(true),
                    (phase, ticks) -> phase.hunt().setCooldownTicks(ticks),
                    phase -> phase.hunt().castSpot())),
            Map.entry(BossAbility.BEAM, new Wiring(
                    phase -> phase.beam().setEnabled(true),
                    (phase, ticks) -> phase.beam().setCooldownTicks(ticks),
                    phase -> phase.beam().castSpot())),
            Map.entry(BossAbility.COCOON, new Wiring(
                    phase -> {
                        phase.cocoon().setEnabled(true);
                        // A cocoon with no clone to build it out of is not an ability yet.
                        phase.cocoon().setCloneName("cocoon");
                    },
                    (phase, ticks) -> phase.cocoon().setCooldownTicks(ticks),
                    phase -> phase.cocoon().castSpot())),
            Map.entry(BossAbility.SUMMON, new Wiring(
                    phase -> {
                        phase.summon().setEnabled(true);
                        phase.summon().setCloneName("minion");
                    },
                    (phase, ticks) -> phase.summon().setCooldownTicks(ticks),
                    phase -> phase.summon().castSpot()))));

    @Test
    @DisplayName("the rotation is exactly the abilities that run off a cooldown")
    void rotationHoldsEveryConfigurableAbility() {
        assertEquals(WIRING.keySet(), Set.copyOf(BossAbility.ROTATION),
                "an ability was added to the table but not wired up, or the other way round");
        assertFalse(BossAbility.ROTATION.contains(BossAbility.TELEPORT),
                "the teleport runs off its own random delay range, not off the rotation");
        assertFalse(BossAbility.ROTATION.contains(BossAbility.NONE));
        assertEquals(BossAbility.ROTATION.size(), Set.copyOf(BossAbility.ROTATION).size(),
                "the rotation lists an ability twice, so it would get two turns per round");
    }

    @Test
    @DisplayName("every rotation row names a distinct slot of the shared ability list")
    void rotationKindsAreDistinctAndInRange() {
        Set<Integer> seen = new HashSet<>();
        for (BossAbility ability : BossAbility.ROTATION) {
            int kind = ability.kind();
            assertTrue(kind >= 0 && kind < BossAbilityKind.COUNT,
                    ability + " indexes the shared ability list out of bounds: " + kind);
            assertTrue(seen.add(kind),
                    ability + " shares slot " + kind + " with another ability, so the warning "
                            + "and cast-root masks cannot tell them apart");
        }
    }

    @Test
    @DisplayName("the teleport and the idle boss are on no ability list")
    void unlistedActionsCarryNoKind() {
        assertEquals(BossAbility.NO_KIND, BossAbility.TELEPORT.kind());
        assertEquals(BossAbility.NO_KIND, BossAbility.NONE.kind());
        assertFalse(BossAbility.TELEPORT.isScheduledFromCooldown());
        assertFalse(BossAbility.NONE.isScheduledFromCooldown());
        BossPhaseData phase = new BossPhaseData();
        assertNull(BossAbility.TELEPORT.castSpot(phase), "a teleport has no settings to keep a spot in");
        assertNull(BossAbility.NONE.castSpot(phase), "doing nothing is done from nowhere in particular");
    }

    @Test
    @DisplayName("a phase straight out of the box has no ability switched on")
    void freshPhaseHasNothingEnabled() {
        BossPhaseData phase = new BossPhaseData();
        for (BossAbility ability : BossAbility.ROTATION) {
            assertFalse(ability.isEnabledIn(phase),
                    ability + " reads as switched on before anything was configured, so it is "
                            + "reading somebody else's flag");
        }
    }

    @Test
    @DisplayName("switching one ability on switches on that one and no other")
    void enablingOneAbilityEnablesOnlyThatRow() {
        for (BossAbility ability : BossAbility.ROTATION) {
            BossPhaseData phase = new BossPhaseData();
            WIRING.get(ability).enable().accept(phase);
            for (BossAbility other : BossAbility.ROTATION) {
                boolean enabled = other.isEnabledIn(phase);
                if (other == ability) {
                    assertTrue(enabled, ability + " stayed off after its own setting was "
                            + "switched on, so its row points at the wrong flag");
                } else {
                    assertFalse(enabled, other + " came on when only " + ability
                            + " was switched on, so the two rows read the same flag");
                }
            }
        }
    }

    @Test
    @DisplayName("every row reads back its own cooldown and nobody else's")
    void everyRowReadsItsOwnCooldown() {
        BossPhaseData phase = new BossPhaseData();
        // Distinct and comfortably inside every ability's clamp, so a row reading the wrong
        // getter comes back with a number that belongs to a neighbour.
        List<BossAbility> rotation = BossAbility.ROTATION;
        for (int i = 0; i < rotation.size(); i++) {
            WIRING.get(rotation.get(i)).cooldown().accept(phase, 101 + i * 7);
        }
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < rotation.size(); i++) {
            BossAbility ability = rotation.get(i);
            int expected = 101 + i * 7;
            int actual = ability.cooldownTicks(phase);
            if (actual != expected) {
                wrong.add(ability + " read " + actual + " where " + expected + " was set");
            }
        }
        assertTrue(wrong.isEmpty(), "rows are wired to the wrong cooldown: " + wrong);
    }

    @Test
    @DisplayName("every row hands out its own cast spot and nobody else's")
    void everyRowHandsOutItsOwnCastSpot() {
        BossPhaseData phase = new BossPhaseData();
        // Identity, not equality: every unset spot looks the same, and the failure this is
        // after is a row handing out its neighbour's spot, which would then be edited on one
        // screen and obeyed by another ability.
        Set<BossCastSpot> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BossAbility ability : BossAbility.ROTATION) {
            BossCastSpot spot = ability.castSpot(phase);
            assertNotNull(spot, ability + " has no cast spot at all");
            assertSame(WIRING.get(ability).spot().apply(phase), spot,
                    ability + " hands out a spot that is not the one its own settings hold");
            assertTrue(seen.add(spot), ability + " shares a cast spot with another row");
        }
        // And each one is really the one the phase saves under that ability's name: setting
        // it through the table has to reach the tag under the right prefix.
        BossAbility.BEAM.castSpot(phase).setMode(BossCastSpot.MODE_WALK);
        assertEquals(BossCastSpot.MODE_WALK, phase.writeToNBT().getInt("BeamSpotMode"));
        assertEquals(BossCastSpot.MODE_NONE, phase.writeToNBT().getInt("GeyserSpotMode"));
    }

    @Test
    @DisplayName("the table is complete enough for the controller to load")
    void controllerAcceptsTheTable() {
        // The controller's static initialiser refuses to load when the starter map and the
        // rotation disagree, which is the other half of the same wiring.
        assertNotNull(assertDoesNotThrowLoading());
    }

    @Test
    @DisplayName("everything that can be wound up also has something to carry it out")
    void everyWoundUpActionIsPerformed() {
        // Stated here rather than read off the controller, for the reason the wiring above is:
        // an ability that can be started and then does nothing is invisible in play - the boss
        // winds up, the animation runs, and the hit never lands.
        Set<BossAbility> expected = EnumSet.copyOf(BossAbility.ROTATION);
        expected.add(BossAbility.TELEPORT);
        assertEquals(expected, TeleportPathController.PERFORMED_ACTIONS,
                "an action can be wound up with nothing to carry it out, or the other way round");
        assertFalse(TeleportPathController.PERFORMED_ACTIONS.contains(BossAbility.NONE),
                "doing nothing is not an action that gets performed");
    }

    private static Class<?> assertDoesNotThrowLoading() {
        try {
            return Class.forName(TeleportPathController.class.getName(), true,
                    BossAbilityTableTest.class.getClassLoader());
        } catch (ExceptionInInitializerError | ClassNotFoundException error) {
            throw new AssertionError("the boss controller refused to load: " + error.getCause(), error);
        }
    }
}
