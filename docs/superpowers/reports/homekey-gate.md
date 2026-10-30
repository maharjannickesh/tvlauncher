# Home key service: physical-remote gate

Result: **PASS** (both presses). The epic may proceed.

Device 10.0.0.21:37135, spike build (`spike-homekey` 4cdd20d), mode `split`, ga=4. Physical remote = `Chromecast Remote` (getevent), key events `dev=8 src=0x301`.
The spike's uinput device was not present at the time, so the dev id was not compared against it directly.
The events came from the human's physical remote and reached the accessibility service.

## Short press (YouTube -> Home): PASS
```
11:29:38.677 key code=KEYCODE_HOME action=DOWN ... held=0ms dev=8 mode=split
11:29:38.682 key code=KEYCODE_HOME action=UP   ... held=5ms  dev=8 mode=split
11:29:38.743 startActivity ok mode=home why=key
```
The human saw TV Launcher's Home, not Google TV Home.

## Long press, ~1.5 s (YouTube -> dashboard): PASS
```
11:30:04.104 key code=KEYCODE_HOME action=DOWN ... held=0ms dev=8
11:30:05.117 key code=KEYCODE_HOME action=UP   ... held=1021ms dev=8
11:30:05.124 performGlobalAction(4) -> true
```
The human saw the Google TV quick-settings panel. Note: `longPress=false` and no repeats arrive from the physical remote, so the service must decide long vs short by DOWN->UP duration (held >= 500 ms), and acts on UP.

A third Home press at 11:30:21 (short, startActivity ok) was the human leaving the panel. DPAD events between presses were navigating YouTube and were also received (dev=8).

## Screenshot
`shots/homekey-gate-final.png` is the screen after the presses. The per-press screenshots were not taken during the human walk; the human's observation plus logcat is the evidence.

## Baseline
Before: enabled_accessibility_services=null, accessibility_enabled=0. After restore: null, 0. Spike worktree removed; v1 build reinstalled (HomeKeyService absent from the installed package).
