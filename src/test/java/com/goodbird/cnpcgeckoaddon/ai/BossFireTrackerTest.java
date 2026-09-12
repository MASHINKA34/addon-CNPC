package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how the boss fire bookkeeping keeps and replaces its burns, without a world: the fire
 * itself cannot be lit here, but when a burn stops multiplying, and which of two burns wins,
 * is plain arithmetic on game ticks.
 */
class BossFireTrackerTest {
    private final UUID victim = UUID.randomUUID();

    @Test
    @DisplayName("a burn multiplies up to the tick it was lit until, and not on that tick")
    void aBurnEndsOnItsOwnTick() {
        BossFireTracker.Burns burns = new BossFireTracker.Burns();
        burns.light(victim, Level.OVERWORLD, 3, 120L, 20L);
        assertEquals(3, burns.multiplier(victim, 20L));
        assertEquals(3, burns.multiplier(victim, 119L));
        assertEquals(1, burns.multiplier(victim, 120L), "a burn that is over should bite the vanilla way");
        assertEquals(1, burns.multiplier(UUID.randomUUID(), 20L), "nobody else was lit");
    }

    @Test
    @DisplayName("vanilla-strength fire is not remembered and pushes nothing out")
    void levelOneIsNotTracked() {
        BossFireTracker.Burns burns = new BossFireTracker.Burns();
        burns.light(victim, Level.OVERWORLD, 1, 500L, 0L);
        assertTrue(burns.isEmpty());

        burns.light(victim, Level.OVERWORLD, 4, 100L, 0L);
        burns.light(victim, Level.OVERWORLD, 1, 500L, 10L);
        assertEquals(4, burns.multiplier(victim, 99L));
        assertEquals(1, burns.multiplier(victim, 100L));
    }

    @Test
    @DisplayName("a stronger burn replaces a weaker one, a weaker one never a lit stronger one")
    void strengthDecidesBetweenTwoLitBurns() {
        BossFireTracker.Burns burns = new BossFireTracker.Burns();
        burns.light(victim, Level.OVERWORLD, 2, 300L, 0L);
        burns.light(victim, Level.OVERWORLD, 5, 60L, 10L);
        assertEquals(5, burns.multiplier(victim, 59L));
        assertEquals(1, burns.multiplier(victim, 60L), "the stronger burn takes the weaker one's place whole");

        burns.light(victim, Level.OVERWORLD, 5, 200L, 70L);
        burns.light(victim, Level.OVERWORLD, 3, 900L, 80L);
        assertEquals(5, burns.multiplier(victim, 199L), "a weaker burn must not cool a stronger one down");
        assertEquals(1, burns.multiplier(victim, 200L));
    }

    @Test
    @DisplayName("an equally strong burn only ever makes it last longer")
    void anEqualBurnExtends() {
        BossFireTracker.Burns burns = new BossFireTracker.Burns();
        burns.light(victim, Level.OVERWORLD, 3, 100L, 0L);
        burns.light(victim, Level.OVERWORLD, 3, 80L, 20L);
        assertEquals(3, burns.multiplier(victim, 99L), "a shorter repeat must not cut the burn short");

        burns.light(victim, Level.OVERWORLD, 3, 150L, 40L);
        assertEquals(3, burns.multiplier(victim, 149L), "a hazard dosing again keeps its burn going");
        assertEquals(1, burns.multiplier(victim, 150L));
    }

    @Test
    @DisplayName("a burn that is over gives way to any new one, weaker included")
    void anOverBurnIsReplacedByAnything() {
        BossFireTracker.Burns burns = new BossFireTracker.Burns();
        burns.light(victim, Level.OVERWORLD, 9, 100L, 0L);
        burns.light(victim, Level.OVERWORLD, 2, 250L, 100L);
        assertEquals(2, burns.multiplier(victim, 249L));
    }

    @Test
    @DisplayName("a sweep drops this dimension's burns that are over or went out, and nothing else")
    void sweepDropsOnlyWhatIsDone() {
        BossFireTracker.Burns burns = new BossFireTracker.Burns();
        UUID over = UUID.randomUUID();
        UUID doused = UUID.randomUUID();
        UUID burning = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        burns.light(over, Level.OVERWORLD, 2, 100L, 0L);
        burns.light(doused, Level.OVERWORLD, 2, 400L, 0L);
        burns.light(burning, Level.OVERWORLD, 2, 400L, 0L);
        burns.light(elsewhere, Level.NETHER, 2, 50L, 0L);

        burns.sweep(Level.OVERWORLD, 100L, Set.of(doused)::contains);
        assertEquals(1, burns.multiplier(over, 99L), "a burn past its tick should be dropped");
        assertEquals(1, burns.multiplier(doused, 150L), "a victim whose fire went out should be dropped");
        assertEquals(2, burns.multiplier(burning, 150L));
        assertEquals(2, burns.multiplier(elsewhere, 49L),
                "another dimension's burns are that dimension's sweep to drop");

        burns.clear(Level.NETHER);
        assertEquals(1, burns.multiplier(elsewhere, 49L));
        assertFalse(burns.isEmpty());
        burns.forget(burning);
        assertTrue(burns.isEmpty());
    }
}
