package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** What the boss shows and says before an ability lands on anybody. */
public final class SubGuiBossTelegraph extends GuiBasic {
    private static final int ENABLED_BUTTON = 1;
    private static final int STYLE_BUTTON = 2;
    private static final int ANNOUNCE_BUTTON = 3;
    private static final int SOUND_BUTTON = 4;
    private static final int ABILITIES_LABEL = 5;
    private static final int FIRST_ABILITY_BUTTON = 100;
    /** Eight abilities down one column would leave no room for the settings above them. */
    private static final int ABILITY_ROWS = 4;

    private final TeleportPathData data;

    public SubGuiBossTelegraph(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.telegraph_title", guiLeft + 6, guiTop + 8, 0xFFFFFF));

        int y = guiTop + 22;
        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.telegraph_enabled", y, data.isTelegraphEnabled());
        y += 22;
        addLabel(new GuiLabel(STYLE_BUTTON, "cnpcgeckoaddon.boss.telegraph_style", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, STYLE_BUTTON, guiLeft + 130, y, 112, 20,
                TeleportPathData.TELEGRAPH_STYLE_LABELS, data.getTelegraphStyle()));
        y += 22;
        addYesNo(ANNOUNCE_BUTTON, "cnpcgeckoaddon.boss.telegraph_announce", y, data.isTelegraphAnnounce());
        y += 22;
        addYesNo(SOUND_BUTTON, "cnpcgeckoaddon.boss.telegraph_sound", y, data.isTelegraphSound());

        addLabel(new GuiLabel(ABILITIES_LABEL, "cnpcgeckoaddon.boss.telegraph_abilities",
                guiLeft + 6, guiTop + 114, 0xFFFFFF));
        for (int ability = 0; ability < TeleportPathData.TELEGRAPH_ABILITY_COUNT; ability++) {
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + ability,
                    guiLeft + 6 + ability / ABILITY_ROWS * 124,
                    guiTop + 126 + ability % ABILITY_ROWS * 22, 120, 20, abilityLabel(ability)));
        }

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.telegraph_hint", guiLeft + 6, guiTop + 218, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    /**
     * A row whose label runs the width of the screen, so the toggle sits hard against the
     * right edge: "warn before abilities" is a sentence in some languages, not a word.
     */
    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 196, y, 46, 20, value));
    }

    /** "+ Ground attack" while it warns, "- Ground attack" once it goes quiet. */
    private String abilityLabel(int ability) {
        return (data.isTelegraphAbility(ability) ? "+ " : "- ")
                + I18n.get(TeleportPathData.TELEGRAPH_ABILITY_LABELS[ability]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int ability = button.id - FIRST_ABILITY_BUTTON;
        if (ability >= 0 && ability < TeleportPathData.TELEGRAPH_ABILITY_COUNT) {
            data.setTelegraphAbility(ability, !data.isTelegraphAbility(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(ability));
            return;
        }
        if (button.id == ENABLED_BUTTON) {
            data.setTelegraphEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STYLE_BUTTON) {
            data.setTelegraphStyle(button.getValue());
        } else if (button.id == ANNOUNCE_BUTTON) {
            data.setTelegraphAnnounce(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SOUND_BUTTON) {
            data.setTelegraphSound(((GuiButtonYesNo) button).getBoolean());
        }
    }
}
