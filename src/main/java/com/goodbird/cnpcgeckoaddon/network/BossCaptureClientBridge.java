package com.goodbird.cnpcgeckoaddon.network;

/** Common-code handoff to the client-only capture movement lock. */
public final class BossCaptureClientBridge {
    private static Handler handler;

    private BossCaptureClientBridge() {
    }

    @FunctionalInterface
    public interface Handler {
        void accept(boolean active, double x, double y, double z, long startedAt, long endsAt,
                    long liftEndsAt, double targetY, float yaw, float pitch, boolean allowLook);
    }

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void accept(boolean active, double x, double y, double z, long startedAt,
                              long endsAt, long liftEndsAt, double targetY, float yaw,
                              float pitch, boolean allowLook) {
        if (handler != null) {
            handler.accept(active, x, y, z, startedAt, endsAt, liftEndsAt, targetY,
                    yaw, pitch, allowLook);
        }
    }
}
