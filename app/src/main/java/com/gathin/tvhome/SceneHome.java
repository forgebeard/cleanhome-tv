package com.gathin.tvhome;

/**
 * The numbers behind the "Scene" home screen (config.xml, scene_home), kept apart from the views so they can be
 * checked without a device: where the row of cards scrolls to, and how dark an unfocused card is.
 */
final class SceneHome {
    /** An unfocused card is drawn at this share of its brightness; the focused one at full. */
    static final float REST_BRIGHTNESS = 0.6f;

    private SceneHome() {
    }

    /**
     * How far the row has to scroll sideways so the card whose cell starts at cellLeft stands on the anchor, the
     * row's left padding: positive scrolls forward, negative back. Always the whole distance, even when the cell is
     * already on screen, so the focused card never moves from the anchor and the others pass under it.
     */
    static int anchorDx(int cellLeft, int anchorPx) {
        return cellLeft - anchorPx;
    }

    /**
     * Room to leave after the last cell so it can scroll as far as the anchor too: what is left of the viewport once
     * the anchor and one cell are taken off. Never negative (a row narrower than its viewport needs none).
     */
    static int endSpacePx(int viewWidthPx, int cellWidthPx, int anchorPx) {
        return Math.max(0, viewWidthPx - cellWidthPx - anchorPx);
    }

    /**
     * The alpha of a black layer over the card that draws it at the given brightness: 1 is untouched (no layer), 0 is
     * black. Black over a picture at alpha a is the picture times (1 - a), which is exactly what multiplying by a grey
     * did. Unlike a colour filter, changing a layer's alpha redraws nothing: the card's own drawing is left alone.
     */
    static float dimAlpha(float brightness) {
        return 1f - Math.max(0f, Math.min(1f, brightness));
    }
}
