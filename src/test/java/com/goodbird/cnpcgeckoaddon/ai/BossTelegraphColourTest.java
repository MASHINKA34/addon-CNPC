package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What colour, style and width a warning is drawn with once abilities have profiles: the kind's
 * own colour and the boss' own style for an untouched profile, the profile's where it has one,
 * and never one ability's look leaking onto another.
 */
class BossTelegraphColourTest {

    private static final int OWNER = 7;

    private static BossTelegraphPaint paint(TeleportPathData data, BossPhaseData phase, int kind) {
        return BossTelegraphPaint.of(BossTelegraphPaint.Settings.of(data, phase, kind), OWNER,
                BossTelegraphPaint.CHANNEL_CAST, kind, BossTelegraphPaint.NO_END);
    }

    @Test
    @DisplayName("an untouched profile draws in the kind's own colour, exactly as before profiles")
    void withoutAProfileTheKindsColour() {
        TeleportPathData data = new TeleportPathData();
        BossPhaseData phase = data.getPhase(0);
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            assertEquals(BossTelegraphUtil.textColor(kind), paint(data, phase, kind).rgb(), "kind " + kind);
            assertEquals(BossTelegraphUtil.textColor(kind), BossTelegraphUtil.textColor(phase, kind));
            assertSame(BossTelegraphUtil.dust(kind), BossTelegraphUtil.dust(phase, kind),
                    "the prebuilt dust, not a new one per mark");
            assertSame(BossTelegraphUtil.dust(kind), paint(data, phase, kind).dust());
        }
        assertEquals(BossTelegraphUtil.textColor(BossAbilityKind.AREA),
                BossTelegraphUtil.textColor((BossPhaseData) null, BossAbilityKind.AREA), "no phase is the kind's colour");
        assertEquals(BossTelegraphPaint.Settings.of(data), BossTelegraphPaint.Settings.of(data, phase, BossAbilityKind.AREA),
                "an untouched profile leaves the boss-wide settings exactly as they were");
    }

    @Test
    @DisplayName("a profile's colour is the mark's, its dust's and its text's, and only that ability's")
    void withAProfileItsColour() {
        TeleportPathData data = new TeleportPathData();
        BossPhaseData phase = data.getPhase(0);
        phase.profile(BossAbilityKind.AREA).setTelegraphColor(0xFF2020);

        BossTelegraphPaint area = paint(data, phase, BossAbilityKind.AREA);
        assertEquals(0xFF2020, area.rgb());
        assertEquals(0xFF2020, BossTelegraphUtil.textColor(phase, BossAbilityKind.AREA));
        assertColour(0xFF2020, 1.0F, area.dust());
        assertColour(0xFF2020, 1.0F, BossTelegraphUtil.dust(phase, BossAbilityKind.AREA));
        assertSame(area.dust(), BossTelegraphUtil.dust(phase, BossAbilityKind.AREA), "one dust per colour, built once");
        // The faded half keeps the profile's hue at the boss' brightness.
        assertColour(0xFF2020, data.tuning().telegraphFadedPercent() / 100.0F, area.fadedDust());

        assertEquals(BossTelegraphUtil.textColor(BossAbilityKind.LINE), paint(data, phase, BossAbilityKind.LINE).rgb(),
                "another ability in the same phase keeps its own colour");
        assertEquals(BossTelegraphUtil.textColor(BossAbilityKind.AREA),
                paint(data, data.getPhase(1), BossAbilityKind.AREA).rgb(), "and so does the same ability in another phase");

        phase.profile(BossAbilityKind.AREA).setTelegraphColor(BossAbilityProfile.COLOR_OF_KIND);
        assertEquals(BossTelegraphUtil.textColor(BossAbilityKind.AREA), paint(data, phase, BossAbilityKind.AREA).rgb(),
                "cleared, it is the kind's colour again");
    }

    @Test
    @DisplayName("black is a colour of its own, not the kind's")
    void blackIsAColour() {
        TeleportPathData data = new TeleportPathData();
        BossPhaseData phase = data.getPhase(0);
        phase.profile(BossAbilityKind.HUNT).setTelegraphColor(0x000000);
        assertEquals(0x000000, paint(data, phase, BossAbilityKind.HUNT).rgb());
        assertNotEquals(BossTelegraphUtil.textColor(BossAbilityKind.HUNT), BossTelegraphUtil.textColor(phase,
                BossAbilityKind.HUNT));
    }

    @Test
    @DisplayName("a profile's style and width go over the boss' own; motion, fill and the long marks stay the boss'")
    void styleAndWidthOverTheBoss() {
        TeleportPathData data = new TeleportPathData();
        data.setTelegraphLineStyle(TelegraphLineStyles.PARTICLES);
        data.setTelegraphLineWidth(3);
        data.setTelegraphLineFill(40);
        BossPhaseData phase = data.getPhase(0);
        phase.profile(BossAbilityKind.LINE).setTelegraphStyle(TelegraphLineStyles.SOLID);
        phase.profile(BossAbilityKind.LINE).setTelegraphLineWidth(9);

        BossTelegraphPaint.Settings line = BossTelegraphPaint.Settings.of(data, phase, BossAbilityKind.LINE);
        assertEquals(TelegraphLineStyles.SOLID, line.style());
        assertEquals(9, line.widthTenths());
        assertEquals(40, line.fillPercent(), "the fill is the boss'");
        assertEquals(data.getTelegraphLineMotion(), line.motion());
        assertEquals(data.isTelegraphLineLasting(), line.lasting());
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, line.colour(), "a style alone leaves the colour the kind's");
        assertTrue(paint(data, phase, BossAbilityKind.LINE).lines(), "the line strike is drawn as a band");
        assertFalse(paint(data, phase, BossAbilityKind.AREA).lines(), "while the boss' particles stay on the rest");

        // The other way round: a boss drawing bands, one ability forced back to plain dust.
        data.setTelegraphLineStyle(TelegraphLineStyles.DASHED);
        phase.profile(BossAbilityKind.AREA).setTelegraphStyle(TelegraphLineStyles.PARTICLES);
        assertFalse(paint(data, phase, BossAbilityKind.AREA).lines());
        assertTrue(paint(data, phase, BossAbilityKind.MELEE).lines());
        assertEquals(3, BossTelegraphPaint.Settings.of(data, phase, BossAbilityKind.MELEE).widthTenths());
    }

    private static void assertColour(int rgb, float brightness, DustParticleOptions dust) {
        Vector3f colour = dust.getColor();
        assertEquals((rgb >> 16 & 0xFF) / 255.0F * brightness, colour.x(), 1.0E-5F);
        assertEquals((rgb >> 8 & 0xFF) / 255.0F * brightness, colour.y(), 1.0E-5F);
        assertEquals((rgb & 0xFF) / 255.0F * brightness, colour.z(), 1.0E-5F);
    }
}
