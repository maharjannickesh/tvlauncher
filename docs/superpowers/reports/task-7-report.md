# Task 7 report: Weather (Open-Meteo) with cache

Implemented: WeatherCode, OpenMeteoClient/WeatherApi/GeoResult/Forecast, WeatherRepository (+ @Serializable WeatherSnapshot, WeatherCache, DataStoreWeatherCache with `weather_json` and per-city geocode keys), container.weather, HomeViewModel weather loop (IO, collectLatest on (city, unit, zone), 30-min delay, exposed as `weather` StateFlow), WeatherCard now takes `WeatherUi?` + city (null -> "--°", city, no forecast).
TDD: RED = compile failure (unresolved WeatherApi etc.) from --tests '*WeatherCodeTest*' '*WeatherRepositoryTest*'; GREEN after impl (one test-authoring slip: forecast index in toUi test, fixed). Full `:app:testDebugUnitTest` BUILD SUCCESSFUL.
Device: shots/task7-weather.png shows 60° / H 76° L 47° / Clear sky; curl at 23:03 EDT gave 60.2, max 76.0, min 46.6, code 0/1; forecast Fri 67/48, Sat 60/49, Sun 59/53, Mon 55/53 match.
Offline step skipped: `svc wifi disable/enable` returned silently with the adb-over-wifi link intact (a no-op), so offline could not be proven; covered by apiFailure_returnsStaleCache unit test.
Concerns: stale cached data is shown even for a changed city/unit on failure (per spec); weather loop re-runs per ViewModel instance; geocode cache never expires (fine for city coordinates). Extra test added: refetch_whenUnitChanged.

## Fix report
- cache.read() moved inside a try (exceptions -> null cache). toUi returns WeatherUi? (null if today or any of next 4 days missing; getOrNull everywhere, LocalDate.parse guarded). HomeViewModel loop iteration wrapped in try/catch (CancellationException rethrown, includes ZoneId.of); retry after 90 s when load/toUi gives null, else 30 min.
- Tests: toUi_shortDaily_returnsNull (lengths 0 and 1), freshFetch asserts geocodeCalls == 1. `./gradlew :app:testDebugUnitTest` -> BUILD SUCCESSFUL.
- Device: NOT verified. After the fix the TV was unreachable (adb devices empty, connect refused, tv.sh "TV not found"); likely asleep / port changed (possibly after my earlier `svc wifi disable/enable` attempt). No task7-fix-weather.png.
