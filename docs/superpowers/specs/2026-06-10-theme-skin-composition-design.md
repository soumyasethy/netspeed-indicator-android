# Theme × Skin Composition — Design

## Problem
- Selecting any non-TIER **skin** makes the selected **theme** do nothing: the hero
  dispatch is `if (skin == TIER) drawTheme(...) else drawSkin(...)`. So ~all
  Theme×Skin combinations "break" (theme ignored).
- The "gemini" gradient colour-flow animation is dead: non-TIER skins bypass the
  animated theme path, and the remaining gradient drift (angle ±28° only) is too
  subtle to read as movement.

## Decision
Make Theme and Skin **compose**:
- **Theme** = the animation/treatment (what the hero is + how it moves).
- **Skin** = colour palette + font only (gradient stops, accent, base bg, fg, mono).
- The hero is **always theme-driven**, fed the skin's colours. Content dispatches on
  **theme**. Every skin recolours every theme.

The three special treatments that were skins become **themes** (per user approval):
- **Terminal** (htop sparkline + blinking cursor)
- **Brutalist** (hard block + offset shadow)
- **Glass** (frosted card over blurred blobs)

## Theme list (14)
Kinetic, Tier flow, Liquid, ECG, Dial, Radar, Particles, Curtains, Material You,
Sky, Bento, **Terminal**, **Brutalist**, **Glass**.

## Skin list (6) — palette + font only
Tier (live tier colours), Aurora, Carbon (mono), Glasswave, Neo-brutal, Terminal (mono).
Each supplies: `heroColors` (gradient stops), `accent`, `bg(dark)`, `fg`, `mono`.
Skins NO LONGER override hero background or content.

## Rendering rules
- `drawHeroBackground(theme, clock, mbps, gradColors, accent, dark)` runs for EVERY
  skin. `gradColors` = TIER → live blend; else → skin.heroColors. `accent` likewise.
- Content dispatch on theme:
  - Terminal → TerminalHero, Brutalist → BrutalHero, Glass → GlassHero,
    Bento → BentoContent, else → HeroContent. All use `fg`/`accent`/`mono` from skin.
- Remove `drawSkinBackground` and the skin-based content branch in `Hero.kt`.
- Gradient themes (Tier flow + the gradient base used by Kinetic/Bento) drift the
  gradient **position** on a slow Lissajous path (visible flow), not just the angle.

## Battery / accessibility (unchanged)
Single `withFrameNanos` clock, paused when not RESUMED and under reduced-motion
(animator scale 0 → static).

## Acceptance
- Every Theme×Skin pair renders without breaking (spot-check a diverse set on device).
- Gradient flow visibly animates (Tier flow + Aurora-skin).
- Terminal/Brutalist/Glass selectable as themes, recoloured by any skin.
- Release builds, unit tests green, APK stays ~1.2 MB.

## Files
`data/HeroTheme.kt`, `data/ColorSkin.kt`, `ui/hero/HeroThemes.kt`, `ui/hero/Hero.kt`,
`ui/SettingsScreen.kt` (theme picker already lists HeroTheme.entries → auto-picks up new themes).
