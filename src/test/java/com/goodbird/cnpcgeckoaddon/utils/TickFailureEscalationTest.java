package com.goodbird.cnpcgeckoaddon.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss controller's answer to a tick that keeps failing: skipped, then the fight reset,
 * then the controller switched off - each once per run of failures.
 */
class TickFailureEscalationTest {

    /** The step each failed tick was answered with, numbered from 1. */
    private static List<Integer> ticksAnsweredWith(TickFailureEscalation escalation,
                                                   TickFailureEscalation.Step step, int failures) {
        List<Integer> ticks = new ArrayList<>();
        for (int tick = 1; tick <= failures; tick++) {
            if (escalation.failed() == step) {
                ticks.add(tick);
            }
        }
        return ticks;
    }

    @Test
    @DisplayName("ten seconds of failures reset the fight and ten more switch the controller off, once each")
    void escalatesOnceAtEachLimit() {
        assertEquals(200, TickFailureEscalation.RESET_AFTER);
        assertEquals(400, TickFailureEscalation.DISABLE_AFTER);

        TickFailureEscalation resets = new TickFailureEscalation();
        assertEquals(List.of(200), ticksAnsweredWith(resets, TickFailureEscalation.Step.RESET, 1000));

        TickFailureEscalation disables = new TickFailureEscalation();
        assertEquals(List.of(400), ticksAnsweredWith(disables, TickFailureEscalation.Step.DISABLE, 1000));
        assertEquals(400, disables.consecutiveFailures(), "the count stops at the limit");
    }

    @Test
    @DisplayName("a tick that goes through ends the run, so scattered failures never escalate")
    void aGoodTickStartsTheCountOver() {
        TickFailureEscalation escalation = new TickFailureEscalation();
        for (int round = 0; round < 10; round++) {
            for (int tick = 0; tick < TickFailureEscalation.RESET_AFTER - 1; tick++) {
                assertEquals(TickFailureEscalation.Step.SKIP, escalation.failed());
            }
            escalation.succeeded();
            assertEquals(0, escalation.consecutiveFailures());
        }
    }

    @Test
    @DisplayName("a new run after a good tick escalates all over again")
    void aSecondRunEscalatesAgain() {
        TickFailureEscalation escalation = new TickFailureEscalation();
        assertTrue(ticksAnsweredWith(escalation, TickFailureEscalation.Step.DISABLE, 400).contains(400));
        escalation.succeeded();
        assertEquals(List.of(200), ticksAnsweredWith(escalation, TickFailureEscalation.Step.RESET, 300));
    }
}
