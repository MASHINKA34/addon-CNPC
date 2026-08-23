package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Covers the part of the fluid spit that touches the world, because getting it wrong
 * means permanently damaging someone's save: the puddle must stay put, and it must give
 * the original terrain back.
 */
@GameTestHolder(CNPCGeckoAddon.MODID)
public class TemporaryFluidGameTest {

    private static final int LIFETIME_TICKS = 20;

    @GameTest(template = "fluid_platform", timeoutTicks = 200)
    public static void temporaryFluidDoesNotSpreadAndIsRestored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos absolute = helper.absolutePos(relative);
        BlockState before = level.getBlockState(absolute);

        TemporaryFluidStore store = TemporaryFluidStore.get(level);
        helper.assertTrue(store.place(level, absolute, Blocks.LAVA.defaultBlockState(), LIFETIME_TICKS),
                "the temporary fluid should have been placed");
        helper.assertBlockPresent(Blocks.LAVA, relative);
        helper.assertTrue(TemporaryFluidStore.isFrozen(level, absolute),
                "the placed position should be frozen");

        // Long enough for lava to have flowed several blocks if freezing did not work.
        helper.runAfterDelay(LIFETIME_TICKS - 5, () -> {
            helper.assertBlockPresent(Blocks.LAVA, relative);
            helper.assertBlockNotPresent(Blocks.LAVA, relative.east());
            helper.assertBlockNotPresent(Blocks.LAVA, relative.west());
            helper.assertBlockNotPresent(Blocks.LAVA, relative.north());
            helper.assertBlockNotPresent(Blocks.LAVA, relative.south());
        });

        helper.runAfterDelay(LIFETIME_TICKS + 20, () -> {
            helper.assertBlockPresent(before.getBlock(), relative);
            helper.assertFalse(TemporaryFluidStore.isFrozen(level, absolute),
                    "the position should be released again");
            helper.assertFalse(TemporaryFluidStore.hasAnyPending(),
                    "no temporary fluid should be left over");
            helper.succeed();
        });
    }

    /** A block a player built must never be swallowed by a puddle. */
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void temporaryFluidKeepsSolidBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.OAK_PLANKS);

        helper.assertFalse(
                TemporaryFluidStore.get(level).place(level, absolute, Blocks.LAVA.defaultBlockState(), LIFETIME_TICKS),
                "a solid block must not be replaced by the puddle");
        helper.assertBlockPresent(Blocks.OAK_PLANKS, relative);
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void fluidSpitProjectileIsUsable(GameTestHelper helper) {
        helper.assertTrue(EntityRegistry.entityFluidSpit != null,
                "the fluid spit entity type should be registered");
        EntityFluidSpit spit = EntityRegistry.entityFluidSpit.create(helper.getLevel());
        helper.assertTrue(spit != null, "the fluid spit entity should be constructible");
        spit.discard();

        helper.assertTrue(FluidBlockUtil.resolve("minecraft:lava") != null,
                "lava should resolve to a fluid block");
        helper.assertTrue(FluidBlockUtil.resolve("minecraft:stone") == null,
                "a non-fluid block should be rejected");
        helper.assertTrue(FluidBlockUtil.resolve("not a resource location") == null,
                "a malformed id should be rejected instead of throwing");
        helper.succeed();
    }
}
