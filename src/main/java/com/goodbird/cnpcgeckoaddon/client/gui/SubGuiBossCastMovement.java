package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

/** Whether the boss stands still or keeps walking while it casts, one row per ability. */
public final class SubGuiBossCastMovement extends SubGuiFieldScreen {
    private static final int FIRST_ABILITY_BUTTON = 100;
    private static final int LEAP_BUTTON = 90;
    private static final int FIRST_HINT_LABEL = 40;

    /**
     * Two columns, the way the warning and immunity screens list the same abilities. Eleven
     * rows since the cone strike: its choice and the leap's placeholder made twenty-one, one
     * more than two columns of ten, so the hints moved down under the eleventh row. The
     * platforms' choice filled the last slot of eleven; the hurricane's and the shadow copies'
     * choices make twenty-four with the placeholder, two columns of twelve, the seismic
     * waves' choice twenty-five, which opened a thirteenth row, and the vents' twenty-seven
     * with the rift's, which opens a fourteenth.
     */
    private static final int ROWS_PER_COLUMN = 14;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;
    /** Where the hints start: under the fourteenth row. */
    private static final int HINTS_Y = 336;
    private static final int DONE_Y = 388;
    private static final int HINT_COLOR = 0xA0A0A0;
    private static final int HINT_LINE_HEIGHT = 9;

    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossCastMovement(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        imageHeight = DONE_Y + 26;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.cast_move_title", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));

        for (int i = 0; i < BossPhaseData.CAST_ROOT_ABILITIES.length; i++) {
            addButton(new ThemeButton(this, FIRST_ABILITY_BUTTON + i, gridX(i), gridY(i),
                    COLUMN_WIDTH, 20, abilityLabel(i)));
        }
        // The leap fills the grid's last slot but takes no clicks: its crouch is rooted and
        // its flight free whatever a builder picks, so there is nothing here to choose. The
        // teleport is not listed at all - it lives on its own screen, not the ability list.
        int slot = BossPhaseData.CAST_ROOT_ABILITIES.length;
        GuiButtonNop leap = new ThemeButton(this, LEAP_BUTTON, gridX(slot), gridY(slot),
                COLUMN_WIDTH, 20, I18n.get(BossAbilityKind.LABELS[BossAbilityKind.LEAP]));
        leap.setEnabled(false);
        addButton(leap);

        int y = addWrappedText(FIRST_HINT_LABEL,
                "+ " + I18n.get("cnpcgeckoaddon.boss.cast_move_rooted")
                        + "   - " + I18n.get("cnpcgeckoaddon.boss.cast_move_free"), guiTop + HINTS_Y);
        y = addWrappedText(FIRST_HINT_LABEL + 10,
                I18n.get(BossAbilityKind.LABELS[BossAbilityKind.LEAP]) + ": "
                        + I18n.get("cnpcgeckoaddon.boss.cast_move_locked"), y + 2);
        addWrappedText(FIRST_HINT_LABEL + 20, I18n.get("cnpcgeckoaddon.boss.cast_move_hint"), y + 2);
        addDoneButton(guiLeft + 182, guiTop + DONE_Y, 60, 20);
    }

    private int gridX(int index) {
        return guiLeft + 8 + index / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
    }

    private int gridY(int index) {
        return guiTop + 24 + index % ROWS_PER_COLUMN * ROW_HEIGHT;
    }

    /** "+ Ground attack" while the boss casts it standing still, "- ..." while it walks. */
    private String abilityLabel(int index) {
        int ability = BossPhaseData.CAST_ROOT_ABILITIES[index];
        return (phase.isCastRooted(ability) ? "+ " : "- ") + I18n.get(BossAbilityKind.LABELS[ability]);
    }


    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_ABILITY_BUTTON;
        if (index >= 0 && index < BossPhaseData.CAST_ROOT_ABILITIES.length) {
            int ability = BossPhaseData.CAST_ROOT_ABILITIES[index];
            phase.setCastRooted(ability, !phase.isCastRooted(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(index));
        }
    }
}
