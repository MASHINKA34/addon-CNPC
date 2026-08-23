package com.goodbird.cnpcgeckoaddon.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class BossBarStyleClientBridge {
    private static final Map<UUID, String> PENDING = new HashMap<>();
    private static BiConsumer<UUID, String> handler;

    private BossBarStyleClientBridge() {
    }

    public static void setHandler(BiConsumer<UUID, String> value) {
        handler = value;
        PENDING.forEach(handler);
        PENDING.clear();
    }

    public static void accept(UUID eventId, String styleId) {
        if (handler == null) {
            PENDING.put(eventId, styleId);
        } else {
            handler.accept(eventId, styleId);
        }
    }
}
