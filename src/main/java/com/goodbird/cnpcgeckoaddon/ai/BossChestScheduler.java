package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.block.BossChestBlock;
import com.goodbird.cnpcgeckoaddon.data.BossChestStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.registry.BlockRegistry;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import com.goodbird.cnpcgeckoaddon.utils.ContainerBlockUtil;
import com.goodbird.cnpcgeckoaddon.world.BossChestStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Puts a chest full of loot where a boss died, a configurable number of ticks afterwards.
 *
 * <p>Built the same way as {@link BossExplosionScheduler} and for the same reason: CustomNPCs
 * may discard the entity the moment it dies, so the position and the settings are snapshotted
 * at the death and the chest is placed from the level tick instead of from the boss.</p>
 *
 * <p>Nothing here is persisted either. A pending chest lives for a second or two, and a
 * server killed inside that window should not drop loot on an empty field on the next start.
 * The chest that did get placed is another matter - {@code BossChestStore} owns it from
 * there on.</p>
 */
public final class BossChestScheduler {

    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");

    /** How far around the death spot a replaceable block is looked for, horizontally. */
    private static final int SEARCH_RADIUS = 2;
    /** ...and vertically. */
    private static final int SEARCH_HEIGHT = 2;
    /** How far down a boss that died in mid-air looks for something to stand a chest on. */
    private static final int MAX_DROP_HEIGHT = 8;
    /**
     * A position with nothing underneath is only used when nothing better is in range, so
     * the penalty has to outweigh every distance the search can produce.
     */
    private static final double NO_SUPPORT_PENALTY = 1000.0D;
    /** How long drops wait for a chest to claim them before they are thrown away. */
    private static final int STAGED_DROPS_TIMEOUT = 100;

    /**
     * @param deathPos where the boss actually fell - kept apart from {@code origin} because
     *                 loot that cannot be put in a chest is dropped where it was earned
     * @param exact    place on {@code origin} and nowhere else, replacing whatever stands there
     */
    private record Pending(int bossId, ResourceKey<Level> dimension, BlockPos deathPos, BlockPos origin,
                           boolean exact, Direction facing, long spawnAt, String blockId, String styleId,
                           String lootTableId, Component name, int lifetimeTicks, List<ItemStack> items) {
    }

    private record StagedDrops(long stagedAt, List<ItemStack> items) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Drops handed over before the chest that wants them was scheduled. CustomNPCs empties
     * the npc's inventory onto the ground from inside its own death event, which runs before
     * the death event this addon listens to, so the items always turn up first.
     */
    private static final Map<Integer, StagedDrops> STAGED_DROPS = new HashMap<>();

    private static String reportedBrokenBlock = "";
    private static String reportedBrokenLootTable = "";

    private BossChestScheduler() {
    }

    public static void schedule(ServerLevel level, Entity boss, TeleportPathData data, Entity killer,
                                BlockPos arenaHome) {
        long delay = data.getChestDelayTicks();
        if (data.isExplosionEnabled()) {
            // A boss that blows up on top of its own chest takes the loot with it, so the
            // chest always waits until after the blast no matter how it was configured.
            delay = Math.max(delay, data.getExplosionDelayTicks() + 2L);
        }

        Component name = data.getChestName().isEmpty()
                ? boss.getDisplayName() : Component.literal(data.getChestName());
        Pending pending = new Pending(boss.getId(), level.dimension(), boss.blockPosition(),
                resolveOrigin(boss, data, arenaHome),
                data.getChestPlacement() == TeleportPathData.CHEST_PLACEMENT_FIXED,
                chestFacing(boss, killer), level.getGameTime() + delay, data.getChestBlock(),
                data.getChestStyle(), data.getChestLootTable(), name, data.getChestLifetimeTicks(),
                new ArrayList<>());

        StagedDrops staged = STAGED_DROPS.remove(boss.getId());
        if (staged != null) {
            pending.items().addAll(staged.items());
        }
        // Rolled here rather than at spawn time, so editing the boss while its corpse is
        // still warm cannot change what it just dropped.
        pending.items().addAll(data.getChestLoot().rollAll(level.getRandom()));
        PENDING.add(pending);
    }

    /**
     * Works out which spot this boss' chest belongs to.
     *
     * <p>The dimension is always the one the boss died in - a chest that could appear in
     * another world would be a way to lose loot behind a portal, and none of the four modes
     * is worth that.</p>
     */
    private static BlockPos resolveOrigin(Entity boss, TeleportPathData data, BlockPos arenaHome) {
        BlockPos death = boss.blockPosition();
        return switch (data.getChestPlacement()) {
            case TeleportPathData.CHEST_PLACEMENT_DEATH_OFFSET -> offset(death, data);
            // No controller, or a boss that died without ever engaging: there is no arena
            // to speak of, so the death spot is the only honest answer.
            case TeleportPathData.CHEST_PLACEMENT_ARENA -> arenaHome == null ? death : offset(arenaHome, data);
            case TeleportPathData.CHEST_PLACEMENT_FIXED ->
                    new BlockPos(data.getChestFixedX(), data.getChestFixedY(), data.getChestFixedZ());
            default -> death;
        };
    }

