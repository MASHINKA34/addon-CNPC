package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomBlocks;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BlockAnimationGameTest {
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void scriptedBlocksSupportModelBlockEntities(GameTestHelper helper) {
        TileEntityCustomModel tile = new TileEntityCustomModel(BlockPos.ZERO, CustomBlocks.scripted.defaultBlockState());
        TileEntityCustomModel door = new TileEntityCustomModel(BlockPos.ZERO, CustomBlocks.scripted_door.defaultBlockState());
        helper.assertTrue(tile.getType().isValid(tile.getBlockState()),
                "a scripted block must support its model block entity");
        helper.assertTrue(door.getType().isValid(door.getBlockState()),
                "a scripted door must support its model block entity");
        helper.succeed();
    }
}
