package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * Which of the boss' abilities warn before they land, one row per ability.
 *
 * <p>A page of its own since the warning screen took on the reaction time settings: the
 * ability rows and seven settings do not share 256 pixels without one of them being
 * unreadable.</p>
 */
public final class SubGuiBossTelegraphAbilities extends GuiBasic {
    private static final int FIRST_ABILITY_BUTTON = 100;

    private final TeleportPathData data;

    public SubGuiBossTelegraphAbilities(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 254;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.telegraph_abilities",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int ability = 0; ability < TeleportPathData.TELEGRAPH_ABILITY_COUNT; ability++) {
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + ability,
                    guiLeft + 8, guiTop + 24 + ability * 22, 234, 20, abilityLabel(ability)));
        }
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 228, 60, 20,
                "gui.done", button -> close()));
    }

    /** "+ Ground attack" while it warns, "- Ground attack" once it goes quiet. */
    private String abilityLabel(int ability) {
        return (data.isTelegraphAbility(ability) ? "+ " : "- ")
                + I18n.get(BossAbilityKind.LABELS[ability]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int ability = button.id - FIRST_ABILITY_BUTTON;
        if (ability >= 0 && ability < TeleportPathData.TELEGRAPH_ABILITY_COUNT) {
            data.setTelegraphAbility(ability, !data.isTelegraphAbility(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(ability));
        }
    }
}
