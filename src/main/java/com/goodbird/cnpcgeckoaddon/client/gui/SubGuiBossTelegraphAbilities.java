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

    /**
     * Two columns, the way the npc immunity screen lists the same abilities. Eight rows
     * since the take cover strike made it fifteen.
     */
    private static final int ROWS_PER_COLUMN = 8;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;

    private final TeleportPathData data;

    public SubGuiBossTelegraphAbilities(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 230;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.telegraph_abilities",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int i = 0; i < TeleportPathData.TELEGRAPH_ABILITIES.length; i++) {
            int x = guiLeft + 8 + i / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
            int y = guiTop + 24 + i % ROWS_PER_COLUMN * ROW_HEIGHT;
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + i, x, y, COLUMN_WIDTH, 20,
                    abilityLabel(i)));
        }
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 204, 60, 20,
                "gui.done", button -> close()));
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
