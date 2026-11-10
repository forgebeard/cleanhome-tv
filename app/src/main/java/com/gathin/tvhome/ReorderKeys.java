package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * What a key does while reorder mode or edit-dock mode is on. The mode is one of six named states, worked out
 * from MainActivity's fields; decide() is the whole key table, so MainActivity only carries the answer out.
 *
 * Reorder mode (top-bar button, the tile menu's Move):
 * - BROWSE, nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
 *   Back ends the mode and saves the order.
 * - CARRY, a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
 *
 * Edit-dock mode (Settings menu, the tile menu's "Replace in dock" and "Add to dock"):
 * - DOCK_PICK_PLACE: OK on a dock tile chooses that place and focus moves to the grid; Back leaves the mode.
 * - DOCK_PICK_APP, a place chosen: OK on any tile swaps that app into the place, saves and ends the mode;
 *   Back goes back to choosing the place.
 * - DOCK_PLACE_FOR_APP, "Add to dock": the app is chosen, OK on a dock tile puts it there; Back leaves the mode.
 *
 * OK never opens an app in any of these. Other keys (volume, Home) pass through.
 */
final class ReorderKeys {

    enum Mode { OFF, BROWSE, CARRY, DOCK_PICK_PLACE, DOCK_PICK_APP, DOCK_PLACE_FOR_APP }

    enum Key { OK, BACK, ARROW, OTHER }

    enum Action {
        /** Not ours: the event goes on to the focused view. */
        PASS,
        /** Ours, and nothing happens: the other half of a press, or OK where it has no meaning. */
        CONSUME,
        /** The carried tile moves one step in the arrow's direction. */
        MOVE,
        /** The carried tile is put down; the mode stays on. */
        DROP,
        /** The mode ends and the order is saved. */
        END,
        /** The focused tile is picked up. */
        PICK_UP,
        /** OK on the App library pill: the mode ends, then the library opens. */
        END_AND_OPEN_LIBRARY,
        /** Edit dock: from choosing the app back to choosing the place. */
        DOCK_BACK_TO_PLACE,
        /** Edit dock: the focused dock tile becomes the place to change. */
        DOCK_CHOOSE_PLACE,
        /** Edit dock: the app that was waiting ("Add to dock") goes into the focused dock place. */
        DOCK_SWAP_WAITING_APP,
        /** Edit dock: the focused app goes into the chosen place. */
        DOCK_SWAP_INTO_PLACE
    }

    private ReorderKeys() {
    }

    /**
     * @param carrying     a tile is being carried (movingKey)
     * @param dockEditSlot the dock place being changed, or -1 while it is still being chosen
     * @param appWaiting   "Add to dock": an app waits for a dock place (dockEditAppKey)
     */
    static Mode mode(boolean reordering, boolean carrying, boolean dockEditing, int dockEditSlot, boolean appWaiting) {
        if (!reordering) return Mode.OFF;
        if (dockEditing) {
            if (appWaiting) return Mode.DOCK_PLACE_FOR_APP;
            return dockEditSlot >= 0 ? Mode.DOCK_PICK_APP : Mode.DOCK_PICK_PLACE;
        }
        return carrying ? Mode.CARRY : Mode.BROWSE;
    }

    /** BUTTON_A is not OK here: reorder mode never took a gamepad's A as OK. */
    static Key key(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return Key.OK;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                return Key.BACK;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return Key.ARROW;
            default:
                return Key.OTHER;
        }
    }

    /**
     * @param down      the event is ACTION_DOWN; everything else acts as the release
     * @param released  ACTION_UP and not cancelled (the library pill asks for exactly that)
     * @param onLibrary focus is on the App library pill
     * @param inGrid    focus is somewhere in the grid
     * @param pos       adapter position of the focused tile, or -1 when focus is not on a tile
     * @param movable   how many tiles can move (the "Default home" tile stays put)
     * @param columns   tiles per row; the dock is the first row
     */
    static Action decide(Mode mode, Key key, boolean down, boolean released, boolean onLibrary, boolean inGrid,
                         int pos, int movable, int columns) {
        switch (mode) {
            case CARRY:
                if (key == Key.ARROW) return down ? Action.MOVE : Action.CONSUME;
                if (key == Key.OK || key == Key.BACK) return down ? Action.CONSUME : Action.DROP;
                return Action.PASS;
            case BROWSE:
                if (key == Key.BACK) return down ? Action.CONSUME : Action.END;
                if (key != Key.OK) return Action.PASS;   // arrows navigate
                // The App library pill sits in the grid but is no tile: OK ends the mode (saving the order) and opens it.
                if (onLibrary) return released ? Action.END_AND_OPEN_LIBRARY : Action.CONSUME;
                // RecyclerView can briefly report NO_POSITION during a move or rebind. Still consume
                // OK on the grid: falling through would let the focused card open its app in reorder mode.
                if (inGrid) return !down && pos >= 0 ? Action.PICK_UP : Action.CONSUME;
                return Action.PASS;   // OK on a top-bar pill works as usual
            case DOCK_PICK_PLACE:
            case DOCK_PICK_APP:
            case DOCK_PLACE_FOR_APP:
                if (key == Key.BACK) {
                    if (down) return Action.CONSUME;
                    return mode == Mode.DOCK_PICK_APP ? Action.DOCK_BACK_TO_PLACE : Action.END;
                }
                if (key != Key.OK || pos < 0) return Action.PASS;   // arrows navigate as usual
                if (down) return Action.CONSUME;                    // never opens the app while editing
                boolean inDock = pos < Math.min(columns, movable);
                if (mode == Mode.DOCK_PLACE_FOR_APP) return inDock ? Action.DOCK_SWAP_WAITING_APP : Action.CONSUME;
                if (mode == Mode.DOCK_PICK_PLACE) return inDock ? Action.DOCK_CHOOSE_PLACE : Action.CONSUME;
                return pos < movable ? Action.DOCK_SWAP_INTO_PLACE : Action.CONSUME;
            default:
                return Action.PASS;
        }
    }
}
