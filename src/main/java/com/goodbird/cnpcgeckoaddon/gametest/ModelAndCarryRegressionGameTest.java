package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncNpcCarryState;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class ModelAndCarryRegressionGameTest {
    @GameTest(template = "fluid_platform")
    public static void resizingModelUpdatesCachedDimensions(GameTestHelper helper) {
        EntityCustomModel model = new EntityCustomModel(EntityRegistry.entityCustomModel, helper.getLevel());
        model.setSize(3.0F, 4.0F);
        helper.assertTrue(model.getBbWidth() == 3.0F && model.getBbHeight() == 4.0F,
                "the entity cache must match the selected model size");
        model.setSize(0.7F, 2.0F);
        helper.assertTrue(model.getBbWidth() == 0.7F && model.getBbHeight() == 2.0F
                        && model.getDimensions(Pose.STANDING).width() == 0.7F,
                "returning to the default size must update both dimension views");
        model.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void invalidHitboxNumbersCannotSurviveLoading(GameTestHelper helper) {
        CustomModelData data = new CustomModelData();
        data.setModel("test:geo/missing.geo.json");
        data.setAutoHitbox(false);
        CompoundTag saved = data.writeToNBT(new CompoundTag());
        saved.putFloat("Width", Float.NaN);
        saved.putFloat("Height", Float.POSITIVE_INFINITY);
        saved.putFloat("HitboxScale", Float.NEGATIVE_INFINITY);
        data.readFromNBT(saved);
        helper.assertTrue(data.getWidth() == 0.7F && data.getHeight() == 2.0F && data.getHitboxScale() == 1.0F,
                "invalid saved numbers must recover finite defaults");
        data.setWidth(Float.MAX_VALUE);
        data.setHeight(Float.MAX_VALUE);
        data.setHitboxScale(16.0F);
        helper.assertTrue(Float.isFinite(data.getEffectiveWidth()) && Float.isFinite(data.getEffectiveHeight()),
                "finite inputs must not overflow when the hitbox scale is applied");
        data.setWidth(-2.0F);
        data.setHeight(Float.NaN);
        helper.assertTrue(data.getWidth() >= 0.0F && Float.isFinite(data.getHeight()),
                "direct setters must enforce the same validation as NBT loading");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void carryPacketUpdatesIndependentNpcCopy(GameTestHelper helper) {
        EntityNPCInterface source = CustomEntities.entityCustomNpc.create(helper.getLevel());
        EntityNPCInterface copy = CustomEntities.entityCustomNpc.create(helper.getLevel());
        copy.setId(source.getId());
        copy.setUUID(source.getUUID());
        copy.display.setHitboxState((byte) 2);
        helper.assertTrue(copy.canBeCollidedWith(), "the fixture must begin with a solid hitbox");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PacketSyncNpcCarryState.encode(new PacketSyncNpcCarryState(source, true), buffer);
            PacketSyncNpcCarryState.decode(buffer).apply(copy);
            helper.assertTrue(NpcCarryManager.isCarried(copy) && !copy.canBeCollidedWith() && !copy.isPushable(),
                    "a copy without server carry maps must become noncolliding after receiving the packet");
            new PacketSyncNpcCarryState(copy.getId(), UUID.randomUUID(), false).apply(copy);
            helper.assertTrue(NpcCarryManager.isCarried(copy), "a reused numeric ID must not accept another NPC's packet");
            new PacketSyncNpcCarryState(source, false).apply(copy);
            helper.assertTrue(!NpcCarryManager.isCarried(copy) && copy.canBeCollidedWith(),
                    "release must restore the original collision behavior");
            helper.succeed();
        } finally {
            buffer.release();
            copy.discard();
            source.discard();
        }
    }

    @GameTest(template = "fluid_platform")
    public static void pickupAndReleaseRestoreCarryStateAndSavedFlags(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cnpc_carry_test"));
        EntityNPCInterface npc = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        npc.setNoAi(false);
        npc.setNoGravity(false);
        npc.setInvulnerable(false);
        player.setPos(npc.position());
        try {
            helper.assertTrue(NpcCarryManager.pickUp(player, npc, true), "the builder must be able to pick up the NPC");
            helper.assertTrue(NpcCarryManager.isCarried(npc) && npc.isNoAi() && npc.isNoGravity(),
                    "pickup must activate the carry state and borrowed flags");
            CompoundTag saved = npc.saveWithoutId(new CompoundTag());
            helper.assertTrue(!saved.getBoolean("NoAI") && !saved.getBoolean("NoGravity") && !saved.getBoolean("Invulnerable"),
                    "a save during carry must preserve the original flags");
            NpcCarryManager.releaseNpc(npc);
            helper.assertTrue(!NpcCarryManager.isCarried(npc) && !NpcCarryManager.isCarrying(player)
                            && !npc.isNoAi() && !npc.isNoGravity() && !npc.isInvulnerable(),
                    "release must clear runtime carry state and restore every borrowed flag");
            helper.succeed();
        } finally {
            NpcCarryManager.releaseNpc(npc);
            npc.discard();
            player.discard();
        }
    }
}
