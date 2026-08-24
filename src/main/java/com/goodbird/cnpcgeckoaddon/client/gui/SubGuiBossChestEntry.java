package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossLootEntry;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Editor for one slot of a boss loot chest. */
public final class SubGuiBossChestEntry extends GuiBasic implements ITextfieldListener {
    private static final int ITEM_FIELD = 1;
    private static final int FROM_HAND_BUTTON = 2;
    private static final int MIN_FIELD = 3;
    private static final int MAX_FIELD = 4;
    private static final int CHANCE_FIELD = 5;
    private static final int REMOVE_BUTTON = 6;

    private final BossLootEntry entry;

    public SubGuiBossChestEntry(BossLootEntry entry) {
        this.entry = entry;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.chest_entry", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addLabel(new GuiLabel(ITEM_FIELD, "cnpcgeckoaddon.boss.chest_item", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(ITEM_FIELD, this, guiLeft + 50, y, 96, 20, itemId(entry.getStack())));
        addButton(new GuiButtonNop(this, FROM_HAND_BUTTON, guiLeft + 150, y, 92, 20,
                "cnpcgeckoaddon.boss.chest_from_hand"));
        y += 24;

        addLabel(new GuiLabel(MIN_FIELD, "cnpcgeckoaddon.boss.chest_count", guiLeft + 8, y + 6));
        addTextField(numberField(MIN_FIELD, guiLeft + 150, y, 44, entry.getMinCount(), 1, 64, 1));
        addTextField(numberField(MAX_FIELD, guiLeft + 198, y, 44, entry.getMaxCount(), 1, 64, 1));
        y += 24;

        addLabel(new GuiLabel(CHANCE_FIELD, "cnpcgeckoaddon.boss.chest_chance", guiLeft + 8, y + 6));
        addTextField(numberField(CHANCE_FIELD, guiLeft + 172, y, 70, entry.getChancePercent(), 1, 100, 100));

        addButton(new GuiButtonNop(this, REMOVE_BUTTON, guiLeft + 8, guiTop + 104, 234, 20,
                "cnpcgeckoaddon.boss.chest_remove"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    private GuiTextFieldNop numberField(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        return field;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == FROM_HAND_BUTTON) {
            takeFromHand();
        } else if (button.id == REMOVE_BUTTON) {
            entry.clear();
            close();
        }
    }

    /**
     * Copies whatever the editor is holding into this slot.
     *
     * <p>Taking the whole stack is what makes enchantments, custom names and every other
     * component come along - typing an id can only ever name a plain item.</p>
     */
    private void takeFromHand() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        entry.setStack(held);
        getTextField(ITEM_FIELD).setValue(itemId(entry.getStack()));
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

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
        GuiTextFieldNop min = getTextField(MIN_FIELD);
        GuiTextFieldNop max = getTextField(MAX_FIELD);
        if (min != null && max != null) {
            entry.setCountRange(min.getInteger(), max.getInteger());
            // The setter keeps the two in order, so show back what it settled on.
            min.setValue(Integer.toString(entry.getMinCount()));
            max.setValue(Integer.toString(entry.getMaxCount()));
        }
        GuiTextFieldNop chance = getTextField(CHANCE_FIELD);
        if (chance != null) {
            entry.setChancePercent(chance.getInteger());
        }
    }

    private void applyItemId(GuiTextFieldNop field) {
        String value = field.getValue().trim();
        if (value.equals(itemId(entry.getStack()))) {
            // Untouched: leave the stack alone so its components survive editing the counts.
            return;
        }
        if (value.isEmpty()) {
            entry.setStack(ItemStack.EMPTY);
            return;
        }
        ResourceLocation location = AnimationFileUtil.parse(value);
        Item item = location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item == null) {
            // A typo would leave the slot holding something else entirely, so reject it
            // while the editor is still looking at the field.
            field.setValue(itemId(entry.getStack()));
            return;
        }
        entry.setStack(new ItemStack(item));
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
