package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.network.ManualAnimationClientBridge;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncTileAnimation;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class AnimationPacketGameTest {
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void tileAnimationKeepsItsDimensionAndStages(GameTestHelper helper) {
        ResourceLocation dimension = Level.NETHER.location();
        BlockPos pos = new BlockPos(10, 70, -20);
        RawAnimation animation = RawAnimation.begin().thenPlay("open").thenWait(12).thenLoop("idle")
                .then("close", null);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        AtomicBoolean handled = new AtomicBoolean();
        try {
            new PacketSyncTileAnimation(dimension, pos, animation).encode(buffer);
            PacketSyncTileAnimation decoded = PacketSyncTileAnimation.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0, "the decoder must consume the whole payload");
            ManualAnimationClientBridge.setHandlers(null, (receivedDimension, receivedPos, receivedAnimation) -> {
                helper.assertTrue(dimension.equals(receivedDimension), "the target dimension must survive transport");
                helper.assertTrue(pos.equals(receivedPos), "the block position must survive transport");
                helper.assertTrue(animation.equals(receivedAnimation), "animation stages must survive transport");
                handled.set(true);
            });
            PacketSyncTileAnimation.handle(decoded);
            helper.assertTrue(handled.get(), "the tile handler must receive the decoded payload");
        } finally {
            ManualAnimationClientBridge.setHandlers(null, null);
            buffer.release();
        }
        helper.succeed();
    }
}
