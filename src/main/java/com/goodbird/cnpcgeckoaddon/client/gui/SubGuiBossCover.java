package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Take cover: a long wind-up, then a hit on the whole arena that spares only whoever hid. */
public final class SubGuiBossCover extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int MODE_BUTTON = 3;
    private static final int RANGE_FIELD = 4;
    private static final int DAMAGE_FIELD = 5;
    private static final int KNOCKBACK_FIELD = 6;
    private static final int SHELTER_COUNT_FIELD = 7;
    private static final int SHELTER_RADIUS_FIELD = 8;
    private static final int SHELTER_MIN_FIELD = 9;
    private static final int SHELTER_MAX_FIELD = 10;
    private static final int ACTION_DELAY_FIELD = 11;
    private static final int COOLDOWN_FIELD = 12;
    private static final int VFX_STYLE_BUTTON = 13;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossCover(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 262;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.cover_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isCoverEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 108, y, 86, 20,
                phase.getCoverAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        // The first thing to pick, because the two shelter rows below belong to one of the
        // rules only and come and go with it.
        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.cover_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.COVER_MODE_LABELS, phase.getCoverMode()));
        y += 21;

        // How far the strike reaches and what it costs to be caught in it, on one line.
        addPairRow(RANGE_FIELD, DAMAGE_FIELD, "cnpcgeckoaddon.boss.cover_range", y,
                phase.getCoverRange(), 4, 96, 40,
                phase.getCoverDamage(), 0, 1000, 40);
        y += 21;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.knockback", y,
                phase.getCoverKnockback(), 0, 10, 2);
        y += 21;

        // Shelter rule only: how many circles, how wide, and the ring they are scattered in.
        addPairRow(SHELTER_COUNT_FIELD, SHELTER_RADIUS_FIELD, "cnpcgeckoaddon.boss.cover_shelters", y,
                phase.getCoverShelterCount(), 1, 6, 2,
                phase.getCoverShelterRadius(), 1, 16, 3);
        y += 21;
        addPairRow(SHELTER_MIN_FIELD, SHELTER_MAX_FIELD, "cnpcgeckoaddon.boss.cover_shelter_ring", y,
                phase.getCoverShelterMinRange(), 1, 48, 4,
                phase.getCoverShelterMaxRange(), 2, 64, 14);
        y += 21;

        // The wind-up is the time to hide and the one warning, which is why it cannot go
        // under a second here any more than it can in the phase itself.
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getCoverActionDelayTicks(), 20, 1200, 80,
                phase.getCoverCooldownTicks(), 1, 12000, 500);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.cover_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 234);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY, 60, 20,
                "gui.done", button -> close()));
        applyModeRows();
    }

    /**
     * Takes the shelter rows off the screen while the sight rule is in force.
     *
     * <p>Hidden in place rather than laid out again: this GUI framework has no
     * widget-clearing rebuild, so a screen that re-flowed itself on every click of the rule
     * button would stack a second copy of every row on the first. The rows keep their line
     * and simply stop being drawn or clicked.</p>
     */
    private void applyModeRows() {
        boolean shelters = phase.getCoverMode() == BossPhaseData.COVER_MODE_SHELTER;
        showRow(SHELTER_COUNT_FIELD, shelters);
        showField(SHELTER_RADIUS_FIELD, shelters);
        showRow(SHELTER_MIN_FIELD, shelters);
        showField(SHELTER_MAX_FIELD, shelters);
    }

    /** Shows or hides one labelled number field, label and all. */
    private void showRow(int id, boolean shown) {
        GuiLabel label = getLabel(id);
        if (label != null) {
            label.enabled = shown;
        }
        showField(id, shown);
    }

    /** Shows or hides the right-hand half of a pair, which has no label of its own. */
    private void showField(int id, boolean shown) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) {
            field.enabled = shown;
        }
    }

    private int vfxStyleIndex() {
        String id = phase.getCoverVfx();
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
            setSubGui(new SubGuiBossEffectList(phase.getCoverEffects(), "cnpcgeckoaddon.boss.effects_cover"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setCoverEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            phase.setCoverMode(button.getValue());
            applyModeRows();
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.setCoverVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting take cover animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setCoverAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setCoverActionDelayTicks);
                // A clip shorter than the second the wind-up is held at leaves the phase on
                // that floor, and the field has to say so rather than show the clip's length.
                getTextField(ACTION_DELAY_FIELD).setValue(Integer.toString(phase.getCoverActionDelayTicks()));
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setCoverAnimation(value);
            else animation.setValue(phase.getCoverAnimation());
        }
        GuiTextFieldNop range = getTextField(RANGE_FIELD);
        if (range != null) phase.setCoverRange(range.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setCoverDamage(damage.getInteger());
        GuiTextFieldNop knockback = getTextField(KNOCKBACK_FIELD);
        if (knockback != null) phase.setCoverKnockback(knockback.getInteger());
        // Read whether or not the rule in force shows them: a hidden row keeps the numbers a
        // builder typed into it under the other rule, rather than losing them on a stray click.
        GuiTextFieldNop shelterCount = getTextField(SHELTER_COUNT_FIELD);
        if (shelterCount != null) phase.setCoverShelterCount(shelterCount.getInteger());
        GuiTextFieldNop shelterRadius = getTextField(SHELTER_RADIUS_FIELD);
        if (shelterRadius != null) phase.setCoverShelterRadius(shelterRadius.getInteger());
        GuiTextFieldNop shelterMin = getTextField(SHELTER_MIN_FIELD);
        GuiTextFieldNop shelterMax = getTextField(SHELTER_MAX_FIELD);
        // Set as a pair: the inner edge is only legal against the outer one.
        if (shelterMin != null && shelterMax != null) {
            phase.setCoverShelterRing(shelterMin.getInteger(), shelterMax.getInteger());
        }
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setCoverActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setCoverCooldownTicks(cooldown.getInteger());
    }
}
