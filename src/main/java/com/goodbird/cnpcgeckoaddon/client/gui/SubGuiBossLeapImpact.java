package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/**
 * What the leap does when it comes down: the slam, its wave and the landing marker.
 *
 * <p>A second page for the same reason the capture has one - the jump itself already fills
 * a screen, and cramming ten more rows under it would leave nothing readable.</p>
 */
public final class SubGuiBossLeapImpact extends SubGuiFieldScreen implements ITextfieldListener {
    /** Read by the leap screen, which points a picked animation's length at this delay. */
    static final int ACTION_DELAY_FIELD = 1;
    private static final int COOLDOWN_FIELD = 2;
    private static final int DAMAGE_FIELD = 3;
    private static final int RADIUS_FIELD = 4;
    private static final int KNOCKBACK_FIELD = 5;
    private static final int AIR_TICKS_FIELD = 6;
    private static final int TELEGRAPH_BUTTON = 7;
    private static final int VFX_STYLE_BUTTON = 8;
    private static final int BLOCK_WAVE_BUTTON = 9;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossLeapImpact(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 218;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.leap_impact_phase", phaseIndex), guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getLeapActionDelayTicks(), 0, 1200, 12,
                phase.getLeapCooldownTicks(), 1, 12000, 200);
        y += 21;
        addPairRow(DAMAGE_FIELD, RADIUS_FIELD, "cnpcgeckoaddon.boss.leap_impact", y,
                phase.getLeapImpactDamage(), 0, 1000, 10,
                phase.getLeapImpactRadius(), 1, 32, 4);
        y += 21;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.leap_knockback", y,
                phase.getLeapImpactKnockback(), 0, 10, 2);
        y += 21;
        addNumberField(AIR_TICKS_FIELD, "cnpcgeckoaddon.boss.leap_air_ticks", y,
                phase.getLeapMaxAirTicks(), 20, 400, 100);
        y += 21;

        addLabel(new GuiLabel(TELEGRAPH_BUTTON, "cnpcgeckoaddon.boss.leap_telegraph", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, TELEGRAPH_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isLeapTelegraph()));
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;

        addLabel(new GuiLabel(BLOCK_WAVE_BUTTON, "cnpcgeckoaddon.boss.area_block_wave", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, BLOCK_WAVE_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isLeapBlockWave()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.leap_hint", guiLeft + 6, guiTop + 170, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.enemies_hint", guiLeft + 6, guiTop + 182, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 194, 60, 20,
                "gui.done", button -> close()));
    }

    private int vfxStyleIndex() {
        String id = phase.getLeapVfx();
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
        // This screen family starts its labels a column tighter than the shared default.
        return 6;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == TELEGRAPH_BUTTON) {
            phase.setLeapTelegraph(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BLOCK_WAVE_BUTTON) {
            phase.setLeapBlockWave(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.setLeapVfx(AreaVfxStyles.values().get(button.getValue()).id());
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
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setLeapActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setLeapCooldownTicks(cooldown.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setLeapImpactDamage(damage.getInteger());
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        if (radius != null) phase.setLeapImpactRadius(radius.getInteger());
        GuiTextFieldNop knockback = getTextField(KNOCKBACK_FIELD);
        if (knockback != null) phase.setLeapImpactKnockback(knockback.getInteger());
        GuiTextFieldNop airTicks = getTextField(AIR_TICKS_FIELD);
        if (airTicks != null) phase.setLeapMaxAirTicks(airTicks.getInteger());
    }
}
