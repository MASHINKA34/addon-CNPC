package com.goodbird.cnpcgeckoaddon.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Holds the last countdown the server sent for each boss bar.
 *
 * <p>The same trick as {@link BossBarStyleClientBridge}: the packet class is loaded on both
 * sides, so it cannot mention the client-only HUD directly. The renderer registers itself
 * here instead, and until it does the values simply sit in the map.</p>
 */
public final class BossTimerClientBridge {

    /** One bar's countdown, as the server last described it. */
    public record Timer(int remainingTicks, int totalTicks, byte state) {
    }

    private static final Map<UUID, Timer> TIMERS = new HashMap<>();
    private static BiConsumer<UUID, Timer> handler;

    private BossTimerClientBridge() {
    }

    public static void setHandler(BiConsumer<UUID, Timer> value) {
        handler = value;
        TIMERS.forEach(handler);
    }

    public static void accept(UUID eventId, int remainingTicks, int totalTicks, byte state) {
        Timer timer = new Timer(remainingTicks, totalTicks, state);
        if (state == PacketSyncBossTimer.STATE_NONE) {
            // A boss with nothing left to count is dropped rather than remembered: the map
            // would otherwise keep an entry for every bar seen since the game started.
            TIMERS.remove(eventId);
        } else {
            TIMERS.put(eventId, timer);
        }
        if (handler != null) {
            handler.accept(eventId, timer);
        }
    }

    /** @return the countdown for this bar, or null when it has none */
    public static Timer get(UUID eventId) {
        return TIMERS.get(eventId);
    }
}
