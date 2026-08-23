package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.utils.FluidBlockUtil;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Spits a glob of fluid that leaves a puddle for a few seconds and then disappears. */
public final class SubGuiBossFluidSpit extends GuiBasic implements ITextfieldListener {
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

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossFluidSpit(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.fluid_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isFluidSpitEnabled()));
        y += 22;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getFluidSpitAnimation());
        y += 22;
        addSelectRow(FLUID_FIELD, "cnpcgeckoaddon.boss.fluid_block", y, phase.getFluidSpitBlock());
        y += 21;
        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getFluidSpitTargetMode()));
        y += 21;

        addNumberField(LIFETIME_FIELD, "cnpcgeckoaddon.boss.fluid_lifetime", y,
                phase.getFluidSpitLifetimeTicks(), 5, 1200, 60);
        y += 21;
        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.fluid_radius", y,
                phase.getFluidSpitRadius(), 0, 4, 1);
        y += 21;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.fluid_impact_damage", y,
                phase.getFluidSpitDamage(), 0, 1000, 0);
        y += 21;
        // Min and max share a row: the extra target selector would otherwise push the
        // last field past the bottom edge of the 256px background.
        addRangeRow(y, phase.getFluidSpitMinRange(), phase.getFluidSpitMaxRange());
        y += 21;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.getFluidSpitActionDelayTicks(), 0, 1200, 12);
        y += 21;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.getFluidSpitCooldownTicks(), 1, 12000, 120);

        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
                "gui.done", button -> close()));
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 88, y, 106, 20, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    private void addRangeRow(int y, int min, int max) {
        addLabel(new GuiLabel(MIN_RANGE_FIELD, "cnpcgeckoaddon.boss.range", guiLeft + 6, y + 6));
        GuiTextFieldNop minField = new GuiTextFieldNop(MIN_RANGE_FIELD, this, guiLeft + 130, y, 52, 20,
                Integer.toString(min));
        minField.setNumbersOnly();
        minField.setMinMaxDefault(0, 64, 2);
        addTextField(minField);
        GuiTextFieldNop maxField = new GuiTextFieldNop(MAX_RANGE_FIELD, this, guiLeft + 190, y, 52, 20,
                Integer.toString(max));
        maxField.setNumbersOnly();
        maxField.setMinMaxDefault(1, 128, 24);
        addTextField(maxField);
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 175, y, 67, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == TARGET_MODE_BUTTON) {
            phase.setFluidSpitTargetMode(button.getValue());
        } else if (button.id == ENABLED_BUTTON) {
            phase.setFluidSpitEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting fluid spit animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setFluidSpitAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase::setFluidSpitActionDelayTicks);
            }));
        } else if (button.id == FLUID_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting fluid block:",
                    FluidBlockUtil.getSelectableIds(), name -> {
                phase.setFluidSpitBlock(name);
                getTextField(FLUID_FIELD).setValue(name);
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
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setFluidSpitAnimation(value);
            else animation.setValue(phase.getFluidSpitAnimation());
        }
        GuiTextFieldNop fluid = getTextField(FLUID_FIELD);
        if (fluid != null) {
            String value = fluid.getValue().trim();
            // Anything that is not a fluid block would silently never spit, so reject it here.
            if (FluidBlockUtil.isFluidBlock(value)) phase.setFluidSpitBlock(value);
            else fluid.setValue(phase.getFluidSpitBlock());
        }
        GuiTextFieldNop lifetime = getTextField(LIFETIME_FIELD);
        if (lifetime != null) phase.setFluidSpitLifetimeTicks(lifetime.getInteger());
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        if (radius != null) phase.setFluidSpitRadius(radius.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setFluidSpitDamage(damage.getInteger());
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.setFluidSpitRange(min.getInteger(), max.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setFluidSpitActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setFluidSpitCooldownTicks(cooldown.getInteger());
    }
}
