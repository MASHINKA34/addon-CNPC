package com.goodbird.cnpcgeckoaddon.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Places fluid blocks that vanish again after a fixed time and leave the world exactly
 * as they found it.
 *
 * <p>Two things make that safe. The original {@link BlockState} of every replaced position
 * is recorded and put back on expiry, and every placed position is registered as "frozen"
 * so {@code MixinFlowingFluid} cancels its fluid tick - otherwise the fluid would spread
 * and the spread blocks would outlive the ones we know about. The pending entries live in
 * level data, so a server that is killed mid-effect still restores the terrain on the next
 * load instead of leaving the fluid behind forever.</p>
 */
public class TemporaryFluidStore extends SavedData {
    private static final String NAME = "cnpcgeckoaddon_temporary_fluids";
    private static final String ENTRIES_KEY = "Entries";
    private static final String POS_KEY = "Pos";
    private static final String STATE_KEY = "State";
    private static final String PLACED_KEY = "Placed";
    /**
     * The same two states again, stored by block name instead of by numeric id. The numeric
     * ids shift the moment the modpack gains or loses a mod, and these entries are restored
     * on the load right after a restart - which is exactly when the ids may have moved, and
     * a shifted id would put a different block back than the one the fluid covered. The old
     * int keys are still written and read so a downgrade keeps working.
     */
    private static final String STATE_TAG_KEY = "StateTag";
    private static final String PLACED_TAG_KEY = "PlacedTag";
    private static final String EXPIRES_KEY = "Expires";

    /**
     * Mirror of every frozen position, keyed by dimension. The fluid tick mixin runs for
     * every scheduled fluid update in the world, so it needs a lookup that does not touch
     * the level's data storage. Only ever accessed from the server thread.
     */
    private static final Map<ResourceKey<Level>, LongSet> FROZEN = new HashMap<>();

    private final List<Entry> entries = new ArrayList<>();
    private ResourceKey<Level> dimension;

    private record Entry(BlockPos pos, BlockState original, BlockState placed, long expiresAt) {
    }

    /**
     * Client-only update. Neighbour updates would let a puddle knock torches off walls,
     * drop sand and retrigger redstone, none of which the restore could undo.
     */
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS;

    private static final SavedData.Factory<TemporaryFluidStore> FACTORY =
            new SavedData.Factory<>(TemporaryFluidStore::new, TemporaryFluidStore::load);

    public static TemporaryFluidStore get(ServerLevel level) {
        TemporaryFluidStore store = level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        store.dimension = level.dimension();
        return store;
    }

    /**
     * True while any dimension holds a temporary fluid. Lets the per-level tick and the
     * fluid mixin skip all further work in the overwhelmingly common case of no boss
     * having spit anything.
     */
    public static boolean hasAnyPending() {
        return !FROZEN.isEmpty();
    }

    /** True when the fluid at this position was placed by the addon and must not spread. */
    public static boolean isFrozen(Level level, BlockPos pos) {
        if (FROZEN.isEmpty()) {
            return false;
        }
        LongSet positions = FROZEN.get(level.dimension());
        return positions != null && positions.contains(pos.asLong());
    }

    private static void freeze(ResourceKey<Level> dimension, BlockPos pos) {
        FROZEN.computeIfAbsent(dimension, key -> new LongOpenHashSet()).add(pos.asLong());
    }

    private static void unfreeze(ResourceKey<Level> dimension, BlockPos pos) {
        LongSet positions = FROZEN.get(dimension);
        if (positions != null && positions.remove(pos.asLong()) && positions.isEmpty()) {
            // Dropping the empty set keeps hasAnyPending() an exact answer.
            FROZEN.remove(dimension);
        }
    }

    private TemporaryFluidStore() {
    }

