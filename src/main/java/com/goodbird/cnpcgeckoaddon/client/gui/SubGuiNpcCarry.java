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

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

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
    private static final int DISTANCE_FIELD = 16;
    private static final int DROP_FIELD = 17;
    private static final int PLACE_REACH_FIELD = 18;
    private static final int PREVIEW_FREE_FIELD = 19;
    private static final int PREVIEW_BLOCKED_FIELD = 20;
    private static final int THROW_GRAVITY_FIELD = 21;
    private static final int THROW_LIFT_FIELD = 22;
    private static final int THROW_MAX_FLIGHT_FIELD = 23;
    private static final int THROW_TITLE_LABEL = 31;

    private static final int FIRST_ROW_Y = 26;
    private static final int ROW_HEIGHT = 22;
    /** Every row the screen lays out, counted before the panel is sized on them. */
    private static final int ROWS = 22;
    /** The gap the throw heading sits in, between the two halves of the screen. */
    private static final int SECTION_GAP = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 8;
    private static final String HINT = "cnpcgeckoaddon.carry.hint";
    private static final String THROW_HINT = "cnpcgeckoaddon.carry.throw_hint";
    private static final String TUNING_HINT = "cnpcgeckoaddon.npc.tuning_hint";

    private final NpcCarryData data;

    public SubGuiNpcCarry(DataAI ai) {
        data = ((INpcCarryData) ai).cnpcgeckoaddon$getNpcCarryData();
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Two dozen rows and three hints outgrow the panel, so the screen is as tall as it
        // needs and scrolls. Settled before super.init() centres the panel on it: how many
        // lines each hint wraps to is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.carry.title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + FIRST_ROW_Y;

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

        addNumberField(DISTANCE_FIELD, "cnpcgeckoaddon.carry.distance", y,
                data.getCarryDistanceTenths(), NpcCarryData.MIN_CARRY_DISTANCE,
                NpcCarryData.MAX_CARRY_DISTANCE, NpcCarryData.DEFAULT_CARRY_DISTANCE);
        y += ROW_HEIGHT;

        addNumberField(DROP_FIELD, "cnpcgeckoaddon.carry.drop", y,
                data.getCarryDropHundredths(), 0, NpcCarryData.MAX_CARRY_DROP,
                NpcCarryData.DEFAULT_CARRY_DROP);
        y += ROW_HEIGHT;

        addNumberField(PLACE_REACH_FIELD, "cnpcgeckoaddon.carry.place_reach", y,
                data.getPlaceReach(), NpcCarryData.MIN_PLACE_REACH, NpcCarryData.MAX_PLACE_REACH,
                NpcCarryData.DEFAULT_PLACE_REACH);
        y += ROW_HEIGHT;

        addColorField(PREVIEW_FREE_FIELD, "cnpcgeckoaddon.carry.preview_free", y,
                data.getPreviewFreeColor());
        y += ROW_HEIGHT;

        addColorField(PREVIEW_BLOCKED_FIELD, "cnpcgeckoaddon.carry.preview_blocked", y,
                data.getPreviewBlockedColor());
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(THROW_TITLE_LABEL, "cnpcgeckoaddon.carry.throw_title",
                guiLeft + 8, y + 4, 0xFFFFFF));
        y += SECTION_GAP;

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

        addNumberField(THROW_GRAVITY_FIELD, "cnpcgeckoaddon.carry.throw_gravity", y,
                data.getThrowGravityThousandths(), NpcCarryData.MIN_THROW_GRAVITY,
                NpcCarryData.MAX_THROW_GRAVITY, NpcCarryData.DEFAULT_THROW_GRAVITY);
        y += ROW_HEIGHT;

        addNumberField(THROW_LIFT_FIELD, "cnpcgeckoaddon.carry.throw_lift", y,
                data.getThrowLiftHundredths(), 0, NpcCarryData.MAX_THROW_LIFT,
                NpcCarryData.DEFAULT_THROW_LIFT);
        y += ROW_HEIGHT;

        addNumberField(THROW_MAX_FLIGHT_FIELD, "cnpcgeckoaddon.carry.throw_max_flight", y,
                data.getThrowMaxFlightTicks(), NpcCarryData.MIN_THROW_MAX_FLIGHT_TICKS,
                NpcCarryData.MAX_THROW_MAX_FLIGHT_TICKS, NpcCarryData.DEFAULT_THROW_MAX_FLIGHT_TICKS);

        int hintY = addWrappedHint(40, HINT, guiTop + hintY());
        hintY = addWrappedHint(50, THROW_HINT, hintY);
        addWrappedHint(60, TUNING_HINT, hintY);
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the hints start, from the panel's top: just under the last row. */
    private int hintY() {
        return FIRST_ROW_Y + ROWS * ROW_HEIGHT + SECTION_GAP + 6;
    }

    /** Where the done button goes, from the panel's top: just under the three hints. */
    private int doneButtonY() {
        return hintY() + wrappedHintHeight(HINT) + wrappedHintHeight(THROW_HINT)
                + wrappedHintHeight(TUNING_HINT) + 4;
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

    /**
     * One of the preview ring's colours, typed as hex in the number fields' own column.
     *
     * <p>A plain field rather than a number one: a colour is six hex digits, and a
     * numbers-only field refuses every letter in one.</p>
     */
    private void addColorField(int id, String label, int y, int color) {
        addLabel(new GuiLabel(id, label, guiLeft + numberLabelX(), y + numberLabelYOffset()));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + numberFieldX(), y,
                numberFieldWidth(), numberFieldHeight(), hex(color)));
    }

    private static String hex(int color) {
        return String.format("%06X", color);
    }

    /**
     * Reads one hex colour back, and writes the stored one into the field.
     *
     * <p>Anything that is not a colour leaves the setting alone: half a colour is what every
     * one of them looks like while it is being typed, and a field that emptied the setting on
     * the way through would lose it on the first keystroke.</p>
     */
    private void applyColorField(int id, IntSupplier current, IntConsumer setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String typed = field.getValue().trim();
        if (typed.startsWith("#")) {
            typed = typed.substring(1);
        }
        try {
            setter.accept(Integer.parseInt(typed, 16));
        } catch (NumberFormatException ignored) {
            // Not a colour: the field goes back to the one that is set.
        }
        field.setValue(hex(current.getAsInt()));
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
        applyNumberField(DISTANCE_FIELD, data::setCarryDistanceTenths);
        applyNumberField(DROP_FIELD, data::setCarryDropHundredths);
        applyNumberField(PLACE_REACH_FIELD, data::setPlaceReach);
        applyColorField(PREVIEW_FREE_FIELD, data::getPreviewFreeColor, data::setPreviewFreeColor);
        applyColorField(PREVIEW_BLOCKED_FIELD, data::getPreviewBlockedColor,
                data::setPreviewBlockedColor);
        applyNumberField(THROW_GRAVITY_FIELD, data::setThrowGravityThousandths);
        applyNumberField(THROW_LIFT_FIELD, data::setThrowLiftHundredths);
        applyNumberField(THROW_MAX_FLIGHT_FIELD, data::setThrowMaxFlightTicks);
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
