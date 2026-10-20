package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the fight calls an ability: the name its profile gives it in a phase, typed by the
 * builder and shown as typed, or the kind's own, translated for whoever reads it.
 */
class BossAbilityNamesTest {

    @Test
    @DisplayName("an ability with no name of its own is called by its kind's translated name")
    void theStandardName() {
        BossPhaseData phase = new BossPhaseData();
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            assertKey(BossAbilityKind.LABELS[kind], BossAbilityNames.name(phase, kind));
        }
        assertKey(BossAbilityKind.LABELS[BossAbilityKind.AREA], BossAbilityNames.name(null, BossAbilityKind.AREA));
    }

    @Test
    @DisplayName("a profile's name is shown as typed, for that ability in that phase only")
    void theProfilesName() {
        BossPhaseData phase = new BossPhaseData();
        phase.profile(BossAbilityKind.AREA).setDisplayName("Инферно");

        MutableComponent name = BossAbilityNames.name(phase, BossAbilityKind.AREA);
        assertEquals("Инферно", name.getString());
        assertKey(BossAbilityKind.LABELS[BossAbilityKind.LINE], BossAbilityNames.name(phase, BossAbilityKind.LINE));
        assertKey(BossAbilityKind.LABELS[BossAbilityKind.AREA],
                BossAbilityNames.name(new BossPhaseData(), BossAbilityKind.AREA));

        phase.profile(BossAbilityKind.AREA).setDisplayName("   ");
        assertKey(BossAbilityKind.LABELS[BossAbilityKind.AREA], BossAbilityNames.name(phase, BossAbilityKind.AREA));
    }

    @Test
    @DisplayName("every call hands out a fresh component, so styling one leaves the next alone")
    void aFreshComponentEachTime() {
        BossPhaseData phase = new BossPhaseData();
        phase.profile(BossAbilityKind.HUNT).setDisplayName("Chase");
        BossAbilityNames.name(phase, BossAbilityKind.HUNT).withStyle(ChatFormatting.RED);
        assertNull(BossAbilityNames.name(phase, BossAbilityKind.HUNT).getStyle().getColor());
    }

    @Test
    @DisplayName("a kind outside the list is called by its number rather than throwing")
    void anUnknownKind() {
        assertEquals("99", BossAbilityNames.name(new BossPhaseData(), 99).getString());
        assertEquals("-1", BossAbilityNames.name(null, -1).getString());
    }

    private static void assertKey(String key, Component name) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, name.getContents());
        assertEquals(key, contents.getKey());
    }
}
