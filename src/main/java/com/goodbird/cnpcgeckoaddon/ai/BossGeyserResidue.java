package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The residue an eruption leaves on the floor: how long it lies there, and for everyone who
 * has stood in it, how long they have and what that is worth in stacks. Kept apart from the
 * world so the doses, the stacks and their fading can be checked without one.
 *
 * <p>Each victim has a line of their own. Every tick inside counts one tick towards their
 * stacks - one stack per {@code stackTicks}, up to the ceiling - and a dose lands the tick
 * they step in and every {@code intervalTicks} after for as long as they stay. Outside, the
 * count fades a stack at a time, one every {@code decayTicks} from the last tick they were in,
 * and the line is dropped once nothing is left of it. Stepping back in carries on from
 * wherever the count had faded to.</p>
 *
 * <p>The count inside is held to the ceiling's worth of ticks, so somebody who stood in it
 * for a minute fades as fast as somebody who stood in it for exactly long enough: the stacks
 * are what the residue has on them, and there is nothing above full.</p>
 *
 * <p>A line at nought is kept until its dose clock has run out too. Without that a victim
 * who stepped out, waited for the line to drop and stepped straight back in would be dosed
 * sooner than one who stayed, and the residue would be worse to dance on than to stand in.</p>
 */
final class BossGeyserResidue {

    /** The residue's numbers, taken off the settings on the cast; each held to at least one. */
    record Rules(int lifetimeTicks, int intervalTicks, int stackTicks, int maxStacks, int decayTicks,
                 int soundIntervalTicks) {

        Rules {
            lifetimeTicks = Math.max(1, lifetimeTicks);
            intervalTicks = Math.max(1, intervalTicks);
            stackTicks = Math.max(1, stackTicks);
            maxStacks = Math.max(1, maxStacks);
            decayTicks = Math.max(1, decayTicks);
            soundIntervalTicks = Math.max(1, soundIntervalTicks);
        }

        /** The most ticks inside that count: past this the stacks are full. */
        int ceilingTicks() {
            return stackTicks * maxStacks;
        }
    }

    /** One dose owed on this tick: who, and how many stacks go on top of the potions' own level. */
    record Hit(UUID victim, int stacks) {
    }

    /** One victim's line: how long they have been in, and the two clocks on them. */
    private static final class Soak {
        private int ticksInside;
        private long nextHitAt;
        private long nextDecayAt;

        private Soak(long now) {
            // The first dose lands the tick they step in: the residue is painful to enter.
            nextHitAt = now;
            nextDecayAt = Long.MAX_VALUE;
        }
    }

    private final Rules rules;
    private final long startedAt;
    private final long endsAt;
    private final Map<UUID, Soak> soaks = new LinkedHashMap<>();

    BossGeyserResidue(Rules rules, long startedAt) {
        this.rules = rules;
        this.startedAt = startedAt;
        this.endsAt = startedAt + rules.lifetimeTicks();
    }

    long endsAt() {
        return endsAt;
    }

    /** Whether the residue has dried up; from here there is nothing left of it. */
    boolean isOver(long gameTime) {
        return gameTime >= endsAt;
    }

    /** How much of its life has passed: 0 the tick it was left, 1 the tick it dries up. */
    float progress(long gameTime) {
        return Mth.clamp((float) (gameTime - startedAt) / rules.lifetimeTicks(), 0.0F, 1.0F);
    }

    /** Whether the residue's own noise falls on this tick: on the tick it was left, then every interval. */
    boolean soundDue(long gameTime) {
        return gameTime >= startedAt && (gameTime - startedAt) % rules.soundIntervalTicks() == 0L;
    }

    /** The stacks the residue has on this victim right now; nought for anyone it has no line on. */
    int stacks(UUID victim) {
        Soak soak = soaks.get(victim);
        return soak == null ? 0 : stacksOf(soak);
    }

    /** How many ticks inside this victim's line counts, faded and all. */
    int ticksInside(UUID victim) {
        Soak soak = soaks.get(victim);
        return soak == null ? 0 : soak.ticksInside;
    }

    boolean hasLine(UUID victim) {
        return soaks.containsKey(victim);
    }

    int lines() {
        return soaks.size();
    }

    /**
     * Moves every line on by one tick: those inside count up and may be dosed, those outside
     * fade, and lines with nothing left are dropped.
     *
     * @param inside everyone standing in the residue on this tick
     * @return the doses that fall due on this tick, in the order the victims were given
     */
    List<Hit> tick(long gameTime, Collection<UUID> inside) {
        List<Hit> hits = new ArrayList<>();
        Set<UUID> in = new HashSet<>();
        for (UUID victim : inside) {
            if (!in.add(victim)) {
                continue;
            }
            Soak soak = soaks.computeIfAbsent(victim, id -> new Soak(gameTime));
            soak.ticksInside = Math.min(soak.ticksInside + 1, rules.ceilingTicks());
            // The fade is counted from the last tick inside, and starts a whole interval out.
            soak.nextDecayAt = gameTime + rules.decayTicks();
            if (gameTime >= soak.nextHitAt) {
                hits.add(new Hit(victim, stacksOf(soak)));
                soak.nextHitAt = gameTime + rules.intervalTicks();
            }
        }
        Iterator<Map.Entry<UUID, Soak>> lines = soaks.entrySet().iterator();
        while (lines.hasNext()) {
            Map.Entry<UUID, Soak> line = lines.next();
            if (in.contains(line.getKey())) {
                continue;
            }
            Soak soak = line.getValue();
            // Every fade that has fallen due, so a tick that held the residue up is not one
            // the victim keeps their stacks through.
            while (soak.ticksInside > 0 && gameTime >= soak.nextDecayAt) {
                soak.ticksInside = Math.max(0, soak.ticksInside - rules.stackTicks());
                soak.nextDecayAt += rules.decayTicks();
            }
            if (soak.ticksInside <= 0 && gameTime >= soak.nextHitAt) {
                lines.remove();
            }
        }
        return hits;
    }

    private int stacksOf(Soak soak) {
        return Math.min(rules.maxStacks(), soak.ticksInside / rules.stackTicks());
    }
}
