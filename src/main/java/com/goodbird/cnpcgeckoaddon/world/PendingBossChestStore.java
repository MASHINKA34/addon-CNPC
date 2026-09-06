package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PendingBossChestStore extends SavedData {
    private static final String NAME = "cnpcgeckoaddon_pending_boss_chests";
    private static final Factory<PendingBossChestStore> FACTORY =
            new Factory<>(PendingBossChestStore::new, PendingBossChestStore::load);

    public record Pending(UUID bossId, BlockPos deathPos, BlockPos origin, boolean exact,
                          Direction facing, long spawnAt, String blockId, String styleId,
                          String lootTableId, Component name, int lifetimeTicks, List<ItemStack> items) {
    }

    private final List<Pending> entries = new ArrayList<>();
    private final TickQueue<Pending> queue = new TickQueue<>("boss loot chests", 16);

    public static PendingBossChestStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void add(Pending pending) {
        entries.add(pending);
        queue.add(pending);
        setDirty();
    }

    public boolean takeDrops(UUID bossId, List<ItemStack> drops) {
        Pending pending = queue.find(entry -> entry.bossId().equals(bossId));
        if (pending == null) {
            return false;
        }
        pending.items().addAll(drops);
        setDirty();
        return true;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void drain(Predicate<Pending> ready, Consumer<Pending> action) {
        queue.drain(ready, pending -> {
            action.accept(pending);
            entries.remove(pending);
            setDirty();
        });
    }

    public static PendingBossChestStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingBossChestStore store = new PendingBossChestStore();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos deathPos = NbtUtils.readBlockPos(entry, "DeathPos").orElse(null);
            BlockPos origin = NbtUtils.readBlockPos(entry, "Origin").orElse(null);
            if (deathPos == null || origin == null || !entry.hasUUID("Boss")) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            ListTag stacks = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < stacks.size(); j++) {
                ItemStack stack = ItemStack.parseOptional(registries, stacks.getCompound(j));
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            Component name = Component.Serializer.fromJson(entry.getString("Name"), registries);
            Pending pending = new Pending(entry.getUUID("Boss"), deathPos, origin,
                    entry.getBoolean("Exact"), Direction.from3DDataValue(entry.getByte("Facing")),
                    entry.getLong("SpawnAt"), entry.getString("Block"), entry.getString("Style"),
                    entry.getString("LootTable"), name == null ? Component.empty() : name,
                    entry.getInt("Lifetime"), items);
            store.entries.add(pending);
            store.queue.add(pending);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Pending pending : entries) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Boss", pending.bossId());
            entry.put("DeathPos", NbtUtils.writeBlockPos(pending.deathPos()));
            entry.put("Origin", NbtUtils.writeBlockPos(pending.origin()));
            entry.putBoolean("Exact", pending.exact());
            entry.putByte("Facing", (byte) pending.facing().get3DDataValue());
            entry.putLong("SpawnAt", pending.spawnAt());
            entry.putString("Block", pending.blockId());
            entry.putString("Style", pending.styleId());
            entry.putString("LootTable", pending.lootTableId());
            entry.putString("Name", Component.Serializer.toJson(pending.name(), registries));
            entry.putInt("Lifetime", pending.lifetimeTicks());
            ListTag items = new ListTag();
            for (ItemStack stack : pending.items()) {
                if (!stack.isEmpty()) {
                    items.add(stack.save(registries));
                }
            }
            entry.put("Items", items);
            list.add(entry);
        }
        tag.put("Entries", list);
        return tag;
    }
}
