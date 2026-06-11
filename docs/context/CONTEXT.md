# NetSpeed Indicator — Context Index

> **Load this file first in any new session.** Obsidian vault root. Dense by design
> to minimise re-exploration tokens. Links: [[architecture]] · [[gotchas]] ·
> [[features]] · [[build-and-test]] (build + device testing).

## One-paragraph summary
Native Android real-time internet speed meter (Kotlin · Compose + Material 3 ·
DataStore · foreground service). Samples `TrafficStats` every 1 s, renders speed as
a notification small-icon bitmap (the only sanctioned status-bar surface), plus 5
home-screen widgets, an animated in-app hero, and an optional floating overlay
bubble. **Zero `INTERNET` permission** — verifiable privacy guarantee. Release APK
≈ 1.2 MB, no third-party deps.

## Project facts
- Path: `/Users/soumyasethy/Desktop/code/NetSpeedIndicator`
- Package: `com.netspeed.indicator`
- min SDK 26, target/compile 35. Gradle Kotlin DSL + version catalog. KSP-free.
- **Build needs JDK 17** (default Homebrew JDK 25 crashes Kotlin). See [[gotchas]].
- Test device(s): Samsung Galaxy S25 Ultra (One UI, `RZCY10L23WL`) and Pixel 3a
  (`94BAY0LVUF`). They hot-swap on USB — verify serial before assuming which.

## Where things live
- Status-bar icon rendering → `service/IconRenderer.kt`
- Per-second loop, smoothing, idle-hide, widgets push, signal % → `service/SpeedMeterService.kt`
- Notification build (panel card, gradient bitmap) → `service/NotificationFactory.kt`
- Floating overlay bubble → `service/FloatingChip.kt`
- Widgets (5 Canvas painters) → `render/WidgetPainters.kt`, providers `widget/SpeedWidgets.kt`
- In-app hero (14 themes) → `ui/hero/Hero.kt`, `ui/hero/HeroThemes.kt`
- Gradient-flow engine → `core/GradientFlow.kt`
- Settings screen → `ui/SettingsScreen.kt`; wiring → `ui/MainActivity.kt`
- Theme/skin tokens → `ui/theme/Theme.kt`; skins → `data/ColorSkin.kt`
- Persisted settings → `data/SettingsRepository.kt` (DataStore)

## Specs + plans (history of decisions)
`docs/superpowers/specs/` and `docs/superpowers/plans/` — one pair per feature batch:
theme×skin composition, horizontal-icon legibility, gemini gradient flow, ISML
parity, icon customisation pack. Read the matching spec before changing that area.
