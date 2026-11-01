# Agenda Home: calendar, to-do list and Continue watching on the Home screen

Date: 2026-11-01. Status: design, builder-checked 2026-11-01 (four questions: three settled below as my calls, one hand-off confirmed by the user), awaiting approval. Visual reference: `2026-11-01-agenda-home-mockup.html` beside this file (open in a browser; **option G** at the top is the approved layout, the rest is history).

## 1. Intent

The TV in the living room is the thing the user glances at most. When it wakes to our Home they want, without touching the phone: what is coming up on their calendar, what is on their to-do list (and the ability to tick a task off from the remote), and the shows they are in the middle of watching. Everything else on Home stays as it is.

The launcher will also be given to other people as a sideloaded APK from a public GitHub repo. So every new connection must be something a stranger with a remote and a phone can finish in a couple of minutes, with no laptop, no adb, no developer console, and the app must ship no secret keys.

## 2. Goals, non-goals, anti-goals

Goals
- Home shows three new blocks: **Up next** (next three calendar events), **To do** (open Todoist tasks, Select completes one), **Continue watching** (the last three shows in progress across apps, Select resumes playback).
- Connecting the calendar and the to-do list is done from the TV with a phone: scan a QR, paste two things, done.
- Everything that exists today keeps working and looks the same, except where this document says otherwise.

Non-goals (v2.0 of this feature)
- Creating or editing events or tasks on the TV. The TV reads and ticks; the phone writes.
- Any Google sign-in on the TV. Google Tasks, Google Keep and the Google Calendar API are out (Keep's API is Workspace-only; the TV device-code OAuth flow does not allow Calendar or Tasks scopes).
- A hosted relay/backend for "Sign in with Todoist". The personal API token is pasted instead.
- Netflix/YouTube/Hulu continue-watching when those apps do not publish to Android's Watch Next list (on the author's Chromecast only Prime Video publishes; nothing we can do about that).
- Play Store distribution, release signing, the public-repo README. Those are a separate follow-up.

Anti-goals (a build that does these has failed even if it works)
- Leaking a token: the Todoist token and the calendar's secret link must never be logged, shown in full on screen, or sent anywhere but Todoist/Google.
- Blocking Home on the network: Home must render instantly from cache; a dead network shows the last good data, never a spinner over the whole screen.
- Making the existing Home slower to appear or the apps row harder to reach than today.

## 3. Constraints

