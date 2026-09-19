package com.goodbird.cnpcgeckoaddon.mixin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads the classes the guarded mixins go into, so a guard that no longer fits its target fails
 * the build rather than the game's start.
 *
 * <p>Every injection that does more than read a field runs its body behind CrashGuard, and some
 * of them were split into a guarded handler and a {@code @Unique} body to get there. Mixin
 * merges all of it into CustomNPCs' and vanilla's classes, and a handler it cannot merge - a
 * shadow that is not there, a redirect that finds nothing - takes the whole target class down
 * with it, which for {@code EntityNPCInterface} or {@code DataAI} is every npc in the game. These
 * tests run under the game's transforming class loader, so loading a class here applies every
 * mixin aimed at it the way the game does. Client-only mixins are not applied in this
 * environment and are left to the game.</p>
 */
class GuardedMixinsApplyTest {

    @ParameterizedTest(name = "{1} is merged into {0}")
    @DisplayName("every guarded handler is merged into the class it is written for")
    @CsvSource({
            "noppes.npcs.entity.data.DataAI, cnpcgeckoaddon$saveSoundReaction",
            "noppes.npcs.entity.data.DataAI, cnpcgeckoaddon$loadSoundReaction",
            "noppes.npcs.entity.data.DataDisplay, cnpcgeckoaddon$saveCustomModel",
            "noppes.npcs.entity.data.DataDisplay, cnpcgeckoaddon$loadCustomModel",
            "noppes.npcs.entity.data.DataRanged, cnpcgeckoaddon$saveRangedExtra",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$tickTeleportPath",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$stopBossBarTracking",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$shutdownBossBar",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$disableBossController",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$tickLaunchPad",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$tickSoundReaction",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$addSoundInvestigationGoal",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$saveCarriedNpcAsItWas",
            "noppes.npcs.entity.EntityNPCInterface, cnpcgeckoaddon$addKeepDistanceGoal",
            "noppes.npcs.entity.EntityCustomNpc, cnpcgeckoaddon$limitModelHitbox",
            "noppes.npcs.entity.EntityCustomNpc, cnpcgeckoaddon$syncModelEntitySize",
            "noppes.npcs.ModelData, cnpcgeckoaddon$skipBrokenEntity",
            "noppes.npcs.ModelData, cnpcgeckoaddon$rememberFailedEntity",
            "noppes.npcs.ai.EntityAIAttackTarget, cnpcgeckoaddon$holdChaseForCastSpot",
            "noppes.npcs.ai.EntityAIAttackTarget, cnpcgeckoaddon$boundForCastSpot",
            "noppes.npcs.ai.EntityAIAttackTarget, cnpcgeckoaddon$replacesAttacks",
            "noppes.npcs.ai.EntityAIAttackTarget, cnpcgeckoaddon$standDownForAddonRangedAi",
            "noppes.npcs.ai.EntityAIAttackTarget, cnpcgeckoaddon$endChaseForAddonRangedAi",
            "noppes.npcs.ai.EntityAIAttackTarget, cnpcgeckoaddon$addonHoldsTheFight",
            "noppes.npcs.ai.EntityAIRangedAttack, cnpcgeckoaddon$suppressVanillaProjectile",
            "noppes.npcs.ai.EntityAIRangedAttack, cnpcgeckoaddon$replacesAttacks",
            "noppes.npcs.ai.EntityAIRangedAttack, cnpcgeckoaddon$standDownForAddonRangedAi",
            "noppes.npcs.ai.EntityAIRangedAttack, cnpcgeckoaddon$addonRunsRangedAi",
            "noppes.npcs.ai.EntityAIPounceTarget, cnpcgeckoaddon$disableBossPounce",
            "noppes.npcs.blocks.tiles.TileScripted, cnpcgeckoaddon$updateDisplay",
            "net.minecraft.world.level.material.FlowingFluid, cnpcgeckoaddon$freezeTemporaryFluid",
            "net.minecraft.world.level.material.FluidState, cnpcgeckoaddon$freezeRandomTick",
            "net.neoforged.neoforge.fluids.FluidInteractionRegistry, cnpcgeckoaddon$freezeInteraction",
            "net.minecraft.server.network.ServerGamePacketListenerImpl, cnpcgeckoaddon$lockCapturedPlayer",
            "net.minecraft.world.entity.LivingEntity, cnpcgeckoaddon$rewrapUnregisteredHolder",
    })
    void theGuardedHandlerIsMerged(String target, String handler) throws ClassNotFoundException {
        Class<?> type = Class.forName(target, false, GuardedMixinsApplyTest.class.getClassLoader());
        Set<String> fromTheAddon = new TreeSet<>();
        for (Method method : type.getDeclaredMethods()) {
            // Mixin may rename a handler as it merges it, but always keeps the name it was given as the tail.
            if (method.getName().endsWith(handler)) {
                return;
            }
            if (method.getName().contains("cnpcgeckoaddon$")) {
                fromTheAddon.add(method.getName());
            }
        }
        throw new AssertionError(target + " was loaded without " + handler + " - either the mixin no longer "
                + "applies, or the tests no longer run under the transforming class loader ("
                + type.getClassLoader() + "); merged from the addon: " + fromTheAddon);
    }
}
