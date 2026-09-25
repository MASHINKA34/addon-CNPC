package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossShadowSettings;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * Which of the phase's abilities the shadow copies cast, one row per ability.
 *
 * <p>The warning screen's layout over the copies' own list: everything the rotation casts
 * but the two that would make copies of copies, which are simply not offered.</p>
 */
public final class SubGuiBossShadowAbilities extends SubGuiFieldScreen {
    private static final int FIRST_ABILITY_BUTTON = 100;

    /** Two columns of twelve: twenty-four abilities may be handed to a copy today, the vents the last. */
    private static final int ROWS_PER_COLUMN = 12;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;
    private static final int DONE_Y = 24 + ROWS_PER_COLUMN * ROW_HEIGHT + 4;

    private final BossShadowSettings shadow;

    public SubGuiBossShadowAbilities(BossShadowSettings shadow) {
        this.shadow = shadow;
        imageWidth = 256;
        imageHeight = DONE_Y + 26;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.shadow_abilities_title",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int i = 0; i < BossShadowSettings.COPY_ABILITIES.length; i++) {
            int x = guiLeft + 8 + i / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
            int y = guiTop + 24 + i % ROWS_PER_COLUMN * ROW_HEIGHT;
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + i, x, y, COLUMN_WIDTH, 20,
                    abilityLabel(i)));
        }
        addDoneButton(guiLeft + 182, guiTop + DONE_Y, 60, 20);
    }

    /** "+ Ground attack" while the copies cast it, "- Ground attack" while they do not. */
    private String abilityLabel(int index) {
        int ability = BossShadowSettings.COPY_ABILITIES[index];
        return (shadow.castsAbility(ability) ? "+ " : "- ") + I18n.get(BossAbilityKind.LABELS[ability]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_ABILITY_BUTTON;
        if (index >= 0 && index < BossShadowSettings.COPY_ABILITIES.length) {
            int ability = BossShadowSettings.COPY_ABILITIES[index];
            shadow.setCastsAbility(ability, !shadow.castsAbility(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(index));
        }
    }
}
