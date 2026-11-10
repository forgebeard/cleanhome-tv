package com.gathin.tvhome;

import static com.gathin.tvhome.ReorderKeys.Action;
import static com.gathin.tvhome.ReorderKeys.Key;
import static com.gathin.tvhome.ReorderKeys.Mode;
import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

public class ReorderKeysTest {

    private static final int COLUMNS = 4;
    private static final int MOVABLE = 10;

    /** A key going down with focus on the tile at pos. */
    private static Action down(Mode mode, Key key, int pos) {
        return ReorderKeys.decide(mode, key, true, false, false, true, pos, MOVABLE, COLUMNS);
    }

    /** The same key coming up. */
    private static Action up(Mode mode, Key key, int pos) {
        return ReorderKeys.decide(mode, key, false, true, false, true, pos, MOVABLE, COLUMNS);
    }

    // ---- mode ----

    @Test public void offWhenNotReordering() {
        assertEquals(Mode.OFF, ReorderKeys.mode(false, true, true, 2, true));
    }

    @Test public void browseUntilATileIsCarried() {
        assertEquals(Mode.BROWSE, ReorderKeys.mode(true, false, false, -1, false));
        assertEquals(Mode.CARRY, ReorderKeys.mode(true, true, false, -1, false));
    }

    @Test public void editDockChoosesThePlaceThenTheApp() {
        assertEquals(Mode.DOCK_PICK_PLACE, ReorderKeys.mode(true, false, true, -1, false));
        assertEquals(Mode.DOCK_PICK_APP, ReorderKeys.mode(true, false, true, 0, false));
    }

    @Test public void anAppWaitingForAPlaceWinsOverAChosenPlace() {
        assertEquals(Mode.DOCK_PLACE_FOR_APP, ReorderKeys.mode(true, false, true, -1, true));
        assertEquals(Mode.DOCK_PLACE_FOR_APP, ReorderKeys.mode(true, false, true, 1, true));
    }

    @Test public void editDockWinsOverACarriedTile() {
        assertEquals(Mode.DOCK_PICK_PLACE, ReorderKeys.mode(true, true, true, -1, false));
    }

    // ---- keys ----

