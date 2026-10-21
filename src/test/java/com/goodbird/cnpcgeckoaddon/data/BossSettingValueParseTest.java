package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A number typed into a field with a "not set" of its own - the profile's first delay, the warning's
 * lead and width - read as that "not set" whenever the field holds no number, rather than as nought,
 * which is a setting of its own there.
 */
class BossSettingValueParseTest {

    private static final int UNSET = -1;

    @Test
    @DisplayName("nothing usable typed is the field's own not set")
    void noNumberIsUnset() {
        for (String typed : new String[]{"", " ", "-", "+", "abc", "1a", "1.5", "--1", "- 1", null}) {
            assertEquals(UNSET, BossSettingValue.parseOr(typed, UNSET), "'" + typed + "'");
        }
        assertEquals(BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN,
                BossSettingValue.parseOr("-", BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN));
        assertEquals(BossAbilityProfile.AS_BOSS, BossSettingValue.parseOr("abc", BossAbilityProfile.AS_BOSS));
    }

    @Test
    @DisplayName("a number is that number, unclamped: the setter's own range keeps what it keeps")
    void aNumberIsItself() {
        assertEquals(12, BossSettingValue.parseOr("12", UNSET));
        assertEquals(12, BossSettingValue.parseOr(" 12 ", UNSET), "spaces round it are no matter");
        assertEquals(0, BossSettingValue.parseOr("0", UNSET), "nought typed is nought");
        assertEquals(-1, BossSettingValue.parseOr("-1", 5));
        assertEquals(-7, BossSettingValue.parseOr("-7", UNSET));
        assertEquals(7, BossSettingValue.parseOr("+7", UNSET));
    }

    @Test
    @DisplayName("a number past an int is the furthest one of its sign, for the setter to clamp")
    void anOverflowIsTheFurthest() {
        assertEquals(Integer.MAX_VALUE, BossSettingValue.parseOr("99999999999", UNSET));
        assertEquals(Integer.MIN_VALUE, BossSettingValue.parseOr("-99999999999", UNSET));

        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setFirstCastDelayTicks(BossSettingValue.parseOr("99999999999",
                BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN));
        assertEquals(BossAbilityProfile.MAX_FIRST_CAST_DELAY, profile.getFirstCastDelayTicks());
        profile.setFirstCastDelayTicks(BossSettingValue.parseOr("-7", BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN));
        assertEquals(BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN, profile.getFirstCastDelayTicks(),
                "the clamp is the setter's");
    }
}
