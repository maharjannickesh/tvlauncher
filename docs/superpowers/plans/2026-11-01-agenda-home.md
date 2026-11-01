# Agenda Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put three new blocks on the TV Launcher Home (Up next from Google Calendar iCal links, To do from Todoist with tick-from-remote, Continue watching from Android's Watch Next) and a laptop-free Connect flow (QR → LAN page served by the TV) to set them up.

**Architecture:** New `data/calendar`, `data/todoist`, `data/watchnext`, `data/connect` packages feed a new `AgendaViewModel` whose `StateFlow<AgendaUi>` is passed to `HomeScreen` beside the existing `HomeUiState`; pure decision code (selection, ordering, formatting, tick state machine, connect rules) lives in `domain/agenda` and `domain/connect` and is unit-tested on the JVM; the Home layout is rearranged to the approved option G.

**Tech Stack:** Kotlin 2.2, Compose for TV (`androidx.tv:tv-material` 1.1.0), DataStore preferences, OkHttp 4.12, kotlinx.serialization 1.9, coroutines 1.10. New: `net.sf.biweekly:biweekly:0.6.8` (ICS + RRULE), `io.coil-kt.coil3:coil-compose:3.3.0` + `io.coil-kt.coil3:coil-network-okhttp:3.3.0` (posters), `com.google.zxing:core:3.5.3` (QR), `org.nanohttpd:nanohttpd:2.3.1` (LAN page). JUnit 4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-11-01-agenda-home-design.md` (layout reference: `…/2026-11-01-agenda-home-mockup.html`, option G).

## Global Constraints

- Branch `v2-agenda` off `v1` (`git worktree add ../tvlauncher-agenda -b v2-agenda v1`; copy `local.properties` into the worktree). `JAVA_HOME` = JDK 21 (`~/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home`).
- Every task ends with `./gradlew -q :app:testDebugUnitTest :app:lintDebug` green (124 tests today, lint 0 errors). Tests are JVM-only (`app/src/test`, package mirrors main); no `androidTest`.
- Design pixels: `Int.dpx()` / `Int.spx()` on a 1920×1080 space (`ui/theme/Theme.kt`). Fonts: `Outfit`. Glass cards: `RoundedCornerShape(28.dpx())`, fill `Color(0x1AFFFFFF)`, border `Color(0x29FFFFFF)`, focused border 3 dpx white (copy `WorldClockCard.kt`).
- The Todoist token and calendar links never appear in a log line or a toast; on screen the token shows as `••••` + last 4.
- Home must render from cache instantly; no block ever blocks on the network. Keep last good data on failure.
- Copy pinned by the spec: headers `UP NEXT` / `TO DO` / `CONTINUE WATCHING`; sub-labels `Google Calendar`, `Todoist · N open`; empty states `Connect in Settings`, `Can't reach calendar`, `Nothing scheduled`, `Can't reach Todoist`, `All done`, `Nothing in progress`, `Continue watching is off · Settings`; permission tile `Show what you're watching · Press Select to allow`; failure toast `Couldn't update Todoist`; phone-page errors `That link didn't return a calendar`, `Up to 4 calendars`.
- Calendar fetch every 15 min; Todoist every 5 min; both also on Home resume when older than 5 min; Todoist again right after a tick. Undo window 5 s. Up to 4 calendar links.
- Commit after every task with a `feat:`/`fix:`/`docs:` message; never `git add .`; stage named files.

## Review Focus

1. **A multi-day all-day event spanning today** (`DTSTART;VALUE=DATE:20261030`, `DTEND;VALUE=DATE:20261103`): a person expects it in Up next as `All day` while today is inside the range, not hidden because it "started" days ago. Pinned in Task 3.
2. **A token or link pasted with a trailing newline or spaces from the phone**: it must be trimmed before validation, or every paste fails with a confusing error. Pinned in Task 10 (`ConnectRules`).
3. **A `webcal://` calendar link** (what some calendar apps copy): it must be normalised to `https://` and accepted. Pinned in Task 1.
4. **A Watch Next row with no duration, or a position past the duration**: the progress bar must be hidden or clamped, never a crash or a 300% bar. Pinned in Task 7.
5. **A VEVENT with no DTEND and no DURATION**: Google emits these for zero-length events; the parser must treat end = start (timed) or start + 1 day (all-day), never throw and drop the whole feed. Pinned in Task 2.

---

### Task 1: Settings fields, keys and connect rules

**Files:**
- Modify: `app/src/main/java/com/nickesh/tvlauncher/data/settings/Settings.kt`
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/connect/ConnectRules.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/data/settings/SettingsMappingTest.kt` (extend), `app/src/test/java/com/nickesh/tvlauncher/domain/connect/ConnectRulesTest.kt`, `app/src/test/java/com/nickesh/tvlauncher/domain/settings/SettingsDraftTest.kt` (extend)

**Interfaces:**
- Produces: `Settings.calendarUrls: List<String> = emptyList()`, `Settings.todoistToken: String? = null`, `Settings.continueWatchingDismissed: Boolean = false`; keys `calendar_urls` (joined with `|`), `todoist_token`, `continue_watching_dismissed` (token key is optional: removed when null, add to `SettingsKeys.optionalKeys`).
- Produces: `object ConnectRules { const val MAX_CALENDARS = 4; fun normaliseCalendarUrl(raw: String): String?; fun addCalendar(current: List<String>, raw: String): Result<List<String>>; fun removeCalendar(current: List<String>, url: String): List<String>; fun normaliseToken(raw: String): String?; fun maskToken(token: String): String }`.
  - `normaliseCalendarUrl`: trim; `webcal://` → `https://`; must start with `https://` else null; empty → null.
  - `addCalendar`: blank input → `Result.success(current)` (unchanged); not `https://` after normalising → `Result.failure(IllegalArgumentException("That link didn't return a calendar"))`; already present → success(current); a fifth link → failure `"Up to 4 calendars"`; else success(current + url).
  - `normaliseToken`: trim; empty → null. `maskToken("abcdef1a2b")` = `"••••1a2b"` (whole token when shorter than 4).

