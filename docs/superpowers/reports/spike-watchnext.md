# Spike: can a sideloaded launcher read "Watch Next" on the Chromecast? (2026-11-01)

**Question.** Can our app show a "Continue watching" row (last shows across streaming apps) without privileged permissions or adb?

**Method.** Throwaway `WatchNextProbeActivity` built with `-Pprobe=true` (package `com.nickesh.tvlauncher.probe`), declaring `android.permission.READ_TV_LISTINGS`, querying `content://android.media.tv/{watch_next_program,channel,preview_program}` and logging rows. Device: Chromecast with Google TV (sabrina), Android 14 / API 34, TvProvider versionName 14.

**Findings (all device-proven).**
- `READ_TV_LISTINGS` is `prot=dangerous` on this build: a runtime permission. The probe's `requestPermissions` showed the stock GrantPermissionsActivity on screen; Allow → result `[0]`. No adb needed for other users.
- Without the grant the provider returns **empty cursors** (0 rows, no SecurityException) — the shell's `content query` is empty for the same reason (shell holds only WRITE_EPG_DATA).
- With the grant: `watch_next_program` = 20 rows, `channel` = 12 preview channels (YouTube, Prime, Tubi, Paramount+, Play Movies), `preview_program` ≈ 120–140 rows (Paramount+ etc.).
- Watch Next rows carry: `package_name`, `title`, `season_display_number`, `episode_display_number`, `watch_next_type` (0 CONTINUE, 2 NEW seen), `last_engagement_time_utc_millis`, `last_playback_position_millis`, `duration_millis`, `poster_art_uri` (https, 16:9 Amazon CDN), `intent_uri` (https://app.primevideo.com/watch?gti=…&time=auto — resumes playback), `browsable=1`.
- **Only Prime Video publishes** on this TV. Netflix, YouTube TV, Hulu all hold `WRITE_EPG_DATA` but had no Watch Next rows; Disney+ does not even hold it. launcherx holds `ACCESS_ALL_EPG_DATA` (signature-level; not available to us) — the stock "Continue watching" row may be fed server-side for those apps.
- `last_engagement_time_utc_millis` updates live (Friends S3 E22 row refreshed while the user was in Prime Video), so sorting by it and taking the top 3 of type CONTINUE/NEXT is the right query.

**Side lesson.** `adb mdns services` on this Mac lists nothing even while the TV advertises `_adb-tls-connect._tcp`; macOS `dns-sd -B/-L` finds it. `tools/tv.sh port` now falls back to dns-sd.

**Status.** Probe uninstalled from the TV. Code kept on branch `spike-watchnext` only; nothing merged.
