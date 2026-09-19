package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;

import java.util.function.IntSupplier;

/**
 * What an npc whose ranged fight the addon runs does on one tick: where it goes, whether it
 * shoots, and whether the shot arcs over what is in the way.
 *
 * <p>Kept clear of the world on purpose. Every rule the customer asked for - the window it
 * fires from, what it does about a target too far off or too close, waiting for a clear shot,
 * lobbing over cover behind a warning, the burst and the pause after it - is a decision about
 * one distance and one yes-or-no about sight, and all of it is testable by ticking this with
 * numbers. {@link NpcRangedAttackGoal} is then only the part that needs an npc: reading the
 * distance, moving, firing, drawing the ring.</p>
 *
 * <p>One of these belongs to one npc and holds the clocks between ticks: the delay until the
 * next shot, what is left of a burst, the pause after one, and how long the warning for a
 * lobbed shot has been up.</p>
 */
public final class NpcRangedPlan {

    /** CustomNPCs' own "shoot indirect": 0 never, 1 past half the range, 2 while hidden. */
    public static final int FIRE_TYPE_FLAT = 0;
    public static final int FIRE_TYPE_WHEN_DISTANT = 1;
    public static final int FIRE_TYPE_WHEN_HIDDEN = 2;

    /** What the npc does with its legs this tick. */
    public enum Move {
        /** Stand where it is: in the window, or told to hold its position. */
        HOLD,
        /** Walk toward the target: too far off, or nothing to shoot through. */
        APPROACH,
        /** Walk away from it: closer than the window and told to back off. */
        RETREAT,
        /** Nothing of ours: the target is inside the window and melee has the npc. */
        MELEE
    }

    /**
     * One tick's worth of orders.
     *
     * @param move          where the npc goes
     * @param fire          whether a shot leaves this tick
     * @param indirect      whether that shot is lobbed rather than sent flat
     * @param warnLob       whether the ring for a coming lobbed shot is up this tick
     * @param reloadStarted whether the pause after a burst begins on this tick, which is
     *                      where the reload animation and its noise go
     */
    public record Step(Move move, boolean fire, boolean indirect, boolean warnLob, boolean reloadStarted) {
    }

    /**
     * Everything the plan reads off the npc's settings, taken once a tick.
     *
     * @param minRange           near edge of the window, in blocks
     * @param maxRange           far edge, in blocks; already resolved from the CustomNPCs range
     * @param tooFarMode         {@link RangedExtraData#TOO_FAR_APPROACH}
     *                           or {@code TOO_FAR_WAIT}
     * @param tooCloseMode       {@code TOO_CLOSE_RETREAT}, {@code TOO_CLOSE_MELEE} or {@code TOO_CLOSE_FIRE}
     * @param burstShots         shots in the addon's own burst, or 0 to leave the volley to CustomNPCs
     * @param burstDelayTicks    ticks between two shots of the addon's burst
     * @param reloadTicks        the pause after a burst
     * @param losMode            {@code LOS_CUSTOMNPCS}, {@code LOS_WAIT} or {@code LOS_LOB}
     * @param lobWarnTicks       how long the ring stands before a lobbed shot
     * @param cnpcBurst          {@code DataRanged.getBurst()}
     * @param cnpcBurstDelayTicks {@code DataRanged.getBurstDelay()}
     * @param fireType           {@code DataRanged.getFireType()}
     */
    public record Settings(double minRange, double maxRange, int tooFarMode, int tooCloseMode,
                           int burstShots, int burstDelayTicks, int reloadTicks,
                           int losMode, int lobWarnTicks,
                           int cnpcBurst, int cnpcBurstDelayTicks, int fireType) {
    }

    private int shotTimer;
    private int reloadTimer;
    private int burstLeft;
    private int warnTimer;
    private int cnpcBurstCount;

    /**
     * Starts the clock the way CustomNPCs' own goal does, at half the shortest delay, so an
     * npc that has just seen somebody does not shoot on the same tick.
     */
    public void start(int delayMinTicks) {
        shotTimer = Math.max(0, delayMinTicks / 2);
        reloadTimer = 0;
        burstLeft = 0;
        warnTimer = 0;
        cnpcBurstCount = 0;
    }

