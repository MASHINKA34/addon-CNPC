package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Pins the on-disk format of the temporary fluid entries.
 *
 * <p>These entries are the one piece of state that is restored on the load right after a
 * restart - which is exactly when the numeric block ids may have shifted, because that is
 * when the modpack changes. The store therefore writes the states by name as well, and a
 * load has to prefer the name over the id, or a restart could repair the arena with the
 * wrong block entirely.</p>
 */
@GameTestHolder(CNPCGeckoAddon.MODID)
public class TemporaryFluidStoreFormatGameTest {

    /** The literal keys of the saved format, spelled out so a rename cannot go unnoticed. */
    private static final String ENTRIES_KEY = "Entries";
    private static final String POS_KEY = "Pos";
    private static final String STATE_KEY = "State";
    private static final String PLACED_KEY = "Placed";
    private static final String STATE_TAG_KEY = "StateTag";
    private static final String PLACED_TAG_KEY = "PlacedTag";
    private static final String EXPIRES_KEY = "Expires";

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void savedEntriesCarryStatesByNameAndById(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState before = level.getBlockState(absolute);

        TemporaryFluidStore store = TemporaryFluidStore.get(level);
        helper.assertTrue(store.place(level, absolute, Blocks.LAVA.defaultBlockState(), 60),
                "the temporary fluid should have been placed");
        CompoundTag saved = store.save(new CompoundTag(), level.registryAccess());
        // Put the world back right away, before lava has a chance to schedule anything.
        store.restoreAll(level);
        helper.assertBlockPresent(before.getBlock(), new BlockPos(2, 1, 2));

        CompoundTag entry = findEntry(helper, saved, absolute);
        helper.assertTrue(entry.contains(STATE_TAG_KEY, Tag.TAG_COMPOUND)
                        && entry.contains(PLACED_TAG_KEY, Tag.TAG_COMPOUND),
                "the entry should carry both states by name");
        helper.assertTrue(entry.contains(STATE_KEY, Tag.TAG_INT) && entry.contains(PLACED_KEY, Tag.TAG_INT),
                "the entry should still carry both states by id, for a downgrade");
        helper.assertTrue(NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                        entry.getCompound(PLACED_TAG_KEY)).is(Blocks.LAVA),
                "the placed state written by name should be the lava that was placed");
        helper.succeed();
    }

    /** A load prefers the state written by name over a numeric id that may have shifted. */
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void loadPrefersStatesByNameOverShiftedIds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.LAVA);

        // An entry as a save from this session would hold it, except that the numeric ids
        // point at entirely different blocks - the shift a modpack change produces.
        CompoundTag entry = new CompoundTag();
        entry.put(POS_KEY, NbtUtils.writeBlockPos(absolute));
        entry.putInt(STATE_KEY, Block.getId(Blocks.SPONGE.defaultBlockState()));
        entry.putInt(PLACED_KEY, Block.getId(Blocks.SPONGE.defaultBlockState()));
        entry.put(STATE_TAG_KEY, NbtUtils.writeBlockState(Blocks.OAK_PLANKS.defaultBlockState()));
        entry.put(PLACED_TAG_KEY, NbtUtils.writeBlockState(Blocks.LAVA.defaultBlockState()));
        entry.putLong(EXPIRES_KEY, 0L);

        TemporaryFluidStore.load(wrap(entry), level.registryAccess()).restoreAll(level);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, relative);
        helper.succeed();
    }

    /** An entry from a save older than the name-based keys still restores through its ids. */
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void loadStillReadsEntriesSavedById(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 3);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.LAVA);

        CompoundTag entry = new CompoundTag();
        entry.put(POS_KEY, NbtUtils.writeBlockPos(absolute));
        entry.putInt(STATE_KEY, Block.getId(Blocks.OAK_PLANKS.defaultBlockState()));
        entry.putInt(PLACED_KEY, Block.getId(Blocks.LAVA.defaultBlockState()));
        entry.putLong(EXPIRES_KEY, 0L);

        TemporaryFluidStore.load(wrap(entry), level.registryAccess()).restoreAll(level);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, relative);
        helper.succeed();
    }

    private static CompoundTag wrap(CompoundTag entry) {
        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put(ENTRIES_KEY, list);
        return tag;
    }

    private static CompoundTag findEntry(GameTestHelper helper, CompoundTag saved, BlockPos absolute) {
        ListTag list = saved.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (absolute.equals(NbtUtils.readBlockPos(entry, POS_KEY).orElse(null))) {
                return entry;
            }
        }
        helper.fail("the saved tag should hold the entry that was just placed");
        return new CompoundTag();
    }
}
