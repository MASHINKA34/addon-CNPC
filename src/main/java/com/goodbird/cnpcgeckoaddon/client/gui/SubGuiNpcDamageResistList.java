package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.NpcDamageResistEntry;
import com.goodbird.cnpcgeckoaddon.data.NpcImmunityData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** The damage resistance rules of one npc, one row per slot - the topmost match wins. */
public final class SubGuiNpcDamageResistList extends SubGuiFieldScreen {
    private static final int FIRST_SLOT_BUTTON = 100;
    private static final int FIRST_HINT_LABEL = 40;

    private final NpcImmunityData data;

    public SubGuiNpcDamageResistList(NpcImmunityData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.npc.resist_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));

        int y = guiTop + 24;
        for (int i = 0; i < NpcImmunityData.RESIST_SLOTS; i++) {
            addButton(new GuiButtonNop(this, FIRST_SLOT_BUTTON + i, guiLeft + 8, y, 234, 20, slotLabel(i)));
            y += 22;
        }

        addWrappedHint(FIRST_HINT_LABEL, "cnpcgeckoaddon.npc.resist_hint", guiTop + 202);
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    /** "1. scorchedguns:* - 20%", or "1. not set" for an unused slot. */
    private String slotLabel(int index) {
        NpcDamageResistEntry entry = data.getResist(index);
        if (!entry.isSet()) {
            return (index + 1) + ". " + I18n.get("cnpcgeckoaddon.npc.resist_empty");
        }
        return (index + 1) + ". " + entry.getMatcher() + " - " + entry.getPercent() + "%";
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_SLOT_BUTTON;
        if (index >= 0 && index < NpcImmunityData.RESIST_SLOTS) {
            setSubGui(new SubGuiNpcDamageResistEntry(data.getResist(index)));
        }
    }

    @Override
    public void subGuiClosed(Screen subgui) {
        super.subGuiClosed(subgui);
        // Relabel in place: this GUI framework has no widget-clearing rebuild, so calling
        // init() again would stack a second set of buttons on top of the first.
        for (int i = 0; i < NpcImmunityData.RESIST_SLOTS; i++) {
            GuiButtonNop button = getButton(FIRST_SLOT_BUTTON + i);
            if (button != null) {
                button.setDisplayText(slotLabel(i));
            }
        }
    }
}
