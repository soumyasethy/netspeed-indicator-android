# Build, test, device

Back to [[CONTEXT]].

## Build (JDK 17 mandatory — see [[gotchas]])
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
cd /Users/soumyasethy/Desktop/code/NetSpeedIndicator
./gradlew assembleDebug                       # debug APK
./gradlew testDebugUnitTest assembleRelease   # tests + release (~1.2 MB)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```
Unit tests: `GradientFlowTest`, `UsageHistoryTest`, `SpeedFormatterTest`,
`SpeedSamplerTest`, `SpeedTierTest`.

## Device testing (`device-testing` workflow)
- Confirm serial first: `adb devices` (Samsung `RZCY10L23WL`, Pixel `94BAY0LVUF`).
- Restart service after reinstall (it is killed): open app, toggle master OFF→ON,
  then `adb shell dumpsys notification | grep -c "NotificationRecord.*netspeed"`
  (expect 1, or 2 only mid dual-icon attempt).
- Generate traffic on-device (curl is present):
  `adb shell curl -s --limit-rate 800K -o /dev/null "https://speed.cloudflare.com/__down?bytes=60000000"`
  Run in background (held-open shell) so it sustains; rate-limit so the reading is
  steady and multi-digit.
- Ground-truth speed via kernel counters:
  `adb shell cat /proc/net/dev | grep wlan0` (col 2 = rx bytes) over a timed window.
- Inspect the live icon bitmap size: `adb shell dumpsys notification --noredact |
  grep -A1 "id=1001" | grep icon=` → `Icon(typ=BITMAP size=WxH)`. `1x1` = auto-hidden.
- Overlay permission for the bubble: `adb shell appops set com.netspeed.indicator
  SYSTEM_ALERT_WINDOW allow`.

## Screenshot capture (when image reads are available)
```bash
adb exec-out screencap -p > /tmp/s.png
# zoom the status bar with ffmpeg (nearest-neighbour keeps pixels crisp):
ffmpeg -i /tmp/s.png -vf "crop=900:118:0:0,scale=iw*3:ih*3:flags=neighbor" -y /tmp/z.png
```
Measure glyph heights numerically when image reads are blocked: dump a cropped
`format=gray` raw frame via ffmpeg → `od -tu1` → scan columns for pixels differing
from the median (background) by >60 to find content row-spans.
