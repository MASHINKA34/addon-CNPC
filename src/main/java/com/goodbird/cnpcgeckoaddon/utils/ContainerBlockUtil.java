package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves the block id a boss loot chest is made of into a block that can hold items. */
public final class ContainerBlockUtil {
    /** What a boss falls back on when its configured block is missing or holds nothing. */
    public static final String DEFAULT_ID = "minecraft:chest";

    private static List<String> selectableIds;

    private ContainerBlockUtil() {
    }

    /**
     * @return the block behind {@code id}, or null when the id is unknown or the block has no
     *         container block entity - a chest made of stone would swallow the whole drop
     */
    public static Block resolve(String id) {
        ResourceLocation location = AnimationFileUtil.parse(id);
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        return block != null && isContainer(block) ? block : null;
    }

    /** Never null: the configured block when it works out, plain chest when it does not. */
    public static Block resolveOrDefault(String id) {
        Block block = resolve(id);
        return block == null ? Blocks.CHEST : block;
    }

    public static boolean isContainerBlock(String id) {
        return resolve(id) != null;
    }

    /** Every registered block that stores items, for the selection GUI. */
    public static List<String> getSelectableIds() {
        if (selectableIds != null) {
            return selectableIds;
        }
        List<String> ids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!isContainer(block)) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key != null) {
                ids.add(key.toString());
            }
        }
        Collections.sort(ids);
        // Building this means asking every block in the game for a block entity, which is
        // more than a menu should do twice.
        selectableIds = ids;
        return ids;
    }

    private static boolean isContainer(Block block) {
        if (!(block instanceof EntityBlock entityBlock)) {
            return false;
        }
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            return blockEntity instanceof Container;
        } catch (Throwable ignored) {
            // A modded block is free to assume it is only ever built in a real world.
            return false;
        }
    }
}