- [ ] **Step 1: Write the failing tests**

```kotlin
// SettingsMappingTest additions
@Test fun connections_roundTrip() {
    val s = Settings(calendarUrls = listOf("https://a/basic.ics", "https://b/basic.ics"), todoistToken = "tok1a2b", continueWatchingDismissed = true)
    assertEquals(s, s.toPreferences().toSettings())
}
@Test fun applySettings_removesClearedToken() {
    val prefs = mutablePreferencesOf(); prefs.applySettings(Settings(todoistToken = "x"))
    prefs.applySettings(Settings(todoistToken = null))
    assertNull(prefs[SettingsKeys.todoistToken])
}
// ConnectRulesTest
@Test fun webcal_isNormalisedToHttps() = assertEquals("https://x/basic.ics", ConnectRules.normaliseCalendarUrl(" webcal://x/basic.ics\n"))
@Test fun http_isRejected() = assertNull(ConnectRules.normaliseCalendarUrl("http://x/basic.ics"))
@Test fun add_empty_leavesUnchanged() = assertEquals(listOf("https://a"), ConnectRules.addCalendar(listOf("https://a"), "  ").getOrThrow())
@Test fun add_duplicate_leavesUnchanged()
@Test fun add_fifth_fails_withCopy() { val r = ConnectRules.addCalendar(List(4) { "https://c$it" }, "https://new"); assertEquals("Up to 4 calendars", r.exceptionOrNull()!!.message) }
@Test fun add_appends() = assertEquals(listOf("https://a", "https://b"), ConnectRules.addCalendar(listOf("https://a"), "https://b").getOrThrow())
@Test fun token_isTrimmed_andEmptyIsNull() { assertEquals("abc", ConnectRules.normaliseToken(" abc\n")); assertNull(ConnectRules.normaliseToken("  ")) }
@Test fun maskToken_showsLastFour() = assertEquals("••••1a2b", ConnectRules.maskToken("abcdef1a2b"))
// SettingsDraftTest addition
@Test fun merge_keepsConnections() { val saved = Settings(calendarUrls = listOf("https://a"), todoistToken = "t"); assertEquals(saved.calendarUrls, SettingsDraftRules.merge(saved, Settings()).calendarUrls); assertEquals("t", SettingsDraftRules.merge(saved, Settings()).todoistToken) }
```

- [ ] **Step 2: Run them, expect compile failures** — `./gradlew -q :app:testDebugUnitTest --tests '*ConnectRulesTest*' --tests '*SettingsMappingTest*' --tests '*SettingsDraftTest*'`
- [ ] **Step 3: Add the three fields, keys, `toPreferences`/`toSettings`/`optionalKeys` handling, and `ConnectRules`**
- [ ] **Step 4: Run the three test classes, expect PASS; then the full suite + lint**
- [ ] **Step 5: Commit** `feat: connection settings and connect rules`

---

### Task 2: ICS parsing with recurrence expansion

