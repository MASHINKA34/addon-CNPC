package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public class CustomModelData {
    private String model = CNPCGeckoAddon.MODID + ":geo/geo_npc.geo.json";
    private String animFile = CNPCGeckoAddon.MODID + ":animations/geo_npc.animation.json";
    private String idleAnim = "idle";
    private String walkAnim = "walk";
    private List<String> attackAnims = new ArrayList<>();
    private String hurtAnim = "";
    private String deathAnim = "";
    private String headBoneName = "head";
    private int transitionLengthTicks = 10;
    private float width = 0.7f;
    private float height = 2f;
    private boolean hurtTintEnabled = false;
    public CompoundTag writeToNBT(CompoundTag nbttagcompound) {
        nbttagcompound.putString("Model", model);
        nbttagcompound.putString("AnimFile", animFile);
        nbttagcompound.putString("IdleAnim", idleAnim);
        nbttagcompound.putString("WalkAnim", walkAnim);
        ListTag attackList = new ListTag();
        for (String anim : attackAnims) {
            attackList.add(StringTag.valueOf(anim));
        }
        nbttagcompound.put("AttackAnims", attackList);
        nbttagcompound.putString("HurtAnim", hurtAnim);
        nbttagcompound.putString("DeathAnim", deathAnim);
        nbttagcompound.putString("HeadBoneName", headBoneName);
        nbttagcompound.putInt("TransitionLengthTicks", transitionLengthTicks);
        nbttagcompound.putFloat("Width",width);
        nbttagcompound.putFloat("Height",height);
        nbttagcompound.putBoolean("HurtTintEnabled",hurtTintEnabled);
        return nbttagcompound;
    }

    public void readFromNBT(CompoundTag nbttagcompound) {
        if (nbttagcompound.contains("Model")) {
            model = nbttagcompound.getString("Model");
            animFile = nbttagcompound.getString("AnimFile");
            idleAnim = nbttagcompound.getString("IdleAnim");
            walkAnim = nbttagcompound.getString("WalkAnim");
            hurtAnim = nbttagcompound.getString("HurtAnim");
            deathAnim = nbttagcompound.getString("DeathAnim");
            attackAnims = readAttackAnims(nbttagcompound);

            // Only overwrite the "head" default when the tag is actually present:
            // NPCs saved before this field existed would otherwise end up with an
            // empty bone name, which silently disables head tracking.
            if (nbttagcompound.contains("HeadBoneName"))
                headBoneName = nbttagcompound.getString("HeadBoneName");

            if (nbttagcompound.contains("Width"))
                width = nbttagcompound.getFloat("Width");

            if (nbttagcompound.contains("Height"))
                height = nbttagcompound.getFloat("Height");

            if (nbttagcompound.contains("TransitionLengthTicks"))
                transitionLengthTicks = nbttagcompound.getInt("TransitionLengthTicks");

            if (nbttagcompound.contains("HurtTintEnabled"))
                hurtTintEnabled = nbttagcompound.getBoolean("HurtTintEnabled");
        }
    }

    private List<String> readAttackAnims(CompoundTag nbttagcompound) {
        List<String> anims = new ArrayList<>();
        if (nbttagcompound.contains("AttackAnims")) {
            ListTag list = nbttagcompound.getList("AttackAnims", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String anim = list.getString(i);
                if (!anim.isEmpty()) {
                    anims.add(anim);
                }
            }
        } else if (!nbttagcompound.getString("AttackAnim").isEmpty()) {
            anims.add(nbttagcompound.getString("AttackAnim"));
        }
        return anims;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAnimFile() {
        return animFile;
    }

    public void setAnimFile(String animFile) {
        this.animFile = animFile;
    }

    public String getIdleAnim() {
        return idleAnim;
    }

    public void setIdleAnim(String idleAnim) {
        this.idleAnim = idleAnim;
    }

    public String getWalkAnim() {
        return walkAnim;
    }

    public void setWalkAnim(String walkAnim) {
        this.walkAnim = walkAnim;
    }

    public List<String> getAttackAnims() {
        return attackAnims;
    }

    public void setAttackAnims(List<String> attackAnims) {
        this.attackAnims = new ArrayList<>(attackAnims);
    }

    public String getHurtAnim() {
        return hurtAnim;
    }

    public void setHurtAnim(String hurtAnim) {
        this.hurtAnim = hurtAnim;
    }

    public String getDeathAnim() {
        return deathAnim;
    }

    public void setDeathAnim(String deathAnim) {
        this.deathAnim = deathAnim;
    }

    public String getHeadBoneName() {
        return headBoneName;
    }

    public void setHeadBoneName(String headBoneName) {
        this.headBoneName = headBoneName;
    }

    public int getTransitionLengthTicks() {
        return transitionLengthTicks;
    }

    public void setTransitionLengthTicks(int transitionLengthTicks) {
        this.transitionLengthTicks = transitionLengthTicks;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public boolean isHurtTintEnabled() {
        return hurtTintEnabled;
    }

    public void setHurtTintEnabled(boolean value) {
        this.hurtTintEnabled = value;
    }
}
