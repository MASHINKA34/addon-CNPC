package com.goodbird.cnpcgeckoaddon.registry;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.block.BossChestBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class BlockRegistry {

    public static final String BOSS_CHEST_NAME = "boss_chest";

    public static BossChestBlock bossChest;

    @SubscribeEvent
    public static void registerBlocks(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            // No item form and no loot table: this block is only ever put down by a dying
            // boss, and breaking it should give back its contents, not a chest to keep.
            bossChest = new BossChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noLootTable());
            helper.register(ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, BOSS_CHEST_NAME), bossChest);
        });
    }
}
