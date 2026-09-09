package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.NpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryState;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncNpcCarryState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Server side state of every carry: the {@code /cnpcgecko carry} builder tool, and the npcs
 * marked carryable that any player may pick up without it.
 *
 * <p>A carried npc never leaves the world. It is moved in front of its carrier every tick
 * instead of being serialised into a pocket, because an npc that only exists as a tag is one
 * failed write away from losing its dialogs, inventory and every setting on it.</p>
 *
 * <p>Both paths run the same carry. They differ only in what it is allowed to do to the npc
 * and to its carrier, and that is decided once, at pickup.</p>
 *
 * <p>A carry can also end in a throw, for the npcs that allow one: the npc leaves the hands
 * and flies until it meets something, still under the carry's rules - no ai, no hitbox, the
 * invulnerability it was held with - and the landing is the placement, wherever it comes.</p>
 *
 * <p>Nothing here is written to NBT: after a restart nobody is carrying anything and nothing
 * is in the air.</p>
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
    /** One fixed id, so picking up a second npc replaces the slowdown instead of stacking. */
    private static final ResourceLocation SLOWNESS_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "npc_carry_slowness");
    /**
     * Longest single collision step of a flight, well under the thinnest wall: a move resolved
     * in slices this short can never skip clean through a block. The boulder flies by the same
     * figure.
     */
    private static final double COLLISION_SLICE = 0.4D;
    private static final double THROW_GRAVITY = 0.05D;
    /** Added to the look before it is normalised, so a level throw still lobs a little. */
    private static final double THROW_LIFT = 0.12D;
    /** A flight that has met nothing by then is put down where it is. */
    private static final int MAX_FLIGHT_TICKS = 100;

    private static final Set<UUID> CARRY_MODE = new HashSet<>();
    private static final Map<UUID, CarryRuntime> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, CarryRuntime> BY_NPC = new ConcurrentHashMap<>();
    /** Every thrown npc still in the air, by npc; its carry stays in {@link #BY_NPC} meanwhile. */
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();

    private NpcCarryManager() {
    }

    /** Everything needed to put one npc back the way it was found. */
    private static final class CarryRuntime {
        private final UUID playerId;
        private final UUID npcId;
        /** Kept so the carrier can be found again from any path that ends a carry. */
        private final MinecraftServer server;
        private final ResourceKey<Level> levelKey;
        private final Vec3 pickupPos;
        private final float pickupYaw;
        private final float pickupPitch;
        private final BlockPos pickupStartPos;
        private final boolean hadNoAi;
        private final boolean wasInvulnerable;
        private final boolean hadNoGravity;
        private final boolean invulnerableWhileHeld;
        private final boolean updatesHome;
        private final int slownessPercent;
        private final boolean dropOnDamage;
        private final double leashRadiusSqr;
        private final boolean throwable;
        private final int throwSpeed;
        private final int throwDamage;
        private final int throwKnockback;
        private final int throwSelfDamage;
        private final boolean throwBomb;
        /** Game time from which a throw is allowed: the pickup plus the npc's own pause. */
        private final long throwReadyAt;

        private CarryRuntime(ServerPlayer player, EntityNPCInterface npc, boolean builderTool) {
            this.playerId = player.getUUID();
            this.npcId = npc.getUUID();
            this.server = player.getServer();
            this.levelKey = npc.level().dimension();
            this.pickupPos = npc.position();
            this.pickupYaw = npc.getYRot();
            this.pickupPitch = npc.getXRot();
            this.pickupStartPos = npc.ais.startPos();
            this.hadNoAi = npc.isNoAi();
            this.wasInvulnerable = npc.isInvulnerable();
            this.hadNoGravity = npc.isNoGravity();
            // Read once, here: the editor can change these mid carry, and a carry has to end
            // under the rules it started under. The builder tool answers to none of them.
            NpcCarryData settings = settings(npc);
            this.invulnerableWhileHeld = builderTool || settings.isInvulnerable();
            this.updatesHome = builderTool || settings.isUpdatesHome();
            // The builder tool costs its user nothing: no slowdown, no fumbling, no leash.
            this.slownessPercent = builderTool ? 0 : settings.getSlownessPercent();
            this.dropOnDamage = !builderTool && settings.isDropOnDamage();
            double radius = builderTool ? 0.0D : settings.getLeashRadius();
            this.leashRadiusSqr = radius * radius;
            // The builder tool puts npcs down; it never throws them.
            this.throwable = !builderTool && settings.isThrowable();
            this.throwSpeed = settings.getThrowSpeed();
            this.throwDamage = settings.getThrowDamage();
            this.throwKnockback = settings.getThrowKnockback();
            this.throwSelfDamage = settings.getThrowSelfDamage();
            this.throwBomb = settings.isThrowDiesOnImpact();
            this.throwReadyAt = npc.level().getGameTime() + settings.getThrowCooldownTicks();
        }

        /** @return true when the carrier has taken the npc too far from where it was picked up */
        private boolean outOfLeash(ServerPlayer player) {
            return leashRadiusSqr > 0.0D && player.position().distanceToSqr(pickupPos) > leashRadiusSqr;
        }

        /**
         * Where the npc should call home once it is put down at {@code point}.
         *
         * @return null to leave its home where it is, which is what keeps a player from
         *         rehoming a dungeon npc by carrying it across the room
         */
        private BlockPos homeFor(Vec3 point) {
            return updatesHome ? BlockPos.containing(point) : null;
        }
    }

    /** One thrown npc, between the hands that threw it and whatever stops it. */
    private static final class Flight {
        private final CarryRuntime carry;
        /** The thrower's yaw at the throw, which the npc faces all the way. */
        private final float yaw;
        private Vec3 velocity;
        private int age;

        private Flight(CarryRuntime carry, Vec3 velocity, float yaw) {
            this.carry = carry;
            this.velocity = velocity;
            this.yaw = yaw;
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

    /**
     * Whether this npc lets any player carry it, and whether this click is how it asked.
     *
     * <p>Sneaking is asked for by default so an ordinary right click still reaches the npc's
     * own interaction: a carried npc is allowed to have dialogs like any other.</p>
     */
    public static boolean canPlayerCarry(ServerPlayer player, EntityNPCInterface npc) {
        NpcCarryData settings = settings(npc);
        if (!settings.isCarryable()) {
            return false;
        }
        if (settings.isRequireSneak() && !player.isShiftKeyDown()) {
            return false;
        }
        return holdsRequiredItem(player, settings);
    }

    private static boolean holdsRequiredItem(ServerPlayer player, NpcCarryData settings) {
        if (settings.getRequiredItem().isEmpty()) {
            return true;
        }
        // Parsed rather than compared as text, so a hand-typed "torch" matches the same item
        // the registry knows as "minecraft:torch".
        ResourceLocation required = ResourceLocation.tryParse(settings.getRequiredItem());
        return required != null
                && required.equals(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
    }

    private static NpcCarryData settings(EntityNPCInterface npc) {
        return ((INpcCarryData) npc.ais).cnpcgeckoaddon$getNpcCarryData();
    }

    public static boolean isCarrying(ServerPlayer player) {
        return BY_PLAYER.containsKey(player.getUUID());
    }

    /** Answers for client side copies too, so a held npc has no hitbox on either side. */
    public static boolean isCarried(Entity npc) {
        return npc instanceof INpcCarryState state && state.cnpcgeckoaddon$isCarried();
    }

    private static void setCarried(EntityNPCInterface npc, boolean carried) {
        INpcCarryState state = (INpcCarryState) npc;
        if (state.cnpcgeckoaddon$isCarried() != carried) {
            state.cnpcgeckoaddon$setCarried(carried);
            NetworkWrapper.sendToTracking(npc, new PacketSyncNpcCarryState(npc, carried));
        }
    }

    public static void syncForTracking(ServerPlayer player, Entity npc) {
        if (npc instanceof INpcCarryState) {
            NetworkWrapper.send(player, new PacketSyncNpcCarryState(npc, isCarried(npc)));
        }
    }

    /**
     * Takes an npc into the carrier's hands.
     *
     * @param builderTool true for {@code /cnpcgecko carry}, which may pick up any npc and
     *                    answers to none of that npc's own carry settings
     * @return true when the click was consumed and the npc's own interaction must not run
     */
    public static boolean pickUp(ServerPlayer player, EntityNPCInterface npc, boolean builderTool) {
        if (!(player.level() instanceof ServerLevel level) || npc.level() != level
                || npc.isRemoved() || !npc.isAlive()) {
            return false;
        }
        if (BY_PLAYER.containsKey(player.getUUID())) {
            // One npc per pair of hands. A held npc covers the crosshair, so this is also
            // the click a builder aims at the floor to put one down - and either way it
            // belongs to the tool, never to the npc that was clicked.
            placeFromAim(player);
            return true;
        }
        if (BY_NPC.containsKey(npc.getUUID())) {
            player.displayClientMessage(Component.translatable("cnpcgeckoaddon.carry.busy"), true);
            return true;
        }
        CarryRuntime carry = new CarryRuntime(player, npc, builderTool);
        BY_PLAYER.put(carry.playerId, carry);
        BY_NPC.put(carry.npcId, carry);
        setCarried(npc, true);
        // Borrowed for the trip and handed back on placement: a carried npc neither thinks
        // nor fights, and does not sink out of the carrier's hands under its own weight.
        npc.setNoAi(true);
        npc.setNoGravity(true);
        if (carry.invulnerableWhileHeld) {
            // Only when it was asked for. A dungeon npc that players carry into a fight is
            // meant to be shootable out of their hands - that is the fight, not a bug.
            npc.setInvulnerable(true);
        }
        applySlowness(player, carry);
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
            forget(carry, held);
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
        settle(npc, carry, placement.point(), yaw, 0.0F, carry.homeFor(placement.point()));
        forget(carry);
        player.displayClientMessage(
                Component.translatable("cnpcgeckoaddon.carry.placed", npc.getName()), true);
        return true;
    }

    /**
     * Throws the held npc the way the carrier is looking, if that npc allows it.
     *
     * <p>The npc leaves the hands the way a placement takes it - off the carrier's slowdown
     * and out of their hands at once, so they may pick up the next one while this one is
     * still in the air - but keeps everything else the carry borrowed: no ai, no gravity, no
     * hitbox, and the invulnerability it was held with. The landing hands those back.</p>
     *
     * @return true when the click was consumed, a refused throw included; false leaves the
     *         click to the placement it always was
     */
    public static boolean throwFromAim(ServerPlayer player) {
        CarryRuntime carry = BY_PLAYER.get(player.getUUID());
        if (carry == null || !carry.throwable || !(player.level() instanceof ServerLevel level)
                || !carry.levelKey.equals(level.dimension())) {
            return false;
        }
        Entity held = level.getEntity(carry.npcId);
        if (!(held instanceof EntityNPCInterface npc) || npc.isRemoved() || !npc.isAlive()) {
            forget(carry, held);
            return false;
        }
        if (level.getGameTime() < carry.throwReadyAt) {
            message(player, "cnpcgeckoaddon.carry.throw_wait", npc);
            return true;
        }
        if (!clearOfBlocks(level, npc, npc.position())) {
            // Held into a wall. A flight from inside it ends on its first slice, and the
            // rescue for that puts the npc at the carrier's feet - not what a throw is for.
            message(player, "cnpcgeckoaddon.carry.blocked", npc);
            return true;
        }
        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x, look.y + THROW_LIFT, look.z).normalize()
                .scale(carry.throwSpeed / 10.0D);
        // Off the hands but still this npc's carry: the flags it borrowed and the busy mark
        // it wears are handed back by the landing, and BY_NPC is what keeps both honest.
        BY_PLAYER.remove(carry.playerId, carry);
        clearSlowness(carry);
        FLIGHTS.put(carry.npcId, new Flight(carry, velocity, player.getYRot()));
        message(player, "cnpcgeckoaddon.carry.thrown", npc);
        return true;
    }

    /**
     * A click that reached no block on the client: the throw, or nothing at all.
     *
     * <p>The server's own aim is asked first. The client's reach is shorter than the
     * placement's, so a block the ring is already sitting on can still be a miss on the
     * client - and a click at the ring puts the npc down there, as it always has.</p>
     */
    public static boolean throwIntoAir(ServerPlayer player) {
        CarryRuntime carry = BY_PLAYER.get(player.getUUID());
        if (carry == null || !carry.throwable || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return aimRay(level, player).getType() != HitResult.Type.BLOCK && throwFromAim(player);
    }

    /** An item click that reached no block on the client: a throw, else the old placement. */
    public static boolean throwOrPlace(ServerPlayer player) {
        return throwIntoAir(player) || placeFromAim(player);
    }

    /** Ends this player's carry: the mode going off, a death, a logout, a new dimension. */
    public static void release(ServerPlayer player) {
        CarryRuntime carry = BY_PLAYER.get(player.getUUID());
        if (carry == null) {
            return;
        }
        ServerLevel level = player.getServer() == null
                ? null : player.getServer().getLevel(carry.levelKey);
        end(level, carry, player);
    }

    /** A logout also drops carry mode, so a returning builder is not still holding a tool. */
    public static void onPlayerGone(ServerPlayer player) {
        release(player);
        // Straight off this instance, without going through the player list it is being
        // removed from: a slowdown that outlives the carry is a player slow forever.
        clearSlowness(player);
        CARRY_MODE.remove(player.getUUID());
    }

    /**
     * Knocks the npc out of a carrier's hands, when the npc it is carrying asked for that.
     *
     * <p>Any damage counts. The npc lands where it slipped rather than going back to its
     * pickup point, because a bomb dropped mid fight is supposed to be lying at your feet.</p>
     */
    public static void onCarrierDamaged(ServerPlayer player) {
        CarryRuntime carry = BY_PLAYER.get(player.getUUID());
        if (carry == null || !carry.dropOnDamage
                || !(player.level() instanceof ServerLevel level)
                || !carry.levelKey.equals(level.dimension())) {
            return;
        }
        Entity held = level.getEntity(carry.npcId);
        if (!(held instanceof EntityNPCInterface npc) || npc.isRemoved() || !npc.isAlive()) {
            forget(carry, held);
            return;
        }
        drop(level, carry, npc, player, null);
    }

    /** The npc is gone - dead, deleted or unloaded - so there is nowhere left to carry it. */
    public static void releaseNpc(Entity npc) {
        if (BY_NPC.isEmpty()) {
            return;
        }
        CarryRuntime carry = BY_NPC.get(npc.getUUID());
        if (carry != null) {
            forget(carry, npc);
        }
    }

    public static void clearLevel(ServerLevel level) {
        for (CarryRuntime carry : BY_PLAYER.values().toArray(CarryRuntime[]::new)) {
            if (carry.levelKey.equals(level.dimension())) {
                end(level, carry, carrier(level, carry));
            }
        }
        for (Flight flight : FLIGHTS.values().toArray(Flight[]::new)) {
            if (flight.carry.levelKey.equals(level.dimension())) {
                landNow(level, flight.carry);
            }
        }
    }

    /** Every carry has to land before the world is written out. */
    public static void shutdown(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            clearLevel(level);
        }
        CARRY_MODE.clear();
    }

    /**
     * Writes the borrowed flags back to what the npc had before it was picked up.
     *
     * <p>An autosave or a chunk unload can catch an npc mid carry, and that snapshot is what
     * a crash restores from. Left alone it would bring the npc back as an immortal statue
     * with its ai switched off, which is exactly the state nobody can debug afterwards.</p>
     */
    public static void restoreSavedFlags(Entity npc, CompoundTag tag) {
        if (BY_NPC.isEmpty()) {
            return;
        }
        CarryRuntime carry = BY_NPC.get(npc.getUUID());
        if (carry == null) {
            return;
        }
        tag.putBoolean("NoAI", carry.hadNoAi);
        tag.putBoolean("Invulnerable", carry.wasInvulnerable);
        tag.putBoolean("NoGravity", carry.hadNoGravity);
    }

    public static void tick(ServerLevel level) {
        tickCarries(level);
        tickFlights(level);
    }

    private static void tickCarries(ServerLevel level) {
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
                forget(carry, held);
                continue;
            }
            ServerPlayer player = carrier(level, carry);
            if (player == null || player.isRemoved() || !player.isAlive()) {
                abort(level, carry, npc, player);
                continue;
            }
            if (carry.outOfLeash(player)) {
                // Checked before the npc is pulled along for another tick, so it stays at the
                // edge of the leash instead of being dragged one more step past it.
                drop(level, carry, npc, player, "cnpcgeckoaddon.carry.too_far");
                continue;
            }
            hold(player, npc);
            if (showRing) {
                showPlacementRing(level, player, npc);
            }
        }
    }

    private static void tickFlights(ServerLevel level) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        for (Flight flight : FLIGHTS.values().toArray(Flight[]::new)) {
            CarryRuntime carry = flight.carry;
            if (!carry.levelKey.equals(level.dimension())) {
                continue;
            }
            Entity held = level.getEntity(carry.npcId);
            if (!(held instanceof EntityNPCInterface npc) || npc.isRemoved() || !npc.isAlive()) {
                forget(carry, held);
                continue;
            }
            ServerPlayer thrower = carrier(level, carry);
            if (thrower == null || thrower.isRemoved() || !thrower.isAlive()) {
                // Nobody left to credit a hit to: the flight is over where it is.
                land(level, carry, npc, null, npc.position());
                continue;
            }
            if (++flight.age > MAX_FLIGHT_TICKS) {
                land(level, carry, npc, thrower, npc.position());
                continue;
            }
            fly(level, flight, npc, thrower);
        }
    }

    /** Pins the npc in front of the carrier for one tick. */
    private static void hold(ServerPlayer player, EntityNPCInterface npc) {
        Vec3 anchor = carryAnchor(player, npc);
        pin(npc, anchor.x, anchor.y, anchor.z, player.getYRot());
    }

    /** Puts the npc exactly here for one tick, with nothing of its own moving it. */
    private static void pin(EntityNPCInterface npc, double x, double y, double z, float yaw) {
        npc.moveTo(x, y, z, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        if (npc.getTarget() != null) {
            // Guarded because CustomNPCs runs the npc's target-lost scripts on every call,
            // and a carry would otherwise fire them twenty times a second.
            npc.setTarget(null);
        }
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
        BlockHitResult hit = aimRay(level, player);
        if (hit.getType() != HitResult.Type.BLOCK) {
            // A miss ends where the reach does, which is where the ring is drawn.
            return new Placement(hit.getLocation(), false);
        }
        if (hit.getDirection() == Direction.DOWN) {
            // Aimed at a ceiling. Its top face is on the far side of it, so snapping there
            // would push the npc through the block into whatever room is above.
            return new Placement(hit.getLocation(), false);
        }
        BlockPos hitPos = hit.getBlockPos();
        VoxelShape shape = level.getBlockState(hitPos).getCollisionShape(level, hitPos);
        double top = shape.isEmpty() ? 0.0D : shape.max(Direction.Axis.Y);
        Vec3 point = new Vec3(hitPos.getX() + 0.5D, hitPos.getY() + top, hitPos.getZ() + 0.5D);
        return new Placement(point, fits(level, npc, point));
    }

    /** The carrier's aim, as far as a placement reaches. */
    private static BlockHitResult aimRay(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(PLACE_REACH));
        return level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
    }

    private static boolean fits(ServerLevel level, EntityNPCInterface npc, Vec3 point) {
        return clearOfBlocks(level, npc, point) && level.noCollision(npc, boxAt(npc, point));
    }

    /**
     * The landing's own test: inside the world and clear of blocks, whatever entities are
     * standing there. A thrown npc comes down beside the victim it hit, and may overlap the
     * solid hitbox of a boss the way the full test never lets a placement.
     */
    private static boolean clearOfBlocks(ServerLevel level, EntityNPCInterface npc, Vec3 point) {
        if (!level.isInWorldBounds(BlockPos.containing(point))) {
            return false;
        }
        AABB box = boxAt(npc, point);
        return level.getWorldBorder().isWithinBounds(box) && level.noBlockCollision(npc, box);
    }

    private static AABB boxAt(EntityNPCInterface npc, Vec3 point) {
        return npc.getBoundingBox().move(
                point.x - npc.getX(), point.y - npc.getY(), point.z - npc.getZ());
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

    /**
     * One tick of a flight, resolved in slices the way the boulder rolls.
     *
     * <p>Blocks stop it and the first living thing in its path takes the hit; both are asked
     * of every slice in turn, so a fast npc breaks on a thin wall rather than blinking through
     * it, and comes down beside the victim it met rather than a whole tick's travel past.</p>
     */
    private static void fly(ServerLevel level, Flight flight, EntityNPCInterface npc, ServerPlayer thrower) {
        Vec3 motion = flight.velocity;
        int slices = Math.max(1, Mth.ceil(motion.length() / COLLISION_SLICE));
        Vec3 step = motion.scale(1.0D / slices);
        AABB box = npc.getBoundingBox();
        for (int i = 0; i < slices; i++) {
            AABB moved = box.move(step.x, step.y, step.z);
            if (!level.noBlockCollision(npc, moved)) {
                land(level, flight.carry, npc, thrower, pointOf(box));
                return;
            }
            box = moved;
            LivingEntity victim = firstVictim(level, npc, thrower, box);
            if (victim != null) {
                strike(level, flight, npc, thrower, victim, pointOf(box));
                return;
            }
        }
        Vec3 point = pointOf(box);
        if (point.y < level.getMinBuildHeight()) {
            // Thrown over an edge: nothing below will ever stop it, and the landing's own
            // fallback is what brings it back from outside the world.
            land(level, flight.carry, npc, thrower, point);
            return;
        }
        pin(npc, point.x, point.y, point.z, flight.yaw);
        flight.velocity = motion.subtract(0.0D, THROW_GRAVITY, 0.0D);
    }

    /** The feet of a box, which is where an npc stands. */
    private static Vec3 pointOf(AABB box) {
        return new Vec3((box.minX + box.maxX) * 0.5D, box.minY, (box.minZ + box.maxZ) * 0.5D);
    }

    /** Whoever the box just moved over, nearest to its middle first; null for nobody. */
    private static LivingEntity firstVictim(ServerLevel level, EntityNPCInterface npc,
                                            ServerPlayer thrower, AABB box) {
        LivingEntity first = null;
        double firstDistance = Double.MAX_VALUE;
        Vec3 center = box.getCenter();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.1D),
                found -> isVictim(npc, thrower, found))) {
            double distance = candidate.position().distanceToSqr(center);
            if (distance < firstDistance) {
                first = candidate;
                firstDistance = distance;
            }
        }
        return first;
    }

    /**
     * Who a thrown npc can hit: anything living but itself, its thrower, and whatever else is
     * being carried or thrown - two npcs in the air must not break on each other.
     */
    private static boolean isVictim(EntityNPCInterface npc, ServerPlayer thrower, LivingEntity candidate) {
        return candidate != npc && candidate != thrower && candidate.isAlive()
                && !candidate.isSpectator() && !isCarried(candidate);
    }

    /**
     * The hit: the damage under the throw's own type, the shove on along the flight, and then
     * either the bomb's exit or a landing beside the victim.
     *
     * <p>The damage goes through {@code hurt} the way a sword swing does, so a boss' totem
     * shield, its resistance list and its barrier all get their say; a rule naming
     * {@code cnpcgeckoaddon:thrown_npc} is how a boss is made to take nothing else.</p>
     */
    private static void strike(ServerLevel level, Flight flight, EntityNPCInterface npc,
                               ServerPlayer thrower, LivingEntity victim, Vec3 point) {
        CarryRuntime carry = flight.carry;
        boolean damaged = carry.throwDamage > 0
                && victim.hurt(NpcThrowDamage.source(level, npc, thrower), carry.throwDamage);
        if (damaged && carry.throwKnockback > 0) {
            // Vanilla shoves against the vector it is handed, so the flight goes in negated
            // to throw the victim the way the npc was moving.
            Vec3 along = flight.velocity.normalize();
            victim.knockback(carry.throwKnockback, -along.x, -along.z);
        }
        if (carry.throwBomb) {
            // Gone like a totem: no drops, no death scripts, nothing for a respawn timer to
            // revive. Forgotten first, so the leave event finds no carry to hand flags to.
            forget(carry);
            npc.discard();
            return;
        }
        land(level, carry, npc, thrower, point);
        if (carry.throwSelfDamage > 0 && !npc.isRemoved()) {
            // After the landing, which is what hands the npc back the vulnerability it may
            // have been held without.
            npc.hurt(NpcThrowDamage.source(level, npc, null), carry.throwSelfDamage);
        }
    }

    /**
     * Ends a flight at {@code point} with the placement's own code.
     *
     * <p>Only blocks can refuse the spot. One inside a wall or outside the world falls back on
     * {@link #abort}, which puts the npc at the thrower's feet or back where it was picked up:
     * an npc is never lost, however far it was thrown.</p>
     */
    private static void land(ServerLevel level, CarryRuntime carry, EntityNPCInterface npc,
                             ServerPlayer thrower, Vec3 point) {
        if (!clearOfBlocks(level, npc, point)) {
            abort(level, carry, npc, thrower);
            return;
        }
        settle(npc, carry, point, npc.getYRot(), 0.0F, carry.homeFor(point));
        forget(carry);
    }

    /** Brings a flight down where it is, for an end that cannot wait for the next tick. */
    private static void landNow(ServerLevel level, CarryRuntime carry) {
        Entity held = level.getEntity(carry.npcId);
        if (held instanceof EntityNPCInterface npc && !npc.isRemoved() && npc.isAlive()) {
            land(level, carry, npc, carrier(level, carry), npc.position());
        } else {
            forget(carry, held);
        }
    }

    private static ServerPlayer carrier(ServerLevel level, CarryRuntime carry) {
        return level.getPlayerByUUID(carry.playerId) instanceof ServerPlayer found ? found : null;
    }

    private static void end(ServerLevel level, CarryRuntime carry, ServerPlayer player) {
        Entity held = level == null ? null : level.getEntity(carry.npcId);
        if (held instanceof EntityNPCInterface npc && !npc.isRemoved() && npc.isAlive()) {
            abort(level, carry, npc, player);
        } else {
            forget(carry, held);
        }
    }

    /**
     * Ends a carry that cannot go on, and never at the npc's expense.
     *
     * <p>It is put down where the carrier is standing. When that spot cannot hold it, or the
     * carrier is not there to stand anywhere, it goes back to the exact place it was picked
     * up from with the start position it had before anyone touched it.</p>
     */
    private static void abort(ServerLevel level, CarryRuntime carry,
                              EntityNPCInterface npc, ServerPlayer player) {
        Vec3 atCarrier = player != null && player.level() == level && !player.isRemoved()
                ? player.position() : null;
        if (atCarrier != null && fits(level, npc, atCarrier)) {
            settle(npc, carry, atCarrier, npc.getYRot(), 0.0F, carry.homeFor(atCarrier));
            message(player, "cnpcgeckoaddon.carry.placed", npc);
        } else {
            settleAtPickup(npc, carry);
            message(player, "cnpcgeckoaddon.carry.returned", npc);
        }
        forget(carry);
    }

    /**
     * Ends a carry the carrier did not ask to end, leaving the npc where it slipped.
     *
     * <p>Where it slipped is where it was floating, so it drops to the floor under its own
     * weight once its gravity comes back. Only when that spot cannot hold it at all does this
     * fall back on {@link #abort}, which puts it at the carrier's feet or back where it was
     * picked up: an npc is never lost, whatever the drop was for.</p>
     *
     * @param messageKey what to tell the carrier, or null to let the npc falling say it
     */
    private static void drop(ServerLevel level, CarryRuntime carry, EntityNPCInterface npc,
                             ServerPlayer player, String messageKey) {
        if (messageKey != null) {
            message(player, messageKey, npc);
        }
        Vec3 here = npc.position();
        if (!fits(level, npc, here)) {
            abort(level, carry, npc, player);
            return;
        }
        settle(npc, carry, here, npc.getYRot(), 0.0F, carry.homeFor(here));
        forget(carry);
    }

    private static void message(ServerPlayer player, String key, EntityNPCInterface npc) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, npc.getName()), true);
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
     *             and the point a respawn resurrects on. Null leaves its home untouched, and
     *             the npc walks back to where it belongs on its own
     */
    private static void settle(EntityNPCInterface npc, CarryRuntime carry,
                               Vec3 point, float yaw, float pitch, BlockPos home) {
        npc.moveTo(point.x, point.y, point.z, yaw, pitch);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        if (home != null) {
            npc.ais.setStartPos(home);
            relocateArena(npc);
        }
        restoreFlags(npc, carry);
        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0.0F;
        npc.hasImpulse = true;
        npc.updateClient();
    }

    /**
     * A boss also remembers the arena it woke up in, which start position knows nothing about.
     * Without this it teleports back to the old room the first time its leash or a reset fires.
     *
     * <p>Only ever called when the npc's home moved with it, so a boss put down without being
     * rehomed still treats the room it came from as its arena.</p>
     */
    private static void relocateArena(EntityNPCInterface npc) {
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                controller.onRelocated();
            }
        }
    }

    private static void restoreFlags(EntityNPCInterface npc, CarryRuntime carry) {
        setCarried(npc, false);
        npc.setNoAi(carry.hadNoAi);
        npc.setInvulnerable(carry.wasInvulnerable);
        npc.setNoGravity(carry.hadNoGravity);
    }

    /**
     * Drops a carry that has nothing left to put down, handing the flags back on the way out.
     *
     * <p>A dead npc gets them too: CustomNPCs revives it on its own spawn cycle, and it would
     * come back an immortal statue if the flags stayed on it.</p>
     */
    private static void forget(CarryRuntime carry, Entity held) {
        if (held instanceof EntityNPCInterface npc) {
            restoreFlags(npc, carry);
        }
        forget(carry);
    }

    private static void forget(CarryRuntime carry) {
        BY_PLAYER.remove(carry.playerId, carry);
        BY_NPC.remove(carry.npcId, carry);
        FLIGHTS.remove(carry.npcId);
        clearSlowness(carry);
    }

    /**
     * Weighs the carrier down for as long as they are holding the npc.
     *
     * <p>Transient on purpose: a permanent modifier is written into the player's own data,
     * and a server that goes down mid carry would bring them back slow with nothing left to
     * explain why.</p>
     */
    private static void applySlowness(ServerPlayer player, CarryRuntime carry) {
        if (carry.slownessPercent <= 0) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }
        // ADD_MULTIPLIED_TOTAL scales the finished value by 1 + amount, so a 30% setting is
        // handed -0.3 and leaves the carrier at seven tenths of their own speed.
        instance.removeModifier(SLOWNESS_MODIFIER_ID);
        instance.addTransientModifier(new AttributeModifier(SLOWNESS_MODIFIER_ID,
                -carry.slownessPercent / 100.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Looks the carrier up wherever they are now, because a carry can end without them. */
    private static void clearSlowness(CarryRuntime carry) {
        if (carry.slownessPercent <= 0 || carry.server == null) {
            return;
        }
        ServerPlayer player = carry.server.getPlayerList().getPlayer(carry.playerId);
        if (player != null) {
            clearSlowness(player);
        }
    }

    /** Idempotent, so every path out of a carry can call it without checking first. */
    public static void clearSlowness(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(SLOWNESS_MODIFIER_ID);
        }
    }
}