    /** What the npc should do now.
     *
     * @param distance    how far the target is, in blocks
     * @param lineOfSight whether the npc can see it
     * @param volleyDelay the delay before the next volley, asked for only when one ends,
     *                    because CustomNPCs rolls it at random between its two delays
     */
    public Step tick(Settings settings, double distance, boolean lineOfSight, IntSupplier volleyDelay) {
        Move move = move(settings, distance);
        // The delay runs down whatever the distance is, CustomNPCs' way: a shot that came due
        // while the target was out of reach goes off as soon as it is back in it.
        if (shotTimer > 0) {
            shotTimer--;
        }
        if (reloadTimer > 0) {
            // Reloading is the one state that outranks the window: the npc keeps walking to
            // where it wants to be and simply has nothing to fire yet. The delay above keeps
            // running underneath it, so the next volley leaves when the longer of the two is
            // over rather than after both in turn.
            reloadTimer--;
            return new Step(move, false, false, false, false);
        }
        if (!firesAt(settings, distance)) {
            // Nothing is coming, so a warning already up was for a shot that is not: down it goes.
            warnTimer = 0;
            return new Step(move, false, false, false, false);
        }
        boolean blind = !lineOfSight;
        if (blind && settings.losMode() == RangedExtraData.LOS_WAIT) {
            warnTimer = 0;
            // Waiting for a clear shot means going and finding one rather than standing still.
            return new Step(move == Move.HOLD ? Move.APPROACH : move, false, false, false, false);
        }
        if (blind && settings.losMode() == RangedExtraData.LOS_CUSTOMNPCS
                && settings.fireType() != FIRE_TYPE_WHEN_HIDDEN) {
            // Exactly what CustomNPCs' goal does with a hidden target: hold fire unless the
            // npc is set to shoot indirect while hidden.
            warnTimer = 0;
            return new Step(move, false, false, false, false);
        }
        if (shotTimer > 0) {
            return new Step(move, false, false, false, false);
        }
        boolean lob = blind && settings.losMode() == RangedExtraData.LOS_LOB;
        if (lob && warnTimer < settings.lobWarnTicks()) {
            // The shot is due but the ring goes up first, and the delay stays run out so the
            // shot leaves on the tick the warning ends rather than a whole delay later.
            warnTimer++;
            return new Step(move, false, false, true, false);
        }
        warnTimer = 0;
        boolean indirect = lob || indirectForCustomNpcs(settings, distance, lineOfSight);
        return fire(settings, move, indirect, volleyDelay);
    }

    /** Whether a shot leaves this tick, and what it costs the burst and the delay. */
    private Step fire(Settings settings, Move move, boolean indirect, IntSupplier volleyDelay) {
        if (settings.burstShots() > 0) {
            if (burstLeft <= 0) {
                burstLeft = settings.burstShots();
            }
            burstLeft--;
            if (burstLeft > 0) {
                shotTimer = Math.max(1, settings.burstDelayTicks());
                return new Step(move, true, indirect, false, false);
            }
            return new Step(move, true, indirect, false, endVolley(settings, volleyDelay));
        }
        // CustomNPCs' own counter, kept as it stands rather than tidied: it judges the count
        // before this pass against the burst, and fires on the count after it, so the first
        // pass of a cycle only starts the burst clock and the pass after the last shot only
        // resets it - a burst of N is N shots with a burst delay on either side. An npc whose
        // burst is left to CustomNPCs must keep firing at exactly the rhythm it always did.
        int before = cnpcBurstCount++;
        boolean reloadStarted = false;
        if (before <= settings.cnpcBurst()) {
            shotTimer = Math.max(0, settings.cnpcBurstDelayTicks());
        } else {
            cnpcBurstCount = 0;
            reloadStarted = endVolley(settings, volleyDelay);
        }
        return new Step(move, cnpcBurstCount > 1, indirect, false, reloadStarted);
    }

    /** The pause after a volley and the delay before the next one.
     *
     * @return whether a reload starts here, which is what the animation and the noise hang on
     */
    private boolean endVolley(Settings settings, IntSupplier volleyDelay) {
        burstLeft = 0;
        reloadTimer = Math.max(0, settings.reloadTicks());
        shotTimer = Math.max(0, volleyDelay.getAsInt());
        return reloadTimer > 0;
    }

    /** Whether the target is somewhere the npc is willing to shoot at. */
    private static boolean firesAt(Settings settings, double distance) {
        if (distance > settings.maxRange()) {
            return false;
        }
        return distance >= minRange(settings)
                || settings.tooCloseMode() == RangedExtraData.TOO_CLOSE_FIRE;
    }

    private static Move move(Settings settings, double distance) {
        if (distance > settings.maxRange()) {
            return settings.tooFarMode() == RangedExtraData.TOO_FAR_WAIT
                    ? Move.HOLD : Move.APPROACH;
        }
        if (distance < minRange(settings)) {
            return switch (settings.tooCloseMode()) {
                case RangedExtraData.TOO_CLOSE_RETREAT -> Move.RETREAT;
                case RangedExtraData.TOO_CLOSE_MELEE -> Move.MELEE;
                default -> Move.HOLD;
            };
        }
        return Move.HOLD;
    }

    /**
     * A window whose near edge was typed past its far edge is read as no near edge at all,
     * rather than as a window nothing can ever be inside.
     */
    private static double minRange(Settings settings) {
        return settings.minRange() > settings.maxRange() ? 0.0D : settings.minRange();
    }

    /** What CustomNPCs itself would send indirect, for the npc that is left set to its rules. */
    private static boolean indirectForCustomNpcs(Settings settings, double distance, boolean lineOfSight) {
        if (settings.losMode() != RangedExtraData.LOS_CUSTOMNPCS) {
            return false;
        }
        return switch (settings.fireType()) {
            // Past half the squared range, which is the far seven tenths of it.
            case FIRE_TYPE_WHEN_DISTANT ->
                    distance * distance > settings.maxRange() * settings.maxRange() / 2.0D;
            case FIRE_TYPE_WHEN_HIDDEN -> !lineOfSight;
            default -> false;
        };
    }
}
