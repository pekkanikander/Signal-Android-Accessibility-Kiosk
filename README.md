
---

# Care Mode — Optimal Intent Stack Architecture (Proposed Design)

This section provides the requested deliverables: intent-stack designs per scenario, implementation code, state management, testing plan, edge-case handling, and performance notes. It assumes the current triad of activities (`MainActivity`, `AccessibilityModeActivity`, `AppSettingsActivity`) and aims for minimal, surgical changes.

## Design Summary

- Treat **Care Mode** as a **root-level mode** with its own root activity (`AccessibilityModeActivity`).
- When toggling mode (either direction), **rebase the task**: start the new root with `NEW_TASK | CLEAR_TASK` and finish the old stack. This guarantees predictable Back/App Switcher behaviour and avoids ghost activities.
- Do **not** rely on opportunistic redirects in random lifecycle callbacks. Centralise routing in a tiny `CareModeRouter` invoked only in:
  - `MainActivity.onStart()`
  - `AccessibilityModeActivity.onStart()`
  - Immediately after a toggle in `AppSettingsActivity` (to rebase proactively)
- Keep all other flows (notifications, deeplinks) funneled through a single router method so they respect Care Mode.


## Components

- **CareModeStore**: persistent source of truth `{ enabled: Boolean, threadId: Long? }` (backed by existing `SignalStore.accessibilityMode`).
- **CareModeRouter**: centralises all routing decisions and task-rebasing.
- **IntentFactory**: creates intents with the correct flags/extras for both modes.


## Intent Flags & Behaviour (Canonical)

- **Rebase to Care Mode root**: `Intent(AccessibilityModeActivity) + flags(NEW_TASK | CLEAR_TASK | NO_ANIMATION)`; `overridePendingTransition(0,0)`.
- **Rebase to Normal root**: `Intent(MainActivity) + flags(NEW_TASK | CLEAR_TASK | NO_ANIMATION)`; `overridePendingTransition(0,0)`.
- **Open Settings**: default `startActivity(Intent(AppSettingsActivity))` (no stack manipulation). Returning from Settings lets the router decide if a rebase is required (if mode changed).
- **Back in Care Mode root**: exit app (`finishAndRemoveTask()` or default since it is root).

Rationale: This pattern avoids flashes, prevents duplicate instances, and ensures the **task reflects the chosen mode** at all times.


## Detailed Intent Stack per Scenario

Notation: `[`top → bottom`]`. *Root* is the last element.

### Scenario 1 — Fresh Launch with Care Mode **Disabled**
**Flow**: Launch → Normal UI → Settings → Enable → Return.
- **Initial launch**: `[`MainActivity (root)`]`.
- **Open Settings**: `[`AppSettingsActivity, MainActivity (root)`]`.
- **Enable Care Mode & Return**: on save/toggle, **rebase**:
  - Before returning: `CareModeRouter.rebaseToCare(context, threadId)`.
  - **After rebase**: `[`AccessibilityModeActivity (root)`]`.

### Scenario 2 — Fresh Launch with Care Mode **Enabled**
**Flow**: Launch → Care UI → Settings → Disable → Return.
- **Initial launch**: `[`AccessibilityModeActivity (root)`]`.
- **Open Settings**: `[`AppSettingsActivity, AccessibilityModeActivity (root)`]`.
- **Disable Care Mode & Return**: `rebaseToNormal()`.
  - **After rebase**: `[`MainActivity (root)`]`.

### Scenario 3 — Care Mode Active → Settings → Return (**No changes**)
**Flow**: Care UI → Settings → Back.
- **Before**: `[`AppSettingsActivity, AccessibilityModeActivity (root)`]`.
- **Back**: No rebase since mode unchanged → `[`AccessibilityModeActivity (root)`]`.

### Scenario 4 — Normal → Settings → **Enable Care Mode**
Same as Scenario 1 → ends with `[`AccessibilityModeActivity (root)`]`.

### Scenario 5 — **Back Button** in Care Mode
- Stack is just `[`AccessibilityModeActivity (root)`]`.
- Back exits app. (Optionally call `finishAndRemoveTask()` in `onBackPressedDispatcher` for clarity.)

