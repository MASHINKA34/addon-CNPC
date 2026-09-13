package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.client.Minecraft;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.List;

public class SubGuiRangedExtras extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int PROJECTILE_FIELD = 1;
    private static final int KEEP_DISTANCE_FIELD = 2;
    private static final int MUZZLE_FIELD = 3;
    private static final int SHOT_VOLUME_FIELD = 4;
    private static final int SHOT_PITCH_FIELD = 5;
    private static final int RESET_BUTTON = 2;
    private static final int FIRST_HINT_LABEL = 40;

    private static final int FIRST_ROW_Y = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int ROWS = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 8;
    private static final String TUNING_HINT = "cnpcgeckoaddon.npc.tuning_hint";

    private final RangedExtraData data;

    public SubGuiRangedExtras(DataRanged ranged) {
        this.data = ((IRangedData) ranged).getRangedExtraData();
        this.imageWidth = 256;
        this.closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hint wraps
        // to is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        int y = guiTop + FIRST_ROW_Y;

        addLabel(new GuiLabel(PROJECTILE_FIELD, "cnpcgeckoaddon.ranged_extras.projectile_entity", guiLeft + 5, y + 6, 0xffffff));
        addTextField(new GuiTextFieldNop(PROJECTILE_FIELD, this, guiLeft + 100, y, 95, 20, data.getProjectileEntity()));
        addButton(new GuiButtonNop(this, PROJECTILE_FIELD, guiLeft + 199, y, 50, 20, "mco.template.button.select", (b) ->
                setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.projectile_entity", getEntityList(), (name) -> {
                    data.setProjectileEntity(name);
                    getTextField(PROJECTILE_FIELD).setValue(name);
                }))));
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(KEEP_DISTANCE_FIELD, "cnpcgeckoaddon.ranged_extras.keep_distance", guiLeft + 5, y + 6, 0xffffff));
        GuiTextFieldNop keepDistance = new GuiTextFieldNop(KEEP_DISTANCE_FIELD, this, guiLeft + 100, y, 50, 20, "" + data.getKeepDistance());
        keepDistance.setNumbersOnly();
        keepDistance.setMinMaxDefault(0, RangedExtraData.MAX_KEEP_DISTANCE, 0);
        addTextField(keepDistance);
        y += ROW_HEIGHT;

        // A plain field rather than a number one: the muzzle sits below the eyes by default,
        // and a numbers-only field refuses the minus sign that says so.
        addLabel(new GuiLabel(MUZZLE_FIELD, "cnpcgeckoaddon.ranged_extras.muzzle",
                guiLeft + 5, y + 6, 0xffffff));
        addTextField(coordinateField(MUZZLE_FIELD, guiLeft + 190, y, 50, data.getMuzzleHeightTenths()));
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(SHOT_VOLUME_FIELD, "cnpcgeckoaddon.ranged_extras.shot_volume",
                guiLeft + 5, y + 6, 0xffffff));
        addShotField(SHOT_VOLUME_FIELD, guiLeft + 190, y, data.getShotSoundVolumeTenths(),
                0, RangedExtraData.MAX_SHOT_VOLUME, RangedExtraData.DEFAULT_SHOT_VOLUME);
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(SHOT_PITCH_FIELD, "cnpcgeckoaddon.ranged_extras.shot_pitch",
                guiLeft + 5, y + 6, 0xffffff));
        addShotField(SHOT_PITCH_FIELD, guiLeft + 190, y, data.getShotSoundPitchTenths(),
                RangedExtraData.MIN_SHOT_PITCH, RangedExtraData.MAX_SHOT_PITCH,
                RangedExtraData.DEFAULT_SHOT_PITCH);
        y += ROW_HEIGHT;

        addButton(new GuiButtonNop(this, RESET_BUTTON, guiLeft + 100, y, 100, 20, "cnpcgeckoaddon.ranged_extras.reset_projectile", (b) -> {
            data.setProjectileEntity("");
            getTextField(PROJECTILE_FIELD).setValue("");
        }));

        addWrappedHint(FIRST_HINT_LABEL, TUNING_HINT, guiTop + hintY());
        addDoneButton(guiLeft + 190, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** One of the two short numbers the shot sound is read from. */
    private void addShotField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 50, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /** Where the hint starts, from the panel's top: just under the last row. */
    private int hintY() {
        return FIRST_ROW_Y + ROWS * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: just under the hint. */
    private int doneButtonY() {
        return hintY() + wrappedHintHeight(TUNING_HINT) + 4;
    }

    @Override
    protected void applyFields() {
        data.setMuzzleHeightTenths(signed(MUZZLE_FIELD));
        applyNumberField(SHOT_VOLUME_FIELD, data::setShotSoundVolumeTenths);
        applyNumberField(SHOT_PITCH_FIELD, data::setShotSoundPitchTenths);
    }

    public List<String> getEntityList() {
        return ProjectileEntityUtil.getSelectableIds(Minecraft.getInstance().level);
    }

    public boolean isValidEntity(String name) {
        return ProjectileEntityUtil.isSelectable(name, Minecraft.getInstance().level);
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        // The two fields below answer for themselves; everything added since goes through
        // applyFields, which is also what a close with Escape runs.
        applyFields();
        if (textfield.id == PROJECTILE_FIELD) {
            String value = textfield.getValue().trim();
            if (value.isEmpty() || isValidEntity(value)) {
                data.setProjectileEntity(value);
                textfield.setValue(value);
            } else {
                textfield.setValue(data.getProjectileEntity());
            }
        }
        if (textfield.id == KEEP_DISTANCE_FIELD) {
            data.setKeepDistance(textfield.getInteger());
        }
    }
}
