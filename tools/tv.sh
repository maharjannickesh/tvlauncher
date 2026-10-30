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
  *)
    echo "usage: tv.sh {port|connect|install|shot <name>|dream-set|dream|dream-stop}" >&2; exit 2 ;;
esac
