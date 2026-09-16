package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The clock of the geyser's second strike, the column that falls back onto the circle from
 * above, kept apart from the world so its timing can be checked without one.
 *
 * <p>Three moments, counted from the tick the ground opened: the fall begins after the
 * phase's delay, the front of the column comes down over the fall's ticks, and the strike
 * lands the tick after the last of them. The circle is warned for the whole of that, from
 * the eruption to the landing, so a player thrown up by the eruption reads what is about to
 * come down on the spot they will land on.</p>
 *
 * <p>The start and the strike are taken off the clock once each, on the first tick at or
 * past their moment, so a tick that held the strike up lands it late rather than twice or
 * never.</p>
 */
final class BossGeyserSky {

    /** What the server's own pass takes off a player's vertical speed before the client hears of it. */
    private static final double VERTICAL_DRAG = 0.98D;
    private static final double GRAVITY = 0.08D;

    private final long eruptedAt;
    private final long fallsAt;
    private final long hitsAt;
    private boolean started;
    private boolean struck;

    BossGeyserSky(long eruptedAt, int delayTicks, int fallTicks) {
        this.eruptedAt = eruptedAt;
        this.fallsAt = eruptedAt + Math.max(1, delayTicks);
        this.hitsAt = fallsAt + Math.max(1, fallTicks);
    }

    /** The tick the column starts down. */
    long fallsAt() {
        return fallsAt;
    }

    /** The tick it lands. */
    long hitsAt() {
        return hitsAt;
    }

    /** Whether the fall has not started yet: the circle is warned, and nothing is drawn above it. */
    boolean isWaiting(long gameTime) {
        return gameTime < fallsAt;
    }

    /** Takes the start of the fall off the clock: true exactly once, on the first tick at or past it. */
    boolean takeStart(long gameTime) {
        if (started || gameTime < fallsAt) {
            return false;
        }
        started = true;
        return true;
    }

    /** Whether the column is on its way down on this tick. */
    boolean isFalling(long gameTime) {
        return gameTime >= fallsAt && gameTime < hitsAt;
    }

    /** Takes the landing off the clock: true exactly once, on the first tick at or past it. */
    boolean takeStrike(long gameTime) {
        if (struck || gameTime < hitsAt) {
            return false;
        }
        struck = true;
        return true;
    }

    /** Whether the strike has landed; from here there is nothing left of it. */
    boolean isOver() {
        return struck;
    }

    /** How far the warning has got: 0 the tick the ground opened, 1 the tick the strike lands. */
    float warnProgress(long gameTime) {
        long whole = hitsAt - eruptedAt;
        return whole <= 0L ? 1.0F : Mth.clamp((float) (gameTime - eruptedAt) / whole, 0.0F, 1.0F);
    }

    /**
     * The press as it has to be set so that it arrives as {@code down} straight down, on top
     * of whatever run the victim had.
     *
     * <p>The gravity throw's sum turned downward: for a player the server's own pass runs
     * before the send and takes one tick of drag and gravity off the speed, so that tick is
     * put back in advance. A mob moves by exactly what it is handed.</p>
     */
    static Vec3 pressVelocity(Vec3 movement, double down, boolean player) {
        double y = player ? -down / VERTICAL_DRAG + GRAVITY : -down;
        return new Vec3(movement.x, y, movement.z);
    }
}
