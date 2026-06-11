# Gemini Gradient Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** The reference gradient animation (wrapped 120° gradient, 3× size, 9 s
ping-pong pan) on the hero, the expanded notification card, and the hero widget.

**Architecture:** One `GradientFlow` engine (pure maths + an Android shader
builder). Hero feeds it the 60 fps clock; notification + widgets feed it wall-time
phase from the existing 1 s service tick.

**Tech Stack:** Kotlin, Compose DrawScope, android.graphics, RemoteViews.

---

### Task 1: GradientFlow engine + unit test

**Files:** Create `app/src/main/java/com/netspeed/indicator/core/GradientFlow.kt`,
`app/src/test/java/com/netspeed/indicator/core/GradientFlowTest.kt`

- [ ] Step 1: Write failing test (pan endpoints/midpoint, wrapped append, phase wrap).
- [ ] Step 2: Implement `PERIOD_MS=9000`, `phase(nowMs)`, `pan(phase)`, `wrapped(IntArray)`,
  and `shader(w,h,colors,phase)` (120° axis, span=3×diag, translate=pan·⅔span).
- [ ] Step 3: `./gradlew testDebugUnitTest` → green.

### Task 2: Hero flow (Kinetic + Tier flow/Bento base)

**Files:** Modify `app/src/main/java/com/netspeed/indicator/ui/hero/HeroThemes.kt`

- [ ] Step 1: `drawGradientBg(clock, colors)` — replace Lissajous centre drift with
  reference pan: stops = colors + first, span = 3×maxDim, axis 120°, offset =
  `GradientFlow.pan((clock % 9f)/9f) · ⅔span`. Keep both white blobs.
- [ ] Step 2: `drawKineticBg(clock, colors)` — same flow (add clock param at call site).
- [ ] Step 3: Build → SUCCESSFUL.

### Task 3: Widget hero flow

**Files:** Modify `app/src/main/java/com/netspeed/indicator/render/WidgetPainters.kt`

- [ ] Step 1: `WidgetData` + `phase: Float = 0f`. `hero()` uses `GradientFlow.shader(...)`.
- [ ] Step 2: Build → SUCCESSFUL.

### Task 4: Notification card flow

**Files:** Modify `app/src/main/res/layout/notification_expanded.xml`,
`app/src/main/java/com/netspeed/indicator/service/NotificationFactory.kt`

- [ ] Step 1: Layout root → `FrameLayout` { `ImageView` `@+id/notif_bg` (fitXY,
  match content) + existing `LinearLayout` (transparent bg) }.
- [ ] Step 2: `Content` + `gradientArgb: List<Int>` + `phase: Float`;
  `buildExpandedView` renders rounded gradient bitmap (600×~220, radius 14dp-px)
  via `GradientFlow.shader` → `setImageViewBitmap(R.id.notif_bg, …)`.
- [ ] Step 3: Delete `notif_strip_gradient.xml`. Build → SUCCESSFUL.

### Task 5: Service plumbing

**Files:** Modify `app/src/main/java/com/netspeed/indicator/service/SpeedMeterService.kt`

- [ ] Step 1: tick: `val flowPhase = GradientFlow.phase(System.currentTimeMillis())`;
  pass to `currentContent(...)` (gradient = skin heroColors or brand trio) and
  `pushWidgets(...)` → `WidgetData(phase = flowPhase)`.
- [ ] Step 2: Build → SUCCESSFUL.

### Task 6: Device verification (verification-before-completion)

- [ ] Step 1: Install; restart service. Hero (Kinetic, default): 2 screenshots ~3 s
  apart → colour bands moved.
- [ ] Step 2: Tier flow theme: same check.
- [ ] Step 3: Expand the notification: 2 captures ~4 s apart → card colours differ.
- [ ] Step 4: Hero widget on home (pin via launcher): 2 captures → gradient differs.
- [ ] Step 5: `./gradlew testDebugUnitTest assembleRelease` → green, ~1.2 MB.
