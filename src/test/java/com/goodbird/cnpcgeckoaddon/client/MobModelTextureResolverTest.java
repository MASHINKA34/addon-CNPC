package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.util.TsvResource;
import com.goodbird.cnpcgeckoaddon.utils.MobModelNameMatcher;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobModelTextureResolverTest {

    private static final String TEXTURES = "/META-INF/MOBMODEL_TEXTURES.tsv";
    private static final String OVERRIDES = "/META-INF/MOBMODEL_TEXTURE_OVERRIDES.tsv";
    private static final String HITBOXES = "/META-INF/MOBMODEL_HITBOXES.tsv";

    @Test
    @DisplayName("every bundle listed in the texture table passes the namespace gate")
    void everyRecordedModelIsRecognisedAsBundled() {
        Set<String> uncovered = new TreeSet<>();
        for (TsvResource.Row row : TsvResource.read(TEXTURES)) {
            ResourceLocation model = ResourceLocation.parse(row.key());
            if (!MobModelTextureResolver.isBundledModel(model)) {
                uncovered.add(model.getNamespace());
            }
        }
        assertTrue(uncovered.isEmpty(),
                "these namespaces ship a texture table but are missing from BUNDLED_NAMESPACES, "
                        + "so their models render with a stretched npc skin: " + uncovered);
    }

    @Test
    @DisplayName("every bundle listed in the hitbox table passes the namespace gate")
    void everyHitboxNamespaceIsRecognisedAsBundled() {
        Set<String> uncovered = new TreeSet<>();
        for (TsvResource.Row row : TsvResource.read(HITBOXES)) {
            ResourceLocation model = ResourceLocation.parse(row.key());
            // The addon's own placeholders are sized here but never textured from a bundle:
            // ModelCustom hands them the alphabet sheet by hand.
            if (model.getNamespace().equals("cnpcgeckoaddon")
                    && !model.getPath().startsWith("geo/arphex/")) {
                continue;
            }
            if (!MobModelTextureResolver.isBundledModel(model)) {
                uncovered.add(model.getNamespace());
            }
        }
        assertTrue(uncovered.isEmpty(),
                "these namespaces have recorded hitboxes but no recorded textures: " + uncovered);
    }

    @Test
    @DisplayName("the override table parses into unique model keys")
    void overridesAreWellFormedAndUnique() {
        List<TsvResource.Row> rows = TsvResource.read(OVERRIDES);
        assertFalse(rows.isEmpty(), OVERRIDES + " parsed to nothing");
        Set<String> keys = new LinkedHashSet<>();
        for (TsvResource.Row row : rows) {
            ResourceLocation model = ResourceLocation.parse(row.key());
            ResourceLocation texture = ResourceLocation.parse(row.value());
            assertTrue(model.getPath().endsWith(".geo.json"),
                    OVERRIDES + ":" + row.line() + " does not point at a model");
            assertTrue(texture.getPath().endsWith(".png"),
                    OVERRIDES + ":" + row.line() + " does not point at a texture");
            assertTrue(MobModelTextureResolver.isBundledModel(model),
                    OVERRIDES + ":" + row.line() + " overrides a model outside every bundle");
            assertTrue(keys.add(row.key()),
                    OVERRIDES + ":" + row.line() + " overrides " + row.key() + " twice");
        }
    }

    @Test
    @DisplayName("a model outside the bundles keeps the npc skin")
    void unbundledModelKeepsTheNpcSkin() {
        ResourceLocation own = ResourceLocation.fromNamespaceAndPath("somemod", "geo/thing.geo.json");
        ResourceLocation skin = ResourceLocation.fromNamespaceAndPath("customnpcs", "textures/skin.png");
        assertFalse(MobModelTextureResolver.isBundledModel(own));
        assertEquals(skin, MobModelTextureResolver.resolve(own, skin));
        assertEquals(skin, MobModelTextureResolver.resolve(null, skin));
    }
}
