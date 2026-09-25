package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.ZoneSelectionClient;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossZonePreview;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Edits the two-corner volume which can start a boss encounter without an opening hit. */
public final class SubGuiBossAggroZone extends SubGuiFieldScreen implements ITextfieldListener, BossZoneScreen {
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
    private static final int EXCLUSIVE_BUTTON = 14;
    private static final int BLOCK_OUTSIDE_BUTTON = 15;
    private static final int SELECT_BUTTON = 16;
    /** Where the hints start, under the show button. */
    private static final int HINTS_Y = 255;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    private static final String HINT = "cnpcgeckoaddon.boss.aggro_zone_hint";
    private static final String EXCLUSIVE_HINT = "cnpcgeckoaddon.boss.aggro_zone_exclusive_hint";

    private final TeleportPathData data;

    public SubGuiBossAggroZone(TeleportPathData data) {
        this.data = data;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hints wrap
        // to is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
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
        addYesNo(EXCLUSIVE_BUTTON, "cnpcgeckoaddon.boss.aggro_zone_exclusive", guiTop + 110,
                data.isAggroZoneExclusive());
        addYesNo(BLOCK_OUTSIDE_BUTTON, "cnpcgeckoaddon.boss.aggro_zone_block_outside", guiTop + 132,
                data.isAggroZoneBlocksOutsideDamage());

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.aggro_zone_corner1",
                guiLeft + 8, guiTop + 157));
        addCornerFields(X1_FIELD, Y1_FIELD, Z1_FIELD, CORNER1_HERE_BUTTON, guiTop + 167,
                data.getAggroZoneX1(), data.getAggroZoneY1(), data.getAggroZoneZ1());
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.aggro_zone_corner2",
                guiLeft + 8, guiTop + 193));
        addCornerFields(X2_FIELD, Y2_FIELD, Z2_FIELD, CORNER2_HERE_BUTTON, guiTop + 203,
                data.getAggroZoneX2(), data.getAggroZoneY2(), data.getAggroZoneZ2());

        // Show and select share the row: the show button's longest translation takes 117 of its 124.
        addButton(new GuiButtonNop(this, SHOW_BUTTON, guiLeft + 8, guiTop + 229, 124, 20,
                "cnpcgeckoaddon.boss.aggro_zone_show"));
        addButton(new GuiButtonNop(this, SELECT_BUTTON, guiLeft + 136, guiTop + 229, 106, 20,
                ZoneSelectionClient.SELECT_BOX));
        // Wrapped, both: a single label never wraps, and the second hint is wider than the panel.
        int hintY = addWrappedHint(33, HINT, guiTop + HINTS_Y);
        addWrappedHint(40, EXCLUSIVE_HINT, hintY);
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the done button goes, from the panel's top: just under both hints. */
    private int doneButtonY() {
        return HINTS_Y + wrappedHintHeight(HINT) + wrappedHintHeight(EXCLUSIVE_HINT) + 4;
    }


    private void addCornerFields(int xId, int yId, int zId, int buttonId, int y,
                                 int x, int cornerY, int z) {
        addTextField(coordinateField(xId, guiLeft + 8, y, 40, x));
        addTextField(coordinateField(yId, guiLeft + 52, y, 40, cornerY));
        addTextField(coordinateField(zId, guiLeft + 96, y, 40, z));
        addButton(new GuiButtonNop(this, buttonId, guiLeft + 142, y, 100, 20,
                "cnpcgeckoaddon.boss.aggro_zone_here"));
    }


    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setAggroZoneEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_BUTTON) {
            data.setAggroZoneTargetMode(button.getValue());
        } else if (button.id == KEEP_BUTTON) {
            data.setAggroZoneKeepInside(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == EXCLUSIVE_BUTTON) {
            data.setAggroZoneExclusive(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BLOCK_OUTSIDE_BUTTON) {
            data.setAggroZoneBlocksOutsideDamage(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == CORNER1_HERE_BUTTON) {
            takePlayerPosition(true);
        } else if (button.id == CORNER2_HERE_BUTTON) {
            takePlayerPosition(false);
        } else if (button.id == SHOW_BUTTON) {
            applyFields();
            BossZonePreview.showAggroZone(data);
        } else if (button.id == SELECT_BUTTON) {
            applyFields();
            // The zone keeps world blocks, so the two corners go in as they were clicked.
            ZoneSelectionClient.selectBox(BossZonePreview.KIND_AGGRO, (box, anchor) -> {
                data.setAggroZoneCorner1(box.min().getX(), box.min().getY(), box.min().getZ());
                data.setAggroZoneCorner2(box.max().getX(), box.max().getY(), box.max().getZ());
            });
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
    protected void applyFields() {
        applyNumberField(INTERVAL_FIELD, data::setAggroZoneRecheckTicks);
        data.setAggroZoneCorner1(signed(X1_FIELD), signed(Y1_FIELD), signed(Z1_FIELD));
        data.setAggroZoneCorner2(signed(X2_FIELD), signed(Y2_FIELD), signed(Z2_FIELD));
    }

    /** The aggro zone, which the boss keeps in no object of its own. */
    @Override
    public Object zoneFocus() {
        return BossZonePreview.Focus.AGGRO_ZONE;
    }
}
