package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
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

/** Geyser: a mark burns on the floor under a victim, then the ground opens under it. */
public final class SubGuiBossGeyser extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_MODE_BUTTON = 3;
    private static final int TARGET_COUNT_FIELD = 4;
    private static final int FUSE_FIELD = 5;
    private static final int DAMAGE_FIELD = 6;
    private static final int RADIUS_FIELD = 7;
    private static final int LAUNCH_FIELD = 8;
    private static final int MIN_RANGE_FIELD = 9;
    private static final int MAX_RANGE_FIELD = 10;
    private static final int ACTION_DELAY_FIELD = 11;
    private static final int COOLDOWN_FIELD = 12;
    private static final int FOLLOW_BUTTON = 13;
    private static final int FLUID_FIELD = 14;
    private static final int FLUID_LIFE_FIELD = 15;
    private static final int VFX_STYLE_BUTTON = 16;
    private static final int BLOCK_WAVE_BUTTON = 17;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossGeyser(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        // The tallest ability screen in the mod, and deliberately so: the cast, the fuse, the
        // eruption and what it leaves behind are four sets of numbers, and a builder tuning
        // the fuse against the radius needs to see both of them at once.
        imageHeight = 326;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.geyser_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isGeyserEnabled()));
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getGeyserAnimation());
        y += 21;

        // How many marks and who they go under, on one line: the two answer the same
        // question and are always read together.
        addLabel(new GuiLabel(TARGET_COUNT_FIELD, "cnpcgeckoaddon.boss.geyser_targets", guiLeft + 6, y + 6));
        addPairedField(TARGET_COUNT_FIELD, guiLeft + 72, y, phase.getGeyserTargetCount(), 1, 8, 1, 38);
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getGeyserTargetMode()));
        y += 21;

        addNumberField(FUSE_FIELD, "cnpcgeckoaddon.boss.geyser_fuse", y,
                phase.getGeyserFuseTicks(), 5, 200, 25);
        y += 21;
        addPairRow(DAMAGE_FIELD, RADIUS_FIELD, "cnpcgeckoaddon.boss.geyser_area", y,
                phase.getGeyserDamage(), 0, 1000, 8,
                phase.getGeyserRadius(), 1, 16, 3);
        y += 21;
        addNumberField(LAUNCH_FIELD, "cnpcgeckoaddon.boss.geyser_launch", y,
                phase.getGeyserLaunch(), 0, 20, 8);
        y += 21;
        addPairRow(MIN_RANGE_FIELD, MAX_RANGE_FIELD, "cnpcgeckoaddon.boss.range", y,
                phase.getGeyserMinRange(), 0, 64, 3,
                phase.getGeyserMaxRange(), 1, 128, 24);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getGeyserActionDelayTicks(), 0, 1200, 12,
                phase.getGeyserCooldownTicks(), 1, 12000, 160);
        y += 21;

        addLabel(new GuiLabel(FOLLOW_BUTTON, "cnpcgeckoaddon.boss.geyser_follow", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, FOLLOW_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isGeyserFollowTarget()));
        y += 21;

        // Left empty the eruption pools nothing, which is why this field is not validated
        // the way the fluid spit's is.
        addSelectRow(FLUID_FIELD, "cnpcgeckoaddon.boss.geyser_fluid", y, phase.getGeyserFluid());
        y += 21;
        addNumberField(FLUID_LIFE_FIELD, "cnpcgeckoaddon.boss.geyser_fluid_life", y,
                phase.getGeyserFluidLifetimeTicks(), 5, 1200, 60);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;
        addLabel(new GuiLabel(BLOCK_WAVE_BUTTON, "cnpcgeckoaddon.boss.area_block_wave", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, BLOCK_WAVE_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isGeyserBlockWave()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.geyser_hint", guiLeft + 6, guiTop + 292, 0xA0A0A0));
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 302, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 302, 60, 20,
                "gui.done", button -> close()));
    }

    private int vfxStyleIndex() {
        String id = phase.getGeyserVfx();
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    /** A name with a picker beside it, for the animation and for the fluid. */
    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 108, y, 86, 20, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new GuiLabel(leftId, label, guiLeft + 6, y + 6));
        addPairedField(leftId, guiLeft + 130, y, leftValue, leftMin, leftMax, leftFallback, 52);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback, 52);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback,
                                int width) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
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
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.getGeyserEffects(), "cnpcgeckoaddon.boss.effects_geyser"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setGeyserEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FOLLOW_BUTTON) {
            phase.setGeyserFollowTarget(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BLOCK_WAVE_BUTTON) {
            phase.setGeyserBlockWave(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setGeyserTargetMode(button.getValue());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.setGeyserVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting geyser animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setGeyserAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setGeyserActionDelayTicks);
            }));
        } else if (button.id == FLUID_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting geyser fluid:",
                    FluidBlockUtil.getSelectableIds(), name -> {
                phase.setGeyserFluid(name);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setGeyserAnimation(value);
            else animation.setValue(phase.getGeyserAnimation());
        }
        GuiTextFieldNop fluid = getTextField(FLUID_FIELD);
        if (fluid != null) {
            String value = fluid.getValue().trim();
            // Empty is a real answer - the eruption simply leaves nothing behind - so only a
            // filled-in id that is not a fluid is rejected.
            if (value.isEmpty() || FluidBlockUtil.isFluidBlock(value)) phase.setGeyserFluid(value);
            else fluid.setValue(phase.getGeyserFluid());
        }
        GuiTextFieldNop count = getTextField(TARGET_COUNT_FIELD);
        if (count != null) phase.setGeyserTargetCount(count.getInteger());
        GuiTextFieldNop fuse = getTextField(FUSE_FIELD);
        if (fuse != null) phase.setGeyserFuseTicks(fuse.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setGeyserDamage(damage.getInteger());
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        if (radius != null) phase.setGeyserRadius(radius.getInteger());
        GuiTextFieldNop launch = getTextField(LAUNCH_FIELD);
        if (launch != null) phase.setGeyserLaunch(launch.getInteger());
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.setGeyserRange(min.getInteger(), max.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setGeyserActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setGeyserCooldownTicks(cooldown.getInteger());
        GuiTextFieldNop fluidLife = getTextField(FLUID_LIFE_FIELD);
        if (fluidLife != null) phase.setGeyserFluidLifetimeTicks(fluidLife.getInteger());
    }
}
