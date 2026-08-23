package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.network.BossTimerClientBridge;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossTimer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The countdown strip drawn under a boss health bar.
 *
 * <p>Holds what the server last said about each bar and runs the clock forward itself
 * between packets, so the digits tick every second instead of jumping in five-tick steps.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossTimerOverlay {

    /** One bar's countdown as the client currently believes it. */
    public static final class TimerState {
        public int remainingTicks;
        public int totalTicks;
        public byte state;
        /** Client tick the last packet landed on, so the same tick is never counted twice. */
        public long lastPacketClientTick;
    }

    private static final Map<UUID, TimerState> TIMERS = new HashMap<>();
    private static long clientTick;

    static {
        BossTimerClientBridge.setHandler(BossTimerOverlay::accept);
    }

    private BossTimerOverlay() {
    }

    /** @return the countdown to draw under this bar, or null when it has none */
    public static TimerState get(UUID eventId) {
        TimerState timer = TIMERS.get(eventId);
        return timer == null || timer.state == PacketSyncBossTimer.STATE_NONE ? null : timer;
    }

    private static void accept(UUID eventId, BossTimerClientBridge.Timer packet) {
        if (packet.state() == PacketSyncBossTimer.STATE_NONE) {
            TIMERS.remove(eventId);
            return;
        }
        TimerState timer = TIMERS.computeIfAbsent(eventId, id -> new TimerState());
        // Hard reset rather than a nudge: the server is the only authority on the clock, and
        // a client that drifted ahead has to snap back even if that means a visible jump.
        timer.remainingTicks = packet.remainingTicks();
        timer.totalTicks = packet.totalTicks();
        timer.state = packet.state();
        timer.lastPacketClientTick = clientTick;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        clientTick++;
        for (Iterator<TimerState> timers = TIMERS.values().iterator(); timers.hasNext(); ) {
            TimerState timer = timers.next();
            if (timer.state == PacketSyncBossTimer.STATE_NONE) {
                timers.remove();
            } else if (timer.lastPacketClientTick != clientTick && timer.remainingTicks > 0) {
                timer.remainingTicks--;
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TIMERS.clear();
        BossTimerClientBridge.clear();
    }
}
