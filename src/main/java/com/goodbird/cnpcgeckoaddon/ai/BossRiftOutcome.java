package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;

import java.util.EnumSet;
import java.util.Set;

/**
 * How a reality rift goes, decided without a world: whether it is a solo one, when it is over and
 * which way it went, and what the arena owes for a failure.
 *
 * <p>Every input is a number the rift's manager counts on its tick, so the rules can be checked by
 * a test that has no players, no minions and no pocket dimension to count them in.</p>
 */
public final class BossRiftOutcome {

    /** Where a rift stands on one tick. */
    public enum Result {
        /** Still open. */
        RUNNING,
        /** The taken players did what they were sent for. */
        SUCCESS,
        /** They did not, in time, or none of them lived through it. */
        FAILURE,
        /** Nobody is left in it and nobody died failing it: it closes and the fight goes on as it was. */
        EMPTY
    }

    /** What a failed rift costs, each one its own switch. */
    public enum Penalty {
        /** The boss enrages, unless it already has. */
        RAGE,
        /** Everyone fighting within the failure's radius of the boss is hit. */
        ARENA_DAMAGE,
        /** Everyone fighting within that radius is given the failure's potions. */
        EFFECTS,
        /** The boss heals a share of its maximum health. */
        HEAL
    }

    private BossRiftOutcome() {
    }

    /** Whether a rift is a solo one: no more fighting on the arena than the phase allows. */
    public static boolean isSolo(int fightersOnArena, int soloMaxPlayers) {
        return fightersOnArena <= soloMaxPlayers;
    }

    /**
     * Where a rift stands.
     *
     * @param exitMode       the way out as it runs, see {@link BossRiftSettings#effectiveExitMode}
     * @param gameTime       now
     * @param endsAt         the end of the survival rift, and every other way out's limit
     * @param taken          how many players the rift took
     * @param inside         how many of them are still in it, online or not
     * @param deaths         how many of them died in it
     * @param failOnDeath    whether everyone taken dying is a failure
     * @param minionsReady   whether the rift has stood its minions up yet
     * @param minionsSpawned how many minions it stood up
     * @param minionsLeft    how many of those are still alive
     * @param crystalsLeft   how many crystals are still to be gathered
     */
    public static Result judge(int exitMode, long gameTime, long endsAt, int taken, int inside, int deaths,
                               boolean failOnDeath, boolean minionsReady, int minionsSpawned, int minionsLeft,
                               int crystalsLeft) {
        if (inside <= 0) {
            // Nobody left to come back: a failure only if every one of them died there and the
            // phase counts that as failing; left some other way, the rift just closes.
            return failOnDeath && taken > 0 && deaths >= taken ? Result.FAILURE : Result.EMPTY;
        }
        boolean timeUp = gameTime >= endsAt;
        // A minion rift whose minions never stood up - no clone to spawn them from - is a survival
        // rift in all but name: its players could not have done anything else.
        boolean minionsDone = minionsReady && minionsLeft <= 0;
        boolean minionsMissing = minionsReady && minionsSpawned <= 0;
        boolean crystalsDone = crystalsLeft <= 0;
        return switch (exitMode) {
            case BossRiftSettings.EXIT_MINIONS -> minionsMissing
                    ? (timeUp ? Result.SUCCESS : Result.RUNNING)
                    : minionsDone ? Result.SUCCESS : timeUp ? Result.FAILURE : Result.RUNNING;
            case BossRiftSettings.EXIT_CRYSTALS -> crystalsDone ? Result.SUCCESS
                    : timeUp ? Result.FAILURE : Result.RUNNING;
            case BossRiftSettings.EXIT_BOTH -> (minionsDone || minionsMissing) && crystalsDone ? Result.SUCCESS
                    : timeUp ? Result.FAILURE : Result.RUNNING;
            default -> timeUp ? Result.SUCCESS : Result.RUNNING;
        };
    }

    /** What the boss takes of every hit while a rift runs: the group's share, or all of it for a solo one. */
    public static int damagePercent(boolean solo, int groupDamagePercent) {
        return solo ? 100 : Math.max(0, Math.min(100, groupDamagePercent));
    }

    /** Whether a finished rift leaves the boss exposed: a solo one got through, and the window is not nought. */
    public static boolean exposes(Result result, boolean solo, int vulnerableTicks) {
        return result == Result.SUCCESS && solo && vulnerableTicks > 0;
    }

    /** What a failure costs, by the phase's switches: a zero or an empty set is no penalty. */
    public static Set<Penalty> penalties(boolean rage, int arenaDamage, boolean effects, int healPercent) {
        Set<Penalty> penalties = EnumSet.noneOf(Penalty.class);
        if (rage) {
            penalties.add(Penalty.RAGE);
        }
        if (arenaDamage > 0) {
            penalties.add(Penalty.ARENA_DAMAGE);
        }
        if (effects) {
            penalties.add(Penalty.EFFECTS);
        }
        if (healPercent > 0) {
            penalties.add(Penalty.HEAL);
        }
        return penalties;
    }

    /** The penalties a set of settings adds up to. */
    public static Set<Penalty> penalties(BossRiftSettings settings) {
        return penalties(settings.isFailRage(), settings.getFailArenaDamage(),
                settings.getFailEffects().isAnyEnabled(), settings.getFailHealPercent());
    }

    /** How much a failure heals the boss: its share of the maximum. */
    public static float healAmount(float maxHealth, int percent) {
        return maxHealth <= 0.0F || percent <= 0 ? 0.0F : maxHealth * Math.min(percent, 100) / 100.0F;
    }

    /** Whole seconds left, rounded up, for a countdown that must not read nought while time remains. */
    public static long secondsLeft(long gameTime, long endsAt) {
        long ticks = Math.max(0L, endsAt - gameTime);
        return (ticks + 19L) / 20L;
    }
}
