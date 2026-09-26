package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.BoulderStyles;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Boulder: a giant stone rolled or thrown down a corridor, breaking on whatever stops it. */
public final class SubGuiBossBoulder extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_MODE_BUTTON = 3;
    private static final int MODE_BUTTON = 4;
    private static final int BLOCK_FIELD = 5;
    private static final int SCALE_FIELD = 6;
    private static final int SPEED_FIELD = 7;
    private static final int DAMAGE_FIELD = 8;
    private static final int KNOCKBACK_FIELD = 9;
    private static final int RANGE_FIELD = 10;
    private static final int STOPS_BUTTON = 11;
    private static final int SHATTER_RADIUS_FIELD = 12;
    private static final int SHATTER_DAMAGE_FIELD = 13;
    private static final int ACTION_DELAY_FIELD = 14;
    private static final int COOLDOWN_FIELD = 15;
    private static final int VFX_STYLE_BUTTON = 16;
    private static final int LOOK_BUTTON = 17;
    private static final int TUNING_BUTTON = 18;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private static final String[] LOOK_LABELS = BoulderStyles.values().stream()
            .map(BoulderStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossBoulder(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // One row taller than the panel it used to be: the way into the roll's own
        // physics sits under the hints, above the buttons.
        imageHeight = 376;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.boulder_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new ThemeLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new ThemeYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.boulder().isEnabled()));
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.boulder().getAnimation());
        y += 21;

        addLabel(new ThemeLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.boulder().getTargetMode()));
        y += 21;

        addLabel(new ThemeLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.boulder_mode", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.BOULDER_MODE_LABELS, phase.boulder().getMode()));
        y += 21;

        // Typed rather than picked: any block id works, and a list of every block in the
        // game would bury the four that a dungeon actually wants.
        addLabel(new ThemeLabel(BLOCK_FIELD, "cnpcgeckoaddon.boss.boulder_block", guiLeft + 6, y + 6));
        addTextField(new ThemeTextField(BLOCK_FIELD, this, guiLeft + 108, y, 134, 20,
                phase.boulder().getBlock()));
        y += 21;

        addLabel(new ThemeLabel(LOOK_BUTTON, "cnpcgeckoaddon.boss.boulder_style", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, LOOK_BUTTON, guiLeft + 112, y, 130, 20,
                LOOK_LABELS, lookIndex()));
        y += 21;

        addPairRow(SCALE_FIELD, SPEED_FIELD, "cnpcgeckoaddon.boss.boulder_size", y,
                phase.boulder().getScale(), 5, 40, 15,
                phase.boulder().getSpeed(), 1, 20, 6);
        y += 21;
        addPairRow(DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.boulder_hit", y,
                phase.boulder().getDamage(), 0, 1000, 12,
                phase.boulder().getKnockback(), 0, 10, 3);
        y += 21;
        addNumberField(RANGE_FIELD, "cnpcgeckoaddon.boss.boulder_range", y,
                phase.boulder().getRange(), 4, 64, 20);
        y += 21;

        addLabel(new ThemeLabel(STOPS_BUTTON, "cnpcgeckoaddon.boss.boulder_stops", guiLeft + 6, y + 6));
        addButton(new ThemeYesNo(this, STOPS_BUTTON, guiLeft + 155, y, 87, 20,
                phase.boulder().isStopsOnHit()));
        y += 21;

        addPairRow(SHATTER_RADIUS_FIELD, SHATTER_DAMAGE_FIELD, "cnpcgeckoaddon.boss.boulder_shatter", y,
                phase.boulder().getShatterRadius(), 0, 16, 2,
                phase.boulder().getShatterDamage(), 0, 1000, 4);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.boulder().getActionDelayTicks(), 0, 1200, 16,
                phase.boulder().getCooldownTicks(), 1, 12000, 180);
        y += 21;

        addLabel(new ThemeLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.boulder_hint", guiTop + 290);
        addWrappedHint(40, "cnpcgeckoaddon.boss.boulder_style_hint", hintY);
        addButton(new ThemeButton(this, TUNING_BUTTON, guiLeft + 6, guiTop + 328, 236, 20,
                "cnpcgeckoaddon.boss.boulder_tuning"));
        addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 352, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 352, 60, 20);
    }

    private int lookIndex() {
        String id = phase.boulder().getStyle();
        for (int i = 0; i < BoulderStyles.values().size(); i++) {
            if (BoulderStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private int vfxStyleIndex() {
        String id = phase.boulder().getVfx();
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new ThemeLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new ThemeTextField(id, this, guiLeft + 108, y, 86, 20, value));
        addButton(new ThemeButton(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new ThemeLabel(leftId, label, guiLeft + 6, y + 6));
        addPairedField(leftId, guiLeft + 130, y, leftValue, leftMin, leftMax, leftFallback);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new ThemeTextField(id, this, x, y, 52, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
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
            setSubGui(new SubGuiBossEffectList(phase.boulder().getEffects(), "cnpcgeckoaddon.boss.effects_boulder"));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossBoulderTuning(phase.boulder()));
        } else if (button.id == ENABLED_BUTTON) {
            phase.boulder().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STOPS_BUTTON) {
            phase.boulder().setStopsOnHit(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.boulder().setTargetMode(button.getValue());
        } else if (button.id == MODE_BUTTON) {
            phase.boulder().setMode(button.getValue());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.boulder().setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == LOOK_BUTTON) {
            phase.boulder().setStyle(BoulderStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.boulder_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.boulder().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase.boulder()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.boulder().setAnimation(value);
            else animation.setValue(phase.boulder().getAnimation());
        }
        GuiTextFieldNop block = getTextField(BLOCK_FIELD);
        if (block != null) {
            String value = block.getValue().trim();
            // An id that is not a block would silently never launch, so reject it here.
            if (EntityBossBoulder.resolveBlock(value) != null) phase.boulder().setBlock(value);
            else block.setValue(phase.boulder().getBlock());
        }
        applyNumberField(SCALE_FIELD, phase.boulder()::setScale);
        applyNumberField(SPEED_FIELD, phase.boulder()::setSpeed);
        applyNumberField(DAMAGE_FIELD, phase.boulder()::setDamage);
        applyNumberField(KNOCKBACK_FIELD, phase.boulder()::setKnockback);
        applyNumberField(RANGE_FIELD, phase.boulder()::setRange);
        applyNumberField(SHATTER_RADIUS_FIELD, phase.boulder()::setShatterRadius);
        applyNumberField(SHATTER_DAMAGE_FIELD, phase.boulder()::setShatterDamage);
        applyNumberField(ACTION_DELAY_FIELD, phase.boulder()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.boulder()::setCooldownTicks);
    }
}
