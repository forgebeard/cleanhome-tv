# Install CleanHome on an Android TV: guide for AI agents

You are helping a person who is not a programmer. They pasted a prompt like «Install CleanHome on my TV» into
Claude Code, Codex or another coding agent. Your job: put the CleanHome launcher on their Android TV and make it
the Home screen, then show them how to change it. Do the computer side yourself. The person only uses the TV
remote, and you tell them exactly which buttons to press.

Talk to the person in their language, in plain words. One step at a time, and say what they will see on the TV.

On the TV the app is not called CleanHome: its name follows the TV language. Russian: «Главный экран»,
Ukrainian: «Головний екран», English and others: «Home». The package is `com.gathin.tvhome`.

## Safety rules

- Everything you do must be reversible by uninstalling one app. Never factory-reset the TV, never flash firmware,
  never root it.
- Never disable or uninstall a stock app (including the stock launcher) unless the person agrees after you
  explain the rollback command. Write the rollback command down for them before you run the change.
- Never give the TV a static IP address. A static IP once took a computer's address on the home network.
- Change no TV system setting beyond the ones named in this guide without asking.
- Stop and ask when the TV shows something this guide does not describe.

## What you need

- The APK. Take the newest release: `gh release download --repo ungurenko/cleanhome-tv --pattern '*.apk'`.
  Without a signed-in `gh`, use the public API:
  `curl -sL https://api.github.com/repos/ungurenko/cleanhome-tv/releases/latest | grep -o 'https://[^"]*\.apk' | head -n 1 | xargs curl -sLO`.
  Build from source only when the person wants changes (see «Change the launcher»).
- The TV and the computer in the same home network.
- `adb` for path A: `brew install android-platform-tools` on macOS, `winget install Google.PlatformTools` on
  Windows, `sudo apt install adb` on Debian/Ubuntu.

## Step 1. Find the TV

1. Ask for the TV model and the Android version, if the person knows it.
2. Ask them to open Settings → Network (or «Сеть») on the TV and read you the IP address. This is the fastest way.
3. In parallel, look yourself: `arp -a` lists devices in the network; a TV with network debugging answers on
   port 5555 (`nc -z -w 1 <ip> 5555`).

## Step 2. Choose the path

- **Path A, ADB over Wi-Fi.** Most Android TV and Google TV sets, Xiaomi, TCL, Tuvio, Haier and others.
- **Path B, download in the TV browser.** Sets where the vendor locked developer options and ADB, for example
  Yandex TV Stations on YaOS (tested: Яндекс ТВ Станция MiniLED, Android 14). Take path B when the TV has no
  «Developer options» after 7 presses on Build, or when `adb connect` answers «Connection refused» with network
  debugging on.

## Path A: ADB over Wi-Fi

1. Turn on developer options: Settings → Device Preferences → About → «Build» (or «Сборка»). Ask the person to
   press OK on it 7 times until the TV says they are a developer.
2. Settings → Device Preferences → Developer options: turn on «USB debugging» and, if present, «Network
   debugging» / «ADB over network».
3. Connect: `adb connect <ip>:5555`. Tell the person a dialog appears on the TV and ask them to choose «Always
   allow from this computer» and press OK. Check with `adb devices` that the state is `device`.
4. Install: `adb -s <ip>:5555 install -r CleanHome-*.apk`.
5. Speed up the start (about a third faster cold start on a weak TV):
   `adb -s <ip>:5555 shell cmd package compile -m speed -f com.gathin.tvhome`.
6. Wi-Fi name on the Home screen needs location access:
   `adb -s <ip>:5555 shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION`.
