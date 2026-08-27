package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Whether the boss stands still or keeps walking while it casts, one row per ability. */
public final class SubGuiBossCastMovement extends GuiBasic {
    private static final int FIRST_ABILITY_BUTTON = 100;
    private static final int LEAP_BUTTON = 90;
    private static final int FIRST_HINT_LABEL = 40;

    /** Two columns, the way the warning and immunity screens list the same abilities. */
    private static final int ROWS_PER_COLUMN = 5;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;
    private static final int HINT_COLOR = 0xA0A0A0;
    private static final int HINT_LINE_HEIGHT = 9;

    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossCastMovement(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 230;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.cast_move_title", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));

        for (int i = 0; i < BossPhaseData.CAST_ROOT_ABILITIES.length; i++) {
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + i, gridX(i), gridY(i),
                    COLUMN_WIDTH, 20, abilityLabel(i)));
        }
        // The leap fills the grid's last slot but takes no clicks: its crouch is rooted and
        // its flight free whatever a builder picks, so there is nothing here to choose. The
        // teleport is not listed at all - it lives on its own screen, not the ability list.
        int slot = BossPhaseData.CAST_ROOT_ABILITIES.length;
        GuiButtonNop leap = new GuiButtonNop(this, LEAP_BUTTON, gridX(slot), gridY(slot),
                COLUMN_WIDTH, 20, I18n.get(BossAbilityKind.LABELS[BossAbilityKind.LEAP]));
        leap.setEnabled(false);
        addButton(leap);

        int y = addWrappedHint(FIRST_HINT_LABEL,
                "+ " + I18n.get("cnpcgeckoaddon.boss.cast_move_rooted")
                        + "   - " + I18n.get("cnpcgeckoaddon.boss.cast_move_free"), guiTop + 140);
        y = addWrappedHint(FIRST_HINT_LABEL + 10,
                I18n.get(BossAbilityKind.LABELS[BossAbilityKind.LEAP]) + ": "
                        + I18n.get("cnpcgeckoaddon.boss.cast_move_locked"), y + 2);
        addWrappedHint(FIRST_HINT_LABEL + 20, I18n.get("cnpcgeckoaddon.boss.cast_move_hint"), y + 2);
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 202, 60, 20,
                "gui.done", button -> close()));
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

    /**
     * A GuiLabel draws one line and never clips it, so a hint too wide for the panel is split
     * into its own labels here rather than running off the edge of the background.
     *
     * @return the y the next thing down may start at
     */
    private int addWrappedHint(int id, String text, int y) {
        int width = imageWidth - 16;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && font.width(line + " " + word) > width) {
                addLabel(new GuiLabel(id++, Component.literal(line.toString()), HINT_COLOR,
                        guiLeft + 8, y, width, HINT_LINE_HEIGHT));
                y += HINT_LINE_HEIGHT;
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            addLabel(new GuiLabel(id, Component.literal(line.toString()), HINT_COLOR,
                    guiLeft + 8, y, width, HINT_LINE_HEIGHT));
            y += HINT_LINE_HEIGHT;
        }
        return y;
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
