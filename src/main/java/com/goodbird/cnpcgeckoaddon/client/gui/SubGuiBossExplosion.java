package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Makes the boss detonate when it dies. */
public final class SubGuiBossExplosion extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int MODE_BUTTON = 2;
    private static final int FIRE_BUTTON = 3;
    private static final int DELAY_FIELD = 4;
    private static final int POWER_FIELD = 5;
    private static final int SYNC_BUTTON = 6;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossExplosion(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.explosion", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.explosion_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, data.isExplosionEnabled()));
        y += 24;

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.explosion_mode", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 92, y, 150, 20,
                TeleportPathData.EXPLOSION_MODE_LABELS, data.getExplosionMode()));
        y += 24;

        addNumberField(POWER_FIELD, "cnpcgeckoaddon.boss.explosion_power", y,
                data.getExplosionPower(), 1, 20, 4);
        y += 24;

        addLabel(new GuiLabel(FIRE_BUTTON, "cnpcgeckoaddon.boss.explosion_fire", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, FIRE_BUTTON, guiLeft + 155, y, 87, 20, data.isExplosionFire()));
        y += 24;

        addNumberField(DELAY_FIELD, "cnpcgeckoaddon.boss.explosion_delay", y,
                data.getExplosionDelayTicks(), 0, 1200, 20);
        y += 24;

        // The boss plays the death animation configured on the model, so the delay wants to
        // match its length - otherwise it blows up in the middle of falling over.
        addButton(new GuiButtonNop(this, SYNC_BUTTON, guiLeft + 8, y, 234, 20,
                "cnpcgeckoaddon.boss.explosion_sync_death"));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.explosion_hint", guiLeft + 8, guiTop + 166, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setExplosionEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FIRE_BUTTON) {
            data.setExplosionFire(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            data.setExplosionMode(button.getValue());
        } else if (button.id == SYNC_BUTTON) {
            syncDelayToDeathAnimation();
        }
    }

    private void syncDelayToDeathAnimation() {
        String deathAnimation;
        try {
            deathAnimation = ((IDataDisplay) npc.display).getCustomModelData().getDeathAnim();
        } catch (Throwable ignored) {
            return;
        }
        BossAnimationGuiUtil.syncDelayToAnimation(this, npc, deathAnimation, DELAY_FIELD,
                data::setExplosionDelayTicks);
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop delay = getTextField(DELAY_FIELD);
        if (delay != null) data.setExplosionDelayTicks(delay.getInteger());
        GuiTextFieldNop power = getTextField(POWER_FIELD);
        if (power != null) data.setExplosionPower(power.getInteger());
    }
}
