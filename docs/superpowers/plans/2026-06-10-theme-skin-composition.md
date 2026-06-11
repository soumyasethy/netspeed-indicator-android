# Theme × Skin Composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Make every Theme × Skin combination render correctly (theme = animation, skin = colour+font), promote Terminal/Brutalist/Glass to themes, and restore a visible animated gradient flow.

**Architecture:** The hero becomes purely theme-driven; skin only supplies colours + a mono flag. `drawHeroBackground(theme,…)` runs for every skin, fed the skin's `gradColors`/`accent`. Content dispatches on theme. The skin-override path is deleted.

**Tech Stack:** Kotlin, Jetpack Compose, Android Canvas.

---

### Task 1: Add the 3 new themes to the enum

**Files:** Modify `app/src/main/java/com/netspeed/indicator/data/HeroTheme.kt`

- [ ] **Step 1:** Add `TERMINAL("terminal","Terminal")`, `BRUTALIST("brutalist","Brutalist")`, `GLASS("glass","Glass")` to the `HeroTheme` enum (after BENTO). The theme picker reads `HeroTheme.entries`, so they appear automatically.
- [ ] **Step 2:** Build `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit `feat: add Terminal/Brutalist/Glass hero themes`.

### Task 2: Skins become colour-only (drop content/bg override)

**Files:** Modify `app/src/main/java/com/netspeed/indicator/ui/hero/Hero.kt`

- [ ] **Step 1:** In `TierFlowHero`, change the `drawBehind` to ALWAYS call `drawHeroBackground(theme, clock, smoothedMBps, gradColors, accent, dark)` (remove the `if (tierSkin) … else drawSkinBackground(…)`).
- [ ] **Step 2:** Change the content `when` to dispatch on **theme**, not skin:
  - `theme == TERMINAL -> TerminalHero(...)`
  - `theme == BRUTALIST -> BrutalHero(...)`
  - `theme == GLASS -> GlassHero(...)`
  - `theme == BENTO -> BentoContent(...)`
  - `else -> HeroContent(...)`
  (Keep passing skin-derived `fg`/`accent`/`mono`.)
- [ ] **Step 3:** Build → SUCCESSFUL. Commit `refactor: hero dispatches on theme; skin = colour only`.

### Task 3: Theme background dispatch handles the 3 new themes; delete skin override

**Files:** Modify `app/src/main/java/com/netspeed/indicator/ui/hero/HeroThemes.kt`

- [ ] **Step 1:** In `drawHeroBackground`'s `when(theme)`, add:
  - `TERMINAL -> drawRect(if (dark) Color(0xFF050A06) else Color(0xFF0A140D))`
  - `BRUTALIST -> drawRect(gradColors.lastOrNull() ?: accent)` (flat, no gradient)
  - `GLASS -> drawGlass(clock, accent, dark)` (reuse existing blurred-blobs)
- [ ] **Step 2:** Delete `drawSkinBackground` and the now-unused `drawAurora`/`drawCarbon` IF only referenced there — OR keep `drawCarbon`/`drawAurora` reachable by making them part of theme dispatch is NOT required; just remove `drawSkinBackground`. (Aurora's drift becomes the standard gradient drift in Task 4.)
- [ ] **Step 3:** Build → SUCCESSFUL. Commit `feat: theme bg dispatch for new themes; drop skin override`.

### Task 4: Visible animated gradient flow (fix "gemini" animation)

**Files:** Modify `app/src/main/java/com/netspeed/indicator/ui/hero/HeroThemes.kt` (`drawGradientBg`)

- [ ] **Step 1:** Replace the subtle angle-only oscillation with a **position drift**: animate the gradient `start`/`end` along a slow Lissajous path (like the old `drawAurora`) AND keep a gentle angle wobble, so the colour bands visibly move. Period ~14–18s, amplitude ~0.4×size.
- [ ] **Step 2:** Keep the two drifting white blobs.
- [ ] **Step 3:** Build → SUCCESSFUL. Commit `fix: visible animated gradient flow`.

### Task 5: ColorSkin cleanup (palette + font only)

**Files:** Modify `app/src/main/java/com/netspeed/indicator/data/ColorSkin.kt`

- [ ] **Step 1:** Confirm ColorSkin still exposes `heroColors`, `accent`, `bg(dark)`, `fg(dark)`, `mono`. No content fields. (It already is palette-only — verify, no change likely needed.)
- [ ] **Step 2:** Build → SUCCESSFUL.

### Task 6: Device verification (verification-before-completion)

- [ ] **Step 1:** Install debug. Enable service.
- [ ] **Step 2:** Spot-check a diverse matrix on device (screenshot each):
  - Tier skin + Tier flow → animated gradient flow visible.
  - Aurora skin + Liquid theme → liquid waves in aurora colours.
  - Carbon skin + ECG theme → green ECG, mono number.
  - Terminal skin + Terminal theme → green htop.
  - Aurora skin + Terminal theme → htop in purple.
  - Neo-brutal skin + Brutalist theme → yellow block.
  - Glasswave skin + Radar theme → radar in glass colours.
- [ ] **Step 3:** Confirm none break (no crash, content + colours both apply).
- [ ] **Step 4:** `./gradlew testDebugUnitTest assembleRelease` → green, APK ~1.2 MB.
- [ ] **Step 5:** Commit any fixes.
