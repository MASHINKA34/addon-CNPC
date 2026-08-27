package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Boulder: a giant stone rolled or thrown down a corridor, breaking on whatever stops it. */
public final class SubGuiBossBoulder extends SubGuiFieldScreen implements ITextfieldListener {
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
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossBoulder(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 320;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.boulder_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isBoulderEnabled()));
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getBoulderAnimation());
        y += 21;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getBoulderTargetMode()));
        y += 21;

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.boulder_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.BOULDER_MODE_LABELS, phase.getBoulderMode()));
        y += 21;

        // Typed rather than picked: any block id works, and a list of every block in the
        // game would bury the four that a dungeon actually wants.
        addLabel(new GuiLabel(BLOCK_FIELD, "cnpcgeckoaddon.boss.boulder_block", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(BLOCK_FIELD, this, guiLeft + 108, y, 134, 20,
                phase.getBoulderBlock()));
        y += 21;

        addPairRow(SCALE_FIELD, SPEED_FIELD, "cnpcgeckoaddon.boss.boulder_size", y,
                phase.getBoulderScale(), 5, 40, 15,
                phase.getBoulderSpeed(), 1, 20, 6);
        y += 21;
        addPairRow(DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.boulder_hit", y,
                phase.getBoulderDamage(), 0, 1000, 12,
                phase.getBoulderKnockback(), 0, 10, 3);
        y += 21;
        addNumberField(RANGE_FIELD, "cnpcgeckoaddon.boss.boulder_range", y,
                phase.getBoulderRange(), 4, 64, 20);
        y += 21;

        addLabel(new GuiLabel(STOPS_BUTTON, "cnpcgeckoaddon.boss.boulder_stops", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, STOPS_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isBoulderStopsOnHit()));
        y += 21;

        addPairRow(SHATTER_RADIUS_FIELD, SHATTER_DAMAGE_FIELD, "cnpcgeckoaddon.boss.boulder_shatter", y,
                phase.getBoulderShatterRadius(), 0, 16, 2,
                phase.getBoulderShatterDamage(), 0, 1000, 4);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getBoulderActionDelayTicks(), 0, 1200, 16,
                phase.getBoulderCooldownTicks(), 1, 12000, 180);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));

        addWrappedHint(31, "cnpcgeckoaddon.boss.boulder_hint", guiTop + 274);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 296, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 296, 60, 20,
                "gui.done", button -> close()));
    }

    private int vfxStyleIndex() {
        String id = phase.getBoulderVfx();
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

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
            setSubGui(new SubGuiBossEffectList(phase.getBoulderEffects(), "cnpcgeckoaddon.boss.effects_boulder"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setBoulderEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STOPS_BUTTON) {
            phase.setBoulderStopsOnHit(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setBoulderTargetMode(button.getValue());
        } else if (button.id == MODE_BUTTON) {
            phase.setBoulderMode(button.getValue());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.setBoulderVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting boulder animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setBoulderAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setBoulderActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setBoulderAnimation(value);
            else animation.setValue(phase.getBoulderAnimation());
        }
        GuiTextFieldNop block = getTextField(BLOCK_FIELD);
        if (block != null) {
            String value = block.getValue().trim();
            // An id that is not a block would silently never launch, so reject it here.
            if (EntityBossBoulder.resolveBlock(value) != null) phase.setBoulderBlock(value);
            else block.setValue(phase.getBoulderBlock());
        }
        GuiTextFieldNop scale = getTextField(SCALE_FIELD);
        if (scale != null) phase.setBoulderScale(scale.getInteger());
        GuiTextFieldNop speed = getTextField(SPEED_FIELD);
        if (speed != null) phase.setBoulderSpeed(speed.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setBoulderDamage(damage.getInteger());
        GuiTextFieldNop knockback = getTextField(KNOCKBACK_FIELD);
        if (knockback != null) phase.setBoulderKnockback(knockback.getInteger());
        GuiTextFieldNop range = getTextField(RANGE_FIELD);
        if (range != null) phase.setBoulderRange(range.getInteger());
        GuiTextFieldNop shatterRadius = getTextField(SHATTER_RADIUS_FIELD);
        if (shatterRadius != null) phase.setBoulderShatterRadius(shatterRadius.getInteger());
        GuiTextFieldNop shatterDamage = getTextField(SHATTER_DAMAGE_FIELD);
        if (shatterDamage != null) phase.setBoulderShatterDamage(shatterDamage.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setBoulderActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setBoulderCooldownTicks(cooldown.getInteger());
    }
}
