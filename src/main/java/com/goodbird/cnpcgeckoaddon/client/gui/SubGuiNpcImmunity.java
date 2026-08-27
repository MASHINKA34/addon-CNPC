package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.NpcImmunityData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcImmunityData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Which boss abilities this npc is simply not there for, one row per ability. */
public final class SubGuiNpcImmunity extends GuiBasic {
    private static final int FIRST_ABILITY_BUTTON = 100;
    private static final int FIRST_HINT_LABEL = 40;

    /** Two columns, because nine rows and two hints do not share one panel comfortably. */
    private static final int ROWS_PER_COLUMN = 5;
    private static final int COLUMN_WIDTH = 117;
    private static final int ROW_HEIGHT = 22;
    private static final int HINT_COLOR = 0xA0A0A0;
    private static final int HINT_LINE_HEIGHT = 9;

    private final NpcImmunityData data;

    public SubGuiNpcImmunity(DataAI ai) {
        data = ((INpcImmunityData) ai).cnpcgeckoaddon$getNpcImmunityData();
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 230;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.npc.immunity_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));

        for (int i = 0; i < BossAbilityKind.IMMUNITY_ABILITIES.length; i++) {
            int x = guiLeft + 8 + i / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
            int y = guiTop + 24 + i % ROWS_PER_COLUMN * ROW_HEIGHT;
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + i, x, y, COLUMN_WIDTH, 20,
                    abilityLabel(i)));
        }

        int y = addWrappedHint(FIRST_HINT_LABEL, "cnpcgeckoaddon.npc.immunity_hint", guiTop + 138);
        addWrappedHint(FIRST_HINT_LABEL + 10, "cnpcgeckoaddon.npc.immunity_blast_hint", y + 4);
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 200, 60, 20,
                "gui.done", button -> close()));
    }

    /** "+ Area attack" while the ability is switched off for this npc, "- ..." while it lands. */
    private String abilityLabel(int index) {
        int ability = BossAbilityKind.IMMUNITY_ABILITIES[index];
        return (data.isImmuneTo(ability) ? "+ " : "- ") + I18n.get(BossAbilityKind.LABELS[ability]);
    }

    /**
     * A GuiLabel draws one line and never clips it, so a hint too wide for the panel is split
     * into its own labels here rather than running off the edge of the background.
     *
     * @return the y the next thing down may start at
     */
    private int addWrappedHint(int id, String key, int y) {
        int width = imageWidth - 16;
        StringBuilder line = new StringBuilder();
        for (String word : I18n.get(key).split(" ")) {
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
        if (index >= 0 && index < BossAbilityKind.IMMUNITY_ABILITIES.length) {
            int ability = BossAbilityKind.IMMUNITY_ABILITIES[index];
            data.setImmuneTo(ability, !data.isImmuneTo(ability));
            // Relabelled in place: this GUI framework has no widget-clearing rebuild, so
            // calling init() again would stack a second set of buttons on the first.
            button.setDisplayText(abilityLabel(index));
        }
    }
}
