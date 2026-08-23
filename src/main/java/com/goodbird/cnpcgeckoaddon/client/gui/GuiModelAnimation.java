package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import software.bernie.geckolib.cache.GeckoLibCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiModelAnimation extends GuiNPCInterface implements ITextfieldListener {

    @Override
    public void init() {
        super.init();
        int y = guiTop + 44;
        addSelectionBlock(1,y,"Animation File:",getModelData(npc).getAnimFile());
        addSelectionBlock(2,y+=23,"Idle:",getModelData(npc).getIdleAnim());
        addSelectionBlock(3,y+=23,"Walk:",getModelData(npc).getWalkAnim());
        addSelectionBlock(4,y+=23,"Attack:",String.join(", ", getModelData(npc).getAttackAnims()));
        addSelectionBlock(5,y+=23,"Hurt:",getModelData(npc).getHurtAnim());
        addSelectionBlock(6,y+23,"Death:",getModelData(npc).getDeathAnim());
        this.addButton(new GuiButtonNop(this, 670, width - 22, 2, 20, 20, "X"));
    }

    public CustomModelData getModelData(EntityNPCInterface npc){
        return ((IDataDisplay)npc.display).getCustomModelData();
    }

    public void addSelectionBlock(int id, int y, String label, String value){
        this.addLabel(new GuiLabel(id,label, guiLeft - 85, y + 5,0xffffff));
        addTextField(new GuiTextFieldNop(id,this, guiLeft - 40, y, 200, 20, value));
        this.addButton(new GuiButtonNop(this,id, guiLeft + 163, y, 80, 20, "mco.template.button.select"));
    }

    public List<String> getAnimationList(){
        return AnimationFileUtil.getAnimationList(getModelData(npc).getAnimFile());
    }

    public void selectAnimation(int id, String subtitle, Consumer<String> setter){
        setSubGui(new GuiStringSelection(this, subtitle, getAnimationList(), (name)-> {
            setter.accept(name);
            getTextField(id).setValue(name);
        }));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if(button.id == 670){
            close();
        }
        if(button.id==1){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib animation file:",
                    AnimationFileUtil.getAnimationFileList(), (name)-> {
                getModelData(npc).setAnimFile(name);
                getTextField(1).setValue(name);
            }));
        }
        if(button.id==2){
            selectAnimation(2, "Selecting geckolib idle animation:", (name)-> getModelData(npc).setIdleAnim(name));
        }
        if(button.id==3){
            selectAnimation(3, "Selecting geckolib walk animation:", (name)-> getModelData(npc).setWalkAnim(name));
        }
        if(button.id==4){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib attack animations:",
                    getAnimationList(), getModelData(npc).getAttackAnims(), (names)-> {
                getModelData(npc).setAttackAnims(names);
                getTextField(4).setValue(String.join(", ", names));
            }));
        }
        if(button.id==5){
            selectAnimation(5, "Selecting geckolib hurt animation:", (name)-> getModelData(npc).setHurtAnim(name));
        }
        if(button.id==6){
            selectAnimation(6, "Selecting geckolib death animation:", (name)-> getModelData(npc).setDeathAnim(name));
        }
    }

    public boolean isValidAnimFile(String name){
        // Typed by hand, so it may not even be a well-formed resource location.
        ResourceLocation location = AnimationFileUtil.parse(name);
        return location != null && GeckoLibCache.getBakedAnimations().containsKey(location);
    }

    public boolean isValidAnimation(String name){
        return name.isEmpty() || getAnimationList().contains(name);
    }

    public List<String> parseAnimations(String value){
        List<String> anims = new ArrayList<>();
        for(String name : value.split(",")){
            String anim = name.trim();
            if(!anim.isEmpty() && getAnimationList().contains(anim)){
                anims.add(anim);
            }
        }
        return anims;
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if(textfield.id == 1){
            if(isValidAnimFile(textfield.getValue()))
                getModelData(npc).setAnimFile(textfield.getValue());
            else
                textfield.setValue(getModelData(npc).getAnimFile());
        }
        if(textfield.id == 2){
            if(isValidAnimation(textfield.getValue()))
                getModelData(npc).setIdleAnim(textfield.getValue());
            else
                textfield.setValue(getModelData(npc).getIdleAnim());
        }
        if(textfield.id == 3){
            if(isValidAnimation(textfield.getValue()))
                getModelData(npc).setWalkAnim(textfield.getValue());
            else
                textfield.setValue(getModelData(npc).getWalkAnim());
        }
        if(textfield.id == 4){
            getModelData(npc).setAttackAnims(parseAnimations(textfield.getValue()));
            textfield.setValue(String.join(", ", getModelData(npc).getAttackAnims()));
        }
        if(textfield.id == 5){
            if(isValidAnimation(textfield.getValue()))
                getModelData(npc).setHurtAnim(textfield.getValue());
            else
                textfield.setValue(getModelData(npc).getHurtAnim());
        }
        if(textfield.id == 6){
            if(isValidAnimation(textfield.getValue()))
                getModelData(npc).setDeathAnim(textfield.getValue());
            else
                textfield.setValue(getModelData(npc).getDeathAnim());
        }
    }
}
