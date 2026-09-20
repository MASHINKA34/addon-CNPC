package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The agreement between the crystal's code and its artwork, written out twice.
 *
 * <p>The model, the animation file, the four drawings and the two clip names are made by
 * somebody else, possibly after this code ships. Nothing fails loudly when the two sides drift
 * apart: the code simply never finds the model and quietly draws the block instead, which is
 * exactly what it does when the artwork has not arrived yet, so the mistake is invisible.</p>
 *
 * <p>So every string is spelt out here as a literal rather than read from the class under test.
 * Changing a path on one side alone fails here, which is the only place that can tell the
 * difference between "not drawn yet" and "drawn somewhere else".</p>
 */
class RiftCrystalContractTest {

    @Test
    @DisplayName("the three file paths are the ones the artwork is drawn to")
    void thePathsAreTheAgreedOnes() {
        assertEquals("cnpcgeckoaddon", RiftCrystalContract.NAMESPACE);
        assertEquals("geo/rift_crystal.geo.json", RiftCrystalContract.GEO_PATH);
        assertEquals("animations/rift_crystal.animation.json", RiftCrystalContract.ANIMATION_PATH);
        assertEquals("textures/entity/rift_crystal/%s.png", RiftCrystalContract.TEXTURE_PATH_FORMAT);
        assertEquals("textures/entity/rift_crystal/amethyst.png", RiftCrystalContract.texturePath("amethyst"));
        assertEquals("textures/entity/rift_crystal/ember.png", RiftCrystalContract.texturePath("ember"));
        assertEquals("textures/entity/rift_crystal/void.png", RiftCrystalContract.texturePath("void"));
        assertEquals("textures/entity/rift_crystal/ice.png", RiftCrystalContract.texturePath("ice"));
        assertEquals("textures/entity/rift_crystal/amethyst.png", RiftCrystalContract.DEFAULT_TEXTURE_PATH);
        assertEquals(RiftCrystalContract.texturePath(RiftCrystalContract.DEFAULT_SKIN),
                RiftCrystalContract.DEFAULT_TEXTURE_PATH,
                "the spelt-out fallback path has drifted from the one the format builds");
    }

    @Test
    @DisplayName("every agreed path is a resource id the game can be asked for")
    void everyPathBuildsAnId() {
        for (String path : List.of(RiftCrystalContract.GEO_PATH, RiftCrystalContract.ANIMATION_PATH,
                RiftCrystalContract.texturePath(RiftCrystalContract.DEFAULT_SKIN))) {
            assertNotNull(ResourceLocation.tryBuild(RiftCrystalContract.NAMESPACE, path),
                    path + " is not a legal resource path");
        }
        for (String skin : RiftCrystalContract.SKINS) {
            assertNotNull(ResourceLocation.tryBuild(RiftCrystalContract.NAMESPACE,
                    RiftCrystalContract.texturePath(skin)), skin);
        }
    }

    @Test
    @DisplayName("the four skins, the default among them, are the ones the artwork ships")
    void theSkinsAreTheAgreedFour() {
        assertEquals(List.of("amethyst", "ember", "void", "ice"), RiftCrystalContract.SKINS);
        assertEquals("amethyst", RiftCrystalContract.DEFAULT_SKIN);
        assertTrue(RiftCrystalContract.SKINS.contains(RiftCrystalContract.DEFAULT_SKIN),
                "the default has to be one of the drawings");
        for (String skin : RiftCrystalContract.SKINS) {
            assertEquals(skin, RiftCrystalContract.cleanSkin(skin), skin + " is not already clean");
        }
    }

    @Test
    @DisplayName("the controller and the two clips are named the way the animation file names them")
    void theClipsAreTheAgreedTwo() {
        assertEquals("main", RiftCrystalContract.CONTROLLER);
        assertEquals("idle", RiftCrystalContract.IDLE_ANIMATION);
        assertEquals("collect", RiftCrystalContract.COLLECT_ANIMATION);
    }

    @Test
    @DisplayName("a collected crystal is held for exactly the half second the collect clip runs")
    void theLingerMatchesTheClip() {
        assertEquals(10, RiftCrystalContract.COLLECT_LINGER_TICKS);
        assertEquals(32, RiftCrystalContract.MAX_SKIN_LENGTH);
        assertFalse(RiftCrystalContract.lingerOver(1000L, 1000L), "the tick it was collected on");
        assertFalse(RiftCrystalContract.lingerOver(1000L, 1009L), "one tick of clip still to run");
        assertTrue(RiftCrystalContract.lingerOver(1000L, 1010L), "the clip has run");
        assertTrue(RiftCrystalContract.lingerOver(1000L, 5000L), "a tick that was missed is not a crystal for ever");
        assertFalse(RiftCrystalContract.lingerOver(1000L, 900L),
                "a clock that went backwards holds it rather than taking it early");
    }

    @Test
    @DisplayName("the model is only drawn when it was asked for and both of its files are loaded")
    void bothHalvesOfTheArtworkAreNeeded() {
        assertTrue(RiftCrystalContract.drawsModel(true, true, true));
        assertFalse(RiftCrystalContract.drawsModel(true, false, true), "no geometry: the block is drawn");
        assertFalse(RiftCrystalContract.drawsModel(true, true, false), "no animation: the block is drawn");
        assertFalse(RiftCrystalContract.drawsModel(true, false, false));
        assertFalse(RiftCrystalContract.drawsModel(false, true, true), "the block look is never overruled");
        assertFalse(RiftCrystalContract.drawsModel(false, false, false));
    }

    @Test
    @DisplayName("the rift's own settings default to the block look, so an old boss is unchanged")
    void theDefaultLookIsTheBlock() {
        assertEquals(0, BossRiftSettings.LOOK_BLOCK);
        assertEquals(1, BossRiftSettings.LOOK_MODEL);
        BossRiftSettings rift = new BossRiftSettings();
        assertEquals(BossRiftSettings.LOOK_BLOCK, rift.getCrystalLook());
        assertEquals(RiftCrystalContract.DEFAULT_SKIN, rift.getCrystalSkin());
        assertEquals(2, BossRiftSettings.LOOK_LABELS.length);
        assertEquals("cnpcgeckoaddon.boss.rift_crystal_look.block", BossRiftSettings.LOOK_LABELS[0]);
        assertEquals("cnpcgeckoaddon.boss.rift_crystal_look.model", BossRiftSettings.LOOK_LABELS[1]);
    }
}
