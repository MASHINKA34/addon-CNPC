package com.goodbird.cnpcgeckoaddon.tile;

import com.goodbird.cnpcgeckoaddon.registry.TileEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Plain chest guts under a boss chest; only the name it falls back on differs. */
public class BossChestBlockEntity extends ChestBlockEntity {

    public BossChestBlockEntity(BlockPos pos, BlockState state) {
        super(TileEntityRegistry.bossChest, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        // The scheduler almost always sets a name of its own; this is what shows when a
        // boss was configured with neither a chest name nor a display name.
        return Component.translatable("block.cnpcgeckoaddon.boss_chest");
    }
}
