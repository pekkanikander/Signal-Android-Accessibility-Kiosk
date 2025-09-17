# Signal Android - Care Mode

Signal — Care mode is a simplified Signal UX for congnitively challenged users.

Signal – Care Mode is a Signal feature that provides a **simplified, single-conversation experience**
for users with reduced cognitive capacity (elderly, dementia, etc.)
while maintaining full Signal security and functionality.

> The original upstream Signal Android README is preserved as **`README-SIGNAL.md`**.

---

## For Caregivers & Family Members

### What is Signal Care Mode?

Signal Care Mode transforms Signal into a very **simple messaging app** for your loved one.
Instead of seeing all many conversations, they see only **one selected conversation** -
typically with their primary caregiver or family member, or a group chat with a few people.

### How It Works

1. **Setup**: You enable Care Mode in Signal Settings and select which conversation to show
2. **Simplified Interface**: Your loved one sees only their conversation - no complex menus, popups, or multiple chats
3. **Easy Communication**: Large, clear buttons for sending messages and voice notes
4. **No Confusion**: No back buttons, settings, popups, or other apps to accidentally tap

### Key Benefits

- **Reduces Cognitive Load**: No complex navigation or multiple conversations
- **Maintains Privacy**: Uses Signal's secure messaging protocol
- **Familiar Technology**: Works with existing Signal contacts and groups
- **Easy Setup**: Simple toggle in Signal settings

### Getting Started

1. Open Signal on your loved one's device
2. Go to **Settings** → **Accessibility Mode**
3. Select the conversation you want them to see
4. Select **Enable Accessibility Mode**
5. Exit Settings - Signal will switch to Care Mode
6. Your loved one can now use the simplified interface

### Customization

You can adjust, using the baseline Signal settings:
- **Text size** for better readability
- **Theme** for dark, light, or dynamic
New settings, to be implemented:
- **Contrast** for visual clarity
- **Touch sensitivity** for easier interaction
- **Voice note settings** for audio communication

### Exiting Care Mode

To return to normal Signal:
- Use the exit gesture to return to **Settings**
- Go to **Accessibility Mode** → **Enable Accessibility Mode** and disable it

#### Exit gestures

The default exit gesture is two-finger hold on the upper conversation title.
Keep two fingers on the title until you see a confirmation dialogue to appear.
Then, confirm that you want to exit accessibility mode.

The exit gesture can be changed at **Settings** → **Accessibility Mode** → **Advanced...**


## Goals

- **One conversation only**: App opens directly into a preselected conversation
- **Simplified interface**: Large, high-contrast controls; no complex navigation
- **Essential actions only**: Send/receive text; later maybe record/send voice notes
- **Low cognitive load**: Removes surprises and interaction traps
- **Maintains Signal security**: No changes to protocol, registration, or cryptography

## Non-Goals

- No changes to the Signal protocol, registration, servers, or cryptography
- No alternative networks or bridges
- No theming beyond what's required for clarity and accessibility

## License

This fork remains open-source under the same license as the upstream project. See upstream license files for details.

## Acknowledgements

Thanks to the Signal team for the upstream codebase and to the maintainers of related forks whose build and packaging practices informed this approach.

For the upstream documentation and build notes, see **`README-SIGNAL.md`**.

---

## For Engineers

### Technical Overview

This implementation adds a **parallel accessibility interface** to Signal without modifying existing functionality.
The approach maintains Signal's user experience and security model while providing a simplified user experience.
All modifications to the Signal upstream baseline are attempted to be minimal.

**Parallel Interface Design:**
- New `AccessibilityModeActivity` and `AccessibilityModeFragment`
- Reuses existing `ConversationViewModel`, `ConversationRepository`, and backend services
- Zero changes to existing Signal functionality
- Minimal patchset that rebases cleanly onto upstream

**Component Reuse Strategy:**
- **Existing**: Message handling, crypto, network, storage, conversation logic
- **New**: Accessibility UI, simplified attachment handling, accessibility-specific navigation
- **Minimal changes** to existing Signal code

## Interface Design Summary — Android

- Treat **Care Mode** as a **root-level mode** with its own root activity (`AccessibilityModeActivity`).
- When toggling mode (either direction), **rebase the task**:
  - Start the new root with `NEW_TASK | CLEAR_TASK` and finish the old stack.
    This guarantees predictable Back/App Switcher behaviour and avoids ghost activities.
- Centralise routing in a tiny `AccessibilityModeRouter` invoked only in:
  - `MainActivity.onStart()`
  - `AccessibilityModeActivity.onStart()`
- Keep all other flows (notifications, deeplinks) funneled through a single router method so they respect Care Mode.

## Components

- **AccessibilityModeStore**: persistent source of truth `{ enabled: Boolean, threadId: Long? }` (backed by existing `SignalStore.accessibilityMode`).
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

### 1) AccessibilityModeState & Store
```kotlin
@Immutable
data class AccessibilityModeState(val enabled: Boolean, val threadId: Long?)

interface AccessibilityModeStore {
    fun state(): Flow<AccessibilityModeState>
    fun current(): AccessibilityModeState // synchronous read (cached)
    fun setEnabled(enabled: Boolean, threadId: Long?): Unit
}

class SignalCareModeStore(private val signalStore: SignalStore) : AccessibilityModeStore {
    override fun state(): Flow<AccessibilityModeState> = signalStore.accessibilityMode.stateFlow()
    override fun current(): AccessibilityModeState = signalStore.accessibilityMode.read()
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
