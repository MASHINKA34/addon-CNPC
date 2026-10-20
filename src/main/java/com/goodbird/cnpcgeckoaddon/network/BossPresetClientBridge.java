package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common-code handoff of the server's answers about boss presets to the client's presets screen,
 * which sets the handler when it is first opened: no answer comes before a request from it.
 */
public final class BossPresetClientBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private static Handler handler;

    private BossPresetClientBridge() {
    }

    @FunctionalInterface
    public interface Handler {
        void accept(PacketBossPresetReply reply);
    }

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void accept(PacketBossPresetReply reply) {
        if (handler == null) {
            LOGGER.debug("Boss preset answer to {} dropped: no presets screen was ever opened on this client",
                    PacketBossPresetRequest.actionName(reply.action()));
            return;
        }
        handler.accept(reply);
    }
}
