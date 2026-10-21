package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A boss' cue set to the ability's dust, with the dust handed in: the colour of the ability's profile
 * in the phase it was cast in, and for a profile with no colour exactly the dust the kind always had.
 */
class BossCueDustColourTest {

    private static BossParticleCue dustCue() {
        return new BossParticleCue(BossParticleCue.DUST_ID, 8);
    }

    @Test
    @DisplayName("an untouched profile hands the cue the very dust its kind always spat")
    void anUntouchedProfileIsTheKindsDust() {
        BossPhaseData phase = new TeleportPathData().getPhase(0);
        BossParticleCue cue = dustCue();
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            assertSame(cue.resolve(kind), cue.resolve(BossTelegraphUtil.dust(phase, kind)), "kind " + kind);
            assertSame(cue.resolve(kind),
                    cue.resolve(BossTelegraphPaint.Settings.of(new TeleportPathData(), phase, kind).dust(kind)),
                    "kind " + kind + " through the cast's snapshot");
        }
    }

    @Test
    @DisplayName("a profile's colour is the cue's dust, and only that ability's")
    void aProfilesColourIsTheCuesDust() {
        TeleportPathData data = new TeleportPathData();
        BossPhaseData phase = data.getPhase(0);
        phase.profile(BossAbilityKind.GEYSER).setTelegraphColor(0xFF2020);
        BossParticleCue cue = dustCue();

        DustParticleOptions red = BossTelegraphUtil.dust(phase, BossAbilityKind.GEYSER);
        assertSame(red, cue.resolve(red));
        assertNotSame(cue.resolve(BossAbilityKind.GEYSER), red, "not the kind's own any more");
        assertSame(red, cue.resolve(BossTelegraphPaint.Settings.of(data, phase, BossAbilityKind.GEYSER)
                .dust(BossAbilityKind.GEYSER)), "the cast's snapshot says the same");
        assertSame(cue.resolve(BossAbilityKind.VENT), cue.resolve(BossTelegraphUtil.dust(phase, BossAbilityKind.VENT)),
                "another ability keeps its kind's dust");
    }

    @Test
    @DisplayName("no dust handed in is no dust thrown, as a cue with no ability behind it")
    void noDustIsNothing() {
        assertNull(dustCue().resolve((DustParticleOptions) null));
    }

    @Test
    @DisplayName("a run breaking on a chain puffs in the dash's colour once it has one, the tether's otherwise")
    void theChainPuff() {
        BossPhaseData phase = new TeleportPathData().getPhase(0);
        assertSame(BossTelegraphUtil.dust(BossAbilityKind.TETHER), BossDashRuntime.chainDust(phase),
                "untouched, the tether's as before");
        assertSame(BossTelegraphUtil.dust(BossAbilityKind.TETHER), BossDashRuntime.chainDust(null));

        phase.profile(BossAbilityKind.TETHER).setTelegraphColor(0x20FF20);
        assertSame(BossTelegraphUtil.dust(BossAbilityKind.TETHER), BossDashRuntime.chainDust(phase),
                "the chain puff follows the dash's profile, not the tether's");

        phase.profile(BossAbilityKind.DASH).setTelegraphColor(0xFF2020);
        assertSame(BossTelegraphUtil.dust(phase, BossAbilityKind.DASH), BossDashRuntime.chainDust(phase));
        assertNotSame(BossTelegraphUtil.dust(BossAbilityKind.TETHER), BossDashRuntime.chainDust(phase));
    }
}
