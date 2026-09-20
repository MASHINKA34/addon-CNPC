package com.goodbird.cnpcgeckoaddon.client.model;

import com.goodbird.cnpcgeckoaddon.data.RiftCrystalContract;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossRiftCrystal;
import com.goodbird.cnpcgeckoaddon.utils.ResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which files the rift crystal's model is drawn from.
 *
 * <p>The geometry and the animation file are one pair for every crystal; only the drawing
 * changes, by the skin its rift was set to. A skin whose drawing has not shipped falls back to
 * the default one rather than to the black and magenta checkerboard, which reads as a broken
 * install rather than as a name nobody drew.</p>
 *
 * <p>Whether a drawing exists is asked of the resource manager, which walks every pack, so the
 * answer is kept per skin. The renderer is rebuilt on a resource reload but this map is not -
 * it is static, for the four answers to be shared - so {@link #invalidate()} drops it when the
 * packs change, and a skin added by a pack halfway through a session is found.</p>
 */
public class ModelRiftCrystal extends GeoModel<EntityBossRiftCrystal> {

    /** The one geometry and the one animation file, shared with whoever asks whether they loaded. */
    public static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            RiftCrystalContract.NAMESPACE, RiftCrystalContract.GEO_PATH);
    public static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            RiftCrystalContract.NAMESPACE, RiftCrystalContract.ANIMATION_PATH);
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            RiftCrystalContract.NAMESPACE, RiftCrystalContract.DEFAULT_TEXTURE_PATH);

    /** The drawing each skin id resolves to, its own or the default one. */
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    @Override
    public ResourceLocation getModelResource(EntityBossRiftCrystal crystal) {
        return GEO;
    }

    @Override
    public ResourceLocation getAnimationResource(EntityBossRiftCrystal crystal) {
        return ANIMATIONS;
    }

    @Override
    public ResourceLocation getTextureResource(EntityBossRiftCrystal crystal) {
        return texture(crystal.skin());
    }

    /** The drawing this skin is dressed in: its own if there is one, otherwise the default. */
    public static ResourceLocation texture(String skin) {
        return TEXTURES.computeIfAbsent(RiftCrystalContract.cleanSkin(skin), id -> {
            ResourceLocation own = texturePath(id);
            return Minecraft.getInstance().getResourceManager().getResource(own).isEmpty()
                    ? DEFAULT_TEXTURE : own;
        });
    }

    /** Drops the answers so a resource reload can find a drawing a pack has just added. */
    public static void invalidate() {
        TEXTURES.clear();
    }

    /**
     * The id one skin's drawing would sit at.
     *
     * <p>Through {@link ResourceIds} rather than built outright: the skin comes out of a text
     * field on a screen, and although it has been cleaned down to path characters on the way
     * in, a path built on the render thread is not the place to find out that it was not.</p>
     */
    private static ResourceLocation texturePath(String skin) {
        return ResourceIds.pathOrDefault(RiftCrystalContract.NAMESPACE,
                RiftCrystalContract.texturePath(skin), DEFAULT_TEXTURE);
    }
}
