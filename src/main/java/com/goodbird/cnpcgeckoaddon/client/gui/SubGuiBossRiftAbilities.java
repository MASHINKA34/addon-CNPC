package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

/**
 * Which of the phase's abilities the boss may cast while its rift is open, one row per ability.
 *
 * <p>The shadow copies' list, over the rift's own: everything the rotation casts but a second rift
 * and the two that would stand more of the boss up beside it, which are simply not offered. Nothing
 * marked is a boss that only stands and waits.</p>
 */
public final class SubGuiBossRiftAbilities extends SubGuiFieldScreen {
    private static final int FIRST_ABILITY_BUTTON = 100;
    private static final int HINT_LABEL = 40;

    /** Two columns of twelve: twenty-four abilities may be cast meanwhile today, the vents the last. */
    private static final int ROWS_PER_COLUMN = 12;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;
    private static final int HINT_Y = 24 + ROWS_PER_COLUMN * ROW_HEIGHT + 4;
    private static final String HINT = "cnpcgeckoaddon.boss.rift_abilities_hint";

    private final BossRiftSettings rift;

    public SubGuiBossRiftAbilities(BossRiftSettings rift) {
        this.rift = rift;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: the hint's lines are the locale's.
        imageHeight = doneY() + 26;
        super.init();
        addLabel(new ThemeLabel(30, "cnpcgeckoaddon.boss.rift_abilities_title",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int i = 0; i < BossRiftSettings.MEANWHILE_ABILITIES.length; i++) {
            int x = guiLeft + 8 + i / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
            int y = guiTop + 24 + i % ROWS_PER_COLUMN * ROW_HEIGHT;
            addButton(new ThemeButton(this, FIRST_ABILITY_BUTTON + i, x, y, COLUMN_WIDTH, 20,
                    abilityLabel(i)));
        }
        addWrappedHint(HINT_LABEL, HINT, guiTop + HINT_Y);
        addDoneButton(guiLeft + 182, guiTop + doneY(), 60, 20);
    }

    private int doneY() {
        return HINT_Y + wrappedHintHeight(HINT) + 4;
    }

    /** "+ Ranged attack" while the boss may cast it meanwhile, "- Ranged attack" while it may not. */
    private String abilityLabel(int index) {
        int ability = BossRiftSettings.MEANWHILE_ABILITIES[index];
        return (rift.castsMeanwhile(ability) ? "+ " : "- ") + I18n.get(BossAbilityKind.LABELS[ability]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_ABILITY_BUTTON;
        if (index >= 0 && index < BossRiftSettings.MEANWHILE_ABILITIES.length) {
            int ability = BossRiftSettings.MEANWHILE_ABILITIES[index];
            rift.setCastsMeanwhile(ability, !rift.castsMeanwhile(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(index));
        }
    }
}
