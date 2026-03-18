# Agent Display / Virtual Display / Shizuku Notes

This document summarizes what `Agent Display` is, how it relates to `Agent Virtual Display`, what Android APIs are involved, and what works on stock Android with/without Shizuku.

---

## 1) What is **Agent Display** vs **Agent Virtual Display**?

### Agent Display (`AGENT_DISPLAY_API.md`)

`Agent Display` is the **framework Binder API surface** (`IAgentDisplayService`) that AndyClaw calls.

It defines methods for:

- Display lifecycle (`createAgentDisplay`, `destroyAgentDisplay`, `resizeAgentDisplay`, `getDisplayInfo`)
- App launch/control on the display
- Touch/gesture/key/text/clipboard input
- Screen capture (`captureFrame*`)
- Accessibility tree + node actions via proxy

Think of this file as the **contract**: what methods are available.

### Agent Virtual Display (`AGENT_VIRTUAL_DISPLAY.md`)

`Agent Virtual Display` is the **implementation architecture/design** behind the above API.

It explains:

- How to create/control a `VirtualDisplay`
- How to inject `MotionEvent`/`KeyEvent` style input
- How screenshot capture works with `ImageReader`
- Why auto-screenshot-after-every-action is useful for LLM loops
- Why permissions (`INJECT_EVENTS`) and execution context matter

Think of this file as the **how-to internals**.

---

## 2) Which Android APIs are used?

Core APIs/patterns used by the design:

- `DisplayManager#createVirtualDisplay(...)` + `VirtualDisplay`
- `ImageReader` (`newInstance`, `acquireLatestImage`) for capture
- `MotionEvent` (with display targeting via `displayId`)
- `InputManager.injectInputEvent(...)` for event injection (hidden/system API)
- `KeyEvent` + `KeyCharacterMap.getEvents()` for key/text paths
- `AccessibilityService` + `AccessibilityNodeInfo.performAction(...)` for tree/node actions

Important permission note from the design doc:

- `android.permission.INJECT_EVENTS` is signature-level.
- The doc recommends shell-context execution (`app_process`) for reliable injection when not platform-signed.

---

## 3) Can base/generic Android AndyClaw use Agent Display today?

Short answer: **No, not out-of-the-box.**

Why:

1. `AgentDisplaySkill` is privileged-only in this repo.
   - OPEN tier manifest has no `agent_display` tools.
2. It depends on framework Binder service `"agentdisplay"`.
   - If missing, it throws `AgentDisplayService not available`.
3. UI exposes Agent Display test controls only in privileged mode.

So on stock/generic Android builds, this exact feature path is unavailable unless the platform service/backend exists.

---

## 4) If Shizuku is running and connected, can we use this exact functionality?

Short answer: **Not the exact AgentDisplay binder feature.**

Shizuku gives ADB/shell-level command execution (very powerful), but it does **not** magically provide the framework `agentdisplay` Binder service.

So:

- **Exact `agent_display_*` feature path**: still unavailable without the platform service.
- **Practical automation alternative**: yes, many actions are still possible via shell commands (`input`, `am`, `wm`, `screencap`, etc.).

---

## 5) Recommended Shizuku fallback design (stock Android)

Goal: emulate useful parts of Agent Display without framework `agentdisplay`.

### Suggested tool surface

- `screen_screenshot`
- `screen_tap(x, y)`
- `screen_swipe(x1, y1, x2, y2, duration_ms)`
- `screen_key(keycode)`
- `screen_type(text)`
- `screen_launch(package/component)`
- (optional) `screen_ui_dump` via `uiautomator dump`

### Suggested implementation mapping

- Tap → `input tap x y`
- Swipe → `input swipe x1 y1 x2 y2 duration`
- Key → `input keyevent <code>`
- Type → `input text <escaped>`
- Launch → `am start ...`
- Screenshot → `screencap -p` (binary output path preferred)

### Suggested orchestration loop

1. Capture initial screenshot
2. Ask model for one action
3. Execute action via Shizuku
4. Wait for UI settle (roughly 300–1000ms depending on action)
5. Capture screenshot again
6. Repeat until complete or iteration cap reached

### Known tradeoffs vs true Agent Display

- Controls the active/main display (no isolated virtual display)
- Less deterministic than targeted `displayId` event injection
- Still effective for many practical automation tasks

---

## 6) Implementation note for this repo

If implementing fallback here, use a separate stock-Android skill (e.g. `ShizukuDisplaySkill`) and keep existing privileged `AgentDisplaySkill` unchanged.

One technical caveat: current `ShizukuManager.executeCommand()` is text-oriented and truncates output; screenshot capture is better handled through a binary streaming path.

