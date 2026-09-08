package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BoulderStyles;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Boulder rain: stones dropped out of the sky in a ring around the boss. */
public final class SubGuiBossBoulderRain extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int BLOCK_FIELD = 3;
    private static final int LOOK_BUTTON = 4;
    private static final int RADIUS_FIELD = 5;
    private static final int MIN_RADIUS_FIELD = 6;
    private static final int COUNT_FIELD = 7;
    private static final int INTERVAL_FIELD = 8;
    private static final int FALL_HEIGHT_FIELD = 9;
    private static final int SCALE_FIELD = 10;
    private static final int DAMAGE_FIELD = 11;
    private static final int KNOCKBACK_FIELD = 12;
    private static final int SHATTER_DAMAGE_FIELD = 13;
    private static final int SHATTER_RADIUS_FIELD = 14;
    private static final int ACTION_DELAY_FIELD = 15;
    private static final int COOLDOWN_FIELD = 16;
    private static final int VFX_STYLE_BUTTON = 17;
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

    public SubGuiBossBoulderRain(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // The ring, the volley, the stone and what it does on landing are four sets of
        // numbers, and a builder tuning the height against the interval reads both at once.
        imageHeight = 322;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.boulder_rain_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.boulderRain().isEnabled()));
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.boulderRain().getAnimation());
        y += 21;

        // Typed rather than picked, for the reason the corridor boulder's is: any block id
        // works, and a list of every block in the game would bury the four a dungeon wants.
        addLabel(new GuiLabel(BLOCK_FIELD, "cnpcgeckoaddon.boss.boulder_block", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(BLOCK_FIELD, this, guiLeft + 108, y, 134, 20,
                phase.boulderRain().getBlock()));
        y += 21;

        addLabel(new GuiLabel(LOOK_BUTTON, "cnpcgeckoaddon.boss.boulder_style", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, LOOK_BUTTON, guiLeft + 112, y, 130, 20,
                LOOK_LABELS, lookIndex()));
        y += 21;

        addPairRow(RADIUS_FIELD, MIN_RADIUS_FIELD, "cnpcgeckoaddon.boss.boulder_rain_ring", y,
                phase.boulderRain().getRadius(), 2, 48, 12,
                phase.boulderRain().getMinRadius(), 0, 47, 0);
        y += 21;
        addPairRow(COUNT_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.boulder_rain_volley", y,
                phase.boulderRain().getCount(), 1, 32, 8,
                phase.boulderRain().getIntervalTicks(), 0, 100, 4);
        y += 21;
        addNumberField(FALL_HEIGHT_FIELD, "cnpcgeckoaddon.boss.boulder_rain_height", y,
                phase.boulderRain().getFallHeight(), 4, 48, 16);
        y += 21;
        addNumberField(SCALE_FIELD, "cnpcgeckoaddon.boss.boulder_rain_size", y,
                phase.boulderRain().getScale(), 5, 40, 12);
        y += 21;
        addPairRow(DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.boulder_rain_hit", y,
                phase.boulderRain().getDamage(), 0, 1000, 10,
                phase.boulderRain().getKnockback(), 0, 10, 2);
        y += 21;
        addPairRow(SHATTER_DAMAGE_FIELD, SHATTER_RADIUS_FIELD, "cnpcgeckoaddon.boss.boulder_rain_shatter", y,
                phase.boulderRain().getShatterDamage(), 0, 1000, 4,
                phase.boulderRain().getShatterRadius(), 0, 16, 2);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.boulderRain().getActionDelayTicks(), 0, 1200, 16,
                phase.boulderRain().getCooldownTicks(), 1, 12000, 240);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));

        addWrappedHint(31, "cnpcgeckoaddon.boss.boulder_rain_hint", guiTop + 274);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 296, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 296, 60, 20);
    }

    private int lookIndex() {
        String id = phase.boulderRain().getStyle();
        for (int i = 0; i < BoulderStyles.values().size(); i++) {
            if (BoulderStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private int vfxStyleIndex() {
        String id = phase.boulderRain().getVfx();
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
            setSubGui(new SubGuiBossEffectList(phase.boulderRain().getEffects(),
                    "cnpcgeckoaddon.boss.effects_boulder_rain"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.boulderRain().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.boulderRain().setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == LOOK_BUTTON) {
            phase.boulderRain().setStyle(BoulderStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.boulder_rain_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.boulderRain().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase.boulderRain()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.boulderRain().setAnimation(value);
            else animation.setValue(phase.boulderRain().getAnimation());
        }
        GuiTextFieldNop block = getTextField(BLOCK_FIELD);
        if (block != null) {
            String value = block.getValue().trim();
            // An id that is not a block would silently never drop, so reject it here.
            if (EntityBossBoulder.resolveBlock(value) != null) phase.boulderRain().setBlock(value);
            else block.setValue(phase.boulderRain().getBlock());
        }
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        GuiTextFieldNop minRadius = getTextField(MIN_RADIUS_FIELD);
        // Set as a pair: the inner edge is only legal against the outer one.
        if (radius != null && minRadius != null) {
            phase.boulderRain().setRing(radius.getInteger(), minRadius.getInteger());
        }
        applyNumberField(COUNT_FIELD, phase.boulderRain()::setCount);
        applyNumberField(INTERVAL_FIELD, phase.boulderRain()::setIntervalTicks);
        applyNumberField(FALL_HEIGHT_FIELD, phase.boulderRain()::setFallHeight);
        applyNumberField(SCALE_FIELD, phase.boulderRain()::setScale);
        applyNumberField(DAMAGE_FIELD, phase.boulderRain()::setDamage);
        applyNumberField(KNOCKBACK_FIELD, phase.boulderRain()::setKnockback);
        applyNumberField(SHATTER_DAMAGE_FIELD, phase.boulderRain()::setShatterDamage);
        applyNumberField(SHATTER_RADIUS_FIELD, phase.boulderRain()::setShatterRadius);
        applyNumberField(ACTION_DELAY_FIELD, phase.boulderRain()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.boulderRain()::setCooldownTicks);
    }
}
