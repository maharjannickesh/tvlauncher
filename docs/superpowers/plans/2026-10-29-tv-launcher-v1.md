# TV Launcher v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Compose-for-TV launcher app for the Living Room Chromecast: Welcome splash → Home (clocks, weather, apps) + system screensaver + Background/Settings screens, installed and screenshot-verified on the real device.

**Architecture:** Single-activity Compose app with a tiny hand-rolled navigation state (`Screen` sealed class in `MainActivity`), one `AppContainer` on the `Application` holding three repositories (settings via DataStore, weather via OkHttp + Open-Meteo, apps via PackageManager). Pure logic (clock math, weather-code mapping, app ordering) lives in `domain/` with JVM unit tests; UI composables read a `HomeUiState` from `HomeViewModel`. A `DreamService` hosts the same background + a flip clock for the screensaver.

**Tech Stack:** Kotlin 2.2.10, AGP 8.13.0, Gradle 8.14.3, JDK 21, compileSdk 36 / minSdk 26, Compose BOM 2026.08.00, `androidx.tv:tv-material:1.1.0`, `androidx.datastore:datastore-preferences:1.1.7`, OkHttp 4.12.0, kotlinx-serialization-json 1.9.0, coroutines 1.10.2, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-29-tv-launcher-v1-design.md` (and the handoff `tv-launcher-design/DESIGN_SPEC.md` + `tv-launcher-design/mockups/*.dc.html`, which are the authority for layout/sizes/colours).

## Global Constraints

- Package `com.nickesh.tvlauncher`; app label "TV Launcher"; project root is `~/ClaudeProjects/tvlauncher` (Android project at the root, `tv-launcher-design/` untouched).
- Device is **armeabi-v7a, API 34, 1920×1080 at 320 dpi (= 960×540 dp)**. All mockup pixel values are converted with `LocalDesignScale` (= screenWidthDp / 1920f, so 0.5 on this device); never hardcode dp from the mockups.
- v1 manifest intent filters: `MAIN` + `LEANBACK_LAUNCHER` only. **No `HOME` category** in v1.
- `uses-feature android.software.leanback required=true`, `android.hardware.touchscreen required=false`; activity `android:banner` 320×180 drawable.
- No analytics, no accounts, no Google branding; the assistant pill says "Ask Gemini" with a generic mic icon.
- Fonts bundled: Outfit 200/300/400/500/600, Roboto Mono 500/600, under `app/src/main/res/font/` (snake_case names).
- Glass style: fill `Color(0x1AFFFFFF)` (white 10 %), 1 px border `Color(0x29FFFFFF)` (white 16 %), radius 28 design px. **Builder call: no backdrop blur in v1** (the scenes are flat vectors; Compose has no cheap backdrop blur). Spec's "blur 28" is dropped.
- Focus treatment everywhere: tv-material `Surface` with `scale(focusedScale = 1.1f)`, `border` 3 design px white radius-matched, `glow` elevation color = accent at 33 % alpha.
- Accent default `Color(0xFF8FB8FF)`; options `0xFFFFB27A`, `0xFF7FE0C2`, `0xFFF2A7D8`.
- Every network call degrades to cache/placeholder; the app must never crash on launch with Wi-Fi off.
- Commit after every task with a conventional message; attribution trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Build/test command: `JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home ./gradlew <task>` (system Java is 27; Gradle needs 21). Install: `tools/tv.sh install` (Task 1).

## Review Focus

1. Kathmandu (+5:45) and other quarter-hour zones: the meta line must read "+9h 45m" style, never "+9.75h" or a rounding to the hour. → ClockMath test in Task 3.
2. Day boundary: at 21:00 New York, Tokyo is "Tomorrow"; at 03:00 Tokyo, New York is "Yesterday". → ClockMath test in Task 3.
3. Weather fetch fails or Wi-Fi is off on launch: card shows cached data if any, else "--°" placeholders with the city; never a crash or an empty card that steals focus. → WeatherRepository test in Task 7 (network failure returns cached snapshot; no cache returns `null`, UI shows placeholder).
4. Fewer than 8 leanback apps installed, or an app uninstalled while its tile is focused: row shows what exists, "All apps" is always last, focus moves to a neighbour instead of being lost. → AppOrdering test (zero and three apps) in Task 6; manual uninstall check in Task 6 verification.
5. Midnight / noon formatting in 12 h mode: 00:05 → "12:05 AM", 12:05 → "12:05 PM"; 24 h mode shows "00:05" and hides AM/PM everywhere (hero, cards, screensaver). → ClockMath tests in Task 3; FlipClock digits test in Task 9.

---

## File structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/*, gradlew, .gitignore
tools/tv.sh                                   # adb helper: port discovery, connect, install, screenshot, dream
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/font/outfit_200.ttf … outfit_600.ttf, roboto_mono_500.ttf, roboto_mono_600.ttf
app/src/main/res/drawable/banner.xml          # 320x180 vector banner
app/src/main/res/xml/screensaver.xml          # <dream> metadata
app/src/main/java/com/nickesh/tvlauncher/
  TvLauncherApp.kt            # Application; AppContainer(settings, weather, apps)
  MainActivity.kt             # Screen state + cross-fade; owns HomeViewModel
  ScreensaverService.kt       # DreamService hosting ScreensaverContent
  data/settings/Settings.kt   # Settings data class, enums, DEFAULTS, zone list
  data/settings/SettingsRepository.kt   # DataStore <-> Settings mapping
  data/weather/OpenMeteo.kt   # DTOs + OpenMeteoClient (geocode, forecast)
  data/weather/WeatherRepository.kt     # cache + refresh loop
  data/apps/AppsRepository.kt # PackageManager query, banners, launch, broadcast
  data/system/SystemIntents.kt          # assist/search/inputs/settings/profile
  domain/clock/ClockMath.kt   # pure: ClockSnapshot, formatting, meta, greeting
  domain/weather/WeatherCode.kt         # pure: WMO code -> WeatherKind + label
  domain/apps/AppOrdering.kt  # pure: favourites-then-alpha, max 8
  ui/theme/Theme.kt           # fonts, colours, LocalDesignScale, Int.dpx/spx, Glass
  ui/components/Icons.kt      # ImageVectors traced from the mockup SVGs
  ui/components/GlassCard.kt, RoundButton.kt
  ui/background/Scenes.kt     # Dusk/Night/Forest painters + scrim + Background()
  ui/welcome/WelcomeScreen.kt
  ui/home/HomeViewModel.kt, HomeScreen.kt, TopBar.kt, HeroClock.kt, WeatherCard.kt, WorldClockCard.kt, AppsRow.kt
  ui/allapps/AllAppsScreen.kt
  ui/screensaver/FlipClock.kt, ScreensaverContent.kt
  ui/settings/SettingsScreen.kt, BackgroundScreen.kt
app/src/test/java/com/nickesh/tvlauncher/
  domain/clock/ClockMathTest.kt, domain/weather/WeatherCodeTest.kt, domain/apps/AppOrderingTest.kt,
  data/settings/SettingsMappingTest.kt, data/weather/WeatherRepositoryTest.kt, ui/screensaver/FlipDigitsTest.kt
README.md
```

---

### Task 1: Project scaffold, fonts, theme scale, device loop

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/drawable/banner.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/font/*.ttf`, `app/src/main/java/com/nickesh/tvlauncher/{TvLauncherApp.kt,MainActivity.kt}`, `app/src/main/java/com/nickesh/tvlauncher/ui/theme/Theme.kt`, `tools/tv.sh`, `README.md`
- Copy: `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat` from `~/ClaudeProjects/Greenlight/project/android/` (copy, do not symlink; project-isolation policy).

**Interfaces:**
- Produces: `object Design { const val WIDTH = 1920f; const val HEIGHT = 1080f }`; `val LocalDesignScale: ProvidableCompositionLocal<Float>`; extension `@Composable fun Int.dpx(): Dp` and `@Composable fun Int.spx(): TextUnit` (design px → Dp/TextUnit via `LocalDesignScale`); `val Outfit: FontFamily` (weights 200–600) and `val RobotoMono: FontFamily` (500, 600); `object Glass { val fill = Color(0x1AFFFFFF); val border = Color(0x29FFFFFF); val radiusPx = 28 }`; `val AccentOptions: List<Color>`; `@Composable fun TvLauncherTheme(accent: Color, content)` which provides `LocalDesignScale` = `LocalConfiguration.current.screenWidthDp / Design.WIDTH` and tv-material `MaterialTheme` with `Outfit` as the default typography family.
- Produces: `tools/tv.sh` with sub-commands `port` (prints the `_adb-tls-connect` port for 10.0.0.21 via `adb mdns services`, backgrounded + `sleep 6`, no `timeout` on this Mac), `connect`, `install` (`gradlew installDebug` then `adb shell am start -n com.nickesh.tvlauncher/.MainActivity`), `shot <name>` (`adb exec-out screencap -p > shots/<name>.png`), `dream` (Task 9 fills this). `ADB=$HOME/Library/Android/sdk/platform-tools/adb`, serial `10.0.0.21:$PORT`.

- [ ] **Step 1: Gradle files.** `settings.gradle.kts` as in the Bara repo but `rootProject.name = "TvLauncher"`; `gradle/libs.versions.toml` with the versions in the Tech Stack plus `tvMaterial = "1.1.0"`, `okhttp = "4.12.0"`, `kotlinxSerialization = "1.9.0"`, `coroutines = "1.10.2"`, `datastore = "1.1.7"`, `activityCompose = "1.10.1"`, `lifecycle = "2.9.2"`, `junit = "4.13.2"`; plugins `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`. `app/build.gradle.kts`: `namespace = "com.nickesh.tvlauncher"`, compileSdk 36, minSdk 26, targetSdk 36, `versionCode 1`, `versionName "0.1.0"`, `kotlin { jvmToolchain(21) }`, `buildFeatures { compose = true }`, deps: compose BOM, `androidx.compose.ui:ui`, `ui-graphics`, `foundation`, `material3` (for `Text` only), `androidx.tv:tv-material`, activity-compose, lifecycle-runtime-compose + viewmodel-compose, datastore-preferences, okhttp, kotlinx-serialization-json, coroutines-android, `testImplementation junit`, `kotlinx-coroutines-test`. `gradle.properties` copied from Bara (`-Xmx3g`, androidx, nonTransitiveRClass). `.gitignore`: `/local.properties`, `.gradle/`, `build/`, `*.iml`, `.idea/`, `.DS_Store`, `shots/`. Write `local.properties` with `sdk.dir=/Users/maharjannickesh/Library/Android/sdk` (gitignored).

- [ ] **Step 2: Fonts.** Fetch the Google Fonts CSS with a browser User-Agent and download each `src: url(...)` into `app/src/main/res/font/` named by weight:
```bash
cd app/src/main/res/font
curl -s -A "Mozilla/5.0" "https://fonts.googleapis.com/css2?family=Outfit:wght@200;300;400;500;600&family=Roboto+Mono:wght@500;600" \
 | python3 -c "
import re,sys,urllib.request
css=sys.stdin.read()
for fam,w,url in re.findall(r\"font-family: '([^']+)';\s*font-style: normal;\s*font-weight: (\d+);[^}]*?url\((https://[^)]+)\)\", css):
    name=('outfit' if fam=='Outfit' else 'roboto_mono')+'_'+w+'.ttf'
    urllib.request.urlretrieve(url,name); print(name)
"
ls -la
```
Expected: 7 `.ttf` files (outfit_200…600, roboto_mono_500, roboto_mono_600), each > 50 KB.

- [ ] **Step 3: Manifest + Application + Activity + banner.** Manifest: `<uses-feature android:name="android.software.leanback" android:required="true"/>`, `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`, `INTERNET` permission, `<application android:name=".TvLauncherApp" android:banner="@drawable/banner" android:label="@string/app_name" android:theme="@android:style/Theme.NoTitleBar.Fullscreen">`, `MainActivity` exported with `MAIN` + `LEANBACK_LAUNCHER`, `android:screenOrientation="landscape"`, `android:configChanges="keyboard|keyboardHidden|navigation"`. `banner.xml`: 320×180 vector, `#16213F` fill with the text "TV" is not possible in vector — use a rounded rect in `#16213F` with a `#8FB8FF` circle (r 40) at centre; good enough for the launcher tile. `TvLauncherApp : Application` with `lateinit var container: AppContainer` (AppContainer is an empty class for now; Tasks 2/6/7 add fields). `MainActivity : ComponentActivity` → `setContent { TvLauncherTheme(accent = AccentOptions[0]) { Box(Modifier.fillMaxSize().background(Color(0xFF0B0E1A))) { Text("TV Launcher", color = Color.White, fontSize = 96.spx(), fontFamily = Outfit, fontWeight = FontWeight.W200, modifier = Modifier.align(Alignment.Center)) } } }`.

- [ ] **Step 4: Theme.kt** with the interfaces above. `Int.dpx()` = `(this * LocalDesignScale.current).dp`; `Int.spx()` = `(this * LocalDesignScale.current).sp`. Typography: tv-material `Typography()` with `Outfit` applied to every style via `copy(fontFamily = Outfit)`.

- [ ] **Step 5: tools/tv.sh** as specified; `chmod +x`. Also `README.md` with the three commands (build, install, screenshot) and the JAVA_HOME line.

- [ ] **Step 6: Build.** Run: `JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home ./gradlew assembleDebug -q` Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`. (First run downloads dependencies; allow 10 min.)

- [ ] **Step 7: Install on the Chromecast and screenshot.** Run: `tools/tv.sh connect && tools/tv.sh install && sleep 3 && tools/tv.sh shot task1` Expected: `shots/task1.png` shows "TV Launcher" centred in thin white on dark navy at 1920×1080. If the TV is not connected (`adb devices` empty), run `tools/tv.sh port`; if it prints nothing the TV is asleep or needs re-pairing (see memory `living-room-tv-adb`) — ask the user.

- [ ] **Step 8: Commit.** `git add -A && git commit -m "feat: scaffold Compose for TV app with fonts, design scale and device tooling"`

---

### Task 2: Settings model + DataStore repository

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/settings/Settings.kt`, `.../data/settings/SettingsRepository.kt`
- Modify: `TvLauncherApp.kt` (AppContainer gets `settings: SettingsRepository`)
- Test: `app/src/test/java/com/nickesh/tvlauncher/data/settings/SettingsMappingTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class TempUnit { F, C }
enum class BackgroundSource { PHOTOS, SCENES, COLOR }      // PHOTOS exists but is disabled in v1 UI
enum class Scene { DUSK, NIGHT, FOREST }
enum class ChangeInterval(val minutes: Int?) { M5(5), M15(15), H1(60), DAILY(1440), NEVER(null) }
data class WorldClock(val city: String, val zoneId: String)
data class Settings(
  val name: String = "Nickesh",
  val homeCity: String = "Columbus", val homeZone: String = "America/New_York",
  val clocks: List<WorldClock> = listOf(WorldClock("Kathmandu","Asia/Kathmandu"), WorldClock("London","Europe/London"), WorldClock("Tokyo","Asia/Tokyo")),
  val weatherCity: String = "Columbus, OH", val unit: TempUnit = TempUnit.F, val use24h: Boolean = false,
  val accentArgb: Long = 0xFF8FB8FF,
  val backgroundSource: BackgroundSource = BackgroundSource.SCENES, val scene: Scene = Scene.DUSK, val solidColorArgb: Long = 0xFF0F1115,
  val changeInterval: ChangeInterval = ChangeInterval.H1, val shuffle: Boolean = true,
  val screensaverDim: Float = 0.35f, val screensaverSeconds: Boolean = false,
  val favourites: List<String> = DEFAULT_FAVOURITES)
val DEFAULT_FAVOURITES = listOf("com.google.android.youtube.tv","com.netflix.ninja","com.amazon.amazonvideo.livingroom","com.disney.disneyplus","com.spotify.tv.android","com.plexapp.android","com.nirajgtm.xtreamtv","com.android.vending")
val ZONE_OPTIONS: List<String>   // the 19 IANA ids from the mockup prop panel, same order
val SOLID_COLOR_OPTIONS = listOf(0xFF0F1115, 0xFF1B2238, 0xFF24302C, 0xFF3A2A2E)
fun Settings.toPreferences(): Preferences            // pure mapping (keys in SettingsKeys)
fun Preferences.toSettings(): Settings               // missing keys -> defaults
class SettingsRepository(private val store: DataStore<Preferences>) {
  val settings: Flow<Settings>
  suspend fun update(transform: (Settings) -> Settings)
}
```
- Clocks are stored as three key pairs `clock{0,1,2}_city` / `clock{0,1,2}_zone`; favourites as a `|`-joined string; enums by `name`.

- [ ] **Step 1: Write the failing tests** in `SettingsMappingTest`:
  - `defaults_roundTrip`: `Settings().toPreferences().toSettings() == Settings()`.
  - `custom_roundTrip`: a Settings with name "Ada", use24h true, unit C, scene NIGHT, interval NEVER, shuffle false, dim 0.6f, seconds true, favourites `listOf("a.b","c.d")`, clocks replaced by `WorldClock("Paris","Europe/Paris")` ×3 → equal after round-trip.
  - `emptyPreferences_yieldsDefaults`: `emptyPreferences().toSettings() == Settings()`.
  - `zoneOptions_has19_andContainsKathmandu`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SettingsMappingTest*'` Expected: compilation FAIL (symbols missing).
- [ ] **Step 3: Implement** `Settings.kt` and `SettingsRepository.kt` (`settings = store.data.map { it.toSettings() }`; `update` = `store.edit { prefs -> transform(prefs.toSettings()).toPreferences().asMap().forEach { (k, v) -> prefs[k as Preferences.Key<Any>] = v } }`). Add to `AppContainer(context)`: `val settings = SettingsRepository(context.preferencesDataStore("settings"))` — use `PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }`.
- [ ] **Step 4: Run** the tests. Expected: 4 PASS.
- [ ] **Step 5: Commit** `feat: settings model with DataStore persistence`.

---

### Task 3: Clock math (pure)

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/clock/ClockMath.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/clock/ClockMathTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class TimeText(val time: String, val ampm: String)          // ampm "" in 24h
data class ClockSnapshot(val city: String, val text: TimeText, val meta: String, val isDay: Boolean,
                         val hourDeg: Float, val minuteDeg: Float, val secondDeg: Float)
object ClockMath {
  fun timeText(t: ZonedDateTime, use24h: Boolean): TimeText     // 12h: "12:05"/"AM"; 24h: "00:05"/""
  fun dateLine(t: ZonedDateTime): String                         // "Thursday, October 29" (Locale.US)
  fun greeting(hour: Int): String                                // <5 night, <12 morning, <17 afternoon, else evening ("Good night"…)
  fun offsetMeta(home: ZonedDateTime, other: ZonedDateTime): String   // "Tomorrow  ·  +9h 45m" | "Today  ·  Same time" | "Yesterday  ·  −14h"
  fun isDay(hour: Int): Boolean                                  // 6..17
  fun dialAngles(t: ZonedDateTime): Triple<Float, Float, Float>  // hour ((h%12)+m/60)*30, minute (m+s/60)*6, second s*6
  fun snapshot(clock: WorldClock, home: ZonedDateTime, now: Instant, use24h: Boolean): ClockSnapshot
}
```
- `offsetMeta` algorithm (from the mockup JS): compare wall-clock minutes-of-epoch computed as `LocalDateTime.toEpochSecond(UTC)/60` for both; diff rounded to the nearest 15; sign `+`/`−` (U+2212); `hh + "h"` plus `" mm m"` when non-zero; day label from the difference of `LocalDate.toEpochDay()`; separator is two spaces, middle dot, two spaces.

- [ ] **Step 1: Write failing tests** (fixed instants, `ZoneId.of`):
  - `kathmandu_from_newYork_isQuarterHour`: home NY at 2026-10-29T21:00-04:00; other Kathmandu → `"Tomorrow  ·  +9h 45m"`.
  - `tokyo_from_newYork_isTomorrow`: same home; Tokyo → `"Tomorrow  ·  +13h"`.
  - `newYork_from_tokyo_isYesterday`: home Tokyo 2026-10-30T03:00+09:00; NY → `"Yesterday  ·  −13h"`.
  - `sameZone_isSameTime` → `"Today  ·  Same time"`.
  - `midnight_12h` → `TimeText("12:05","AM")`; `noon_12h` → `("12:05","PM")`; `midnight_24h` → `("00:05","")`.
  - `greeting_boundaries`: 4→"Good night", 5→"Good morning", 11→"Good morning", 12→"Good afternoon", 16→"Good afternoon", 17→"Good evening", 23→"Good evening".
  - `dateLine_format` → `"Thursday, October 29"`.
  - `dialAngles_at_3_30_15` → `(105f, 181.5f, 90f)`.
  - `isDay_bounds`: 5 false, 6 true, 17 true, 18 false.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ClockMathTest*'` Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement** `ClockMath.kt`.
- [ ] **Step 4: Run** tests. Expected: all PASS.
- [ ] **Step 5: Commit** `feat: pure clock math for hero, world clocks and greeting`.

---

### Task 4: Backgrounds, Welcome splash, navigation skeleton

**Files:**
- Create: `ui/background/Scenes.kt`, `ui/welcome/WelcomeScreen.kt`, `ui/components/GlassCard.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Produces:
```kotlin
@Composable fun Background(settings: Settings, dim: Float = 0f, scrim: Boolean = true, modifier: Modifier = Modifier)
// draws Scene (DUSK/NIGHT/FOREST) via Canvas or solid colour, then optional Home scrim
// (top 0–32 % black 38 %→0, bottom 52–100 % 0→black 62 %), then black overlay at `dim` alpha
fun scenePalette(scene: Scene): ScenePalette   // skyTop, skyMid, horizon, orb, orbR, m1..m4, stars — hex values exactly from Main.dc.html `scenes`
@Composable fun WelcomeScreen(name: String, accent: Color, settings: Settings)
@Composable fun GlassCard(modifier: Modifier = Modifier, radiusPx: Int = 28, content: @Composable BoxScope.() -> Unit)
sealed interface Screen { data object Welcome; data object Home; data object AllApps; data object Settings; data object Background }
```
- Scene geometry: the four mountain `path d=` strings and the orb centre (1240, 470) are copied verbatim from the mockup SVG; draw with `Path` parsed by `androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()` scaled by `size.width / 1920f`. Stars: the 12 circles (cx, cy, r, opacity) from the Night scene.
- Welcome: text "Welcome $name!!!" Outfit W300 120 spx, white; behind it a radial gradient halo (accent → transparent, radius 520 design px) with `infiniteTransition` alpha 0.35↔0.75 and scale 0.95↔1.05, 1600 ms `EaseInOutSine`, `RepeatMode.Reverse`; a shimmer: a 240-px-wide white 35 % linear-gradient band sweeping left→right across the text every 2400 ms using a `Brush.linearGradient` keyed on an animated offset in `drawWithContent`/`graphicsLayer` with `BlendMode.SrcAtop`.

- [ ] **Step 1: Scenes.kt** with `scenePalette` and `Background`.
- [ ] **Step 2: WelcomeScreen.kt** as specified.
- [ ] **Step 3: MainActivity**: `var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }`; collect `container.settings.settings` with `collectAsStateWithLifecycle(initialValue = Settings())`; `Crossfade(targetState = screen, animationSpec = tween(400))`; Welcome branch: `LaunchedEffect(Unit) { delay(1200); screen = Screen.Home }` (Task 6 replaces the fixed delay with the readiness gate + 4 s cap). Home branch for now: `Background(settings)` + the hero clock text from `ClockMath.timeText(ZonedDateTime.now(ZoneId.of(settings.homeZone)), settings.use24h)` refreshed each minute (`LaunchedEffect` loop delaying to the next minute boundary).
- [ ] **Step 4: Build + install + screenshot** `tools/tv.sh install && tools/tv.sh shot task4-welcome && sleep 2 && tools/tv.sh shot task4-home`. Expected: welcome shows "Welcome Nickesh!!!" with a blue glow over the Dusk scene; after ~1.2 s the hero time appears on the Dusk scene with the scrim. Open both PNGs with the Read tool and compare against the mockup colours (sky `#16213F`→`#4F4372`→`#E39470`).
- [ ] **Step 5: Commit** `feat: scene backgrounds, welcome splash with glow, screen navigation`.

---

### Task 5: Home screen layout (static data)

**Files:**
- Create: `ui/components/Icons.kt`, `ui/components/RoundButton.kt`, `ui/home/HomeScreen.kt`, `ui/home/TopBar.kt`, `ui/home/HeroClock.kt`, `ui/home/WeatherCard.kt`, `ui/home/WorldClockCard.kt`, `ui/home/AppsRow.kt`, `ui/home/HomeViewModel.kt`
- Modify: `MainActivity.kt` (Home branch → `HomeScreen`)

**Interfaces:**
- Produces:
```kotlin
enum class WeatherKind { SUN, PARTLY, CLOUD, RAIN }
data class DayForecast(val day: String, val kind: WeatherKind, val hi: String, val lo: String)   // "Fri", "64°"
data class WeatherUi(val city: String, val now: String, val condition: String, val hi: String, val lo: String, val kind: WeatherKind, val forecast: List<DayForecast>)
data class AppTile(val packageName: String, val label: String, val banner: ImageBitmap?, val icon: ImageBitmap?, val tone: Color)
data class HomeUiState(val greeting: String, val homeCity: String, val hero: TimeText, val dateLine: String,
                       val weather: WeatherUi?, val clocks: List<ClockSnapshot>, val apps: List<AppTile>, val accent: Color, val settings: Settings)
class HomeViewModel(container: AppContainer) : ViewModel() { val state: StateFlow<HomeUiState> }   // ticks: hero per minute, clocks per second
@Composable fun HomeScreen(state: HomeUiState, onOpenAllApps: () -> Unit, onOpenSettings: () -> Unit, onLaunch: (AppTile) -> Unit, onAction: (SystemAction) -> Unit)
enum class SystemAction { ASSIST, SEARCH, INPUTS, PROFILE }
object Icons { val Pin, Mic, Search, Inputs, Settings, Profile, Sun, Moon, Partly, Cloud, Rain, Grid, Back, Photo: ImageVector }  // paths traced from the mockup SVGs, 24×24 viewport, stroke-style
```
- Layout (design px, all via `dpx`/`spx`): padding 60 top / 96 sides / 56 bottom; header row 56 high; hero section `margin-top 44`; clocks grid `margin-top 40`, gap 24, card height 152, padding 22×28, dial 104; apps section pinned to the bottom (`Spacer(weight 1f)`), label row + tiles 172×104 radius 20, **gap 22** (not 26; the mockup overflows), labels 19 spx at opacity 0.7 (1.0 when focused). Weather card 580 wide, padding 28×32, gap 22; temp 96 spx W300; condition 28 W500; H/L 22; city 20; divider 1 px; forecast 4 columns. Hero 220 spx W200 line-height 0.9 letter-spacing −8 px; AM/PM 52 spx W300 85 % white; date 36 spx W300 88 % white.
- Placeholder weather until Task 7: `WeatherUi("Columbus, OH","58°","Partly cloudy","63°","47°",PARTLY, [Fri SUN 64/46, Sat CLOUD 59/44, Sun RAIN 55/41, Mon PARTLY 60/43])` (day names computed from home date). Placeholder apps until Task 6: the 8 names + tones from the mockup with `banner = null, icon = null` so tiles show the initial letter (40 spx W600) — keep this fallback path in `AppsRow` permanently.
- Focus: `AppsRow` tiles are tv-material `Surface(onClick)` with the Global focus treatment; the first tile requests initial focus (`FocusRequester` + `LaunchedEffect`). World-clock cards and the weather card are also `Surface(onClick = onOpenSettings)` focus stops; top-bar buttons are `RoundButton` (56 px, glass) and the assistant pill (56 high, 18/26 padding, fill white 18 %, border white 28 %, accent 36 px mic circle). Up/down between rows works through default focus search; no custom `focusProperties` unless the screenshot pass shows a dead end.

- [ ] **Step 1: Icons.kt** — one `ImageVector` per mockup SVG (copy `d` strings; `stroke = SolidColor(Color.White)`, `strokeLineWidth` as in the SVG, `strokeLineCap = Round`, `strokeLineJoin = Round`).
- [ ] **Step 2: Components** `RoundButton(icon, contentDescription, onClick)`; `GlassCard` from Task 4.
- [ ] **Step 3: Home composables** per the layout above; `WorldClockCard` draws the dial on a `Canvas`: face fill `isDay ? white 14 % : black 28 %`, ring white 28 % 1.5, four ticks white 60 % 2.5, hour hand 4.5 wide to r 23, minute 3 wide to r 34, second accent 1.5 from −8 to r 36, hub accent r 3.5 (SVG units of a 100-unit dial).
- [ ] **Step 4: HomeViewModel** combining `settings`, a per-second ticker (`flow { while(true){ emit(Instant.now()); delay(1000 - now.toEpochMilli() % 1000) } }`), placeholder weather/apps → `HomeUiState`. Hero text recomputed every tick is fine (cheap) — but `HeroClock` must only recompose when `hero` changes (it is a data class; Compose skips).
- [ ] **Step 5: Wire** `MainActivity` Home branch to `HomeScreen(state, …)` with `viewModel { HomeViewModel(container) }`; `onOpenAllApps/onOpenSettings` set `screen`; `onAction`/`onLaunch` are TODO no-ops until Tasks 6/8.
- [ ] **Step 6: Build, install, screenshot** `task5-home`, then press D-pad right ×2 and screenshot `task5-focus` (`adb shell input keyevent 22` twice). Expected: layout matches `Main.dc.html` region by region (top bar, hero, weather card right-aligned, three clock cards, apps row at the bottom); the focused tile is 1.1× with a white ring and blue glow. Fix spacing deviations before committing; open the PNGs with Read.
- [ ] **Step 7: Commit** `feat: home screen layout with hero, weather card, world clocks and apps row`.

---

### Task 6: Real apps row, All apps grid, Welcome readiness gate

**Files:**
- Create: `domain/apps/AppOrdering.kt`, `data/apps/AppsRepository.kt`, `ui/allapps/AllAppsScreen.kt`
- Modify: `TvLauncherApp.kt` (container gets `apps`), `HomeViewModel.kt`, `MainActivity.kt`, `AppsRow.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/apps/AppOrderingTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class InstalledApp(val packageName: String, val label: String)
object AppOrdering { fun order(installed: List<InstalledApp>, favourites: List<String>, max: Int = 8): List<InstalledApp> }
// favourites that are installed, in favourites order; then the rest sorted by label (case-insensitive); take(max)
class AppsRepository(private val context: Context) {
  val apps: StateFlow<List<AppTile>>          // ALL leanback apps, label-sorted, with banners decoded at 172x104*scale px, tone = TONES[index % 8]
  val ready: StateFlow<Boolean>               // true after the first successful load
  fun launch(packageName: String): Boolean    // getLeanbackLaunchIntentForPackage ?: getLaunchIntentForPackage; returns false if neither
  fun refresh()                               // re-query; called by the package-change BroadcastReceiver (ACTION_PACKAGE_ADDED/REMOVED/CHANGED/REPLACED, data scheme "package")
}
val TONES = listOf(0xFF3B2F52, 0xFF4A2B36, 0xFF233A4D, 0xFF24315A, 0xFF1E3F36, 0xFF423823, 0xFF2C3D4A, 0xFF343844).map(::Color)
@Composable fun AllAppsScreen(apps: List<AppTile>, onLaunch: (AppTile) -> Unit, onBack: () -> Unit)   // 6-column LazyVerticalGrid of the same tiles, header "All apps" 48 spx + Back RoundButton; BackHandler → onBack
```
- Query: `pm.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LEANBACK_LAUNCHER), 0)`, excluding our own package. Banner: `activityInfo.loadBanner(pm) ?: applicationInfo.loadBanner(pm)` → `Drawable.toBitmap(w, h)`; icon: `loadIcon(pm)` → 64×64·scale bitmap. Decode on `Dispatchers.IO`.

- [ ] **Step 1: Write failing tests** `AppOrderingTest`: `favouritesFirst_thenAlpha`, `missingFavourites_skipped`, `max8_enforced` (12 apps → 8), `empty_installed_returnsEmpty`, `threeApps_returnsThree`, `alpha_isCaseInsensitive` ("youtube" before "Zattoo").
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** `AppOrdering`. **Step 4: Run** → PASS.
- [ ] **Step 5: AppsRepository** + receiver registration in `TvLauncherApp.onCreate` (`ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`), container gets `apps = AppsRepository(this)`.
- [ ] **Step 6: HomeViewModel** replaces placeholder apps with `AppOrdering.order(repo.apps.value → InstalledApp, settings.favourites)` mapped back to tiles, plus a trailing synthetic `AppTile("", "All apps", null, null, Color(0x1FFFFFFF))` that `AppsRow` renders with the Grid icon and routes to `onOpenAllApps`. `onLaunch` → `container.apps.launch(pkg)`; on `false` show a `Toast` "Can't open ${label}".
- [ ] **Step 7: Welcome gate** in `MainActivity`: `LaunchedEffect(Unit) { val start = now; withTimeoutOrNull(4000) { container.apps.ready.first { it } }; delay(max(0, 1200 - elapsed)); screen = Screen.Home }`; `Welcome` is shown only when `savedInstanceState == null` (cold start) — store `shownWelcome` in a `rememberSaveable`.
- [ ] **Step 8: AllAppsScreen** + `Screen.AllApps` branch; `BackHandler` in Home is not needed (system back exits the app in v1).
- [ ] **Step 9: Install, verify**: screenshot `task6-home` (real banners for YouTube/Netflix/… visible, Stream Everything in the row, "All apps" last), press OK on the first tile → that app opens (`adb shell dumpsys activity activities | grep -m1 topResumedActivity`), Back → launcher; open All apps → screenshot `task6-allapps`. Review Focus 4 (app removed while focused) is covered by the `empty_installed`/`threeApps` tests plus a code read confirming the receiver calls `refresh()`; do not uninstall apps on the user's TV. In `AppsRow`, the tiles are keyed by package name so Compose drops a removed tile and focus falls to a neighbour.
- [ ] **Step 10: Commit** `feat: real leanback apps row with banners, All apps grid, welcome readiness gate`.

---

### Task 7: Weather (Open-Meteo) with cache

**Files:**
- Create: `domain/weather/WeatherCode.kt`, `data/weather/OpenMeteo.kt`, `data/weather/WeatherRepository.kt`
- Modify: `TvLauncherApp.kt` (container gets `weather`), `HomeViewModel.kt`, `SettingsRepository` keys (weather cache)
- Test: `domain/weather/WeatherCodeTest.kt`, `data/weather/WeatherRepositoryTest.kt`

**Interfaces:**
- Produces:
```kotlin
object WeatherCode { fun kind(code: Int): WeatherKind; fun label(code: Int): String }
// 0 → SUN "Clear sky"; 1 → SUN "Mainly clear"; 2 → PARTLY "Partly cloudy"; 3 → CLOUD "Overcast"; 45,48 → CLOUD "Fog";
// 51..57 → RAIN "Drizzle"; 61..67 → RAIN "Rain"; 71..77 → RAIN "Snow"; 80..82 → RAIN "Rain showers"; 85,86 → RAIN "Snow showers"; 95..99 → RAIN "Thunderstorm"; else CLOUD "Cloudy"
@Serializable data class GeoResult(val name: String, val latitude: Double, val longitude: Double, val timezone: String)
@Serializable data class Forecast(val current: Current, val daily: Daily) { @Serializable data class Current(val temperature_2m: Double, val weather_code: Int); @Serializable data class Daily(val time: List<String>, val weather_code: List<Int>, val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>) }
interface WeatherApi { suspend fun geocode(city: String): GeoResult?; suspend fun forecast(lat: Double, lon: Double, unit: TempUnit, zone: String): Forecast }
class OpenMeteoClient(private val http: OkHttpClient, private val json: Json) : WeatherApi
// geocode: GET https://geocoding-api.open-meteo.com/v1/search?name=<urlenc city>&count=1 → results[0]
// forecast: GET https://api.open-meteo.com/v1/forecast?latitude=&longitude=&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&temperature_unit=fahrenheit|celsius&timezone=<urlenc zone>&forecast_days=5
data class WeatherSnapshot(val city: String, val unit: TempUnit, val forecast: Forecast, val fetchedAtEpochMs: Long)
class WeatherRepository(private val api: WeatherApi, private val cache: WeatherCache, private val clock: () -> Long = System::currentTimeMillis) {
  suspend fun load(city: String, unit: TempUnit, zone: String): WeatherSnapshot?   // fresh if cache older than 30 min or city/unit changed; on failure return cached (even stale) else null
  fun toUi(s: WeatherSnapshot, homeDate: LocalDate): WeatherUi                     // "58°" rounding, forecast = daily[1..4], day names Locale.US EEE
}
interface WeatherCache { suspend fun read(): WeatherSnapshot?; suspend fun write(s: WeatherSnapshot) }   // DataStore-backed impl stores JSON string + fetchedAt
```
- [ ] **Step 1: Failing tests** `WeatherCodeTest` (one assertion per range above, plus `unknownCode_isCloudy`) and `WeatherRepositoryTest` with a fake `WeatherApi` and in-memory cache:
  - `freshFetch_whenNoCache` (api called once, snapshot written).
  - `usesCache_whenYoungerThan30min` (api not called).
  - `refetch_whenCityChanged`.
  - `apiFailure_returnsStaleCache` (api throws `IOException`, cache 2 h old → returned).
  - `apiFailure_noCache_returnsNull`.
  - `toUi_roundsAndTakesNext4Days` (62.4 → "62°"; forecast size 4; first day label is tomorrow's EEE).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** all files; container gets `weather = WeatherRepository(OpenMeteoClient(OkHttpClient.Builder().callTimeout(10, SECONDS).build(), Json { ignoreUnknownKeys = true }), DataStoreWeatherCache(settingsStore))`. **Step 4: Run** → PASS.
- [ ] **Step 5: HomeViewModel**: on start and every 30 min while the VM lives (`while (true) { load(); delay(30.minutes) }`), and whenever `weatherCity`/`unit`/`homeZone` change (`distinctUntilChanged` on that triple), call `load` on `Dispatchers.IO`; `weather = snapshot?.let(::toUi)`; `WeatherCard` renders the placeholder "--°" with the city and no forecast rows when `null`.
- [ ] **Step 6: Install, screenshot** `task7-weather` (real Columbus temperature; compare with `curl` of the same URL). Then `adb shell svc wifi disable`, relaunch, screenshot `task7-offline` (cached values still shown), `adb shell svc wifi enable`. If `svc wifi` is not permitted on the Chromecast, skip the offline step and rely on the unit tests; say so in the task report.
- [ ] **Step 7: Commit** `feat: live weather from Open-Meteo with 30-minute cache`.

---

### Task 8: System actions (assistant, search, inputs, settings, profile)

**Files:**
- Create: `data/system/SystemIntents.kt`
- Modify: `MainActivity.kt` (`onAction`), `TopBar.kt` (hide Inputs when unavailable)

**Interfaces:**
- Produces:
```kotlin
class SystemIntents(private val context: Context) {
  fun candidates(action: SystemAction): List<Intent>      // ordered fallbacks, see below
  fun isAvailable(action: SystemAction): Boolean           // any candidate resolves
  fun perform(action: SystemAction): Boolean               // start the first that resolves; false if none
}
// ASSIST:  Intent(Intent.ACTION_ASSIST), Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE), Intent(Intent.ACTION_VOICE_COMMAND)
// SEARCH:  Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH), Intent(Intent.ACTION_SEARCH), then ASSIST candidates
// INPUTS:  Intent("com.android.tv.settings.INPUTS") (resolves on TVs with HDMI inputs), fallback none. TopBar hides the button when `isAvailable(INPUTS)` is false. Builder call: on the Chromecast (no HDMI inputs) the Inputs button is hidden.
// PROFILE: Intent(Settings.ACTION_SYNC_SETTINGS), Intent(Settings.ACTION_SETTINGS)
```
- [ ] **Step 1: Implement** `SystemIntents`; `perform` adds `FLAG_ACTIVITY_NEW_TASK`, catches `ActivityNotFoundException`/`SecurityException` → next candidate; a failure of all shows a Toast "Not available on this TV".
- [ ] **Step 2: Wire** `onAction` in `MainActivity`; `TopBar` takes `showInputs: Boolean`.
- [ ] **Step 3: Verify on device**: focus the "Ask Gemini" pill, press OK → the Google Assistant / voice UI appears (screenshot `task8-assist`); Search → search UI or assistant; Profile → system settings. Record in the report which candidate resolved for each.
- [ ] **Step 4: Commit** `feat: top-bar system actions with resolvable fallbacks`.

---

### Task 9: Screensaver (DreamService + flip clock)

**Files:**
- Create: `ui/screensaver/FlipClock.kt`, `ui/screensaver/ScreensaverContent.kt`, `ScreensaverService.kt`, `app/src/main/res/xml/screensaver.xml`
- Modify: `AndroidManifest.xml`, `tools/tv.sh` (`dream` and `dream-set` sub-commands)
- Test: `ui/screensaver/FlipDigitsTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class FlipDigits(val groups: List<String>, val ampm: String)   // ["09","41"] or ["09","41","07"]; ampm "" in 24h
object FlipDigitsCalc { fun from(t: ZonedDateTime, use24h: Boolean, showSeconds: Boolean): FlipDigits }
@Composable fun FlipDigit(value: Char, width: Dp, height: Dp, fontSize: TextUnit)   // animates when `value` changes: top half rotationX 0→−90 (300 ms EaseIn, pivot bottom), then bottom half 90→0 (300 ms EaseOut, pivot top); cameraDistance 12f * density; colours top #23262E, bottom #1C1F26, digit #EEF1F6, hinge 4 px #05070D, side notches 10×18
@Composable fun FlipClock(digits: FlipDigits, showSeconds: Boolean)   // card 250×360 fs 280 (200×290 fs 220 with seconds), gap 16 within a group, 28 between groups, colon = two 26 px dots 56 apart; AM/PM 44 spx beside, aligned to the bottom, margin-bottom 18
@Composable fun ScreensaverContent(settings: Settings, weather: WeatherUi?, clocks: List<ClockSnapshot>, now: ZonedDateTime)
// Background(settings, dim = settings.screensaverDim, scrim = false); centre column gap 40: FlipClock, date 40 spx W300 90 %, pill bar (padding 20×36, radius 40, white 8 % / border 14 %): weather icon 40 + temp 30 + city 24, then 3 clocks (city 24 at 75 %, time 30, ampm 20) separated by 1×36 dividers, gap 40
class ScreensaverService : DreamService()   // isInteractive=false, isFullscreen=true; hosts a ComposeView with its own LifecycleOwner + SavedStateRegistryOwner (set via setViewTreeLifecycleOwner / setViewTreeSavedStateRegistryOwner / setViewTreeViewModelStoreOwner on the view); collects settings + weather cache (no network fetch) + per-second ticker; shifts the content by (±24, ±24) design px every 60 s (burn-in guard)
```
- Manifest: `<service android:name=".ScreensaverService" android:exported="true" android:permission="android.permission.BIND_DREAM_SERVICE"><intent-filter><action android:name="android.service.dreams.DreamService"/><category android:name="android.intent.category.DEFAULT"/></intent-filter><meta-data android:name="android.service.dream" android:resource="@xml/screensaver"/></service>`; `screensaver.xml`: `<dream xmlns:android="http://schemas.android.com/apk/res/android"/>`.
- `tools/tv.sh dream-set`: `adb shell settings put secure screensaver_components com.nickesh.tvlauncher/.ScreensaverService; adb shell settings put secure screensaver_enabled 1; adb shell settings put secure screensaver_activate_on_sleep 1`. `tools/tv.sh dream`: `adb shell cmd dreams start` (fallback `adb shell am start -n com.android.systemui/.Somnambulator`); `dream-stop`: `adb shell cmd dreams stop`.

- [ ] **Step 1: Failing tests** `FlipDigitsTest`: `twelveHour_9_41` → groups ["09","41"], ampm "AM"; `twentyFourHour_midnight` → ["00","05"], ""; `withSeconds` → 3 groups; `noon_12h` → ["12","05"], "PM".
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** `FlipDigitsCalc`. **Step 4: Run** → PASS.
- [ ] **Step 5: FlipDigit/FlipClock/ScreensaverContent/ScreensaverService** + manifest + tv.sh.
- [ ] **Step 6: Verify on device**: `tools/tv.sh install && tools/tv.sh dream-set && tools/tv.sh dream && sleep 2 && tools/tv.sh shot task9-dream`. Expected: the flip clock over the dimmed Dusk scene with the pill bar. Wait for a minute change and take two screenshots 150 ms apart during it (`for i in 1 2 3; do shot task9-flip$i; sleep 0.15; done` started 1 s before the boundary) to see the fold mid-animation. `tools/tv.sh dream-stop`. Also confirm `adb shell settings get secure screensaver_components` prints our component.
- [ ] **Step 7: Commit** `feat: flip-clock screensaver as a DreamService with adb selection script`.

---

### Task 10: Background and Settings screens, scene shuffle

**Files:**
- Create: `ui/settings/BackgroundScreen.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `MainActivity.kt` (branches, `BackHandler`s), `HomeViewModel.kt` (shuffle rotation), `Settings.kt` (none expected)

**Interfaces:**
- Produces:
```kotlin
@Composable fun BackgroundScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onBack: () -> Unit)
@Composable fun SettingsScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onOpenBackground: () -> Unit, onBack: () -> Unit)
```
- BackgroundScreen per `Background.dc.html`: header (Back RoundButton + title 48 W400 + subtitle 22); left column 520 wide: SOURCE label (22 W500 80 % letter-spacing 1), three source rows (96 high, radius 22, 3 px ring white when selected, fill 16 % selected / 6 % otherwise, radio dot) — **Google Photos row is rendered with sub-text "Coming soon" at 50 % opacity and is not focusable (`focusProperties { canFocus = false }`)**; CHANGE PHOTO chips (56 high, radius 28; selected = white fill, `#0D1020` text); Shuffle toggle row (80 high, switch 72×40 knob 32, on colour `#7FE0C2`). Right pane: for SCENES the three 16:9 scene previews (radius 20, 3 px ring on the selected one) with names (24 spx) — previews are `Background` composables clipped, not gradients; for COLOR the four 120 px circles; the info line at the bottom. Selecting a scene/colour/source/interval/shuffle calls `onUpdate` immediately (no Save button).
- SettingsScreen: tv-material `ListItem`s in a 820-design-px-wide column over `Background(settings, dim = 0.72f)`: Name (opens a tv-material text field inline; the system keyboard appears), Home city (text) + Home time zone (cycles through `ZONE_OPTIONS` with left/right, shows the id), Clock 1–3 city + zone (same pattern), Weather city (text), Units (F/C toggle), 24-hour clock (switch), Accent (4 swatches), Screensaver dim (slider 0–0.8 step 0.05 via left/right), Show seconds (switch), Background… (navigates). Each change calls `onUpdate`.
- Shuffle: in `HomeViewModel`, when `backgroundSource == SCENES && shuffle && changeInterval.minutes != null`, a loop advances `scene` to the next enum value every `minutes` (persisted through `settings.update`), so Home and the screensaver follow.

- [ ] **Step 1: BackgroundScreen** + `Screen.Background` branch with `BackHandler { screen = Screen.Settings }`.
- [ ] **Step 2: SettingsScreen** + `Screen.Settings` branch with `BackHandler { screen = Screen.Home }`; Home's Settings button → `Screen.Settings`.
- [ ] **Step 3: Shuffle loop** in `HomeViewModel`.
- [ ] **Step 4: Verify on device**: screenshots `task10-background` (Scenes selected) and `task10-settings`; change scene to Night via D-pad → Home background changes (`task10-night`); set 24-hour → hero shows "22:14" with no AM/PM; set name "Nick" → relaunch (`adb shell am force-stop com.nickesh.tvlauncher` then start) → Welcome says "Welcome Nick!!!"; reset name to Nickesh.
- [ ] **Step 5: Commit** `feat: background and settings screens with persisted choices`.

---

### Task 11: Polish pass, README, GitHub

**Files:**
- Modify: whatever the screenshot comparison flags; `README.md`
- Create: GitHub repo `maharjannickesh/tvlauncher` (private)

- [ ] **Step 1: Full screenshot pass** of all five screens (`welcome`, `home`, `home-focus`, `allapps`, `background`, `settings`, `dream`) into `shots/final-*.png`; open each with Read next to the matching mockup and list deviations > 4 design px or any colour/weight mismatch; fix them.
- [ ] **Step 2: Cold-start timing**: `adb shell am force-stop com.nickesh.tvlauncher; adb shell am start -W -n com.nickesh.tvlauncher/.MainActivity` → note `TotalTime`; then measure welcome→home by polling screenshots every 250 ms until the hero appears. Expected ≤ 2 s on Wi-Fi; record the numbers in README.
- [ ] **Step 3: Tests green**: `./gradlew :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL, 0 failures. `./gradlew lintDebug` Expected: no errors (warnings allowed; list them).
- [ ] **Step 4: README**: what it is, dev loop (connect/install/shot), screensaver selection command, settings list, v2 backlog (from the spec).
- [ ] **Step 5: GitHub**: `gh repo create maharjannickesh/tvlauncher --private --source=. --remote=origin --push` (confirm `gh auth status` first). Expected: `git log origin/main --oneline -1` equals local HEAD.
- [ ] **Step 6: Commit** any README/polish changes before the push: `chore: polish pass, README, repo setup`.
