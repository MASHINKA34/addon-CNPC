package com.goodbird.cnpcgeckoaddon.mixin;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityProjectile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 */
class ProjectileMixinsApplyTest {

    private static final String PARTICLE_GUARD = "cnpcgeckoaddon$impactParticleStack";

    @Test
    @DisplayName("the impact particle guard is merged into CustomNPCs' projectile")
    void theParticleGuardApplies() throws ClassNotFoundException {
        assertNotNull(merged("noppes.npcs.entity.EntityProjectile", PARTICLE_GUARD));
    }

    @Test
    @DisplayName("the ranged settings hook, with the npc it shadows, is merged into DataRanged")
    void theRangedSettingsHookApplies() throws ClassNotFoundException {
        assertNotNull(merged("noppes.npcs.entity.data.DataRanged", "cnpcgeckoaddon$loadRangedExtra"));
    }

    @Test
    @DisplayName("the shot choice is merged into the npc's ranged attack")
    void theShotChoiceApplies() throws ClassNotFoundException {
        assertNotNull(merged("noppes.npcs.entity.EntityNPCInterface", "cnpcgeckoaddon$performCustomRangedAttack"));
        assertNotNull(merged("noppes.npcs.entity.EntityNPCInterface", "cnpcgeckoaddon$fireVolley"));
    }

    /**
     * The crash itself, end to end but for the level: the stack CustomNPCs would have built
     * its particles of, put through the merged guard and into the constructor that threw.
     * A projectile never needs its level to be made or to be asked what it carries, which is
     * what lets this run without a world.
     */
    @Test
    @DisplayName("a projectile without an item makes its impact particles of an arrow instead of throwing")
    void anEmptyProjectileNoLongerThrowsOnImpact() throws ReflectiveOperationException {
        EntityProjectile projectile = emptyProjectile();
        assertTrue(projectile.getItemDisplay().isEmpty(), "the fixture is meant to carry no item");
        assertThrows(IllegalArgumentException.class,
                () -> new ItemParticleOption(ParticleTypes.ITEM, projectile.getItemDisplay()),
                "the game no longer refuses item particles of an empty stack, so the guard has nothing to guard");

        ItemStack guarded = guard(projectile);
        assertTrue(guarded.is(Items.ARROW), "an empty projectile's particles should be an arrow's, not " + guarded);
        assertDoesNotThrow(() -> new ItemParticleOption(ParticleTypes.ITEM, guarded));
    }

    @Test
    @DisplayName("a projectile with an item keeps that item's particles")
    void aRealItemIsLeftAlone() throws ReflectiveOperationException {
        EntityProjectile projectile = emptyProjectile();
        projectile.setThrownItem(new ItemStack(Items.SNOWBALL));
        ItemStack guarded = guard(projectile);
        assertTrue(guarded.is(Items.SNOWBALL), "a snowball's particles should stay a snowball's, not " + guarded);
        assertTrue(projectile.getItemDisplay().is(Items.SNOWBALL), "the guard must not touch what the projectile carries");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityProjectile emptyProjectile() {
        return new EntityProjectile((EntityType) CustomEntities.entityProjectile, null);
    }

    /** Calls the redirect's handler the way {@code onHit} does once it is merged: on the projectile, with itself. */
    private static ItemStack guard(EntityProjectile projectile) throws ReflectiveOperationException {
        Method handler = merged("noppes.npcs.entity.EntityProjectile", PARTICLE_GUARD);
        handler.setAccessible(true);
        return (ItemStack) handler.invoke(projectile, projectile);
    }

    /** Mixin renames a handler as it merges it, but always keeps the name it was given as the tail. */
    private static Method merged(String target, String handler) throws ClassNotFoundException {
        Class<?> type = Class.forName(target, false, ProjectileMixinsApplyTest.class.getClassLoader());
        Set<String> fromTheAddon = new TreeSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().endsWith(handler)) {
                return method;
            }
            if (method.getName().contains("cnpcgeckoaddon$")) {
                fromTheAddon.add(method.getName());
            }
        }
        throw new AssertionError(target + " was loaded without " + handler + " - either the mixin no longer "
                + "applies to this CustomNPCs, or the tests no longer run under the transforming class loader ("
                + type.getClassLoader() + "); merged from the addon: " + fromTheAddon);
    }
}
