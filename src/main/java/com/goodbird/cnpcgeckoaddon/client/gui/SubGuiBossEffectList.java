package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossEffectData;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** The potion effects one attack applies, one row per slot. */
public final class SubGuiBossEffectList extends GuiBasic {
    private static final int FIRST_SLOT_BUTTON = 100;

    private final BossEffectSet effects;
    private final String titleKey;

    public SubGuiBossEffectList(BossEffectSet effects, String titleKey) {
        this.effects = effects;
        this.titleKey = titleKey;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, titleKey, guiLeft + 8, guiTop + 8, 0xFFFFFF));

        int y = guiTop + 28;
        for (int i = 0; i < BossEffectSet.SLOTS; i++) {
            addButton(new GuiButtonNop(this, FIRST_SLOT_BUTTON + i, guiLeft + 8, y, 234, 22, slotLabel(i)));
            y += 26;
        }

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.effects_hint", guiLeft + 8, guiTop + 120, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    /** "1. minecraft:poison  II  5s" or "1. -" when the slot is switched off. */
    private String slotLabel(int index) {
        BossEffectData effect = effects.get(index);
        if (!effect.isEnabled()) {
            return (index + 1) + ". " + I18n.get("cnpcgeckoaddon.boss.effect_off");
        }
        return (index + 1) + ". " + effect.getEffectId()
                + "  " + I18n.get("cnpcgeckoaddon.boss.effect_short_level") + effect.getLevel()
                + "  " + (effect.getDurationTicks() / 20) + "s";
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_SLOT_BUTTON;
        if (index >= 0 && index < BossEffectSet.SLOTS) {
            setSubGui(new SubGuiBossEffect(effects.get(index)));
        }
    }

    @Override
    public void subGuiClosed(Screen subgui) {
        super.subGuiClosed(subgui);
        // Relabel in place: this GUI framework has no widget-clearing rebuild, so calling
        // init() again would stack a second set of buttons on top of the first.
        for (int i = 0; i < BossEffectSet.SLOTS; i++) {
            GuiButtonNop button = getButton(FIRST_SLOT_BUTTON + i);
            if (button != null) {
                button.setDisplayText(slotLabel(i));
            }
        }
    }
}
