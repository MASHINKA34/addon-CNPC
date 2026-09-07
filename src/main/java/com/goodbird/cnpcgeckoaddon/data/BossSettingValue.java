package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * How every boss setting is read back out of a save.
 *
 * <p>A number arriving from a tag has been through a world downgrade, an editor or a
 * script, so it is never used as it stands: an absent key falls back to the default the
 * setting shipped with, and a present one is clamped into the range the setting is
 * playable in. A cooldown of two billion ticks is a boss that never attacks again, and
 * nothing about it throws.</p>
 */
final class BossSettingValue {

    private BossSettingValue() {
    }

    static int value(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
