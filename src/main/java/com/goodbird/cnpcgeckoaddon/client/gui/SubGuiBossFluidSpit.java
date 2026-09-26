package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossFluidSpitSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Spits a glob of fluid that leaves a puddle for a few seconds and then disappears. */
public final class SubGuiBossFluidSpit extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int FLUID_FIELD = 3;
    private static final int LIFETIME_FIELD = 4;
    private static final int RADIUS_FIELD = 5;
    private static final int DAMAGE_FIELD = 6;
    private static final int MIN_RANGE_FIELD = 7;
    private static final int MAX_RANGE_FIELD = 8;
    private static final int ACTION_DELAY_FIELD = 9;
    private static final int COOLDOWN_FIELD = 10;
    private static final int TARGET_MODE_BUTTON = 11;
    private static final int AIM_TURN_FIELD = 12;
    private static final int TUNING_BUTTON = 13;
    private static final int EFFECTS_BUTTON = 67;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossFluidSpit(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // Two rows taller than the panel it used to be: the aim's own number and the way
        // into the throw's fine-tuning sit under the rows a builder already knows.
        imageHeight = 301;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.fluid_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new ThemeLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new ThemeYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.fluidSpit().isEnabled()));
        y += 22;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.fluidSpit().getAnimation());
        y += 22;
        addSelectRow(FLUID_FIELD, "cnpcgeckoaddon.boss.fluid_block", y, phase.fluidSpit().getBlock());
        y += 21;
        addLabel(new ThemeLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.fluidSpit().getTargetMode()));
        y += 21;

        addNumberField(LIFETIME_FIELD, "cnpcgeckoaddon.boss.fluid_lifetime", y,
                phase.fluidSpit().getLifetimeTicks(), 5, 1200, 60);
        y += 21;
        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.fluid_radius", y,
                phase.fluidSpit().getRadius(), 0, 4, 1);
        y += 21;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.fluid_impact_damage", y,
                phase.fluidSpit().getDamage(), 0, 1000, 0);
        y += 21;
        // Min and max share a row: the extra target selector would otherwise push the
        // last field past the bottom edge of the 256px background.
        addRangeRow(y, phase.fluidSpit().getMinRange(), phase.fluidSpit().getMaxRange());
        y += 21;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.fluidSpit().getActionDelayTicks(), 0, 1200, 12);
        y += 21;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.fluidSpit().getCooldownTicks(), 1, 12000, 120);
        y += 21;
        addNumberField(AIM_TURN_FIELD, "cnpcgeckoaddon.boss.fluid_aim_turn", y,
                phase.fluidSpit().getAimTurnDegrees(), BossFluidSpitSettings.MIN_AIM_TURN,
                BossFluidSpitSettings.MAX_AIM_TURN, 30);

        addButton(new ThemeButton(this, TUNING_BUTTON, guiLeft + 6, guiTop + 253, 236, 20,
                "cnpcgeckoaddon.boss.fluid_tuning"));
        addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 277, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 277, 60, 20);
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new ThemeLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new ThemeTextField(id, this, guiLeft + 88, y, 106, 20, value));
        addButton(new ThemeButton(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    private void addRangeRow(int y, int min, int max) {
        addLabel(new ThemeLabel(MIN_RANGE_FIELD, "cnpcgeckoaddon.boss.range", guiLeft + 6, y + 6));
        GuiTextFieldNop minField = new ThemeTextField(MIN_RANGE_FIELD, this, guiLeft + 130, y, 52, 20,
                Integer.toString(min));
        minField.setNumbersOnly();
        minField.setMinMaxDefault(0, 64, 2);
        addTextField(minField);
        GuiTextFieldNop maxField = new ThemeTextField(MAX_RANGE_FIELD, this, guiLeft + 190, y, 52, 20,
                Integer.toString(max));
        maxField.setNumbersOnly();
        maxField.setMinMaxDefault(1, 128, 24);
        addTextField(maxField);
    }

    @Override
    protected int numberLabelX() {
        return 6;
    }

    @Override
    protected int numberFieldX() {
        return 175;
    }

    @Override
    protected int numberFieldWidth() {
        return 67;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.fluidSpit().getEffects(), "cnpcgeckoaddon.boss.effects_fluid"));
            return;
        }
        if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossFluidSpitTuning(phase.fluidSpit()));
            return;
        }
        if (button.id == TARGET_MODE_BUTTON) {
            phase.fluidSpit().setTargetMode(button.getValue());
        } else if (button.id == ENABLED_BUTTON) {
            phase.fluidSpit().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.fluid_spit_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.fluidSpit().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase.fluidSpit()::setActionDelayTicks);
            }));
        } else if (button.id == FLUID_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.fluid_block",
                    FluidBlockUtil.getSelectableIds(), name -> {
                phase.fluidSpit().setBlock(name);
                getTextField(FLUID_FIELD).setValue(name);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.fluidSpit().setAnimation(value);
            else animation.setValue(phase.fluidSpit().getAnimation());
        }
        GuiTextFieldNop fluid = getTextField(FLUID_FIELD);
        if (fluid != null) {
            String value = fluid.getValue().trim();
            // Anything that is not a fluid block would silently never spit, so reject it here.
            if (FluidBlockUtil.isFluidBlock(value)) phase.fluidSpit().setBlock(value);
            else fluid.setValue(phase.fluidSpit().getBlock());
        }
        applyNumberField(LIFETIME_FIELD, phase.fluidSpit()::setLifetimeTicks);
        applyNumberField(RADIUS_FIELD, phase.fluidSpit()::setRadius);
        applyNumberField(DAMAGE_FIELD, phase.fluidSpit()::setDamage);
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.fluidSpit().setRange(min.getInteger(), max.getInteger());
        applyNumberField(ACTION_DELAY_FIELD, phase.fluidSpit()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.fluidSpit()::setCooldownTicks);
        applyNumberField(AIM_TURN_FIELD, phase.fluidSpit()::setAimTurnDegrees);
    }
}