7. Make it Home:
   - Android 10 and newer: start it with
     `adb -s <ip>:5555 shell am start -n com.gathin.tvhome/.MainActivity`. The launcher asks once to become the
     Home app; ask the person to choose «Главный экран» («Home»).
   - Android 9 and older: `adb -s <ip>:5555 shell cmd package set-home-activity com.gathin.tvhome/.MainActivity`.
   - If the Home button still opens the stock screen, the firmware pins its own launcher (common on Google TV).
     Explain the choice and ask first. Find the stock package with
     `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`,
     then `adb shell pm disable-user --user 0 <stock package>`. Rollback: `adb shell pm enable <stock package>`.
     Google TV has more traps (a setup wizard that claims Home): read the upstream notes in
     https://github.com/Gathin23/cleanyourtv before you do this there.
8. Check: `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
   ends with `com.gathin.tvhome/.MainActivity`. Take a screenshot with `adb exec-out screencap -p > home.png`
   and show it to the person.
9. Network debugging works without encryption on port 5555, and on some firmware any device in the network can
   connect without the confirmation dialog. When the person plans no more changes, tell them how to turn
   «Network debugging» off in Developer options. Keep it on only while you keep changing the launcher.

## Path B: download in the TV browser

1. Start the download page on the computer: `python3 scripts/serve-apk.py CleanHome-*.apk`. It prints the
   address, for example `http://192.168.1.20:8000`. If the first address does not open on the TV, try the
   other printed ones. A firewall prompt on the computer must be allowed for incoming connections.
2. Ask the person to open the browser on the TV (on Yandex TV it is «Браузер» in the apps), type the address
   with the remote and press «Скачать / Download».
3. Ask them to open the downloaded file. The TV asks to allow app installs from the browser: they allow it
   once, go back and press «Install».
4. Ask them to open «Главный экран» («Home») from the app list. It asks once to become the Home app: they
   choose it.
   If they closed that dialog: on the CleanHome screen hold OK on the «Настройки» (Settings) pill at the top,
   choose «Настройки главного экрана» (Launcher settings), then «Главный экран» (Home screen). The TV settings
   also have Apps → Default apps → Home on most firmware.
5. Ask them to press Home on the remote and tell you what they see. You cannot take screenshots without ADB:
   ask for a phone photo of the screen when something looks wrong.
6. Stop the server with Ctrl+C when the install is done.

Without ADB the speed-up command from path A cannot run. The launcher still works, its first start is slower.

## Updates and rollback

- Update: install the new APK the same way (`adb install -r` or the browser). Android accepts an update only
  with a higher `versionCode` and the same signature. Release APKs are signed with one key; a build from your
  computer has another, so remove the release build first (`adb uninstall com.gathin.tvhome`).
- Rollback: Settings → Apps → «Главный экран» («Home») → Uninstall, or `adb uninstall com.gathin.tvhome`. The stock Home
  screen comes back. Re-enable anything you disabled in path A, step 7.

## Change the launcher (vibe-coding)

This is the point of the project: the person tells you what they want, you change the code, build and install.

- Build: JDK 17 and the Android command-line tools. macOS:
  `brew install openjdk@17 android-commandlinetools`, then
  `sdkmanager "platforms;android-34" "build-tools;34.0.0"`, then
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleRelease`.
  The APK is `app/build/outputs/apk/release/app-release.apk`. Tests: `./gradlew testDebugUnitTest`.
- Before every install raise `versionCode` in `app/build.gradle`, or the TV refuses the update.
- Main settings: `app/src/main/res/values/config.xml` (dock apps, hidden apps, clock, columns and many more,
  each with a comment). Theme overrides: `app/src/main/assets/theme/config.json`.
- Wallpapers: put 1920×1080 JPEGs into `app/src/main/assets/theme/wallpapers/` and rebuild. They rotate every
  30 minutes (`wallpaper_rotation_minutes`), and the on-TV picker shows them with previews.
- The Home screen layout «Сцена» lives in `SceneHome.java` and `MainActivity.java`; a performance report with
  measured numbers is in `PERFORMANCE-REVIEW.md`.
- Comments in the code mention `build-and-install.sh` and `tools/tv.py`. Those scripts belong to the upstream
  CleanYourTV repository and are not part of this fork; here use plain `adb shell` commands instead.

Show the person each visible change on the TV (path A: a screenshot) before you move to the next one.
