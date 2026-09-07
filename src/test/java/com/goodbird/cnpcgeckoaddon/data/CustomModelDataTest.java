package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelDataTest {
    @Test
    void manualDimensionsAndScaleCannotCreateAnUnboundedHitbox() {
        CustomModelData data = new CustomModelData();
        data.setAutoHitbox(false);
        for (float size : new float[]{32, 1_000_000, Float.MAX_VALUE}) {
            data.setWidth(size);
            data.setHeight(size);
            data.setHitboxScale(16);
            assertTrue(data.getWidth() <= 32 && data.getHeight() <= 32);
            assertTrue(data.getEffectiveWidth() <= 32 && data.getEffectiveHeight() <= 32);
        }
    }

    @Test
    void oversizedSavedHitboxesAreClampedOnLoad() {
        CustomModelData data = new CustomModelData();
        CompoundTag saved = data.writeToNBT(new CompoundTag());
        saved.putBoolean("AutoHitbox", false);
        saved.putFloat("Width", 1_000_000);
        saved.putFloat("Height", 1_000_000);
        saved.putFloat("HitboxScale", 16);
        data.readFromNBT(saved);
        assertTrue(data.getEffectiveWidth() <= 32 && data.getEffectiveHeight() <= 32);
    }

    @Test
    void ordinaryHitboxesKeepTheirDimensionsAcrossSaveAndLoad() {
        CustomModelData original = new CustomModelData();
        original.setAutoHitbox(false);
        original.setWidth(3);
        original.setHeight(4);
        original.setHitboxScale(2);
        CustomModelData restored = new CustomModelData();
        restored.readFromNBT(original.writeToNBT(new CompoundTag()));
        assertEquals(6, restored.getEffectiveWidth());
        assertEquals(8, restored.getEffectiveHeight());
    }
}
