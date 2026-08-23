package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Drives {@link TemporaryFluidStore} expiry and makes sure nothing survives a shutdown. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class TemporaryFluidEvents {

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (TemporaryFluidStore.hasAnyPending() && event.getLevel() instanceof ServerLevel level) {
            TemporaryFluidStore.get(level).tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(final LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Entries read back from disk belong to a session that ended while fluid was
            // still placed; expire them right away so the terrain is repaired on load.
            TemporaryFluidStore.get(level).onLevelLoaded(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TemporaryFluidStore store = TemporaryFluidStore.get(level);
            store.restoreAll(level);
            store.onLevelUnloaded();
        }
    }
}
