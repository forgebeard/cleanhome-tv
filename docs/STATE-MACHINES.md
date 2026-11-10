# State machines in CleanHome

A map of every behaviour in the launcher that has modes. Read the entry for a machine before you change it.

An **implicit state machine** here is a behaviour whose mode lives in scattered fields (boolean flags, `-1`/`null`
sentinels, a view's visibility, a posted `Runnable`, a SharedPreferences key) while its transitions are spread over
several methods. Nothing names the states, so a new transition is easy to add without resetting a field.

- Written 2026-11-10 against version 4.6.2, from a full read of `app/src/main/java/com/gathin/tvhome/`.
- Line numbers drift. Every entry names fields and methods instead: search for those.
- `unverified` marks a risk that was read out of the code and never reproduced. Reproduce it before fixing it.
- Only two defects were reproduced on an Android TV 14 emulator; both are fixed in 4.6.2 (see the tables).

Status values:

| Status | Meaning |
|---|---|
| `explicit` | States are named (an enum or a small class) and the rules sit in one place, usually with a unit test. |
| `implicit-ok` | Scattered fields, but small and well guarded. Leave it unless you are changing that behaviour. |
| `implicit-risky` | Scattered fields with an ordering rule, a duplicate flag or a race. Read its note below first. |

## How to make a machine explicit here

Follow the pattern the project already uses. Do not add a framework.

1. A small `final` class with **no `android.*` imports**: an `enum` for the states or the decision, and static
   functions (or a few named methods) for the transitions. Models: `TileRefresh` (`Action`, `onResume`),
   `LibraryLoads` (`start`, `isLatest`, `takeReveal`), `ClockBackdrop` (`Kind`, `choose`), `ReorderKeys` (`Mode`,
   `Action`, `decide`), `RaceWeekend.State` with
   the pure `at(calendar, now)`, `Cards.State`.
2. A JUnit 4 test next to it, `<Class>Test`, whose method names are sentences about behaviour
   (`aWhiteBannerFloodsGreyNotWhite`, `wallpaperOnWithNoPicturesAndGlowOffIsBlackNotTheGlow`). Models:
   `TileRefreshTest`, `LibraryLoadsTest`, `ClockBackdropTest`, `RaceWeekendTest`, `CardsTest`.
3. The Activity, Service or View stays a thin binding: it feeds the class and acts on what comes back. Keep the
   existing side-effect methods; move only the decision.
4. Run `./gradlew :app:testDebugUnitTest`. There is no Robolectric, so anything that needs a `View`, a `Handler` or a
   `Context` cannot be unit tested: check it on a TV or the Android TV emulator and say so.

A machine earns this only when it buys the viewer something (a fixed defect, a behaviour locked by tests). Working
code with no such gain stays as it is and stays on this map.

## MainActivity

| Machine | Where (fields) | States | Status |
|---|---|---|---|
| Reorder tiles and edit dock | `reordering`, `movingKey`, `orderAtStart`, `dockInSession`, `dockedAtStart`, `dockEditing`, `dockEditSlot`, `dockEditAppKey`; also `AppAdapter.reordering` | off; browsing; carrying a tile; `ReorderKeys.Mode`: OFF, BROWSE, CARRY, DOCK_PICK_PLACE, DOCK_PICK_APP, DOCK_PLACE_FOR_APP | keys `explicit` (`ReorderKeys`); begin / end `implicit-risky` |
| Focus, glass and navigation | `lastFocused`, `lastFocusedKey` (static), `glassShown`, `glassCardRect`, `glassTargetX`, `libraryFocusPending`, `pendingFocusScroll` | glass hidden / shown; focus on a tile, the library pill, the top bar; waiting for a scroll; waiting to focus the library | `implicit-risky` |
| Menus and overlays | `openPopup`, `cardPrompt`, `appLibrary`, `libraryFromTopBar`, `libraryFromVpnPill`, `promptsPaused`, `gestureHint`; gate `homeBusy()` | nothing open; a menu; the App library; the card prompt; prompts paused | `explicit` (gate) |
| Home lifecycle | `started`, `stopped`, `resumed`, `tilesReady`, `pillsShown`, `settingsRevision`, `settingsRecreationPending` | covered; visible; in front; recreating | `implicit-risky` |
| Hero shelf and ambient glow | `heroShown`, `heroArtKey`, `heroArtCache`, `ambientColor`, `ambientAnimator`, `glowSnap` | shelf hidden / shown / changing; glow steady / moving / dimmed | `implicit-risky` |
| Tile list and package changes | `tileRefresh`, `packageSequence`, `tilesReady`, `lastReloadMs` | current; stale; rebuild or refocus on resume | `explicit` (`TileRefresh`) |
| Open on start and on wake | `openOnStartChoice`, `openOnStartWaitMs`, `openOnStartOnWake`, `startPending`, `startDeadline`, `bootToLiveTv` | idle; waiting for the network; opened; cancelled by leaving Home | `implicit-ok` |
| Hold OK on a tile | `holdOkDown`, `holdOkPosition`, `holdOkKey`, `holdOkFired`, `holdOkTimer` | no press; down and timing; fired (UP is eaten); interrupted | `implicit-ok` |
| Race and game-day panel | `gameDay`, `raceCalendar`, `rotationStart`, `lastEndedRead`, `turnTick` | hidden; one turn; rotating turns; F1 or team turn | `implicit-risky` |
| Clock and greeting | `clockCheck`, `updateClockVisibility`, `minuteTick` | time valid / not yet valid (polled each second) | `implicit-ok` |
| Festive skin | `festive`, `festival`, `lamp`, `lastPetalShower` (static) | skin off / on; lamp lit; petals once a day | `implicit-ok` |
| Stats line | `statsThread`, `statsBg`, `procStatDead` | stopped / running | `implicit-ok` |
| Loose deferred work and caches | `maybeAskForNetworkName`, `refocusAfterLayout`, `vpnPillState`, `showInputsPill` / `showInputsMenuRow`, `idleClockMs` / `showIdleClock` | one-shot posts and cached pill state | `implicit-risky` |

## Tiles, library, cards, screensaver

| Machine | Where (fields) | States | Status |
|---|---|---|---|
| Reorder, adapter side | `AppAdapter`: `reordering`, `savedRanks`, `savedDocked`, `docked`, `emptySlots` | normal; reordering (empty dock slots left out); saved | `implicit-risky` (same machine as above) |
| App library life cycle | `AppLibraryDialog`: `closed`, `loads`, `popup`; `LibraryLoads`: `latest`, `revealPending` | loading; shown; closed | `explicit` (`LibraryLoads`) |
| App library focus and actions menu | `AppLibraryDialog`: `selectedPackage`, `selectedPosition`, `selectedOffset`, `actionPopup`, `catalog`, `spring`, `highlightShown` | focus on a card; actions menu open; catalogue rebuilt | `implicit-risky` |
| Module cards | `Cards.State` (NEW, RESIGNED, RELABELLED, SHOWN, HIDDEN), `Cards.Record` | pending (three kinds); shown; hidden | `explicit` |
| Scene tile brightness | `SceneHome.REST_BRIGHTNESS`; `AppAdapter` holder: `scene`, `dim` | focused (full); at rest (0.6) | `implicit-ok` |
| Screensaver backdrop | `ClockScene`: `backdrop` (`ClockBackdrop.Kind`), `wallpaperFile`, `shownFile`, `wallpaperGeneration`, `rotation`, `shownMs` | GLOW; PICTURE; BLACK; stopped | `explicit` |
| Saved order and library choices | `Order` (`decode`, `ranks`, `dockKey`), `LibraryApps` (two files) | order applies / ignored; app on Home / removed | `implicit-ok` |

## Services, keys, settings, wallpaper

| Machine | Where (fields) | States | Status |
|---|---|---|---|
| Home at power-on | `HomeKeyService`: `powerOnLiveTv`, `powerOnUntil`, `powerOnAlways`, `screenOnReceiver` | off; armed for the boot window; armed after screen-on; done | `implicit-risky` |
| Short and long press | `RemoteKeys.Presses`: `buttons`; per press `downAt`, `upAt`, `fired`, `hold`, `owners` | idle; down and timing; held and fired; released | `implicit-risky` |
| Accessibility service list | `GuardKeep` (`screenOn`, `ensureHomeKey`, `stepOut`), `ServiceRepair` (`restore`, `replaceList`), `HomeKeyService` (`steppingOut`, `standbyExit`) | helper listed; stepped out for standby; repair loop running | `implicit-risky` |
| Wallpaper choice and theme sync | `Config`: `wallpaper_choice`, `theme_signature`, `SETTINGS_REVISION_KEY`, `syncThemeChoices`, `readOverlay`; `LauncherSettingsActivity.followThemeRow` | rotation; one file; none; reset by a theme | `implicit-risky` |
| Wallpaper player | `WallpaperPlayer`: `active`, `closed`, `loading`, `displayed`, `current`, `firstPicture` | created; attached; active / stopped; loading; closed; memory released | `implicit-risky` |
| Volume auto-repeat | `VolumeKeys`: static `handler`, `step` | idle; repeating | `implicit-ok` |
| Key filter and gamepad | `GamepadPassthrough` (`update`), `RemoteKeys.applyKeyFilter`; the `FLAG_REQUEST_FILTER_KEY_EVENTS` bit | filtering on / off | `implicit-ok` |
| Launcher settings preview | `LauncherSettingsActivity`: `columns`, `rowsBelowDock`, `dockOpacity`, `clockFormat`, `previewFile`, `previewBitmap` | each choice held in a field and in prefs | `implicit-ok` |
| Stock Home redirect | `HomeKeyService`: `redirectHomeKey`, prefs key `probe_boot`; `ProbeActivity` | probed this boot / not yet | `implicit-ok` |
| Wait for unlock | `HomeKeyService.unlockReceiver`, `GuardRevival` | waiting; unlocked | `implicit-ok` |
| Bat and petal overlays | `BatFlightView`, `PetalShowerView`: `play`, the `done` callback | idle; playing; done | `implicit-ok` |
| Startup warm-up | `StartupWarmup`: `released` | warming; released | `implicit-ok` |
| VPN badge | `VpnBadge`: `lock`, `pillState`, the `PILL_*` constants | no VPN; VPN up; app only | `implicit-ok` |
| Open on start, per boot | `OpenOnStart`: `live_tv_boot`, `handledBoot` | not handled this boot / handled | `implicit-ok` |
| Home settings picker | `HomeSettingsActivity`: `left` | picker open; left | `implicit-ok` |
| Festival date | `Festival`: `active(today)`, `accentColor` | active / not | `explicit` |
| Game-day timer | `GameDay`: `start`, `stop`, `turnSeconds`, `delayToNextTurn` | stopped / running | `explicit` |
| Clock refresh | `ClockRefresh`: `start`, `stop` | stopped / running | `explicit` |
| Race weekend | `RaceWeekend.Status`, `RaceWeekend.State` | per session status | `explicit` |

## Notes on the risky ones

### Reorder tiles and edit dock (`MainActivity`, `AppAdapter`)

- **Order in `beginReorder` is load-bearing:** `adapter.setReordering(true)`, then `reloadTiles()`, then
  `reordering = true`. `reloadTiles()` starts with `endReorder()`, which returns at once only while `reordering` is
  still false. Raise the flag one line earlier and the mode ends before it begins.
- **`endReorder` and `reloadTiles` call each other.** The loop stops only because `endReorder` clears `reordering`
  before it calls `reloadTiles()`. Keep that clear above the call.
- **Two flags, one truth:** `MainActivity.reordering` and `AppAdapter.reordering` are synced by hand in
  `beginReorder` and `endReorder`. Any new exit must go through `endReorder`.
- `setSavedOrder` takes effect at the adapter's next reload. If `Order.save` fails, the screen keeps the new order
  and the file the old one until the next rebuild (a toast says so).
- `endReorder` is called from a dozen places (Back, the button, `onPause`, `reloadTiles`, every dock-edit exit). It
  must stay idempotent.
- **Keys are explicit.** `ReorderKeys.mode(...)` names the mode from the fields above and `ReorderKeys.decide(...)`
  is the whole key table (`ReorderKeysTest`, one test per branch, plus "OK never opens an app in any mode").
  `MainActivity.dispatchKeyEvent` only carries the `Action` out. A new key rule goes into `decide` with a test.
- **Begin and end are still implicit:** the fields stay in `MainActivity`, and the three notes above still hold.
- Checked on the Android TV 14 emulator, old build against new, 24 checkpoints (Move, Reorder apps, Replace in
  dock, Add to dock, Back at every step, Home while carrying): same focus, same saved order, same pixels. Not
  exercised there: OK on the App library pill (`app_library` is off) and Edit dock from the Settings menu.

### Focus, glass and navigation (`MainActivity`)

- `libraryFocusPending` is cleared in five places. If `focusLibrary()` returns early, the flag stays set and
  `refitGlass` / `restoreFocus` keep choosing the library pill. `unverified`
- `onPause` cancels `pendingFocusScroll` but not `libraryFocusPending`. Whether that is intended is `unverified`.
- `lastFocused` / `lastFocusedKey` are static and outlive `recreate()`; a rebuild can put another app in that slot,
  so compare the key, not only the position.
- `glassTargetX` / `glassTargetY` are written and never read in this file.
- The pair `!animations || reducedMotion` versus `animations && !reducedMotion` is repeated in more than ten places.
  A new animation must take the same branch.

### Home lifecycle (`MainActivity`)

- `onResume` returns early when `openLiveTvAfterBoot()` opens an app. That skips `gameDay.start()`,
  `wallpaperPlayer.start()`, the `minuteTick` post, the stats and the lamp, while `onPause` still stops them. The
  stops are safe on something never started; keep them that way.
- "Home is on screen" is spelled three ways: `!stopped`, `started`, `resumed`. They differ while the translucent
  quick panel is over Home (paused, not stopped). Pick the one you mean; see the next note.
- `tilesReady` is set synchronously in one branch of `reloadApps` and through `grid.post` in another.

### Race and game-day panel (`MainActivity`)

- `updatePanel` schedules `turnTick` when `!stopped`, but `onPause` is what removes it. A `GameDay` callback between
  `onPause` and `onStop` arms it again; it then runs once more and stops itself. Harmless today, `unverified` on a TV.
- `racePanel` visibility is written in four places.

### Hero shelf and ambient glow (`MainActivity`)

- Chained `withEndAction` blocks in `showHero`, `showGlowOnly` and `showHeroArt` capture the colour and text at call
  time. A fast run of focus changes can let an end action apply an old value. `unverified`
- How the glow changes is decided in three places with different priority (`advancedEffects`, `glowSnap`).
  `setAmbient` picks the shape by scene > cinematic > glow horizon > default.
- `reducedMotion` does not switch off `advancedEffects` everywhere (tile lift, pill scale).

### Loose deferred work (`MainActivity`, `HomeKeyService`)

Posts that nothing cancels when Home leaves the screen or the service dies:

- `maybeShowGestureHint`, posted from `onResume` (a new post on every resume).
- `playPetalShower`, posted from `onResume`; guarded only by `isFinishing()`.
- `maybeAskForNetworkName`, posted from `onCreate`; it can call `requestPermissions` after Home was left. `unverified`
- `refocusAfterLayout` waits up to ten frames and can run while paused.
- `HomeKeyService`: the power-on posts in `armPowerOnHome` and the delayed `openHome` in `redirectHomeKey` are not
  removed in `onDestroy`. A second `loadConfig` posts again. Harm is `unverified`.

Also: `showInputsPill` and `showInputsMenuRow` hold one setting, and `top_menu` changes only the first.

### App library focus (`AppLibraryDialog`)

- `restoreCardFocus` returns focus by `selectedPosition`, not by `selectedPackage`. After the catalogue is rebuilt
  the index may point at another app. `unverified`
- No unit tests; `LibraryApps.nextFocus` and `nextAfterRemoval` are the tested parts.

### Home at power-on (`HomeKeyService`)

- `powerOnUntil` holds both an absolute boot-window time and a time relative to screen-on; `0` means "not armed".
- The mode depends on three flags at once (`powerOnLiveTv`, `powerOnAlways`, the receiver being registered).

### Short and long press (`RemoteKeys.Presses`)

- **Two clocks measure one hold:** the `Handler` timer and `eventTime - downAt`. A busy main thread can make a short
  press take the hold branch. `unverified`
- Both key services see the same DOWN; the copy is recognised only by an equal `eventTime`. That it always matches
  is `unverified`.
- `hold` reads the first entry of `owners` without an emptiness check; safe only because both are cleared together.
- `release(owner)` is how a paused Home or a dying service gives up its share. A new owner must call it.

### Accessibility service list (`GuardKeep`, `ServiceRepair`)

- **Highest risk in the app.** `GuardKeep.ensureHomeKey` writes the system's enabled-services list without reading
  it back. `ServiceRepair.restore` writes the same list from a background thread in three steps a second apart,
  verifying each in `replaceList`. One can overwrite the other. Read from the code, never seen on a TV: `unverified`
- `ensureHomeKey` has four callers and no shared lock.
- "Stepped out on purpose" is known in two places: `HomeKeyService.steppingOut` and `GuardKeep.screenOn`.
- Test only on a real TV. A wrong write here switches the Home button off.

### Wallpaper choice and theme sync (`Config`, `LauncherSettingsActivity`)

- Two reset paths disagree: "Follow theme" (`followThemeRow`) clears the Open on start choice,
  `Config.syncThemeChoices` does not, and `hasOnTvChoices` counts it as the owner's choice.
- `Config.readOverlay` deletes the kept copy when `config.json` exists but cannot be read (bad JSON, over 64 KB).
  On the next start before storage is mounted the theme is gone.
- The theme signature is length and modification time; an edit that keeps both goes unnoticed. `unverified`
- `Config.load` runs on many paths and rewrites the copy each time.

### Wallpaper player (`WallpaperPlayer`)

- `loading` is cleared only inside `root.post` in `update()`. If `update()` ran before `attach()` set `root`, the
  decoder thread would throw and `loading` would stay true: the wallpaper would stop changing. Today `start()` is
  always called after `attach()`. `unverified`
- `files` is touched by the decoder thread (`loadFirst`) and the main thread (`update`) without a lock; safe only
  because of that same call order.
- `attach` blocks the main thread on the first picture by design.

## Fixed in 4.6.2

- **Tile menu stuck after an empty Inputs list.** `openPopup` was set when a menu was built. An Inputs list with no
  rows never opened, its dismiss listener never ran, and hold OK and the Menu key did nothing until the process
  restarted. Reachable only with `top_menu` (or a VIEW_INPUTS intent) on a TV that lists no inputs. Now `openPopup`
  is set at show time (`dropIn`, and before `showAtLocation` in `onTileLongPress`) and the four checks share
  `homeBusy()`. Rule: never assign `openPopup` for a popup that might not be shown.
- **Screensaver glow instead of black.** `ClockScene.wallpaperShown` meant three things. It is now
  `ClockBackdrop.Kind`: `clock_wallpaper` with no pictures and `clock_glow` false draws plain black, and a failed
  rotation decode keeps the old picture without restarting the glow cycle.

## Deliberately left alone

Focus and glass, the hero shelf and glow, the Home lifecycle, `RemoteKeys.Presses`, `GuardKeep` / `ServiceRepair`,
`Config` and themes, `WallpaperPlayer`, `HomeKeyService` (Home at power-on), `VolumeKeys`, `GamepadPassthrough`, the
race panel and the festive skin.

Reasons, the same for all: they work; making them explicit would not change anything the viewer sees; and they can
only be checked on a real TV (remote timing, the accessibility service, boot and standby) or with features that are
switched off in this build. Change one only together with a reproduced defect and a test that fails before the fix.