**Files:**
- Modify: `gradle/libs.versions.toml` (+ `biweekly = "0.6.8"`, library `biweekly = { group = "net.sf.biweekly", name = "biweekly", version.ref = "biweekly" }`), `app/build.gradle.kts` (`implementation(libs.biweekly)`)
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/calendar/CalendarEvent.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/calendar/IcsParser.kt`, `app/src/main/java/com/nickesh/tvlauncher/domain/connect/CalendarOwner.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/data/calendar/IcsParserTest.kt`, fixtures under `app/src/test/resources/ics/` (hand-written in Google's export shape: `VCALENDAR` with `X-WR-CALNAME`, `VTIMEZONE America/New_York`, events as listed below)

**Interfaces:**
- Produces: `@Serializable data class CalendarEvent(val uid: String, val title: String, val startMs: Long, val endMs: Long, val allDay: Boolean, val calendar: String)`.
- Produces: `object IcsParser { fun calendarName(text: String): String?; fun parse(text: String, windowStartMs: Long, windowEndMs: Long, zone: ZoneId, ownerEmail: String?, calendarName: String): List<CalendarEvent> }` — one `CalendarEvent` per occurrence whose `[startMs, endMs)` overlaps the window (recurrences expanded with biweekly's `RecurrenceRule` date iterator, `EXDATE` honoured, `RECURRENCE-ID` overrides replace the master's occurrence); drops `STATUS:CANCELLED`; drops events where the `ATTENDEE` with `mailto:<ownerEmail>` (case-insensitive) has `PARTSTAT=DECLINED`; all-day events use `zone` midnight; missing `DTEND`/`DURATION` → end = start (timed) or start + 1 day (all-day); a malformed VEVENT is skipped, never fails the feed.
- Produces: `object CalendarOwner { fun emailFromUrl(url: String): String? }` — the URL-decoded path segment after `/ical/` for `calendar.google.com` links; null otherwise.

- [ ] **Step 1: Write the fixtures and failing tests** (window = 2026-11-01T00:00 to 2026-12-31 New York unless stated)

```kotlin
@Test fun timedEvent_isParsed()                      // plain.ics: "Dentist" 2026-11-03 15:30–16:00 NY → startMs/endMs exact, allDay=false, calendar="Nickesh"
@Test fun allDay_usesZoneMidnight()                  // allday.ics: DTSTART;VALUE=DATE:20261105 → start = 2026-11-05T00:00 NY, end = +1 day, allDay=true
@Test fun weeklyRecurrence_expands_andHonoursExdate() // weekly.ics: every Monday from 2026-10-05, EXDATE 2026-11-09 → occurrences 2026-11-02, 11-16, 11-23, … in window; none on 11-09
@Test fun movedInstance_replacesMasterOccurrence()   // moved.ics: RECURRENCE-ID 2026-11-16 moved to 2026-11-17 10:00 → no 11-16 occurrence, one on 11-17 10:00
@Test fun cancelled_isDropped()
@Test fun declinedByOwner_isDropped_othersKept()      // declined.ics: two events; owner declined one, a colleague declined the other → only the second remains
@Test fun noOwner_keepsDeclined()                     // same fixture, ownerEmail = null → both remain
@Test fun dstCrossover_keepsWallClock()               // dst.ics: weekly 09:00 NY from 2026-10-26 → 2026-11-02 occurrence is 09:00 NY (14:00Z), 2026-11-09 is 09:00 NY (14:00Z) — both after the Nov 1 change; and 2026-10-26 is 13:00Z
@Test fun untilAndCount_stopExpansion()               // until.ics: COUNT=3 → exactly 3; UNTIL=20261115 → nothing after
@Test fun missingDtend_timed_isZeroLength()           // Review Focus 5
@Test fun missingDtend_allDay_isOneDay()
@Test fun malformedEvent_isSkipped_feedStillParses()  // malformed.ics: one VEVENT with DTSTART "garbage", one good → 1 event
@Test fun calendarName_fromXWrCalname()
@Test fun ownerEmail_fromGooglePrivateUrl() = assertEquals("nick@gmail.com", CalendarOwner.emailFromUrl("https://calendar.google.com/calendar/ical/nick%40gmail.com/private-abc/basic.ics"))
@Test fun ownerEmail_nullForGroupAndForeign()        // "…/ical/abc%40group.calendar.google.com/…" → null; "https://example.com/x.ics" → null
```

- [ ] **Step 2: Run, expect failure** — `./gradlew -q :app:testDebugUnitTest --tests '*IcsParserTest*'`
- [ ] **Step 3: Add the dependency, `CalendarEvent`, `IcsParser` (biweekly `Biweekly.parse(text).first()`, iterate `VEvent`s, expand with `event.getDateIterator(timeZone)` bounded by the window, apply overrides keyed by `RECURRENCE-ID`), and `CalendarOwner`**
- [ ] **Step 4: Run the class, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: ICS parser with recurrence expansion`

---

### Task 3: Up next selection and time column

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/agenda/UpNext.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/agenda/UpNextTest.kt`

**Interfaces:**
- Produces: `data class UpNextRow(val time: String, val title: String, val suffix: String?)`; `object UpNext { fun select(events: List<CalendarEvent>, nowMs: Long, limit: Int = 3): List<CalendarEvent>; fun timeColumn(e: CalendarEvent, nowMs: Long, zone: ZoneId, use24h: Boolean): String; fun suffix(e: CalendarEvent, nowMs: Long): String?; fun rows(events: List<CalendarEvent>, nowMs: Long, zone: ZoneId, use24h: Boolean): List<UpNextRow> }`.
  - `select`: `endMs > nowMs`, sorted by `startMs` then title, first `limit`.
  - `timeColumn`: same local day → `3:30 PM` (12 h, no leading zero) / `15:30`; tomorrow → `Tomorrow 10:00 AM`; within the next 6 days → `Sat 10:00 AM`; later → `12 Nov`; all-day → `All day` (today) / `Tomorrow · All day` / `Sat · All day` / `12 Nov · All day`.
  - `suffix` (first row only, applied by `rows`): not started → `in 46 min` (< 60 min), `in 2 h` (< 24 h, rounded down), else null; in progress → `now`.

- [ ] **Step 1: Write the failing tests** (fixed `now` = 2026-11-01T14:44 New York)

```kotlin
@Test fun select_dropsEnded_sortsByStart_limitsToThree()
@Test fun multiDayAllDay_spanningToday_isIncluded_asAllDay()   // Review Focus 1: start 10-30, end 11-03 → selected, timeColumn == "All day"
@Test fun timeColumn_today_12h() = assertEquals("3:30 PM", …)
@Test fun timeColumn_today_24h() = assertEquals("15:30", …)
@Test fun timeColumn_tomorrow()  = "Tomorrow 10:00 AM"
@Test fun timeColumn_withinWeek() = "Sat 10:00 AM"            // 2026-11-07
@Test fun timeColumn_later() = "12 Nov"
@Test fun timeColumn_allDayVariants()                         // "All day", "Tomorrow · All day", "Sat · All day", "12 Nov · All day"
@Test fun suffix_minutes_hours_null_now()                     // 15:30 → "in 46 min"; 18:00 → "in 3 h"; 2026-11-03 → null; started 14:00–15:00 → "now"
@Test fun rows_onlyFirstHasSuffix()
```

- [ ] **Step 2: Run, expect failure** — `--tests '*UpNextTest*'`
- [ ] **Step 3: Implement `UpNext`**
- [ ] **Step 4: Run, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: up-next selection and time formatting`

---

