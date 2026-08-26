package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import noppes.npcs.entity.EntityNPCInterface;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side state of the {@code /cnpcgecko carry} builder tool.
 *
 * <p>A carried npc never leaves the world. It is moved in front of its carrier every tick
 * instead of being serialised into a pocket, because an npc that only exists as a tag is one
 * failed write away from losing its dialogs, inventory and every setting on it.</p>
 *
 * <p>Nothing here is written to NBT: after a restart nobody is carrying anything.</p>
 */
public final class NpcCarryManager {
    /** How far in front of the carrier's eyes the npc floats, before its own width. */
    private static final double CARRY_DISTANCE = 2.0D;
    /** Held a little under eye level so it does not sit on top of the crosshair. */
    private static final double CARRY_DROP = 0.35D;
    private static final double PLACE_REACH = 6.0D;
    private static final int PREVIEW_INTERVAL = 4;
    private static final int PREVIEW_POINTS = 12;
    private static final double PREVIEW_RADIUS = 0.6D;
    private static final Vector3f PREVIEW_FREE = new Vector3f(0.35F, 0.95F, 0.45F);
    private static final Vector3f PREVIEW_BLOCKED = new Vector3f(0.95F, 0.25F, 0.25F);

    private static final Set<UUID> CARRY_MODE = new HashSet<>();
    private static final Map<UUID, CarryRuntime> BY_PLAYER = new HashMap<>();
    /** Also read off the server thread, by the carry mixin on client side npc copies. */
    private static final Map<UUID, CarryRuntime> BY_NPC = new ConcurrentHashMap<>();

    private NpcCarryManager() {
    }

    /** Everything needed to put one npc back the way it was found. */
    private static final class CarryRuntime {
        private final UUID playerId;
        private final UUID npcId;
        private final ResourceKey<Level> levelKey;
        private final Vec3 pickupPos;
        private final float pickupYaw;
        private final float pickupPitch;
        private final BlockPos pickupStartPos;
        private final boolean hadNoAi;
        private final boolean wasInvulnerable;
        private final boolean hadNoGravity;

        private CarryRuntime(ServerPlayer player, EntityNPCInterface npc) {
            this.playerId = player.getUUID();
            this.npcId = npc.getUUID();
            this.levelKey = npc.level().dimension();
            this.pickupPos = npc.position();
            this.pickupYaw = npc.getYRot();
            this.pickupPitch = npc.getXRot();
            this.pickupStartPos = npc.ais.startPos();
            this.hadNoAi = npc.isNoAi();
            this.wasInvulnerable = npc.isInvulnerable();
            this.hadNoGravity = npc.isNoGravity();
        }
    }

    /** Where the npc would land if it were put down now, and whether it fits there. */
    private record Placement(Vec3 point, boolean fits) {
    }

    /** @return true when carry mode is on for this player after the toggle */
    public static boolean toggleMode(ServerPlayer player) {
        if (CARRY_MODE.add(player.getUUID())) {
            return true;
        }
        CARRY_MODE.remove(player.getUUID());
        release(player);
        return false;
    }

    public static boolean isCarryMode(ServerPlayer player) {
        return CARRY_MODE.contains(player.getUUID());
    }

    public static boolean isCarrying(ServerPlayer player) {
        return BY_PLAYER.containsKey(player.getUUID());
    }

    /** Answers for client side copies too, so a held npc has no hitbox on either side. */
    public static boolean isCarried(Entity npc) {
        return !BY_NPC.isEmpty() && BY_NPC.containsKey(npc.getUUID());
    }

