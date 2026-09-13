package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.utils.TelegraphShape;

import java.util.List;

/** Common-code handoff to the client-only renderer that draws boss warning frames. */
public final class BossTelegraphClientBridge {
    private static Handler handler;

    private BossTelegraphClientBridge() {
    }

    @FunctionalInterface
    public interface Handler {
        void accept(int ownerId, byte channel, String styleId, int widthTenths, int motion,
                    int fillPercent, float progress, int ttlTicks, List<TelegraphShape> shapes);
    }

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void accept(int ownerId, byte channel, String styleId, int widthTenths,
                              int motion, int fillPercent, float progress, int ttlTicks,
                              List<TelegraphShape> shapes) {
        if (handler != null) {
            handler.accept(ownerId, channel, styleId, widthTenths, motion, fillPercent,
                    progress, ttlTicks, shapes);
        }
    }
}
