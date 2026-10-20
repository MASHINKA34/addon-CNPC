package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The geometry of an ability centred on a target rather than on the boss: the warning marks the
 * spot the cast committed to, and a gravity field fixed on a spot bites there, with the boss'
 * own box moved onto it - while a cast round the boss keeps everything exactly where it was.
 */
class BossCastCentreTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("the warning marks the committed spot, or the boss when nothing was committed")
    void theWarningMarksTheCommittedSpot() {
        Vec3 bossFeet = new Vec3(10.0D, 64.0D, -3.0D);
        Vec3 target = new Vec3(18.5D, 63.0D, 4.25D);
        BossTelegraphRuntime.Cast roundTheBoss = new BossTelegraphRuntime.Cast(BossAbility.GROUND_ATTACK, 20L, 0L,
                -1, List.of(), null, 0.0F, null);
        BossTelegraphRuntime.Cast onTheTarget = new BossTelegraphRuntime.Cast(BossAbility.GROUND_ATTACK, 20L, 0L,
                -1, List.of(), null, 0.0F, target);
        assertSame(bossFeet, roundTheBoss.centreOr(bossFeet));
        assertSame(target, onTheTarget.centreOr(bossFeet));
    }

    @Test
    @DisplayName("a field round the boss bites with the boss' own box")
    void aFieldRoundTheBossBitesWithItsBox() {
        AABB box = new AABB(9.0D, 64.0D, -4.0D, 11.0D, 67.0D, -2.0D);
        assertSame(box, BossGravityScheduler.teeth(box, new Vec3(10.0D, 64.0D, -3.0D), null));
    }

    @Test
    @DisplayName("a field fixed on a spot bites with the same box standing on that spot")
    void aFixedFieldBitesAtItsCentre() {
        AABB box = new AABB(9.0D, 64.0D, -4.0D, 11.0D, 67.0D, -2.0D);
        AABB teeth = BossGravityScheduler.teeth(box, new Vec3(10.0D, 64.0D, -3.0D), new Vec3(30.0D, 60.0D, 7.0D));
        assertEquals(29.0D, teeth.minX, EPSILON);
        assertEquals(60.0D, teeth.minY, EPSILON);
        assertEquals(6.0D, teeth.minZ, EPSILON);
        assertEquals(31.0D, teeth.maxX, EPSILON);
        assertEquals(63.0D, teeth.maxY, EPSILON);
        assertEquals(8.0D, teeth.maxZ, EPSILON);
    }
}
