package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPlatformZone;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZoneList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a platform is in the world and which platforms one cast sets alight, checked without a
 * world.
 *
 * <p>None of it throws when it is wrong: a box one block short is a player standing on the edge
 * of the platform they typed in and never getting hit, a turn that skips a platform or an "all
 * but one" that burns them all is a fight that reads as unfair rather than as broken.</p>
 */
class BossPlatformZonesTest {

    private static final int MIN_BUILD = -64;
    private static final int MAX_BUILD = 320;
    private static final BlockPos HOME = new BlockPos(100, 64, -50);

    @Test
    @DisplayName("both corner blocks are inside, whichever corner was typed first")
    void cornerBlocksAreInsideInEitherOrder() {
        AABB forward = fixedBox(0, 64, 0, 4, 66, 4);
        AABB backward = fixedBox(4, 66, 4, 0, 64, 0);
        assertEquals(forward, backward, "swapping the corners must describe the same platform");
        assertEquals(fixedBox(0, 64, 0, 4, 66, 4), fixedBox(4, 64, 0, 0, 66, 4), "corners mixed per axis make it too");
        assertTrue(forward.contains(new Vec3(0.0D, 64.0D, 0.0D)), "the low face of the first corner block is on it");
        assertTrue(forward.contains(new Vec3(4.999D, 66.999D, 4.999D)), "the far edge of the second corner block is on it");
        assertFalse(forward.contains(new Vec3(5.0D, 65.0D, 2.0D)), "a step past the far x face is off it");
        assertFalse(forward.contains(new Vec3(2.0D, 67.0D, 2.0D)), "standing on top of the top block is above it");
        assertFalse(forward.contains(new Vec3(-0.001D, 65.0D, 2.0D)));
    }

    @Test
    @DisplayName("a platform given as an offset is counted from the block the boss activated on")
    void anOffsetIsCountedFromHome() {
        BossPlatformZone zone = zone(BossPlatformZone.COORDINATE_ARENA_OFFSET, -3, 0, 2, 3, 2, 6);
        AABB box = BossPlatformRuntime.zoneBox(zone, HOME, MIN_BUILD, MAX_BUILD);
        assertEquals(new AABB(97, 64, -48, 104, 67, -43), box);
        assertEquals(fixedBox(97, 64, -48, 103, 66, -44), box,
                "an offset platform is the fixed platform its corners add up to");
        BlockPos negativeHome = BlockPos.containing(-10.5D, 70.0D, 3.2D);
        AABB underNegative = BossPlatformRuntime.zoneBox(
                zone(BossPlatformZone.COORDINATE_ARENA_OFFSET, 0, 0, 0, 0, 0, 0), negativeHome, MIN_BUILD, MAX_BUILD);
        assertNotNull(underNegative);
        assertTrue(underNegative.contains(new Vec3(-10.5D, 70.2D, 3.2D)),
                "a one block platform at no offset is the block the boss stood on, west of nought included");
    }

    @Test
    @DisplayName("a fixed platform ignores where the boss is")
    void aFixedPlatformIgnoresHome() {
        BossPlatformZone zone = zone(BossPlatformZone.COORDINATE_FIXED, 10, 70, 10, 12, 70, 12);
        assertEquals(BossPlatformRuntime.zoneBox(zone, BlockPos.ZERO, MIN_BUILD, MAX_BUILD),
                BossPlatformRuntime.zoneBox(zone, HOME, MIN_BUILD, MAX_BUILD));
    }

