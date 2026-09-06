package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossChestScheduler;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.world.PendingBossChestStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossChestRewardGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void rewardsRespectItemStackLimits(GameTestHelper helper) {
        TeleportPathData data = settings(helper);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Boss Blade"));
        data.getChestLoot().get(0).setStack(sword);
        data.getChestLoot().get(0).setCountRange(2, 2);
        data.getChestLoot().get(1).setStack(new ItemStack(Items.ENDER_PEARL));
        data.getChestLoot().get(1).setCountRange(32, 32);
        schedule(helper, data);
        helper.runAfterDelay(5, () -> {
            Container chest = container(helper);
            helper.assertTrue(count(chest, Items.DIAMOND_SWORD) == 2, "both unstackable swords must be awarded");
            helper.assertTrue(count(chest, Items.ENDER_PEARL) == 32, "all pearls must survive splitting into stacks of 16");
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "every slot must contain a valid stack");
                if (stack.is(Items.DIAMOND_SWORD)) {
                    helper.assertTrue(sword.getHoverName().equals(stack.getHoverName()), "split rewards must retain components");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void overflowingRewardsAreDroppedWithoutLoss(GameTestHelper helper) {
        TeleportPathData data = settings(helper);
        data.getChestLoot().get(0).setStack(new ItemStack(Items.DIAMOND_SWORD));
        data.getChestLoot().get(0).setCountRange(64, 64);
        schedule(helper, data);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(count(container(helper), Items.DIAMOND_SWORD) == 27, "all available chest slots must be used");
            helper.assertTrue(droppedCount(helper, Items.DIAMOND_SWORD) == 37, "every sword that did not fit must be dropped");
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void restrictedContainerRejectsLootWithoutDestroyingIt(GameTestHelper helper) {
        TeleportPathData data = settings(helper);
        data.setChestBlock("minecraft:chiseled_bookshelf");
        data.getChestLoot().get(0).setStack(new ItemStack(Items.DIAMOND));
        data.getChestLoot().get(0).setCountRange(4, 4);
        data.getChestLoot().get(1).setStack(new ItemStack(Items.BOOK));
        data.getChestLoot().get(1).setCountRange(3, 3);
        schedule(helper, data);
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(Blocks.CHISELED_BOOKSHELF, POS);
            helper.assertTrue(count(container(helper), Items.BOOK) == 3, "accepted loot must still find the free slots");
            helper.assertTrue(droppedCount(helper, Items.DIAMOND) == 4, "rejected loot must be returned to the world");
            helper.succeed();
        });
    }

    @GameTest(template = "fluid_platform", batch = "chest_persistence", timeoutTicks = 100)
    public static void delayedRewardSurvivesSaveAndReload(GameTestHelper helper) {
        TeleportPathData data = settings(helper);
        data.setChestDelayTicks(12);
        data.setChestName("Saved Hoard");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Saved Blade"));
        data.getChestLoot().get(0).setStack(sword);
        data.getChestLoot().get(0).setCountRange(2, 2);
        UUID bossId = schedule(helper, data);
        BossChestScheduler.takeDrops(helper.getLevel(), bossId, List.of(new ItemStack(Items.EMERALD, 7)));
        PendingBossChestStore original = PendingBossChestStore.get(helper.getLevel());
        helper.assertTrue(original.isDirty(), "scheduling and captured drops must mark the data for saving");
        CompoundTag saved = original.save(new CompoundTag(), helper.getLevel().registryAccess());
        BossChestScheduler.clear(helper.getLevel());
        PendingBossChestStore restored = PendingBossChestStore.load(saved, helper.getLevel().registryAccess());
        helper.getLevel().getDataStorage().set("cnpcgeckoaddon_pending_boss_chests", restored);
        BossChestScheduler.tick(helper.getLevel());
        helper.assertBlockNotPresent(Blocks.CHEST, POS);
        helper.runAfterDelay(17, () -> {
            Container chest = container(helper);
            helper.assertTrue(count(chest, Items.DIAMOND_SWORD) == 2, "configured rewards must survive reloading");
            helper.assertTrue(count(chest, Items.EMERALD) == 7, "captured NPC drops must survive reloading");
            helper.assertTrue(((net.minecraft.world.Nameable) chest).getDisplayName().getString().equals("Saved Hoard"),
                    "the delayed chest must retain its name");
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(Items.DIAMOND_SWORD)) {
                    helper.assertTrue(stack.getHoverName().equals(sword.getHoverName()), "saved items must retain components");
                }
            }
            var remaining = restored.save(new CompoundTag(), helper.getLevel().registryAccess())
                    .getList("Entries", Tag.TAG_COMPOUND);
            helper.assertFalse(remaining.stream().map(tag -> (CompoundTag) tag)
                    .anyMatch(tag -> tag.getUUID("Boss").equals(bossId)), "delivered rewards must not return after another restart");
            helper.assertTrue(restored.isDirty(), "delivery must mark the saved queue as changed");
            helper.succeed();
        });
    }

    private static TeleportPathData settings(GameTestHelper helper) {
        TeleportPathData data = new TeleportPathData();
        data.setChestEnabled(true);
        data.setChestDelayTicks(0);
        data.setChestPlacement(TeleportPathData.CHEST_PLACEMENT_FIXED);
        BlockPos absolute = helper.absolutePos(POS);
        data.setChestFixedX(absolute.getX());
        data.setChestFixedY(absolute.getY());
        data.setChestFixedZ(absolute.getZ());
        return data;
    }

    private static UUID schedule(GameTestHelper helper, TeleportPathData data) {
        Entity boss = helper.spawn(EntityType.ARMOR_STAND, POS);
        BossChestScheduler.schedule(helper.getLevel(), boss, data, null, null);
        boss.discard();
        return boss.getUUID();
    }

    private static Container container(GameTestHelper helper) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        helper.assertTrue(blockEntity instanceof Container, "the reward container must be placed");
        return (Container) blockEntity;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                count += container.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static int droppedCount(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2))
                .stream().map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
