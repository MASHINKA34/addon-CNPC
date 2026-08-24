package com.goodbird.cnpcgeckoaddon.network;

/** Common-code handoff to the client-only persistent link renderer. */
public final class BossLinkClientBridge {
    private static Handler handler;

    private BossLinkClientBridge() {
    }

    @FunctionalInterface
    public interface Handler {
        void accept(byte linkKind, int sourceEntityId, int targetEntityId, int slotOrChannel,
                    String styleId, int durationTicks, int widthPercent, int sagPercent,
                    boolean drawHead);
    }

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void accept(byte linkKind, int sourceEntityId, int targetEntityId,
                              int slotOrChannel, String styleId, int durationTicks,
                              int widthPercent, int sagPercent, boolean drawHead) {
        if (handler != null) {
            handler.accept(linkKind, sourceEntityId, targetEntityId, slotOrChannel, styleId,
                    durationTicks, widthPercent, sagPercent, drawHead);
        }
    }
}
