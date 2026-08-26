package com.goodbird.cnpcgeckoaddon.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class BossBarStyleClientBridge {

    /** One bar's skin, as the server last described it. */
    public record Bar(String styleId, int scalePercent) {
    }

    private static final Map<UUID, Bar> PENDING = new HashMap<>();
    private static BiConsumer<UUID, Bar> handler;

    private BossBarStyleClientBridge() {
    }

    public static void setHandler(BiConsumer<UUID, Bar> value) {
        handler = value;
        PENDING.forEach(handler);
        PENDING.clear();
    }

    public static void accept(UUID eventId, String styleId, int scalePercent) {
        Bar bar = new Bar(styleId, scalePercent);
        if (handler == null) {
            PENDING.put(eventId, bar);
        } else {
            handler.accept(eventId, bar);
        }
    }
}
