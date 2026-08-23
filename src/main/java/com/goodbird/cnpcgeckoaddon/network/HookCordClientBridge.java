package com.goodbird.cnpcgeckoaddon.network;

/**
 * Keeps the packet from naming the client-only renderer directly.
 *
 * <p>Unlike the boss bar bridges nothing is buffered here: the renderer registers itself
 * while the client mod loads, long before a boss can swing a hook, and a cord that arrived
 * before that would have expired by now anyway.</p>
 */
public final class HookCordClientBridge {
    private static Handler handler;

    private HookCordClientBridge() {
    }

    @FunctionalInterface
    public interface Handler {
        void accept(int bossEntityId, int victimEntityId, String styleId, int durationTicks);
    }

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void accept(int bossEntityId, int victimEntityId, String styleId, int durationTicks) {
        if (handler != null) {
            handler.accept(bossEntityId, victimEntityId, styleId, durationTicks);
        }
    }
}
