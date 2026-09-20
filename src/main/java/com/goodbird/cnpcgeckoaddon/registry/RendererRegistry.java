package com.goodbird.cnpcgeckoaddon.registry;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.goodbird.cnpcgeckoaddon.client.BossRiftSkyEffects;
import com.goodbird.cnpcgeckoaddon.client.ManualAnimationClient;
import com.goodbird.cnpcgeckoaddon.client.model.ModelRiftCrystal;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossChestRenderer;
import com.goodbird.cnpcgeckoaddon.client.renderer.RenderBossBoulder;
import com.goodbird.cnpcgeckoaddon.client.renderer.RenderBossRiftCrystal;
import com.goodbird.cnpcgeckoaddon.client.renderer.RenderCustomModel;
import com.goodbird.cnpcgeckoaddon.client.renderer.RenderTileCustomModel;
import com.goodbird.cnpcgeckoaddon.client.MobModelTextureResolver;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public class RendererRegistry {

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        EventGuard.handle("registry.renderer.register_renderers", event, RendererRegistry::handleRegisterRenderers);
    }

    private static void handleRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Piggybacks on renderer registration: a client-only moment that always runs before
        // any server can send a manual animation.
        ManualAnimationClient.register();
        event.registerEntityRenderer(EntityRegistry.entityCustomModel, RenderCustomModel::new);
        event.registerBlockEntityRenderer(TileEntityRegistry.tileEntityCustomModel, context -> new RenderTileCustomModel());
        // The fluid spit draws itself entirely out of block particles, so it needs no model.
        event.registerEntityRenderer(EntityRegistry.entityFluidSpit, NoopRenderer::new);
        event.registerEntityRenderer(EntityRegistry.entityBossBoulder, RenderBossBoulder::new);
        // The tether stake is only ever the far end of a beam; the beam is what gets drawn.
        event.registerEntityRenderer(EntityRegistry.entityBossTetherAnchor, NoopRenderer::new);
        event.registerEntityRenderer(EntityRegistry.entityBossRiftCrystal, RenderBossRiftCrystal::new);
        event.registerBlockEntityRenderer(TileEntityRegistry.bossChest, BossChestRenderer::new);
    }

    /**
     * The reality rift dimension's sky, under the effects id its dimension type names. Here with
     * the renderers rather than with the rift's client, which listens on the game bus.
     */
    @SubscribeEvent
    public static void registerDimensionEffects(final RegisterDimensionSpecialEffectsEvent event) {
        EventGuard.handle("registry.renderer.register_dimension_effects", event,
                RendererRegistry::handleRegisterDimensionEffects);
    }

    private static void handleRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BossRiftDimension.ID, new BossRiftSkyEffects());
    }

    /**
     * The resolved model textures are memoized against the resource manager contents,
     * so they have to be dropped whenever resource packs are reloaded.
     */
    @SubscribeEvent
    public static void registerReloadListeners(final RegisterClientReloadListenersEvent event) {
        EventGuard.handle("registry.renderer.register_reload_listeners", event, RendererRegistry::handleRegisterReloadListeners);
    }

    private static void handleRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((preparationBarrier, resourceManager, preparationsProfiler,
                                      reloadProfiler, backgroundExecutor, gameExecutor) ->
                // Clear after the barrier: only then are the new resources actually live,
                // so a lookup racing the reload cannot re-cache the old contents.
                preparationBarrier.<Void>wait(null).thenRun(() -> {
                    MobModelTextureResolver.invalidate();
                    // Which crystal skins have a drawing is memoized the same way, and a pack
                    // that has just added one is only found once the old answer is dropped.
                    ModelRiftCrystal.invalidate();
                }));
    }
}