    /**
     * Takes an npc into the carrier's hands.
     *
     * @return true when the click was consumed and the npc's own interaction must not run
     */
    public static boolean pickUp(ServerPlayer player, EntityNPCInterface npc) {
        if (!(player.level() instanceof ServerLevel level) || npc.level() != level
                || npc.isRemoved() || !npc.isAlive()) {
            return false;
        }
        if (BY_PLAYER.containsKey(player.getUUID())) {
            // One npc per pair of hands. A held npc covers the crosshair, so this is also
            // the click a builder aims at the floor to put one down.
            return placeFromAim(player);
        }
        if (BY_NPC.containsKey(npc.getUUID())) {
            player.displayClientMessage(Component.translatable("cnpcgeckoaddon.carry.busy"), true);
            return true;
        }
        CarryRuntime carry = new CarryRuntime(player, npc);
        BY_PLAYER.put(carry.playerId, carry);
        BY_NPC.put(carry.npcId, carry);
        // Borrowed for the trip and handed back on placement: a carried npc neither thinks
        // nor fights, and does not sink out of the carrier's hands under its own weight.
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setNoGravity(true);
        hold(player, npc);
        player.displayClientMessage(
                Component.translatable("cnpcgeckoaddon.carry.picked", npc.getName()), true);
        return true;
    }

    /**
     * Puts the held npc down on the block the carrier is looking at.
     *
     * @return true when the click was consumed, a refused placement included
     */
    public static boolean placeFromAim(ServerPlayer player) {
        CarryRuntime carry = BY_PLAYER.get(player.getUUID());
        if (carry == null || !(player.level() instanceof ServerLevel level)
                || !carry.levelKey.equals(level.dimension())) {
            return false;
        }
        Entity held = level.getEntity(carry.npcId);
        if (!(held instanceof EntityNPCInterface npc) || npc.isRemoved() || !npc.isAlive()) {
            forget(carry);
            return false;
        }
        Placement placement = aimedPlacement(level, player, npc);
        if (!placement.fits()) {
            player.displayClientMessage(Component.translatable("cnpcgeckoaddon.carry.blocked"), true);
            return true;
        }
        // Shift puts the npc down the way it was standing, for rebuilding a room full of
        // statues without lining every one of them up again.
        float yaw = player.isShiftKeyDown() ? carry.pickupYaw : player.getYRot();
        settle(npc, carry, placement.point(), yaw, 0.0F, BlockPos.containing(placement.point()));
        forget(carry);
        player.displayClientMessage(
                Component.translatable("cnpcgeckoaddon.carry.placed", npc.getName()), true);
        return true;
    }

    /** Puts down whatever this player is holding, back where it was picked up. */
    public static void release(ServerPlayer player) {
        CarryRuntime carry = BY_PLAYER.get(player.getUUID());
        if (carry == null) {
            return;
        }
        ServerLevel level = player.getServer() == null
                ? null : player.getServer().getLevel(carry.levelKey);
        Entity held = level == null ? null : level.getEntity(carry.npcId);
        if (held instanceof EntityNPCInterface npc && !npc.isRemoved() && npc.isAlive()) {
            settleAtPickup(npc, carry);
            player.displayClientMessage(
                    Component.translatable("cnpcgeckoaddon.carry.returned", npc.getName()), true);
        }
        forget(carry);
    }

    public static void tick(ServerLevel level) {
        if (BY_PLAYER.isEmpty()) {
            return;
        }
        boolean showRing = level.getGameTime() % PREVIEW_INTERVAL == 0L;
        for (CarryRuntime carry : BY_PLAYER.values().toArray(CarryRuntime[]::new)) {
            if (!carry.levelKey.equals(level.dimension())) {
                continue;
            }
            Entity held = level.getEntity(carry.npcId);
            if (!(held instanceof EntityNPCInterface npc) || npc.isRemoved() || !npc.isAlive()) {
                // Nothing left to put down, so there is nothing to repair either.
                forget(carry);
                continue;
            }
            ServerPlayer player = level.getPlayerByUUID(carry.playerId) instanceof ServerPlayer found
                    ? found : null;
            if (player == null || player.isRemoved() || !player.isAlive()) {
                settleAtPickup(npc, carry);
                forget(carry);
                continue;
            }
            hold(player, npc);
            if (showRing) {
                showPlacementRing(level, player, npc);
            }
        }
    }

    /** Pins the npc in front of the carrier for one tick. */
    private static void hold(ServerPlayer player, EntityNPCInterface npc) {
        float yaw = player.getYRot();
        Vec3 anchor = carryAnchor(player, npc);
        npc.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setTarget(null);
        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0.0F;
        // Mobs are only sampled for their trackers every third tick; without this the npc
        // swims a long way behind the hands that are holding it.
        npc.hasImpulse = true;
    }

