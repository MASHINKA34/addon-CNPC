package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossShadowSettings;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;

/** The blast wave's length, whether a phase change takes the copies with it, and the seven noises and puffs. */
public final class SubGuiBossShadowTuning extends SubGuiBossAbilityTuning {
    private static final int VFX_TICKS_FIELD = 1;
    private static final int CLEAR_PHASE_BUTTON = 2;
    private static final int SPAWN_SOUND_BUTTON = 3;
    private static final int SPAWN_PARTICLES_BUTTON = 4;
    private static final int ABSORB_SOUND_BUTTON = 5;
    private static final int ABSORB_PARTICLES_BUTTON = 6;
    private static final int BLAST_SOUND_BUTTON = 7;
    private static final int BLAST_PARTICLES_BUTTON = 8;
    private static final int VANISH_PARTICLES_BUTTON = 9;

    private final BossShadowSettings shadow;

    public SubGuiBossShadowTuning(BossShadowSettings shadow) {
        super("cnpcgeckoaddon.boss.shadow_tuning_title");
        this.shadow = shadow;
    }

    @Override
    protected int rows() {
        return 9;
    }

    @Override
    protected void addRows() {
        addNumberField(VFX_TICKS_FIELD, "cnpcgeckoaddon.boss.shadow_blast_vfx_ticks", nextRow(),
                shadow.getBlastVfxTicks(), BossShadowSettings.MIN_BLAST_VFX_TICKS,
                BossShadowSettings.MAX_BLAST_VFX_TICKS, 20);
        addYesNo(CLEAR_PHASE_BUTTON, "cnpcgeckoaddon.boss.shadow_clear_phase", nextRow(),
                shadow.isClearOnPhaseChange());
        addCueButton(SPAWN_SOUND_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_spawn", nextRow(),
                shadow.getSpawnSound());
        addCueButton(SPAWN_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_spawn_particles", nextRow(),
                shadow.getSpawnParticles());
        addCueButton(ABSORB_SOUND_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_absorb", nextRow(),
                shadow.getAbsorbSound());
        addCueButton(ABSORB_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_absorb_particles", nextRow(),
                shadow.getAbsorbParticles());
        addCueButton(BLAST_SOUND_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_blast", nextRow(),
                shadow.getBlastSound());
        addCueButton(BLAST_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_blast_particles", nextRow(),
                shadow.getBlastParticles());
        addCueButton(VANISH_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.shadow_cue_vanish", nextRow(),
                shadow.getVanishParticles());
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == CLEAR_PHASE_BUTTON) {
            shadow.setClearOnPhaseChange(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(VFX_TICKS_FIELD, shadow::setBlastVfxTicks);
    }
}