    /** Public only so the game tests can feed it a saved tag; the factory is the real caller. */
    public static TemporaryFluidStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporaryFluidStore store = new TemporaryFluidStore();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(entry, POS_KEY).orElse(null);
            if (pos == null) {
                continue;
            }
            store.entries.add(new Entry(pos,
                    readState(blocks, entry, STATE_TAG_KEY, STATE_KEY),
                    readState(blocks, entry, PLACED_TAG_KEY, PLACED_KEY),
                    entry.getLong(EXPIRES_KEY)));
        }
        return store;
    }

    /** The name-based state when this session wrote one, the numeric id of an older save otherwise. */
    private static BlockState readState(HolderGetter<Block> blocks, CompoundTag entry,
                                        String tagKey, String idKey) {
        if (entry.contains(tagKey, Tag.TAG_COMPOUND)) {
            return NbtUtils.readBlockState(blocks, entry.getCompound(tagKey));
        }
        return Block.stateById(entry.getInt(idKey));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag saved = new CompoundTag();
            saved.put(POS_KEY, NbtUtils.writeBlockPos(entry.pos()));
            saved.putInt(STATE_KEY, Block.getId(entry.original()));
            saved.putInt(PLACED_KEY, Block.getId(entry.placed()));
            saved.put(STATE_TAG_KEY, NbtUtils.writeBlockState(entry.original()));
            saved.put(PLACED_TAG_KEY, NbtUtils.writeBlockState(entry.placed()));
            saved.putLong(EXPIRES_KEY, entry.expiresAt());
            list.add(saved);
        }
        tag.put(ENTRIES_KEY, list);
        return tag;
    }

    /**
     * Replaces the block at {@code pos} with {@code fluid} for {@code lifetimeTicks}.
     *
     * @return true when the fluid was actually placed
     */
    public boolean place(ServerLevel level, BlockPos pos, BlockState fluid, int lifetimeTicks) {
        BlockPos immutable = pos.immutable();
        if (!level.isLoaded(immutable) || !level.getWorldBorder().isWithinBounds(immutable)) {
            return false;
        }
        BlockState current = level.getBlockState(immutable);
        if (current.equals(fluid) || !isReplaceable(level, immutable, current) || findEntry(immutable) != null) {
            return false;
        }

        freeze(level.dimension(), immutable);
        if (!level.setBlock(immutable, fluid, UPDATE_FLAGS)) {
            unfreeze(level.dimension(), immutable);
            return false;
        }
        entries.add(new Entry(immutable, current, fluid, level.getGameTime() + Math.max(lifetimeTicks, 1)));
        setDirty();
        return true;
    }

    /** Only air, plants, snow layers and other fluids - never something a player built. */
    private static boolean isReplaceable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.hasBlockEntity()
                && (state.isAir() || state.canBeReplaced())
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private Entry findEntry(BlockPos pos) {
        for (Entry entry : entries) {
            if (entry.pos().equals(pos)) {
                return entry;
            }
        }
        return null;
    }

    /** Restores every entry whose lifetime has run out. */
    public void tick(ServerLevel level) {
        if (entries.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        for (Entry entry : takeOut(level, expired -> gameTime >= expired.expiresAt())) {
            restore(level, entry);
        }
    }

    /**
     * Puts every reachable block back, regardless of its remaining lifetime. Positions in
     * chunks that are already gone are kept in the level data instead of being force-loaded
     * during shutdown - the next load expires and restores them.
     */
    public void restoreAll(ServerLevel level) {
        if (entries.isEmpty()) {
            return;
        }
        for (Entry entry : takeOut(level, entry -> true)) {
            restore(level, entry);
        }
    }

    /**
     * Takes the entries the filter accepts, and whose chunk is loaded, out of the list.
     *
     * <p>Collected before anything is put back rather than as it goes: restoring a block
     * writes to the world and wakes its neighbours up, and none of that may happen while the
     * list it could grow is being walked.</p>
     */
    private List<Entry> takeOut(ServerLevel level, Predicate<Entry> filter) {
        List<Entry> taken = new ArrayList<>();
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            // An unloaded chunk is kept for later so the fluid is never silently forgotten.
            if (!level.isLoaded(entry.pos()) || !filter.test(entry)) {
                continue;
            }
            iterator.remove();
            taken.add(entry);
        }
        if (!taken.isEmpty()) {
            setDirty();
        }
        return taken;
    }

    private void restore(ServerLevel level, Entry entry) {
        unfreeze(level.dimension(), entry.pos());
        BlockState current = level.getBlockState(entry.pos());
        // Only take the block back while it still is exactly the fluid we placed. Anything
        // else means a player or another mod changed it, and overwriting that would destroy
        // their work - a waterlogged block, for instance, also reports a fluid state.
        if (current.equals(entry.placed())) {
            level.setBlock(entry.pos(), entry.original(), UPDATE_FLAGS);
        }
    }

    /**
     * Rebuilds the frozen-position mirror after level data was read from disk and marks
     * everything as expired, so a restart cannot leave fluid behind.
     */
    public void onLevelLoaded(ServerLevel level) {
        dimension = level.dimension();
        for (Entry entry : entries) {
            freeze(level.dimension(), entry.pos());
        }
        entries.replaceAll(entry -> new Entry(entry.pos(), entry.original(), entry.placed(), 0L));
    }

    public void onLevelUnloaded() {
        if (dimension != null) {
            FROZEN.remove(dimension);
        }
    }

    /** Fallback used when a configured block id cannot be resolved to a fluid. */
    public static BlockState defaultFluid() {
        return Blocks.LAVA.defaultBlockState();
    }
}
