package com.goodbird.cnpcgeckoaddon.network;

/** Common-code handoff to the client-only rift look: the tint on the screen and the fog. */
public final class BossRiftClientBridge {
    private static Handler handler;

    private BossRiftClientBridge() {
    }

    @FunctionalInterface
    public interface Handler {
        void accept(boolean active, int tintColor, int tintAlpha, int pulseTicks, int fogColor,
                    int fogDistance, long endsAt);
    }

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void accept(boolean active, int tintColor, int tintAlpha, int pulseTicks, int fogColor,
                              int fogDistance, long endsAt) {
        if (handler != null) {
            handler.accept(active, tintColor, tintAlpha, pulseTicks, fogColor, fogDistance, endsAt);
        }
    }
}
