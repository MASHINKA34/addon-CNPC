package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A boss loaded from a preset and its health link group: taken out of it unless the load asks to keep
 * it, since a preset is made to go into more than one npc and two bosses from it in one group would
 * share their health or die together - and the line the presets screen shows about it.
 */
class BossPresetHealthLinkTest {

    @Test
    @DisplayName("a load drops the group it brought and says which, unless it keeps it")
    void theGroupGoesUnlessKept() {
        TeleportPathData dropped = linked("twins");
        assertEquals("twins", BossPresetManager.dropHealthLink(dropped, false));
        assertEquals("", dropped.getHealthLinkGroup());
        assertFalse(dropped.isHealthLinked());

        TeleportPathData kept = linked("twins");
        assertEquals("", BossPresetManager.dropHealthLink(kept, true), "nothing was taken away");
        assertEquals("twins", kept.getHealthLinkGroup());
        assertTrue(kept.isHealthLinked());
    }

    @Test
    @DisplayName("a boss in no group has nothing to drop or keep")
    void noGroupNothingToSay() {
        for (boolean keepLink : new boolean[]{false, true}) {
            TeleportPathData boss = new TeleportPathData();
            assertEquals("", BossPresetManager.dropHealthLink(boss, keepLink), "keepLink " + keepLink);
            assertEquals("", boss.getHealthLinkGroup());
        }
    }

    @Test
    @DisplayName("only the group goes: the link's mode and the rest wait for the day a group is set again")
    void theRestOfTheLinkStays() {
        TeleportPathData boss = linked("twins");
        boss.setHealthLinkMode(TeleportPathData.HEALTH_LINK_TOGETHER);
        boss.setHealthLinkRange(24);
        BossPresetManager.dropHealthLink(boss, false);
        assertEquals(TeleportPathData.HEALTH_LINK_TOGETHER, boss.getHealthLinkMode());
        assertEquals(24, boss.getHealthLinkRange());
    }

    @Test
    @DisplayName("the second line of a load names the group dropped or kept, and is not there without one")
    void theSecondLine() {
        BossPresetManager.Result dropped = loaded("twins", false);
        assertEquals("cnpcgeckoaddon.boss.preset_link_dropped", dropped.linkKey());
        assertEquals(List.of("twins"), dropped.linkArgs());

        BossPresetManager.Result kept = loaded("twins", true);
        assertEquals("cnpcgeckoaddon.boss.preset_link_kept", kept.linkKey());
        assertEquals(List.of("twins"), kept.linkArgs());

        for (boolean keepLink : new boolean[]{false, true}) {
            BossPresetManager.Result none = loaded("", keepLink);
            assertEquals("", none.linkKey(), "a preset in no group, keepLink " + keepLink);
            assertEquals(List.of(), none.linkArgs());
        }
        BossPresetManager.Result saved = new BossPresetManager.Result(BossPresetManager.Outcome.SAVED, "demo", "Boss",
                null, 10L, List.of());
        assertEquals("", saved.linkKey(), "only a load says anything about the group");
    }

    private static BossPresetManager.Result loaded(String group, boolean kept) {
        return new BossPresetManager.Result(BossPresetManager.Outcome.LOADED, "demo", "Boss", null, 10L, List.of(),
                group, kept);
    }

    private static TeleportPathData linked(String group) {
        TeleportPathData boss = new TeleportPathData();
        boss.setHealthLinkGroup(group);
        return boss;
    }
}
