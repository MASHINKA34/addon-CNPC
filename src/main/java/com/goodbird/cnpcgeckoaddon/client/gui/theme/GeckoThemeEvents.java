package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Hooks the theme into resource reloading, so it notices a pack that adds or drops its files. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class GeckoThemeEvents {

    private GeckoThemeEvents() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(final RegisterClientReloadListenersEvent event) {
        EventGuard.handle("client.gui.theme.register_reload_listener", event,
                GeckoThemeEvents::handleRegisterReloadListeners);
    }

    private static void handleRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(GeckoTheme.RELOAD_LISTENER);
    }
}
