package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.renderer.BossAggroZonePreview;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Edits the two-corner volume which can start a boss encounter without an opening hit. */
public final class SubGuiBossAggroZone extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int TARGET_BUTTON = 2;
    private static final int INTERVAL_FIELD = 3;
    private static final int KEEP_BUTTON = 4;
    private static final int X1_FIELD = 5;
    private static final int Y1_FIELD = 6;
    private static final int Z1_FIELD = 7;
    private static final int CORNER1_HERE_BUTTON = 8;
    private static final int X2_FIELD = 9;
    private static final int Y2_FIELD = 10;
    private static final int Z2_FIELD = 11;
    private static final int CORNER2_HERE_BUTTON = 12;
    private static final int SHOW_BUTTON = 13;

    private final TeleportPathData data;

    public SubGuiBossAggroZone(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.aggro_zone_title",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));

        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.aggro_zone_enabled", guiTop + 22,
                data.isAggroZoneEnabled());
        addLabel(new GuiLabel(TARGET_BUTTON, "cnpcgeckoaddon.boss.aggro_zone_target",
                guiLeft + 8, guiTop + 50));
        addButton(new GuiButtonNop(this, TARGET_BUTTON, guiLeft + 142, guiTop + 44, 100, 20,
                TeleportPathData.AGGRO_ZONE_TARGET_LABELS, data.getAggroZoneTargetMode()));
        addLabel(new GuiLabel(INTERVAL_FIELD, "cnpcgeckoaddon.boss.aggro_zone_interval",
                guiLeft + 8, guiTop + 72));
        GuiTextFieldNop interval = new GuiTextFieldNop(INTERVAL_FIELD, this,
                guiLeft + 172, guiTop + 66, 70, 20, Integer.toString(data.getAggroZoneRecheckTicks()));
        interval.setNumbersOnly();
        interval.setMinMaxDefault(TeleportPathData.MIN_AGGRO_ZONE_RECHECK_TICKS,
                TeleportPathData.MAX_AGGRO_ZONE_RECHECK_TICKS, 5);
        addTextField(interval);
        addYesNo(KEEP_BUTTON, "cnpcgeckoaddon.boss.aggro_zone_keep", guiTop + 88,
                data.isAggroZoneKeepInside());

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.aggro_zone_corner1",
                guiLeft + 8, guiTop + 113));
        addCornerFields(X1_FIELD, Y1_FIELD, Z1_FIELD, CORNER1_HERE_BUTTON, guiTop + 123,
                data.getAggroZoneX1(), data.getAggroZoneY1(), data.getAggroZoneZ1());
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.aggro_zone_corner2",
                guiLeft + 8, guiTop + 149));
        addCornerFields(X2_FIELD, Y2_FIELD, Z2_FIELD, CORNER2_HERE_BUTTON, guiTop + 159,
                data.getAggroZoneX2(), data.getAggroZoneY2(), data.getAggroZoneZ2());

        addButton(new GuiButtonNop(this, SHOW_BUTTON, guiLeft + 8, guiTop + 185, 234, 20,
                "cnpcgeckoaddon.boss.aggro_zone_show"));
        addLabel(new GuiLabel(33, "cnpcgeckoaddon.boss.aggro_zone_hint",
                guiLeft + 8, guiTop + 211, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 142, y, 100, 20, value));
    }

    private void addCornerFields(int xId, int yId, int zId, int buttonId, int y,
                                 int x, int cornerY, int z) {
        addTextField(coordinateField(xId, guiLeft + 8, y, x));
        addTextField(coordinateField(yId, guiLeft + 52, y, cornerY));
        addTextField(coordinateField(zId, guiLeft + 96, y, z));
        addButton(new GuiButtonNop(this, buttonId, guiLeft + 142, y, 100, 20,
                "cnpcgeckoaddon.boss.aggro_zone_here"));
    }

    /** Coordinate fields stay plain so a minus sign can be entered before the digits. */
    private GuiTextFieldNop coordinateField(int id, int x, int y, int value) {
        return new GuiTextFieldNop(id, this, x, y, 40, 20, Integer.toString(value));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setAggroZoneEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_BUTTON) {
            data.setAggroZoneTargetMode(button.getValue());
        } else if (button.id == KEEP_BUTTON) {
            data.setAggroZoneKeepInside(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == CORNER1_HERE_BUTTON) {
            takePlayerPosition(true);
        } else if (button.id == CORNER2_HERE_BUTTON) {
            takePlayerPosition(false);
        } else if (button.id == SHOW_BUTTON) {
            applyFields();
            BossAggroZonePreview.show(data);
        }
    }

    private void takePlayerPosition(boolean firstCorner) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (firstCorner) {
            data.setAggroZoneCorner1(pos.getX(), pos.getY(), pos.getZ());
            showCorner(X1_FIELD, Y1_FIELD, Z1_FIELD, pos);
        } else {
            data.setAggroZoneCorner2(pos.getX(), pos.getY(), pos.getZ());
            showCorner(X2_FIELD, Y2_FIELD, Z2_FIELD, pos);
        }
    }

    private void showCorner(int xId, int yId, int zId, BlockPos pos) {
        getTextField(xId).setValue(Integer.toString(pos.getX()));
        getTextField(yId).setValue(Integer.toString(pos.getY()));
        getTextField(zId).setValue(Integer.toString(pos.getZ()));
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyFields();
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (interval != null) {
            data.setAggroZoneRecheckTicks(interval.getInteger());
        }
        data.setAggroZoneCorner1(signed(X1_FIELD), signed(Y1_FIELD), signed(Z1_FIELD));
        data.setAggroZoneCorner2(signed(X2_FIELD), signed(Y2_FIELD), signed(Z2_FIELD));
    }

    private int signed(int id) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return 0;
        }
        String value = field.getValue().trim();
        try {
            return value.isEmpty() || value.equals("-") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
