package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.NpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryData;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Whether ordinary players may carry this npc, and what carrying it costs them. */
public final class SubGuiNpcCarry extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int CARRYABLE_BUTTON = 1;
    private static final int SNEAK_BUTTON = 2;
    private static final int ITEM_FIELD = 3;
    private static final int SLOWNESS_FIELD = 4;
    private static final int DROP_DAMAGE_BUTTON = 5;
    private static final int INVULNERABLE_BUTTON = 6;
    private static final int UPDATES_HOME_BUTTON = 7;
    private static final int LEASH_FIELD = 8;

    private static final int ROW_HEIGHT = 22;

    private final NpcCarryData data;

    public SubGuiNpcCarry(DataAI ai) {
        data = ((INpcCarryData) ai).cnpcgeckoaddon$getNpcCarryData();
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.carry.title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addYesNo(CARRYABLE_BUTTON, "cnpcgeckoaddon.carry.carryable", y, data.isCarryable());
        y += ROW_HEIGHT;

        addYesNo(SNEAK_BUTTON, "cnpcgeckoaddon.carry.sneak", y, data.isRequireSneak());
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(ITEM_FIELD, "cnpcgeckoaddon.carry.item", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(ITEM_FIELD, this, guiLeft + 122, y, 120, 20,
                data.getRequiredItem()));
        y += ROW_HEIGHT;

        addNumberField(SLOWNESS_FIELD, "cnpcgeckoaddon.carry.slowness", y,
                data.getSlownessPercent(), 0, NpcCarryData.MAX_SLOWNESS_PERCENT,
                NpcCarryData.DEFAULT_SLOWNESS_PERCENT);
        y += ROW_HEIGHT;

        addYesNo(DROP_DAMAGE_BUTTON, "cnpcgeckoaddon.carry.drop_damage", y, data.isDropOnDamage());
        y += ROW_HEIGHT;

        addYesNo(INVULNERABLE_BUTTON, "cnpcgeckoaddon.carry.invulnerable", y, data.isInvulnerable());
        y += ROW_HEIGHT;

        addYesNo(UPDATES_HOME_BUTTON, "cnpcgeckoaddon.carry.updates_home", y, data.isUpdatesHome());
        y += ROW_HEIGHT;

        addNumberField(LEASH_FIELD, "cnpcgeckoaddon.carry.leash", y, data.getLeashRadius(),
                0, NpcCarryData.MAX_LEASH_RADIUS, 0);

        addWrappedHint(40, "cnpcgeckoaddon.carry.hint", guiTop + 208);
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 155, y, 87, 20, value));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == CARRYABLE_BUTTON) {
            data.setCarryable(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SNEAK_BUTTON) {
            data.setRequireSneak(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == DROP_DAMAGE_BUTTON) {
            data.setDropOnDamage(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == INVULNERABLE_BUTTON) {
            data.setInvulnerable(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == UPDATES_HOME_BUTTON) {
            data.setUpdatesHome(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyFields();
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop item = getTextField(ITEM_FIELD);
        if (item != null) {
            applyItemId(item);
        }
        GuiTextFieldNop slowness = getTextField(SLOWNESS_FIELD);
        if (slowness != null) {
            data.setSlownessPercent(slowness.getInteger());
        }
        GuiTextFieldNop leash = getTextField(LEASH_FIELD);
        if (leash != null) {
            data.setLeashRadius(leash.getInteger());
        }
    }

    private void applyItemId(GuiTextFieldNop field) {
        String value = field.getValue().trim();
        if (value.isEmpty()) {
            data.setRequiredItem("");
            return;
        }
        ResourceLocation location = AnimationFileUtil.parse(value);
        if (location == null || BuiltInRegistries.ITEM.getOptional(location).isEmpty()) {
            // A typo here reads as an item nobody is holding, which locks every player out of
            // an npc that looks configured. Refuse it while the editor is still on the field.
            field.setValue(data.getRequiredItem());
            return;
        }
        // Stored the way the registry spells it, so a hand-typed "torch" survives a reopen.
        data.setRequiredItem(location.toString());
        field.setValue(location.toString());
    }
}
