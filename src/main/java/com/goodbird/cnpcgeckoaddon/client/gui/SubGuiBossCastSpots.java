package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

import java.util.List;

/**
 * Where each ability sends the boss before it casts: one row per ability of the rotation,
 * with the way it gets there on the row and everything else about the spot behind its
 * "..." button.
 *
 * <p>The rows are the rotation table itself, in its own order, so a spot edited here is
 * the one the fight reads for that ability and no other.</p>
 */
public final class SubGuiBossCastSpots extends SubGuiFieldScreen {
    private static final int TITLE_LABEL = 30;
    private static final int FIRST_HINT_LABEL = 40;
    private static final int FIRST_MODE_BUTTON = 100;
    private static final int FIRST_EDIT_BUTTON = 200;

    private static final int FIRST_ROW_Y = 24;
    private static final int ROW_HEIGHT = 22;
    private static final int MODE_BUTTON_X = 110;
    private static final int MODE_BUTTON_WIDTH = 92;
    private static final int EDIT_BUTTON_X = 206;
    private static final int EDIT_BUTTON_WIDTH = 36;
    /** Room for the hint under the rows, and the Done button under that. */
    private static final int FOOTER_HEIGHT = 66;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final List<BossAbility> rows = BossAbility.ROTATION;

    public SubGuiBossCastSpots(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // Nineteen rows do not fit the panel, so the screen is as tall as it needs and scrolls.
        imageHeight = FIRST_ROW_Y + rows.size() * ROW_HEIGHT + FOOTER_HEIGHT;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.cast_spots_phase", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int i = 0; i < rows.size(); i++) {
            BossAbility ability = rows.get(i);
            int y = guiTop + FIRST_ROW_Y + i * ROW_HEIGHT;
            addLabel(new GuiLabel(FIRST_MODE_BUTTON + i, BossAbilityKind.LABELS[ability.kind()],
                    guiLeft + 8, y + 6));
            addButton(new GuiButtonNop(this, FIRST_MODE_BUTTON + i, guiLeft + MODE_BUTTON_X, y,
                    MODE_BUTTON_WIDTH, 20, BossCastSpot.MODE_LABELS, ability.castSpot(phase).getMode()));
            addButton(new GuiButtonNop(this, FIRST_EDIT_BUTTON + i, guiLeft + EDIT_BUTTON_X, y,
                    EDIT_BUTTON_WIDTH, 20, "..."));
        }
        addWrappedHint(FIRST_HINT_LABEL, "cnpcgeckoaddon.boss.cast_spot_hint",
                guiTop + FIRST_ROW_Y + rows.size() * ROW_HEIGHT + 4);
        addDoneButton(guiLeft + 182, guiTop + imageHeight - 26, 60, 20);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int modeRow = button.id - FIRST_MODE_BUTTON;
        if (modeRow >= 0 && modeRow < rows.size()) {
            rows.get(modeRow).castSpot(phase).setMode(button.getValue());
            return;
        }
        int editRow = button.id - FIRST_EDIT_BUTTON;
        if (editRow >= 0 && editRow < rows.size()) {
            setSubGui(new SubGuiBossCastSpot(npc, phase, phaseIndex, rows.get(editRow)));
        }
    }
}
