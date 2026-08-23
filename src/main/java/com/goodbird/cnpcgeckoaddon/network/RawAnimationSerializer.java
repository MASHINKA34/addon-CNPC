package com.goodbird.cnpcgeckoaddon.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.Map;

/**
 * Shared wire format for {@link RawAnimation}.
 *
 * <p>GeckoLib ships {@code RawAnimation.STREAM_CODEC}, but it writes the loop type through
 * {@code LoopType#getId()}, which throws for a stage that has no explicit loop type. A stage
 * without one means "use whatever the animation json declares", which the addon has to be
 * able to transmit, so the loop key is simply omitted in that case.</p>
 */
final class RawAnimationSerializer {
    private static final String ANIMATIONS_KEY = "anims";
    private static final String NAME_KEY = "name";
    private static final String LOOP_KEY = "loop";

    private RawAnimationSerializer() {
    }

    static void write(FriendlyByteBuf buf, RawAnimation animation) {
        CompoundTag compound = new CompoundTag();
        ListTag animList = new ListTag();
        if (animation != null) {
            for (RawAnimation.Stage stage : animation.getAnimationStages()) {
                CompoundTag animTag = new CompoundTag();
                animTag.putString(NAME_KEY, stage.animationName());
                String loopId = loopTypeId(stage.loopType());
                if (loopId != null) {
                    animTag.putString(LOOP_KEY, loopId);
                }
                animList.add(animTag);
            }
        }
        compound.put(ANIMATIONS_KEY, animList);
        buf.writeNbt(compound);
    }

    static RawAnimation read(FriendlyByteBuf buf) {
        RawAnimation animation = RawAnimation.begin();
        CompoundTag compound = buf.readNbt();
        if (compound == null) {
            return animation;
        }
        ListTag animList = compound.getList(ANIMATIONS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < animList.size(); i++) {
            CompoundTag animTag = animList.getCompound(i);
            String name = animTag.getString(NAME_KEY);
            if (name.isEmpty()) {
                continue;
            }
            // Absent loop key => keep the loop type declared by the animation file.
            Animation.LoopType loopType = animTag.contains(LOOP_KEY, Tag.TAG_STRING)
                    ? Animation.LoopType.fromString(animTag.getString(LOOP_KEY))
                    : null;
            animation.then(name, loopType);
        }
        return animation;
    }

    private static String loopTypeId(Animation.LoopType type) {
        if (type == null) {
            return null;
        }
        for (Map.Entry<String, Animation.LoopType> entry : Animation.LoopType.LOOP_TYPES.entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        return "play_once";
    }
}
