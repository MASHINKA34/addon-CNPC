package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.utils.FloatTextFieldUtils;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class SubGuiModelExtras extends GuiNPCInterface implements ITextfieldListener {
    private static final int FIELD_HEAD_BONE = 1;
    private static final int FIELD_TRANSITION = 2;
    private static final int FIELD_WIDTH = 3;
    private static final int FIELD_HEIGHT = 4;
    private static final int FIELD_SCALE = 7;
    private static final int BUTTON_HURT_TINT = 5;
    private static final int BUTTON_AUTO_HITBOX = 6;
    private static final int LABEL_AUTO_SIZE = 8;
    private static final int BUTTON_CLOSE = 670;

    public SubGuiModelExtras(EntityNPCInterface npc){
        this.npc = npc;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        int y = guiTop + 30;

        addLabel(new GuiLabel(FIELD_HEAD_BONE,"Head Bone Name", guiLeft - 85, y + 5,0xffffff));
        addTextField(new GuiTextFieldNop(FIELD_HEAD_BONE,this, guiLeft + 50, y, 200, 20, getModelData(npc).getHeadBoneName()));
        y+=23;

        addLabel(new GuiLabel(FIELD_TRANSITION,"Transition Length (ticks)", guiLeft - 85, y + 5,0xffffff));
        GuiTextFieldNop transitionLength = new GuiTextFieldNop(FIELD_TRANSITION,this, guiLeft + 50, y,
                200, 20, ""+getModelData(npc).getTransitionLengthTicks());
        transitionLength.setNumbersOnly();
        transitionLength.setMinMaxDefault(0, Integer.MAX_VALUE, getModelData(npc).getTransitionLengthTicks());
        addTextField(transitionLength);
        y+=23;

        // With this on, the box comes from the model's own geometry instead of the
        // humanoid default that used to apply to every model alike.
        addLabel(new GuiLabel(BUTTON_AUTO_HITBOX,"Hitbox From Model", guiLeft - 85, y + 5,0xffffff));
        addButton(new GuiButtonYesNo(this, BUTTON_AUTO_HITBOX, guiLeft + 50, y, 200, 20, getModelData(npc).isAutoHitbox()));
        y+=23;

        addLabel(new GuiLabel(LABEL_AUTO_SIZE, modelSizeText(), guiLeft + 50, y + 2, 0xa0a0a0));
        y+=14;

        addLabel(new GuiLabel(FIELD_WIDTH,"Manual Hitbox Width", guiLeft - 85, y + 5,0xffffff));
        addTextField(new GuiTextFieldNop(FIELD_WIDTH,this, guiLeft + 50, y, 200, 20, ""+getModelData(npc).getWidth()));
        y+=23;

        addLabel(new GuiLabel(FIELD_HEIGHT,"Manual Hitbox Height", guiLeft - 85, y + 5,0xffffff));
        addTextField(new GuiTextFieldNop(FIELD_HEIGHT,this, guiLeft + 50, y, 200, 20, ""+getModelData(npc).getHeight()));
        y+=23;

        addLabel(new GuiLabel(FIELD_SCALE,"Hitbox Scale", guiLeft - 85, y + 5,0xffffff));
        addTextField(new GuiTextFieldNop(FIELD_SCALE,this, guiLeft + 50, y, 200, 20, ""+getModelData(npc).getHitboxScale()));
        y+=23;

        addLabel(new GuiLabel(BUTTON_HURT_TINT,"Enable Hurt Tint", guiLeft - 85, y + 5,0xffffff));
        addButton(new GuiButtonYesNo(this, BUTTON_HURT_TINT, guiLeft + 50, y, 200, 20, getModelData(npc).isHurtTintEnabled()));

        addButton(new GuiButtonNop(this, BUTTON_CLOSE, width - 22, 2, 20, 20, "X"));
    }

    /**
     * Spells out the box the current model asks for, so the manual fields below
     * are edited against a known number rather than guessed at.
     */
    private String modelSizeText() {
        float[] derived = getModelData(npc).getDerivedHitbox();
        if (derived == null) {
            return "Model records no size, manual values are used";
        }
        return String.format("Model size: %.2f wide, %.2f tall", derived[0], derived[1]);
    }

    public CustomModelData getModelData(EntityNPCInterface npc){
        return ((IDataDisplay)npc.display).getCustomModelData();
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if(button.id == BUTTON_AUTO_HITBOX){
            getModelData(npc).setAutoHitbox(((GuiButtonYesNo)button).getBoolean());
            // Turning it off seeds the manual box from the model, so show that.
            getTextField(FIELD_WIDTH).setValue(""+getModelData(npc).getWidth());
            getTextField(FIELD_HEIGHT).setValue(""+getModelData(npc).getHeight());
        }
        if(button.id == BUTTON_HURT_TINT){
            getModelData(npc).setHurtTintEnabled(((GuiButtonYesNo)button).getBoolean());
        }
        if(button.id == BUTTON_CLOSE){
            close();
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if(textfield.id == FIELD_HEAD_BONE){
            getModelData(npc).setHeadBoneName(textfield.getValue());
        }
        if(textfield.id == FIELD_TRANSITION){
            getModelData(npc).setTransitionLengthTicks(textfield.getInteger());
        }
        if(textfield.id == FIELD_WIDTH){
            FloatTextFieldUtils.performFloatChecks(0, Float.MAX_VALUE, getModelData(npc).getWidth(), textfield);
            getModelData(npc).setWidth(FloatTextFieldUtils.getFloat(textfield));
        }
        if(textfield.id == FIELD_HEIGHT){
            FloatTextFieldUtils.performFloatChecks(0, Float.MAX_VALUE, getModelData(npc).getHeight(), textfield);
            getModelData(npc).setHeight(FloatTextFieldUtils.getFloat(textfield));
        }
        if(textfield.id == FIELD_SCALE){
            FloatTextFieldUtils.performFloatChecks(0.05f, 16f, getModelData(npc).getHitboxScale(), textfield);
            getModelData(npc).setHitboxScale(FloatTextFieldUtils.getFloat(textfield));
        }
    }
}