    @Test public void okBackAndArrowsAreRecognised() {
        assertEquals(Key.OK, ReorderKeys.key(KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(Key.OK, ReorderKeys.key(KeyEvent.KEYCODE_ENTER));
        assertEquals(Key.OK, ReorderKeys.key(KeyEvent.KEYCODE_NUMPAD_ENTER));
        assertEquals(Key.BACK, ReorderKeys.key(KeyEvent.KEYCODE_BACK));
        assertEquals(Key.BACK, ReorderKeys.key(KeyEvent.KEYCODE_ESCAPE));
        assertEquals(Key.ARROW, ReorderKeys.key(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(Key.ARROW, ReorderKeys.key(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(Key.ARROW, ReorderKeys.key(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(Key.ARROW, ReorderKeys.key(KeyEvent.KEYCODE_DPAD_DOWN));
    }

    @Test public void volumeHomeAndAGamepadsAAreNotOurs() {
        assertEquals(Key.OTHER, ReorderKeys.key(KeyEvent.KEYCODE_VOLUME_UP));
        assertEquals(Key.OTHER, ReorderKeys.key(KeyEvent.KEYCODE_HOME));
        assertEquals(Key.OTHER, ReorderKeys.key(KeyEvent.KEYCODE_BUTTON_A));
    }

    // ---- off ----

    @Test public void nothingIsTakenWhenTheModeIsOff() {
        for (Key key : Key.values()) {
            assertEquals(Action.PASS, down(Mode.OFF, key, 0));
            assertEquals(Action.PASS, up(Mode.OFF, key, 0));
        }
    }

    // ---- carrying ----

    @Test public void arrowsMoveTheCarriedTileOnDownOnly() {
        assertEquals(Action.MOVE, down(Mode.CARRY, Key.ARROW, 3));
        assertEquals(Action.CONSUME, up(Mode.CARRY, Key.ARROW, 3));
    }

    @Test public void okOrBackPutsTheCarriedTileDownOnRelease() {
        assertEquals(Action.CONSUME, down(Mode.CARRY, Key.OK, 3));
        assertEquals(Action.DROP, up(Mode.CARRY, Key.OK, 3));
        assertEquals(Action.CONSUME, down(Mode.CARRY, Key.BACK, 3));
        assertEquals(Action.DROP, up(Mode.CARRY, Key.BACK, 3));
    }

    @Test public void otherKeysPassWhileCarrying() {
        assertEquals(Action.PASS, down(Mode.CARRY, Key.OTHER, 3));
        assertEquals(Action.PASS, up(Mode.CARRY, Key.OTHER, 3));
    }

    // ---- browsing ----

    @Test public void backEndsTheModeOnRelease() {
        assertEquals(Action.CONSUME, down(Mode.BROWSE, Key.BACK, 3));
        assertEquals(Action.END, up(Mode.BROWSE, Key.BACK, 3));
    }

    @Test public void okPicksUpTheFocusedTileOnRelease() {
        assertEquals(Action.CONSUME, down(Mode.BROWSE, Key.OK, 3));
        assertEquals(Action.PICK_UP, up(Mode.BROWSE, Key.OK, 3));
    }

    @Test public void okOnTheGridWithNoPositionIsEatenSoTheAppDoesNotOpen() {
        assertEquals(Action.CONSUME, down(Mode.BROWSE, Key.OK, -1));
        assertEquals(Action.CONSUME, up(Mode.BROWSE, Key.OK, -1));
    }

    @Test public void okOnTheLibraryPillEndsTheModeAndOpensTheLibrary() {
        assertEquals(Action.CONSUME,
                ReorderKeys.decide(Mode.BROWSE, Key.OK, true, false, true, true, -1, MOVABLE, COLUMNS));
        assertEquals(Action.END_AND_OPEN_LIBRARY,
                ReorderKeys.decide(Mode.BROWSE, Key.OK, false, true, true, true, -1, MOVABLE, COLUMNS));
    }

    @Test public void aCancelledOkOnTheLibraryPillOpensNothing() {
        assertEquals(Action.CONSUME,
                ReorderKeys.decide(Mode.BROWSE, Key.OK, false, false, true, true, -1, MOVABLE, COLUMNS));
    }

    @Test public void okOnATopBarPillWorksAsUsual() {
        assertEquals(Action.PASS,
                ReorderKeys.decide(Mode.BROWSE, Key.OK, true, false, false, false, -1, MOVABLE, COLUMNS));
        assertEquals(Action.PASS,
                ReorderKeys.decide(Mode.BROWSE, Key.OK, false, true, false, false, -1, MOVABLE, COLUMNS));
    }

    @Test public void arrowsAndOtherKeysNavigateWhileBrowsing() {
        assertEquals(Action.PASS, down(Mode.BROWSE, Key.ARROW, 3));
        assertEquals(Action.PASS, up(Mode.BROWSE, Key.ARROW, 3));
        assertEquals(Action.PASS, down(Mode.BROWSE, Key.OTHER, 3));
    }

    // ---- edit dock ----

    @Test public void okOnADockTileChoosesThePlace() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PICK_PLACE, Key.OK, 2));
        assertEquals(Action.DOCK_CHOOSE_PLACE, up(Mode.DOCK_PICK_PLACE, Key.OK, 2));
    }

    @Test public void okOnAGridTileChoosesNoPlaceAndOpensNothing() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PICK_PLACE, Key.OK, COLUMNS));
        assertEquals(Action.CONSUME, up(Mode.DOCK_PICK_PLACE, Key.OK, COLUMNS));
    }

    @Test public void aShortDockEndsAtTheLastMovableTile() {
        // Three movable tiles in four columns: position 3 is the "Default home" tile, not a dock place.
        assertEquals(Action.CONSUME,
                ReorderKeys.decide(Mode.DOCK_PICK_PLACE, Key.OK, false, true, false, true, 3, 3, COLUMNS));
        assertEquals(Action.DOCK_CHOOSE_PLACE,
                ReorderKeys.decide(Mode.DOCK_PICK_PLACE, Key.OK, false, true, false, true, 2, 3, COLUMNS));
    }

    @Test public void backWhileChoosingThePlaceLeavesTheMode() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PICK_PLACE, Key.BACK, 0));
        assertEquals(Action.END, up(Mode.DOCK_PICK_PLACE, Key.BACK, 0));
    }

    @Test public void okOnAnyMovableTileSwapsItIntoTheChosenPlace() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PICK_APP, Key.OK, 7));
        assertEquals(Action.DOCK_SWAP_INTO_PLACE, up(Mode.DOCK_PICK_APP, Key.OK, 7));
        assertEquals(Action.DOCK_SWAP_INTO_PLACE, up(Mode.DOCK_PICK_APP, Key.OK, 1));
    }

    @Test public void theDefaultHomeTileCannotGoIntoTheDock() {
        assertEquals(Action.CONSUME, up(Mode.DOCK_PICK_APP, Key.OK, MOVABLE));
    }

    @Test public void backWhileChoosingTheAppGoesBackToThePlace() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PICK_APP, Key.BACK, 7));
        assertEquals(Action.DOCK_BACK_TO_PLACE, up(Mode.DOCK_PICK_APP, Key.BACK, 7));
    }

    @Test public void addToDockPutsTheWaitingAppIntoTheFocusedDockPlace() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PLACE_FOR_APP, Key.OK, 1));
        assertEquals(Action.DOCK_SWAP_WAITING_APP, up(Mode.DOCK_PLACE_FOR_APP, Key.OK, 1));
    }

    @Test public void addToDockIgnoresOkOnAGridTile() {
        assertEquals(Action.CONSUME, up(Mode.DOCK_PLACE_FOR_APP, Key.OK, COLUMNS + 1));
    }

    @Test public void backCancelsAddToDock() {
        assertEquals(Action.CONSUME, down(Mode.DOCK_PLACE_FOR_APP, Key.BACK, 0));
        assertEquals(Action.END, up(Mode.DOCK_PLACE_FOR_APP, Key.BACK, 0));
    }

    @Test public void okOffATilePassesInEveryEditDockStep() {
        for (Mode mode : new Mode[]{Mode.DOCK_PICK_PLACE, Mode.DOCK_PICK_APP, Mode.DOCK_PLACE_FOR_APP}) {
            assertEquals(Action.PASS, down(mode, Key.OK, -1));
            assertEquals(Action.PASS, up(mode, Key.OK, -1));
            assertEquals(Action.PASS, down(mode, Key.ARROW, 0));
            assertEquals(Action.PASS, down(mode, Key.OTHER, 0));
        }
    }

    @Test public void okNeverPassesToATileWhileAnyModeIsOn() {
        for (Mode mode : Mode.values()) {
            if (mode == Mode.OFF) continue;
            for (int pos = 0; pos <= MOVABLE; pos++) {
                assertEquals(mode + " down at " + pos, false, down(mode, Key.OK, pos) == Action.PASS);
                assertEquals(mode + " up at " + pos, false, up(mode, Key.OK, pos) == Action.PASS);
            }
        }
    }
}
