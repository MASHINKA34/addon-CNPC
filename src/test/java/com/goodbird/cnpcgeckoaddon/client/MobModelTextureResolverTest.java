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
        assertEquals(MobModelTextureResolver.Source.NPC, MobModelTextureResolver.explain(own, skin).source());
    }

    private static final ResourceLocation STEVE =
            ResourceLocation.fromNamespaceAndPath("customnpcs", "textures/entity/humanmale/steve.png");
    private static final ResourceLocation BUNDLED_MODEL =
            ResourceLocation.fromNamespaceAndPath("cataclysm", "geo/ignis_model.geo.json");
    private static final ResourceLocation FOREIGN_MODEL =
            ResourceLocation.fromNamespaceAndPath("somemod", "geo/wraith.geo.json");

    private static MobModelTextureResolver.Resolution own(String namespace, String path,
                                                          MobModelTextureResolver.Source source) {
        return new MobModelTextureResolver.Resolution(ResourceLocation.fromNamespaceAndPath(namespace, path), source);
    }

    @Test
    @DisplayName("a default skin gives way to the model's own sheet, found either way")
    void aDefaultSkinGivesWayToTheOwnSheet() {
        MobModelTextureResolver.Resolution mapped = own("cataclysm", "textures/entity/ignis.png",
                MobModelTextureResolver.Source.MAP);
        assertEquals(mapped, MobModelTextureResolver.decide(BUNDLED_MODEL, STEVE, mapped, true));
        assertEquals(mapped, MobModelTextureResolver.decide(BUNDLED_MODEL, null, mapped, true));

        MobModelTextureResolver.Resolution named = own("somemod", "textures/entity/wraith.png",
                MobModelTextureResolver.Source.NAME);
        assertEquals(named, MobModelTextureResolver.decide(FOREIGN_MODEL, STEVE, named, false),
                "another mod's model is dressed in the png named after it too");
    }

    @Test
    @DisplayName("a bundled model with no sheet shows as missing rather than as a stretched default skin")
    void aBundledModelWithoutASheetShowsAsMissing() {
        MobModelTextureResolver.Resolution missing = MobModelTextureResolver.decide(BUNDLED_MODEL, STEVE, null, true);
        assertEquals(MobModelTextureResolver.MISSING_TEXTURE, missing.texture());
        assertEquals(MobModelTextureResolver.Source.NONE, missing.source());
        assertEquals(MobModelTextureResolver.Source.NONE,
                MobModelTextureResolver.decide(BUNDLED_MODEL, null, null, true).source());
    }

    @Test
    @DisplayName("another mod's model with no sheet keeps the default skin it was made for")
    void aForeignModelWithoutASheetKeepsTheDefaultSkin() {
        assertEquals(new MobModelTextureResolver.Resolution(STEVE, MobModelTextureResolver.Source.NPC),
                MobModelTextureResolver.decide(FOREIGN_MODEL, STEVE, null, false));
    }

    @Test
    @DisplayName("a skin somebody chose is kept over the model's own sheet")
    void aChosenSkinIsKept() {
        ResourceLocation chosen = ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/ignis/ignis_idle_0.png");
        MobModelTextureResolver.Resolution mapped = own("cataclysm", "textures/entity/ignis.png",
                MobModelTextureResolver.Source.MAP);
        assertEquals(new MobModelTextureResolver.Resolution(chosen, MobModelTextureResolver.Source.NPC),
                MobModelTextureResolver.decide(BUNDLED_MODEL, chosen, mapped, true));
        assertEquals(new MobModelTextureResolver.Resolution(chosen, MobModelTextureResolver.Source.NPC),
                MobModelTextureResolver.decide(BUNDLED_MODEL, chosen, null, true),
                "a model with no sheet of its own wears a chosen one");
    }

    @Test
    @DisplayName("another bundled model's recorded sheet or a render layer left in the skin gives way")
    void staleSheetsGiveWay() {
        MobModelTextureResolver.Resolution mapped = own("cataclysm", "textures/entity/ignis.png",
                MobModelTextureResolver.Source.MAP);
        ResourceLocation recorded = ResourceLocation.parse(TsvResource.read(TEXTURES).getFirst().value());
        assertTrue(MobModelTextureResolver.isRecordedDefault(recorded));
        assertEquals(mapped, MobModelTextureResolver.decide(BUNDLED_MODEL, recorded, mapped, true));

        ResourceLocation layer = ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/ignis_glow.png");
        assertEquals(mapped, MobModelTextureResolver.decide(BUNDLED_MODEL, layer, mapped, true));
    }
}
