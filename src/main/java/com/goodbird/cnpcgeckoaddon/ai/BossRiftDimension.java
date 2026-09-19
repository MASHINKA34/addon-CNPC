package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The addon's own pocket dimension, where a reality rift takes its victims, and the platforms
 * laid out in it.
 *
 * <p>The dimension comes from the mod's datapack ({@code data/cnpcgeckoaddon/dimension/rift.json}),
 * so it exists in every world the addon is loaded into, old saves included; when something keeps
 * the datapack from loading, {@link #level} answers null and the rift simply does not start.</p>
 *
 * <p>The void is cut into a grid of {@link #SLOTS} slots {@link #SLOT_SPACING} blocks apart, one
 * platform to a slot, so two bosses casting at once each have a platform of their own. A boss
 * takes the slot its builder named, or one worked out from its UUID. Nothing here is saved but
 * the blocks: a platform, once built, stays for the next rift and for anybody who wants to
 * decorate it by hand.</p>
 */
public final class BossRiftDimension {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** The dimension's id, and the id its dimension type draws its sky under. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "rift");
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ID);

    /** How many platforms fit the grid: sixty-four by sixty-four. */
    public static final int SLOTS = 4096;
    private static final int GRID = 64;
    /** How far apart two slots' centres are: far enough that neither ever loads the other's chunks. */
    public static final int SLOT_SPACING = 2048;

    /** One line in the log per problem in ten seconds, however often it is run into. */
    static final long WARN_INTERVAL_NANOS = 10_000_000_000L;
    private static final Map<String, Long> LAST_WARNED = new ConcurrentHashMap<>();

    private BossRiftDimension() {
    }

    /**
     * The rift dimension's level, or null when the world has none - its datapack did not load -
     * which is said in the log once in ten seconds rather than on every cast that asks.
     */
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel level = server == null ? null : server.getLevel(KEY);
        if (level == null) {
            warn("missing", "The reality rift dimension {} is not loaded in this world, so no rift can open; "
                    + "check that the addon's datapack is enabled", ID);
        }
        return level;
    }

    /** Whether a level is the rift dimension. */
    public static boolean isRift(Level level) {
        return level != null && KEY.equals(level.dimension());
    }

    /**
     * The slot a boss' platform goes in: the builder's own when one is set, or one worked out from
     * the boss' UUID, so a boss nobody gave a slot still comes back to the same platform.
     *
     * @param configured the slot the settings name; nought means "by the boss"
     */
    public static int resolveSlot(int configured, UUID bossId) {
        if (configured > 0) {
            return Math.min(configured, SLOTS - 1);
        }
        return hashSlot(bossId);
    }

    /** A slot out of a UUID: its hash folded onto itself, cut down to the grid. */
    public static int hashSlot(UUID id) {
        if (id == null) {
            return 0;
        }
        int hash = id.hashCode();
        hash ^= hash >>> 16;
        return hash & (SLOTS - 1);
    }

    /** The x of a slot's centre block: the low six bits pick the column. */
    public static int slotCentreX(int slot) {
        return ((slot & (GRID - 1)) - GRID / 2) * SLOT_SPACING + SLOT_SPACING / 2;
    }

    /** The z of a slot's centre block: the next six bits pick the row. */
    public static int slotCentreZ(int slot) {
        return (((slot >> 6) & (GRID - 1)) - GRID / 2) * SLOT_SPACING + SLOT_SPACING / 2;
    }

    /** A slot's centre block at the given height: the floor block the taken players land on. */
    public static BlockPos slotCentre(int slot, int y) {
        return new BlockPos(slotCentreX(slot), y, slotCentreZ(slot));
    }

    /** Where somebody stands on a platform: on top of its centre block, in the middle of it. */
    public static Vec3 standingSpot(BlockPos centre) {
        return new Vec3(centre.getX() + 0.5D, centre.getY() + 1.0D, centre.getZ() + 0.5D);
    }

    /** The block an id names, or null for an id that names none, or names air. */
    public static BlockState resolveBlock(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id.trim());
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        return block == null || block.defaultBlockState().isAir() ? null : block.defaultBlockState();
    }

    /**
     * The block an id names, or {@code fallback} when it names none - with a line in the log, since
     * a platform built of something the builder did not ask for is otherwise a mystery.
     */
    public static BlockState blockOrDefault(String id, BlockState fallback, String what) {
        BlockState state = resolveBlock(id);
        if (state != null) {
            return state;
        }
        warn("block:" + what + ":" + id, "Reality rift {} block '{}' is not a block; building with {} instead",
                what, id, BuiltInRegistries.BLOCK.getKey(fallback.getBlock()));
        return fallback;
    }

    /** Whether the slot's platform is standing: its centre block holds the floor block. */
    public static boolean hasPlatform(ServerLevel level, BlockPos centre, BlockState floor) {
        return level.getBlockState(centre).is(floor.getBlock());
    }

    /**
     * Lays a platform round {@code centre}, setting every block of it - loading, and generating,
     * the void chunks it covers on the way, which in a void takes next to nothing.
     *
     * @return how many blocks were set
     */
    public static int build(ServerLevel level, BlockPos centre, int radius, int wallHeight, int lightSpacing,
                            boolean roof, BlockState floor, BlockState wall, BlockState light) {
        int set = 0;
        for (BossRiftPlatform.Placement placement
                : BossRiftPlatform.placements(radius, wallHeight, lightSpacing, roof)) {
            BlockPos pos = centre.offset(placement.dx(), placement.dy(), placement.dz());
            if (level.isOutsideBuildHeight(pos)) {
                continue;
            }
            BlockState state = switch (placement.part()) {
                case FLOOR, SUBFLOOR -> floor;
                case LIGHT -> light;
                case WALL, ROOF -> wall;
            };
            // Clients told, neighbours not: nothing in a void has anything to react to.
            if (level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
                set++;
            }
        }
        return set;
    }

    /** One warning per key in ten seconds. */
    static void warn(String key, String message, Object... arguments) {
        long now = System.nanoTime();
        Long last = LAST_WARNED.get(key);
        if (last != null && now - last < WARN_INTERVAL_NANOS) {
            return;
        }
        LAST_WARNED.put(key, now);
        LOGGER.warn(message, arguments);
    }
}
