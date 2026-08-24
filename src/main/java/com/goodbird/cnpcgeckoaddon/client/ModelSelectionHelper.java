package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.utils.MobModelTextureResolver;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityCustomNpc;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;

/** Keeps preview and final model selection resource rules identical. */
public final class ModelSelectionHelper {
    private static final String GEO_PREFIX = "geo/";
    private static final String GEO_SUFFIX = ".geo.json";

    private ModelSelectionHelper() {
    }

    public static ModelResources resolve(ResourceLocation model) {
        return new ModelResources(
                model,
                MobModelTextureResolver.getDefaultTexture(model),
                findPairedAnimation(model));
    }

    public static ModelResources applyToNpc(EntityCustomNpc npc, ResourceLocation model) {
        ModelResources resources = resolve(model);
        CustomModelData modelData = ((IDataDisplay) npc.display).getCustomModelData();
        modelData.setModel(model.toString());
        if (resources.defaultTexture() != null) {
            npc.display.setSkinTexture(resources.defaultTexture().toString());
        }
        if (resources.animation() != null) {
            modelData.setAnimFile(resources.animation().toString());
        }
        return resources;
    }

    @Nullable
    public static ResourceLocation findPairedAnimation(ResourceLocation model) {
        String path = model.getPath();
        if (!path.startsWith(GEO_PREFIX) || !path.endsWith(GEO_SUFFIX)) {
            return null;
        }

        String name = path.substring(GEO_PREFIX.length(), path.length() - GEO_SUFFIX.length());
        ResourceLocation animation = ResourceLocation.fromNamespaceAndPath(
                model.getNamespace(), "animations/" + name + ".animation.json");
        return GeckoLibCache.getBakedAnimations().containsKey(animation) ? animation : null;
    }

    public record ModelResources(
            ResourceLocation model,
            @Nullable ResourceLocation defaultTexture,
            @Nullable ResourceLocation animation) {
    }
}
