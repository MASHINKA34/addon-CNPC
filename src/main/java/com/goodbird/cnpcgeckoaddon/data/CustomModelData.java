package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.MobModelHitboxResolver;
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
    private boolean autoHitbox = true;
    private float hitboxScale = 1f;
    private boolean hurtTintEnabled = false;

    /** The box every model used before sizes were derived from the geometry. */
    private static final float LEGACY_WIDTH = 0.7f;
    private static final float LEGACY_HEIGHT = 2f;
    private static final float MIN_SCALE = 0.05f;
    private static final float MAX_SCALE = 16f;

    private String derivedModel;
    private float[] derivedSize;
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
        nbttagcompound.putBoolean("AutoHitbox",autoHitbox);
        nbttagcompound.putFloat("HitboxScale",hitboxScale);
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

            // NPCs saved before the derived sizes existed carry the old 0.7 by 2.0
            // box. Those were never chosen, they were simply the only default, so
            // they move to the derived size. A box someone actually tuned is left
            // exactly as it was.
            if (nbttagcompound.contains("AutoHitbox"))
                autoHitbox = nbttagcompound.getBoolean("AutoHitbox");
            else
                autoHitbox = width == LEGACY_WIDTH && height == LEGACY_HEIGHT;

            if (nbttagcompound.contains("HitboxScale"))
                hitboxScale = nbttagcompound.getFloat("HitboxScale");

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

    public boolean isAutoHitbox() {
        return autoHitbox;
    }

    /**
     * Turning automatic sizing off seeds the manual box from the derived size, so
     * tuning starts at the model's own size rather than at the humanoid default.
     * A box that was already tuned by hand is left alone.
     */
    public void setAutoHitbox(boolean autoHitbox) {
        if (!autoHitbox && this.autoHitbox && width == LEGACY_WIDTH && height == LEGACY_HEIGHT) {
            float[] derived = derived();
            if (derived != null) {
                width = derived[0];
                height = derived[1];
            }
        }
        this.autoHitbox = autoHitbox;
    }

    public float getHitboxScale() {
        return hitboxScale;
    }

    public void setHitboxScale(float hitboxScale) {
        this.hitboxScale = hitboxScale;
    }

    /** The size the model records for itself, or null for a non-bundled model. */
    public float[] getDerivedHitbox() {
        float[] derived = derived();
        return derived == null ? null : new float[]{derived[0], derived[1]};
    }

    public float getEffectiveWidth() {
        float[] derived = autoHitbox ? derived() : null;
        return (derived == null ? width : derived[0]) * clampedScale();
    }

    public float getEffectiveHeight() {
        float[] derived = autoHitbox ? derived() : null;
        return (derived == null ? height : derived[1]) * clampedScale();
    }

    /**
     * Both effective sizes are read every tick for every NPC, so the table lookup
     * is kept until the model itself changes. The array is never handed out
     * without copying, so caching it cannot leak a mutable size.
     */
    private float[] derived() {
        if (derivedModel == null || !derivedModel.equals(model)) {
            derivedModel = model;
            derivedSize = MobModelHitboxResolver.resolve(model);
        }
        return derivedSize;
    }

    /** A scale of zero would leave an entity that cannot be seen or clicked. */
    private float clampedScale() {
        if (!(hitboxScale > MIN_SCALE)) {
            return MIN_SCALE;
        }
        return Math.min(hitboxScale, MAX_SCALE);
    }

    public boolean isHurtTintEnabled() {
        return hurtTintEnabled;
    }

    public void setHurtTintEnabled(boolean value) {
        this.hurtTintEnabled = value;
    }
}
