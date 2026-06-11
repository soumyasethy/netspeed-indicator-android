# ISML Parity (icon size, steadiness, auto-hide, history) — Design

## Comparison findings (measured on device, Galaxy S25 Ultra)
Versus "Internet Speed Meter Lite" (com.internet.speed.meter.lite), both running,
curl rate-limited to 900 KB/s, kernel `/proc/net/dev` as ground truth (945 KiB/s
over a 10 s window):

1. **Accuracy:** both apps read the same kernel counters. Per-second readings
   diverge in BOTH directions (ISML 887 vs ours 1005; later ISML 925 vs ours 906)
   because token-bucket rate limiting is bursty and the apps sample at different
   second boundaries. Ours is NOT less accurate — but ISML's number is visibly
   STEADIER (they smooth), which *feels* more accurate.
2. **Font size:** ISML small-icon = stacked square (value over unit), heavier
   (black) typeface, zero padding, no arrow inside the icon, large bold unit.
   Their digits render visibly larger/denser than our vertical styles, which
   still float text with padding and spend pixels on the arrow glyph.
   (Their icon is a pre-baked RESOURCE drawable per value; ours is a per-second
   bitmap — equivalent display-wise; the win is typography + tightness, not the
   mechanism.)

## Decisions (user approved all four)

### F1 — Bigger stacked digits
Apply the tight cap-height technique (already used by the horizontal/single-row
renderers) to the remaining styles, with a heavier face:
- All icon text paints use `sans-serif-black` (heaviest system weight).
- `arrowsDownOnly` (Arrows ↕, download-only): value row cap-height ≈ 0.56×H flush
  to the top, unit row (small ↓ + bold unit) cap-height ≈ 0.30×H flush to the
  bottom, ~0.06×H gap, zero outer padding; bitmap width tight to content.
- `stacked`: same geometry without the arrow.
- `arrowsUpDown` (two rows ▲▼): rows sized by cap-height, zero outer padding.
- `compact`: single token at ~0.95×H cap-height (tight, like renderSingle).

### F2 — Steady displayed number (smoothing)
Exponential moving average over the DISPLAYED rates only, in the service:
`display = display + 0.45 × (raw − display)` per 1 s tick (≈2–3 s settle, like
ISML's feel). Applies to: status-bar icon, notification title/expanded number,
widget numbers. Raw values still drive: today/lifetime accounting (real deltas),
tier engine thresholds, hero (it has its own animation smoothing), peak tracking.
Snap-to-zero: if raw == 0 and display < 1 KB/s → display 0 (no long decay tail).
No settings toggle (always on).

### F3 — Auto-hide icon when idle
New toggle `autoHideIdle` (default OFF): when smoothed down+up < 1 KB/s for 30
consecutive seconds, the notification small icon is swapped to a fully
transparent bitmap (the ISML trick — Android requires an icon; alpha-0 renders
invisible). Any tick with traffic ≥ 1 KB/s restores the live icon immediately.
Panel row remains (FGS requirement). Settings copy: "Hide icon when idle".

### F4 — Daily usage history (30 days)
- Codec `data/UsageHistory.kt`: encode/decode `epochDay:bytes|epochDay:bytes|…`
  (newest last), `append(day, bytes)` keeping the most recent 30 — pure Kotlin,
  unit-tested.
- `SettingsRepository`: `KEY_DAILY_HISTORY` string + `dailyHistory: Flow<List<DayUsage>>`
  + `appendDailyHistory(day, bytes)`.
- Service: on day rollover in `accumulateToday()`, append the finished day's
  total before resetting.
- UI: "Usage history" section (collapsed by default) listing up to 30 rows:
  date label, total, proportional bar. Empty state: "History builds up day by day."

## Acceptance
- Icon digits at least match ISML's visual size side-by-side on device.
- Displayed number visibly steadier than before under bursty traffic.
- Auto-hide: icon disappears after ~30 s idle, returns on traffic (device check).
- History: codec unit tests green (append/trim/decode round-trip); rollover path
  code-reviewed (real rollover needs a date change).
- Release builds, tests green, APK ~1.2 MB.

## Files
NEW `data/UsageHistory.kt` + test; modify `service/IconRenderer.kt`,
`service/SpeedMeterService.kt`, `data/SettingsRepository.kt`,
`ui/SettingsScreen.kt`, `ui/MainActivity.kt`.
