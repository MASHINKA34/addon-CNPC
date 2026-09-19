package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/**
 * The rift's own minions: the clone they are stood up from, how many, where, and whether they go
 * when the rift closes. Kept apart from the phase's summon on purpose - these fight in the pocket
 * dimension, and the summon's caps and points have nothing to say about them.
 */
public final class SubGuiBossRiftMinions extends SubGuiFieldScreen {
    private static final int CLONE_NAME_FIELD = 1;
    private static final int CLONE_TAB_FIELD = 2;
    private static final int COUNT_FIELD = 3;
    private static final int RADIUS_FIELD = 4;
    private static final int POINTS_BUTTON = 5;
    private static final int REMOVE_BUTTON = 6;

    private static final int TITLE_LABEL = 30;
    private static final int HINT_LABEL = 40;
    private static final int WRAPPED_LABEL = 80;

    private static final int FIRST_ROW = 22;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 2;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    private static final int RIGHT_EDGE = 242;
    private static final int NAME_X = 108;
    private static final int NAME_WIDTH = 86;
    private static final int TAB_X = 198;
    private static final int TAB_WIDTH = 44;
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final String HINT = "cnpcgeckoaddon.boss.rift_minions_hint";

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossRiftMinions(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        imageHeight = hintY() + wrappedHintHeight(HINT) + 6 + CONTROL_HEIGHT + 8;
        super.init();
        BossRiftSettings rift = phase.rift();
        wrappedLabel = WRAPPED_LABEL;
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.rift_minions_title",
                phaseIndex), guiLeft + LABEL_X, guiTop + 7, 0xFFFFFF));
        int y = FIRST_ROW;
        rowLabel(CLONE_NAME_FIELD, "cnpcgeckoaddon.boss.rift_minion_clone", y, NAME_X);
        addTextField(new GuiTextFieldNop(CLONE_NAME_FIELD, this, guiLeft + NAME_X, guiTop + y, NAME_WIDTH,
                CONTROL_HEIGHT, rift.getMinionCloneName()));
        number(CLONE_TAB_FIELD, guiLeft + TAB_X, guiTop + y, TAB_WIDTH, rift.getMinionCloneTab(),
                1, BossRiftSettings.MAX_CLONE_TAB, 1);
        y += ROW;
        rowLabel(COUNT_FIELD, "cnpcgeckoaddon.boss.rift_minion_count", y, PAIR_X);
        number(COUNT_FIELD, guiLeft + PAIR_X, guiTop + y, PAIR_WIDTH, rift.getMinionCount(),
                1, BossRiftSettings.MAX_MINION_COUNT, 3);
        number(RADIUS_FIELD, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH, rift.getMinionRadius(),
                1, BossRiftSettings.MAX_MINION_RADIUS, 6);
        y += ROW;
        addButton(new GuiButtonNop(this, POINTS_BUTTON, guiLeft + LABEL_X, guiTop + y, RIGHT_EDGE - LABEL_X,
                CONTROL_HEIGHT, "cnpcgeckoaddon.boss.rift_minion_points"));
        y += ROW;
        rowLabel(REMOVE_BUTTON, "cnpcgeckoaddon.boss.rift_minion_remove", y, TOGGLE_X);
        addButton(new GuiButtonYesNo(this, REMOVE_BUTTON, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH,
                CONTROL_HEIGHT, rift.isMinionRemoveOnEnd()));
        addWrappedHint(HINT_LABEL, HINT, guiTop + hintY());
        addDoneButton(guiLeft + 182, guiTop + hintY() + wrappedHintHeight(HINT) + 6, 60, CONTROL_HEIGHT);
    }

    private static int hintY() {
        return FIRST_ROW + 4 * ROW + 4;
    }

    private void number(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, CONTROL_HEIGHT, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /** A row's label, wrapped onto two lines rather than run under the control beside it. */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new GuiLabel(id, key, guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
            return;
        }
        int top = guiTop + y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new GuiLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossRiftSettings rift = phase.rift();
        if (button.id == POINTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossMinionSpawnList(npc, phase, phaseIndex, rift.getMinionPoints(),
                    "cnpcgeckoaddon.boss.rift_minion_points_title", true));
        } else if (button.id == REMOVE_BUTTON) {
            rift.setMinionRemoveOnEnd(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        BossRiftSettings rift = phase.rift();
        GuiTextFieldNop name = getTextField(CLONE_NAME_FIELD);
        if (name != null) {
            rift.setMinionCloneName(name.getValue());
        }
        applyNumberField(CLONE_TAB_FIELD, rift::setMinionCloneTab);
        applyNumberField(COUNT_FIELD, rift::setMinionCount);
        applyNumberField(RADIUS_FIELD, rift::setMinionRadius);
    }
}
