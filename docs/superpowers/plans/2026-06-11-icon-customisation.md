# Icon Customisation Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Auto direction style, unit-display choice, constant bg pill, outline
option, signal % in panel.

**Tech Stack:** Kotlin, Android Canvas, DataStore, Compose.

### Task 1: Data layer
**Files:** `data/IconStyle.kt`, NEW `data/UnitStyle.kt`, `data/SettingsRepository.kt`
- [ ] Add `AUTO("auto", "Auto ⇅", "Shows the busier direction")` to IconStyle.
- [ ] New enum `UnitStyle(storageKey, label)`: SHORT/FULL/BELOW, DEFAULT=SHORT, fromKey.
- [ ] Settings fields: `iconUnitStyle: UnitStyle`, `iconBorderColor: Int = 0`,
  `iconBorderWidth: Int = 1` + keys + setters. Build.

### Task 2: Renderer
**Files:** `service/IconRenderer.kt`
- [ ] Fields: `unitStyle`, `borderColorArgb` (0=off), `borderWidthPx` (1..3).
- [ ] `renderSingle(bps, down)` honours unitStyle (SHORT current; FULL = digits +
  half-cap " KB/s"; BELOW = stackedBitmap(value, unit, arrow=dir)).
- [ ] `compact` honours unitStyle (FULL adds unit text; BELOW = stackedBitmap no arrow).
- [ ] AUTO dispatch in renderStyle: `if (upBps > downBps) renderSingle(upBps, false) else renderSingle(downBps, true)`.
- [ ] Fixed-frame compositing: when bg or border active, final canvas W =
  max(contentW, refW(style,unitStyle)) (ref via "888" + widest unit), content centred;
  draw bg fill then border stroke (radius = min(w,h)*0.22, stroke = borderWidthPx*2f).
  Implement as `finalise(content: Bitmap): Bitmap` applied in render()/renderSingle().
- [ ] Build.

### Task 3: Service + notification
**Files:** `service/SpeedMeterService.kt`, `service/NotificationFactory.kt`, `AndroidManifest.xml`
- [ ] Wire renderer fields from settings each tick.
- [ ] `connectionLabel()` → returns label + signal % ("Wi-Fi 78%", "Mobile 50%",
  "Offline"): WifiManager.calculateSignalLevel(rssi,101); TelephonyManager
  .signalStrength?.level×25. Add ACCESS_WIFI_STATE to manifest.
- [ ] Build.

### Task 4: Settings UI
**Files:** `ui/SettingsScreen.kt`, `ui/MainActivity.kt`
- [ ] "Unit display" 3-chip row in IconStyleCard (below colour rows), wired +
  preview remember keys include unitStyle/border.
- [ ] "Outline" ColorSwatchRow (None + presets + custom) + width chips 1/2/3 px
  (shown when colour active). Wire MainActivity persists.
- [ ] Build.

### Task 5: Device verification
- [ ] AUTO: upload-dominant window (curl POST __up) → ▲; download → ▼.
- [ ] Unit chips: live icon switches 84k / 84 KB/s / below.
- [ ] Bg colour set: two captures different values → pill width identical.
- [ ] Outline 1px + colour visible; with transparent fill too.
- [ ] Shade: sub-line shows "Wi-Fi NN%".
- [ ] testDebugUnitTest + assembleRelease green ~1.2 MB.
