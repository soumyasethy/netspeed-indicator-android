# Horizontal Arrows Icon Legibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Make the "Arrows ↔" status-bar icon render near clock size (tight-glyph
sizing), add a persisted "Icon text size" slider, and fix the misleading preview.

**Architecture:** The horizontal renderer sizes text by measured digit cap-height and
fills ~0.90 of the bitmap height (vs 0.57). A new `userScale` (from a persisted
`iconTextScale`) multiplies the system font-scale across styles. The preview renders
the bitmap at fixed height with its natural aspect ratio.

**Tech Stack:** Kotlin, Jetpack Compose, Android Canvas, DataStore.

---

### Task 1: Persist `iconTextScale`

**Files:** Modify `app/src/main/java/com/netspeed/indicator/data/SettingsRepository.kt`

- [ ] **Step 1:** Add `floatPreferencesKey` import; field `val iconTextScale: Float = 1f`
  to `Settings`; read `iconTextScale = p[KEY_ICON_TEXT_SCALE] ?: 1f`; key
  `val KEY_ICON_TEXT_SCALE = floatPreferencesKey("icon_text_scale")`; setter
  `suspend fun setIconTextScale(v: Float) = edit { it[KEY_ICON_TEXT_SCALE] = v }`.
- [ ] **Step 2:** Build `./gradlew assembleDebug` → SUCCESSFUL.

### Task 2: Tight-glyph horizontal renderer + `userScale`

**Files:** Modify `app/src/main/java/com/netspeed/indicator/service/IconRenderer.kt`

- [ ] **Step 1:** Add `var userScale: Float = 1f` (coerce 0.7..1.4) and a private
  `eff get() = fontScale * userScale`.
- [ ] **Step 2:** Rewrite `arrowsHorizontal` to: measure digit cap-height via
  `getTextBounds("8")`, set `textSize` so cap-height ≈ `sizePx * (0.90f*eff).coerceIn(0.55f,0.96f)`,
  draw arrows at that same cap-height, baseline so the cap is vertically centred,
  bitmap width tight to measured content. No floating padding.
- [ ] **Step 3:** Multiply `eff` (not bare `fontScale`) into `valueTextSizeFor` and
  the text sizes in `arrowsUpDown`/`stacked`/`compact` so the slider affects all.
- [ ] **Step 4:** Build → SUCCESSFUL.

### Task 3: Service wires `userScale`

**Files:** Modify `app/src/main/java/com/netspeed/indicator/service/SpeedMeterService.kt:218`

- [ ] **Step 1:** After `iconRenderer.fontScale = …`, add
  `iconRenderer.userScale = settings.iconTextScale`.
- [ ] **Step 2:** Build → SUCCESSFUL.

### Task 4: Slider UI + preview fix

**Files:** Modify `app/src/main/java/com/netspeed/indicator/ui/SettingsScreen.kt`,
`app/src/main/java/com/netspeed/indicator/ui/MainActivity.kt`

- [ ] **Step 1:** Thread `iconTextScale: Float` + `onIconTextScale: (Float)->Unit`
  through `SettingsScreen` → `IconStyleCard`. Wire in `MainActivity`:
  `onIconTextScale = { v -> persist { repo.setIconTextScale(v) } }`.
- [ ] **Step 2:** In `IconStyleCard`, set `renderer.userScale = iconTextScale` before
  rendering previews (add `iconTextScale` to the `remember` key). Add a Material3
  `Slider` labelled "Icon text size" with `value=iconTextScale`,
  `valueRange=0.8f..1.4f`, `steps=11`, `onValueChange=onIconTextScale`, showing the %.
- [ ] **Step 3:** Fix `MiniStatusBar`: replace the `size(26.dp)`/`ContentScale.Fit`
  Image with `Modifier.height(20.dp).aspectRatio(preview.width.toFloat()/preview.height)`
  so wide bitmaps show wide + tall (true to the status bar).
- [ ] **Step 4:** Build → SUCCESSFUL.

### Task 5: Device verification (verification-before-completion)

- [ ] **Step 1:** Install debug, enable service, set Arrows ↔ style + combined.
- [ ] **Step 2:** Screenshot the status bar; confirm horizontal digits render clearly
  larger, near clock size (compare to the system clock).
- [ ] **Step 3:** Open the app; confirm the preview matches (wide, large glyph) and the
  slider grows/shrinks the icon live.
- [ ] **Step 4:** `./gradlew testDebugUnitTest assembleRelease` → green, APK ~1.2 MB.
