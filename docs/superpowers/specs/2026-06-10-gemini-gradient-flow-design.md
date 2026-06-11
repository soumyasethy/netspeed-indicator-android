# Gemini Gradient Flow Everywhere — Design

## Problem
The "gemini" effect = the gradient colour bands visibly FLOWING across the surface.
User reference (netspeed_theme_explorer.html):

```css
@keyframes flow {0%{background-position:0% 50%}50%{background-position:100% 50%}100%{background-position:0% 50%}}
background: linear-gradient(120deg, c1, c2, c3, c1);   /* wrapped: last stop = first */
background-size: 300% 300%;
animation: flow 9s ease infinite;                       /* pans 0→100%→0 */
```

Today:
- **In-app hero**: position drifts on a Lissajous loop, but slow/subtle and not the
  reference pattern.
- **Notification expanded card**: static XML `<shape>` gradient — no motion ever.
- **Widgets (hero 4×2)**: static `LinearGradient` — no motion ever.

## Decision
Implement the reference pattern EXACTLY, once, and use it on every gradient
surface; each surface keeps its existing refresh cadence (no new wakeups).

### `core/GradientFlow.kt` (new)
Pure-Kotlin maths (unit-testable, no android.graphics):
- `PERIOD_MS = 9_000` (reference: 9 s loop).
- `phase(nowMs): Float` → `[0,1)`.
- `pan(phase): Float = (1 − cos(2π·phase)) / 2` — smooth 0→1→0 ping-pong
  (the CSS `0%→100%→0%` with ease).
- `wrapped(colors: IntArray): IntArray` — appends `colors[0]` (the `…,c1` wrap).

Android-side painter (same file, separate fun):
- `shader(w, h, colors, phase): LinearGradient` — wrapped stops laid along the
  **120° axis** with total span **3×** the surface diagonal (`background-size:300%`),
  translated along that axis by `pan(phase) · 2×span/3` (CSS pannable range =
  200% of the element). Every pixel cycles c1→c2→c3→c1→back — the gemini flow.

### Surfaces
1. **Hero (Compose, 60 fps)** — `HeroThemes.kt`, `phase = (clock·1000 % 9000)/9000`:
   - `drawGradientBg` (Tier flow / Bento base): replace the Lissajous drift with
     the reference pan (wrapped stops, 3× span, 120°). Keep the two white blobs.
   - `drawKineticBg` (Kinetic — the DEFAULT theme): same flow (Kinetic must move
     too; it is what most users see).
   - Reduced-motion: clock already frozen → static, unchanged behaviour.
2. **Notification expanded card (1 fps, piggybacks the per-second notify)** —
   replace the static drawable with `FrameLayout` root + background `ImageView`;
   `NotificationFactory` renders a rounded-corner Bitmap per tick via the shared
   shader with the skin gradient (non-Tier skin) or brand blue→purple→pink (Tier).
   ~600×220 bitmap — same cost class as the existing per-second sparkline bitmap.
3. **Widgets (1 fps while screen on, piggybacks `pushWidgets`)** — `WidgetData`
   gains `phase: Float`; `WidgetPainters.hero` uses the shared shader. (Hero is
   the only gradient widget.) Framework `onUpdate` paints phase 0 — fine for a
   one-off snapshot.

### Phase source
Service tick computes `GradientFlow.phase(System.currentTimeMillis())` once per
second → notification + widgets. 9 s period at 1 fps = ~11% pan per step — reads
as a smooth drift.

## Battery
Zero new timers: hero animates via existing `withFrameNanos` (RESUMED +
reduced-motion gated); notification + widgets already re-render every second.
Delta = colour maths + one small gradient bitmap per tick.

## Acceptance
- Hero (Kinetic AND Tier flow): frames seconds apart show the colour bands moved.
- Notification expanded: captures seconds apart show different card colours.
- Widget hero: captures seconds apart show different gradient.
- Unit tests: `pan(0)=0`, `pan(.5)=1`, `pan(1)=0`, `wrapped` appends first, phase wraps.
- Release builds, APK ~1.2 MB.

## Files
NEW `core/GradientFlow.kt`, NEW `test …/GradientFlowTest.kt`;
modify `ui/hero/HeroThemes.kt`, `render/WidgetPainters.kt`,
`res/layout/notification_expanded.xml`, `service/NotificationFactory.kt`,
`service/SpeedMeterService.kt`; delete `res/drawable/notif_strip_gradient.xml`.

## Revision (user feedback: ping-pong felt jerky)
The CSS-style ping-pong pan surges mid-cycle (velocity peak ≈ π× the average) and
reverses direction — on the 1 fps surfaces each second stepped the pan by up to
~17%, a visible jump; even on the hero the surge-and-reverse read as jerk. Final
design: **constant-velocity seamless loop** — the wrapped palette is tiled
(`Shader.TileMode.REPEAT` / Compose `TileMode.Repeated`, tile = 2× diagonal) and
translated linearly, wrapping pixel-identically each period. Hero: 12 s period at
display refresh (equal displacement per frame — verified via screen-recording
frame extraction). Notification + widgets: 36 s period at 1 fps → ~2.8% nudge per
step, reads as continuous drift (verified: even drift across 6 s captures, no
jumps). Unit test asserts equal phase steps (no mid-cycle surge).
