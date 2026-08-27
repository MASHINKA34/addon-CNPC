package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.NpcDamageResistEntry;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Editor for one damage resistance rule of an npc. */
public final class SubGuiNpcDamageResistEntry extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int MATCHER_FIELD = 1;
    private static final int PERCENT_FIELD = 2;
    private static final int CLEAR_BUTTON = 3;
    private static final int FIRST_HINT_LABEL = 40;

    private final NpcDamageResistEntry entry;

    public SubGuiNpcDamageResistEntry(NpcDamageResistEntry entry) {
        this.entry = entry;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.npc.resist_slot", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addLabel(new GuiLabel(MATCHER_FIELD, "cnpcgeckoaddon.npc.resist_match", guiLeft + 8, y + 6));
        // Free text on purpose: the interesting ids come from other mods, so there is no
        // registry to validate against - an unknown string just never matches anything.
        addTextField(new GuiTextFieldNop(MATCHER_FIELD, this, guiLeft + 90, y, 152, 20, entry.getMatcher()));
        y += 24;
        y = addWrappedHint(FIRST_HINT_LABEL, "cnpcgeckoaddon.npc.resist_examples", y) + 6;

        addNumberField(PERCENT_FIELD, "cnpcgeckoaddon.npc.resist_percent", y, entry.getPercent(),
                0, NpcDamageResistEntry.PERCENT_MAX, NpcDamageResistEntry.PERCENT_NORMAL);
        y += 26;

        addButton(new GuiButtonNop(this, CLEAR_BUTTON, guiLeft + 8, y, 234, 20,
                "cnpcgeckoaddon.npc.resist_clear"));

        addWrappedHint(FIRST_HINT_LABEL + 10, "cnpcgeckoaddon.npc.resist_hint", guiTop + 158);
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == CLEAR_BUTTON) {
            entry.clear();
            getTextField(MATCHER_FIELD).setValue("");
            getTextField(PERCENT_FIELD).setValue(Integer.toString(entry.getPercent()));
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop matcher = getTextField(MATCHER_FIELD);
        if (matcher != null) {
            entry.setMatcher(matcher.getValue());
        }
        GuiTextFieldNop percent = getTextField(PERCENT_FIELD);
        if (percent != null) {
            entry.setPercent(percent.getInteger());
        }
    }
}
