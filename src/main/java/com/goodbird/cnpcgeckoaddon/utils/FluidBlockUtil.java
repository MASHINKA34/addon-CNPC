package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves the block id a boss spits into a placeable fluid block state. */
public final class FluidBlockUtil {

    private FluidBlockUtil() {
    }

    /**
     * @return the source state of the fluid block behind {@code id}, or null when the id is
     *         unknown or does not describe a fluid (which keeps a typo from placing stone)
     */
    public static BlockState resolve(String id) {
        ResourceLocation location = AnimationFileUtil.parse(id);
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        return state.getFluidState().isEmpty() ? null : state;
    }

    public static boolean isFluidBlock(String id) {
        return resolve(id) != null;
    }

    /** Every registered block that is a fluid, for the selection GUI. */
    public static List<String> getSelectableIds() {
        List<String> ids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().getFluidState().isEmpty()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key != null) {
                ids.add(key.toString());
            }
        }
        Collections.sort(ids);
        return ids;
    }
}