### Task 4: Calendar repository with cache

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/calendar/CalendarRepository.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/net/TextFetcher.kt`
- Modify: `app/src/main/java/com/nickesh/tvlauncher/AppContainer.kt` (wire `OkHttpTextFetcher`, `CalendarRepository`)
- Test: `app/src/test/java/com/nickesh/tvlauncher/data/calendar/CalendarRepositoryTest.kt`

**Interfaces:**
- Produces: `interface TextFetcher { suspend fun get(url: String, bearer: String? = null): String }` and `class OkHttpTextFetcher(client: OkHttpClient) : TextFetcher` (throws `IOException` on non-2xx; 10 s call timeout).
- Produces: `@Serializable data class CalendarCache(val events: List<CalendarEvent>, val fetchedAtMs: Long, val ok: Boolean)`; `class CalendarRepository(fetcher: TextFetcher, store: DataStore<Preferences>, clock: () -> Long, log: (String) -> Unit)` with `suspend fun cached(): CalendarCache?` (key `calendar_json`, corrupt → null), `suspend fun refresh(urls: List<String>, zone: ZoneId): CalendarCache` (fetches every URL, parses with window `now − 1 day … now + 60 days`, owner from `CalendarOwner.emailFromUrl`, calendar name from `IcsParser.calendarName` else URL host; **any URL failing keeps that URL's events from the previous cache** and marks `ok = false`; writes and returns the merged cache; empty `urls` → empty cache `ok = true`), `suspend fun validate(url: String): Result<Int>` (fetch + parse, returns event count or failure `"That link didn't return a calendar"` when the body has no `BEGIN:VCALENDAR`).
- Log lines contain the URL's host only, never the path.

- [ ] **Step 1: Write the failing tests** (fake `TextFetcher` keyed by URL, in-memory `DataStore` via `PreferenceDataStoreFactory.create` to a temp file as `DataStoreWeatherCache` tests do — check `app/src/test/.../data/weather/` for the existing pattern and reuse it)

```kotlin
@Test fun refresh_mergesTwoFeeds_andCaches()
@Test fun refresh_failingFeed_keepsItsOldEvents_andMarksNotOk()
@Test fun refresh_emptyUrls_isEmptyAndOk()
@Test fun cached_corruptBlob_isNull()
@Test fun validate_nonCalendarBody_failsWithCopy()
@Test fun validate_counts_events()
@Test fun log_neverContainsPath()   // route log into a list; assert no entry contains "private-"
```

- [ ] **Step 2: Run, expect failure** — `--tests '*CalendarRepositoryTest*'`
- [ ] **Step 3: Implement `TextFetcher`, `OkHttpTextFetcher`, `CalendarRepository`; wire in `AppContainer` (`val textFetcher`, `val calendar`)**
- [ ] **Step 4: Run, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: calendar repository with per-feed cache`

---

### Task 5: Todoist API, repository and ordering

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/todoist/TodoistApi.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/todoist/TodoistRepository.kt`, `app/src/main/java/com/nickesh/tvlauncher/domain/agenda/TodoOrder.kt`
- Modify: `AppContainer.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/data/todoist/TodoistApiTest.kt`, `app/src/test/java/com/nickesh/tvlauncher/domain/agenda/TodoOrderTest.kt`, `app/src/test/java/com/nickesh/tvlauncher/data/todoist/TodoistRepositoryTest.kt`

**Interfaces:**
- Produces: `@Serializable data class TodoTask(val id: String, val content: String, val dueDate: String?, val dueDatetime: String?, val order: Int)` (`dueDate` = `YYYY-MM-DD`, `dueDatetime` = RFC 3339 when the task has a time).
- Produces: `interface TodoistApi { suspend fun tasks(token: String): List<TodoTask>; suspend fun close(token: String, id: String); suspend fun reopen(token: String, id: String) }`; `class HttpTodoistApi(client: OkHttpClient, json: Json, baseUrl: String = "https://api.todoist.com/api/v1") : TodoistApi` — the implementer verifies the endpoints against https://developer.todoist.com/api/v1 before coding (as of 2026-11-01: `GET /tasks` paginated with `cursor`/`next_cursor`, `POST /tasks/{id}/close`, `POST /tasks/{id}/reopen`; if the docs differ, follow the docs and note it in the task report). Non-2xx → `IOException("todoist <status>")`; 401 → `TodoistAuthException`.
- Produces: `@Serializable data class TodoCache(val tasks: List<TodoTask>, val fetchedAtMs: Long, val ok: Boolean)`; `class TodoistRepository(api: TodoistApi, store, clock, log)` with `cached()` (key `todoist_json`), `refresh(token): TodoCache` (keeps old tasks + `ok=false` on failure), `close(token,id)`, `reopen(token,id)`, `validate(token): Result<Int>` (task count; failure message `"Todoist didn't accept that token"` on 401, `"Can't reach Todoist"` otherwise).
- Produces: `data class TodoRow(val id: String, val text: String, val due: String?, val overdue: Boolean)`; `object TodoOrder { fun sort(tasks: List<TodoTask>, today: LocalDate, zone: ZoneId): List<TodoTask>; fun dueLabel(t: TodoTask, today: LocalDate, zone: ZoneId): Pair<String?, Boolean>; fun rows(tasks: List<TodoTask>, today: LocalDate, zone: ZoneId): List<TodoRow> }`: order overdue (oldest first) → today → later (soonest) → undated (by `order`); labels: overdue → `"overdue"` + true; today → `"today"`; tomorrow → `"tomorrow"`; within 6 days → `"Fri"`; later → `"12 Nov"`; undated → null. A timed due earlier today is **today**, not overdue.

- [ ] **Step 1: Write the failing tests** (`TodoistApiTest` uses OkHttp's `MockWebServer`: add `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")` to the catalog)

