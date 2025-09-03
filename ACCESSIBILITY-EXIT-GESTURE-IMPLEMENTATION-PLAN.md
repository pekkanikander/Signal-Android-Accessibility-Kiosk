

# Accessibility Exit Gesture — Settings & Behaviour Spec

This document defines the user‑visible settings, runtime behaviour, and implementation plan for the **Exit to Settings** gesture in Accessibility (Care) Mode.

We support two trigger variants and an optional PIN gate:
- **Gesture A:** Opposite‑corners two‑finger **hold** (strict).
- **Gesture B:** Two‑finger **header hold** (intentional but learnable).
- **Optional PIN:** After confirmation slider, require a 4–6 digit PIN (independent toggle).

---

## 1) Settings UI (Accessibility Mode page)

### Controls
1. **Exit gesture** (single‑choice)
   - *Opposite corners hold (recommended for strict)*
   - *Two‑finger header hold*
2. **Require PIN** (toggle)
   - Off (default for Gesture B profile)
   - On (recommended for Gesture A profile)

### Advanced (developer / hidden)
- **Hold duration (gesture trigger)**: default A=2500 ms, B=1800 ms
- **Confirm hold duration (slider)**: default 1500 ms
- **Timeout on confirmation screen**: default 10 s
- **Corner hit‑rect size**: default 72 dp (A)
- **Drift tolerance**: default 24 dp

These should be visible only under a small “Advanced” expander or a debug flag, and implemented only in a later stage.

### Persistence keys (example, change the names to follow Signal conventions)
```text
pref_accessibility_exit_gesture: ENUM { OPPOSITE_CORNERS, HEADER_HOLD }
pref_accessibility_exit_require_pin: BOOLEAN
pref_accessibility_exit_hold_ms: INT
pref_accessibility_exit_confirm_ms: INT
pref_accessibility_exit_timeout_ms: INT
pref_accessibility_exit_corner_dp: INT
pref_accessibility_exit_drift_dp: INT
```

### Copy (user visible strings)
- **Exit gesture** — "Choose how to open Settings from Care Mode"
  - "Opposite corners hold (strict)"
  - "Two‑finger header hold"
- **Require PIN** — "Ask for PIN before opening Settings", optional
- **Advanced** — "Advanced" — implemented later

---

## 2) Runtime Behaviour (state machine)

```
IDLE (in AccessibilityModeActivity)
  └─(configured trigger A or B detected for hold_ms)→ TRIGGER_CONFIRMED
TRIGGER_CONFIRMED
  └─ show AccessibilityModeConfirmExitActivity (no animation)
CONFIRM_OVERLAY (AccessibilityModeConfirmExitActivity active)
  ├─ press‑and‑hold completed for confirm_ms →
  │     if (require_pin) → PIN_ENTRY else → OPEN_SETTINGS
  ├─ timeout after timeout_ms → DISMISS → IDLE
  ├─ user taps "Return to messages" → DISMISS → IDLE
  └─ Back → DISMISS → IDLE
PIN_ENTRY
  ├─ correct → OPEN_SETTINGS
  ├─ incorrect (x3) → cooldown 30 s, then DISMISS → IDLE
OPEN_SETTINGS
  └─ startActivity(AppSettingsActivity) (no animation), finish overlay
```

---

## 3) Gesture detection

### Common gating
- Only active in **AccessibilityModeActivity** (root) and when window is focused.
- Ignore when a modal/system dialog is on top (e.g., permission, call UI).
- Respect TalkBack: use **hold** rather than swipes; announce feedback.
- Implement in a lightweight overlay attached to the activity’s root content.

### A) Opposite‑corners hold
- Two pointers down within 150 ms of each other.
- Each pointer must land within a square hit‑rect centred at top‑left and bottom‑right corners (default 72 dp). On small phones increase to 88 dp if needed.
- Inter‑pointer distance ≥ 0.85 × screen diagonal (prevents near‑corner cheating).
- Maintain hold for **hold_ms**; allow drift ≤ **drift_dp**.

### B) Two‑finger header hold
- Two pointers down within 150 ms inside the **top app bar/header** bounds.
- Maintain hold for **hold_ms**; allow drift ≤ **drift_dp**.

