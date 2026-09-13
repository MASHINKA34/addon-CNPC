package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the spit's throw and its glob to the literals they replaced.
 *
 * <p>Spelled out rather than compared against the fields they came from: a default quietly
 * changed here would change every spit on every server at once, and "the glob falls short now"
 * is the kind of thing nobody traces back to a settings class.</p>
 */
class BossFluidSpitTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh spit is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossFluidSpitSettings spit = new BossFluidSpitSettings();

        assertEquals(20, spit.getArcLiftHundredths());
        assertEquals(0.2D, spit.getArcLift(), EPSILON);
        assertEquals(12, spit.getVelocityTenths());
        assertEquals(1.2F, spit.getVelocity(), 1.0E-6F);
        assertEquals(40, spit.getInaccuracyTenths());
        assertEquals(4.0F, spit.getInaccuracy(), 1.0E-6F);
        assertEquals(50, spit.getGravityThousandths());
        assertEquals(0.05D, spit.getGravity(), EPSILON);
        assertEquals(200, spit.getProjectileLifeTicks());
        assertEquals(12, spit.getSplashBase());
        assertEquals(8, spit.getSplashPerRadius());

        assertTrue(spit.getSpitSound().isEnabled());
        assertEquals("minecraft:entity.llama.spit", spit.getSpitSound().getSoundId());
        assertEquals(10, spit.getSpitSound().getVolume());
        assertEquals(8, spit.getSpitSound().getPitch());
    }

    @Test
    @DisplayName("a boss saved before any of this existed spits exactly as it used to")
    void anOldSaveKeepsTheOldBehaviour() {
        BossPhaseData written = new BossPhaseData();
        CompoundTag old = written.writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (isNewKey(key)) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        BossFluidSpitSettings spit = reloaded.fluidSpit();
        assertEquals(0.2D, spit.getArcLift(), EPSILON);
        assertEquals(1.2F, spit.getVelocity(), 1.0E-6F);
        assertEquals(4.0F, spit.getInaccuracy(), 1.0E-6F);
        assertEquals(0.05D, spit.getGravity(), EPSILON);
        assertEquals(200, spit.getProjectileLifeTicks());
        assertEquals(12, spit.getSplashBase());
        assertEquals(8, spit.getSplashPerRadius());
        assertTrue(spit.getSpitSound().isEnabled());
        assertEquals("minecraft:entity.llama.spit", spit.getSpitSound().getSoundId());
        assertEquals(10, spit.getSpitSound().getVolume());
        assertEquals(8, spit.getSpitSound().getPitch());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("FluidArcLift") || key.equals("FluidVelocity")
                || key.equals("FluidInaccuracy") || key.equals("FluidGravity")
                || key.equals("FluidProjectileLife") || key.equals("FluidSplashBase")
                || key.equals("FluidSplashPerRadius") || key.startsWith("FluidSpitSound");
    }
}
