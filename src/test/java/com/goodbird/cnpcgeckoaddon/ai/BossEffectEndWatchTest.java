package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When an ability's end animation plays: on the first look its lasting effect is seen stopped
 * after running, and never for an effect a reset or a stun cut short, however long it outlives
 * the reset.
 */
class BossEffectEndWatchTest {

    @Test
    @DisplayName("an effect seen running and then stopped ends once, on the look it stopped")
    void endsOnce() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        assertFalse(watch.observe(BossAbilityKind.GEYSER, false), "never running, never ending");
        assertFalse(watch.observe(BossAbilityKind.GEYSER, true));
        assertFalse(watch.observe(BossAbilityKind.GEYSER, true), "still running is no end");
        assertTrue(watch.observe(BossAbilityKind.GEYSER, false));
        assertFalse(watch.observe(BossAbilityKind.GEYSER, false), "and the next look is not a second end");

        // The next cast is watched afresh.
        assertFalse(watch.observe(BossAbilityKind.GEYSER, true));
        assertTrue(watch.observe(BossAbilityKind.GEYSER, false));
    }

    @Test
    @DisplayName("kinds are watched apart")
    void kindsApart() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        watch.observe(BossAbilityKind.VENT, true);
        watch.observe(BossAbilityKind.HURRICANE, true);
        assertTrue(watch.observe(BossAbilityKind.VENT, false));
        assertFalse(watch.observe(BossAbilityKind.HURRICANE, true));
        assertTrue(watch.observe(BossAbilityKind.HURRICANE, false));
    }

    @Test
    @DisplayName("a reset passes over whatever it caught running, even when it ends ticks later")
    void aResetIsNoEnd() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        watch.observe(BossAbilityKind.HURRICANE, true);
        watch.observe(BossAbilityKind.GEYSER, true);
        watch.suppressAll();
        // Cleared with the reset: gone on the next look.
        assertFalse(watch.observe(BossAbilityKind.HURRICANE, false));
        // Outlived the reset - a fuse a phase change does not put out - and ran out later.
        assertFalse(watch.observe(BossAbilityKind.GEYSER, true));
        assertFalse(watch.observe(BossAbilityKind.GEYSER, true));
        assertFalse(watch.observe(BossAbilityKind.GEYSER, false), "its end was the reset's");
        // Watched again from the next cast on.
        assertFalse(watch.isSuppressed(BossAbilityKind.GEYSER));
        assertFalse(watch.observe(BossAbilityKind.GEYSER, true));
        assertTrue(watch.observe(BossAbilityKind.GEYSER, false));
    }

    @Test
    @DisplayName("a reset also covers an effect it never saw, started before the next look")
    void aResetCoversTheUnseen() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        watch.suppressAll();
        assertFalse(watch.observe(BossAbilityKind.SEISMIC, true), "running after the reset is the reset's");
        assertFalse(watch.observe(BossAbilityKind.SEISMIC, false));
        // A kind idle at the first look after the reset is free at once.
        watch.suppressAll();
        assertFalse(watch.observe(BossAbilityKind.VENT, false));
        assertFalse(watch.observe(BossAbilityKind.VENT, true));
        assertTrue(watch.observe(BossAbilityKind.VENT, false));
    }

    @Test
    @DisplayName("a stun's cut is no end; the other kinds run on and end as usual")
    void aStunIsNoEnd() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        watch.observe(BossAbilityKind.DASH, true);
        watch.observe(BossAbilityKind.HURRICANE, true);
        watch.suppress(BossAbilityKind.DASH);
        assertFalse(watch.observe(BossAbilityKind.DASH, false), "stopped by the stun, not run out");
        assertTrue(watch.observe(BossAbilityKind.HURRICANE, false), "a storm the stun did not touch ends on its own");
        assertFalse(watch.observe(BossAbilityKind.DASH, true));
        assertTrue(watch.observe(BossAbilityKind.DASH, false), "the next run ends as usual");
    }

    @Test
    @DisplayName("a kind left unwatched forgets what it saw, but not a reset's claim")
    void forgetting() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        watch.observe(BossAbilityKind.BEAM, true);
        watch.forget(BossAbilityKind.BEAM);
        assertFalse(watch.isRunning(BossAbilityKind.BEAM));
        assertFalse(watch.observe(BossAbilityKind.BEAM, false), "an end animation set after the start has nothing to end");

        watch.suppressAll();
        watch.forget(BossAbilityKind.BEAM);
        assertTrue(watch.isSuppressed(BossAbilityKind.BEAM));
        assertFalse(watch.observe(BossAbilityKind.BEAM, true));
        assertFalse(watch.observe(BossAbilityKind.BEAM, false));
    }

    @Test
    @DisplayName("a kind outside the list is never watched")
    void outsideTheList() {
        BossEffectEndWatch watch = new BossEffectEndWatch();
        assertFalse(watch.observe(-1, true));
        assertFalse(watch.observe(-1, false));
        assertFalse(watch.observe(BossAbilityKind.COUNT, true));
        assertFalse(watch.observe(BossAbilityKind.COUNT, false));
        watch.suppress(-1);
        watch.forget(BossAbilityKind.COUNT);
        assertFalse(watch.isSuppressed(BossAbilityKind.AREA));
    }
}
