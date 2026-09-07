package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry the take cover strike is judged by, checked without a world.
 *
 * <p>These three decide who lives through the strike and how long its shockwave is drawn
 * for, and all three are cheap to get subtly wrong - a radius compared against a squared
 * distance, a spacing check that lets two shelters merge into one odd shape, a duration
 * that collapses to nothing for a small arena. None of that throws in play; it just makes
 * the mechanic read wrong to whoever is standing in it.</p>
 */
class BossCoverGeometryTest {

    @Test
    @DisplayName("a spot inside a shelter is sheltered, and the edge counts as inside")
    void shelterCoversItsOwnCircle() {
        List<Vec3> shelters = List.of(new Vec3(10.0D, 64.0D, 10.0D));
        assertTrue(BossCoverRuntime.isSheltered(shelters, 3.0D, new Vec3(10.0D, 64.0D, 10.0D)),
                "the centre of a shelter has to count as inside it");
        assertTrue(BossCoverRuntime.isSheltered(shelters, 3.0D, new Vec3(12.9D, 64.0D, 10.0D)),
                "just inside the radius has to count as inside");
        assertTrue(BossCoverRuntime.isSheltered(shelters, 3.0D, new Vec3(13.0D, 64.0D, 10.0D)),
                "standing exactly on the edge is being in cover, not out of it");
        assertFalse(BossCoverRuntime.isSheltered(shelters, 3.0D, new Vec3(13.5D, 64.0D, 10.0D)),
                "past the radius is out in the open");
    }

    @Test
    @DisplayName("shelter cover is judged in three dimensions, not on the floor plan")
    void shelterDoesNotReachUpAPillar() {
        List<Vec3> shelters = List.of(new Vec3(0.0D, 64.0D, 0.0D));
        assertFalse(BossCoverRuntime.isSheltered(shelters, 3.0D, new Vec3(0.0D, 80.0D, 0.0D)),
                "somebody on a tower sixteen blocks over a shelter is not in it");
    }

    @Test
    @DisplayName("no shelters means nobody is spared")
    void emptyShelterListSparesNobody() {
        assertFalse(BossCoverRuntime.isSheltered(List.of(), 3.0D, new Vec3(0.0D, 0.0D, 0.0D)));
    }

    @Test
    @DisplayName("shelters closer than their own diameter crowd each other out")
    void crowdingIsMeasuredFlatAndSquared() {
        List<Vec3> placed = List.of(new Vec3(0.0D, 64.0D, 0.0D));
        // apart is two radii: two shelters at exactly that distance touch without overlapping.
        assertTrue(BossCoverRuntime.crowdsShelter(placed, 5.9D, 0.0D, 6.0D),
                "a shelter that would overlap one already down has to be rejected");
        assertFalse(BossCoverRuntime.crowdsShelter(placed, 6.0D, 0.0D, 6.0D),
                "two shelters exactly touching are allowed; only overlap is crowding");
        assertFalse(BossCoverRuntime.crowdsShelter(placed, 20.0D, 20.0D, 6.0D));
    }

    @Test
    @DisplayName("crowding ignores height, because the shelters are drawn on the floor")
    void crowdingIsFlat() {
        List<Vec3> placed = List.of(new Vec3(0.0D, 200.0D, 0.0D));
        assertTrue(BossCoverRuntime.crowdsShelter(placed, 1.0D, 1.0D, 6.0D),
                "a shelter on a balcony still crowds the spot underneath it on the floor plan");
    }

    @Test
    @DisplayName("the shockwave is drawn for a time that fits the range it crosses")
    void waveDurationTracksRangeWithinItsBounds() {
        assertEquals(20, BossCoverRuntime.waveDuration(0.0D),
                "a wave has to be visible for a moment even at no range at all");
        assertEquals(20, BossCoverRuntime.waveDuration(8.0D),
                "a short range is held at the floor rather than flashing past");
        assertEquals(40, BossCoverRuntime.waveDuration(40.0D));
        assertEquals(60, BossCoverRuntime.waveDuration(1000.0D),
                "a huge range is capped rather than leaving the wave up for a minute");
        int previous = 0;
        for (int range = 0; range <= 120; range++) {
            int duration = BossCoverRuntime.waveDuration(range);
            assertTrue(duration >= previous, "the wave got shorter as the range grew, at " + range);
            assertTrue(duration >= 20 && duration <= 60, "duration left its bounds at range " + range);
            previous = duration;
        }
    }
}
