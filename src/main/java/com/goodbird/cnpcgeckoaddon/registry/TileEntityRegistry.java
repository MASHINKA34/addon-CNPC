package com.goodbird.cnpcgeckoaddon.registry;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.tile.BossChestBlockEntity;
import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import noppes.npcs.CustomBlocks;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class TileEntityRegistry {

    public static BlockEntityType<? extends TileEntityCustomModel> tileEntityCustomModel;
    public static BlockEntityType<BossChestBlockEntity> bossChest;

    @SubscribeEvent
    public static void registerBlocks(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            tileEntityCustomModel = createTile("custommodeltileentity", TileEntityCustomModel::new,
                    CustomBlocks.scripted, CustomBlocks.scripted_door);
            helper.register(ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "custommodeltileentity"),
                    tileEntityCustomModel);
            // Blocks are handed out before block entities are, so the block this one is
            // bound to already exists by the time we get here.
            bossChest = createTile(BlockRegistry.BOSS_CHEST_NAME, BossChestBlockEntity::new, BlockRegistry.bossChest);
            helper.register(ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                    BlockRegistry.BOSS_CHEST_NAME), bossChest);
        });
    }

    private static <T extends BlockEntity> BlockEntityType<T> createTile(String key, BlockEntityType.BlockEntitySupplier<T> factoryIn, Block... blocks){
        BlockEntityType.Builder<T> builder = BlockEntityType.Builder.of(factoryIn, blocks);
        return builder.build(Util.fetchChoiceType(References.BLOCK_ENTITY, key));
    }
}