```kotlin
// TodoistApiTest
@Test fun tasks_sendsBearer_andFollowsCursor()   // two pages via next_cursor → 3 tasks; request header Authorization == "Bearer tok"
@Test fun tasks_401_throwsAuth()
@Test fun close_posts_toCloseEndpoint()
// TodoOrderTest (today = 2026-11-01, NY)
@Test fun sort_overdueFirst_thenToday_thenLater_thenUndated()
@Test fun timedDue_earlierToday_isToday_notOverdue()
@Test fun dueLabel_variants()  // "overdue"/true, "today", "tomorrow", "Fri", "12 Nov", null
// TodoistRepositoryTest
@Test fun refresh_failure_keepsOldTasks_notOk()
@Test fun validate_401_hasCopy()
@Test fun log_neverContainsToken()
```

- [ ] **Step 2: Run, expect failure**
- [ ] **Step 3: Implement API, repository, ordering; wire `val todoist` in `AppContainer`**
- [ ] **Step 4: Run, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: Todoist api, cache and ordering`

---

### Task 6: Tick state machine

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/agenda/TickState.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/agenda/TickStateTest.kt`

**Interfaces:**
- Produces: `data class Pending(val id: String, val expiresAtMs: Long)`; `data class TickState(val pending: List<Pending> = emptyList(), val hiddenIds: Set<String> = emptySet())`; `object TickRules { const val UNDO_MS = 5_000L; fun tick(s: TickState, id: String, nowMs: Long): TickState; fun undo(s: TickState, id: String): TickState; fun fail(s: TickState, id: String): TickState; fun expire(s: TickState, nowMs: Long): Pair<TickState, List<String>>  /* ids that just left */; fun isPending(s: TickState, id: String): Boolean; fun visible(tasks: List<TodoRow>, s: TickState): List<TodoRow>; fun openCount(total: Int, s: TickState): Int }`.
  - Pending rows stay in `visible` (drawn ticked); after expiry they go to `hiddenIds` (removed until the next refresh returns without them); `undo`/`fail` remove the pending entry; `openCount` = total − pending − hidden.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun tick_addsPending_withFiveSecondDeadline()
@Test fun undo_removesPending()
@Test fun fail_removesPending()
@Test fun expire_movesToHidden_andReportsIds()
@Test fun visible_keepsPending_dropsHidden()
@Test fun openCount_subtractsPendingAndHidden()
@Test fun tick_twice_isIdempotent()
```

- [ ] **Step 2: Run, expect failure**
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: todo tick/undo state`

---

### Task 7: Watch Next reader and Continue watching selection

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/watchnext/WatchNextRow.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/watchnext/WatchNextReader.kt`, `app/src/main/java/com/nickesh/tvlauncher/domain/agenda/ContinueWatching.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.READ_TV_LISTINGS" />`)
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/agenda/ContinueWatchingTest.kt`

**Interfaces:**
- Produces: `data class WatchNextRow(val id: Long, val packageName: String, val title: String, val season: String?, val episode: String?, val type: Int, val lastEngagedMs: Long, val positionMs: Long?, val durationMs: Long?, val posterUri: String?, val intentUri: String?, val browsable: Boolean)`.
- Produces: `class WatchNextReader(context: Context) { fun hasPermission(): Boolean; fun read(): List<WatchNextRow>  /* empty when no permission or on any exception */; fun observe(onChange: () -> Unit): () -> Unit /* registers a ContentObserver on content://android.media.tv/watch_next_program, returns the unregister */ }` — projection columns exactly as in the spike report.
- Produces: `data class WatchTile(val id: Long, val packageName: String, val title: String, val meta: String, val progress: Float?, val posterUri: String?, val intentUri: String?)`; `object ContinueWatching { fun select(rows: List<WatchNextRow>, limit: Int = 3): List<WatchNextRow>  /* browsable && type in 0,1,2; sort lastEngagedMs desc */; fun meta(r: WatchNextRow): String; fun progress(r: WatchNextRow): Float?; fun tiles(rows): List<WatchTile> }`.
  - `meta`: `"S3 E22 · 9 min left"` (position and duration known, type 0), `"1h 56m left"` (no season/episode), `"S3 E23 · Up next"` (type 1 or 2, or no position), `""` when nothing is known. Remaining < 1 min → `"< 1 min left"`.
  - `progress`: `position / duration` clamped to 0..1; null when either is null or duration ≤ 0.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun select_filtersBrowsableAndTypes_sortsByEngagement_limits3()
@Test fun meta_episodeWithTimeLeft() = assertEquals("S3 E22 · 9 min left", …)   // position 842000, duration 1364000
@Test fun meta_filmHoursMinutes() = assertEquals("1h 56m left", …)                 // 5687000 of 12675000
@Test fun meta_newType_isUpNext() = assertEquals("S2 E1 · Up next", …)
@Test fun progress_clampsPastDuration_andNullWithoutDuration()                   // Review Focus 4
```

