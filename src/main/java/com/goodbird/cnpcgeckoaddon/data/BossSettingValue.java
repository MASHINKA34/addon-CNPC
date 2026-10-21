package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * How every boss setting is read back out of a save.
 *
 * <p>A number arriving from a tag has been through a world downgrade, an editor or a
 * script, so it is never used as it stands: an absent key falls back to the default the
 * setting shipped with, and a present one is clamped into the range the setting is
 * playable in. A cooldown of two billion ticks is a boss that never attacks again, and
 * nothing about it throws.</p>
 *
 * <p>Public for {@link #parseOr} alone, which the editor's screens read their typed numbers with.</p>
 */
public final class BossSettingValue {

    /** A whole number as it may be typed: an optional sign and digits, nothing else. */
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[+-]?[0-9]+");
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private BossSettingValue() {
    }

    /**
     * A number typed into a field that has a "not set" of its own, or {@code fallback} when the field
     * holds none: an empty field, a lone minus sign and anything that is not a whole number all mean
     * not set. Nothing is clamped here but to an int - a number past the int range is the furthest of
     * its sign - so the setter's own floor and ceiling decide what is kept, as in every other field.
     */
    public static int parseOr(String text, int fallback) {
        String value = text == null ? "" : text.trim();
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            return fallback;
        }
        return new BigInteger(value).max(INT_MIN).min(INT_MAX).intValue();
    }

    static int value(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
