package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** What the boss shows and says before an ability lands, and how long it gives for it. */
public final class SubGuiBossTelegraph extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int STYLE_BUTTON = 2;
    private static final int ANNOUNCE_BUTTON = 3;
    private static final int SOUND_BUTTON = 4;
    private static final int DODGE_BUTTON = 5;
    private static final int LEAD_FIELD = 6;
    private static final int ZONE_RADIUS_FIELD = 7;
    private static final int ABILITIES_BUTTON = 8;

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
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.telegraph_title", guiLeft + 6, guiTop + 6, 0xFFFFFF));

        int y = guiTop + 20;
        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.telegraph_enabled", y, data.isTelegraphEnabled());
        y += 22;
        addLabel(new GuiLabel(STYLE_BUTTON, "cnpcgeckoaddon.boss.telegraph_style", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, STYLE_BUTTON, guiLeft + 130, y, 112, 20,
                TeleportPathData.TELEGRAPH_STYLE_LABELS, data.getTelegraphStyle()));
        y += 22;
        addYesNo(ANNOUNCE_BUTTON, "cnpcgeckoaddon.boss.telegraph_announce", y, data.isTelegraphAnnounce());
        y += 22;
        addYesNo(SOUND_BUTTON, "cnpcgeckoaddon.boss.telegraph_sound", y, data.isTelegraphSound());
        y += 22;
        addNumberField(LEAD_FIELD, "cnpcgeckoaddon.boss.telegraph_lead", y,
                data.getTelegraphLeadTicks(), TeleportPathData.MIN_TELEGRAPH_LEAD_TICKS,
                TeleportPathData.MAX_TELEGRAPH_LEAD_TICKS, TeleportPathData.DEFAULT_TELEGRAPH_LEAD_TICKS);
        y += 22;
        addNumberField(ZONE_RADIUS_FIELD, "cnpcgeckoaddon.boss.telegraph_zone_radius", y,
                data.getTelegraphZoneRadius(), TeleportPathData.MIN_TELEGRAPH_ZONE_RADIUS,
                TeleportPathData.MAX_TELEGRAPH_ZONE_RADIUS, TeleportPathData.DEFAULT_TELEGRAPH_ZONE_RADIUS);
        y += 22;
        addYesNo(DODGE_BUTTON, "cnpcgeckoaddon.boss.telegraph_dodge", y, data.isTelegraphDodge());

        addButton(new GuiButtonNop(this, ABILITIES_BUTTON, guiLeft + 6, guiTop + 174, 236, 20,
                "cnpcgeckoaddon.boss.telegraph_abilities"));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.telegraph_hint", guiLeft + 6, guiTop + 200, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.telegraph_lead_hint", guiLeft + 6, guiTop + 210, 0xA0A0A0));
        addLabel(new GuiLabel(33, "cnpcgeckoaddon.boss.telegraph_dodge_hint", guiLeft + 6, guiTop + 220, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
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

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setTelegraphEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STYLE_BUTTON) {
            data.setTelegraphStyle(button.getValue());
        } else if (button.id == ANNOUNCE_BUTTON) {
            data.setTelegraphAnnounce(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SOUND_BUTTON) {
            data.setTelegraphSound(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == DODGE_BUTTON) {
            data.setTelegraphDodge(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ABILITIES_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossTelegraphAbilities(data));
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
        GuiTextFieldNop lead = getTextField(LEAD_FIELD);
        if (lead != null) data.setTelegraphLeadTicks(lead.getInteger());
        GuiTextFieldNop radius = getTextField(ZONE_RADIUS_FIELD);
        if (radius != null) data.setTelegraphZoneRadius(radius.getInteger());
    }
}
