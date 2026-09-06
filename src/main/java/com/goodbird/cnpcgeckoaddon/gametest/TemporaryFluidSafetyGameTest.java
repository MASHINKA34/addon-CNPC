package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class TemporaryFluidSafetyGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void temporaryLavaDoesNotTurnIntoObsidian(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(POS);
        helper.setBlock(POS.east(), Blocks.WATER);
        TemporaryFluidStore store = isolatedStore(helper);
        try {
            helper.assertTrue(store.place(helper.getLevel(), absolute, Blocks.LAVA.defaultBlockState(), 20),
                    "temporary lava must be placed next to water");
            helper.assertBlockPresent(Blocks.LAVA, POS);
            helper.assertFalse(FluidInteractionRegistry.canInteract(helper.getLevel(), absolute),
                    "temporary lava must not react with water");
        } finally {
            store.restoreAll(helper.getLevel());
        }
        helper.assertBlockPresent(Blocks.AIR, POS);
        helper.assertBlockPresent(Blocks.WATER, POS.east());
        helper.setBlock(POS, Blocks.LAVA);
        helper.assertBlockPresent(Blocks.OBSIDIAN, POS);
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void temporaryWaterDoesNotHardenNeighboringLava(GameTestHelper helper) {
        TemporaryFluidStore store = isolatedStore(helper);
        try {
            helper.assertTrue(store.place(helper.getLevel(), helper.absolutePos(POS),
                    Blocks.WATER.defaultBlockState(), 20), "temporary water must be placed");
            helper.setBlock(POS.east(), Blocks.LAVA);
            helper.assertBlockPresent(Blocks.LAVA, POS.east());
            helper.assertFalse(FluidInteractionRegistry.canInteract(helper.getLevel(), helper.absolutePos(POS.east())),
                    "natural lava must not react with temporary water");
        } finally {
            store.restoreAll(helper.getLevel());
        }
        helper.assertBlockPresent(Blocks.LAVA, POS.east());
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void temporaryLavaCannotIgniteWood(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        helper.setBlock(POS, Blocks.AIR);
        TemporaryFluidStore store = isolatedStore(helper);
        GameRules.BooleanValue fireTick = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK);
        boolean previous = fireTick.get();
        try {
            fireTick.set(true, helper.getLevel().getServer());
            helper.assertTrue(store.place(helper.getLevel(), helper.absolutePos(POS),
                    Blocks.LAVA.defaultBlockState(), 20), "temporary lava must be placed");
            RandomSource random = RandomSource.create(42);
            for (int i = 0; i < 128; i++) {
                helper.getLevel().getFluidState(helper.absolutePos(POS))
                        .randomTick(helper.getLevel(), helper.absolutePos(POS), random);
            }
            for (int x = 0; x <= 4; x++) {
                for (int y = 1; y <= 4; y++) {
                    for (int z = 0; z <= 4; z++) {
                        helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, y, z));
                    }
                }
            }
        } finally {
            fireTick.set(previous, helper.getLevel().getServer());
            store.restoreAll(helper.getLevel());
        }
        helper.succeed();
    }

    private static TemporaryFluidStore isolatedStore(GameTestHelper helper) {
        return TemporaryFluidStore.load(new CompoundTag(), helper.getLevel().registryAccess());
    }
}
