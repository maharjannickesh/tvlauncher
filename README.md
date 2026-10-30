# TV Launcher

A personal home screen for a Chromecast with Google TV (Android 14, API 34, armeabi-v7a, 1920x1080 render surface). It replaces the busy stock Google TV home with a calm, glassy dashboard. It runs as a normal app, and an optional accessibility service makes the remote's Home button open it (see [Home button](#home-button)); nothing on the TV is disabled.

Built with Kotlin and Jetpack Compose for TV (`androidx.tv:tv-material`), DataStore, OkHttp (Open-Meteo weather) and kotlinx-serialization. Package `com.nickesh.tvlauncher`. The design source is in `tv-launcher-design/` and the v1 design doc is `docs/superpowers/specs/2026-10-29-tv-launcher-v1-design.md`.

## Features

- Welcome splash ("Welcome <name>!!!") that cross-fades into Home once the apps and background are ready.
- Home: greeting by home-time hour in title case ("Good Afternoon, Nickesh", name from Settings), hero clock (12 h or 24 h), date, weather card for the home city (current, hi/lo, 4-day forecast from Open-Meteo, cached), three digital world-clock cards each with that city's weather icon and temperature (moon for a clear night; a dimmed "--°" while loading, failed or just re-picked), and an apps row (favourites first, then alphabetical) ending in **All apps**.
- Top bar: Ask pill (assistant, generic mic icon), Search, Inputs (hidden if the system has no Inputs page), Settings, Profile.
- Search opens **All apps with a search field**: the TV keyboard rises as soon as the field is focused and the grid filters as you type. Back with the keyboard up dismisses it, the next Back clears the query, the next leaves. The **All apps** tile on Home opens the same screen with the first app focused instead.
- Settings and Background screens, all D-pad driven, with the TV on-screen keyboard for text rows and a right-side city picker for places.
- Weather: four independent fetches (home plus the three clocks), cached 30 minutes per city, refreshed sooner when the location changes.
- Flip-clock screensaver as a real `DreamService`.
- Every network call degrades to cache or placeholders; no analytics, no accounts.

## Dev loop

System Java is 27 on this Mac and Gradle needs 21:

    export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home

- Build: `./gradlew assembleDebug` (`-Pprobe=true` builds a second installable copy, id suffix `.probe`, for focus-on-uninstall checks)
- Tests: `./gradlew :app:testDebugUnitTest`; lint: `./gradlew lintDebug`
- Connect, install and launch on the TV (wireless adb, 10.0.0.21): `tools/tv.sh connect && tools/tv.sh install`
- Screenshot to `shots/<name>.png` (git-ignored): `tools/tv.sh shot <name>`
- Idle permission (see Settings): `tools/tv.sh idle-perm` grants "Modify system settings" to the app, `tools/tv.sh idle-perm-deny` revokes it
- Home key service: `tools/tv.sh homekey-on | homekey-off | homekey-status`; virtual remote press (`input keyevent` never reaches accessibility services, so this uses uinput): `tools/tv.sh remote short|long`; screen-off timeout: `tools/tv.sh idle-set <minutes>` (see below)
- If a second adb device is attached, pass `-s <serial>` to raw adb; `tools/tv.sh` picks the 10.0.0.21 serial itself.

## Screensaver

The Chromecast with Google TV has no picker for third-party screensavers, so select ours over adb once:

    tools/tv.sh dream-set        # sets screensaver_components, enables it
    tools/tv.sh dream            # start it now
    tools/tv.sh dream-stop       # wake it

`dream-set` runs `adb shell settings put secure screensaver_components com.nickesh.tvlauncher/.ScreensaverService` plus `screensaver_enabled 1` and `screensaver_activate_on_sleep 1`.

## Settings

Settings is a draft: edits stay in memory until you move to the **Save** row at the end and press OK. Back with unsaved changes asks "Discard changes?" (Keep editing / Discard). The Background screen is not part of the draft; it still applies immediately.

Rows: Name, **Home**, **Clock 1/2/3**, Units (F/C), 24-hour clock, Accent colour (4 options), Screensaver dim, Show seconds (screensaver), **Screensaver after**, Background... (source: built-in scenes Dusk/Night/Forest or solid colour; Google Photos is shown as "Coming soon"; photo change interval and shuffle for the future photo source), Home button... (On/Off; opens the TV Accessibility page, the state refreshes when Settings is reopened), and Save.

