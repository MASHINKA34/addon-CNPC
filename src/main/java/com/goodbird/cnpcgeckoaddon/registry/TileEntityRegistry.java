package com.goodbird.cnpcgeckoaddon.registry;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.tile.BossChestBlockEntity;
import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = CNPCGeckoAddon.MODID)
public class TileEntityRegistry {

    public static BlockEntityType<? extends TileEntityCustomModel> tileEntityCustomModel;
    public static BlockEntityType<BossChestBlockEntity> bossChest;

    @SubscribeEvent
    public static void registerBlocks(RegisterEvent event) {
        if (event.getRegistry() == BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            tileEntityCustomModel = createTile("custommodeltileentity",TileEntityCustomModel::new);
            Registry.register((Registry<? super BlockEntityType<?>>) event.getRegistry(), CNPCGeckoAddon.MODID+":custommodeltileentity", tileEntityCustomModel);
            // Blocks are handed out before block entities are, so the block this one is
            // bound to already exists by the time we get here.
            bossChest = createTile(BlockRegistry.BOSS_CHEST_NAME, BossChestBlockEntity::new, BlockRegistry.bossChest);
            Registry.register((Registry<? super BlockEntityType<?>>) event.getRegistry(), CNPCGeckoAddon.MODID+":"+BlockRegistry.BOSS_CHEST_NAME, bossChest);
        }
    }

    private static <T extends BlockEntity> BlockEntityType<T> createTile(String key, BlockEntityType.BlockEntitySupplier<T> factoryIn, Block... blocks){
        BlockEntityType.Builder<T> builder = BlockEntityType.Builder.of(factoryIn, blocks);
        return builder.build(Util.fetchChoiceType(References.BLOCK_ENTITY, key));
    }
}
