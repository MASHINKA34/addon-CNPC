package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box the model picker frames its preview by, measured on models built by hand.
 *
 * <p>A wrong turn or a pivot taken in the wrong unit throws nothing: the preview just cuts off
 * a tail or a head, or shrinks a mob to a speck, and only for the models that happen to turn
 * a bone the wrong way.</p>
 */
class GeckoGeometryBoundsTest {

    private static final double EPSILON = 1.0E-9D;
    private static final double QUARTER_TURN = Math.PI / 2.0D;

    private static GeckoGeometryBounds.Bounds measure(GeckoGeometryBounds.Bone... bones) {
        return GeckoGeometryBounds.compute(List.of(bones)).orElseThrow();
    }

    private static void assertBounds(double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     GeckoGeometryBounds.Bounds bounds) {
        assertEquals(minX, bounds.minX(), EPSILON, "min x of " + bounds);
        assertEquals(minY, bounds.minY(), EPSILON, "min y of " + bounds);
        assertEquals(minZ, bounds.minZ(), EPSILON, "min z of " + bounds);
        assertEquals(maxX, bounds.maxX(), EPSILON, "max x of " + bounds);
        assertEquals(maxY, bounds.maxY(), EPSILON, "max y of " + bounds);
        assertEquals(maxZ, bounds.maxZ(), EPSILON, "max z of " + bounds);
    }

    private static GeckoGeometryBounds.Bone turned(double pivotX, double pivotY, double pivotZ,
                                                   double rotX, double rotY, double rotZ,
                                                   List<GeckoGeometryBounds.Cube> cubes,
                                                   List<GeckoGeometryBounds.Bone> children) {
        return new GeckoGeometryBounds.Bone(pivotX, pivotY, pivotZ, rotX, rotY, rotZ,
                1.0D, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D, cubes, children);
    }

    @Test
    @DisplayName("an unturned cube is its own box")
    void anUnturnedCubeIsItsOwnBox() {
        GeckoGeometryBounds.Bone body = GeckoGeometryBounds.Bone.at(0.0D, 0.0D, 0.0D,
                List.of(GeckoGeometryBounds.Cube.box(-0.5D, 0.0D, -0.25D, 0.5D, 2.0D, 0.25D)), List.of());
        assertBounds(-0.5D, 0.0D, -0.25D, 0.5D, 2.0D, 0.25D, measure(body));
    }

    @Test
    @DisplayName("a bone turned a quarter about its pivot swings its cube round that pivot")
    void aBoneTurnsAboutItsPivot() {
        // An arm sticking out along +x from a shoulder one block up, turned a quarter about z:
        // it ends up pointing straight up from the shoulder.
        GeckoGeometryBounds.Bone arm = turned(0.0D, 16.0D, 0.0D, 0.0D, 0.0D, QUARTER_TURN,
                List.of(GeckoGeometryBounds.Cube.box(0.0D, 1.0D, -0.25D, 1.0D, 1.5D, 0.25D)), List.of());
        assertBounds(-0.5D, 1.0D, -0.25D, 0.0D, 2.0D, 0.25D, measure(arm));
    }

    @Test
    @DisplayName("a child bone is posed inside its parent's pose")
    void nestedBonesComposeParentFirst() {
        GeckoGeometryBounds.Bone hand = turned(16.0D, 0.0D, 0.0D, 0.0D, 0.0D, QUARTER_TURN,
                List.of(GeckoGeometryBounds.Cube.box(1.0D, 0.0D, -0.1D, 2.0D, 0.2D, 0.1D)), List.of());
        GeckoGeometryBounds.Bone body = turned(0.0D, 0.0D, 0.0D, 0.0D, QUARTER_TURN, 0.0D,
                List.of(), List.of(hand));
        // The hand's own quarter turn stands it up at x = 1; the body's quarter turn about y
        // then carries x = 1 round to z = -1.
        assertBounds(-0.1D, 0.0D, -1.0D, 0.1D, 1.0D, -0.8D, measure(body));
    }

    @Test
    @DisplayName("the inflate grows a cube on every side")
    void inflateGrowsTheBox() {
        GeckoGeometryBounds.Cube inflated = new GeckoGeometryBounds.Cube(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                0.125D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        GeckoGeometryBounds.Bone bone = GeckoGeometryBounds.Bone.at(0.0D, 0.0D, 0.0D, List.of(inflated), List.of());
        assertBounds(-0.125D, -0.125D, -0.125D, 1.125D, 1.125D, 1.125D, measure(bone));
    }

    @Test
    @DisplayName("a bone's scale works about its pivot")
    void scaleWorksAboutThePivot() {
        GeckoGeometryBounds.Bone doubled = new GeckoGeometryBounds.Bone(8.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 2.0D, 0.0D, 0.0D, 0.0D,
                List.of(GeckoGeometryBounds.Cube.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)), List.of());
        assertBounds(-0.5D, 0.0D, 0.0D, 1.5D, 2.0D, 2.0D, measure(doubled));
    }