    @Test
    @DisplayName("a platform is cut to the build height, and one wholly outside it is no platform")
    void buildHeightCutsAPlatform() {
        AABB tall = fixedBox(0, -100, 0, 2, 400, 2);
        assertNotNull(tall);
        assertEquals(MIN_BUILD, tall.minY, 1.0E-9D);
        assertEquals(MAX_BUILD, tall.maxY, 1.0E-9D, "the top build block is the last one on it");
        assertNull(fixedBox(0, 400, 0, 2, 500, 2), "corners above the world leave nothing between them");
        assertNull(fixedBox(0, -200, 0, 2, -100, 2), "nor do corners under it");
        assertNull(BossPlatformRuntime.zoneBox(zone(BossPlatformZone.COORDINATE_ARENA_OFFSET, 0, 300, 0, 1, 301, 1),
                HOME, MIN_BUILD, MAX_BUILD), "an offset that lands above the world leaves nothing either");
        AABB single = fixedBox(3, 64, 3, 3, 64, 3);
        assertEquals(new AABB(3, 64, 3, 4, 65, 4), single, "a platform of one block holds exactly that block");
    }

    @Test
    @DisplayName("the outline is drawn inside the box, as near the boss' height as it gets")
    void theOutlineHeightStaysInsideTheBox() {
        AABB box = fixedBox(0, 70, 0, 4, 73, 4);
        assertEquals(70.0D, BossPlatformRuntime.floorY(box, 64.0D), 1.0E-9D, "a boss below draws on the floor block");
        assertEquals(73.0D, BossPlatformRuntime.floorY(box, 90.0D), 1.0E-9D, "a boss above draws on the top block");
        assertEquals(71.5D, BossPlatformRuntime.floorY(box, 71.5D), 1.0E-9D);
    }

    @Test
    @DisplayName("a random pick lands on every platform in time, and far more often on a heavier one")
    void aRandomPickFollowsTheWeights() {
        RandomSource random = RandomSource.create(20260913L);
        List<String> platforms = List.of("light", "heavy", "other");
        Map<String, Integer> weights = Map.of("light", 1, "heavy", 50, "other", 1);
        Map<String, Integer> drawn = new HashMap<>();
        for (int cast = 0; cast < 2000; cast++) {
            drawn.merge(BossPlatformRuntime.weighted(platforms, weights::get, random), 1, Integer::sum);
        }
        assertEquals(Set.copyOf(platforms), drawn.keySet(), "a platform of weight one still goes sometimes");
        assertTrue(drawn.get("heavy") > 1600, "fifty to one and one should be drawn nearly every time: " + drawn);

        Map<String, Integer> even = new HashMap<>();
        for (int cast = 0; cast < 3000; cast++) {
            even.merge(BossPlatformRuntime.weighted(platforms, name -> 1, random), 1, Integer::sum);
        }
        for (String platform : platforms) {
            assertTrue(even.get(platform) > 800, "equal weights should be drawn about as often: " + even);
        }
        assertEquals("only", BossPlatformRuntime.weighted(List.of("only"), name -> 7, random));
    }

    @Test
    @DisplayName("the target's platform is the one it stands on, the first on the list where two overlap")
    void theTargetsPlatformIsTheOneItStandsOn() {
        Map<String, AABB> boxes = new HashMap<>();
        boxes.put("a", fixedBox(0, 64, 0, 4, 65, 4));
        boxes.put("b", fixedBox(10, 64, 0, 14, 65, 4));
        boxes.put("c", fixedBox(12, 64, 0, 16, 65, 4));
        List<String> platforms = List.of("a", "b", "c");
        assertEquals("b", BossPlatformRuntime.holding(platforms, boxes::get, new Vec3(11.5D, 64.0D, 2.0D)));
        assertEquals("b", BossPlatformRuntime.holding(platforms, boxes::get, new Vec3(13.0D, 64.5D, 2.0D)),
                "where two platforms overlap the one listed first is the target's");
        assertEquals("c", BossPlatformRuntime.holding(platforms, boxes::get, new Vec3(15.5D, 64.0D, 1.0D)));
        assertNull(BossPlatformRuntime.holding(platforms, boxes::get, new Vec3(7.0D, 64.0D, 2.0D)),
                "a target between the platforms stands on none of them");
        assertNull(BossPlatformRuntime.holding(platforms, boxes::get, new Vec3(2.0D, 66.0D, 2.0D)),
                "nor does one jumping over the top of one");
    }