### Scenario 6 — **App Switcher**
- Returning to Signal restores the **current root** (Care or Normal) because only that root is in the task.

### Scenario 7 — **Deep Links**
- If **Care Mode enabled**: ignore deep link target; route to `AccessibilityModeActivity` with the selected thread ID. (Provide gentle UX: optional toast “Care Mode is active”.)
- If **Care Mode disabled**: handle deep link normally through existing flows.


## Code Implementation

### 1) CareModeState & Store
```kotlin
@Immutable
data class CareModeState(val enabled: Boolean, val threadId: Long?)

interface CareModeStore {
    fun state(): Flow<CareModeState>
    fun current(): CareModeState // synchronous read (cached)
    fun setEnabled(enabled: Boolean, threadId: Long?): Unit
}

class SignalCareModeStore(private val signalStore: SignalStore) : CareModeStore {
    override fun state(): Flow<CareModeState> = signalStore.accessibilityMode.stateFlow()
    override fun current(): CareModeState = signalStore.accessibilityMode.read()
    override fun setEnabled(enabled: Boolean, threadId: Long?) =
        signalStore.accessibilityMode.write(enabled, threadId)
}
```

### 2) IntentFactory
```kotlin
object IntentFactory {
    fun careRoot(context: Context, threadId: Long?): Intent =
        Intent(context, AccessibilityModeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("selected_thread_id", threadId)

    fun normalRoot(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    fun settings(context: Context): Intent = Intent(context, AppSettingsActivity::class.java)
}
```

### 3) CareModeRouter
```kotlin
object CareModeRouter {
    lateinit var store: CareModeStore

    /** Call from MainActivity.onStart() and AccessibilityModeActivity.onStart(). */
    fun routeIfNeeded(host: Activity) {
        val s = store.current()
        val isCare = s.enabled
        when (host) {
            is AccessibilityModeActivity -> {
                // Care expected; verify thread selection remains valid and correct.
                val hostThread = host.intent.getLongExtra("selected_thread_id", -1L).takeIf { it > 0 }
                if (!isCare) {
                    host.startActivity(IntentFactory.normalRoot(host))
                    host.overridePendingTransition(0, 0); host.finish()
                } else if (s.threadId != hostThread) {
                    host.startActivity(IntentFactory.careRoot(host, s.threadId))
                    host.overridePendingTransition(0, 0); host.finish()
                }
            }
            is MainActivity -> {
                if (isCare) {
                    host.startActivity(IntentFactory.careRoot(host, s.threadId))
                    host.overridePendingTransition(0, 0); host.finish()
                }
            }
            else -> { /* no-op for other activities */ }
        }
    }

    /** Call directly from Settings when user toggles mode for immediate rebase. */
    fun rebaseToCare(context: Context, threadId: Long?) {
        context.startActivity(IntentFactory.careRoot(context, threadId))
        if (context is Activity) context.overridePendingTransition(0, 0)
    }

    fun rebaseToNormal(context: Context) {
        context.startActivity(IntentFactory.normalRoot(context))
        if (context is Activity) context.overridePendingTransition(0, 0)
    }
}
```

### 4) Activity hooks
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onStart() {
        super.onStart()
        CareModeRouter.routeIfNeeded(this)
    }
}

class AccessibilityModeActivity : AppCompatActivity() {
    override fun onStart() {
        super.onStart()
        CareModeRouter.routeIfNeeded(this)
    }

    override fun onBackPressed() {
        // As root, back exits the app. Optionally make it explicit:
        finishAndRemoveTask()
    }
}

