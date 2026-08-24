package com.goodbird.cnpcgeckoaddon.block;

import com.goodbird.cnpcgeckoaddon.data.BossChestStyles;
import com.goodbird.cnpcgeckoaddon.registry.TileEntityRegistry;
import com.goodbird.cnpcgeckoaddon.tile.BossChestBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;

/**
 * The chest a boss leaves behind when it is set to a skin of its own.
 *
 * <p>Everything that makes a chest a chest - the lid animation, the opening sound, the
 * container and spilling its contents when a player breaks it - comes from
 * {@link ChestBlock}. All this adds is which skin the block is wearing, and it is a block
 * state rather than block entity data so the look saves itself and reaches the client
 * without a packet of its own.</p>
 */
public class BossChestBlock extends ChestBlock {
    public static final EnumProperty<BossChestStyles.Skin> STYLE =
            EnumProperty.create("style", BossChestStyles.Skin.class);

    public static final MapCodec<BossChestBlock> CODEC = simpleCodec(BossChestBlock::new);

    public BossChestBlock(Properties properties) {
        super(properties, () -> TileEntityRegistry.bossChest);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TYPE, ChestType.SINGLE)
                .setValue(WATERLOGGED, Boolean.FALSE)
                .setValue(STYLE, BossChestStyles.Skin.MOSS_CAVE));
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STYLE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BossChestBlockEntity(pos, state);
    }

    /**
     * Never pairs up into a double chest.
     *
     * <p>Two bosses can easily die next to each other, and a pair that merged would share
     * one inventory and one lifetime - so the only thing left of the vanilla behaviour here
     * is keeping the waterlogging ticking.</p>
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }
}
