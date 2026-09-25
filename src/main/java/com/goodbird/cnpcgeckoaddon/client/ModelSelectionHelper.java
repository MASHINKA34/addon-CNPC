package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.utils.NpcTextureUtils;
import com.goodbird.cnpcgeckoaddon.utils.ResourceIds;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityCustomNpc;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;

/**
 * Keeps preview and final model selection resource rules identical.
 *
 * <p>The picker previews a model with the skin {@link #skinAfterApply} says the npc will carry
 * once it is applied, and {@link #applyToNpc} writes exactly that skin; both are then drawn
 * through {@link MobModelTextureResolver}, so the preview is the npc.</p>
 */
public final class ModelSelectionHelper {
    private static final String GEO_PREFIX = "geo/";
    private static final String GEO_SUFFIX = ".geo.json";

    /** The skin CustomNPCs gives a new npc, and the one a picked model with no sheet falls back to. */
    public static final ResourceLocation DEFAULT_NPC_SKIN = ResourceLocation.fromNamespaceAndPath(
            "customnpcs", "textures/entity/humanmale/steve.png");

    private ModelSelectionHelper() {
    }

    public static ModelResources resolve(ResourceLocation model) {
        return new ModelResources(
                model,
                MobModelTextureResolver.getDefaultTexture(model),
                findPairedAnimation(model));
    }

    public static ModelResources applyToNpc(EntityCustomNpc npc, ResourceLocation model) {
        return applyToNpc(npc, model, null);
    }

    /**
     * Gives the npc {@code model}, its paired animation file and the skin the preview showed.
     *
     * @param chosenTexture a texture picked for this model by hand, or null for the automatic one
     */
    public static ModelResources applyToNpc(EntityCustomNpc npc, ResourceLocation model,
                                            @Nullable ResourceLocation chosenTexture) {
        ModelResources resources = resolve(model);
        ResourceLocation skin = skinAfterApply(npc, model, chosenTexture);
        CustomModelData modelData = ((IDataDisplay) npc.display).getCustomModelData();
        modelData.setModel(model.toString());
        // The same path CustomNPCs' own texture picker takes, so the skin is saved with the
        // display and survives the npc being reloaded.
        if (!skin.equals(currentSkin(npc))) {
            npc.display.setSkinTexture(skin.toString());
        }
        if (resources.animation() != null) {
            modelData.setAnimFile(resources.animation().toString());
        }
        return resources;
    }

    /**
     * The skin the npc will carry once {@code model} is applied to it.
     *
     * <ul>
     *     <li>a texture picked by hand for the model;</li>
     *     <li>for the model the npc already wears, its skin as it is: applying it again changes
     *         nothing, and the picker opens on exactly the look the npc has;</li>
     *     <li>the model's own sheet, recorded or named after it;</li>
     *     <li>for a model without one, the skin as it is when somebody chose it, and the default
     *         skin when it was only the previous model's own sheet - that sheet means nothing on
     *         another model's geometry.</li>
     * </ul>
     */
    public static ResourceLocation skinAfterApply(EntityCustomNpc npc, ResourceLocation model,
                                                  @Nullable ResourceLocation chosenTexture) {
        if (chosenTexture != null) {
            return chosenTexture;
        }
        ResourceLocation skin = currentSkin(npc);
        ResourceLocation currentModel = currentModel(npc);
        if (model.equals(currentModel)) {
            return skin;
        }
        ResourceLocation own = MobModelTextureResolver.getDefaultTexture(model);
        if (own != null) {
            return own;
        }
        boolean automatic = MobModelTextureResolver.isDefaultNpcTexture(skin)
                || MobModelTextureResolver.isRecordedDefault(skin)
                || currentModel != null && skin.equals(MobModelTextureResolver.getDefaultTexture(currentModel));
        return automatic ? DEFAULT_NPC_SKIN : skin;
    }

    /**
     * The skin the npc is drawn with now, never null: the texture the world hands its model
     * each frame, so a player or url skin counts as the skin it shows rather than as whatever
     * the texture field last held.
     */
    public static ResourceLocation currentSkin(EntityCustomNpc npc) {
        ResourceLocation skin = NpcTextureUtils.getNpcTexture(npc);
        return skin == null ? DEFAULT_NPC_SKIN : skin;
    }

    /** The model the npc wears now, or null when its id is not a legal one. */
    @Nullable
    public static ResourceLocation currentModel(EntityCustomNpc npc) {
        return ResourceLocation.tryParse(((IDataDisplay) npc.display).getCustomModelData().getModel());
    }

    @Nullable
    public static ResourceLocation findPairedAnimation(ResourceLocation model) {
        String path = model.getPath();
        if (!path.startsWith(GEO_PREFIX) || !path.endsWith(GEO_SUFFIX)) {
            return null;
        }

        String name = path.substring(GEO_PREFIX.length(), path.length() - GEO_SUFFIX.length());
        ResourceLocation animation = ResourceIds.pathOrDefault(
                model.getNamespace(), "animations/" + name + ".animation.json", null);
        return animation != null && GeckoLibCache.getBakedAnimations().containsKey(animation) ? animation : null;
    }

    public record ModelResources(
            ResourceLocation model,
            @Nullable ResourceLocation defaultTexture,
            @Nullable ResourceLocation animation) {
    }
}
