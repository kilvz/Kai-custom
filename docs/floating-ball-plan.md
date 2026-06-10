# Floating Ball / AI Assistant Widget — Plan

## Overview
Android system-level overlay (`WindowManager`) showing a draggable floating ball. Tap it → expands into a mini chat composable (widget-style) where user can ask Kai about the current screen (via existing `ScreenReaderService`). Kai replies inline. The ball persists across activities via `DaemonService`.

## Architecture

```
DaemonService (existing foreground)
  └─ FloatingBallOverlay (WindowManager-managed View)
       ├─ FloatingBallView (small draggable circle)
       └─ FloatingChatView (expandable mini chat)
            ├─ reads current screen text via ScreenReaderService
            ├─ embeds Kai's chat logic (OverlayChatController)
            └─ renders messages, input field, send button
```

## Files to Create

| # | File | Purpose |
|---|------|---------|
| 1 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/FloatingBallService.kt` | Foreground service managing the overlay via `WindowManager` |
| 2 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/FloatingBallLayout.kt` | `FrameLayout` — hosts ball view and expandable chat view. Handles touch drag. |
| 3 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/FloatingBallView.kt` | Custom `View` drawing the circular floating ball icon |
| 4 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/FloatingChatView.kt` | `ComposeView` hosting mini chat UI (message list + input) |
| 5 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/OverlayChatController.kt` | Lightweight controller — holds message state, calls `dataRepository.ask()`, reads screen via `ScreenReaderService` |
| 6 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/OverlayPermissionHelper.kt` | Checks `Settings.canDrawOverlays()`, opens `ACTION_MANAGE_OVERLAY_PERMISSION` |
| 7 | `composeApp/src/androidMain/kotlin/com/kai/custom/overlay/FloatingBallController.android.kt` | `start()`/`stop()` pattern mirroring `DaemonController` |

## Files to Modify

| # | File | Change |
|---|------|--------|
| 8 | `androidApp/src/main/AndroidManifest.xml` | Add `FloatingBallService` + `FOREGROUND_SERVICE_SPECIAL_USE` permission |
| 9 | `composeApp/src/commonMain/kotlin/com/kai/custom/AppModule.kt` | Register `FloatingBallController`, `OverlayChatController` in Koin |
| 10 | `composeApp/src/commonMain/kotlin/com/kai/custom/data/AppSettings.kt` | Add `isFloatingBallEnabled()` / `setFloatingBallEnabled()` |
| 11 | `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/` | Add toggle in Settings UI |

## Implementation Steps

**Step 1: Settings toggle** — `AppSettings.kt` key + `FloatingBallController` (start/stop wrapper)

**Step 2: Permission helper** — `OverlayPermissionHelper` checks/requests `SYSTEM_ALERT_WINDOW`

**Step 3: Floating ball service** — Foreground service, `TYPE_APPLICATION_OVERLAY`, inflates layout

**Step 4: Layout + Ball View** — `FloatingBallLayout` (FrameLayout) + `FloatingBallView` (48dp circle, draggable)

**Step 5: Overlay chat controller** — Message state, `dataRepository.ask()`, screen read integration

**Step 6: Floating chat composable** — `ComposeView` with message list, input, send/close buttons

**Step 7: Wire Koin + Manifest** — Register everything

**Step 8: Settings UI** — Toggle in Settings → Services

## Constraints

- API 29+: `TYPE_APPLICATION_OVERLAY` requires `SYSTEM_ALERT_WINDOW` (already declared)
- Android 12+: `FOREGROUND_SERVICE_SPECIAL_USE` type required
- Overlay chat history limited to last 10 exchanges
- Drag position clamped to screen bounds
- Keyboard: `SOFT_INPUT_ADJUST_RESIZE` on chat expand
