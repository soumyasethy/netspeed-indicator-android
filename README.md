# netspeed-indicator-android

Real-time internet speed meter for Android. Shows live download/upload speed as a
status-bar icon, home-screen widgets, an animated in-app dashboard and an optional
floating bubble — refreshed every second from kernel `TrafficStats` counters.

## Highlights
- **Status-bar speed icon** — 5 styles (Arrows ↕/↔, Stacked, Compact, Auto), unit
  display options, custom colours, outline chip, size slider. Rendered as a
  per-second notification small-icon bitmap (the only sanctioned status-bar surface).
- **Floating speed bubble** — draggable overlay chip over any app, fully size-configurable.
- **5 home-screen widgets** (hero/dial/rings/pill/weather) sharing one Canvas paint
  pipeline with the in-app previews.
- **Animated hero** — 14 live themes × 6 colour skins, gemini-style flowing gradients
  (constant-velocity seamless loop), reduced-motion aware.
- **Design system** — Material 3 tokens (primary/secondary/tertiary + on-colours)
  derived from the active skin.
- **Usage tracking** — today / 30-day history / lifetime, daily quota ring.
- **Smoothed display** (~2.5 s EMA), auto-hide when idle, signal strength % in the
  notification panel.
- **Privacy: zero `INTERNET` permission** — verifiable guarantee the app cannot
  phone home. APK ≈ 1.2 MB, no third-party dependencies.

## Tech
Kotlin · Jetpack Compose + Material 3 · DataStore · Foreground service (specialUse)
· min SDK 26, target/compile 35 · Gradle Kotlin DSL + version catalog.

## Build
```bash
# Needs JDK 17
./gradlew assembleDebug
./gradlew testDebugUnitTest assembleRelease
```
