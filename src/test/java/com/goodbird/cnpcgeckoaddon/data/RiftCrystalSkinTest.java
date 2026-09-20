package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a crystal skin id is allowed to be by the time it reaches a texture lookup.
 *
 * <p>The id is typed into a field on a screen and then used to build a resource path, which
 * throws on the first character it may not hold - a capital, a space, a colon - on the render
 * thread, where a throw is a crash. So it is cleaned rather than trusted, and cleaning is
 * silent when it is wrong: an id that came back empty, or one whose thirty-third character was
 * quietly kept, only shows up as a crystal drawn in the wrong skin, or as a client that fell
 * over looking at one.</p>
 */
class RiftCrystalSkinTest {

    @Test
    @DisplayName("a skin id is lower-cased and kept to what a resource path may hold")
    void anIdIsCleanedRatherThanTrusted() {
        assertEquals("ember", RiftCrystalContract.cleanSkin("Ember"), "typed with a capital");
        assertEquals("amethyst", RiftCrystalContract.cleanSkin("AMETHYST"));
        assertEquals("void", RiftCrystalContract.cleanSkin("  void  "), "blanks round it are forgiven");
        assertEquals("ice", RiftCrystalContract.cleanSkin("ice"));
        assertEquals("my_skin_2", RiftCrystalContract.cleanSkin("my_skin_2"),
                "digits and underscores are path characters and stay");
    }

    @Test
    @DisplayName("nothing usable left is the default skin rather than an empty path")
    void nothingUsableFallsBackToAmethyst() {
        assertEquals("amethyst", RiftCrystalContract.DEFAULT_SKIN);
        assertEquals("amethyst", RiftCrystalContract.cleanSkin(""));
        assertEquals("amethyst", RiftCrystalContract.cleanSkin("   "));
        assertEquals("amethyst", RiftCrystalContract.cleanSkin(null));
        assertEquals("amethyst", RiftCrystalContract.cleanSkin("!!! ???"), "no path character in it at all");
    }

    @Test
    @DisplayName("characters a path may not hold are dropped, and the id is cut to length")
    void illegalCharactersGoAndTheLengthIsCapped() {
        assertEquals("minecraftember", RiftCrystalContract.cleanSkin("minecraft:ember"));
        assertEquals("deepember", RiftCrystalContract.cleanSkin("deep ember"));
        assertEquals("aback", RiftCrystalContract.cleanSkin("a\\back"));
        assertEquals("ember", RiftCrystalContract.cleanSkin("ember\n"));

        String long64 = "e".repeat(64);
        assertEquals(RiftCrystalContract.MAX_SKIN_LENGTH, RiftCrystalContract.cleanSkin(long64).length());
        assertEquals("e".repeat(RiftCrystalContract.MAX_SKIN_LENGTH), RiftCrystalContract.cleanSkin(long64));
        // The cut counts the characters that survive, not the ones that were typed.
        assertEquals("e".repeat(RiftCrystalContract.MAX_SKIN_LENGTH),
                RiftCrystalContract.cleanSkin("E E E E ".repeat(40)));
    }

    @Test
    @DisplayName("a cleaned id is a path the texture lookup can be built from")
    void aCleanedIdBuildsAPath() {
        assertEquals("textures/entity/rift_crystal/amethyst.png", RiftCrystalContract.texturePath(""));
        assertEquals("textures/entity/rift_crystal/ember.png", RiftCrystalContract.texturePath("Ember"));
        assertEquals("textures/entity/rift_crystal/ice.png", RiftCrystalContract.texturePath("ice"));
        for (String skin : RiftCrystalContract.SKINS) {
            assertTrue(net.minecraft.resources.ResourceLocation
                            .tryBuild(RiftCrystalContract.NAMESPACE, RiftCrystalContract.texturePath(skin)) != null,
                    skin + " does not build a resource id");
        }
        assertTrue(RiftCrystalContract.isKnownSkin("Void"));
        assertFalse(RiftCrystalContract.isKnownSkin("granite"), "not one the artwork ships");
        assertTrue(RiftCrystalContract.isKnownSkin("???"), "nothing left is the default, which is known");
    }

    @Test
    @DisplayName("the rift's own skin setting cleans what it is given, saved or typed")
    void theSettingCleansBothWays() {
        BossRiftSettings rift = new BossRiftSettings();
        assertEquals("amethyst", rift.getCrystalSkin(), "the default look needs no skin, but has one");
        rift.setCrystalSkin("Ember");
        assertEquals("ember", rift.getCrystalSkin());
        rift.setCrystalSkin("");
        assertEquals("amethyst", rift.getCrystalSkin());

        rift.setCrystalSkin("ice");
        rift.setCrystalLook(BossRiftSettings.LOOK_MODEL);
        CompoundTag tag = new CompoundTag();
        rift.writeToNBT(tag);
        BossRiftSettings back = new BossRiftSettings();
        back.readFromNBT(tag);
        assertEquals("ice", back.getCrystalSkin());
        assertTrue(back.isCrystalModel());

        // A save hand-edited into holding something a path cannot: read, not thrown over.
        tag.putString("RiftCrystalSkin", "Deep Void:2");
        tag.putInt("RiftCrystalLook", 99);
        BossRiftSettings poisoned = new BossRiftSettings();
        poisoned.readFromNBT(tag);
        assertEquals("deepvoid2", poisoned.getCrystalSkin());
        assertEquals(BossRiftSettings.LOOK_MODEL, poisoned.getCrystalLook(), "clamped to the looks that exist");
        tag.remove("RiftCrystalSkin");
        tag.remove("RiftCrystalLook");
        BossRiftSettings old = new BossRiftSettings();
        old.readFromNBT(tag);
        assertEquals(BossRiftSettings.LOOK_BLOCK, old.getCrystalLook(), "a save from before the model");
        assertEquals("amethyst", old.getCrystalSkin());
    }
}