    @Test
    @DisplayName("a bone's offset moves it in pixels")
    void theOffsetMovesTheBone() {
        GeckoGeometryBounds.Bone moved = new GeckoGeometryBounds.Bone(0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 16.0D, 8.0D, 0.0D,
                List.of(GeckoGeometryBounds.Cube.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)), List.of());
        assertBounds(1.0D, 0.5D, 0.0D, 2.0D, 1.5D, 1.0D, measure(moved));
    }

    @Test
    @DisplayName("a cube turns about its own pivot, apart from its bone")
    void aCubeTurnsAboutItsOwnPivot() {
        GeckoGeometryBounds.Cube plank = new GeckoGeometryBounds.Cube(0.0D, 0.0D, 0.0D, 2.0D, 1.0D, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, QUARTER_TURN, 0.0D);
        GeckoGeometryBounds.Bone bone = GeckoGeometryBounds.Bone.at(0.0D, 0.0D, 0.0D, List.of(plank), List.of());
        // A quarter about y carries +x round to -z and +z round to +x.
        assertBounds(0.0D, 0.0D, -2.0D, 1.0D, 1.0D, 0.0D, measure(bone));
    }

    @Test
    @DisplayName("a model with nothing to draw has no bounds")
    void nothingToDrawHasNoBounds() {
        assertTrue(GeckoGeometryBounds.compute(List.of()).isEmpty());
        assertTrue(GeckoGeometryBounds.compute(null).isEmpty());
        GeckoGeometryBounds.Bone empty = GeckoGeometryBounds.Bone.at(4.0D, 4.0D, 4.0D, List.of(), List.of());
        assertTrue(GeckoGeometryBounds.compute(List.of(empty)).isEmpty(),
                "a bone with no cube, like a locator, marks no space");
    }

    @Test
    @DisplayName("a tall model is fitted by its height and stands on the bottom margin")
    void aTallModelFitsItsHeight() {
        GeckoGeometryBounds.Bounds tall = new GeckoGeometryBounds.Bounds(-0.5D, 0.0D, -0.5D, 0.5D, 4.0D, 0.5D);
        GeckoGeometryBounds.Fit fit = GeckoGeometryBounds.fit(tall, 200.0D, 200.0D, 0.08D, 4096.0D);
        assertEquals(42.0D, fit.scale(), EPSILON, "84 % of 200 pixels over 4 blocks");
        assertEquals(16.0D, fit.centerAboveBottom() - tall.height() * fit.scale() * 0.5D, EPSILON,
                "the feet rest on the 8 % margin");
    }

    @Test
    @DisplayName("a long model is fitted by its diagonal so no turn pushes it out of the box")
    void aLongModelFitsItsDiagonal() {
        GeckoGeometryBounds.Bounds serpent = new GeckoGeometryBounds.Bounds(-3.0D, 0.0D, -4.0D, 3.0D, 1.0D, 4.0D);
        GeckoGeometryBounds.Fit fit = GeckoGeometryBounds.fit(serpent, 200.0D, 200.0D, 0.08D, 4096.0D);
        assertEquals(10.0D, serpent.horizontalDiagonal(), EPSILON);
        assertEquals(16.8D, fit.scale(), EPSILON, "84 % of 200 pixels over the 10 block diagonal");
        // Turned so that its diagonal lies across the screen, it is exactly as wide as the free width.
        assertEquals(168.0D, serpent.horizontalDiagonal() * fit.scale(), EPSILON);
        assertEquals(16.0D, fit.centerAboveBottom() - serpent.height() * fit.scale() * 0.5D, EPSILON,
                "a model wider than tall still stands on the floor of the box");
    }

    @Test
    @DisplayName("a speck of a model is not blown up past the cap, and still stands on the margin")
    void aSpeckStopsAtTheCap() {
        GeckoGeometryBounds.Bounds speck = new GeckoGeometryBounds.Bounds(0.0D, 0.0D, 0.0D, 0.001D, 0.001D, 0.001D);
        GeckoGeometryBounds.Fit fit = GeckoGeometryBounds.fit(speck, 2000.0D, 2000.0D, 0.08D, 4096.0D);
        assertEquals(4096.0D, fit.scale(), EPSILON);
        assertEquals(160.0D, fit.centerAboveBottom() - speck.height() * fit.scale() * 0.5D, EPSILON);
    }

    @Test
    @DisplayName("scaling bounds scales them about the origin")
    void boundsScaleAboutTheOrigin() {
        GeckoGeometryBounds.Bounds bounds = new GeckoGeometryBounds.Bounds(-1.0D, 0.0D, -2.0D, 1.0D, 3.0D, 2.0D);
        assertBounds(-2.0D, 0.0D, -4.0D, 2.0D, 6.0D, 4.0D, bounds.scaled(2.0D));
    }
}