class AppSettingsActivity : AppCompatActivity() {
    private fun onCareModeToggled(enabled: Boolean, threadId: Long?) {
        CareModeRouter.store.setEnabled(enabled, threadId)
        if (enabled) CareModeRouter.rebaseToCare(this, threadId) else CareModeRouter.rebaseToNormal(this)
        // Optionally finish settings to reveal the new root immediately
        finish()
    }
}
```

### 5) Notifications and Deep Links
Centralise entry-point intents so they honour Care Mode.
```kotlin
object EntryIntents {
    fun messageTap(context: Context, targetThreadId: Long): PendingIntent {
        val s = CareModeRouter.store.current()
        val intent = if (s.enabled) IntentFactory.careRoot(context, s.threadId)
                     else IntentFactory.normalRoot(context).putExtra("open_thread_id", targetThreadId)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun handleDeepLink(context: Context, deepLink: Uri): Intent {
        val s = CareModeRouter.store.current()
        return if (s.enabled) IntentFactory.careRoot(context, s.threadId)
               else /* existing deep-link intent builder */ Intent(context, MainActivity::class.java).setData(deepLink)
    }
}
```


## State Management Strategy

- **Single source of truth**: `CareModeStore` (persisted; exposes `Flow` and immediate `current()`).
- **Immutable hand-off**: Activities read a snapshot via `current()` in `routeIfNeeded` to avoid races.
- **Settings** applies state and triggers immediate rebase. No reliance on incidental `onResume()` checks.
- **Thread existence validation**: before rebasing to Care, validate `threadId` exists; otherwise route to a small **Care Onboarding** screen that asks the caregiver to pick a conversation (or fall back to `MainActivity` with a one-shot dialog).


## Testing Strategy

### JVM (Robolectric) — fast logic tests
- **Router unit tests**: fake `CareModeStore` + verify `routeIfNeeded()` decisions (no actual SQLCipher touch).
- **ViewModel tests**: use fakes for repositories; no database involvement.

### Instrumented (`androidTest`) — integration/UX correctness
Use `ActivityScenario` and Espresso to assert stacks and transitions.

- **Scenario 1**: Start `MainActivity` → open `AppSettingsActivity` → toggle ON → assert only `AccessibilityModeActivity` is resumed; back exits.
- **Scenario 2**: Start `AccessibilityModeActivity` (pre-enable state) → toggle OFF → assert `MainActivity` root.
- **Scenario 3**: Care → Settings → back (no change) → still in `AccessibilityModeActivity`.
- **Scenario 5**: Back exits from Care root (`isFinishing` & task empty).
- **Scenario 7**: With Care enabled, tapping a notification / deep link leads to `AccessibilityModeActivity` regardless of original target.

**CI**: Run instrumented tests on Gradle Managed Devices for determinism.


## Edge Case Handling

- **Selected conversation deleted**: On rebase or on `AccessibilityModeActivity.onStart()`, verify thread exists. If missing → route to Care Onboarding or show inline error with button to Settings. Do not fall back to normal mode silently.
- **Crash/Process death**: On cold start, initial activity calls `routeIfNeeded()` and normalises the task to the correct root.
- **Multiple rapid toggles**: shield with a simple debounce in Settings UI; the router is idempotent due to `CLEAR_TASK` semantics.
- **Permission screens / system dialogs**: these remain above the root; once dismissed, `onStart()` re-validates mode.


## Performance Considerations

- Use `FLAG_ACTIVITY_NO_ANIMATION` + `overridePendingTransition(0,0)` when rebasing to avoid flashes.
- Keep the dependency injection for `CareModeRouter.store` lightweight (e.g., an `object` initialised in `Application.onCreate`).
- Avoid heavy work in `onStart()`; only read the current state and decide. Expensive operations (loading conversation) remain inside the target activity.
- Memory: task rebasing ensures a single-root stack; no leaks from obsolete activities.


## Optional Manifest Tweaks (Not Required, but Safe)

If you see stray re-creations under manufacturer ROMs, consider adding for roots:
```xml
<!-- Optional: reduce duplicate creations under some OEMs -->
<activity android:name=".MainActivity"
          android:launchMode="singleTop" />
<activity android:name=".AccessibilityModeActivity"
          android:launchMode="singleTop" />
```
Keep defaults otherwise; the `CLEAR_TASK` rebasing already guarantees correctness.


## Success Criteria Mapping

- **Smooth UX**: Rebase with no animations; single-root stacks prevent flicker.
- **Predictable back**: Care root exits; normal root follows existing behaviour.
- **Minimal code**: One router, one store, two small hooks.
- **Robustness**: Centralised decisions; deep links/notifications respect mode.
- **Performance**: No heavy lifecycle work; single activity in task.
