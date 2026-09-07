package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

public final class SubGuiBossAreaAttack extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int DAMAGE_FIELD = 3;
    private static final int RADIUS_FIELD = 4;
    private static final int KNOCKBACK_FIELD = 5;
    private static final int ACTION_DELAY_FIELD = 6;
    private static final int COOLDOWN_FIELD = 7;
    private static final int VFX_STYLE_BUTTON = 8;
    private static final int VFX_DURATION_FIELD = 9;
    private static final int BLOCK_WAVE_BUTTON = 10;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossAreaAttack(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.area_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.areaAttack().isEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.areaAttack().getAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y, phase.areaAttack().getDamage(), 1, 1000, 8);
        y += 21;
        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.attack_radius", y, phase.areaAttack().getRadius(), 1, 32, 5);
        y += 21;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.knockback", y, phase.areaAttack().getKnockback(), 0, 10, 1);
        y += 21;
        // The two tick counts share a line so the wave settings below get rows of their own.
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.areaAttack().getActionDelayTicks(), 0, 1200, 12,
                phase.areaAttack().getCooldownTicks(), 1, 12000, 100);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;
        addNumberField(VFX_DURATION_FIELD, "cnpcgeckoaddon.boss.area_vfx_duration", y,
                phase.areaAttack().getVfxDurationTicks(), 5, 100, 20);
        y += 21;
        addLabel(new GuiLabel(BLOCK_WAVE_BUTTON, "cnpcgeckoaddon.boss.area_block_wave", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, BLOCK_WAVE_BUTTON, guiLeft + 155, y, 87, 20,
                phase.areaAttack().isBlockWave()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.area_vfx_hint", guiLeft + 8, guiTop + 209, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.enemies_hint", guiLeft + 8, guiTop + 221, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 67, guiLeft + 6, guiTop + 232, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 232, 60, 20);
    }

    private int vfxStyleIndex() {
        String id = phase.areaAttack().getVfx();
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new GuiLabel(leftId, label, guiLeft + 8, y + 6));
        addPairedField(leftId, guiLeft + 130, y, leftValue, leftMin, leftMax, leftFallback);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 52, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 67) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.areaAttack().getEffects(), "cnpcgeckoaddon.boss.effects_area"));
            return;
        }
        if (button.id == ENABLED_BUTTON) {
            phase.areaAttack().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BLOCK_WAVE_BUTTON) {
            phase.areaAttack().setBlockWave(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.areaAttack().setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting area attack animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.areaAttack().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase.areaAttack()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.areaAttack().setAnimation(value);
            else animation.setValue(phase.areaAttack().getAnimation());
        }
        applyNumberField(DAMAGE_FIELD, phase.areaAttack()::setDamage);
        applyNumberField(RADIUS_FIELD, phase.areaAttack()::setRadius);
        applyNumberField(KNOCKBACK_FIELD, phase.areaAttack()::setKnockback);
        applyNumberField(ACTION_DELAY_FIELD, phase.areaAttack()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.areaAttack()::setCooldownTicks);
        applyNumberField(VFX_DURATION_FIELD, phase.areaAttack()::setVfxDurationTicks);
    }
}
