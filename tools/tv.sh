#!/usr/bin/env bash
# Device loop for the Chromecast with Google TV (10.0.0.21, wireless adb).
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
HOST=10.0.0.21
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home}"

# Prints the _adb-tls-connect port for $HOST (no `timeout` on macOS: background + sleep).
port() {
  local out; out="$(mktemp)"
  "$ADB" mdns services >"$out" 2>&1 &
  local pid=$!
  sleep 6
  kill "$pid" 2>/dev/null
  grep '_adb-tls-connect' "$out" | grep "$HOST" | head -1 | sed -E "s/.*$HOST:([0-9]+).*/\1/"
  rm -f "$out"
}

# Reuses an already-connected 10.0.0.21 serial; otherwise discovers the port.
serial() {
  local s; s="$("$ADB" devices | awk -v h="$HOST:" 'index($1,h)==1 && $2=="device" {print $1; exit}')"
  if [ -n "$s" ]; then echo "$s"; return; fi
  local p; p="$(port)"
  [ -n "$p" ] && echo "$HOST:$p"
}

OURS=com.nickesh.tvlauncher/.MainActivity
STOCK_PKG=com.google.android.apps.tv.launcherx
STOCK=$STOCK_PKG/.home.HomeActivity
# setupwraith's RecoveryActivity is a HOME candidate at priority 1 (ours is capped at 0), so it
# must be disabled too while we own Home. The component only, never the package.
RECOVERY_PKG=com.google.android.tungsten.setupwraith
RECOVERY=$RECOVERY_PKG/.RecoveryActivity
RECOVERY_CLS=$RECOVERY_PKG.RecoveryActivity

