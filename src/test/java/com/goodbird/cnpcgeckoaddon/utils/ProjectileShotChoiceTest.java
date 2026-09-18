package com.goodbird.cnpcgeckoaddon.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.goodbird.cnpcgeckoaddon.utils.ProjectileShotChoice.CNPC;
import static com.goodbird.cnpcgeckoaddon.utils.ProjectileShotChoice.CUSTOM;
import static com.goodbird.cnpcgeckoaddon.utils.ProjectileShotChoice.FALLBACK;
import static com.goodbird.cnpcgeckoaddon.utils.ProjectileShotChoice.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an npc's ranged attack is fired with, branch by branch.
 *
 * <p>The rule behind the table is one a server went down for: CustomNPCs launches its own
 * projectile whether or not the npc has an item for it, and one made of an empty stack throws
 * out of {@code onHit} when it lands. So the answer {@code CNPC} is only ever allowed while
 * there is an item, and the last test here asks that of all thirty-two inputs at once rather
 * than of the handful somebody thought to write down.</p>
 */
class ProjectileShotChoiceTest {

    @Test
    @DisplayName("a usable entity of the npc's own is fired whatever else is configured")
    void aUsableCustomEntityWins() {
        for (boolean itemEmpty : new boolean[]{false, true}) {
            for (boolean fallbackConfigured : new boolean[]{false, true}) {
                for (boolean fallbackUsable : new boolean[]{false, true}) {
                    assertEquals(CUSTOM, ProjectileShotChoice.decide(true, true, itemEmpty,
                            fallbackConfigured, fallbackUsable));
                }
            }
        }
    }

    @Test
    @DisplayName("an npc with a projectile item and no entity shoots the item, as it always did")
    void anItemIsLeftToCustomNpcs() {
        assertEquals(CNPC, ProjectileShotChoice.decide(false, false, false, true, true));
        assertEquals(CNPC, ProjectileShotChoice.decide(false, false, false, false, false));
        // The default fallback is always configured; it must not take the shot from the item.
        assertEquals(CNPC, ProjectileShotChoice.decide(false, true, false, true, true));
    }

    @Test
    @DisplayName("an entity that turned out not to be a projectile gives way to the item")
    void anUnusableCustomEntityGivesWayToTheItem() {
        assertEquals(CNPC, ProjectileShotChoice.decide(true, false, false, true, true));
    }

    @Test
    @DisplayName("without an item the fallback is fired instead of an empty CustomNPCs projectile")
    void theFallbackCoversAnEmptySlot() {
        assertEquals(FALLBACK, ProjectileShotChoice.decide(false, false, true, true, true));
        // The crash this exists for: a non-projectile entity and nothing in the slot.
        assertEquals(FALLBACK, ProjectileShotChoice.decide(true, false, true, true, true));
    }

    @Test
    @DisplayName("an empty fallback means no shot at all")
    void anEmptyFallbackHoldsFire() {
        assertEquals(NONE, ProjectileShotChoice.decide(false, false, true, false, false));
        assertEquals(NONE, ProjectileShotChoice.decide(true, false, true, false, false));
        // "Usable" says nothing about an id that is not there.
        assertEquals(NONE, ProjectileShotChoice.decide(false, false, true, false, true));
    }

    @Test
    @DisplayName("a fallback that is not a projectile either ends in no shot")
    void anUnusableFallbackHoldsFire() {
        assertEquals(NONE, ProjectileShotChoice.decide(false, false, true, true, false));
        assertEquals(NONE, ProjectileShotChoice.decide(true, false, true, true, false));
    }

    @Test
    @DisplayName("a custom id that is set but unusable is not fired just because it is set")
    void configuredIsNotUsable() {
        assertNotEquals(CUSTOM, ProjectileShotChoice.decide(true, false, true, true, true));
        assertNotEquals(CUSTOM, ProjectileShotChoice.decide(false, true, true, true, true));
    }

    @Test
    @DisplayName("CustomNPCs is never left to shoot an empty slot, whatever the other four say")
    void customNpcsNeverShootsAnEmptySlot() {
        for (int bits = 0; bits < 32; bits++) {
            boolean customConfigured = (bits & 1) != 0;
            boolean customUsable = (bits & 2) != 0;
            boolean itemEmpty = (bits & 4) != 0;
            boolean fallbackConfigured = (bits & 8) != 0;
            boolean fallbackUsable = (bits & 16) != 0;
            ProjectileShotChoice choice = ProjectileShotChoice.decide(customConfigured, customUsable,
                    itemEmpty, fallbackConfigured, fallbackUsable);
            String inputs = "decide(" + customConfigured + ", " + customUsable + ", " + itemEmpty
                    + ", " + fallbackConfigured + ", " + fallbackUsable + ")";
            if (itemEmpty) {
                assertNotEquals(CNPC, choice, inputs + " leaves an empty slot to CustomNPCs");
            }
            if (choice == CUSTOM) {
                assertTrue(customConfigured && customUsable, inputs + " fires an entity it cannot");
            }
            if (choice == FALLBACK) {
                assertTrue(fallbackConfigured && fallbackUsable, inputs + " fires a fallback it cannot");
            }
            if (choice == NONE) {
                assertTrue(itemEmpty, inputs + " holds the fire of an npc with an item");
            }
        }
    }
}
