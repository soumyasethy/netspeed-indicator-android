# Gotchas — hard-won, do not relearn

Back to [[CONTEXT]].

## Build / environment
- **JDK 17 required.** Homebrew default is now JDK 25 → Kotlin compiler throws
  `IllegalArgumentException: 25.0.2` (can't parse the version). Always:
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`
  before any `./gradlew`. Pixel/Samsung both fine; this is a host-toolchain issue.
- After `cd /tmp` (e.g. for ffmpeg) the shell cwd resets — re-`cd` to the project
  before `./gradlew`.

## Status-bar small icon — the OS owns the slot
- **The status bar ALWAYS tints small icons monochrome by alpha.** Baked colours
  (custom background/text) are ignored → an opaque filled chip becomes ONE solid
  white/black box ("white square box" bug). Fix: when a fill is set, PUNCH the
  glyphs OUT of the fill as transparent holes (`PorterDuff.Mode.DST_OUT` in
  `IconRenderer.frame()`), so the digits read as cut-outs after tinting. Custom
  colours only render in full on: the floating chip, the notification panel large
  icon, and widgets.
- **Icon height = OS slot height.** A wide bitmap is fit-by-WIDTH → scaled down →
  shorter than neighbouring icons. Only a ~square bitmap reaches full slot height.
  The decorated chip is therefore SQUARE (`w = sizePx`). Tight single-direction
  glyphs (no chip) can go near clock height.
- **One UI allows ONE status-bar icon per app.** It force-bundles all of an app's
  notifications under an autogroup summary (id=0) and shows only the summary's
  static icon — kills the "two notifications = ↓ and ↑ icons" trick AND leaves an
  orphan summary that renders as a blank box. Fix: detect autogroup
  (`activeNotifications` has id 0 / `overrideGroupKey`), persist the verdict
  (`dualIconsBlocked` in DataStore), never re-attempt, and cancel the orphan
  summary (`id==0 && FLAG_GROUP_SUMMARY`). Pixel only auto-groups at 4+, so dual
  icons work there.
- Wide bitmaps ARE shown wide (aspect honoured) up to the per-app width cap; beyond
  that they shrink. Filled bg → blob via alpha tint (see punch-out above).

## Notification / service
- Channel + builder both set `VISIBILITY_PUBLIC` → icon shows on the lock screen.
- Screen-off pauses sampling (battery); last notification persists (ongoing), so
  the lock-screen icon shows the last value, then resumes live when screen on.
- Auto-hide-when-idle swaps the icon to a 1×1 transparent bitmap after 30 s under
  4 KiB/s (ambient keep-alive pings are ~1–3 KiB/s, hence the 4 KiB/s floor).
- Restarting the service after a reinstall: toggling the master switch off/on in
  the UI; blind `adb input tap` on the toggle is unreliable (layout shifts) —
  verify with `dumpsys notification | grep -c netspeed`.

## Rendering / display
- Displayed speed is EMA-smoothed (α=0.45, ~2.5 s settle) so the number reads
  steady under bursty traffic; raw values still drive accounting/peak/tier/bus.
- Gradient "gemini" flow must be CONSTANT-VELOCITY seamless loop (tiled wrapped
  palette, REPEAT tilemode), NOT a ping-pong pan — ping-pong surges mid-cycle and
  reverses → reads jerky, especially on 1 fps surfaces (notification/widget use a
  36 s period so each step is ~3%).

## Floating overlay
- `TYPE_APPLICATION_OVERLAY` needs `SYSTEM_ALERT_WINDOW`, user-granted via
  `ACTION_MANAGE_OVERLAY_PERMISSION`. For test: `adb shell appops set <pkg>
  SYSTEM_ALERT_WINDOW allow`. Overlay does NOT show over the keyguard by default.

## Privacy invariant
- **Never add `android.permission.INTERNET`.** TrafficStats reads kernel counters;
  no network access needed. Added only read-only `ACCESS_NETWORK_STATE` +
  `ACCESS_WIFI_STATE` (signal %) and `SYSTEM_ALERT_WINDOW` (bubble). The no-INTERNET
  promise is a headline feature.
