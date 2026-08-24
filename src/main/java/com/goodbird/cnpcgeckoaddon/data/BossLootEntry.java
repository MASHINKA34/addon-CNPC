package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.utils.ItemStackNbtUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** One slot of a boss loot chest: what goes in, how much of it, and how often. */
public final class BossLootEntry {
    private static final String ITEM_KEY = "Item";
    private static final String MIN_KEY = "Min";
    private static final String MAX_KEY = "Max";
    private static final String CHANCE_KEY = "Chance";

    private ItemStack stack = ItemStack.EMPTY;
    /**
     * The tag this stack was read from. Kept so a round trip through a side that cannot
     * reach the registries writes the item back untouched instead of erasing it.
     */
    private CompoundTag rawItem = new CompoundTag();
    private int minCount = 1;
    private int maxCount = 1;
    private int chancePercent = 100;

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put(ITEM_KEY, ItemStackNbtUtil.save(stack, rawItem));
        tag.putInt(MIN_KEY, minCount);
        tag.putInt(MAX_KEY, maxCount);
        tag.putInt(CHANCE_KEY, chancePercent);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        rawItem = tag.getCompound(ITEM_KEY).copy();
        stack = ItemStackNbtUtil.load(rawItem);
        setCountRange(tag.contains(MIN_KEY) ? tag.getInt(MIN_KEY) : 1,
                tag.contains(MAX_KEY) ? tag.getInt(MAX_KEY) : 1);
        chancePercent = tag.contains(CHANCE_KEY) ? Mth.clamp(tag.getInt(CHANCE_KEY), 1, 100) : 100;
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /**
     * Rolls this slot once.
     *
     * @return the stack to put in the chest, or an empty one when the chance did not come up
     */
    public ItemStack roll(RandomSource random) {
        if (stack.isEmpty() || random.nextInt(100) >= chancePercent) {
            return ItemStack.EMPTY;
        }
        int count = maxCount > minCount ? minCount + random.nextInt(maxCount - minCount + 1) : minCount;
        return stack.copyWithCount(count);
    }

    /** The configured stack itself, components and all - never modify what comes back. */
    public ItemStack getStack() { return stack; }

    public void setStack(ItemStack value) {
        stack = value == null ? ItemStack.EMPTY : value.copy();
        // Keep the fallback in step, so a save without registries still writes this item.
        rawItem = ItemStackNbtUtil.save(stack, new CompoundTag());
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        rawItem = new CompoundTag();
        minCount = 1;
        maxCount = 1;
        chancePercent = 100;
    }

    public int getMinCount() { return minCount; }
    public int getMaxCount() { return maxCount; }

    /** Keeps the two ends in order, so a typo cannot turn the range inside out. */
    public void setCountRange(int min, int max) {
        min = Mth.clamp(min, 1, 64);
        max = Mth.clamp(max, 1, 64);
        minCount = Math.min(min, max);
        maxCount = Math.max(min, max);
    }

    public void setMinCount(int value) { setCountRange(value, maxCount); }
    public void setMaxCount(int value) { setCountRange(minCount, value); }

    public int getChancePercent() { return chancePercent; }
    public void setChancePercent(int value) { chancePercent = Mth.clamp(value, 1, 100); }
}
