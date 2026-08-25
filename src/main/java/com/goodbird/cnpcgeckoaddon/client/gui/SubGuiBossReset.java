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
    private static final int HOME_LEASH_BUTTON = 4;
    private static final int HOME_RADIUS_FIELD = 5;
    private static final int HOME_VERTICAL_BUTTON = 6;
    private static final int HOME_GRACE_FIELD = 7;

    private final TeleportPathData data;

    public SubGuiBossReset(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.reset", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addNumberField(TICKS_FIELD, "cnpcgeckoaddon.boss.reset_ticks", null, y,
                data.getResetTicks(), TeleportPathData.MIN_RESET_TICKS,
                TeleportPathData.MAX_RESET_TICKS, 100);
        y += 24;

        addYesNo(HEAL_BUTTON, "cnpcgeckoaddon.boss.reset_heal", null, y, data.isResetHeal());
        y += 24;

        addYesNo(RETURN_BUTTON, "cnpcgeckoaddon.boss.reset_return", null, y, data.isResetReturn());
        y += 24;

        addYesNo(HOME_LEASH_BUTTON, "cnpcgeckoaddon.boss.home_leash_enabled",
                "cnpcgeckoaddon.boss.home_leash_hint", y, data.isHomeLeashEnabled());
        y += 24;

        addNumberField(HOME_RADIUS_FIELD, "cnpcgeckoaddon.boss.home_leash_radius",
                "cnpcgeckoaddon.boss.home_leash_teleport_hint", y, data.getHomeLeashRadius(),
                TeleportPathData.MIN_HOME_LEASH_RADIUS, TeleportPathData.MAX_HOME_LEASH_RADIUS, 32);
        y += 24;

        addYesNo(HOME_VERTICAL_BUTTON, "cnpcgeckoaddon.boss.home_leash_vertical", null, y,
                data.isHomeLeashVertical());
        y += 24;

        addNumberField(HOME_GRACE_FIELD, "cnpcgeckoaddon.boss.home_leash_grace",
                "cnpcgeckoaddon.teleport.ticks_hint", y, data.getHomeLeashGraceTicks(),
                TeleportPathData.MIN_HOME_LEASH_GRACE_TICKS,
                TeleportPathData.MAX_HOME_LEASH_GRACE_TICKS, 0);

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.reset_hint", guiLeft + 8, guiTop + 202, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    private void addYesNo(int id, String label, String tooltip, int y, boolean value) {
        addLabel(tooltip == null
                ? new GuiLabel(id, label, guiLeft + 8, y + 6)
                : new GuiLabel(id, label, guiLeft + 8, y + 6, tooltip));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 155, y, 87, 20, value));
    }

    private void addNumberField(int id, String label, String tooltip, int y, int value,
                                int min, int max, int fallback) {
        addLabel(tooltip == null
                ? new GuiLabel(id, label, guiLeft + 8, y + 6)
                : new GuiLabel(id, label, guiLeft + 8, y + 6, tooltip));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == HEAL_BUTTON) {
            data.setResetHeal(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == RETURN_BUTTON) {
            data.setResetReturn(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == HOME_LEASH_BUTTON) {
            data.setHomeLeashEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == HOME_VERTICAL_BUTTON) {
            data.setHomeLeashVertical(((GuiButtonYesNo) button).getBoolean());
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
        GuiTextFieldNop radius = getTextField(HOME_RADIUS_FIELD);
        if (radius != null) data.setHomeLeashRadius(radius.getInteger());
        GuiTextFieldNop grace = getTextField(HOME_GRACE_FIELD);
        if (grace != null) data.setHomeLeashGraceTicks(grace.getInteger());
    }
}
