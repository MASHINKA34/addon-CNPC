package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** What the boss rolls back to once nobody is fighting it any more. */
public final class SubGuiBossReset extends GuiBasic implements ITextfieldListener {
    private static final int TICKS_FIELD = 1;
    private static final int HEAL_BUTTON = 2;
    private static final int RETURN_BUTTON = 3;

    private final TeleportPathData data;

    public SubGuiBossReset(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.reset", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 30;

        addLabel(new GuiLabel(TICKS_FIELD, "cnpcgeckoaddon.boss.reset_ticks", guiLeft + 8, y + 6));
        GuiTextFieldNop ticks = new GuiTextFieldNop(TICKS_FIELD, this, guiLeft + 172, y, 70, 20,
                Integer.toString(data.getResetTicks()));
        ticks.setNumbersOnly();
        ticks.setMinMaxDefault(TeleportPathData.MIN_RESET_TICKS, TeleportPathData.MAX_RESET_TICKS, 100);
        addTextField(ticks);
        y += 28;

        addLabel(new GuiLabel(HEAL_BUTTON, "cnpcgeckoaddon.boss.reset_heal", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, HEAL_BUTTON, guiLeft + 155, y, 87, 20, data.isResetHeal()));
        y += 28;

        addLabel(new GuiLabel(RETURN_BUTTON, "cnpcgeckoaddon.boss.reset_return", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, RETURN_BUTTON, guiLeft + 155, y, 87, 20, data.isResetReturn()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.reset_hint", guiLeft + 8, guiTop + 150, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.teleport.ticks_hint", guiLeft + 8, guiTop + 162, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == HEAL_BUTTON) {
            data.setResetHeal(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == RETURN_BUTTON) {
            data.setResetReturn(((GuiButtonYesNo) button).getBoolean());
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
        GuiTextFieldNop ticks = getTextField(TICKS_FIELD);
        if (ticks != null) data.setResetTicks(ticks.getInteger());
    }
}
