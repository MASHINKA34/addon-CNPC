package com.goodbird.cnpcgeckoaddon.network;

import java.util.function.Consumer;

/** Common-code handoff to the client-only ride a storm's victim runs. */
public final class BossSpinClientBridge {
    private static Consumer<PacketSyncBossSpinState> handler;

    private BossSpinClientBridge() {
    }

    public static void setHandler(Consumer<PacketSyncBossSpinState> value) {
        handler = value;
    }

    public static void accept(PacketSyncBossSpinState packet) {
        if (handler != null) {
            handler.accept(packet);
        }
    }
}