- Project: `~/ClaudeProjects/tvlauncher`, Kotlin + Compose for TV (`androidx.tv:tv-material`), minSdk 26, DataStore preferences for settings (`data/settings/Settings.kt`), OkHttp + kotlinx.serialization for network, `AppContainer` for wiring, design pixels via `dpx()`/`spx()` on a 1920×1080 mockup space (`LocalDesignScale` 0.5 on the Chromecast).
- Device: Chromecast with Google TV (Android 14, 1080p output, 720p-class GPU). Dev loop `tools/tv.sh connect|install|shot|remote|idle-perm`. Wireless debugging drops on standby; `tools/tv.sh port` now falls back to macOS `dns-sd`.
- No test files during the build until the app works end to end is NOT the rule here: this repo has 124 unit tests and lint 0; keep both green, add tests for every parser and every decision function (see §10).
- Branch + PR workflow is not required in this repo (user's personal project); work on branch `v2-agenda` off `v1`, user merges.

## 4. Home layout (decided by the user: option G)

Canvas is the current Home: greeting bar on top, apps row on the bottom, 96 dpx side padding. Between them, two columns.

**Left column (about two-thirds of the width, ≈ 60%)**
1. **Hero clock** (existing). Digits may shrink a little (the mockup uses ≈ 11% of screen width per digit line instead of 13%) so the rows below fit. Date line unchanged.
2. **Weather card** (existing), moved from the right edge to sit immediately right of the hero clock in the same top row, ≈ 26% of the screen width. Its content is exactly today's: big temperature, condition, high/low, city, divider, four forecast columns. One change the user asked for in the forecast columns: **line 1 is the day name on the left and the weather icon right-aligned on the same line; line 2 is "hi lo"**. (Today the icon sits between the day and the temperatures, stacked and centred.)
3. **World clock cards** (existing, same content and look: city, time, "Tomorrow · +9h 45m", weather icon and temperature), three cards squeezed into the left column's width (each ≈ 18% of the screen width instead of 29%). Time digits scale down to fit.
4. **Continue watching**: a label "CONTINUE WATCHING" and three 16:9 poster tiles across the left column. Each tile: the app's poster art filling the tile, a small app-name chip top-left, a bottom gradient with the show title (one line, ellipsised) and a meta line ("S3 E22 · 9 min left", or "1h 56m left" for a film, or just "S3 E22" when no position is known), and a thin accent progress bar along the bottom edge (hidden when unknown). Focus = the same white outline used by app tiles.

**Right column (≈ 30% of the width, full height from the top row to the apps row)**
5. **Up next** card (glass, same corner radius/colour as the clock cards), ≈ 29% of the screen height. Header "UP NEXT" with a muted "Google Calendar" on the right. Three rows: time column (accent colour, tabular digits) and title (one line, ellipsised). The first row carries a muted relative suffix ("in 46 min", "in 2 h"). Time column text: today → "15:30"; tomorrow → "Tomorrow 10:00"; within 6 days → "Sat 10:00"; later → "12 Nov"; all-day → "All day" (today) / "Sat · All day". Respect the 24-hour setting.
6. **To do** card below it, filling the rest of the column (≈ 33% of the screen height). Header "TO DO" with "Todoist · 5 open" on the right. Rows: a ring checkbox, the task text (one line, ellipsised), a muted due label on the right ("today", "Fri", "overdue" in a warm colour, nothing when undated). Rows are individually focusable; the list scrolls vertically when there are more rows than fit; the header count tells the rest.

**Bottom**: the existing apps row; tiles become a little shorter (≈ 2:1 instead of 1.55:1 in the mockup) to make room. "Your apps" label stays.

**Remote path** (builder: make these true, pin with focus tests where the existing suite has patterns): Right from the weather card lands on Up next; Right from the third world clock lands on To do's first row; Right from the third Continue-watching tile lands on To do; Down from To do's last row goes to the apps row; Down from Continue watching goes to the apps row; Up from the first To do row goes to Up next; Back on Home still does nothing.

## 5. Up next (calendar)

- **Source**: one or more Google Calendar "secret address in iCal format" links (`https://calendar.google.com/calendar/ical/…/private-…/basic.ics`). Any `https://` ICS URL is accepted; Google is just the documented path. My call: up to **4 links**, merged. Adding and removing (my call): the phone page has **one** calendar field; Save **appends** the link to `calendarUrls` (deduplicated, 5th link refused with "Up to 4 calendars"); an empty field on Save leaves the calendars unchanged. The Connect screen lists each connected calendar as its own row (named by the feed's `X-WR-CALNAME`, else the URL's host) with a **Disconnect** action that removes only that one. The Todoist field works the same way with a single token: Save replaces it, empty leaves it, Disconnect clears it.
- **Fetch**: GET each link with OkHttp (10 s timeout), **every 15 minutes** while Home is visible, plus on every Home resume (wake, return from an app) when the last fetch is older than 5 minutes. Cache the parsed events (JSON) in DataStore so Home renders from cache instantly. Keep the last good result on failure.
- **Selection**: expand recurring events, then take events whose end is after now, ordered by start, first three. All-day events count on their day (local time zone = the home zone setting). Cancelled events (`STATUS:CANCELLED`) are dropped. "Declined" (my call): when the link is a Google private feed, the owner's address is the path segment after `/ical/` (URL-decoded); an event where that address appears as an `ATTENDEE` with `PARTSTAT=DECLINED` is dropped. When no owner address can be derived (group calendars, non-Google feeds) nothing is dropped for declining. Builder's call on the ICS parser: recommended `net.sf.biweekly:biweekly` (pure Java, has RRULE iteration and handles `RECURRENCE-ID` overrides and `EXDATE`); a hand-written parser is acceptable only if it passes the fixtures in §10.
- **Select on the card**: opens the Google Calendar app if installed (`com.google.android.calendar`), otherwise nothing (the card is still focusable so the remote path works).
- **Empty states**: no link configured → the card shows one line "Connect in Settings"; link configured but never loaded → "Can't reach calendar"; loaded but nothing upcoming → "Nothing scheduled".

## 6. To do (Todoist)

- **Source**: Todoist's API with the user's personal API token (Todoist app → Settings → Integrations → Developer → API token) sent as `Authorization: Bearer`. Builder verifies the current endpoints against Todoist's docs at build time; as of this writing the unified **API v1** (`https://api.todoist.com/api/v1/tasks`, `POST …/tasks/{id}/close`, `POST …/tasks/{id}/reopen`) is current and REST v2 is deprecated.
- **Which tasks**: all open tasks across projects. Order: overdue (oldest first), due today, due later (soonest first), undated (Todoist's own order). Subtasks are shown like any other task.
- **Fetch**: every **5 minutes** while Home is visible, on every Home resume (same 5-minute staleness rule as the calendar), and immediately after a tick. Cached in DataStore; last good result kept on failure.
- **Tick** (user decision + my call on the undo): Select on a focused task marks it done. The row immediately shows a filled check and strikes through (optimistic), the header count drops by one, and the app calls close. For **5 seconds** the row stays in place; pressing Select on it again undoes (calls reopen, row returns to normal). After 5 seconds the row leaves the list. If close fails: the row reverts and a short toast says "Couldn't update Todoist". Focus moves to the next row when the ticked one leaves.
- **Empty states**: not connected → "Connect in Settings"; connected, never loaded → "Can't reach Todoist"; no open tasks → "All done".

## 7. Continue watching (Android TV Watch Next)

- **Source**: the system TV provider, `content://android.media.tv/watch_next_program`. Needs the runtime permission `android.permission.READ_TV_LISTINGS` (protection level dangerous on this build; the system shows a normal grant dialog). Device-proven 2026-11-01, report `docs/superpowers/reports/spike-watchnext.md`: 20 rows from Prime Video with title, season/episode, `last_engagement_time_utc_millis`, `last_playback_position_millis`, `duration_millis`, `poster_art_uri` (https), `intent_uri` (https deep link that resumes), `watch_next_type`.
- **Selection**: rows with `browsable = 1` and `watch_next_type` in {CONTINUE (0), NEXT (1), NEW (2)}, sorted by `last_engagement_time_utc_millis` descending, first three. My call: NEW is included because Prime publishes the next episode of a finished show as NEW (the spike saw types 0 and 2 only); sorting by last engagement keeps promotional NEW rows with old timestamps out of the top three. A NEW/NEXT row without a position shows the meta line "S3 E23 · Up next" and no progress bar. No dedupe beyond what the provider gives.
- **Display**: poster from `poster_art_uri` via an image loader (builder's call; recommended Coil 3 with the OkHttp network module, memory + disk cache, crossfade). App name from the package label. Meta line from season/episode and `duration − position` ("9 min left", "1h 56m left"); progress bar = position/duration.
- **Select**: `Intent.parseUri(intent_uri, 0)`-style launch of the row's intent URI with `setPackage(package_name)` and `ACTION_VIEW`; if that fails to resolve, launch the app's leanback launcher intent instead. Either way the launcher stays running as today.
- **Refresh**: on every Home resume, and a `ContentObserver` on the URI while Home is visible (the provider notifies on change).
- **Permission flow** (my call): on the first Home where the row would render and permission is not yet granted, the row shows one wide tile "Show what you're watching · Press Select to allow". Select calls `requestPermissions`. Granted → the real row. Denied → the row collapses to a single muted line "Continue watching is off · Settings" and Home does not ask again by itself. Settings gets a "Continue watching" row that re-asks (or, if the system no longer shows the dialog, opens the app's system settings page).
- **Empty state** when granted but nothing is in progress: three dimmed placeholder tiles and the line "Nothing in progress". The row keeps its height so the layout never jumps.

## 8. Connect flow (my call, accepted by the user)

- Settings gets a section **Connections** with rows: "Calendar…" (value "Not connected" / "1 calendar" / "2 calendars"), "To-do list…" ("Not connected" / "Todoist"), "Continue watching" ("On" / "Off" / "Allow…"). Calendar… and To-do list… open the same **Connect** screen.
- **Connect screen** (TV): left, a QR code (builder's call on the generator; recommended `com.google.zxing:core`, drawn to a bitmap) and under it the same URL in text, e.g. `http://10.0.0.21:8765/c/4f3a9k`. Right, two rows that show what is currently connected with a "Disconnect" action each, and a "Type it here instead" row per field that opens the existing on-screen text entry as a fallback.
- **Phone page**: while the Connect screen is open, the app runs a tiny HTTP server (builder's call; recommended NanoHTTPD 2.3.1) bound to all interfaces on a random high port. The URL carries a random one-time code (≥ 6 base-32 chars); any request without the current code gets 404. The page is plain HTML served by the app: two fields with one-line instructions each ("Google Calendar → Settings → your calendar → Secret address in iCal format. Paste the link." and "Todoist → Settings → Integrations → Developer → API token. Paste it."), one Save button, and a result line. On POST the TV validates (calendar: fetch the ICS and parse ≥ 0 events; Todoist: GET the tasks endpoint returns 200), stores on success, and the TV screen updates live ("Calendar connected · 12 events"). Failures are shown on the phone page and the TV ("That link didn't return a calendar"). The server stops when the Connect screen is left.
- **Storage**: links and the token go into the existing preferences DataStore (new keys). The token is never written to logs; on screen it shows as "••••1a2b" (last 4).
- **Threat model**: the page is only reachable on the LAN, only while the Connect screen is open, only with the code shown on the TV. That is good enough for a home network; say so in the README.

## 9. Settings model changes

New `Settings` fields (defaults in parentheses): `calendarUrls: List<String>` (empty), `todoistToken: String?` (null), `continueWatchingDismissed: Boolean` (false; set when the user denies). They follow the existing draft/Save/Discard pattern only for the toggle-like things; connections made from the Connect screen are saved immediately (they are the result of a round trip, not a draft). Cached calendar events and Todoist tasks are stored like the weather cache (JSON strings under their own keys), not as Settings fields.

## 10. Tests and proof

Unit (JVM, keep the suite green and lint at 0):
- ICS parsing with real Google export fixtures (checked in, sanitised): plain timed event, all-day, recurring weekly with `EXDATE`, recurring with a moved instance (`RECURRENCE-ID`), cancelled event, time zone with DST crossover, `UNTIL`/`COUNT`. Selection of "next three" at a fixed clock.
- Time-column formatting table (today/tomorrow/weekday/date/all-day, 12 h and 24 h) and the relative suffix.
- Todoist JSON → task list; ordering rule; tick/undo state machine (optimistic, 5-second window, failure revert).
- Watch Next row mapping (type filter, sort, meta line, progress) from cursor fixtures.
- Connect server: request with the code → page; without → 404; POST validation outcomes; server stops on leave.
- Settings round-trip for the new keys; token never appears in any log line (a test that routes the logger through a buffer).

Device proof is a **hand-off point in the plan**: unit-tested build installed on the TV → the user scans the QR from the Connect screen and pastes their own calendar link and Todoist token on the phone (credentials never pass through the chat, adb or the builder) → the builder runs the proofs. The tick/undo proof uses a throwaway task the user creates in their real Todoist. Device proof (screenshots in `shots/`, named per block): Home with all three blocks populated; the Connect screen with the QR; ticking a real task and seeing it gone in the Todoist phone app; undo within 5 s; resuming a real Prime Video title from the row; the permission dialog and both outcomes; the empty states. The phone side of the QR flow is walked by the user.

## 11. Left to the builder

Exact dpx sizes (derive from the mockup proportions and the existing cards), poster image library and ICS library choice (recommendations above), HTTP server library, QR library, how the three fetch loops share one lifecycle with the existing weather loops, how the Up next/To do cards split the right column at the 1080p design scale, icon for the "overdue" state, and the toast implementation (the app already shows toasts from Settings).

## 12. Out of scope but recorded for the follow-up

- Public-repo prep: README setup guide for the three connections, release signing, GitHub Releases APK, a settings-level "reset everything" for a new owner.
- Netflix/YouTube/Hulu continue-watching if they start publishing to Watch Next on Google TV.
- Snow/storm weather icons, rearrange apps row, Google Photos backgrounds (existing v2 backlog).
