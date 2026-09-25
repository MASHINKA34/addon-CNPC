package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The sheet a model without a recorded one is dressed in: a png named after it in its own
 * namespace.
 *
 * <p>This runs for the models of every mod the picker lists, not only the bundled ones, so a
 * loose match is a stranger's sheet on somebody's npc; a missed one is Steve stretched over a
 * mob. Both only show once somebody picks that one model.</p>
 */
class MobModelTextureNameMatchTest {

    private static ResourceLocation model(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static ResourceLocation texture(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static ResourceLocation match(ResourceLocation model, ResourceLocation... candidates) {
        return MobModelNameMatcher.findNamedTexture(model, List.of(candidates));
    }

    @Test
    @DisplayName("a png with the model's exact name is its sheet")
    void theExactNameWins() {
        ResourceLocation wraith = model("somemod", "geo/wraith.geo.json");
        ResourceLocation own = texture("somemod", "textures/entity/wraith.png");
        assertEquals(own, match(wraith,
                texture("somemod", "textures/entity/wraith_texture.png"),
                texture("somemod", "textures/entity/wraith2.png"),
                own));
        ResourceLocation lord = texture("somemod", "textures/entity/wraith_lord.png");
        assertEquals(lord, match(model("somemod", "geo/wraith-lord.geo.json"), lord),
                "punctuation never decides a name");
    }

    @Test
    @DisplayName("the texture and skin words around the name still make it the model's sheet")
    void theSheetWordsAreForgiven() {
        ResourceLocation golem = model("somemod", "geo/ice_golem.geo.json");
        ResourceLocation suffixed = texture("somemod", "textures/entity/ice_golem_texture.png");
        assertEquals(suffixed, match(golem, suffixed, texture("somemod", "textures/entity/golem.png")));
        ResourceLocation prefixed = texture("somemod", "textures/entity/skin_ice_golem.png");
        assertEquals(prefixed, match(golem, prefixed));
    }

    @Test
    @DisplayName("of numbered sheets the lowest number is taken, and the model's own number may be dropped")
    void numberedSheetsCount() {
        ResourceLocation deepling = model("somemod", "geo/deepling.geo.json");
        ResourceLocation first = texture("somemod", "textures/entity/deepling/deepling_1.png");
        assertEquals(first, match(deepling, texture("somemod", "textures/entity/deepling/deepling_2.png"), first));

        ResourceLocation secondForm = model("somemod", "geo/lord_of_pumpkins2.geo.json");
        ResourceLocation sheet = texture("somemod", "textures/entity/lordofpumpkins.png");
        assertEquals(sheet, match(secondForm, sheet), "a second form wears the first form's sheet");
    }

    @Test
    @DisplayName("the model and entity words exporters wrap a name in are taken off")
    void exporterWordsAreTakenOff() {
        ResourceLocation ignis = model("somemod", "geo/ignis_model.geo.json");
        ResourceLocation sheet = texture("somemod", "textures/entity/ignis.png");
        assertEquals(sheet, match(ignis, sheet));
    }

    @Test
    @DisplayName("a png of another namespace never counts, whatever it is called")
    void namespacesDoNotMix() {
        ResourceLocation wraith = model("somemod", "geo/wraith.geo.json");
        assertNull(match(wraith, texture("othermod", "textures/entity/wraith.png")));
        ResourceLocation own = texture("somemod", "textures/entity/wraith_skin.png");
        assertEquals(own, match(wraith, texture("othermod", "textures/entity/wraith.png"), own),
                "an exact name elsewhere loses to a looser one at home");
    }

    @Test
    @DisplayName("a relative's name, a render layer or a name merely alike is not the model's sheet")
    void looseNamesAreRefused() {
        ResourceLocation wraith = model("somemod", "geo/wraith.geo.json");
        assertNull(match(wraith, texture("somemod", "textures/entity/wraithling.png")));
        assertNull(match(wraith, texture("somemod", "textures/entity/wraith_eyes.png")));
        assertNull(match(wraith, texture("somemod", "textures/entity/wraith_glow.png")));
        assertNull(match(wraith, texture("somemod", "textures/entity/wrath.png")));
        assertNull(match(model("somemod", "geo/vor.geo.json"), texture("somemod", "textures/entity/vortex.png")),
                "only the whole words texture and skin are forgiven");
        assertNull(match(model("somemod", "geo/a1.geo.json"), texture("somemod", "textures/entity/a.png")),
                "a name too short to stand on its own is not matched once its number is dropped");
    }

    @Test
    @DisplayName("of equal names, the geometry's own folder wins, then an entity folder")
    void theGeometrysFolderWins() {
        ResourceLocation gun = model("somemod", "geo/item/pistol.geo.json");
        ResourceLocation itemSheet = texture("somemod", "textures/item/pistol.png");
        assertEquals(itemSheet, match(gun, texture("somemod", "textures/entity/pistol.png"), itemSheet));

        ResourceLocation mob = model("somemod", "geo/wraith.geo.json");
        ResourceLocation entitySheet = texture("somemod", "textures/entity/wraith.png");
        assertEquals(entitySheet, match(mob, texture("somemod", "textures/block/wraith.png"), entitySheet));
    }

    @Test
    @DisplayName("nothing to match against is no match")
    void nothingIsNothing() {
        assertNull(MobModelNameMatcher.findNamedTexture(model("somemod", "geo/wraith.geo.json"), List.of()));
        assertNull(MobModelNameMatcher.findNamedTexture(null, List.of(texture("somemod", "textures/wraith.png"))));
    }
}
