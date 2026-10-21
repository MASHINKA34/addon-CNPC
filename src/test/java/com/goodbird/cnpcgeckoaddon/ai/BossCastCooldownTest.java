package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One draw of a spread cooldown per cast, for the two casts that stamp theirs more than once - the
 * ranged attack's burst and the cone's series - and none at all without a spread. Two draws for one
 * cast, the later kept only when longer, pulled the cooldown towards the long end of the spread.
 *
 * <p>Each case walks the stamps the runtime makes, in its order, against a random source that counts
 * what is drawn from it: the ranged attack stamps as it starts and from its last shot, a stagger
 * included; the cone stamps where its cooldown counts from - the start, or its last cone - and again
 * when a stagger cuts a series short.</p>
 */
class BossCastCooldownTest {

    private static final int COOLDOWN = 200;
    private static final int SPREAD = 100;

    @Test
    @DisplayName("a burst with a spread draws once: its start and its end - last shot or stagger - stamp one cooldown")
    void aBurstDrawsOnce() {
        Cast cast = new Cast(BossAbilityKind.RANGED, SPREAD);
        int atStart = cast.start();
        int atEnd = cast.stamp();
        assertEquals(1, cast.random.draws);
        assertEquals(atStart, atEnd, "the end's stamp is the draw the start made");
        assertTrue(Math.abs(atStart - COOLDOWN) <= SPREAD);
    }

    @Test
    @DisplayName("a cone draws once whichever way its cooldown counts, run to its last cone or staggered")
    void aConeDrawsOnce() {
        for (int cooldownFrom : new int[]{BossPhaseData.CONE_COOLDOWN_FROM_START,
                BossPhaseData.CONE_COOLDOWN_FROM_END}) {
            for (boolean staggered : new boolean[]{false, true}) {
                Cast cast = new Cast(BossAbilityKind.CONE, SPREAD);
                cast.coneStart(cooldownFrom);
                // Counted from the start the draw is made now; from the last cone the start draws nothing.
                assertEquals(BossConeRuntime.countsFromStart(cooldownFrom) ? 1 : 0, cast.random.draws,
                        "as it starts, counting from " + cooldownFrom);
                if (staggered) {
                    cast.coneStagger();
                } else {
                    cast.coneEnd(cooldownFrom);
                }
                assertEquals(1, cast.random.draws, "counting from " + cooldownFrom
                        + (staggered ? ", staggered" : ", to its last cone"));
            }
        }
    }

    @Test
    @DisplayName("a stagger of a cone counted from its start takes the draw the cast made")
    void aStaggerTakesTheCastsDraw() {
        Cast cast = new Cast(BossAbilityKind.CONE, SPREAD);
        int atStart = cast.coneStart(BossPhaseData.CONE_COOLDOWN_FROM_START);
        assertEquals(atStart, cast.coneStagger());
        assertTrue(Math.abs(atStart - COOLDOWN) <= SPREAD);
        assertTrue(BossConeRuntime.countsFromStart(BossPhaseData.CONE_COOLDOWN_FROM_START));
        assertFalse(BossConeRuntime.countsFromStart(BossPhaseData.CONE_COOLDOWN_FROM_END));
    }

    @Test
    @DisplayName("without a spread nothing is drawn, and every stamp is the cooldown as it always was")
    void noSpreadNoDraw() {
        Cast burst = new Cast(BossAbilityKind.RANGED, 0);
        assertEquals(COOLDOWN, burst.start());
        assertEquals(COOLDOWN, burst.stamp());
        assertEquals(0, burst.random.draws);
        assertEquals(2, burst.worked, "worked out afresh at both stamps, as before");
        for (int cooldownFrom : new int[]{BossPhaseData.CONE_COOLDOWN_FROM_START,
                BossPhaseData.CONE_COOLDOWN_FROM_END}) {
            Cast cone = new Cast(BossAbilityKind.CONE, 0);
            cone.coneStart(cooldownFrom);
            cone.coneEnd(cooldownFrom);
            assertEquals(COOLDOWN, cone.coneStagger());
            assertEquals(0, cone.random.draws, "counting from " + cooldownFrom);
        }
    }

    @Test
    @DisplayName("every cast draws its own: the next one is not held to the last one's draw")
    void eachCastItsOwnDraw() {
        Cast cast = new Cast(BossAbilityKind.RANGED, SPREAD);
        cast.start();
        cast.stamp();
        cast.start();
        cast.stamp();
        assertEquals(2, cast.random.draws);
    }

    /** One ability's casts against a counting random source, the runtime's way round. */
    private static final class Cast {
        private final BossCastCooldown cooldown = new BossCastCooldown();
        private final CountingRandom random = new CountingRandom(20261021L);
        private final boolean spread;
        private final IntSupplier roll;
        /** How many times the cooldown was worked out, drawn or not. */
        private int worked;

        private Cast(int kind, int jitter) {
            BossPhaseData phase = new TeleportPathData().getPhase(0);
            phase.profile(kind).setCooldownJitterTicks(jitter);
            spread = BossCastCooldown.spreads(phase, kind);
            // The draw rolledCooldownTicks makes, without the enrage, which is no draw.
            roll = () -> {
                worked++;
                return phase.profile(kind).spreadCooldown(COOLDOWN, random);
            };
        }

        /** A new cast stamping as it starts. */
        int start() {
            cooldown.reset();
            return stamp();
        }

        /** Any later stamp of the same cast. */
        int stamp() {
            return cooldown.ticks(spread, roll);
        }

        /** A cone starting: its cooldown wanted now only when it counts from the start. */
        int coneStart(int cooldownFrom) {
            cooldown.reset();
            return BossConeRuntime.countsFromStart(cooldownFrom) ? stamp() : 0;
        }

        /** Its last cone: the cooldown wanted only when it counts from there. */
        void coneEnd(int cooldownFrom) {
            if (!BossConeRuntime.countsFromStart(cooldownFrom)) {
                stamp();
            }
        }

        /** A stagger cutting the series short: the cooldown wanted from here whichever way it counts. */
        int coneStagger() {
            return stamp();
        }
    }

    /** A seeded random source that counts every number drawn from it. */
    private static final class CountingRandom implements RandomSource {
        private final RandomSource delegate;
        private int draws;

        private CountingRandom(long seed) {
            delegate = RandomSource.create(seed);
        }

        @Override
        public RandomSource fork() {
            return delegate.fork();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return delegate.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            delegate.setSeed(seed);
        }

        @Override
        public int nextInt() {
            draws++;
            return delegate.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            draws++;
            return delegate.nextInt(bound);
        }

        @Override
        public long nextLong() {
            draws++;
            return delegate.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            draws++;
            return delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            draws++;
            return delegate.nextFloat();
        }

        @Override
        public double nextDouble() {
            draws++;
            return delegate.nextDouble();
        }

        @Override
        public double nextGaussian() {
            draws++;
            return delegate.nextGaussian();
        }
    }
}
