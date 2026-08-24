package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Drives {@link BossChestStore} expiry and picks its entries back up after a restart. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class BossChestEvents {

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (BossChestStore.hasAnyPending() && event.getLevel() instanceof ServerLevel level) {
            BossChestStore.get(level).tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(final LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Level data is only read on the first request for it, and a chest left over
            // from the last session has to start being counted down again right away.
            BossChestStore.get(level).onLevelLoaded(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // The chests themselves stay: their lifetime is measured in game ticks, which
            // stop along with the server and pick up again where they left off.
            BossChestStore.get(level).onLevelUnloaded();
        }
    }
}
