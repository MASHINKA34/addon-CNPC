package com.goodbird.cnpcgeckoaddon.mixin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies the mixins that keep an npc from shooting an empty projectile, by loading the
 * CustomNPCs classes they go into.
 *
 * <p>These tests run under the game's transforming class loader with the addon's mixin config
 * registered, so a CustomNPCs class loaded here is loaded the way the game loads it: every
 * mixin aimed at it is applied on the spot, and one that does not fit - a shadowed field
 * CustomNPCs does not have, a redirect that finds fewer calls than it requires - fails the
 * load instead of the player's start. That matters more for these three than for most. The
 * game itself cannot be run as part of the build, and what they guard against is a server
 * going down: {@code DataRanged} is where a projectile entity is checked on the way in, the
 * npc's {@code performRangedAttack} is where CustomNPCs is kept from shooting an empty slot,
 * and {@code EntityProjectile} is the last line when something shoots one anyway.</p>
 *
 * <p>Nothing is initialised - the classes are only defined, which is all a mixin needs - so
 * no registry or level is touched.</p>
 */
class ProjectileMixinsApplyTest {

    @Test
    @DisplayName("the impact particle guard is merged into CustomNPCs' projectile")
    void theParticleGuardApplies() throws ClassNotFoundException {
        assertMerged("noppes.npcs.entity.EntityProjectile", "cnpcgeckoaddon$impactParticleStack");
    }

    @Test
    @DisplayName("the ranged settings hook, with the npc it shadows, is merged into DataRanged")
    void theRangedSettingsHookApplies() throws ClassNotFoundException {
        assertMerged("noppes.npcs.entity.data.DataRanged", "cnpcgeckoaddon$loadRangedExtra");
    }

    @Test
    @DisplayName("the shot choice is merged into the npc's ranged attack")
    void theShotChoiceApplies() throws ClassNotFoundException {
        assertMerged("noppes.npcs.entity.EntityNPCInterface", "cnpcgeckoaddon$performCustomRangedAttack");
        assertMerged("noppes.npcs.entity.EntityNPCInterface", "cnpcgeckoaddon$fireVolley");
    }

    /** Mixin renames a handler as it merges it, but always keeps the name it was given as the tail. */
    private static void assertMerged(String target, String handler) throws ClassNotFoundException {
        Class<?> type = Class.forName(target, false, ProjectileMixinsApplyTest.class.getClassLoader());
        Set<String> merged = new TreeSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().contains("cnpcgeckoaddon$")) {
                merged.add(method.getName());
            }
        }
        assertTrue(merged.stream().anyMatch(name -> name.endsWith(handler)),
                target + " was loaded without " + handler + " - either the mixin no longer applies to this "
                        + "CustomNPCs, or the tests no longer run under the transforming class loader ("
                        + type.getClassLoader() + "); merged from the addon: " + merged);
    }
}
