package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for scene_home: where the row of cards scrolls to and how dark an unfocused card is. */
public class SceneHomeTest {

    @Test
    public void aCardAlreadyOnTheAnchorDoesNotScroll() {
        assertEquals(0, SceneHome.anchorDx(0, 0));
        assertEquals(0, SceneHome.anchorDx(24, 24));
    }

    @Test
    public void aCardAheadScrollsForwardByItsWholeDistanceEvenWhenItIsOnScreen() {
        // The next card is one cell (419 px) to the right of the anchor and fully visible: still the whole cell.
        assertEquals(419, SceneHome.anchorDx(419, 0));
        assertEquals(395, SceneHome.anchorDx(419, 24));
    }

    @Test
    public void aCardBehindScrollsBack() {
        assertEquals(-419, SceneHome.anchorDx(-419, 0));
    }

    @Test
    public void theLastCardGetsTheRestOfTheViewportAfterIt() {
        // 1920 px wide, 419 px cells: the last cell can reach the left edge only with 1501 px after it.
        assertEquals(1501, SceneHome.endSpacePx(1920, 419, 0));
        assertEquals(1477, SceneHome.endSpacePx(1920, 419, 24));
    }

    @Test
    public void aRowNarrowerThanItsViewportNeedsNoEndSpace() {
        assertEquals(0, SceneHome.endSpacePx(400, 419, 0));
        assertEquals(0, SceneHome.endSpacePx(419, 419, 0));
    }

    @Test
    public void aWhiteBannerFloodsGreyNotWhite() {
        assertEquals(0xFF737373, SceneHome.floodTint(0xFFFFFFFF));
    }

    @Test
    public void aVividColourKeepsItsHueAndIsCappedAtThreeQuartersBrightness() {
        assertEquals(0xFFBF0000, SceneHome.floodTint(0xFFFF0000));
    }

    @Test
    public void aColourDarkerThanItsCapIsLeftAlone() {
        assertEquals(0xFF301020, SceneHome.floodTint(0xFF301020));
        assertEquals(0xFF000000, SceneHome.floodTint(0xFF000000));
    }

    @Test
    public void fullBrightnessNeedsNoLayerAndZeroBrightnessIsBlack() {
        assertEquals(0f, SceneHome.dimAlpha(1f), 0f);
        assertEquals(1f, SceneHome.dimAlpha(0f), 0f);
    }

    @Test
    public void theRestingBrightnessIsAFortyPercentBlackLayer() {
        assertEquals(0.4f, SceneHome.dimAlpha(SceneHome.REST_BRIGHTNESS), 0.0001f);
    }

    @Test
    public void brightnessOutsideZeroToOneIsClamped() {
        assertEquals(0f, SceneHome.dimAlpha(1.7f), 0f);
        assertEquals(1f, SceneHome.dimAlpha(-0.3f), 0f);
    }
}