- **Home and Clock 1/2/3** open a city picker on the right. Type a city and press Done: up to 8 geocoded matches appear (name, region, country and time zone). Picking one sets the city, its time zone and its coordinates together; typed text alone changes nothing. The **Time zone** row in the picker opens the full zone list in two steps (region, then zone) for when you want a zone other than the match's. The big weather card follows the home city; the old "Weather city" setting is gone (installs that had one keep it until a home city is picked).
- **Screensaver after** (OK opens a list: 1, 2, 5, 10, 15, 30 or 60 min) writes the TV's `screen_off_timeout` when you Save. That needs the "Modify system settings" permission, and this Google TV has no settings page to grant it, so grant it once over adb:

      tools/tv.sh idle-perm        # grant (idle-perm-deny revokes it)

  Without it the row shows "Needs permission ›" and a toast with this hint.

## Performance (debug build, Chromecast with Google TV, armv7)

Measured 2026-10-29 with `am start -W` after a force-stop:

- First frame (TotalTime): about 3.1 to 4.5 s (runs: 4.47, 4.05, and 3.1 to 3.6 in polling runs).
- Welcome to settled Home: the splash holds at least 1.2 s after the first frame, then cross-fades for 400 ms, so Home is up roughly 1.6 s after the first frame (about 5 to 6 s after launch). Screenshot polling on this device takes 1 to 2 s per capture, so finer timing was not measurable.
- The "within about 2 s" goal in the design is not met by the debug build on this hardware. A release (minified, R8) build has not been measured.

## Home button

An accessibility service, **TV Launcher Home key** (`HomeKeyService`), makes the remote's Home button open TV Launcher. Nothing on the TV is disabled: the stock Google TV launcher stays installed and enabled.

Enable it in TV Settings -> System -> Accessibility -> TV Launcher Home key (the Settings row "Home button... On/Off" opens that page), or from the Mac:

    tools/tv.sh homekey-on       # appends the service to enabled_accessibility_services (keeps other services) and turns accessibility on
    tools/tv.sh homekey-off      # removes only our entry; turns accessibility off only if no other service remains
    tools/tv.sh homekey-status   # on/off and whether the service is bound
    tools/tv.sh remote short|long   # press Home on a uinput virtual remote (short, or held for 1+ s)
    tools/tv.sh idle-set <min>   # sets system screen_off_timeout to <min> minutes (600000 = 10 min)

- Short press: opens TV Launcher's Home with a plain intent (no Welcome splash; the Welcome replay after at least 10 minutes of standby still applies).
- Long press (1 s or more): opens Google TV's quick-settings dashboard (`GLOBAL_ACTION_NOTIFICATIONS`). A long press is consumed even if that call returns false (it is logged as `HomeKey`), so the dashboard may occasionally not appear.
- If launching our Home fails, the press is let through to Google TV.
- Caveat: `am force-stop com.nickesh.tvlauncher` makes Android switch the service off (a plain reinstall does not). Re-run `tools/tv.sh homekey-on` or re-enable it in TV Settings.
- Boot: Google TV's launcher shows first, then about 30 s later, once the service binds, it opens TV Launcher's Home on its own (observed 2026-10-30). That launch only happens within 90 s of boot (measured with `elapsedRealtime`), so it never fires on a standby wake or when the service is re-enabled later.

Why not disable the stock launcher? That route cannot work on this Chromecast: launcherx has HOME priority 2, setupwraith's `RecoveryActivity` has 1 and ours is capped at 0, the shell cannot disable that component, and it self-heals. Details: `docs/superpowers/reports/takeover-switchover.md`. The physical-remote check that selected the service approach is in `docs/superpowers/reports/homekey-gate.md`.

**Wireless debugging turns itself off on standby; keep Stay awake on** (Developer options) so adb stays reachable.

Debug builds accept `-PwelcomeReplayMs=<ms>` (for example `./gradlew installDebug -PwelcomeReplayMs=20000`) to shorten the Welcome-replay standby threshold for testing.

## v2 backlog

- Google Photos Picker backgrounds (QR plus device-code OAuth; needs a Google Cloud OAuth client).
- Rearrange the apps row (and a favourites editor).
- Snow and storm weather icons (both map to rain in v1).
