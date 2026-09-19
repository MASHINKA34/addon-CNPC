package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/**
 * What a failed rift costs, each its own switch: the boss enraged, a hit on everyone fighting
 * within reach of it, the failure's potions on them, and the boss healing.
 */
public final class SubGuiBossRiftFail extends SubGuiFieldScreen {
    private static final int RAGE_BUTTON = 1;
    private static final int DAMAGE_FIELD = 2;
    private static final int RADIUS_FIELD = 3;
    private static final int HEAL_FIELD = 4;
    private static final int EFFECTS_BUTTON = 5;

    private static final int TITLE_LABEL = 30;
    private static final int HINT_LABEL = 40;
    private static final int WRAPPED_LABEL = 80;

    private static final int FIRST_ROW = 22;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 2;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    private static final int RIGHT_EDGE = 242;
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final String HINT = "cnpcgeckoaddon.boss.rift_fail_hint";

    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossRiftFail(BossPhaseData phase, int phaseIndex) {
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
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.rift_fail_title",
                phaseIndex), guiLeft + LABEL_X, guiTop + 7, 0xFFFFFF));
        int y = FIRST_ROW;
        rowLabel(RAGE_BUTTON, "cnpcgeckoaddon.boss.rift_fail_rage", y, TOGGLE_X);
        addButton(new GuiButtonYesNo(this, RAGE_BUTTON, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH,
                CONTROL_HEIGHT, rift.isFailRage()));
        y += ROW;
        rowLabel(DAMAGE_FIELD, "cnpcgeckoaddon.boss.rift_fail_arena", y, PAIR_X);
        number(DAMAGE_FIELD, guiLeft + PAIR_X, guiTop + y, rift.getFailArenaDamage(),
                0, BossRiftSettings.MAX_FAIL_ARENA_DAMAGE, 0);
        number(RADIUS_FIELD, guiLeft + PAIR_SECOND_X, guiTop + y, rift.getFailArenaRadius(),
                1, BossRiftSettings.MAX_FAIL_ARENA_RADIUS, 32);
        y += ROW;
        rowLabel(HEAL_FIELD, "cnpcgeckoaddon.boss.rift_fail_heal", y, PAIR_SECOND_X);
        number(HEAL_FIELD, guiLeft + PAIR_SECOND_X, guiTop + y, rift.getFailHealPercent(),
                0, BossRiftSettings.MAX_FAIL_HEAL_PERCENT, 0);
        y += ROW;
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + LABEL_X, guiTop + y, RIGHT_EDGE - LABEL_X,
                CONTROL_HEIGHT, "cnpcgeckoaddon.boss.effects_rift_fail"));
        addWrappedHint(HINT_LABEL, HINT, guiTop + hintY());
        addDoneButton(guiLeft + 182, guiTop + hintY() + wrappedHintHeight(HINT) + 6, 60, CONTROL_HEIGHT);
    }

    private static int hintY() {
        return FIRST_ROW + 4 * ROW + 4;
    }

    private void number(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, PAIR_WIDTH, CONTROL_HEIGHT, Integer.toString(value));
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
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(rift.getFailEffects(), "cnpcgeckoaddon.boss.effects_rift_fail"));
        } else if (button.id == RAGE_BUTTON) {
            rift.setFailRage(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        BossRiftSettings rift = phase.rift();
        applyNumberField(DAMAGE_FIELD, rift::setFailArenaDamage);
        applyNumberField(RADIUS_FIELD, rift::setFailArenaRadius);
        applyNumberField(HEAL_FIELD, rift::setFailHealPercent);
    }
}
