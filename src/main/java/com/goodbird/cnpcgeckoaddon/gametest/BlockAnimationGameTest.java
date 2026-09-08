package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomBlocks;
import noppes.npcs.blocks.tiles.TileScripted;
import software.bernie.geckolib.animation.RawAnimation;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BlockAnimationGameTest {
    @GameTest(template = "fluid_platform")
    public static void displayUpdatesPreserveRunningBlockAnimations(GameTestHelper helper) {
        TileScripted tile = new TileScripted(helper.absolutePos(BlockPos.ZERO), CustomBlocks.scripted.defaultBlockState());
        tile.setLevel(helper.getLevel());
        TileEntityCustomModel model = new TileEntityCustomModel(tile);
        tile.renderTile = model;
        RawAnimation running = RawAnimation.begin().thenLoop("test.running");
        model.manualAnim = running;
        var cache = model.getAnimatableInstanceCache();
        CompoundTag tag = tile.getDisplayNBT(new CompoundTag(), helper.getLevel().registryAccess());
        tag.putInt("LightValue", 12);
        tag.getCompound("renderTileTag").putString("textureResLoc", "cnpcgeckoaddon:textures/test.png");
        tile.setDisplayNBT(tag, helper.getLevel().registryAccess());
        helper.assertTrue(tile.renderTile == model && model.manualAnim == running
                        && model.getAnimatableInstanceCache() == cache,
                "display and texture updates must preserve the running animation and its controller cache");
        helper.assertTrue(tile.lightValue == 12 && model.textureResLoc.equals(
                        ResourceLocation.parse("cnpcgeckoaddon:textures/test.png")),
                "preserving animation must not prevent native or custom display updates");
        tag.getCompound("renderTileTag").putString("animResLoc", "cnpcgeckoaddon:animations/other.json");
        tile.setDisplayNBT(tag, helper.getLevel().registryAccess());
        helper.assertTrue(model.manualAnim == null && model.getAnimatableInstanceCache() != cache,
                "switching animation files must discard controllers from the previous file");
        tag.remove("renderTileTag");
        tile.setDisplayNBT(tag, helper.getLevel().registryAccess());
        helper.assertTrue(!(tile.renderTile instanceof TileEntityCustomModel),
                "switching to a native block must remove the custom model");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void displayReloadCreatesIndependentAnimationState(GameTestHelper helper) {
        TileScripted source = new TileScripted(BlockPos.ZERO, CustomBlocks.scripted.defaultBlockState());
        source.setLevel(helper.getLevel());
        TileEntityCustomModel model = new TileEntityCustomModel(source);
        source.renderTile = model;
        model.idleAnimName = "test.idle";
        model.manualAnim = RawAnimation.begin().thenPlay("test.manual");
        CompoundTag saved = source.getDisplayNBT(new CompoundTag(), helper.getLevel().registryAccess());
        TileScripted restored = new TileScripted(BlockPos.ZERO, CustomBlocks.scripted.defaultBlockState());
        restored.setLevel(helper.getLevel());
        restored.setDisplayNBT(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.renderTile instanceof TileEntityCustomModel,
                "saved custom models must load without an existing render tile");
        TileEntityCustomModel loaded = (TileEntityCustomModel) restored.renderTile;
        helper.assertTrue(loaded != model && loaded.manualAnim == null && loaded.idleAnimName.equals("test.idle"),
                "loading a separate block must restore its settings without sharing transient animation state");
        saved.putString("renderTileTag", "invalid");
        restored.setDisplayNBT(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!(restored.renderTile instanceof TileEntityCustomModel),
                "a malformed model tag must not enable a custom render tile");
        helper.succeed();
    }

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
