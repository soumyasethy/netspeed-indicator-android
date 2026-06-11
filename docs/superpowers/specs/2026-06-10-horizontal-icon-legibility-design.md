# Horizontal Arrows Icon Legibility — Design

## Problem (3rd report)
The "Arrows ↔" (down+up side-by-side) status-bar icon renders tiny and unreadable —
both in the live status bar and the in-app preview. Prior attempts (1.75×/2.1×
wide canvas, 0.62h/0.82h text) all stayed small.

## Root cause (verified by reading the renderer)
1. **Status bar:** `arrowsHorizontal` draws text at `textSize = h*0.82f` floating in
   the vertical centre of an `h`-tall bitmap. A bold font's *digit cap-height* is
   only ~0.72× of `textSize`, so the visible ink fills only ~0.82×0.72 ≈ **57% of
   the bitmap height**; the rest is transparent padding. When One UI scales the
   bitmap into the (already short) notification-icon slot, that padding is scaled
   too — so the digits land far smaller than the clock. Making the canvas *wider*
   never addressed the wasted *vertical* space.
2. **Preview:** `MiniStatusBar` shows the wide bitmap in a 26×26 dp
   `ContentScale.Fit` box → fits-to-width → ~26 dp wide × ~12 dp tall → tiny. The
   preview lied about the real (taller) status-bar rendering.

## Decision
- **Tight-glyph sizing for `arrowsHorizontal`:** size the text by measured digit
  cap-height (`Paint.getTextBounds`), draw the row so the glyph cap-height fills a
  fixed fraction of the bitmap height, and keep the bitmap height tied to that
  content. Default fill ≈ **0.90** of the bitmap (vs 0.57 today) → ~1.6× taller
  ink. Arrows are drawn at the same cap-height so digits and arrows match.
- **Honest ceiling:** the displayed height is still capped by the OS
  notification-icon slot (One UI also adds a little padding around any small icon),
  so it can sit *near* the clock size but not exceed the slot. This is an OS limit,
  not an app bug.
- **One UI honours wide bitmaps** (verified earlier: `↓81↑23k` rendered wide), so
  the bitmap stays tight horizontally and the row shows wide + legible.
- **User font-size control:** new persisted `iconTextScale` (0.8–1.4, default 1.0),
  multiplied with the system font-scale. Drives the cap-height target (clamped so
  the glyph can't exceed the bitmap). Applied across icon styles so the slider
  affects all of them.
- **Preview fix:** render the bitmap at a fixed *height* (~20 dp) with its natural
  aspect ratio (`Modifier.height(h).aspectRatio(bitmap.w/bitmap.h)`), so the
  preview matches the real wide, tall-glyph rendering.

## Components / files
- `data/SettingsRepository.kt` — `iconTextScale: Float = 1f`, `KEY_ICON_TEXT_SCALE`,
  `setIconTextScale`.
- `service/IconRenderer.kt` — `var userScale`; rewrite `arrowsHorizontal` (tight
  glyph); fold `eff = fontScale*userScale` into the text sizing of every style.
- `service/SpeedMeterService.kt` — `iconRenderer.userScale = settings.iconTextScale`.
- `ui/SettingsScreen.kt` — "Icon text size" slider in `IconStyleCard`; fix
  `MiniStatusBar` to height+aspect.
- `ui/MainActivity.kt` — wire `onIconTextScale`.

## Acceptance
- Horizontal icon digits render clearly larger than before, near clock size, on the
  USB device (screenshot vs the clock).
- The in-app preview matches the real status-bar appearance (wide, large glyph).
- The "Icon text size" slider visibly grows/shrinks the icon and persists across app
  death (set via app-scope persistence).
- Release builds, unit tests green, APK stays ~1.2 MB.

## On-device findings (One UI 7, Galaxy S25 Ultra)
Measured the rendered icon against the clock and other notification icons:
- The bitmap padding bug was real: tight-glyph sizing lifted the digits from ~30% of
  the clock height to ~58%.
- **One UI caps a notification icon's WIDTH** (~174 px) — not just height. A square
  icon (WhatsApp, Gmail) fills the slot at ~clock height, but a *wide* row is scaled
  down to the width cap, ending up shorter. A 2-value row "↓d ↑u unit" is ~2.9:1, so
  it was width-limited.
- Fix that mattered most: make the **arrows and unit small** (digits stay full
  height). That trims the row width, so the whole bitmap fits taller → digits reach
  **~69% of the clock height** (2.3× the original), clearly readable.
- Honest ceiling: a 2-number horizontal row can't reach 100% of clock — the two
  full-size digits are the irreducible width, and the OS width cap forces some
  down-scale. ~70% is the practical max for this layout. The `iconTextScale` slider
  affects the non-width-limited styles (Stacked/Compact/vertical) more strongly;
  for horizontal it is already near the cap at 100%.

## Addendum: dual-icon attempt (user suggestion) + One UI per-app icon cap
Tried splitting the side-by-side style into TWO notifications (down + up icons,
each near-square → fitted by height → clock-size digits). Verified via dumpsys:
**One UI force-bundles all of an app's notifications** under its own
`Aggregate_NormalNotificationSection` autogroup (ignores app group keys) and shows
only the summary's static resource icon — one status-bar icon per app, hard cap.
Shipped adaptively: try dual → detect autogroup (`activeNotifications` has id-0
summary / overrideGroupKey) → cancel the upload companion and fall back to the
single wide icon. Dual icons work on AOSP (auto-group threshold is 4+); Samsung
falls back automatically. `LiveSpeed.dualIconsBlocked` propagates the fallback so
the in-app preview mirrors reality.

Clock-size digits ARE achievable on One UI with a single-direction icon: the
Arrows ↔ style with "Show upload too" OFF renders "↓84k" as one near-square,
full-height row — measured equal to the clock digit height on-device.

The in-app preview now emulates the OS slot (max height + ~1.7× width cap), so a
wide icon previews shorter — exactly like the real status bar.

## Battery / privacy (unchanged)
No new permissions. Render still once/sec on the existing service tick.
