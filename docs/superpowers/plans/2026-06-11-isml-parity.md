# ISML Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Bigger stacked icon digits (black face, tight cap-height), smoothed
displayed number, auto-hide-when-idle toggle, 30-day usage history.

**Architecture:** Typography/tightness changes live in `IconRenderer`; the EMA +
idle detection live in the service tick; history is a pure codec + DataStore key
+ a settings list section.

**Tech Stack:** Kotlin, Android Canvas, DataStore, Compose.

---

### Task 1: UsageHistory codec (TDD)
**Files:** Create `app/src/main/java/com/netspeed/indicator/data/UsageHistory.kt`,
`app/src/test/java/com/netspeed/indicator/UsageHistoryTest.kt`
- [ ] Failing tests: decode("")=[], round-trip, append keeps 30 newest, garbage → skip.
- [ ] Implement `data class DayUsage(epochDay: Long, bytes: Long)`;
  `decode(String?): List<DayUsage>`; `encode(List)`; `append(list, day, bytes, max=30)`.
- [ ] `./gradlew testDebugUnitTest` green.

### Task 2: Repository plumbing
**Files:** `data/SettingsRepository.kt`
- [ ] `autoHideIdle: Boolean = false` in Settings + KEY + setter.
- [ ] `KEY_DAILY_HISTORY`; `val dailyHistory: Flow<List<DayUsage>>`;
  `suspend fun appendDailyHistory(day, bytes)` (read-modify-write via UsageHistory).
- [ ] Build green.

### Task 3: Icon typography + tight stacked renderers
**Files:** `service/IconRenderer.kt`
- [ ] `textPaint()` uses `Typeface.create("sans-serif-black", Typeface.BOLD)`.
- [ ] Shared `capHeightFor(paint)` probe helper (reuse pattern from renderSingle).
- [ ] Rewrite `arrowsDownOnly`, `stacked`, `arrowsUpDown`, `compact` per spec
  (cap-height rows flush to edges, zero padding, tight width, unit bold-large,
  compact ≈0.95×H).
- [ ] Build green.

### Task 4: Service — EMA + idle hide + rollover append
**Files:** `service/SpeedMeterService.kt`
- [ ] Fields `displayDownBps/displayUpBps` (Double), `idleTicks`.
- [ ] In tick: EMA α=0.45 on raw rates; snap-to-zero; use display values for
  icon render, notification content, widgets. Raw keeps: accounting, peak, tier
  colour, SpeedBus.
- [ ] Idle hide: when `settings.autoHideIdle` && display sum < 1 KB/s → idleTicks++,
  else 0; ≥30 → icon bitmap = transparent 1×1 (cache it). Traffic restores.
- [ ] `accumulateToday()` day-rollover: `persist { repo.appendDailyHistory(prevDay, prevBytes) }`
  via appScope before reset.
- [ ] Build green.

### Task 5: Settings UI
**Files:** `ui/SettingsScreen.kt`, `ui/MainActivity.kt`
- [ ] Toggle row "Hide icon when idle — icon disappears after 30 s without
  traffic; the panel row stays" wired to `setAutoHideIdle`.
- [ ] "Usage history" expandable section: rows date · total · proportional bar;
  empty state copy. Reads `repo.dailyHistory` (pass via MainActivity collect).
- [ ] Build green.

### Task 6: Device verification
- [ ] Install, restart service. Side-by-side screenshot vs ISML at idle + under
  900 KB/s: our digits ≥ theirs.
- [ ] Burst traffic: our number steps smoothly (no 1005→906 swings).
- [ ] Enable auto-hide, wait 35 s idle → icon gone; start traffic → icon back.
- [ ] `./gradlew testDebugUnitTest assembleRelease` green, ~1.2 MB.
