package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClockBackdropTest {

    @Test
    public void theGlowIsTheDefault() {
        assertEquals(ClockBackdrop.Kind.GLOW, ClockBackdrop.choose(false, true, false));
        assertEquals(ClockBackdrop.Kind.GLOW, ClockBackdrop.choose(false, true, true));
    }

    @Test
    public void glowOffLeavesPlainBlack() {
        assertEquals(ClockBackdrop.Kind.BLACK, ClockBackdrop.choose(false, false, false));
        assertEquals(ClockBackdrop.Kind.BLACK, ClockBackdrop.choose(false, false, true));
    }

    @Test
    public void aWallpaperReplacesTheGlowWhateverClockGlowSays() {
        assertEquals(ClockBackdrop.Kind.PICTURE, ClockBackdrop.choose(true, true, true));
        assertEquals(ClockBackdrop.Kind.PICTURE, ClockBackdrop.choose(true, false, true));
    }

    @Test
    public void wallpaperOnWithNoPicturesFallsBackToTheGlow() {
        assertEquals(ClockBackdrop.Kind.GLOW, ClockBackdrop.choose(true, true, false));
    }

    @Test
    public void wallpaperOnWithNoPicturesAndGlowOffIsBlackNotTheGlow() {
        assertEquals(ClockBackdrop.Kind.BLACK, ClockBackdrop.choose(true, false, false));
    }

    @Test
    public void whileAPictureDecodesTheGlowSettingDecides() {
        assertEquals(ClockBackdrop.Kind.GLOW, ClockBackdrop.withoutPicture(true));
        assertEquals(ClockBackdrop.Kind.BLACK, ClockBackdrop.withoutPicture(false));
    }
}
