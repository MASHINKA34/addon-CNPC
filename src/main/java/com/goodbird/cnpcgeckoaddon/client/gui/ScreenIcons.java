package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeIcons;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Which icon the theme puts before each screen's title: the ability the screen sets up, or the
 * section of the boss menus it belongs to.
 *
 * <p>One table rather than a method on every screen, so which screen shows what reads in one
 * place. A screen missing from it - one no section's icon fits, like the rage timer - keeps its
 * title strip and goes without an icon.</p>
 */
final class ScreenIcons {

    private static final Map<Class<? extends Screen>, Integer> ICONS = Map.ofEntries(
            // An ability's own screens and its fine-tuning page show the ability.
            entry(SubGuiBossAreaAttack.class, BossAbilityKind.AREA),
            entry(SubGuiBossRangedAttack.class, BossAbilityKind.RANGED),
            entry(SubGuiNpcRanged.class, BossAbilityKind.RANGED),
            entry(SubGuiBossMeleeAttack.class, BossAbilityKind.MELEE),
            entry(SubGuiBossFluidSpit.class, BossAbilityKind.FLUID),
            entry(SubGuiBossFluidSpitTuning.class, BossAbilityKind.FLUID),
            entry(SubGuiBossHook.class, BossAbilityKind.HOOK),
            entry(SubGuiBossHookTuning.class, BossAbilityKind.HOOK),
            entry(SubGuiBossCapture.class, BossAbilityKind.CAPTURE),
            entry(SubGuiBossCaptureTuning.class, BossAbilityKind.CAPTURE),
            entry(SubGuiBossSummon.class, BossAbilityKind.SUMMON),
            entry(SubGuiBossSummonTuning.class, BossAbilityKind.SUMMON),
            entry(SubGuiBossMinions.class, BossAbilityKind.SUMMON),
            entry(SubGuiBossMinionSpawnSettings.class, BossAbilityKind.SUMMON),
            entry(SubGuiBossLeap.class, BossAbilityKind.LEAP),
            entry(SubGuiBossLeapImpact.class, BossAbilityKind.LEAP),
            entry(SubGuiBossLeapTuning.class, BossAbilityKind.LEAP),
            entry(SubGuiBossLineAttack.class, BossAbilityKind.LINE),
            entry(SubGuiBossExplosion.class, BossAbilityKind.BLAST),
            entry(SubGuiBossGeyser.class, BossAbilityKind.GEYSER),
            entry(SubGuiBossGeyserResidue.class, BossAbilityKind.GEYSER),
            entry(SubGuiBossGeyserSky.class, BossAbilityKind.GEYSER),
            entry(SubGuiBossGeyserTuning.class, BossAbilityKind.GEYSER),
            entry(SubGuiBossBoulder.class, BossAbilityKind.BOULDER),
            entry(SubGuiBossBoulderTuning.class, BossAbilityKind.BOULDER),
            entry(SubGuiBossBoulderRain.class, BossAbilityKind.BOULDER_RAIN),
            entry(SubGuiBossBoulderRainTuning.class, BossAbilityKind.BOULDER_RAIN),
            entry(SubGuiBossTether.class, BossAbilityKind.TETHER),
            entry(SubGuiBossTetherTuning.class, BossAbilityKind.TETHER),
            entry(SubGuiBossGravity.class, BossAbilityKind.GRAVITY),
            entry(SubGuiBossGravityTuning.class, BossAbilityKind.GRAVITY),
            entry(SubGuiBossMark.class, BossAbilityKind.MARK),
            entry(SubGuiBossMarkTuning.class, BossAbilityKind.MARK),
            entry(SubGuiBossCover.class, BossAbilityKind.COVER),
            entry(SubGuiBossCoverTuning.class, BossAbilityKind.COVER),
            entry(SubGuiBossHazard.class, BossAbilityKind.HAZARD),
            entry(SubGuiBossHazardTuning.class, BossAbilityKind.HAZARD),
            entry(SubGuiBossHunt.class, BossAbilityKind.HUNT),
            entry(SubGuiBossHuntTuning.class, BossAbilityKind.HUNT),
            entry(SubGuiBossBeam.class, BossAbilityKind.BEAM),
            entry(SubGuiBossBeamTuning.class, BossAbilityKind.BEAM),
            entry(SubGuiBossCocoon.class, BossAbilityKind.COCOON),
            entry(SubGuiBossCocoonTuning.class, BossAbilityKind.COCOON),
            entry(SubGuiBossDash.class, BossAbilityKind.DASH),
            entry(SubGuiBossDashTuning.class, BossAbilityKind.DASH),
            entry(SubGuiBossCone.class, BossAbilityKind.CONE),
            entry(SubGuiBossConeTuning.class, BossAbilityKind.CONE),
            entry(SubGuiBossPlatform.class, BossAbilityKind.PLATFORM),
            entry(SubGuiBossPlatformTuning.class, BossAbilityKind.PLATFORM),
            entry(SubGuiBossHurricane.class, BossAbilityKind.HURRICANE),
            entry(SubGuiBossHurricaneTuning.class, BossAbilityKind.HURRICANE),
            entry(SubGuiBossShadow.class, BossAbilityKind.SHADOW),
            entry(SubGuiBossShadowAbilities.class, BossAbilityKind.SHADOW),
            entry(SubGuiBossShadowTuning.class, BossAbilityKind.SHADOW),
            entry(SubGuiBossSeismic.class, BossAbilityKind.SEISMIC),
            entry(SubGuiBossSeismicTuning.class, BossAbilityKind.SEISMIC),
            entry(SubGuiBossRift.class, BossAbilityKind.RIFT),
            entry(SubGuiBossRiftAbilities.class, BossAbilityKind.RIFT),
            entry(SubGuiBossRiftCrystals.class, BossAbilityKind.RIFT),
            entry(SubGuiBossRiftFail.class, BossAbilityKind.RIFT),
            entry(SubGuiBossRiftMinions.class, BossAbilityKind.RIFT),
            entry(SubGuiBossRiftTuning.class, BossAbilityKind.RIFT),
            entry(SubGuiBossVent.class, BossAbilityKind.VENT),
            entry(SubGuiBossVentTuning.class, BossAbilityKind.VENT),

            // The sections of the boss menus.
            entry(SubGuiBossPhaseList.class, ThemeIcons.PHASES),
            entry(SubGuiBossPhase.class, ThemeIcons.PHASES),
            entry(SubGuiBossTotems.class, ThemeIcons.TOTEMS),
            entry(SubGuiBossTotemList.class, ThemeIcons.TOTEMS),
            entry(SubGuiBossTotemEntry.class, ThemeIcons.TOTEMS),
            entry(SubGuiBossTotemVulnerability.class, ThemeIcons.TOTEMS),
            entry(SubGuiBossTuningTotems.class, ThemeIcons.TOTEMS),
            entry(SubGuiBossChest.class, ThemeIcons.CHEST),
            entry(SubGuiBossChestEntry.class, ThemeIcons.CHEST),
            entry(SubGuiBossChestLoot.class, ThemeIcons.CHEST),
            entry(SubGuiBossChestPlace.class, ThemeIcons.CHEST),
            entry(SubGuiBossTargeting.class, ThemeIcons.AGGRO_ZONE),
            entry(SubGuiBossAggroZone.class, ThemeIcons.AGGRO_ZONE),
            entry(SubGuiBossBarStyle.class, ThemeIcons.BOSS_BAR),
            entry(SubGuiBossHealthLink.class, ThemeIcons.HEALTH_LINK),
            entry(SubGuiBossTuningHealthLink.class, ThemeIcons.HEALTH_LINK),
            entry(SubGuiBossTuning.class, ThemeIcons.TUNING),
            entry(SubGuiBossTuningRotation.class, ThemeIcons.TUNING),
            entry(SubGuiBossTuningWaves.class, ThemeIcons.TUNING),
            entry(SubGuiBossTuningDeath.class, ThemeIcons.TUNING),
            entry(SubGuiBossBarrierTuning.class, ThemeIcons.TUNING),
            entry(SubGuiBossTeleport.class, ThemeIcons.TELEPORT_PATHS),
            entry(SubGuiBossEffectList.class, ThemeIcons.EFFECTS),
            entry(SubGuiBossEffect.class, ThemeIcons.EFFECTS),
            entry(SubGuiBossCaptureEffects.class, ThemeIcons.EFFECTS),
            entry(SubGuiBossCocoonEffects.class, ThemeIcons.EFFECTS),
            entry(SubGuiBossCastSpots.class, ThemeIcons.POINTS),
            entry(SubGuiBossCastSpot.class, ThemeIcons.POINTS),
            entry(SubGuiBossMinionSpawnList.class, ThemeIcons.POINTS),
            entry(SubGuiBossMinionSpawnPoint.class, ThemeIcons.POINTS),
            entry(SubGuiBossConeAimList.class, ThemeIcons.POINTS),
            entry(SubGuiBossConeAimPoint.class, ThemeIcons.POINTS),
            entry(SubGuiBossPlatformZoneList.class, ThemeIcons.POINTS),
            entry(SubGuiBossPlatformZone.class, ThemeIcons.POINTS),
            entry(SubGuiBossRiftCrystalList.class, ThemeIcons.POINTS),
            entry(SubGuiBossRiftCrystalPoint.class, ThemeIcons.POINTS),
            entry(SubGuiBossVentZoneList.class, ThemeIcons.POINTS),
            entry(SubGuiBossVentZone.class, ThemeIcons.POINTS),
            entry(SubGuiBossSoundCue.class, ThemeIcons.CUES),
            entry(SubGuiBossParticleCue.class, ThemeIcons.CUES),
            entry(SubGuiSoundReaction.class, ThemeIcons.CUES),
            entry(SubGuiNpcImmunity.class, ThemeIcons.IMMUNITIES),
            entry(SubGuiNpcDamageResistList.class, ThemeIcons.IMMUNITIES),
            entry(SubGuiNpcDamageResistEntry.class, ThemeIcons.IMMUNITIES),
            entry(SubGuiBossInvulnerable.class, ThemeIcons.IMMUNITIES),
            entry(SubGuiBossTelegraph.class, ThemeIcons.TELEGRAPH),
            entry(SubGuiBossTelegraphAbilities.class, ThemeIcons.TELEGRAPH),
            entry(SubGuiBossTuningTelegraph.class, ThemeIcons.TELEGRAPH),
            entry(SubGuiBossCombos.class, ThemeIcons.COMBOS),
            entry(SubGuiBossTuningCombos.class, ThemeIcons.COMBOS),
            entry(SubGuiBossFinish.class, ThemeIcons.FINISH),
            entry(SubGuiNpcCarry.class, ThemeIcons.CARRY));

    private ScreenIcons() {
    }

    /** The icon before this screen's title, or {@link ThemeIcons#NONE}. */
    static int of(Screen screen) {
        return ICONS.getOrDefault(screen.getClass(), ThemeIcons.NONE);
    }
}
