package com.goodbird.cnpcgeckoaddon.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Keeps track of the loot chests bosses leave behind and takes them away again when their
 * time is up, contents and all.
 *
 * <p>Built like {@code TemporaryFluidStore}: the original {@link BlockState} of the position
 * is recorded so the world ends up exactly as it was found, and the entries live in level
 * data so a chest survives its chunk being unloaded and the server being restarted. Unlike
 * the fluids these are not expired on load - a chest is supposed to outlive a restart, and
 * its lifetime is counted in game ticks, which stand still while the server is down.</p>
 */
public class BossChestStore extends SavedData {
    private static final String NAME = "cnpcgeckoaddon_boss_chests";
    private static final String ENTRIES_KEY = "Entries";
    private static final String POS_KEY = "Pos";
    private static final String STATE_KEY = "State";
    private static final String PLACED_KEY = "Placed";
    private static final String EXPIRES_KEY = "Expires";

    /**
     * Which dimensions currently hold a chest. Lets the per-level tick skip its lookup in
     * the overwhelmingly common case of nobody having killed a boss. Server thread only.
     */
    private static final Set<ResourceKey<Level>> ACTIVE = new HashSet<>();

    private final List<Entry> entries = new ArrayList<>();
    private ResourceKey<Level> dimension;

    private record Entry(BlockPos pos, BlockState original, BlockState placed, long expiresAt) {
    }

    private static final SavedData.Factory<BossChestStore> FACTORY =
            new SavedData.Factory<>(BossChestStore::new, BossChestStore::load);

    public static BossChestStore get(ServerLevel level) {
        BossChestStore store = level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        store.dimension = level.dimension();
        return store;
    }

    public static boolean hasAnyPending() {
        return !ACTIVE.isEmpty();
    }

    private BossChestStore() {
    }

    private static BossChestStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BossChestStore store = new BossChestStore();
        ListTag list = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(entry, POS_KEY).orElse(null);
            if (pos == null) {
                continue;
            }
            store.entries.add(new Entry(pos,
                    Block.stateById(entry.getInt(STATE_KEY)),
                    Block.stateById(entry.getInt(PLACED_KEY)),
                    entry.getLong(EXPIRES_KEY)));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag saved = new CompoundTag();
            saved.put(POS_KEY, NbtUtils.writeBlockPos(entry.pos()));
            saved.putInt(STATE_KEY, Block.getId(entry.original()));
            saved.putInt(PLACED_KEY, Block.getId(entry.placed()));
            saved.putLong(EXPIRES_KEY, entry.expiresAt());
            list.add(saved);
        }
        tag.put(ENTRIES_KEY, list);
        return tag;
    }

    /** Signs a freshly placed chest up for removal in {@code lifetimeTicks}. */
    public void register(ServerLevel level, BlockPos pos, BlockState original, BlockState placed,
                         int lifetimeTicks) {
        BlockPos immutable = pos.immutable();
        Entry previous = findEntry(immutable);
        if (previous != null) {
            // A chest standing where another one already stood: keep the state the first one
            // covered up, or the position would be restored to a chest and stay one forever.
            original = previous.original();
            entries.remove(previous);
        }
        entries.add(new Entry(immutable, original, placed,
                level.getGameTime() + Math.max(lifetimeTicks, 1)));
        setDirty();
        markActive();
    }

    private Entry findEntry(BlockPos pos) {
        for (Entry entry : entries) {
            if (entry.pos().equals(pos)) {
                return entry;
            }
        }
        return null;
    }

    /** Takes away every chest whose lifetime has run out. */
    public void tick(ServerLevel level) {
        if (entries.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        boolean changed = false;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            // An unloaded chunk keeps its entry, so the chest goes the moment it comes back
            // instead of being forgotten about.
            if (gameTime < entry.expiresAt() || !level.isLoaded(entry.pos())) {
                continue;
            }
            remove(level, entry);
            iterator.remove();
            changed = true;
        }
        if (changed) {
            closeOrphanedMenus(level);
            setDirty();
            markActive();
        }
    }

    private void remove(ServerLevel level, Entry entry) {
        BlockState current = level.getBlockState(entry.pos());
        // Somebody mined it, or the block is not ours any more. Dropping the entry is the
        // whole response: putting the old state back would destroy what they built there.
        if (!current.is(entry.placed().getBlock())) {
            return;
        }
        if (level.getBlockEntity(entry.pos()) instanceof Container container) {
            // Emptying it first is what makes the loot vanish with the chest - a container
            // that still holds items spills them on the floor as the block goes.
            container.clearContent();
        }
        level.setBlock(entry.pos(), entry.original(), Block.UPDATE_ALL);
    }

    /**
     * Boots anyone who was looking inside a chest that just disappeared. Vanilla notices on
     * the player's own next tick, but an open menu with nothing behind it should not last
     * even that long.
     */
    private static void closeOrphanedMenus(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.containerMenu != player.inventoryMenu && !player.containerMenu.stillValid(player)) {
                player.closeContainer();
            }
        }
    }

    /** Signs the dimension up for ticking after level data was read back from disk. */
    public void onLevelLoaded(ServerLevel level) {
        dimension = level.dimension();
        markActive();
    }

    public void onLevelUnloaded() {
        if (dimension != null) {
            ACTIVE.remove(dimension);
        }
    }

    private void markActive() {
        if (dimension == null) {
            return;
        }
        if (entries.isEmpty()) {
            ACTIVE.remove(dimension);
        } else {
            ACTIVE.add(dimension);
        }
    }
}
