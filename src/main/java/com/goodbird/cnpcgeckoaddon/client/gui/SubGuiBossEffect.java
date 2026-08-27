package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossEffectData;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Editor for one potion effect slot of a boss attack. */
public final class SubGuiBossEffect extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int EFFECT_FIELD = 2;
    private static final int DURATION_FIELD = 3;
    private static final int LEVEL_FIELD = 4;
    private static final int PARTICLES_BUTTON = 5;

    private final BossEffectData effect;

    public SubGuiBossEffect(BossEffectData effect) {
        this.effect = effect;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
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

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.effect_hint", guiLeft + 8, guiTop + 166, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            effect.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PARTICLES_BUTTON) {
            effect.setShowParticles(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == EFFECT_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting potion effect:",
                    BossEffectData.getSelectableIds(), name -> {
                effect.setEffectId(name);
                getTextField(EFFECT_FIELD).setValue(name);
            }));
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
        GuiTextFieldNop id = getTextField(EFFECT_FIELD);
        if (id != null) {
            String value = id.getValue().trim();
            // A typo would silently never apply anything, so reject it while it is visible.
            if (BossEffectData.isKnownEffect(value)) effect.setEffectId(value);
            else id.setValue(effect.getEffectId());
        }
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) effect.setDurationTicks(duration.getInteger());
        GuiTextFieldNop level = getTextField(LEVEL_FIELD);
        if (level != null) effect.setLevel(level.getInteger());
    }
}
