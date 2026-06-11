# Icon Customisation Pack — Design

Five user requests, one coherent icon-options upgrade.

## F1 — "Auto" icon style (busier direction wins)
New `IconStyle.AUTO("auto", "Auto ⇅", "Shows the busier direction")`: each second
the icon shows ONE direction — `▲ + speed` when upload > download, else
`▼ + speed` — rendered via the existing single-direction renderer (full-height
digits). Combined toggle is irrelevant for this style. Appears automatically in
the picker (it iterates `IconStyle.entries`).

## F2 — Unit display option (user-chosen)
New enum `UnitStyle`: 
- `SHORT` ("84k") — default, current look;
- `FULL` ("84 KB/s" inline, unit at half cap-height);
- `BELOW` ("84" over "KB/s" — number on top, unit below; arrow sits on the unit
  row).
Setting `iconUnitStyle` (default SHORT). Applies to the single-direction renders:
side-by-side dual icons, Arrows ↔ download-only, the new AUTO style, and Compact.
(The wide combined fallback row stays SHORT — width is already the constraint;
Stacked is inherently "below".) Picker: 3 chips under "Unit display".

## F3 — Constant background pill (bug fix)
Today the bitmap is tight-cropped per value, so with a custom background colour
the pill grows/shrinks every second ("1" vs "888") — jarring. Fix: when a
background is set (alpha ≠ 0) OR an outline is set (F4), the canvas width is
fixed per (style, unitStyle) using reference content ("888" + widest unit), and
the live content is centred inside. Pill height is already constant (= sizePx).
Content wider than the reference (rare 4-digit) widens it — never clips.
Transparent-bg-and-no-border icons stay tight-cropped (maximum glyph size).

## F4 — Outline / border option
New settings: `iconBorderColor` (ARGB, 0 = none — default) and `iconBorderWidth`
(1–3, default 1). Renderer draws a rounded-rect stroke (same radius as the bg
pill) on the fixed-size canvas; works with or without a fill colour. Stroke px =
width × 2 at the 96-px canvas (≈1 screen px per step). UI under the colour rows:
"Outline" swatch row (None ∅, presets, custom picker — reuse ColorSwatchRow) +
width chips `1px 2px 3px` (visible only when an outline colour is active).

## F5 — Signal strength in the notification panel
The notification's sub-line gains live signal %: `"… today · Wi-Fi 78%"` /
`"… · Mobile 50%"`.
- Wi-Fi: `WifiManager.connectionInfo.rssi` → `WifiManager.calculateSignalLevel(rssi, 101)`
  → 0–100 %. Requires `ACCESS_WIFI_STATE` (normal permission, auto-granted; the
  no-INTERNET privacy promise is untouched).
- Mobile: `TelephonyManager.signalStrength?.level` (0–4) → ×25 %.
- Offline/unknown → label only (no %). Computed once per second in the existing
  tick (no listeners, no wakeups).

## Storage
`icon_unit_style` (string), `icon_border_color` (int), `icon_border_width` (int).

## Acceptance (device)
- AUTO: arrow flips to ▲ during an upload-dominant window, ▼ during download.
- Unit chips switch the live icon between 84k / 84 KB/s / stacked-below.
- With a bg colour: pill width constant across changing values (two frames).
- Outline renders at chosen colour/width, with and without fill.
- Panel sub-line shows "Wi-Fi NN%".
- Unit tests green, release ~1.2 MB.

## Files
`data/IconStyle.kt` (+AUTO), NEW `data/UnitStyle.kt`, `data/SettingsRepository.kt`,
`service/IconRenderer.kt`, `service/SpeedMeterService.kt`,
`service/NotificationFactory.kt` (subtext), `ui/SettingsScreen.kt`,
`ui/MainActivity.kt`, `AndroidManifest.xml` (ACCESS_WIFI_STATE).
