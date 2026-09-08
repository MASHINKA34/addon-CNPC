package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.blocks.tiles.TileScripted;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TileScripted.class)
public abstract class TileScriptedMixin extends BlockEntity {

    @Shadow(remap = false)
    public BlockEntity renderTile;

    public TileScriptedMixin(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
        super(p_155228_, p_155229_, p_155230_);
    }


    @WrapMethod(method = "setDisplayNBT", remap = false)
    private void cnpcgeckoaddon$updateDisplay(CompoundTag compound, HolderLookup.Provider registries,
                                            Operation<Void> original) {
        TileEntityCustomModel previous = renderTile instanceof TileEntityCustomModel model ? model : null;
        original.call(compound, registries);
        if (compound.contains("renderTileTag", Tag.TAG_COMPOUND)) {
            TileEntityCustomModel model = previous == null ? new TileEntityCustomModel(this) : previous;
            model.setLevel(getLevel());
            model.loadAdditional(compound.getCompound("renderTileTag"), registries);
            renderTile = model;
        }
    }

    @Inject(method = "getDisplayNBT", at = @At("TAIL"), remap = false)
    public void getDisplayNBT(CompoundTag compound, HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (renderTile instanceof TileEntityCustomModel model) {
            CompoundTag saveTag = new CompoundTag();
            model.saveAdditional(saveTag, registries);
            compound.put("renderTileTag", saveTag);
        }
    }
}