### Feedback (both)
- On trigger success: short haptic tick + brief toast/announcement: "Release. Confirm slider visible." (We immediately open the overlay; releasing fingers is fine.)
- Periodic haptic feedback during the initial gestures, once every 500ms, as the gestures are long.

### Kotlin sketch: detector hookup
```kotlin
class ExitGestureDetector(
    private val cfg: ExitGestureConfig,
    private val headerBoundsProvider: () -> Rect
) : View.OnTouchListener {
    // Track pointers, timing, and regions; call onTriggered() when matched
}

// In AccessibilityModeActivity.onCreate
val detector = ExitGestureDetector(config, ::headerBounds)
rootView.setOnTouchListener(detector)
```

---

## 4) Confirmation UI (Press‑and‑hold slider)

### Form factor
- Implement as a **fullscreen dialog‑themed Activity**: `AccessibilityModeConfirmExitActivity`.
  - Theme: transparent background, dim behind, no status bar animation.
  - Flags: exclude from recents; no transition animations on enter/exit.

### Behaviour
- Show a single large control: **press‑and‑hold button** with radial progress (1.5 s default). No drag needed; continuous press only.
- Accessibility: contentDescription announces progress; haptic ticks every 0.5 s.
- **Timeout**: auto‑dismiss after **timeout_ms** (default 10 s) if no successful hold.
- **Return control**: explicit secondary button "Return to messages".
- Back dismisses.

### Visuals
- Title: "Open Settings"
- Subtitle (small): "Press and hold to confirm"
- Optional tiny countdown text: "Closes in 10 s" updating each second.

### Start/finish
```kotlin
// From AccessibilityModeActivity on trigger
startActivity(Intent(this, ConfirmExitActivity::class.java))
overridePendingTransition(0, 0)

// In ConfirmExitActivity on success
if (requirePin) startActivity(Intent(this, PinEntryActivity::class.java))
else startActivity(Intent(this, AppSettingsActivity::class.java))
overridePendingTransition(0, 0)
finish()
```

---

## 5) PIN entry

### Source of truth
- Preference (example, adjust) `pref_accessibility_exit_require_pin` (BOOLEAN) and stored PIN hash/salt (separate store).

### UI
- Numeric keypad with masked digits; 4–6 digits; backspace; contentDescription for accessibility.
- Cooldown after 3 failures (30 s). Back cancels to messages.

### Reuse vs custom
- **Signal:** Check if there is an existing PIN entry Activity. Reuse it if possible.

If no reusable component exists in the codebase, implement a minimal `AccessibilityModeSettingsPinEntryActivity` (dialog‑themed, fullscreen) with a simple ViewModel validating against a locally stored salted hash.

---

## 6) Edge cases & safeguards
- **Process death:** On cold start into Care Mode, detector is re‑armed; no persistent overlay.
- **Spurious touches:** Temporal gate (≤150 ms), distance gate, and long hold suppress random triggers.
- **TalkBack conflicts:** Use holds, not swipes; announce every state change; ensure focus order (Return → Hold button) is sensible.
- **Screen readers:** If the user is in a Call or System overlay, ignore triggers until focus returns.
- **Small devices / cases:** Allow increasing corner hit‑rect via Advanced.

---

## 7) Test plan

### Robolectric (logic)
- Detector unit tests for A and B: timing windows, drift budget, distance threshold.
- State machine transitions for overlay timeout, success, cancel.

### Instrumented (UX)
- ActivityScenario tests: trigger → overlay appears (no animation), timeout auto‑dismiss, Return button behaviour, PIN flow (on/off).
- Accessibility checks: TalkBack announcements present; focus order; large touch targets (>48 dp).

---

## 8) Telemetry (optional, debug‑only)
- Count of trigger successes vs cancels.
- Overlay timeouts.
- PIN attempts (success/failure counters only; never log PIN digits).

---

## 9) Defaults to ship
- Gesture: **B** for general users; **A** where strict control is required.
- Require PIN: **Off** by default; **On** recommended for Gesture A deployments.
- Durations: trigger A=2500 ms, B=1800 ms; confirm=1500 ms; timeout=10 s.
