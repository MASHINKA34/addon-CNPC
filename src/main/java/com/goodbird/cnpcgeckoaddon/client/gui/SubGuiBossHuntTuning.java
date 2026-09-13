package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossEffectData;
import com.goodbird.cnpcgeckoaddon.data.BossHuntSettings;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.ArrayList;
import java.util.List;

/** How often the prey is bitten, what marks it, and what the catch reach is measured with. */
public final class SubGuiBossHuntTuning extends SubGuiBossAbilityTuning {
    private static final int CATCH_INTERVAL_FIELD = 1;
    private static final int MARK_EFFECT_FIELD = 2;
    private static final int MARK_LEVEL_FIELD = 3;
    private static final int REACH_MODELS_BUTTON = 4;

    private final BossHuntSettings hunt;

    public SubGuiBossHuntTuning(BossHuntSettings hunt) {
        super("cnpcgeckoaddon.boss.hunt_tuning_title");
        this.hunt = hunt;
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void addRows() {
        addNumberField(CATCH_INTERVAL_FIELD, "cnpcgeckoaddon.boss.hunt_catch_interval", nextRow(),
                hunt.getCatchIntervalTicks(), BossHuntSettings.MIN_CATCH_INTERVAL,
                BossHuntSettings.MAX_CATCH_INTERVAL, 20);
        addPickerRow(MARK_EFFECT_FIELD, "cnpcgeckoaddon.boss.hunt_mark_effect", nextRow(),
                hunt.getMarkEffect());
        addNumberField(MARK_LEVEL_FIELD, "cnpcgeckoaddon.boss.hunt_mark_level", nextRow(),
                hunt.getMarkLevel(), 1, BossHuntSettings.MAX_MARK_AMPLIFIER + 1, 1);
        addYesNo(REACH_MODELS_BUTTON, "cnpcgeckoaddon.boss.hunt_reach_models", nextRow(),
                hunt.isReachAddsModels());
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == REACH_MODELS_BUTTON) {
            hunt.setReachAddsModels(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MARK_EFFECT_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.hunt_mark_effect",
                    markableEffects(), name -> {
                hunt.setMarkEffect(name);
                getTextField(MARK_EFFECT_FIELD).setValue(name);
            }));
        }
    }

    /**
     * Every potion, without the fire: the hunt hangs a mark on its prey for the length of the
     * chase, and the fire slot of an effect set is not something that can be hung or taken off.
     */
    private static List<String> markableEffects() {
        List<String> ids = new ArrayList<>(BossEffectData.getSelectableIds());
        ids.remove(BossEffectData.FIRE_ID);
        return ids;
    }

    @Override
    protected void applyFields() {
        applyNumberField(CATCH_INTERVAL_FIELD, hunt::setCatchIntervalTicks);
        applyNumberField(MARK_LEVEL_FIELD, hunt::setMarkLevel);
        GuiTextFieldNop field = getTextField(MARK_EFFECT_FIELD);
        if (field == null) {
            return;
        }
        // A typo would leave the prey unmarked with nothing on screen to say why, so it is
        // refused while the field is still in front of whoever typed it. The fire is no mark:
        // the hunt hangs a potion on its prey rather than setting it alight.
        String value = field.getValue().trim();
        if (BossEffectData.resolve(value) != null) {
            hunt.setMarkEffect(value);
        } else {
            field.setValue(hunt.getMarkEffect());
        }
    }
}
