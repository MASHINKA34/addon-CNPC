package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

/**
 * Which of the boss' abilities warn before they land, one row per ability.
 *
 * <p>A page of its own since the warning screen took on the reaction time settings: the
 * ability rows and seven settings do not share 256 pixels without one of them being
 * unreadable.</p>
 */
public final class SubGuiBossTelegraphAbilities extends SubGuiFieldScreen {
    private static final int FIRST_ABILITY_BUTTON = 100;

    /**
     * Two columns, the way the npc immunity screen lists the same abilities. Fourteen rows: the
     * platforms made it twenty-one and the rain of stones twenty-two, two full columns of
     * eleven, the hurricane and the shadow copies twenty-four, two of twelve, the seismic
     * waves twenty-five, which opened a thirteenth row, and the vents twenty-seven, which open
     * a fourteenth; Done keeps a line of its own under the grid.
     */
    private static final int ROWS_PER_COLUMN = 14;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;
    private static final int DONE_Y = 24 + ROWS_PER_COLUMN * ROW_HEIGHT + 4;

    private final TeleportPathData data;

    public SubGuiBossTelegraphAbilities(TeleportPathData data) {
        this.data = data;
        imageWidth = 256;
        imageHeight = DONE_Y + 26;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(30, "cnpcgeckoaddon.boss.telegraph_abilities",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int i = 0; i < TeleportPathData.TELEGRAPH_ABILITIES.length; i++) {
            int x = guiLeft + 8 + i / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
            int y = guiTop + 24 + i % ROWS_PER_COLUMN * ROW_HEIGHT;
            addButton(new ThemeButton(this, FIRST_ABILITY_BUTTON + i, x, y, COLUMN_WIDTH, 20,
                    abilityLabel(i)));
        }
        addDoneButton(guiLeft + 182, guiTop + DONE_Y, 60, 20);
    }

    /** "+ Ground attack" while it warns, "- Ground attack" once it goes quiet. */
    private String abilityLabel(int index) {
        int ability = TeleportPathData.TELEGRAPH_ABILITIES[index];
        return (data.isTelegraphAbility(ability) ? "+ " : "- ")
                + I18n.get(BossAbilityKind.LABELS[ability]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_ABILITY_BUTTON;
        if (index >= 0 && index < TeleportPathData.TELEGRAPH_ABILITIES.length) {
            int ability = TeleportPathData.TELEGRAPH_ABILITIES[index];
            data.setTelegraphAbility(ability, !data.isTelegraphAbility(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(index));
        }
    }
}
