package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** The countdown a downed boss puts up, what colour it is, and what the killing blow leaves. */
public final class SubGuiBossTuningHealthLink extends SubGuiBossTuningTopic {
    private static final int ANNOUNCE_FIELD = 1;
    private static final int COLOR_FIELD = 2;
    private static final int LETHAL_GUARD_FIELD = 3;
    private static final int DOWNED_SOUND_BUTTON = 4;
    private static final int DOWNED_PARTICLES_BUTTON = 5;
    private static final int REVIVE_SOUND_BUTTON = 6;
    private static final int REVIVE_PARTICLES_BUTTON = 7;

    public SubGuiBossTuningHealthLink(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.health_link", tuning);
    }

    @Override
    protected int rows() {
        return 7;
    }

    @Override
    protected void addRows() {
        addNumberField(ANNOUNCE_FIELD, "cnpcgeckoaddon.boss.tuning.announce_interval", nextRow(),
                tuning.healthLinkAnnounceIntervalTicks(), BossTuningSettings.MIN_HEALTH_LINK_ANNOUNCE,
                BossTuningSettings.MAX_HEALTH_LINK_ANNOUNCE, 20);

        // A colour is six hex digits rather than a number: a numbers-only field would leave
        // the builder converting FF6A5A into sixteen million by hand.
        int colorRow = nextRow();
        addLabel(new GuiLabel(COLOR_FIELD, "cnpcgeckoaddon.boss.tuning.downed_color",
                guiLeft + numberLabelX(), colorRow + numberLabelYOffset()));
        addTextField(new GuiTextFieldNop(COLOR_FIELD, this, guiLeft + numberFieldX(), colorRow,
                numberFieldWidth(), numberFieldHeight(), hex(tuning.healthLinkDownedColor())));

        addNumberField(LETHAL_GUARD_FIELD, "cnpcgeckoaddon.boss.tuning.lethal_guard", nextRow(),
                tuning.lethalGuardHealthTenths(), BossTuningSettings.MIN_LETHAL_GUARD_HEALTH,
                BossTuningSettings.MAX_LETHAL_GUARD_HEALTH, 10);
        addCueButton(DOWNED_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.downed_sound", nextRow(),
                tuning.healthLinkDownedSound());
        addCueButton(DOWNED_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tuning.downed_particles", nextRow(),
                tuning.healthLinkDownedParticles());
        addCueButton(REVIVE_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.revive_sound", nextRow(),
                tuning.healthLinkReviveSound());
        addCueButton(REVIVE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tuning.revive_particles", nextRow(),
                tuning.healthLinkReviveParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(ANNOUNCE_FIELD, tuning::setHealthLinkAnnounceIntervalTicks);
        applyNumberField(LETHAL_GUARD_FIELD, tuning::setLethalGuardHealthTenths);
        GuiTextFieldNop color = getTextField(COLOR_FIELD);
        if (color == null) {
            return;
        }
        // Anything that is not six hex digits snaps back rather than turning the line black.
        String typed = color.getValue().trim();
        if (typed.startsWith("#")) {
            typed = typed.substring(1);
        }
        try {
            tuning.setHealthLinkDownedColor(Integer.parseInt(typed, 16));
        } catch (NumberFormatException ignored) {
            // Left as it was.
        }
        color.setValue(hex(tuning.healthLinkDownedColor()));
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb);
    }
}
