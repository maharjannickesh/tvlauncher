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

SVC=com.nickesh.tvlauncher/com.nickesh.tvlauncher.HomeKeyService

# Echoes the colon-separated list $1 without our component (short or full form), no empty entries.
svc_list_without() {
  local out="" e
  local IFS=:
  for e in $1; do
    case "$e" in ""|"$SVC"|com.nickesh.tvlauncher/.HomeKeyService) ;; *) out="${out:+$out:}$e" ;; esac
  done
  printf %s "$out"
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
  homekey-on)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    cur="$("$ADB" -s "$s" shell settings get secure enabled_accessibility_services | tr -d '\r')"
    [ "$cur" = "null" ] && cur=""
    list="$(svc_list_without "$cur")"
    list="${list:+$list:}$SVC"
    "$ADB" -s "$s" shell settings put secure enabled_accessibility_services "$list"
    "$ADB" -s "$s" shell settings put secure accessibility_enabled 1 ;;
  homekey-off)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    cur="$("$ADB" -s "$s" shell settings get secure enabled_accessibility_services | tr -d '\r')"
    [ "$cur" = "null" ] && cur=""
    list="$(svc_list_without "$cur")"
    if [ -n "$list" ]; then
      "$ADB" -s "$s" shell settings put secure enabled_accessibility_services "$list"
    else
      "$ADB" -s "$s" shell settings delete secure enabled_accessibility_services
      "$ADB" -s "$s" shell settings put secure accessibility_enabled 0
    fi ;;
  homekey-status)
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    if v="$("$ADB" -s "$s" shell settings get secure enabled_accessibility_services)"; then v="$(printf %s "$v" | tr -d '\r')"; else v=unknown; fi
    echo "enabled_accessibility_services=$v"
    if v="$("$ADB" -s "$s" shell settings get secure accessibility_enabled)"; then v="$(printf %s "$v" | tr -d '\r')"; else v=unknown; fi
    echo "accessibility_enabled=$v"
    # The label (not the class name) is what the "Bound services:" line prints.
    if "$ADB" -s "$s" shell dumpsys accessibility | grep '^ *Bound services:' | grep -q 'TV Launcher Home key'; then echo "bound: yes"; else echo "bound: no"; fi ;;
  remote)
    case "${1:-}" in
      short) hold=80 ;;
      long) hold=1500 ;;
      *) echo "usage: tv.sh remote <short|long>" >&2; exit 2 ;;
    esac
    s="$(serial)"
    [ -n "$s" ] || { echo "TV not found" >&2; exit 1; }
    "$ROOT/tools/uinput/press.sh" "$s" "$hold" ;;
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
    echo "usage: tv.sh {port|connect|install|shot <name>|dream-set|dream|dream-stop|homekey-on|homekey-off|homekey-status|remote <short|long>|idle-set <minutes>}" >&2; exit 2 ;;
esac