    @Test
    @DisplayName("in turn, three casts in a row walk all three platforms and come back round")
    void inTurnWalksTheList() {
        List<Integer> listed = List.of(1, 2, 3);
        Set<Integer> usable = Set.of(1, 2, 3);
        int first = BossPlatformRuntime.nextInTurn(listed, usable, 0);
        int second = BossPlatformRuntime.nextInTurn(listed, usable, first);
        int third = BossPlatformRuntime.nextInTurn(listed, usable, second);
        assertEquals(List.of(1, 2, 3), List.of(first, second, third), "a fight's first cast starts at the top");
        assertEquals(1, BossPlatformRuntime.nextInTurn(listed, usable, third), "and the fourth comes back round");
    }

    @Test
    @DisplayName("in turn, a platform switched off keeps its place and the one after it still follows")
    void inTurnSkipsWhatCannotBurn() {
        List<Integer> listed = List.of(4, 9, 2, 7);
        assertEquals(2, BossPlatformRuntime.nextInTurn(listed, Set.of(4, 2, 7), 4),
                "the one after a platform switched off is next, not the top of the list");
        assertEquals(2, BossPlatformRuntime.nextInTurn(listed, Set.of(4, 2, 7), 9),
                "a turn that ended on a platform switched off since carries on after it");
        assertEquals(4, BossPlatformRuntime.nextInTurn(listed, Set.of(4, 2), 2), "past the end it goes round");
        assertEquals(4, BossPlatformRuntime.nextInTurn(listed, Set.of(4, 9, 2, 7), 12),
                "a turn that ended on a platform since deleted starts again from the top");
        assertEquals(7, BossPlatformRuntime.nextInTurn(listed, Set.of(7), 7), "a lone platform goes every time");
        assertEquals(0, BossPlatformRuntime.nextInTurn(listed, Set.of(), 4), "nothing usable is nothing to burn");
        assertEquals(0, BossPlatformRuntime.nextInTurn(List.of(), Set.of(), 0));
    }

    @Test
    @DisplayName("all but one burns every platform but one, and every platform gets to be the safe one")
    void allButOneLeavesOneSafe() {
        RandomSource random = RandomSource.create(7L);
        List<String> platforms = List.of("a", "b", "c", "d");
        Set<String> safe = new HashSet<>();
        for (int cast = 0; cast < 80; cast++) {
            List<String> burning = BossPlatformRuntime.allButOne(platforms, random);
            assertEquals(platforms.size() - 1, burning.size(), "exactly one platform is left safe");
            assertEquals(burning.size(), Set.copyOf(burning).size(), "no platform burns twice in one cast");
            Set<String> left = new HashSet<>(platforms);
            left.removeAll(burning);
            safe.addAll(left);
            assertEquals(platforms.stream().filter(burning::contains).toList(), burning,
                    "the burning platforms keep their list order");
        }
        assertEquals(Set.copyOf(platforms), safe, "some platform was never the safe one in eighty casts");
        assertEquals(1, BossPlatformRuntime.allButOne(List.of("a", "b"), random).size(), "two platforms burn one");
        assertTrue(BossPlatformRuntime.allButOne(List.of("a"), random).isEmpty(),
                "a lone platform with the only safe spot on it burns nothing");
        assertTrue(BossPlatformRuntime.allButOne(List.of(), random).isEmpty());
    }

    private static AABB fixedBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        return BossPlatformRuntime.zoneBox(zone(BossPlatformZone.COORDINATE_FIXED, x1, y1, z1, x2, y2, z2),
                HOME, MIN_BUILD, MAX_BUILD);
    }

    private static BossPlatformZone zone(int mode, int x1, int y1, int z1, int x2, int y2, int z2) {
        BossPlatformZone zone = new BossPlatformZoneList().add();
        zone.setCoordinateMode(mode);
        zone.setCorner1(x1, y1, z1);
        zone.setCorner2(x2, y2, z2);
        return zone;
    }
}
