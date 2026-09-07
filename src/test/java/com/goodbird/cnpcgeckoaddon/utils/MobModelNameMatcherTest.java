package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobModelNameMatcherTest {

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("somemod", "textures/entity/" + name + ".png");
    }

    private static int score(String model, String texture) {
        return MobModelNameMatcher.score(model, texture(texture));
    }

    @Test
    @DisplayName("an exact name wins outright")
    void anExactNameScoresHighest() {
        int exact = score("wraith", "wraith");
        assertTrue(exact > score("wraith", "wraith2"), "an exact name should beat a numbered variant");
        assertTrue(exact > score("wraith", "wraithling"), "an exact name should beat a longer relative");
        assertTrue(exact > score("wraith", "golem"), "an exact name should beat an unrelated one");
    }

    @Test
    @DisplayName("punctuation and case never decide a match")
    void namesAreComparedNormalized() {
        assertEquals(score("Ice_Golem", "icegolem"), score("icegolem", "icegolem"));
        assertEquals("icegolem", MobModelNameMatcher.normalize("Ice_Golem"));
        assertEquals("icegolem", MobModelNameMatcher.normalize("ice-golem "));
    }

    @Test
    @DisplayName("render layers lose to the plain skin of the same mob")
    void auxiliaryTexturesArePushedDown() {
        assertTrue(score("wraith", "wraith") > score("wraith", "wraith_eyes"));
        assertTrue(score("wraith", "wraith") > score("wraith", "wraith_overlay"));
        assertTrue(score("wraith", "wraith") > score("wraith", "wraith_layer_1"));
        assertTrue(score("wraith", "wraith") > score("wraith", "wraith_mask"));
        assertTrue(score("wraith", "wraith") > score("wraith", "wraith_e"));
        assertTrue(MobModelNameMatcher.isAuxiliaryTexture("wraith_eyes"));
        assertFalse(MobModelNameMatcher.isAuxiliaryTexture("wraith"));
    }

    @Test
    @DisplayName("an unrelated texture stays under the threshold")
    void unrelatedNamesAreRefused() {
        assertTrue(score("wraith", "cobblestone_golem_statue")
                < MobModelNameMatcher.MATCH_THRESHOLD);
        assertTrue(score("wraith", "wraith") >= MobModelNameMatcher.MATCH_THRESHOLD);
    }

    @Test
    @DisplayName("a numbered or suffixed variant still counts as the same mob")
    void variantsOfTheSameMobStillMatch() {
        assertTrue(score("spidertarantula", "spidertarantula1")
                >= MobModelNameMatcher.MATCH_THRESHOLD);
        assertTrue(score("ice_spike_big", "ice_spike") >= MobModelNameMatcher.MATCH_THRESHOLD);
    }

    @Test
    @DisplayName("file names are read off a resource path the way the tables spell them")
    void fileHelpersReadResourcePaths() {
        assertEquals("felsteed", MobModelNameMatcher.fileStem("geo/felsteed.geo.json", ".geo.json"));
        assertEquals("shrimp", MobModelNameMatcher.fileStem("textures/entity/sea/shrimp.png", ".png"));
        assertEquals("shrimp.png", MobModelNameMatcher.fileName("textures/entity/sea/shrimp.png"));
        assertEquals("plain", MobModelNameMatcher.fileName("plain"));
    }
}
