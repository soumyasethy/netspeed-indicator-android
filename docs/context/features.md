# Features inventory

Back to [[CONTEXT]]. See [[gotchas]] for the constraints behind each.

## Status-bar icon (`IconRenderer.kt`)
- Styles (`data/IconStyle.kt`): ARROWS ↕, ARROWS_H ↔, STACKED, COMPACT, **AUTO ⇅**
  (shows the busier direction's arrow + speed, one at a time).
- Unit display (`data/UnitStyle.kt`): SHORT `84k` / FULL `84 KB/s` / BELOW
  (number over unit). Applies to single-direction styles + Compact.
- Custom colours: background, text/icon, **outline** (colour + 1/2/3 px width).
  NB colours only show fully on the floating chip / panel / widgets (OS tints the
  status-bar slot monochrome — see [[gotchas]]).
- Size slider `iconTextScale` 0.8–1.4 (× system font scale).
- Decorated chip = fixed SQUARE badge, glyphs fit inside with 14% v / 10% h
  padding, fill glyphs PUNCHED OUT so it never tints to a solid box.

## Service (`SpeedMeterService.kt`)
- 1 Hz `TrafficStats` sampling; EMA-smoothed display; today/lifetime accounting;
  tier engine; sparkline ring buffer.
- Auto-hide-when-idle (toggle); dual-icon attempt with One UI autogroup fallback.
- Signal % in panel sub-line (Wi-Fi RSSI / cellular level).
- 30-day usage history (`data/UsageHistory.kt`, codec unit-tested), appended on
  day rollover.

## Floating bubble (`FloatingChip.kt`)
- Draggable overlay over any app; tap opens the app; position persists.
- Size slider `floatingChipScale` 0.8–1.6. Renders the user's chosen
  bg/text/outline colours IN FULL (our surface, not OS-tinted).

## Widgets (`render/WidgetPainters.kt`, `widget/SpeedWidgets.kt`)
- 5 styles: HERO (4×2 gradient flow), DIAL, RINGS, PILL, WEATHER. Shared Canvas
  painters = pixel-identical to in-app previews.
- All overlap/unit bugs fixed (dial/pill used hardcoded "MB/s"; rings brand moved
  to centre hole; hero sparkline yields to measured text). Ring/pill colours
  derive from skin accent.
- "Add hero banner to Home screen" button pins HeroWidget; all 5 also in the
  launcher widget picker.

## In-app hero (`ui/hero/`)
- 14 live themes (Kinetic, Tier flow, Liquid, ECG, Dial, Radar, Particles,
  Curtains, Material You, Sky, Bento, Terminal, Brutalist, Glass) × 6 colour skins
  (Tier, Aurora, Carbon, Glasswave, Neo-brutal, Terminal).
- Theme = animation/treatment; skin = colour palette + font. They COMPOSE
  (every skin recolours every theme). 60 fps via `withFrameNanos`, paused off-
  RESUMED + under reduced motion.
- Known residual: a few hero themes (Glass/Brutalist/Terminal/Sky) still hardcode
  some of their own scene colours instead of full skin-derivation — cosmetic,
  noted in the icon-customisation audit.

## Design system (`ui/theme/Theme.kt`)
- Tier skin → Android 12+ dynamic (wallpaper) colour. Other skins → full
  primary/secondary/tertiary + on-colours DERIVED from the skin's hero palette
  (auto-contrast on-colour by luminance). Tier scale bar stays rainbow only on
  the Tier skin; other skins render it in their accent.

## TODO / not yet done
- Render the home widget to match the SELECTED hero theme (currently always the
  gradient hero). Needs porting Compose DrawScope themes → widget Canvas. Large.
- Full skin-derivation of the residual hero scene colours (see above).
- Floating bubble on the lock screen (overlay over keyguard) — security-sensitive,
  deferred.
