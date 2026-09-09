package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.NpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryData;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Whether ordinary players may carry this npc, and what carrying it costs them. */
public final class SubGuiNpcCarry extends SubGuiFieldScreen {
    private static final int CARRYABLE_BUTTON = 1;
    private static final int SNEAK_BUTTON = 2;
    private static final int ITEM_FIELD = 3;
    private static final int SLOWNESS_FIELD = 4;
    private static final int DROP_DAMAGE_BUTTON = 5;
    private static final int INVULNERABLE_BUTTON = 6;
    private static final int UPDATES_HOME_BUTTON = 7;
    private static final int LEASH_FIELD = 8;
    private static final int THROWABLE_BUTTON = 9;
    private static final int THROW_SPEED_FIELD = 10;
    private static final int THROW_DAMAGE_FIELD = 11;
    private static final int THROW_KNOCKBACK_FIELD = 12;
    private static final int THROW_SELF_DAMAGE_FIELD = 13;
    private static final int THROW_BOMB_BUTTON = 14;
    private static final int THROW_COOLDOWN_FIELD = 15;
    private static final int THROW_TITLE_LABEL = 31;

    private static final int ROW_HEIGHT = 22;
    /** Where the buttons sit: under two hints of two lines each, in either locale. */
    private static final int DONE_Y = 396;

    private final NpcCarryData data;

    public SubGuiNpcCarry(DataAI ai) {
        data = ((INpcCarryData) ai).cnpcgeckoaddon$getNpcCarryData();
        imageWidth = 256;
        // Fifteen rows and two hints outgrow the panel, so the screen is as tall as it
        // needs and scrolls.
        imageHeight = DONE_Y + 28;
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
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(THROW_TITLE_LABEL, "cnpcgeckoaddon.carry.throw_title",
                guiLeft + 8, y + 4, 0xFFFFFF));
        y += 16;

        addYesNo(THROWABLE_BUTTON, "cnpcgeckoaddon.carry.throw_enabled", y, data.isThrowable());
        y += ROW_HEIGHT;

        addNumberField(THROW_SPEED_FIELD, "cnpcgeckoaddon.carry.throw_speed", y,
                data.getThrowSpeed(), NpcCarryData.MIN_THROW_SPEED, NpcCarryData.MAX_THROW_SPEED,
                NpcCarryData.DEFAULT_THROW_SPEED);
        y += ROW_HEIGHT;

        addPairRow(THROW_DAMAGE_FIELD, THROW_KNOCKBACK_FIELD, "cnpcgeckoaddon.carry.throw_hit", y,
                data.getThrowDamage(), 0, NpcCarryData.MAX_THROW_DAMAGE, NpcCarryData.DEFAULT_THROW_DAMAGE,
                data.getThrowKnockback(), 0, NpcCarryData.MAX_THROW_KNOCKBACK,
                NpcCarryData.DEFAULT_THROW_KNOCKBACK);
        y += ROW_HEIGHT;

        addNumberField(THROW_SELF_DAMAGE_FIELD, "cnpcgeckoaddon.carry.throw_self_damage", y,
                data.getThrowSelfDamage(), 0, NpcCarryData.MAX_THROW_DAMAGE, 0);
        y += ROW_HEIGHT;

        addYesNo(THROW_BOMB_BUTTON, "cnpcgeckoaddon.carry.throw_bomb", y, data.isThrowDiesOnImpact());
        y += ROW_HEIGHT;

        addNumberField(THROW_COOLDOWN_FIELD, "cnpcgeckoaddon.carry.throw_cooldown", y,
                data.getThrowCooldownTicks(), 0, NpcCarryData.MAX_THROW_COOLDOWN_TICKS,
                NpcCarryData.DEFAULT_THROW_COOLDOWN_TICKS);
        y += ROW_HEIGHT;

        int hintY = addWrappedHint(40, "cnpcgeckoaddon.carry.hint", y + 6);
        addWrappedHint(50, "cnpcgeckoaddon.carry.throw_hint", hintY);
        addDoneButton(guiLeft + 182, guiTop + DONE_Y, 60, 20);
    }

    /** Two short numbers on one row, for the pair that is read together: damage and shove. */
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
        } else if (button.id == THROWABLE_BUTTON) {
            data.setThrowable(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == THROW_BOMB_BUTTON) {
            data.setThrowDiesOnImpact(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop item = getTextField(ITEM_FIELD);
        if (item != null) {
            applyItemId(item);
        }
        applyNumberField(SLOWNESS_FIELD, data::setSlownessPercent);
        applyNumberField(LEASH_FIELD, data::setLeashRadius);
        applyNumberField(THROW_SPEED_FIELD, data::setThrowSpeed);
        applyNumberField(THROW_DAMAGE_FIELD, data::setThrowDamage);
        applyNumberField(THROW_KNOCKBACK_FIELD, data::setThrowKnockback);
        applyNumberField(THROW_SELF_DAMAGE_FIELD, data::setThrowSelfDamage);
        applyNumberField(THROW_COOLDOWN_FIELD, data::setThrowCooldownTicks);
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
    @Override
    protected int toggleButtonX() {
        return 155;
    }

    @Override
    protected int toggleButtonWidth() {
        return 87;
    }

}
