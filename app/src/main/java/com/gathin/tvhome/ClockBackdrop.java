package com.gathin.tvhome;

/**
 * What the clock screensaver draws behind the clock (ClockScene). One of three, never a mix: the slow coloured
 * glow, Home's wallpaper, or plain black for an OLED panel.
 */
final class ClockBackdrop {

    enum Kind { GLOW, PICTURE, BLACK }

    private ClockBackdrop() {
    }

    /**
     * The backdrop the settings ask for. clock_wallpaper wins while there is a picture to show; with none, or with
     * clock_wallpaper off, clock_glow decides between the glow and black.
     */
    static Kind choose(boolean clockWallpaper, boolean clockGlow, boolean hasPictures) {
        if (clockWallpaper && hasPictures) return Kind.PICTURE;
        return withoutPicture(clockGlow);
    }

    /** Behind the clock until a picture is decoded, and for good when none can be read. */
    static Kind withoutPicture(boolean clockGlow) {
        return clockGlow ? Kind.GLOW : Kind.BLACK;
    }
}
