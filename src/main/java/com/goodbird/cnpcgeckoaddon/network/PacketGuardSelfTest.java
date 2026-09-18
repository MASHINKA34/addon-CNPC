package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.GuardSelfTest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client half of {@code /cnpcgecko selftest}: makes one guarded place fail on the client
 * of whoever ran the command, so they can watch their game carry on.
 *
 * <p>Carries nothing but which place. It goes through the registration and the guard every
 * other packet of the addon goes through, which is the point: {@link #KIND_PACKET} fails right
 * inside this handler, the way a payload that cannot be applied would.</p>
 */
public final class PacketGuardSelfTest implements CustomPacketPayload {
    public static final Type<PacketGuardSelfTest> TYPE = NetworkWrapper.typeOf(PacketGuardSelfTest.class);

    /** Fail inside the packet handler itself. */
    public static final byte KIND_PACKET = 0;
    /** Lay the wire a client tick trips over, one tick from now. */
    public static final byte KIND_CLIENT_TICK = 1;

    private final byte kind;

    public PacketGuardSelfTest(byte kind) {
        this.kind = kind;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(kind);
    }

    public static PacketGuardSelfTest decode(FriendlyByteBuf buffer) {
        return new PacketGuardSelfTest(buffer.readByte());
    }

    public static void handle(PacketGuardSelfTest packet) {
        CrashGuard.run("packet.guard_self_test", packet, PacketGuardSelfTest::handleGuarded);
    }

    private static void handleGuarded(PacketGuardSelfTest packet) {
        if (packet.kind == KIND_CLIENT_TICK) {
            GuardSelfTest.arm(GuardSelfTest.CLIENT);
            return;
        }
        GuardSelfTest.failNow(GuardSelfTest.PACKET);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
