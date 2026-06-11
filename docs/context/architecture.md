# Architecture

Back to [[CONTEXT]]. Constraints in [[gotchas]], inventory in [[features]].

## Data flow (one tick)
```
SpeedMeterService.tick()  [every 1 s, lifecycleScope ticker]
  ├─ TrafficStats.getTotalRx/TxBytes → SpeedSampler delta → bytes/sec
  ├─ accounting: today / lifetime / peak / sparkline ring buffer
  ├─ EMA smoothing → displayDownBps/UpBps  (α 0.45, snap-to-zero)
  ├─ auto-hide gate (30 s < 4 KiB/s → 1×1 transparent icon)
  ├─ IconRenderer.render(style, down, up, …) → small-icon Bitmap
  │     └─ frame(): if bg/outline → fixed SQUARE chip, glyphs PUNCHED OUT
  ├─ NotificationFactory.build(Content) → notify(1001)
  │     └─ expanded card bg = per-tick GradientFlow bitmap (gemini flow)
  ├─ FloatingChip.update(text + user colours)   [if enabled + overlay perm]
  ├─ SpeedWidgetProvider.pushAll(WidgetData{…, phase})  [if any widget present]
  └─ SpeedBus.publish(LiveSpeed)  → in-app hero + previews observe this StateFlow
```

## Surfaces (where the speed is shown)
| Surface | Renderer | Colour fidelity |
|---|---|---|
| Status-bar icon | `IconRenderer` → notification small icon | OS-tinted monochrome |
| Notification panel card | `NotificationFactory` RemoteViews + bitmap | full colour |
| Floating bubble | `FloatingChip` (WindowManager overlay) | full colour |
| Home widgets ×5 | `WidgetPainters` Canvas → RemoteViews bitmap | full colour |
| In-app hero | Compose `ui/hero/*` DrawScope, 60 fps | full colour |

## Key boundaries
- **`SpeedBus`** (StateFlow) is the ONLY service→UI channel; service is the sole
  writer and clears to `running=false` on stop so the preview never freezes stale.
- **`IconRenderer`** is pure (in → Bitmap); same instance backs the live icon and
  the in-app style previews → what you pick is what you get.
- **`WidgetPainters`** are pure Canvas painters; same code = widget bitmap AND
  in-app widget preview.
- **`GradientFlow`** is pure maths + a shader builder; feeds hero (frame clock),
  notification card and widgets (wall-clock phase). Unit-tested.
- **`SettingsRepository`** (DataStore) is the single source of persisted truth;
  both UI and service collect its `settings: Flow`. Persistence writes go through
  `NetSpeedApp.appScope` (survives activity death), not lifecycleScope.

## Service lifecycle
`specialUse` FGS, `START_STICKY` + `onTaskRemoved` self-heal. Screen-off pauses
sampling unless the user opts in; re-baselines on screen-on. Boot receiver re-arms
if previously enabled.
