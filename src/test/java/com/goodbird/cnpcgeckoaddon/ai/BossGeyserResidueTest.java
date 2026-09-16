package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossEffectData;
import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The residue's clock, checked without a world: the doses on their interval, the stacks
 * growing with the ticks inside up to their ceiling, fading outside a stack at a time, the
 * line dropped at nought, and the stacks reaching the potions' level.
 *
 * <p>None of it throws when it is wrong: a stack that never fades is a debuff a player cannot
 * get rid of, a dose a tick early is one nobody could have stepped out of, and stacks that
 * never reach the amplifier are a residue that only pretends to build up.</p>
 */
class BossGeyserResidueTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes(StandardCharsets.UTF_8));
    private static final UUID BORIS = UUID.nameUUIDFromBytes("boris".getBytes(StandardCharsets.UTF_8));

    /** The residue as it ships: two hundred ticks, a dose a second, a stack per two seconds up to four, fading one per three seconds. */
    private static BossGeyserResidue.Rules rules() {
        return new BossGeyserResidue.Rules(200, 20, 40, 4, 60, 40);
    }

    /** "tick:stacks" for every dose landed while ticking {@code victim} inside from {@code from} to {@code to}. */
    private static List<String> standIn(BossGeyserResidue residue, UUID victim, long from, long to) {
        List<String> doses = new ArrayList<>();
        for (long tick = from; tick <= to; tick++) {
            for (BossGeyserResidue.Hit hit : residue.tick(tick, List.of(victim))) {
                doses.add(tick + ":" + hit.stacks());
            }
        }
        return doses;
    }

    private static void standOut(BossGeyserResidue residue, long from, long to) {
        for (long tick = from; tick <= to; tick++) {
            assertTrue(residue.tick(tick, List.of()).isEmpty(), "nobody inside is nobody dosed, tick " + tick);
        }
    }

    @Test
    @DisplayName("standing in it is a dose on entry and every interval after, the stacks climbing one per stack ticks")
    void theStacksGrowWithTheTicksInside() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 0L);
        assertEquals(List.of("0:0", "20:0", "40:1", "60:1", "80:2", "100:2", "120:3", "140:3", "160:4"),
                standIn(residue, ANNA, 0L, 160L),
                "level one, then two, three, four and five over a hundred and sixty ticks");
        assertEquals(4, residue.stacks(ANNA));
        assertEquals(0, residue.stacks(BORIS), "no line, no stacks");
    }

    @Test
    @DisplayName("the stacks stop at the ceiling, and so does the count behind them")
    void theStacksAreCapped() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 0L);
        standIn(residue, ANNA, 0L, 999L);
        assertEquals(4, residue.stacks(ANNA));
        assertEquals(160, residue.ticksInside(ANNA),
                "held to the ceiling's worth of ticks, so a long stay fades as fast as a full one");
    }

    @Test
    @DisplayName("outside, a stack goes every decay ticks from the last tick inside, and the line is dropped at nought")
    void theStacksFadeOutside() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 0L);
        standIn(residue, ANNA, 0L, 159L);
        assertEquals(4, residue.stacks(ANNA));
        standOut(residue, 160L, 218L);
        assertEquals(4, residue.stacks(ANNA), "fifty-nine ticks out is not yet a stack lost");
        standOut(residue, 219L, 219L);
        assertEquals(3, residue.stacks(ANNA), "sixty ticks after the last tick inside, one stack is gone");
        standOut(residue, 220L, 278L);
        assertEquals(3, residue.stacks(ANNA));
        standOut(residue, 279L, 279L);
        assertEquals(2, residue.stacks(ANNA));
        standOut(residue, 280L, 339L);
        assertEquals(1, residue.stacks(ANNA));
        assertTrue(residue.hasLine(ANNA));
        standOut(residue, 340L, 399L);
        assertEquals(0, residue.stacks(ANNA));
        assertFalse(residue.hasLine(ANNA), "nothing left to fade, so the line is gone");
        assertEquals(0, residue.lines());
    }

    @Test
    @DisplayName("stepping back in carries on from wherever the stacks had faded to")
    void steppingBackInCarriesOn() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 0L);
        standIn(residue, ANNA, 0L, 159L);
        standOut(residue, 160L, 219L);
        assertEquals(3, residue.stacks(ANNA));
        assertEquals(List.of("220:3"), standIn(residue, ANNA, 220L, 220L),
                "dosed on the way back in, with the three stacks that were left");
        assertEquals(List.of("240:3", "260:4"), standIn(residue, ANNA, 221L, 260L),
                "and the count climbs on from where it was");
    }

    @Test
    @DisplayName("a tick that held the residue up fades every stack that fell due in the meantime")
    void aHeldResidueCatchesUpOnTheFade() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 0L);
        standIn(residue, ANNA, 0L, 159L);
        // Not ticked again until two fades have fallen due.
        residue.tick(280L, List.of());
        assertEquals(2, residue.stacks(ANNA));
    }

    @Test
    @DisplayName("a line at nought is kept until its dose clock has run out, so dancing on the edge is not dosed sooner than standing")
    void theLineWaitsOutTheDoseClock() {
        BossGeyserResidue residue = new BossGeyserResidue(new BossGeyserResidue.Rules(200, 20, 40, 4, 5, 40), 0L);
        assertEquals(List.of("0:0"), standIn(residue, ANNA, 0L, 0L));
        standOut(residue, 1L, 5L);
        assertEquals(0, residue.ticksInside(ANNA), "one tick inside faded on the fifth tick out");
        assertTrue(residue.hasLine(ANNA), "but the line stays while a dose is still owed");
        assertTrue(residue.tick(10L, List.of(ANNA)).isEmpty(), "back in before the interval is not dosed again");
        standOut(residue, 11L, 19L);
        assertTrue(residue.hasLine(ANNA));
        standOut(residue, 20L, 20L);
        assertFalse(residue.hasLine(ANNA), "gone once the dose clock has run out too");
        assertEquals(List.of("21:0"), standIn(residue, ANNA, 21L, 21L), "and a fresh line is dosed on entry");
    }

    @Test
    @DisplayName("two victims keep their own lines")
    void eachVictimHasTheirOwnLine() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 0L);
        for (long tick = 0L; tick < 100L; tick++) {
            residue.tick(tick, tick < 50L ? List.of(ANNA, BORIS) : List.of(ANNA));
        }
        assertEquals(2, residue.stacks(ANNA));
        assertEquals(1, residue.stacks(BORIS), "fifty ticks in and fifty out is still one stack");
        assertEquals(2, residue.lines());
        assertTrue(residue.tick(100L, List.of(ANNA, ANNA)).size() <= 1, "a victim listed twice is one line");
    }

    @Test
    @DisplayName("the residue dries up on its last tick, and its outline reads how much of it is left")
    void theResidueDriesUp() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 100L);
        assertEquals(300L, residue.endsAt());
        assertFalse(residue.isOver(299L));
        assertTrue(residue.isOver(300L));
        assertEquals(0.0F, residue.progress(100L), 1.0E-6F);
        assertEquals(0.5F, residue.progress(200L), 1.0E-6F);
        assertEquals(1.0F, residue.progress(300L), 1.0E-6F);
        assertEquals(0.0F, residue.progress(50L), 1.0E-6F, "held to the range either side");
    }

    @Test
    @DisplayName("the residue's noise falls on the tick it was left and every interval after")
    void theSoundKeepsItsInterval() {
        BossGeyserResidue residue = new BossGeyserResidue(rules(), 100L);
        assertTrue(residue.soundDue(100L));
        for (long tick = 101L; tick < 140L; tick++) {
            assertFalse(residue.soundDue(tick), "tick " + tick);
        }
        assertTrue(residue.soundDue(140L));
        assertFalse(residue.soundDue(60L), "nothing before it was left");
    }

    @Test
    @DisplayName("the rules are held to at least one each, so no clock can run every tick or never")
    void theRulesAreHeldToOne() {
        BossGeyserResidue.Rules rules = new BossGeyserResidue.Rules(0, 0, 0, 0, 0, 0);
        assertEquals(1, rules.lifetimeTicks());
        assertEquals(1, rules.intervalTicks());
        assertEquals(1, rules.stackTicks());
        assertEquals(1, rules.maxStacks());
        assertEquals(1, rules.decayTicks());
        assertEquals(1, rules.soundIntervalTicks());
        assertEquals(1, rules.ceilingTicks());
    }

    @Test
    @DisplayName("the look takes the residue's rules and its radius off the settings, nought being the geyser's radius")
    void theLookTakesTheRules() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        geyser.setResidueLifetimeTicks(300);
        geyser.setResidueIntervalTicks(10);
        geyser.setResidueStackTicks(50);
        geyser.setResidueMaxStacks(6);
        geyser.setResidueDecayTicks(80);
        geyser.setResidueSoundIntervalTicks(60);
        BossGeyserScheduler.Look look = BossGeyserScheduler.look(geyser);
        assertEquals(new BossGeyserResidue.Rules(300, 10, 50, 6, 80, 60), look.residueRules());
        assertEquals(3.0D, look.residueRadius(3.0D), 1.0E-9D);
        geyser.setResidueRadius(4);
        assertEquals(4.0D, BossGeyserScheduler.look(geyser).residueRadius(3.0D), 1.0E-9D);
        assertEquals(new BossGeyserResidue.Rules(300, 10, 50, 6, 80, 60), look.residueRules(),
                "the look is a snapshot: editing the settings after does not reach it");
    }

    @Test
    @DisplayName("the stacks reach the potion's amplifier and the fire's level, never above vanilla's ceiling nor below the slot's own")
    void theStacksReachTheAmplifier() {
        BossEffectData effect = new BossEffectData();
        effect.setLevel(1);
        assertEquals(0, effect.amplifierWith(0), "no stacks is the slot's own level");
        assertEquals(4, effect.amplifierWith(4), "four stacks on level one is level five");
        assertEquals(5, effect.levelWith(4));
        assertEquals(0, effect.amplifierWith(-3), "stacks only ever add");
        effect.setAmplifier(9);
        assertEquals(13, effect.amplifierWith(4));
        assertEquals(BossEffectData.MAX_AMPLIFIER, effect.amplifierWith(1000), "held under what the wire can carry");
        assertEquals(BossEffectData.MAX_AMPLIFIER + 1, effect.levelWith(1000));
    }
}
