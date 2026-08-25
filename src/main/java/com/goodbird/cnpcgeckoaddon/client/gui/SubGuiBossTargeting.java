package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Boss-wide target selection: always attack whoever is closest instead of the first aggressor. */
public final class SubGuiBossTargeting extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int LINE_OF_SIGHT_BUTTON = 2;
    private static final int KEEP_TARGET_BUTTON = 3;
    private static final int RADIUS_FIELD = 4;
    private static final int INTERVAL_FIELD = 5;
    private static final int AGGRO_ZONE_BUTTON = 6;
    private static final int HEALTH_SCALING_BUTTON = 7;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossTargeting(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.targeting", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.target_nearest", y, data.isTargetNearestPlayer());
        y += 26;
        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.target_radius", y,
                data.getTargetSearchRadius(), 4, 128, 32);
        y += 26;
        addNumberField(INTERVAL_FIELD, "cnpcgeckoaddon.boss.target_interval", y,
                data.getTargetRecheckTicks(), 1, 200, 20);
        y += 26;
        addYesNo(LINE_OF_SIGHT_BUTTON, "cnpcgeckoaddon.boss.target_los", y, data.isTargetRequiresLineOfSight());
        y += 26;
        addYesNo(KEEP_TARGET_BUTTON, "cnpcgeckoaddon.boss.target_keep", y, data.isKeepTargetOutOfRange());

        addButton(new GuiButtonNop(this, AGGRO_ZONE_BUTTON, guiLeft + 8, guiTop + 158, 234, 20,
                "cnpcgeckoaddon.boss.aggro_zone_settings"));
        addButton(new GuiButtonNop(this, HEALTH_SCALING_BUTTON, guiLeft + 8, guiTop + 182, 234, 20,
                "cnpcgeckoaddon.boss.health_scaling_settings"));
        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.target_hint", guiLeft + 8, guiTop + 208, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 155, y, 87, 20, value));
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setTargetNearestPlayer(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == LINE_OF_SIGHT_BUTTON) {
            data.setTargetRequiresLineOfSight(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == KEEP_TARGET_BUTTON) {
            data.setKeepTargetOutOfRange(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == AGGRO_ZONE_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossAggroZone(data));
        } else if (button.id == HEALTH_SCALING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossHealthScaling(npc, data));
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
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        if (radius != null) data.setTargetSearchRadius(radius.getInteger());
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (interval != null) data.setTargetRecheckTicks(interval.getInteger());
    }
}
