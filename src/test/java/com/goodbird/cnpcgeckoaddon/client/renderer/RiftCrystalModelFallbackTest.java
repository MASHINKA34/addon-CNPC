package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.client.model.ModelRiftCrystal;
import com.goodbird.cnpcgeckoaddon.data.RiftCrystalContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.GeckoLibCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a crystal set to the model look is drawn as while the artwork is not loaded.
 *
 * <p>Nothing in this test JVM ever loads a resource pack, so GeckoLib's two caches are empty -
 * which is exactly the state of a client whose install has the addon but not the crystal's
 * geometry and clips, whether because the artwork had not shipped yet or because somebody
 * stripped the assets out. The gate has to read that as "draw the block", and it has to read it
 * from the same two maps the game fills, not from a flag somebody remembered to set.</p>
 *
 * <p>Checked here rather than only in the contract's own test because the wiring is the part
 * that goes wrong: a gate that asks the wrong map, or asks only one of the two, passes every
 * test of the rule itself and still puts a black and magenta crystal on somebody's platform.</p>
 */
class RiftCrystalModelFallbackTest {

    @Test
    @DisplayName("with nothing loaded the renderer does not reach for the model")
    void anEmptyCacheFallsBackToTheBlock() {
        assertFalse(GeckoLibCache.getBakedModels().containsKey(ModelRiftCrystal.GEO),
                "no pack has been read, so the geometry cannot be baked");
        assertFalse(GeckoLibCache.getBakedAnimations().containsKey(ModelRiftCrystal.ANIMATIONS),
                "nor the clips");
        assertFalse(RenderBossRiftCrystal.modelIsLoaded(),
                "the crystal would be drawn as a model that is not there");
    }

    @Test
    @DisplayName("the two ids the gate asks about are the contract's own")
    void theGateAsksAboutTheAgreedFiles() {
        assertEquals(RiftCrystalContract.NAMESPACE, ModelRiftCrystal.GEO.getNamespace());
        assertEquals(RiftCrystalContract.GEO_PATH, ModelRiftCrystal.GEO.getPath());
        assertEquals(RiftCrystalContract.NAMESPACE, ModelRiftCrystal.ANIMATIONS.getNamespace());
        assertEquals(RiftCrystalContract.ANIMATION_PATH, ModelRiftCrystal.ANIMATIONS.getPath());
    }

    @Test
    @DisplayName("one half of the artwork on its own is still the block")
    void halfTheArtworkIsNotEnough() {
        // The gate the renderer hands its two answers to, with the answers spelt out: a geometry
        // with no clips would hang there as a still lump, which reads as broken, not as unfinished.
        assertFalse(RiftCrystalContract.drawsModel(true, true, false));
        assertFalse(RiftCrystalContract.drawsModel(true, false, true));
    }
}
