# TV Launcher

A personal home screen for a Chromecast with Google TV (Android 14, API 34, armeabi-v7a, 1920x1080 render surface). It replaces the busy stock Google TV home with a calm, glassy dashboard. It runs as a normal app, and can optionally become the Home button's target (see [Home button](#home-button)); the stock launcher stays installed and is only disabled by an explicit `tools/tv.sh home-take`.

Built with Kotlin and Jetpack Compose for TV (`androidx.tv:tv-material`), DataStore, OkHttp (Open-Meteo weather) and kotlinx-serialization. Package `com.nickesh.tvlauncher`. The design source is in `tv-launcher-design/` and the v1 design doc is `docs/superpowers/specs/2026-10-29-tv-launcher-v1-design.md`.

## Features

- Welcome splash ("Welcome <name>!!!") that cross-fades into Home once the apps and background are ready.
- Home: greeting by home-time hour, hero clock (12 h or 24 h), date, weather card (current, hi/lo, 4-day forecast from Open-Meteo, cached), three world-clock cards with analog dials, and an apps row (favourites first, then alphabetical) ending in **All apps**.
- Top bar: Ask pill (assistant, generic mic icon), Search, Inputs (hidden if the system has no Inputs page), Settings, Profile.
- All apps: full-screen grid of every leanback app.
- Settings and Background screens, all D-pad driven, with the TV on-screen keyboard for text rows.
- Flip-clock screensaver as a real `DreamService`.
- Every network call degrades to cache or placeholders; no analytics, no accounts.

## Dev loop

System Java is 27 on this Mac and Gradle needs 21:

    export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home

- Build: `./gradlew assembleDebug` (`-Pprobe=true` builds a second installable copy, id suffix `.probe`, for focus-on-uninstall checks)
- Tests: `./gradlew :app:testDebugUnitTest`; lint: `./gradlew lintDebug`
- Connect, install and launch on the TV (wireless adb, 10.0.0.21): `tools/tv.sh connect && tools/tv.sh install`
- Screenshot to `shots/<name>.png` (git-ignored): `tools/tv.sh shot <name>`
- Home-button switch-over and screen-off timeout: `tools/tv.sh home-status | home-take | home-restore | idle-set <minutes>` (see below)
- If a second adb device is attached, pass `-s <serial>` to raw adb; `tools/tv.sh` picks the 10.0.0.21 serial itself.

## Screensaver

The Chromecast with Google TV has no picker for third-party screensavers, so select ours over adb once:

    tools/tv.sh dream-set        # sets screensaver_components, enables it
    tools/tv.sh dream            # start it now
    tools/tv.sh dream-stop       # wake it

`dream-set` runs `adb shell settings put secure screensaver_components com.nickesh.tvlauncher/.ScreensaverService` plus `screensaver_enabled 1` and `screensaver_activate_on_sleep 1`.

## Settings

Name, Home city, Home time zone, three world clocks (city and time zone each), Weather city, Units (F/C), 24-hour clock, Accent colour (4 options), Screensaver dim, Show seconds (screensaver), and Background... (source: built-in scenes Dusk/Night/Forest or solid colour; Google Photos is shown as "Coming soon"; photo change interval and shuffle for the future photo source).

## Performance (debug build, Chromecast with Google TV, armv7)

Measured 2026-10-29 with `am start -W` after a force-stop:

- First frame (TotalTime): about 3.1 to 4.5 s (runs: 4.47, 4.05, and 3.1 to 3.6 in polling runs).
- Welcome to settled Home: the splash holds at least 1.2 s after the first frame, then cross-fades for 400 ms, so Home is up roughly 1.6 s after the first frame (about 5 to 6 s after launch). Screenshot polling on this device takes 1 to 2 s per capture, so finer timing was not measurable.
- The "within about 2 s" goal in the design is not met by the debug build on this hardware. A release (minified, R8) build has not been measured.

## Home button

The app declares the HOME intent filter and replays the Welcome splash after at least 10 minutes of standby (matches the TV's "Screen saver -> When to start"; see `docs/superpowers/specs/2026-10-30-home-takeover-design.md`). Switching the Home key over is done from the Mac over adb:

    tools/tv.sh home-status      # prints the current HOME component and `launcherx disabled: 0|1`
    tools/tv.sh home-take        # set-home-activity to TV Launcher, disable the stock launcher (launcherx), then home-status
    tools/tv.sh home-restore     # re-enable launcherx, set-home-activity back to launcherx/.home.HomeActivity, then home-status
    tools/tv.sh idle-set <min>   # sets system screen_off_timeout to <min> minutes (integer >= 1) and prints it back (600000 = 10 min)

`idle-set` only changes `system screen_off_timeout`; `secure sleep_timeout` is left alone.

**Stop rule:** if after `home-take` the screen goes black, the app crashes, or the Home key does nothing, run `tools/tv.sh home-restore` straight away.

**Remote-only restore** (no Mac): in TV Launcher, Settings -> "Google TV Home..." opens the stock launcher's app-details page. If the page offers Enable, press it. Note: while the launcher is enabled the page shows no Enable/Disable control, so this path only helps when it is disabled and the page offers Enable. To remove TV Launcher entirely: Enable the stock launcher first, then uninstall.

**Wireless debugging turns itself off on standby; keep Stay awake on** (Developer options) so adb stays reachable, otherwise `home-restore` may not be possible after the TV sleeps.

Debug builds accept `-PwelcomeReplayMs=<ms>` (for example `./gradlew installDebug -PwelcomeReplayMs=20000`) to shorten the Welcome-replay standby threshold for testing.

## v2 backlog

- Google Photos Picker backgrounds (QR plus device-code OAuth; needs a Google Cloud OAuth client).
- Rearrange the apps row (and a favourites editor).
- Snow and storm weather icons (both map to rain in v1).
- Per-clock weather.