- [ ] **Step 2: Run, expect failure**
- [ ] **Step 3: Implement the pure object, the reader (Android, untested), and the manifest permission**
- [ ] **Step 4: Run, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: watch next reader and continue-watching selection`

---

### Task 8: AgendaViewModel

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/ui/home/AgendaViewModel.kt`, `app/src/main/java/com/nickesh/tvlauncher/domain/agenda/AgendaFreshness.kt`
- Modify: `AppContainer.kt` (`val watchNext = WatchNextReader(context)`)
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/agenda/AgendaFreshnessTest.kt`

**Interfaces:**
- Produces: `object AgendaFreshness { const val CALENDAR_MS = 15 * 60_000L; const val TODO_MS = 5 * 60_000L; const val STALE_MS = 5 * 60_000L; fun dueOnResume(fetchedAtMs: Long?, nowMs: Long): Boolean  /* null or older than STALE_MS */; fun sleepMs(periodMs: Long, ok: Boolean): Long /* period when ok, 90 s retry otherwise */ }`.
- Produces: `enum class BlockStatus { NOT_CONNECTED, UNREACHABLE, EMPTY, OK }`; `data class AgendaUi(val upNext: List<UpNextRow>, val upNextStatus: BlockStatus, val todo: List<TodoRow>, val todoOpen: Int, val todoStatus: BlockStatus, val pendingIds: Set<String>, val watching: List<WatchTile>, val watchingState: WatchingState)`; `enum class WatchingState { NEEDS_PERMISSION, DISMISSED, EMPTY, OK }`.
- Produces: `class AgendaViewModel(container: AppContainer) : ViewModel()` with `val state: StateFlow<AgendaUi>`, `fun refreshNow()` (resume: each block refetches only when `AgendaFreshness.dueOnResume`), `fun tick(id: String)` (optimistic `TickRules.tick`, launches `todoist.close`, on exception `TickRules.fail` + emits `toasts: SharedFlow<String>` with `"Couldn't update Todoist"`; a 5-second expiry job calls `expire` then refreshes Todoist), `fun undo(id: String)` (`TickRules.undo` + `reopen`), `fun onWatchNextPermission(granted: Boolean)` (denied → `settings.update { copy(continueWatchingDismissed = true) }`), `fun reloadWatchNext()`.
  - Loops mirror `HomeViewModel`'s weather loop: one `collectLatest` per block over the relevant settings (`calendarUrls` + `homeZone`; `todoistToken`), `while(true)` refresh → `withTimeoutOrNull(sleepMs) { refresh.first() }`. Cached values are emitted before the first network call. Watch Next: read on init, on `refreshNow`, and from the `observe` callback; `watchingState` = `NEEDS_PERMISSION` when `!hasPermission && !continueWatchingDismissed`, `DISMISSED` when dismissed and still no permission.

- [ ] **Step 1: Write the failing `AgendaFreshnessTest`** (`dueOnResume(null, now)`, `dueOnResume(now − 6 min, now)` true, `dueOnResume(now − 1 min, now)` false; `sleepMs(CALENDAR_MS, false) == 90_000`)
- [ ] **Step 2: Run, expect failure**
- [ ] **Step 3: Implement `AgendaFreshness` and `AgendaViewModel`; wire `watchNext` in `AppContainer`**
- [ ] **Step 4: Run, expect PASS; full suite + lint; `./gradlew -q :app:assembleDebug` compiles**
- [ ] **Step 5: Commit** `feat: agenda view model`

---

### Task 9: Home layout G and the three blocks

**Files:**
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts` (Coil 3: `coil = "3.3.0"`, `coil-compose`, `coil-network-okhttp`), `ui/home/HomeScreen.kt`, `ui/home/WeatherCard.kt`, `ui/home/WorldClockCard.kt`, `ui/home/HeroClock.kt`, `ui/home/AppsRow.kt`, `MainActivity.kt`, `TvLauncherApp.kt` (Coil `SingletonImageLoader.Factory` using `container`'s OkHttp)
- Create: `ui/home/UpNextCard.kt`, `ui/home/TodoCard.kt`, `ui/home/ContinueWatchingRow.kt`

**Interfaces:**
- Consumes: `AgendaUi`, `AgendaViewModel.tick/undo/onWatchNextPermission`, `WatchTile`, `UpNextRow`, `TodoRow`.
- Produces: `HomeScreen(state, agenda: AgendaUi, onTick: (String) -> Unit, onUndo: (String) -> Unit, onResume: (WatchTile) -> Unit, onAllowWatching: () -> Unit, onOpenCalendar: () -> Unit, …existing params)`; `UpNextCard(rows, status, onClick, modifier)`, `TodoCard(rows, open, status, pendingIds, onTick, onUndo, modifier)`, `ContinueWatchingRow(tiles, state, onResume, onAllow, onOpenSettings, modifier)`; `WeatherCard` gains `widthPx: Int = 580`; `WorldClockCard` gains `heightPx: Int = 152, timeSpx: Int = 52`; `AppsRow`/`Tile` gain `tileHeightPx: Int = 104`; `HeroClock` gains `digitSpx: Int = 220`.
- Layout targets in design px (1920×1080; shrink Continue-watching tiles first if anything clips at 1080): left column width 1104, right column width 586 at x = 1238 (gap 38). Row 1 (top padding 24 under the top bar): `HeroClock(digitSpx = 190)` + `WeatherCard(widthPx = 510)` with `SpaceBetween`. Row 2 (gap 20): three `WorldClockCard(heightPx = 140, timeSpx = 44)` each 352 wide, spacing 24. Row 3 (gap 20): label `CONTINUE WATCHING` 22 spx W500 letter-spacing 1, then three tiles 354×199, spacing 20, radius 20, poster via Coil `AsyncImage` (`ContentScale.Crop`, crossfade), app chip 16 spx on `0x8C000000`, bottom gradient `0x00000000→0xD9000000` with title 24 spx W500 and meta 18 spx `0xCCFFFFFF`, progress bar 6 px accent; focused: 3 dpx white border + scale 1.05 (match `AppTileButton`). Right column: `UpNextCard` height 300, `TodoCard` fills the rest (gap 20). Apps row: `tileHeightPx = 86`. Weather forecast columns: `Row { Text(day); Spacer(weight 1); Icon(28) }` then `hi lo` text, left-aligned.
- Remote path (`focusProperties`): weather card `right` → Up next; third clock `right` → first To do row (or the card when empty); third watch tile `right` → To do; To do last row `down` → apps row; Continue-watching tiles `down` → apps row; first To do row `up` → Up next.
- `TodoCard` rows: `Surface` per row, ring `Canvas` 28 px (filled accent with `Icons.Check` when pending), text 22 spx, due 18 spx muted (overdue in `0xFFF2A878`); `LazyColumn` with `focusRestorer()`; Select → `onTick`, Select on a pending row → `onUndo`. `UpNextCard`: one `Surface` (whole card focusable), rows 22 spx, time column 150 px accent `fontFeatureSettings = "tnum"`. Status lines use the pinned copy, 20 spx `0x99FFFFFF`, centred.
- `MainActivity`: `val agendaVm = viewModel { AgendaViewModel(container) }`; `ON_START` → `agendaVm.refreshNow()`; collect `agendaVm.toasts` into `Toast`; `onResume(tile)` → `Intent.parseUri(tile.intentUri, 0)` → `setPackage(tile.packageName)`, `addFlags(NEW_TASK)`, `startActivity`; on `ActivityNotFoundException`/`URISyntaxException`/null URI → `container.apps.launch(tile.packageName)`; `onAllowWatching` → `requestPermissions(arrayOf("android.permission.READ_TV_LISTINGS"), 7)`; `onRequestPermissionsResult(7)` → `agendaVm.onWatchNextPermission(granted)`; `onOpenCalendar` → launch `com.google.android.calendar` if installed.

- [ ] **Step 1: Add Coil and the `ImageLoader` factory; build** — `./gradlew -q :app:assembleDebug`
- [ ] **Step 2: Implement the three composables and the layout changes; build**
- [ ] **Step 3: Full suite + lint**
- [ ] **Step 4: Device proof (no credentials yet)** — `tools/tv.sh install && tools/tv.sh shot shots/agenda-layout-empty.png`; expect layout G with `Connect in Settings` in both cards, the permission tile in the Continue-watching row, weather beside the clock with the new forecast columns, nothing clipped at the bottom. Then press Select on the permission tile with `tools/tv.sh remote` / `adb shell input keyevent` and screenshot the dialog (`shots/agenda-permission.png`); accept it with the remote (`input keyevent DPAD_…`) and screenshot the populated row (`shots/agenda-watching.png`). Resume a title from the row and confirm Prime Video opens (`dumpsys window | grep mCurrentFocus`).
- [ ] **Step 5: Commit** `feat: Home layout G with up next, to do and continue watching`

---

### Task 10: Connect server, code and phone page

**Files:**
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts` (`nanohttpd = "2.3.1"`, `zxing = "3.5.3"`)
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/connect/ConnectCode.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/connect/ConnectServer.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/connect/ConnectPage.kt`, `app/src/main/java/com/nickesh/tvlauncher/data/connect/ConnectHandler.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/connect/ConnectCodeTest.kt`, `app/src/test/java/com/nickesh/tvlauncher/data/connect/ConnectServerTest.kt`

**Interfaces:**
- Produces: `object ConnectCode { fun generate(random: Random = Random.Default): String /* 6 chars from "abcdefghjkmnpqrstuvwxyz23456789" */ }`.
- Produces: `sealed interface ConnectOutcome { data class CalendarAdded(val name: String, val events: Int) : ConnectOutcome; data class TodoistConnected(val openTasks: Int) : ConnectOutcome; data object Nothing : ConnectOutcome; data class Failed(val message: String) : ConnectOutcome }`; `class ConnectHandler(current: () -> Settings, validateCalendar: suspend (String) -> Result<Pair<String, Int>>, validateToken: suspend (String) -> Result<Int>, save: suspend ((Settings) -> Settings) -> Unit) { suspend fun submit(calendarRaw: String?, tokenRaw: String?): List<ConnectOutcome> }` — applies `ConnectRules` (trim/normalise/append/replace), validates before saving, empty fields → no change; both fields may be submitted at once (two outcomes).
- Produces: `class ConnectServer(handler: ConnectHandler, code: String, port: Int = 0) { fun start(): Int /* bound port */; fun stop(); val url: (hostIp: String) -> String = "http://$hostIp:$port/c/$code" }` — NanoHTTPD: `GET /c/<code>` → `ConnectPage.html(...)`; `POST /c/<code>` (form fields `calendar`, `token`) → runs `handler.submit` (`runBlocking`), re-renders the page with the result line(s); any other path or a wrong code → 404 with an empty body; **the request log must not include the body or the query**.
- Produces: `object ConnectPage { fun html(results: List<ConnectOutcome>, calendars: List<String>, todoistMasked: String?): String }` — mobile-friendly inline-CSS page, title `TV Launcher · Connect`, two labelled fields with the spec's instruction lines, one `Save` button, result lines in green/red.
- Produces: `object LanAddress { fun ipv4(): String? }` (first non-loopback IPv4 from `NetworkInterface`; in `data/connect/ConnectServer.kt`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// ConnectCodeTest
@Test fun generate_isSixCharsFromAlphabet()
// ConnectServerTest (starts the server on port 0, calls it with OkHttp on 127.0.0.1)
@Test fun get_withCode_servesPage()               // 200, body contains "TV Launcher · Connect"
@Test fun get_wrongCode_is404()
@Test fun post_calendar_isTrimmed_validated_saved()  // Review Focus 2: " https://x/basic.ics\n" → validator sees "https://x/basic.ics"; save called with it appended; body contains "Calendar connected"
@Test fun post_badCalendar_showsFailure_doesNotSave()
@Test fun post_token_replaces_andShowsOpenCount()
@Test fun post_emptyFields_changesNothing()
@Test fun stop_closesPort()
```

- [ ] **Step 2: Run, expect failure**
- [ ] **Step 3: Implement code, handler, page, server, `LanAddress`**
- [ ] **Step 4: Run, expect PASS; full suite + lint**
- [ ] **Step 5: Commit** `feat: connect server and phone page`

---

### Task 11: Connect screen, Settings rows, Screen wiring

**Files:**
- Modify: `Screen.kt` (`data object Connect`, saver `"Connect"`), `MainActivity.kt`, `ui/settings/SettingsScreen.kt`
- Create: `ui/settings/ConnectScreen.kt`, `ui/settings/QrImage.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/ui/ScreenSaverTest.kt` (extend: `Connect` round-trips)

**Interfaces:**
- Consumes: `ConnectServer`, `ConnectHandler`, `LanAddress`, `ConnectRules.maskToken`, `CalendarRepository.validate`, `TodoistRepository.validate`.
- Produces: `QrImage(text: String, sizePx: Int, modifier)` — zxing `QRCodeWriter` → `BitMatrix` → `ImageBitmap`, remembered by `text`.
- Produces: `ConnectScreen(settings: Settings, url: String?, results: List<ConnectOutcome>, onDisconnectCalendar: (String) -> Unit, onDisconnectTodoist: () -> Unit, onTypeCalendar: () -> Unit, onTypeToken: () -> Unit, onBack: () -> Unit)`: left, the QR (560 px) over the URL text (24 spx) and the line `Scan with your phone, paste your calendar link and Todoist token`; right, a `Connected` list: one `SettingRow` per calendar (name or host, trailing `Disconnect`), one for Todoist (masked token, `Disconnect`) or `Not connected`; then `Type a calendar link here…` and `Type a Todoist token here…` rows that open a `TextSettingRow`-style entry whose commit goes through the same `ConnectHandler`. `url == null` → `Connect this TV to Wi-Fi first`.
- `SettingsScreen` gains a `Connections` header text and three rows after `Background…`: `Calendar…` (`Not connected` / `1 calendar` / `N calendars`, opens Connect), `To-do list…` (`Not connected` / `Todoist`, opens Connect), `Continue watching` (`On` when permission granted; `Allow…` otherwise → `onAllowWatching`; re-asks via `requestPermissions`, and when `shouldShowRequestPermissionRationale` is false after a denial, opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the package). New parameters: `calendarCount: Int, todoistConnected: Boolean, watchingGranted: Boolean, onOpenConnect: () -> Unit, onAllowWatching: () -> Unit`.
- `MainActivity`: `Screen.Connect` branch creates the handler + server in a `DisposableEffect` (start on enter, stop on dispose), holds `results` state, passes `url = LanAddress.ipv4()?.let(server.url)`; Back → `Screen.Settings` (draft preserved like Background). Connections save immediately via `container.settings.update`.

- [ ] **Step 1: Extend `ScreenSaverTest`; run, expect failure**
- [ ] **Step 2: Implement `QrImage`, `ConnectScreen`, Settings rows, `Screen.Connect`, wiring**
- [ ] **Step 3: Full suite + lint; `assembleDebug`**
- [ ] **Step 4: Device proof** — `tools/tv.sh install`; open Settings → Calendar… ; `tools/tv.sh shot shots/agenda-connect.png` shows the QR and URL; from the Mac, `curl -s http://10.0.0.21:<port>/c/<code> | head -3` returns the page and `curl -s -o /dev/null -w '%{http_code}' http://10.0.0.21:<port>/c/zzzzzz` is 404; `curl -s -X POST -d 'calendar=http://nope' …` shows the failure line; Back to Settings and confirm the port is closed (`curl` fails).
- [ ] **Step 5: Commit** `feat: connect screen with QR and settings rows`

---

### Task 12: Hand-off, device proofs, docs

**Files:**
- Modify: `README.md` (Features, Settings → Connections, a `## Connecting` section with the phone steps and the LAN threat model; remove the three items from the v2 backlog), `docs/superpowers/reports/agenda-home.md` (create: proofs + screenshots + deviations)

- [ ] **Step 1: Install the Task 11 build and hand off** — tell the user: open Settings → Calendar…, scan the QR with the phone, paste the Google Calendar secret iCal link and the Todoist API token, then create a throwaway task named `TV test` in Todoist. Wait for them.
- [ ] **Step 2: Proofs** (each a screenshot in `shots/` and a line in the report): `agenda-home-full.png` (all three blocks populated); `agenda-todo-tick.png` within 5 s of Select on `TV test` (ticked, count −1); `agenda-todo-undo.png` after a second Select (restored); tick again, wait 6 s, `agenda-todo-gone.png` and the user confirms the task is complete on the phone; `agenda-upnext.png` with the first row's suffix visible; the three empty states by temporarily disconnecting (`agenda-empty-*.png`); `logcat -d | grep -c <last4 of token>` is 0 and no line contains `private-`.
- [ ] **Step 3: README and report; full suite + lint one last time**
- [ ] **Step 4: Commit** `docs: agenda home README and device report`; push `v2-agenda`; tell the user it is ready to merge into `v1`/`main`.
