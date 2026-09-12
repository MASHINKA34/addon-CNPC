package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossEffectData;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Editor for one potion effect slot of a boss attack, or of the fire that shares the slot. */
public final class SubGuiBossEffect extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int EFFECT_FIELD = 2;
    private static final int DURATION_FIELD = 3;
    private static final int LEVEL_FIELD = 4;
    private static final int PARTICLES_BUTTON = 5;
    /** Where the hints start: under the particles row, where the one hint always sat. */
    private static final int HINTS_Y = 166;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;

    private final BossEffectData effect;

    public SubGuiBossEffect(BossEffectData effect) {
        this.effect = effect;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the two hints
        // wrap to is up to the locale.
        imageHeight = buttonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.effect", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.effect_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, effect.isEnabled()));
        y += 26;

        addLabel(new GuiLabel(EFFECT_FIELD, "cnpcgeckoaddon.boss.effect_id", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(EFFECT_FIELD, this, guiLeft + 70, y, 124, 20, effect.getEffectId()));
        addButton(new GuiButtonNop(this, EFFECT_FIELD, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
        y += 26;

        addNumberField(DURATION_FIELD, "cnpcgeckoaddon.boss.effect_duration", y,
                effect.getDurationTicks(), 1, 72000, 100);
        y += 26;
        addNumberField(LEVEL_FIELD, "cnpcgeckoaddon.boss.effect_level", y, effect.getLevel(), 1, 10, 1);
        y += 26;

        addLabel(new GuiLabel(PARTICLES_BUTTON, "cnpcgeckoaddon.boss.effect_particles", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, PARTICLES_BUTTON, guiLeft + 155, y, 87, 20, effect.isShowParticles()));

        // Wrapped, both of them: a single label never wraps, and the first hint alone was
        // already wider than the panel in either locale.
        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.effect_hint", guiTop + HINTS_Y);
        addWrappedHint(40, "cnpcgeckoaddon.boss.effect_fire_hint", hintY);
        addDoneButton(guiLeft + 182, guiTop + buttonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the done button goes, from the panel's top: just under both hints. */
    private int buttonY() {
        return HINTS_Y + wrappedHintHeight("cnpcgeckoaddon.boss.effect_hint")
                + wrappedHintHeight("cnpcgeckoaddon.boss.effect_fire_hint") + 4;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            effect.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PARTICLES_BUTTON) {
            effect.setShowParticles(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == EFFECT_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.potion_effect",
                    BossEffectData.getSelectableIds(), name -> {
                effect.setEffectId(name);
                getTextField(EFFECT_FIELD).setValue(name);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop id = getTextField(EFFECT_FIELD);
        if (id != null) {
            String value = id.getValue().trim();
            // A typo would silently never apply anything, so reject it while it is visible.
            if (BossEffectData.isKnownEffect(value)) effect.setEffectId(value);
            else id.setValue(effect.getEffectId());
        }
        applyNumberField(DURATION_FIELD, effect::setDurationTicks);
        applyNumberField(LEVEL_FIELD, effect::setLevel);
    }
}
