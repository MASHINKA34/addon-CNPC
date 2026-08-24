package com.goodbird.cnpcgeckoaddon.registry;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossChestRenderer;
import com.goodbird.cnpcgeckoaddon.client.renderer.RenderCustomModel;
import com.goodbird.cnpcgeckoaddon.client.renderer.RenderTileCustomModel;
import com.goodbird.cnpcgeckoaddon.utils.MobModelTextureResolver;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RendererRegistry {

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegistry.entityCustomModel, RenderCustomModel::new);
        event.registerBlockEntityRenderer(TileEntityRegistry.tileEntityCustomModel, context -> new RenderTileCustomModel());
        // The fluid spit draws itself entirely out of block particles, so it needs no model.
        event.registerEntityRenderer(EntityRegistry.entityFluidSpit, NoopRenderer::new);
        event.registerBlockEntityRenderer(TileEntityRegistry.bossChest, BossChestRenderer::new);
    }

    /**
     * The resolved model textures are memoized against the resource manager contents,
     * so they have to be dropped whenever resource packs are reloaded.
     */
    @SubscribeEvent
    public static void registerReloadListeners(final RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((preparationBarrier, resourceManager, preparationsProfiler,
                                      reloadProfiler, backgroundExecutor, gameExecutor) ->
                // Clear after the barrier: only then are the new resources actually live,
                // so a lookup racing the reload cannot re-cache the old contents.
                preparationBarrier.<Void>wait(null).thenRun(MobModelTextureResolver::invalidate));
    }
}