    private static Vec3 carryAnchor(ServerPlayer player, EntityNPCInterface npc) {
        // Half the npc's own width on top of the fixed distance keeps a wide boss clear of
        // the carrier instead of standing inside them.
        double distance = CARRY_DISTANCE + npc.getBbWidth() * 0.5D;
        Vec3 center = player.getEyePosition()
                .add(player.getLookAngle().scale(distance))
                .subtract(0.0D, CARRY_DROP, 0.0D);
        return new Vec3(center.x, center.y - npc.getBbHeight() * 0.5D, center.z);
    }

    /**
     * Traces the carrier's aim to the spot the npc would stand on.
     *
     * <p>The landing point snaps to the middle and the top of the block the ray stopped in,
     * which is what makes a row of placed npcs line up with the room around them.</p>
     */
    private static Placement aimedPlacement(ServerLevel level, ServerPlayer player, EntityNPCInterface npc) {
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(PLACE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return new Placement(far, false);
        }
        BlockPos hitPos = hit.getBlockPos();
        VoxelShape shape = level.getBlockState(hitPos).getCollisionShape(level, hitPos);
        double top = shape.isEmpty() ? 0.0D : shape.max(Direction.Axis.Y);
        Vec3 point = new Vec3(hitPos.getX() + 0.5D, hitPos.getY() + top, hitPos.getZ() + 0.5D);
        return new Placement(point, fits(level, npc, point));
    }

    private static boolean fits(ServerLevel level, EntityNPCInterface npc, Vec3 point) {
        if (!level.isInWorldBounds(BlockPos.containing(point))) {
            return false;
        }
        AABB box = npc.getBoundingBox().move(
                point.x - npc.getX(), point.y - npc.getY(), point.z - npc.getZ());
        return level.getWorldBorder().isWithinBounds(box) && level.noCollision(npc, box);
    }

    /** A ring on the ground where the npc would land, so nobody has to place one blind. */
    private static void showPlacementRing(ServerLevel level, ServerPlayer player, EntityNPCInterface npc) {
        Placement placement = aimedPlacement(level, player, npc);
        DustParticleOptions dust = new DustParticleOptions(
                placement.fits() ? PREVIEW_FREE : PREVIEW_BLOCKED, 1.0F);
        double radius = Math.max(PREVIEW_RADIUS, npc.getBbWidth() * 0.5D);
        for (int step = 0; step < PREVIEW_POINTS; step++) {
            double angle = step * Mth.TWO_PI / PREVIEW_POINTS;
            level.sendParticles(player, dust, true,
                    placement.point().x + Math.cos(angle) * radius,
                    placement.point().y + 0.05D,
                    placement.point().z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void settleAtPickup(EntityNPCInterface npc, CarryRuntime carry) {
        settle(npc, carry, carry.pickupPos, carry.pickupYaw, carry.pickupPitch, carry.pickupStartPos);
    }

    /**
     * Puts a carried npc back into the world: its position, its home and every borrowed flag.
     *
     * @param home what CustomNPCs is told to treat as the npc's start position. This is what
     *             makes a placed npc stay placed: it is both the point a reset walks back to
     *             and the point a respawn resurrects on
     */
    private static void settle(EntityNPCInterface npc, CarryRuntime carry,
                               Vec3 point, float yaw, float pitch, BlockPos home) {
        npc.moveTo(point.x, point.y, point.z, yaw, pitch);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.ais.setStartPos(home);
        npc.setNoAi(carry.hadNoAi);
        npc.setInvulnerable(carry.wasInvulnerable);
        npc.setNoGravity(carry.hadNoGravity);
        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0.0F;
        npc.hasImpulse = true;
        relocateArena(npc);
        npc.updateClient();
    }

    /**
     * A boss also remembers the arena it woke up in, which start position knows nothing about.
     * Without this it teleports back to the old room the first time its leash or a reset fires.
     */
    private static void relocateArena(EntityNPCInterface npc) {
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                controller.onRelocated();
            }
        }
    }

    private static void forget(CarryRuntime carry) {
        BY_PLAYER.remove(carry.playerId, carry);
        BY_NPC.remove(carry.npcId, carry);
    }
}