    private static BlockPos offset(BlockPos base, TeleportPathData data) {
        return base.offset(data.getChestOffsetX(), data.getChestOffsetY(), data.getChestOffsetZ());
    }

    /**
     * Hands the drops of a dead boss to the chest it is about to leave behind, whether that
     * chest has been scheduled yet or not.
     */
    public static void takeDrops(int bossId, List<ItemStack> drops, long gameTime) {
        if (drops.isEmpty()) {
            return;
        }
        for (Pending pending : PENDING) {
            if (pending.bossId() == bossId) {
                pending.items().addAll(drops);
                return;
            }
        }
        StagedDrops staged = STAGED_DROPS.computeIfAbsent(bossId,
                key -> new StagedDrops(gameTime, new ArrayList<>()));
        staged.items().addAll(drops);
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty() || !STAGED_DROPS.isEmpty();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        // Drops nobody came back for: the death was cancelled, or the chest was switched off
        // between the two events. Holding on to them would leak the items forever.
        STAGED_DROPS.values().removeIf(staged -> gameTime - staged.stagedAt() > STAGED_DROPS_TIMEOUT);

        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (!pending.dimension().equals(level.dimension()) || gameTime < pending.spawnAt()) {
                continue;
            }
            iterator.remove();
            place(level, pending);
        }
    }

    /** Drops anything still waiting in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension().equals(level.dimension()));
        // Drops belong to a boss in a world that is closing, and entity ids start over in
        // the next one - keeping them would hand somebody else's loot to another boss.
        STAGED_DROPS.clear();
    }

    private static void place(ServerLevel level, Pending pending) {
        // Fixed coordinates are taken at their word: whoever typed them in wants the chest
        // there and not two blocks to the side, and the store puts the covered block back
        // when the time is up anyway.
        BlockPos pos = pending.exact() ? pending.origin() : findPlacement(level, pending.origin());
        if (pos == null || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            LOGGER.warn("No room for a boss loot chest at {}: nothing there can hold a block",
                    pending.exact() ? pending.origin() : pending.deathPos());
            spill(level, pending);
            return;
        }

        BlockState previous = level.getBlockState(pos);
        BlockState placed = orient(level, chestState(pending), pos, pending.facing());
        if (!level.setBlock(pos, placed, Block.UPDATE_ALL)) {
            spill(level, pending);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            // The block claimed to be a container a moment ago. Since it is not making one
            // here, put the world back the way it was rather than leaving a decoy.
            level.setBlock(pos, previous, Block.UPDATE_ALL);
            spill(level, pending);
            return;
        }

        // The name goes on first: applying components copies the container component over
        // the slots, so naming a chest after filling it would empty it again.
        applyName(blockEntity, pending.name());

        List<ItemStack> items = new ArrayList<>(pending.items());
        items.addAll(rollLootTable(level, pending.lootTableId(), pos));
        List<ItemStack> leftovers = fill(container, items, level.getRandom());
        container.setChanged();
        BossChestStore.get(level).register(level, pos, previous, placed, pending.lifetimeTicks());

        if (!leftovers.isEmpty()) {
            LOGGER.warn("Boss loot chest at {} had no room for {} stacks, dropping them next to it",
                    pos, leftovers.size());
            for (ItemStack stack : leftovers) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    /**
     * Last resort for a chest that could not be put down: the loot goes on the ground where
     * the boss fell.
     *
     * <p>The npc drops in here were taken out of the world on the promise of a chest to put
     * them in. Deleting them because no chest could be built would quietly rob whoever won
     * the fight, which is worse than a pile of items in an awkward spot.</p>
     */
    private static void spill(ServerLevel level, Pending pending) {
        if (pending.items().isEmpty()) {
            return;
        }
        LOGGER.warn("Dropping {} stacks of boss loot at {} instead", pending.items().size(), pending.deathPos());
        for (ItemStack stack : pending.items()) {
            Block.popResource(level, pending.deathPos(), stack);
        }
    }

    /**
     * Picks where the chest goes: the death spot when it is free, otherwise the closest free
     * block around it, preferring one that has something underneath.
     *
     * @return null when the boss died somewhere nothing can be placed at all
     */
    private static BlockPos findPlacement(ServerLevel level, BlockPos deathPos) {
        BlockPos origin = descendToSupport(level, deathPos);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        // Bottom up, so two spots the same distance away settle for the lower one.
        for (int dy = -SEARCH_HEIGHT; dy <= SEARCH_HEIGHT; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!canPlace(level, pos)) {
                        continue;
                    }
                    double score = pos.distSqr(origin) + (hasSupport(level, pos) ? 0.0D : NO_SUPPORT_PENALTY);
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /** A boss killed in mid-air leaves its chest on the first floor below it. */
    private static BlockPos descendToSupport(ServerLevel level, BlockPos origin) {
        if (!canPlace(level, origin) || hasSupport(level, origin)) {
            return origin;
        }
        for (int i = 1; i <= MAX_DROP_HEIGHT; i++) {
            BlockPos pos = origin.below(i);
            if (!level.isInWorldBounds(pos) || !canPlace(level, pos)) {
                // Whatever is down here is solid enough to stand the chest on top of.
                return pos.above();
            }
            if (hasSupport(level, pos)) {
                return pos;
            }
        }
        return origin;
    }

    /** Air, plants, snow layers and fluids - never something a player built. */
    private static boolean canPlace(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity()
                && (state.isAir() || state.canBeReplaced())
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static boolean hasSupport(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.isInWorldBounds(below)
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * The block this chest is built out of: the addon's own chest wearing the configured
     * skin, or the plain block from the settings when no skin was picked.
     *
     * <p>Whether the skin has any artwork behind it is not asked here. Resources only exist
     * on the client, so the block goes down either way and the renderer falls back to the
     * vanilla chest look if the texture never shipped.</p>
     */
    private static BlockState chestState(Pending pending) {
        BossChestStyles.Skin skin = BossChestStyles.skinOf(pending.styleId());
        if (skin != null && BlockRegistry.bossChest != null) {
            return BlockRegistry.bossChest.defaultBlockState().setValue(BossChestBlock.STYLE, skin);
        }

        Block block = ContainerBlockUtil.resolve(pending.blockId());
        if (block == null) {
            if (!pending.blockId().equals(reportedBrokenBlock)) {
                reportedBrokenBlock = pending.blockId();
                LOGGER.warn("Boss loot chest block {} is not a container, using {} instead",
                        pending.blockId(), ContainerBlockUtil.DEFAULT_ID);
            }
            return Blocks.CHEST.defaultBlockState();
        }
        reportedBrokenBlock = "";
        return block.defaultBlockState();
    }

    private static BlockState orient(ServerLevel level, BlockState state, BlockPos pos, Direction facing) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, facing);
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            // A boss killed in the sea would otherwise leave a bubble of air around its loot.
            state = state.setValue(BlockStateProperties.WATERLOGGED,
                    level.getFluidState(pos).getType() == Fluids.WATER);
        }
        return state;
    }

    /** Faces the chest at whoever landed the kill, or the way the boss itself was looking. */
    private static Direction chestFacing(Entity boss, Entity killer) {
        if (killer == null) {
            return boss.getDirection();
        }
        double dx = killer.getX() - boss.getX();
        double dz = killer.getZ() - boss.getZ();
        return dx * dx + dz * dz < 1.0E-4D ? boss.getDirection() : Direction.getNearest(dx, 0.0D, dz);
    }

    private static void applyName(BlockEntity blockEntity, Component name) {
        blockEntity.applyComponents(DataComponentMap.EMPTY,
                DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, name).build());
    }

    /**
     * Rolls the configured loot table right here and now.
     *
     * <p>The result is poured into the chest rather than hung on the block entity: vanilla
     * unpacks a pending loot table the first time someone opens the container, and that
     * would wipe everything already put inside.</p>
     */
    private static List<ItemStack> rollLootTable(ServerLevel level, String id, BlockPos pos) {
        if (id.isEmpty()) {
            return List.of();
        }
        ResourceLocation location = AnimationFileUtil.parse(id);
        LootTable table = location == null ? null : level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, location));
        if (table == null || table == LootTable.EMPTY) {
            if (!id.equals(reportedBrokenLootTable)) {
                reportedBrokenLootTable = id;
                LOGGER.warn("Boss loot table {} does not exist, the chest gets nothing from it", id);
            }
            return List.of();
        }
        reportedBrokenLootTable = "";
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    /**
     * Scatters the loot over random free slots, so a half-full chest does not look like a
     * list someone typed in.
     *
     * @return the stacks that did not fit
     */
    private static List<ItemStack> fill(Container container, List<ItemStack> items, RandomSource random) {
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                free.add(slot);
            }
        }
        for (int i = free.size() - 1; i > 0; i--) {
            Collections.swap(free, i, random.nextInt(i + 1));
        }

        List<ItemStack> leftovers = new ArrayList<>();
        int next = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            if (next >= free.size()) {
                leftovers.add(stack);
            } else {
                container.setItem(free.get(next++), stack);
            }
        }
        return leftovers;
    }
}