home_resolve() { "$ADB" -s "$1" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r'; }

# Prints 1 if RecoveryActivity is in user 0's disabledComponents (from dumpsys package), 0 if not,
# nothing if dumpsys failed. pm list packages -d cannot see components.
recovery_disabled() {
  local d; d="$("$ADB" -s "$1" shell dumpsys package "$RECOVERY_PKG" 2>/dev/null)" || return 1
  [ -n "$d" ] || return 1
  printf '%s\n' "$d" | tr -d '\r' | awk -v c="$RECOVERY_CLS" '
    /^ *User [0-9]+:/ { u = ($2 == "0:"); blk = ""; next }
    u && /^ *disabledComponents:/ { blk = "d"; next }
    u && /^ *(enabledComponents|runtime permissions|install permissions):/ { blk = ""; next }
    u && blk == "d" && $1 == c { found = 1 }
    END { print found ? 1 : 0 }'
}

# Prints the current HOME component and whether the stock launcher is disabled (0|1).
home_status() {
  local s; s="$(serial)"
  [ -n "$s" ] || { echo "TV not found" >&2; return 1; }
  home_resolve "$s"
  local dis; dis="$("$ADB" -s "$s" shell pm list packages -d 2>/dev/null)" || { echo "launcherx disabled: unknown" >&2; return 1; }
  dis="$(printf '%s' "$dis" | tr -d '\r')"
  if [ -n "$dis" ] && printf '%s\n' "$dis" | grep -qv '^package:'; then echo "launcherx disabled: unknown" >&2; return 1; fi
  if printf '%s\n' "$dis" | grep -qx "package:$STOCK_PKG"; then echo "launcherx disabled: 1"; else echo "launcherx disabled: 0"; fi
  local r; r="$(recovery_disabled "$s")"
  [ -n "$r" ] || { echo "recovery disabled: unknown" >&2; return 1; }
  echo "recovery disabled: $r"
}

# Re-enables launcherx and RecoveryActivity, points Home back at launcherx, verifies. Prints raw pm output.
home_restore() {
  local s="$1" out rc=0
  out="$("$ADB" -s "$s" shell pm enable "$STOCK_PKG" 2>&1)"; echo "pm enable launcherx: $out"
  out="$("$ADB" -s "$s" shell pm enable "$RECOVERY" 2>&1)"; echo "pm enable RecoveryActivity: $out"
  out="$("$ADB" -s "$s" shell cmd package set-home-activity "$STOCK" 2>&1)"; echo "set-home-activity: $out"
  local now; now="$(home_resolve "$s")"
  [ "$now" = "$STOCK" ] || { echo "Home resolves to '$now', not $STOCK after restore" >&2; rc=1; }
  home_status || rc=1
  [ "$(recovery_disabled "$s")" = 0 ] || { echo "RecoveryActivity still disabled" >&2; rc=1; }
  return $rc
}

cmd="${1:-}"; shift || true
case "$cmd" in
  port) port ;;
  connect)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found (asleep or needs re-pairing)" >&2; exit 1; }
    "$ADB" connect "$s" ;;
  install)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    ( cd "$ROOT" && ANDROID_SERIAL="$s" ./gradlew installDebug -q ) || exit 1
    "$ADB" -s "$s" shell am start -n com.nickesh.tvlauncher/.MainActivity ;;
  shot)
    name="${1:?usage: tv.sh shot <name>}"
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    mkdir -p "$ROOT/shots"
    "$ADB" -s "$s" exec-out screencap -p > "$ROOT/shots/$name.png" ;;
  dream-set)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    "$ADB" -s "$s" shell settings put secure screensaver_components com.nickesh.tvlauncher/.ScreensaverService
    "$ADB" -s "$s" shell settings put secure screensaver_enabled 1
    "$ADB" -s "$s" shell settings put secure screensaver_activate_on_sleep 1 ;;
  dream)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    "$ADB" -s "$s" shell cmd dreams start 2>/dev/null | grep -qv "Unknown command" ||
      "$ADB" -s "$s" shell service call dreams 1 >/dev/null ;; # API 34 has no `cmd dreams`; IDreamManager.dream() is transaction 1
  dream-stop)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    "$ADB" -s "$s" shell service call dreams 2 >/dev/null ;; # IDreamManager.awaken()
  home-status) home_status ;;
  home-take)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    out="$("$ADB" -s "$s" shell cmd package set-home-activity "$OURS" 2>&1)"; echo "set-home-activity: $out"
    case "$out" in *Success*) ;; *) echo "set-home-activity failed; nothing disabled" >&2; exit 1 ;; esac
    out="$("$ADB" -s "$s" shell pm disable-user --user 0 "$RECOVERY" 2>&1)"; echo "pm disable-user RecoveryActivity: $out"
    out="$("$ADB" -s "$s" shell pm disable-user --user 0 "$STOCK_PKG" 2>&1)"; echo "pm disable-user launcherx: $out"
    # Verify after disabling, from device state rather than pm's wording.
    now="$(home_resolve "$s")"
    if [ "$now" != "$OURS" ]; then
      echo "Home resolves to '$now', not $OURS; restoring" >&2
      home_restore "$s"; exit 1
    fi
    home_status ;;
  home-restore)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    home_restore "$s" || exit 1 ;;
  idle-set)
    m="${1:-}"
    case "$m" in ''|*[!0-9]*) echo "usage: tv.sh idle-set <minutes>  (integer 1..1440)" >&2; exit 2 ;; esac
    m=$((10#$m))
    { [ "$m" -ge 1 ] && [ "$m" -le 1440 ]; } || { echo "usage: tv.sh idle-set <minutes>  (integer 1..1440)" >&2; exit 2; }
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    "$ADB" -s "$s" shell settings put system screen_off_timeout $((m*60000))
    "$ADB" -s "$s" shell settings get system screen_off_timeout ;;
  *)
    echo "usage: tv.sh {port|connect|install|shot <name>|dream-set|dream|dream-stop|home-status|home-take|home-restore|idle-set <minutes>}" >&2; exit 2 ;;
esac
