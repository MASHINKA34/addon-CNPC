package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the six abilities that act round a point put it: the area slam, the gravity field, the
 * seismic rings, the take cover strike, the boulder rain and the beams. Each keeps the centre
 * and the target mode under its own prefix, and a save from before the setting reads back as
 * round the boss - which is what all six always did.
 */
class BossCentreSettingsTest {

    /** Key prefix -> the settings of one phase that carry it, in the order the screens list them. */
    private static final Map<String, Function<BossPhaseData, BossCentreSettings>> SIX = new LinkedHashMap<>();

    static {
        SIX.put("AreaAttack", BossPhaseData::areaAttack);
        SIX.put("Gravity", BossPhaseData::gravity);
        SIX.put("Seismic", BossPhaseData::seismic);
        SIX.put("Cover", BossPhaseData::cover);
        SIX.put("BoulderRain", BossPhaseData::boulderRain);
        SIX.put("Beam", BossPhaseData::beam);
    }

    @Test
    @DisplayName("each starts round the boss, on the main target, and a save from before reads back that way")
    void defaultsAreRoundTheBoss() {
        BossPhaseData fresh = new BossPhaseData();
        CompoundTag older = fresh.writeToNBT();
        SIX.forEach((prefix, settings) -> {
            assertEquals(BossCentreMode.BOSS, settings.apply(fresh).getCentreMode(), prefix);
            assertEquals(BossTargetMode.MAIN, settings.apply(fresh).getTargetMode(), prefix);
            older.remove(prefix + "Centre");
            older.remove(prefix + "TargetMode");
        });
        BossPhaseData changed = new BossPhaseData();
        SIX.values().forEach(settings -> {
            settings.apply(changed).setCentreMode(BossCentreMode.TARGET);
            settings.apply(changed).setTargetMode(BossTargetMode.RANDOM);
        });
        // Read over a phase that had all six changed, so the load has to reset them.
        changed.readFromNBT(older);
        SIX.forEach((prefix, settings) -> {
            assertEquals(BossCentreMode.BOSS, settings.apply(changed).getCentreMode(),
                    prefix + " without its key has to go off round the boss");
            assertEquals(BossTargetMode.MAIN, settings.apply(changed).getTargetMode(), prefix);
        });
    }

    @Test
    @DisplayName("both settings round-trip under each ability's own prefix")
    void theSettingsRoundTrip() {
        BossPhaseData phase = new BossPhaseData();
        int mode = BossTargetMode.MAIN;
        for (Function<BossPhaseData, BossCentreSettings> settings : SIX.values()) {
            settings.apply(phase).setCentreMode(BossCentreMode.TARGET);
            settings.apply(phase).setTargetMode(mode);
            mode = mode == BossTargetMode.RANDOM ? BossTargetMode.NEAREST : mode + 1;
        }
        CompoundTag tag = phase.writeToNBT();
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        SIX.forEach((prefix, settings) -> {
            assertEquals(BossCentreMode.TARGET, tag.getInt(prefix + "Centre"), prefix + "Centre is the key");
            assertEquals(settings.apply(phase).getTargetMode(), tag.getInt(prefix + "TargetMode"),
                    prefix + "TargetMode is the key");
            assertEquals(BossCentreMode.TARGET, settings.apply(reread).getCentreMode(), prefix);
            assertEquals(settings.apply(phase).getTargetMode(), settings.apply(reread).getTargetMode(), prefix);
        });
        assertEquals(tag, reread.writeToNBT(), "a re-read phase saves the same tag again");
    }

    @Test
    @DisplayName("a tag's numbers are clamped into range, and so is every setter")
    void theNumbersAreClamped() {
        CompoundTag high = new BossPhaseData().writeToNBT();
        CompoundTag low = high.copy();
        SIX.keySet().forEach(prefix -> {
            high.putInt(prefix + "Centre", Integer.MAX_VALUE);
            high.putInt(prefix + "TargetMode", Integer.MAX_VALUE);
            low.putInt(prefix + "Centre", Integer.MIN_VALUE);
            low.putInt(prefix + "TargetMode", Integer.MIN_VALUE);
        });
        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(high);
        SIX.forEach((prefix, settings) -> {
            assertEquals(BossCentreMode.TARGET, settings.apply(phase).getCentreMode(), prefix);
            assertEquals(BossTargetMode.RANDOM, settings.apply(phase).getTargetMode(), prefix);
        });
        phase.readFromNBT(low);
        SIX.forEach((prefix, settings) -> {
            assertEquals(BossCentreMode.BOSS, settings.apply(phase).getCentreMode(), prefix);
            assertEquals(BossTargetMode.MAIN, settings.apply(phase).getTargetMode(), prefix);
        });
        SIX.forEach((prefix, settings) -> {
            BossCentreSettings centre = settings.apply(phase);
            centre.setCentreMode(5);
            centre.setTargetMode(-4);
            assertTrue(centre.getCentreMode() == BossCentreMode.TARGET && centre.getTargetMode() == BossTargetMode.MAIN,
                    prefix + " setters clamp");
        });
    }
}
