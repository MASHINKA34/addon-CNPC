package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossChestScheduler;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.world.BossChestStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossChestSafetyGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void replacementChestKeepsItsItems(GameTestHelper helper) {
        registerChest(helper);
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, Blocks.CHEST);
        Container replacement = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        replacement.setItem(0, new ItemStack(Items.DIAMOND, 7));
        helper.runAfterDelay(30, () -> {
            helper.assertBlockPresent(Blocks.CHEST, POS);
            helper.assertTrue(replacement.getItem(0).is(Items.DIAMOND)
                    && replacement.getItem(0).getCount() == 7, "a replacement chest must keep its diamonds");
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void ownershipSurvivesBlockAndStoreSerialization(GameTestHelper helper) {
        registerChest(helper);
        BlockPos absolute = helper.absolutePos(POS);
        BlockEntity chest = helper.getLevel().getBlockEntity(absolute);
        CompoundTag blockTag = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        CompoundTag entry = savedEntry(helper);
        helper.assertTrue(entry.hasUUID("Id"), "ownership must be persisted with the timer");
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, Blocks.CHEST);
        helper.getLevel().getBlockEntity(absolute).loadWithComponents(blockTag, helper.getLevel().registryAccess());
        entry.putLong("Expires", 0L);
        BossChestStore.load(wrap(entry), helper.getLevel().registryAccess()).tick(helper.getLevel());
        helper.assertBlockPresent(Blocks.AIR, POS);
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void legacyTimerCannotClaimAnUnidentifiedChest(GameTestHelper helper) {
        registerChest(helper);
        CompoundTag entry = savedEntry(helper);
        entry.remove("Id");
        entry.putLong("Expires", 0L);
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, Blocks.CHEST);
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        chest.setItem(0, new ItemStack(Items.EMERALD, 4));
        BossChestStore.load(wrap(entry), helper.getLevel().registryAccess()).tick(helper.getLevel());
        helper.assertBlockPresent(Blocks.CHEST, POS);
        helper.assertTrue(chest.getItem(0).getCount() == 4, "a legacy timer must not erase unowned items");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void fixedPlacementPreservesBlockEntitiesAndDropsLoot(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.FURNACE);
        BlockEntity furnace = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        ((Container) furnace).setItem(0, new ItemStack(Items.DIAMOND, 7));
        scheduleFixed(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) == furnace,
                    "fixed placement must preserve the original block entity");
            helper.assertTrue(((Container) furnace).getItem(0).getCount() == 7,
                    "fixed placement must preserve the original inventory");
            boolean dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(POS)).inflate(2)).stream()
                    .anyMatch(item -> item.getItem().is(Items.GOLD_INGOT));
            helper.assertTrue(dropped, "the boss reward must drop when its chest cannot be placed");
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 200)
    public static void fixedPlacementStillRestoresOrdinaryBlocks(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.OAK_PLANKS);
        scheduleFixed(helper);
        helper.runAfterDelay(5, () -> helper.assertBlockPresent(Blocks.CHEST, POS));
        helper.runAfterDelay(TeleportPathData.MIN_CHEST_LIFETIME_TICKS + 15, () -> {
            helper.assertBlockPresent(Blocks.OAK_PLANKS, POS);
            helper.succeed();
        });
    }

    private static void registerChest(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        BossChestStore.get(helper.getLevel()).register(helper.getLevel(), helper.absolutePos(POS),
                Blocks.AIR.defaultBlockState(), helper.getLevel().getBlockState(helper.absolutePos(POS)), 20);
    }

    private static void scheduleFixed(GameTestHelper helper) {
        TeleportPathData data = new TeleportPathData();
        data.setChestEnabled(true);
        data.setChestDelayTicks(0);
        data.setChestLifetimeTicks(TeleportPathData.MIN_CHEST_LIFETIME_TICKS);
        data.setChestPlacement(TeleportPathData.CHEST_PLACEMENT_FIXED);
        BlockPos absolute = helper.absolutePos(POS);
        data.setChestFixed(absolute.getX(), absolute.getY(), absolute.getZ());
        data.getChestLoot().get(0).setStack(new ItemStack(Items.GOLD_INGOT));
        Entity boss = helper.spawn(EntityType.ARMOR_STAND, POS);
        BossChestScheduler.schedule(helper.getLevel(), boss, data, null, null);
        boss.discard();
    }

    private static CompoundTag savedEntry(GameTestHelper helper) {
        ListTag entries = BossChestStore.get(helper.getLevel())
                .save(new CompoundTag(), helper.getLevel().registryAccess()).getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (helper.absolutePos(POS).equals(NbtUtils.readBlockPos(entry, "Pos").orElse(null))) {
                return entry;
            }
        }
        throw new AssertionError("the chest timer must be saved");
    }

    private static CompoundTag wrap(CompoundTag entry) {
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("Entries", entries);
        return tag;
    }
}
