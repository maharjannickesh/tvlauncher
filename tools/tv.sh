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

# Prints the current HOME component and whether the stock launcher is disabled (0|1).
home_status() {
  local s; s="$(serial)"
  [ -n "$s" ] || { echo "TV not found" >&2; return 1; }
  "$ADB" -s "$s" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
  local dis; dis="$("$ADB" -s "$s" shell pm list packages -d 2>&1)"
  if [ -z "$dis" ]; then echo "launcherx disabled: unknown" >&2; return 1; fi
  if printf '%s\n' "$dis" | tr -d '\r' | grep -qx "package:$STOCK_PKG"; then echo "launcherx disabled: 1"; else echo "launcherx disabled: 0"; fi
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
    out="$("$ADB" -s "$s" shell cmd package set-home-activity "$OURS" 2>&1)"
    case "$out" in *Success*) ;; *) echo "set-home-activity failed ($out); stock launcher left enabled" >&2; exit 1 ;; esac
    now="$("$ADB" -s "$s" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')"
    [ "$now" = "$OURS" ] || { echo "Home resolves to '$now', not $OURS; stock launcher left enabled" >&2; exit 1; }
    out="$("$ADB" -s "$s" shell pm disable-user --user 0 "$STOCK_PKG" 2>&1)"
    case "$out" in *"new state: disabled-user"*) ;; *) echo "pm disable-user failed ($out); run home-restore" >&2; home_status; exit 1 ;; esac
    home_status ;;
  home-restore)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    out="$("$ADB" -s "$s" shell pm enable "$STOCK_PKG" 2>&1)"
    case "$out" in *"new state: enabled"*|*"already enabled"*) ;; *) echo "pm enable failed ($out); not changing home activity" >&2; exit 1 ;; esac
    out="$("$ADB" -s "$s" shell cmd package set-home-activity "$STOCK" 2>&1)"
    case "$out" in *Success*) ;; *) echo "set-home-activity failed ($out)" >&2; home_status; exit 1 ;; esac
    home_status ;;
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
