#!/usr/bin/env bash
# press.sh <serial> <hold_ms>: presses KEY_HOMEPAGE (scancode 172) on a virtual bluetooth remote
# via the device's `uinput` tool. `input keyevent` never reaches accessibility services; this does.
set -eu
SERIAL="${1:?usage: press.sh <serial> <hold_ms>}"
HOLD="${2:?usage: press.sh <serial> <hold_ms>}"
case "$HOLD" in ''|*[!0-9]*) echo "hold_ms must be an integer" >&2; exit 2 ;; esac
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
REMOTE=/data/local/tmp/tvlauncher-remote.json
TMP="$(mktemp)"; trap 'rm -f "$TMP"' EXIT
cat >"$TMP" <<J
{"id":1,"command":"register","name":"TV Launcher Remote","vid":6353,"pid":1,"bus":"bluetooth","configuration":[{"type":100,"data":[1]},{"type":101,"data":[172]}]}
{"id":1,"command":"delay","duration":1500}
{"id":1,"command":"inject","events":[1,172,1,0,0,0]}
{"id":1,"command":"delay","duration":$HOLD}
{"id":1,"command":"inject","events":[1,172,0,0,0,0]}
{"id":1,"command":"delay","duration":800}
J
"$ADB" -s "$SERIAL" push "$TMP" "$REMOTE" >/dev/null
"$ADB" -s "$SERIAL" shell uinput "$REMOTE"
