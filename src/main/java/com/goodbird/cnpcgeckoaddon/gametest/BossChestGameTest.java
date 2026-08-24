package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossChestScheduler;
import com.goodbird.cnpcgeckoaddon.data.BossLootEntry;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.ContainerBlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Covers the part of the loot chest that touches the world, because getting it wrong means
 * damaging somebody's save: the chest has to land somewhere sensible, and it has to give
 * the terrain back when its time is up.
 */
@GameTestHolder(CNPCGeckoAddon.MODID)
public class BossChestGameTest {

    private static final int LIFETIME_TICKS = 40;
    /** Enough for the level tick to have run the scheduler. */
    private static final int SPAWN_DELAY = 5;

    @GameTest(template = "fluid_platform", timeoutTicks = 400)
    public static void chestIsFilledAndThenTakenAway(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockState before = helper.getLevel().getBlockState(helper.absolutePos(relative));

        TeleportPathData data = bossWithChest();
        BossLootEntry entry = data.getChestLoot().get(0);
        entry.setStack(new ItemStack(Items.DIAMOND));
        entry.setCountRange(3, 3);
        // A table nobody defined must not take the server down with it.
        data.setChestLootTable("cnpcgeckoaddon:no_such_table");

        schedule(helper, relative, data);

        helper.runAfterDelay(SPAWN_DELAY, () -> {
            helper.assertBlockPresent(Blocks.CHEST, relative);
            Container container = containerAt(helper, relative);
            helper.assertTrue(countOf(container, Items.DIAMOND) == 3,
                    "the chest should hold the three diamonds the list asked for");
            helper.assertTrue("Test Hoard".equals(nameOf(helper, relative)),
                    "the chest should carry the configured name");
        });

        helper.runAfterDelay(SPAWN_DELAY + LIFETIME_TICKS + 20, () -> {
            helper.assertBlockPresent(before.getBlock(), relative);
            helper.succeed();
        });
    }

    /** A block somebody built must never be swallowed by a chest. */
    @GameTest(template = "fluid_platform", timeoutTicks = 400)
    public static void chestStepsAsideForABlockThatIsAlreadyThere(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.OAK_PLANKS);

        TeleportPathData data = bossWithChest();
        data.getChestLoot().get(0).setStack(new ItemStack(Items.GOLD_INGOT));
        schedule(helper, relative, data);

        helper.runAfterDelay(SPAWN_DELAY, () -> {
            helper.assertBlockPresent(Blocks.OAK_PLANKS, relative);
            BlockPos found = findChest(helper, relative);
            helper.assertTrue(found != null, "the chest should have moved to a free block nearby");
            helper.assertTrue(countOf(containerAt(helper, found), Items.GOLD_INGOT) == 1,
                    "the chest that moved should still hold its loot");
            helper.succeed();
        });
    }

    /**
     * The round trip an item takes every time the boss is saved. Components only survive it
     * while the registries can be reached from whichever side is serializing.
     */
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void configuredItemsSurviveBeingSaved(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Boss Blade"));

        TeleportPathData data = bossWithChest();
        data.getChestLoot().get(4).setStack(sword);
        data.getChestLoot().get(4).setCountRange(2, 5);
        data.getChestLoot().get(4).setChancePercent(50);

        TeleportPathData reloaded = new TeleportPathData();
        reloaded.readFromNBT(data.writeToNBT(new CompoundTag()));

        BossLootEntry entry = reloaded.getChestLoot().get(4);
        helper.assertTrue(entry.getStack().is(Items.DIAMOND_SWORD), "the item should have come back");
        Component name = entry.getStack().get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(name != null && "Boss Blade".equals(name.getString()),
                "the custom name should have come back with it");
        helper.assertTrue(entry.getMinCount() == 2 && entry.getMaxCount() == 5 && entry.getChancePercent() == 50,
                "the counts and the chance should have come back too");
        helper.assertTrue(reloaded.isChestEnabled() && reloaded.getChestLifetimeTicks() == LIFETIME_TICKS,
                "the chest settings should have come back too");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void onlyBlocksThatHoldItemsAreAccepted(GameTestHelper helper) {
        helper.assertTrue(ContainerBlockUtil.resolve("minecraft:barrel") != null,
                "a barrel should count as a chest block");
        helper.assertTrue(ContainerBlockUtil.resolve("minecraft:stone") == null,
                "a block that holds nothing should be rejected");
        helper.assertTrue(ContainerBlockUtil.resolve("not a resource location") == null,
                "a malformed id should be rejected instead of throwing");
        helper.assertTrue(ContainerBlockUtil.resolveOrDefault("minecraft:stone") == Blocks.CHEST,
                "a rejected id should fall back to a plain chest");
        helper.succeed();
    }

    private static TeleportPathData bossWithChest() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.setChestEnabled(true);
        data.setChestDelayTicks(0);
        data.setChestLifetimeTicks(LIFETIME_TICKS);
        data.setChestName("Test Hoard");
        return data;
    }

    /**
     * Stands in for the boss. The scheduler only ever reads a position, a facing and a name
     * off the entity it is handed, so anything that has died in the right spot will do.
     */
    private static void schedule(GameTestHelper helper, BlockPos relative, TeleportPathData data) {
        Entity boss = helper.spawn(EntityType.ARMOR_STAND, relative);
        BossChestScheduler.schedule(helper.getLevel(), boss, data, null);
        boss.discard();
    }

    private static Container containerAt(GameTestHelper helper, BlockPos relative) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(blockEntity instanceof Container, "there should be a container at " + relative);
        return (Container) blockEntity;
    }

    private static String nameOf(GameTestHelper helper, BlockPos relative) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        return blockEntity instanceof net.minecraft.world.Nameable nameable
                ? nameable.getDisplayName().getString() : "";
    }

    private static int countOf(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static BlockPos findChest(GameTestHelper helper, BlockPos around) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = around.offset(dx, dy, dz);
                    if (helper.getLevel().getBlockState(helper.absolutePos(pos)).is(Blocks.CHEST)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
