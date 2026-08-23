package com.goodbird.cnpcgeckoaddon.entity;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.GeckoLib;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

public class EntityCustomModel extends Animal implements GeoAnimatable, GeoEntity {
    private AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);
    public ResourceLocation modelResLoc=ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "geo/geo_npc.geo.json");
    public ResourceLocation animResLoc=ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID , "animations/geo_npc.animation.json");
    public ResourceLocation textureResLoc = ResourceLocation.fromNamespaceAndPath("customnpcs","textures/entity/humanmale/steve.png");
    public String idleAnim = "";
    public String walkAnim = "";
    public String hurtAnim = "";
    public String deathAnim = "";
    public List<String> attackAnims = new ArrayList<>();
    public RawAnimation dialogAnim = null;
    public RawAnimation manualAnim = null;
    public ItemStack leftHeldItem;
    public String headBoneName = "head";
    private EntityDimensions dims;
    public int size = 5;
    private RawAnimation actionAnim = null;
    private RawAnimation deathRawAnim = null;
    private String deathRawAnimName = "";
    private boolean needsAnimationReset = false;
    private boolean killed = false;
    private boolean prevKilled = false;
    private boolean prevSwinging = false;
    private int prevSwingTime = 0;
    private int prevHurtTime = 0;

    public void updateCombatState(boolean killed, boolean swinging, int swingTime, int hurtTime) {
        if (killed != prevKilled) {
            prevKilled = killed;
            actionAnim = null;
            needsAnimationReset = true;
        }
        this.killed = killed;
        if (!killed) {
            if (swinging && (!prevSwinging || swingTime < prevSwingTime) && !attackAnims.isEmpty()) {
                playAction(attackAnims.get(getRandom().nextInt(attackAnims.size())));
            } else if (hurtTime > prevHurtTime && actionAnim == null && !hurtAnim.isEmpty()) {
                playAction(hurtAnim);
            }
        }
        prevSwinging = swinging;
        prevSwingTime = swingTime;
        prevHurtTime = hurtTime;
    }

    private void playAction(String animName) {
        if (!hasAnimation(animName)) {
            return;
        }
        actionAnim = RawAnimation.begin().then(animName, Animation.LoopType.PLAY_ONCE);
        needsAnimationReset = true;
    }

    private boolean hasAnimation(String animName) {
        BakedAnimations animations = GeckoLibCache.getBakedAnimations().get(animResLoc);
        return animations != null && animations.animations().containsKey(animName);
    }

    private PlayState predicateMovement(AnimationState<EntityCustomModel> event) {
        if (needsAnimationReset) {
            event.getController().forceAnimationReset();
            needsAnimationReset = false;
        }
        if (killed && !deathAnim.isEmpty() && hasAnimation(deathAnim)) {
            if (deathRawAnim == null || !deathRawAnimName.equals(deathAnim)) {
                deathRawAnim = RawAnimation.begin().then(deathAnim, Animation.LoopType.HOLD_ON_LAST_FRAME);
                deathRawAnimName = deathAnim;
            }
            event.getController().setAnimation(deathRawAnim);
            return PlayState.CONTINUE;
        }
        if (manualAnim != null) {
            if (event.getController().getCurrentRawAnimation() == manualAnim && event.getController().getAnimationState() == AnimationController.State.STOPPED) {
                manualAnim = null;
            } else {
                if (event.getController().getCurrentRawAnimation() != manualAnim) {
                    event.getController().forceAnimationReset();
                }
                event.getController().setAnimation(manualAnim);
                return PlayState.CONTINUE;
            }
        }
        if (dialogAnim != null) {
            if (event.getController().getCurrentRawAnimation() == dialogAnim &&event.getController().getAnimationState() == AnimationController.State.STOPPED) {
                dialogAnim = null;
            } else {
                if (event.getController().getCurrentRawAnimation() != dialogAnim) {
                    event.getController().forceAnimationReset();
                }
                event.getController().setAnimation(dialogAnim);
                return PlayState.CONTINUE;
            }
        }
        if (actionAnim != null) {
            if (event.getController().getCurrentRawAnimation() == actionAnim && event.getController().getAnimationState() == AnimationController.State.STOPPED) {
                actionAnim = null;
            } else {
                event.getController().setAnimation(actionAnim);
                return PlayState.CONTINUE;
            }
        }
        if ((event.getLimbSwingAmount() > -0.15F && event.getLimbSwingAmount() < 0.15F) || walkAnim.isEmpty()) {
            if (!idleAnim.isEmpty()) {
                event.getController().setAnimation(RawAnimation.begin().thenLoop(idleAnim));
            } else {
                return PlayState.STOP;
            }
        } else {
            event.getController().setAnimation(RawAnimation.begin().thenLoop(walkAnim));
        }
        return PlayState.CONTINUE;
    }

//    private <E extends IAnimatable> PlayState predicateAttack(AnimationEvent<E> event) {
//        return PlayState.CONTINUE;
//    }

    public EntityCustomModel(EntityType<? extends Animal> type, Level worldIn) {
        super(type, worldIn);
        this.noCulling = true;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return false;
    }

    public void setSize(float width, float height) {
        dims = EntityDimensions.scalable(width, height);
    }

    @Override
    public EntityDimensions getDimensions(Pose p_213305_1_) {
        if(dims==null){
            dims = EntityDimensions.scalable(0.7F, 2F);
        }
        return dims;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 10, this::predicateMovement));
        //controllers.add(new AnimationController<>(this, "attack", 10, this::predicateAttack));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.factory;
    }

    @Override
    public double getTick(Object entity) {
        return tickCount;
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel p_146743_, AgeableMob p_146744_) {
        return null;
    }

    public double getAttributeValue(Holder<Attribute> p_233637_1_) {
        try {
            return this.getAttributes().getValue(p_233637_1_);
        }catch (Exception e){
            return 1.0;
        }
    }
}